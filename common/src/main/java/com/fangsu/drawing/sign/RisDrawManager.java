package com.fangsu.drawing.sign;

import com.fangsu.Main;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RisDrawManager {

    private static final ResourceLocation SCRIPTS_LOCATION = new ResourceLocation("fangsu:route_info_sign/ris_scripts.json");
    private static final List<ModelSelectInfo> drawOptions = new ArrayList<>();
    private static final Map<String, BaseRisDrawing> drawingCache = new ConcurrentHashMap<>();
    private static final Map<String, String> drawingClassMap = new HashMap<>();

    private RisDrawManager() {
    }

    public static void preload() {
        drawOptions.clear();
        drawingCache.clear();
        drawingClassMap.clear();
        injectScriptDrawers();
    }

    public static List<ModelSelectInfo> getDrawOptions() {
        return Collections.unmodifiableList(drawOptions);
    }

    /**
     * 注册一个 Java 绘制类。
     *
     * @param text        显示名称
     * @param key         唯一键
     * @param contentText 描述文本
     * @param clazz       继承 BaseRisDrawing 的 Class 对象
     */
    public static void registerJavaDrawing(String text, String key, String contentText, Class<? extends BaseRisDrawing> clazz) {
        drawOptions.add(new ModelSelectInfo(text, key, contentText));
        drawingClassMap.put(key, clazz.getName());
    }

    /**
     * 根据 key 创建 BaseRisDrawing 实例。
     * 优先尝试从注册的 Java 类反射创建，失败时回退到 JS 脚本。
     */
    public static BaseRisDrawing createDrawing(String key) {
        if (drawingCache.containsKey(key)) {
            return drawingCache.get(key);
        }

        String className = drawingClassMap.get(key);
        if (className != null) {
            try {
                Class<?> clazz = Class.forName(className);
                if (BaseRisDrawing.class.isAssignableFrom(clazz)) {
                    @SuppressWarnings("unchecked")
                    BaseRisDrawing instance = (BaseRisDrawing) clazz.getDeclaredConstructor().newInstance();
                    drawingCache.put(key, instance);
                    return instance;
                }
            } catch (Exception e) {
                Main.LOGGER.error("Failed to reflectively create RIS drawing: {} class={}", key, className, e);
            }
        }

        BaseRisDrawing jsDrawing = new JsRisDrawing(key);
        drawingCache.put(key, jsDrawing);
        return jsDrawing;
    }

    private static void injectScriptDrawers() {
        try {
            JsonObject loaded = ResourceUtil.loadAsJSON(SCRIPTS_LOCATION).getAsJsonObject();
            if (!loaded.has("content")) return;
            JsonArray array = loaded.getAsJsonArray("content");
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                String text = item.get("text").getAsString();
                String content = item.get("content").getAsString();

                if (item.has("javaClass")) {
                    String javaClass = item.get("javaClass").getAsString();
                    drawingClassMap.put(content, javaClass);
                    String contentText = item.has("contentText") ? item.get("contentText").getAsString() : "";
                    drawOptions.add(new ModelSelectInfo(text, content, contentText));
                } else {
                    if (item.has("contentText")) {
                        drawOptions.add(new ModelSelectInfo(text, content, item.get("contentText").getAsString()));
                    } else {
                        drawOptions.add(new ModelSelectInfo(text, content));
                    }
                }
            }
        } catch (Exception e) {
            Main.LOGGER.error("Failed to preload RIS draw scripts", e);
        }
    }

    public static void reset() {
        drawingCache.clear();
    }
}
