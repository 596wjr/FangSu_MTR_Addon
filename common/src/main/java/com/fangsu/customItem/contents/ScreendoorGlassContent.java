package com.fangsu.customItem.contents;

import com.fangsu.customItem.ModelSelectInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ScreendoorGlassContent {
    private ScreendoorGlassContent() {
    }

    public static Map<String, Map<String, Object>> loadLeftEntries(String mainModel) {
        return ContentResourceLoader.loadMapByPath(new ResourceLocation(mainModel), "glass.left");
    }

    public static Map<String, Map<String, Object>> loadRightEntries(String mainModel) {
        return ContentResourceLoader.loadMapByPath(new ResourceLocation(mainModel), "glass.right");
    }

    public static List<ModelSelectInfo> loadLeftModelSelectInfos(String mainModel) {
        return ContentResourceLoader.loadModelSelectInfos(new ResourceLocation(mainModel), "glass.left");
    }

    public static List<ModelSelectInfo> loadRightModelSelectInfos(String mainModel) {
        return ContentResourceLoader.loadModelSelectInfos(new ResourceLocation(mainModel), "glass.right");
    }

    public static List<JsonObject> loadAutoCommands(String mainModel, String autoKey) {
        List<JsonObject> commands = new ArrayList<>();
        JsonObject root = ContentResourceLoader.loadRoot(new ResourceLocation(mainModel));
        if (root == null || !root.has("glass")) return commands;
        JsonObject glass = root.getAsJsonObject("glass");
        if (!glass.has(autoKey) || !glass.get(autoKey).isJsonArray()) return commands;
        JsonArray arr = glass.getAsJsonArray(autoKey);
        for (int i = 0; i < arr.size(); i++) {
            if (arr.get(i).isJsonObject()) commands.add(arr.get(i).getAsJsonObject());
        }
        return commands;
    }

    public static MainModelInfo loadMainModelInfo(String mainModel) {
        JsonObject root = ContentResourceLoader.loadRoot(new ResourceLocation(mainModel));
        if (root == null || !root.has("model")) return null;
        String model = root.get("model").getAsString();
        boolean flipV = root.has("flipV") && root.get("flipV").getAsBoolean();
        return new MainModelInfo(model, flipV);
    }

    public record MainModelInfo(String model, boolean flipV) {
    }
}
