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
    private static final Map<String, DiaobanDrawItem> drawOptions = new HashMap<>();

    private DiaobanDrawManager() {
    }

    public static void preload() {
        drawOptions.clear();
//        drawingSuppliers.clear();

        injectScriptDrawers();
    }

    public static List<ModelSelectInfo> getDrawOptions() {
        return Collections.unmodifiableList(new ArrayList<>(drawOptions.values()));
    }

    /**
     * 根据绘制脚本的 key（content 路径，如 "fangsu:diaoban/mtr_diaoban_route.js"）
     * 在 drawOptions 中遍历查找对应的 DiaobanDrawItem，然后从其 settings 中读取 "script_settings"。
     */
    public static JsonObject getScriptSettingsByDrawKey(String drawScriptKey) {
        for (DiaobanDrawItem item : drawOptions.values()) {
            if (item.getContent().equals(drawScriptKey)) {
                JsonObject settings = item.settings();
                if (settings != null && settings.has("script_settings") && settings.get("script_settings").isJsonObject()) {
                    return settings.getAsJsonObject("script_settings");
                }
                break;
            }
        }
        return new JsonObject();
    }

    public DiaobanDrawItem getDrawItem(String key) {
        return drawOptions.get(key);
    }
    
    public static void registerJavaDrawing(String text, String key, String contentText, Supplier<BaseDiaobanDrawing> factory) {
        drawOptions.put(key, new DiaobanDrawItem(text, key, contentText, new JsonObject(), factory));
//        drawingSuppliers.put(key, factory);
    }

    public static BaseDiaobanDrawing createDrawing(String key) {
        DiaobanDrawItem item = drawOptions.get(key);
        if (item == null) {
            Main.LOGGER.warn("Diaoban draw item not found for key: {}, falling back to JS drawing", key);
            return new JsDiaobanDrawing(key);
        }
        Supplier<BaseDiaobanDrawing> javaFactory = item.supplier();
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
                String id = item.get("id").getAsString();
                String text = item.get("text").getAsString();
                String content = item.get("content").getAsString();

                Supplier<BaseDiaobanDrawing> supplier = () -> new JsDiaobanDrawing(id);

                if (item.has("contentText")) {
                    drawOptions.put(id, new DiaobanDrawItem(text, content, item.get("contentText").getAsString(), item, supplier));
                } else {
                    drawOptions.put(id, new DiaobanDrawItem(text, content, "", item, supplier));
                }
            }
        } catch (Exception e) {
            Main.LOGGER.error("Failed to preload diaoban draw scripts", e);
        }
    }

    public static class DiaobanDrawItem extends ModelSelectInfo {
        private final Supplier<BaseDiaobanDrawing> supplier;
        private final JsonObject settings;

        public DiaobanDrawItem(String text, String key, String contentText, JsonObject settings, Supplier<BaseDiaobanDrawing> supplier) {
            super(text, key, contentText);
            this.supplier = supplier;
            this.settings = settings;
        }

        public Supplier<BaseDiaobanDrawing> supplier() {
            return supplier;
        }

        public JsonObject settings() {
            return settings;
        }
    }
}
