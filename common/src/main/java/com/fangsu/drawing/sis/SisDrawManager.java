package com.fangsu.drawing.sis;

import com.fangsu.drawing.sign.JsSisDrawing;
import com.google.gson.JsonObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class SisDrawManager {

    //    private static final ResourceLocation SCRIPTS_LOCATION = new ResourceLocation("fangsu:sis/sis_scripts.json");
    private static final Map<String, SisDrawItem> drawOptions = new HashMap<>();
    private static final Map<String, BaseSisDrawing> drawingCache = new ConcurrentHashMap<>();
//    private static final Map<String, String> drawingClassMap = new HashMap<>();

    private SisDrawManager() {
    }

    public static void preload() {
        drawOptions.clear();
        drawingCache.clear();
//        drawingClassMap.clear();
    }

//    public static List<SisDrawItem> getDrawOptions() {
//        return Collections.unmodifiableList(drawOptions);
//    }

    /**
     * @param text        显示名称
     * @param key         唯一键
     * @param contentText 描述文本
     * @param clazz       继承 BaseSisDrawing 的 Class 对象
     * @deprecated 注册一个 Java 绘制类。
     */
    @Deprecated
    public static void registerJavaDrawing(String text, String key, String contentText, Class<? extends BaseSisDrawing> clazz) {
        Supplier<BaseSisDrawing> supplier = () -> {
            try {
                return (BaseSisDrawing) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        drawOptions.put(key, new SisDrawItem(text, supplier));
//        drawingClassMap.put(key, clazz.getName());
    }

    public static void registerJavaDrawing(String key, Supplier<BaseSisDrawing> supplier) {
        drawOptions.put(key, new SisDrawItem(key, supplier));
    }

    /**
     * 根据 key 创建 BaseSisDrawing 实例。
     * 优先尝试从注册的 Java 类反射创建，失败时回退到 JS 脚本。
     */
    public static BaseSisDrawing createDrawing(String key) {
        if (drawingCache.containsKey(key)) {
            return drawingCache.get(key);
        }

        var registered = drawOptions.get(key);
        if (registered != null) {
            return registered.supplier.get();
        }

        BaseSisDrawing jsDrawing = new JsSisDrawing(key);
        drawingCache.put(key, jsDrawing);
        return jsDrawing;
    }

    public static void reset() {
        drawingCache.clear();
    }

    public record SisDrawItem(String id, Supplier<BaseSisDrawing> supplier) {
    }
}
