package com.fangsu.drawing.ris;

import com.fangsu.Main;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.drawing.sign.JsRisDrawing;
import com.fangsu.drawing.sis.BaseSisDrawing;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.security.Provider;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class RisDrawManager {

    //    private static final ResourceLocation SCRIPTS_LOCATION = new ResourceLocation("fangsu:route_info_sign/ris_scripts.json");
    private static final Map<String, RisDrawItem> drawOptions = new HashMap<>();
    private static final Map<String, BaseRisDrawing> drawingCache = new ConcurrentHashMap<>();
//    private static final Map<String, String> drawingClassMap = new HashMap<>();

    private RisDrawManager() {
    }

    public static void preload() {
        drawOptions.clear();
        drawingCache.clear();
//        drawingClassMap.clear();
//        injectScriptDrawers();
    }

//    public static List<ModelSelectInfo> getDrawOptions() {
//        return Collections.unmodifiableList(drawOptions);
//    }

    /**
     * @param text        显示名称
     * @param key         唯一键
     * @param contentText 描述文本
     * @param clazz       继承 BaseRisDrawing 的 Class 对象
     * @deprecated 注册一个 Java 绘制类。
     */
    @Deprecated
    public static void registerJavaDrawing(String text, String key, String contentText, Class<? extends BaseRisDrawing> clazz) {
        Supplier<BaseRisDrawing> supplier = () -> {
            try {
                return (BaseRisDrawing) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        drawOptions.put(key, new RisDrawItem(key, supplier));
//        drawingClassMap.put(key, clazz.getName());
    }

    public static void registerJavaDrawing(String key, Supplier<BaseRisDrawing> supplier) {
        drawOptions.put(key, new RisDrawItem(key, supplier));
    }

    /**
     * 根据 key 创建 BaseRisDrawing 实例。
     * 优先尝试从注册的 Java 类反射创建，失败时回退到 JS 脚本。
     */
    public static BaseRisDrawing createDrawing(String key) {
        if (drawingCache.containsKey(key)) {
            return drawingCache.get(key);
        }

        var registered = drawOptions.get(key);
        if (registered != null) {
            return registered.supplier().get();
        }

        BaseRisDrawing jsDrawing = new JsRisDrawing(key);
        drawingCache.put(key, jsDrawing);
        return jsDrawing;
    }

    public static void reset() {
        drawingCache.clear();
    }

    public record RisDrawItem(String id, Supplier<BaseRisDrawing> supplier) {
    }
}
