package com.fangsu.drawing.pids;

import com.fangsu.Main;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.drawing.ris.BaseRisDrawing;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class PidsDrawManager {

    //    private static final ResourceLocation SCRIPTS_LOCATION = new ResourceLocation("fangsu:pids/pids_scripts.json");
    private static final Map<String, PidsDrawItem> drawOptions = new HashMap<>();
    private static final Map<String, BasePidsDrawing> drawingCache = new ConcurrentHashMap<>();
//    private static final Map<String, String> drawingClassMap = new HashMap<>();

    private PidsDrawManager() {
    }

    public static void preload() {
        drawOptions.clear();
        drawingCache.clear();
    }

//    public static List<ModelSelectInfo> getDrawOptions() {
//        return Collections.unmodifiableList(drawOptions);
//    }

    /**
     * 注册一个 Java 绘制类。
     *
     * @param text        显示名称
     * @param key         唯一键
     * @param contentText 描述文本
     * @param clazz       继承 BasePidsDrawing 的 Class 对象
     */
    @Deprecated
    public static void registerJavaDrawing(String text, String key, String contentText, Class<? extends BasePidsDrawing> clazz) {
        Supplier<BasePidsDrawing> supplier = () -> {
            try {
                return (BasePidsDrawing) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        drawOptions.put(key, new PidsDrawItem(key, supplier));
//        drawingClassMap.put(key, clazz.getName());
    }

    public static void registerJavaDrawing(String key, Supplier<BasePidsDrawing> supplier) {
        drawOptions.put(key, new PidsDrawItem(key, supplier));
    }

    /**
     * 根据 key 创建 BasePidsDrawing 实例。
     * 优先尝试从注册的 Java 类反射创建，失败时回退到 JS 脚本。
     */
    public static BasePidsDrawing createDrawing(String key) {
        // 先查缓存
        if (drawingCache.containsKey(key)) {
            return drawingCache.get(key);
        }

        // 尝试从 Java 类反射创建
        var registered = drawOptions.get(key);
        if (registered != null) {
            return registered.supplier().get();
        }

        // 回退到 JS 脚本
        BasePidsDrawing jsDrawing = new JsPidsDrawing(key);
        drawingCache.put(key, jsDrawing);
        return jsDrawing;
    }

    /**
     * 清除缓存（用于重载时）
     */
    public static void reset() {
        drawingCache.clear();
    }

    public record PidsDrawItem(String id, Supplier<BasePidsDrawing> supplier) {
    }
}
