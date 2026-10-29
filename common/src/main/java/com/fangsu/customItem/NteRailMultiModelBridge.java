package com.fangsu.customItem;

import com.fangsu.Main;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ui.NteRailModelSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** 可选 NTE 的反射边界：不在类签名中引用 NTE，也不改变服务端的数据协议。 */
public final class NteRailMultiModelBridge {
    private static final ThreadLocal<String> BAKING_KEY = new ThreadLocal<>();
    private static final ClassValue<Method> MODEL_GETTERS = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("getModelKey");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    };
    private static boolean warned;
    private static final ClassValue<Field> REGISTRIES = new ClassValue<>() {
        @Override
        protected Field computeValue(Class<?> type) {
            try {
                Class<?> registry = sibling(type, "data.RailModelRegistry");
                Field elements = optionalField(registry, "ELEMENTS");
                return elements == null ? field(registry, "elements") : elements;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    };

    private NteRailMultiModelBridge() { }

    public static String rawKey(Object rail) {
        try {
            return (String) MODEL_GETTERS.get(rail.getClass()).invoke(rail);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String bakingKey() {
        return BAKING_KEY.get();
    }

    public static String resolvePrimary(Object rail, String fallback) {
        for (Class<?> cursor = rail.getClass(); cursor != null; cursor = cursor.getSuperclass()) {
            for (Class<?> type : cursor.getInterfaces()) {
                if (type.getName().endsWith(".mtrsteamloco.data.RailExtraSupplier")) {
                    return primaryKey(type, rail, fallback);
                }
            }
        }
        return fallback;
    }

    /** 原方法负责默认轨道、隐藏轨道和不存在的 ID 的回退；只替换有效的主模型。 */
    public static String primaryKey(Class<?> dispatcher, Object rail, String fallback) {
        String raw = rawKey(rail);
        if (!NteRailModelSelection.isCombined(raw)) return fallback;
        String primary = NteRailModelSelection.decode(raw).get(0);
        try {
            return registry(dispatcher).containsKey(primary) && !primary.isEmpty() ? primary : fallback;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return fallback;
        }
    }

    /** 兼容 NTE 0.5.x 的内嵌选择页，以及 ANTE 的独立选择页。 */
    public static boolean openSelection(Object target) {
        try {
            Field selecting = optionalField(target.getClass(), "isSelectingModel");
            Object owner = target;
            if (selecting != null) {
                if (!selecting.getBoolean(target)) return false;
            } else {
                Field outer = optionalField(target.getClass(), "this$0");
                if (outer == null || !target.getClass().getName().endsWith("BrushEditRailScreen$SelectScreen")) return false;
                owner = outer.get(target);
            }
            CompoundTag tag = (CompoundTag) method(owner.getClass(), "getBrushTag").invoke(owner);
            Object rail = field(owner.getClass(), "pickedRail").get(owner);
            String initial = tag != null && tag.contains("ModelKey") ? tag.getString("ModelKey") : rawKey(rail);
            Map<String, Component> choices = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : registry(target.getClass()).entrySet()) {
                choices.put(entry.getKey(), ((Component) field(entry.getValue().getClass(), "name").get(entry.getValue())).copy());
            }
            Method callback = method(target.getClass(), "onBtnClick", String.class);
            Consumer<String> save = value -> {
                try {
                    callback.invoke(target, value);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("NTE rail selection save failed", e);
                }
            };
            Runnable back = () -> {
                if (selecting == null) {
                    ((Screen) target).onClose();
                } else {
                    try {
                        selecting.setBoolean(target, false);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                    Minecraft.getInstance().setScreen((Screen) target);
                }
            };
            Minecraft.getInstance().setScreen(new NteRailModelSelectScreen(choices, initial, save, back));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return false;
        }
    }

    /** 原版画刷主页不认识组合 ID，将它显示成已选模型名称，避免暴露存储串。 */
    public static void refreshLabels(Object target) {
        if (!(target instanceof Screen screen)) return;
        try {
            CompoundTag tag = (CompoundTag) method(target.getClass(), "getBrushTag").invoke(target);
            if (tag == null || !NteRailModelSelection.isCombined(tag.getString("ModelKey"))) return;
            String raw = tag.getString("ModelKey");
            Map<String, Object> models = registry(target.getClass());
            List<String> names = new ArrayList<>();
            for (String key : NteRailModelSelection.decode(raw)) {
                Object model = models.get(key);
                names.add(model == null ? key : ((Component) field(model.getClass(), "name").get(model)).getString());
            }
            String label = String.join(" + ", names);
            for (var child : screen.children()) {
                if (child instanceof AbstractWidget widget && widget.getMessage().getString().contains(raw)) {
                    widget.setMessage(ComponentHelper.literal(label));
                }
            }
        } catch (ReflectiveOperationException e) {
            warn(e);
        }
    }

    /** 状态归属于 dispatcher，随 removeRail / clearRail 清除，不持有卸载世界的轨道。 */
    public static final class Layers {
        private final Map<Object, List<Object>> baked = new HashMap<>();
        private final Map<Object, String> selections = new HashMap<>();
        private RenderAccess access;

        private RenderAccess access(Object dispatcher) throws ReflectiveOperationException {
            if (access == null) access = new RenderAccess(dispatcher.getClass());
            return access;
        }

        public void refresh(Object dispatcher, Object rail) {
            String previous = selections.get(rail);
            if (previous == null || previous.equals(rawKey(rail))) return;
            try {
                // 主模型不变、只增删附属时，原版的引用比较无法感知，因此主动失效一次。
                method(dispatcher.getClass(), "removeRail", access(dispatcher).railType).invoke(dispatcher, rail);
            } catch (ReflectiveOperationException e) {
                warn(e);
            }
        }

        public void add(Object dispatcher, Object rail) {
            if (selections.putIfAbsent(rail, rawKey(rail)) != null) return;
            List<String> keys = NteRailModelSelection.decode(rawKey(rail));
            if (keys.size() <= 1) return;
            List<Object> extras = new ArrayList<>();
            baked.put(rail, extras);
            try {
                RenderAccess a = access(dispatcher);
                Map<String, Map<Long, Object>> chunks = a.chunks(dispatcher);
                Map<String, Object> registry = registry(dispatcher.getClass());
                for (String key : keys.subList(1, keys.size())) {
                    if (key.isEmpty() || key.equals("null") || !registry.containsKey(key)) continue;
                    Object layer;
                    BAKING_KEY.set(key);
                    try {
                        layer = a.bake.newInstance(rail);
                    } finally {
                        BAKING_KEY.remove();
                    }
                    extras.add(layer);
                    // 每个模型独立烘焙，保留自己的 repeatInterval / yOffset / 包围盒。
                    Map<Long, Object> modelChunks = chunks.computeIfAbsent(key, ignored -> new HashMap<>());
                    for (Long chunkId : a.covered(layer).keySet()) {
                        Object chunk = modelChunks.get(chunkId);
                        if (chunk == null) {
                            Constructor<?> constructor = a.instanced.getBoolean(dispatcher) ? a.instanceChunk : a.meshChunk;
                            chunk = constructor.newInstance(chunkId, key);
                            modelChunks.put(chunkId, chunk);
                            a.chunkList(dispatcher).add(chunk);
                        }
                        a.add.invoke(chunk, layer);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                remove(dispatcher, rail);
                warn(e);
            }
        }

        public void remove(Object dispatcher, Object rail) {
            selections.remove(rail);
            List<Object> extras = baked.remove(rail);
            if (extras == null || access == null) return;
            try {
                Map<String, Map<Long, Object>> chunks = access.chunks(dispatcher);
                for (Object layer : extras) {
                    Map<Long, Object> modelChunks = chunks.get(access.key.get(layer));
                    if (modelChunks != null) {
                        for (Long id : access.covered(layer).keySet()) {
                            Object chunk = modelChunks.get(id);
                            if (chunk != null) access.remove.invoke(chunk, layer);
                        }
                    }
                    if (access.dispose != null) access.dispose.invoke(layer);
                }
            } catch (ReflectiveOperationException e) {
                warn(e);
            }
        }

        public void clear(Object dispatcher) {
            for (Object rail : new ArrayList<>(baked.keySet())) remove(dispatcher, rail);
            selections.clear();
        }
    }

    private static final class RenderAccess {
        final Class<?> railType;
        final Constructor<?> bake, instanceChunk, meshChunk;
        final Field chunks, list, instanced, covered, key;
        final Method add, remove, dispose;

        RenderAccess(Class<?> dispatcher) throws ReflectiveOperationException {
            Class<?> bakedClass = sibling(dispatcher, "render.rail.BakedRail");
            // 两个版本都是一参数入口，ANTE 内部再委托给双参数构造器。
            Constructor<?> found = null;
            for (Constructor<?> constructor : bakedClass.getConstructors()) {
                if (constructor.getParameterCount() == 1) found = constructor;
            }
            if (found == null) throw new NoSuchMethodException("BakedRail(rail)");
            bake = found;
            railType = bake.getParameterTypes()[0];
            instanceChunk = sibling(dispatcher, "render.rail.InstancedRailChunk").getDeclaredConstructor(Long.class, String.class);
            meshChunk = sibling(dispatcher, "render.rail.MeshBuildingRailChunk").getDeclaredConstructor(Long.class, String.class);
            instanceChunk.setAccessible(true);
            meshChunk.setAccessible(true);
            chunks = field(dispatcher, "railChunkMap");
            list = field(dispatcher, "railChunkList");
            instanced = field(dispatcher, "isInstanced");
            covered = field(bakedClass, "coveredChunks");
            key = field(bakedClass, "modelKey");
            Class<?> chunk = sibling(dispatcher, "render.rail.RailChunkBase");
            add = method(chunk, "addRail", bakedClass);
            remove = method(chunk, "removeRail", bakedClass);
            Method disposer;
            try { disposer = method(bakedClass, "dispose"); }
            catch (NoSuchMethodException ignored) { disposer = null; }
            dispose = disposer;
        }

        @SuppressWarnings("unchecked")
        Map<String, Map<Long, Object>> chunks(Object target) throws IllegalAccessException {
            return (Map<String, Map<Long, Object>>) chunks.get(target);
        }

        @SuppressWarnings("unchecked")
        List<Object> chunkList(Object target) throws IllegalAccessException {
            return (List<Object>) list.get(target);
        }

        @SuppressWarnings("unchecked")
        Map<Long, ?> covered(Object target) throws IllegalAccessException {
            return (Map<Long, ?>) covered.get(target);
        }
    }

    private static Class<?> sibling(Class<?> type, String suffix) throws ClassNotFoundException {
        String name = type.getName();
        String root = name.substring(0, name.indexOf(".mtrsteamloco.") + ".mtrsteamloco.".length());
        return Class.forName(root + suffix, false, type.getClassLoader());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> registry(Class<?> type) throws ReflectiveOperationException {
        return (Map<String, Object>) REGISTRIES.get(type).get(null);
    }

    private static Field optionalField(Class<?> type, String name) {
        try { return field(type, name); }
        catch (NoSuchFieldException ignored) { return null; }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field result = cursor.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Method result = cursor.getDeclaredMethod(name, parameters);
                result.setAccessible(true);
                return result;
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static void warn(Exception exception) {
        if (warned) return;
        warned = true;
        Main.LOGGER.warn("[FangSu] NTE multi-model integration unavailable", exception);
    }
}
