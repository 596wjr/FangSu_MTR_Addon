package com.fangsu.drawing.diaoban;

import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.Main;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class DiaobanDrawManager {
    private static final ResourceLocation SCRIPTS_LOCATION = new ResourceLocation("fangsu:diaoban/diaoban_scripts.json");
    private static final String JAVA_DRAW_ROUTE_LIKE = "java:route_like";
    private static final List<ModelSelectInfo> drawOptions = new ArrayList<>();
    private static final Map<String, Supplier<BaseDiaobanDrawing>> drawingSuppliers = new HashMap<>();

    private DiaobanDrawManager() {
    }

    public static void preload() {
        drawOptions.clear();
        drawingSuppliers.clear();

        registerJavaDrawing("指示牌样式(Java)", JAVA_DRAW_ROUTE_LIKE, "内置 Java 绘制，无需 JS", RouteLikeDiaobanDrawing::new);
        injectScriptDrawers();
    }

    public static List<ModelSelectInfo> getDrawOptions() {
        return Collections.unmodifiableList(drawOptions);
    }

    public static void registerJavaDrawing(String text, String key, String contentText, Supplier<BaseDiaobanDrawing> factory) {
        drawOptions.add(new ModelSelectInfo(text, key, contentText));
        drawingSuppliers.put(key, factory);
    }

    public static BaseDiaobanDrawing createDrawing(String key) {
        Supplier<BaseDiaobanDrawing> javaFactory = drawingSuppliers.get(key);
        if (javaFactory != null) {
            return javaFactory.get();
        }
        return new JsDiaobanDrawing(key);
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
                if (item.has("contentText")) {
                    drawOptions.add(new ModelSelectInfo(text, content, item.get("contentText").getAsString()));
                } else {
                    drawOptions.add(new ModelSelectInfo(text, content));
                }
            }
        } catch (Exception e) {
            Main.LOGGER.error("Failed to preload diaoban draw scripts", e);
        }
    }
}
