package com.fangsu.customItem.contents;

import com.fangsu.Main;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

public class AdvBoardContent extends BaseContent {
    public static final String[] MODEL_KEYS = new String[]{"left_top", "top", "right_top", "left", "center", "right", "left_bottom", "bottom", "right_bottom"};
    public static final String[] BAR_KEYS = new String[]{"left", "right", "top", "bottom"};

    private final String model;
    private final boolean flipV;
    private final Map<String, float[][]> shapes;
    private final Map<String, Double> faces;
    private final double widthUnit;
    private final double heightUnit;
    private final Map<String, Double> bars;
    private final Map<String, String> subModelMap;

    protected AdvBoardContent(JsonObject json) {
        super(json);

        this.model = getOrDefault(json, "model", "", JsonElement::getAsString);
        this.flipV = getOrDefault(json, "flipV", false, JsonElement::getAsBoolean);

        this.shapes = new HashMap<>();
        JsonObject shapesJson = getOrDefault(json, "shape", new JsonObject(), JsonElement::getAsJsonObject);
        for (String key : MODEL_KEYS) {
            JsonArray shapes = getOrDefault(shapesJson, key, new JsonArray(), JsonElement::getAsJsonArray);
            if (shapes.isEmpty()) {
                this.shapes.put(key, new float[0][0]);
                continue;
            }
            // 单层数组
            if (shapes.get(0).isJsonPrimitive()) {
                float[] arr = new float[]{
                        shapes.get(0).getAsJsonPrimitive().getAsFloat() / 16f,
                        shapes.get(1).getAsJsonPrimitive().getAsFloat() / 16f,
                        shapes.get(2).getAsJsonPrimitive().getAsFloat() / 16f,
                        shapes.get(3).getAsJsonPrimitive().getAsFloat() / 16f,
                        shapes.get(4).getAsJsonPrimitive().getAsFloat() / 16f,
                        shapes.get(5).getAsJsonPrimitive().getAsFloat() / 16f};
                this.shapes.put(key, new float[][]{arr});
                continue;
            }
            float[][] shapesArray = new float[shapes.size()][];
            for (int i = 0; i < shapes.size(); i++) {
                JsonArray shape = shapes.get(i).getAsJsonArray();
                if (shape.size() != 6) continue;
                float[] shapeArr = new float[]{
                        shape.get(0).getAsJsonPrimitive().getAsFloat() / 16f,
                        shape.get(1).getAsJsonPrimitive().getAsFloat() / 16f,
                        shape.get(2).getAsJsonPrimitive().getAsFloat() / 16f,
                        shape.get(3).getAsJsonPrimitive().getAsFloat() / 16f,
                        shape.get(4).getAsJsonPrimitive().getAsFloat() / 16f,
                        shape.get(5).getAsJsonPrimitive().getAsFloat() / 16f};
                shapesArray[i] = shapeArr;
            }
            this.shapes.put(key, shapesArray);
        }

        if (json.has("face")) {
            JsonObject faces = json.getAsJsonObject("face");
            this.faces = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
                Double z = entry.getValue().getAsDouble();
                this.faces.put(entry.getKey(), z);
            }
        } else this.faces = Map.of();

        this.widthUnit = getOrDefault(json, "widthUnit", 0d, JsonElement::getAsDouble);
        this.heightUnit = getOrDefault(json, "heightUnit", 0d, JsonElement::getAsDouble);

        this.bars = new HashMap<>();
        JsonObject barsJson = getOrDefault(json, "bar", new JsonObject(), JsonElement::getAsJsonObject);
        for (String key : BAR_KEYS) {
            double value = getOrDefault(barsJson, key, 0d, JsonElement::getAsDouble);
            this.bars.put(key, value);
        }
        this.subModelMap = new HashMap<>();
        JsonObject subModelJson = getOrDefault(json, "subModels", new JsonObject(), JsonElement::getAsJsonObject);
        for (String key : MODEL_KEYS) {
            String value = getOrDefault(subModelJson, key, key, JsonElement::getAsString);
            this.subModelMap.put(key, value);
        }
    }

    protected static class AdvBoardLoader extends BaseLoader {
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
                    cm.addContent(type, path, new AdvBoardContent(detailObject));
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

    public Map<String, float[][]> getShapes() {
        return shapes;
    }

    public Map<String, Double> getFaces() {
        return faces;
    }

    public double getWidthUnit() {
        return widthUnit;
    }

    public double getHeightUnit() {
        return heightUnit;
    }

    public Map<String, Double> getBars() {
        return bars;
    }

    public Map<String, String> getSubModelMap() {
        return subModelMap;
    }


    @Override
    public String toString() {
        return "AdvBoardContent_" + hashCode() + "[" +
                "id: " + getId() + ", " +
                "model: " + model + ", " +
                "flipV: " + flipV + ", " +
                "]";

    }
}
