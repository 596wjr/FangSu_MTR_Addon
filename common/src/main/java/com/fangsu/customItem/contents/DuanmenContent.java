package com.fangsu.customItem.contents;

import com.fangsu.Main;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DuanmenContent extends BaseContent {
    private final String model;
    private final boolean filpV;
    private final int[] doorPos;
    private final Map<String, String> subModels;
    private final double doorAngle;
    private final Map<String, List<List<Integer>>> shape;

    protected DuanmenContent(JsonObject json) {
        super(json);

        model = json.get("model").getAsString();
        filpV = json.has("flipV") && json.get("flipV").getAsBoolean();
        if (json.has("doorPos")) {
            JsonArray doorPosArray = json.get("doorPos").getAsJsonArray();
            doorPos = new int[doorPosArray.size()];
            for (int i = 0; i < doorPosArray.size(); i++) {
                doorPos[i] = doorPosArray.get(i).getAsInt();
            }
        } else {
            doorPos = new int[]{0, 0, 0};
        }
        subModels = new HashMap<>();
        if (json.has("subModels")) {
            JsonObject subModelsObject = json.get("subModels").getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : subModelsObject.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue().getAsString();
                subModels.put(key, value);
            }
        }
        doorAngle = json.get("doorAngle").getAsDouble();
        shape = new HashMap<>();
        if (json.has("shape")) {
            JsonObject shapeObject = json.get("shape").getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : shapeObject.entrySet()) {
                String key = entry.getKey();
                List<List<Integer>> shapes = getShapes(entry);
                shape.put(key, shapes);
            }
        }
    }

    private static @NotNull List<List<Integer>> getShapes(Map.Entry<String, JsonElement> entry) {
        JsonElement value = entry.getValue();
        List<List<Integer>> shapes = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement shapeJson : value.getAsJsonArray()) {
                List<Integer> shape = new ArrayList<>();
                if (shapeJson.isJsonArray()) {
                    JsonArray shapeArray = shapeJson.getAsJsonArray();
                    for (JsonElement shapeElement : shapeArray) {
                        shape.add(shapeElement.getAsInt());
                    }
                }
                shapes.add(shape);
            }
        }
        return shapes;
    }

    protected static class TicketBarrierLoader extends BaseLoader {
        public static void load(String type, String path, JsonObject content) {
            ContentManager cm = ContentManager.getInstance();
            for (Map.Entry<String, JsonElement> entry : content.entrySet()) {
                String entryKey = entry.getKey();
                JsonElement entryValue = entry.getValue();
                if (entryValue == null || !entryValue.isJsonArray()) {
                    Main.LOGGER.warn("Failed to load content {} of {}({}): JSON is null or empty", entryKey, type, path);
                    continue;
                }
                JsonArray entryArray = entryValue.getAsJsonArray();
                for (int i = 0; i < entryArray.size(); i++) {
                    JsonElement detailElement = entryArray.get(i);
                    if (detailElement == null || !detailElement.isJsonObject()) {
                        Main.LOGGER.warn("Failed to load content index {} in {} of {}({}): JSON is null or empty", i, entryKey, type, path);
                        continue;
                    }
                    JsonObject detailObject = detailElement.getAsJsonObject();
                    cm.addContent(type, path, new DuanmenContent(detailObject));
                }

            }
        }
    }
}
