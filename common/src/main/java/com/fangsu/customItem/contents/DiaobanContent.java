package com.fangsu.customItem.contents;

import com.fangsu.Main;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiaobanContent extends BaseContent {
    private final String model;
    private final boolean flipV;
    private final int unit;
    private final double leftSpace;
    private final double rightSpace;
    private final int texSize;
    private final List<List<Double>> tex;
    private final Map<String, String> subModel;
    private final Map<String, String> doorlight;
    private final Map<String, List<Double>> shape;
    private final JsonObject scriptSettings;

    private DiaobanContent(JsonObject json) {
        super(json);
        model = json.get("model").getAsString();
        flipV = json.has("flipV") && json.get("flipV").getAsBoolean();
        unit = json.has("unit") ? json.get("unit").getAsInt() : 8;
        leftSpace = json.has("left_space") ? json.get("left_space").getAsDouble() : 0;
        rightSpace = json.has("right_space") ? json.get("right_space").getAsDouble() : 0;
        texSize = json.has("texSize") ? json.get("texSize").getAsInt() : 64;

        scriptSettings = json.has("script_settings") && json.get("script_settings").isJsonObject()
                ? json.getAsJsonObject("script_settings") : new JsonObject();

        tex = new ArrayList<>();
        if (json.has("tex") && json.get("tex").isJsonArray()) {
            for (JsonElement lineElement : json.getAsJsonArray("tex")) {
                if (lineElement == null || !lineElement.isJsonArray()) continue;
                List<Double> line = new ArrayList<>();
                for (JsonElement valueElement : lineElement.getAsJsonArray()) {
                    if (valueElement != null && valueElement.isJsonPrimitive() && valueElement.getAsJsonPrimitive().isNumber()) {
                        line.add(valueElement.getAsDouble());
                    }
                }
                if (!line.isEmpty()) {
                    tex.add(line);
                }
            }
        }

        subModel = new HashMap<>();
        if (json.has("subModel") && json.get("subModel").isJsonObject()) {
            JsonObject subModelObject = json.getAsJsonObject("subModel");
            for (Map.Entry<String, JsonElement> entry : subModelObject.entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive()) {
                    subModel.put(entry.getKey(), value.getAsString());
                }
            }
        }

        doorlight = new HashMap<>();
        if (json.has("doorlight") && json.get("doorlight").isJsonObject()) {
            JsonObject doorlightObject = json.getAsJsonObject("doorlight");
            for (Map.Entry<String, JsonElement> entry : doorlightObject.entrySet()) {
                JsonElement value = entry.getValue();
                if (value != null && value.isJsonPrimitive()) {
                    doorlight.put(entry.getKey(), value.getAsString());
                }
            }
        }

        shape = new HashMap<>();
        if (json.has("shape") && json.get("shape").isJsonObject()) {
            JsonObject shapeObject = json.getAsJsonObject("shape");
            for (Map.Entry<String, JsonElement> entry : shapeObject.entrySet()) {
                JsonElement value = entry.getValue();
                if (value == null || !value.isJsonArray()) continue;
                List<Double> box = new ArrayList<>();
                for (JsonElement boxValue : value.getAsJsonArray()) {
                    if (boxValue != null && boxValue.isJsonPrimitive() && boxValue.getAsJsonPrimitive().isNumber()) {
                        box.add(boxValue.getAsDouble());
                    }
                }
                if (!box.isEmpty()) {
                    shape.put(entry.getKey(), box);
                }
            }
        }
    }

    public String getModel() {
        return model;
    }

    public boolean isFlipV() {
        return flipV;
    }

    public int getUnit() {
        return unit;
    }

    public double getLeftSpace() {
        return leftSpace;
    }

    public double getRightSpace() {
        return rightSpace;
    }

    public int getTexSize() {
        return texSize;
    }

    public List<List<Double>> getTex() {
        return tex;
    }

    public Map<String, String> getSubModel() {
        return subModel;
    }

    public Map<String, String> getDoorlight() {
        return doorlight;
    }

    public Map<String, List<Double>> getShape() {
        return shape;
    }

    public JsonObject getScriptSettings() {
        return scriptSettings;
    }

    protected static class DiaobanLoader extends BaseLoader {
        public static void load(String type, String path, JsonObject content) {
            ContentManager cm = ContentManager.getInstance();
            for (Map.Entry<String, JsonElement> entry : content.entrySet()) {
                JsonElement entryValue = entry.getValue();
                if (entryValue == null || !entryValue.isJsonArray()) {
                    Main.LOGGER.warn("Failed to load content {} of {}({}): JSON is null or empty", entry.getKey(), type, path);
                    continue;
                }
                JsonArray entryArray = entryValue.getAsJsonArray();
                for (int i = 0; i < entryArray.size(); i++) {
                    JsonElement detailElement = entryArray.get(i);
                    if (detailElement == null || !detailElement.isJsonObject()) {
                        Main.LOGGER.warn("Failed to load content index {} in {} of {}({}): JSON is null or empty", i, entry.getKey(), type, path);
                        continue;
                    }
                    cm.addContent(type, path, new DiaobanContent(detailElement.getAsJsonObject()));
                }
            }
        }
    }
}
