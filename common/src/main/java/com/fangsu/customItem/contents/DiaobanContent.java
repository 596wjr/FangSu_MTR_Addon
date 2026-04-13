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

    private DiaobanContent(JsonObject json) {
        super(json);
        model = json.get("model").getAsString();
        flipV = json.has("flipV") && json.get("flipV").getAsBoolean();
    }

    public String getModel() {
        return model;
    }

    public boolean isFlipV() {
        return flipV;
    }

    public static class DiaobanDisplayInfo {
        private final String model;
        private final boolean flipV;
        private final int unit;
        private final double leftSpace;
        private final double rightSpace;
        private final int texSize;
        private final List<List<Double>> tex;
        private final Map<String, String> subModel;
        private final Map<String, Object> doorlight;
        private final Map<String, List<Double>> shape;

        private DiaobanDisplayInfo(String model, boolean flipV, int unit, double leftSpace, double rightSpace, int texSize,
                                   List<List<Double>> tex, Map<String, String> subModel, Map<String, Object> doorlight,
                                   Map<String, List<Double>> shape) {
            this.model = model;
            this.flipV = flipV;
            this.unit = unit;
            this.leftSpace = leftSpace;
            this.rightSpace = rightSpace;
            this.texSize = texSize;
            this.tex = tex;
            this.subModel = subModel;
            this.doorlight = doorlight;
            this.shape = shape;
        }

        public static DiaobanDisplayInfo fromMap(Map<String, Object> current) {
            if (current == null || !(current.get("model") instanceof String model)) return null;

            boolean flipV = current.get("flipV") instanceof Boolean b && b;
            int unit = current.get("unit") instanceof Number n ? n.intValue() : 8;
            double leftSpace = current.get("left_space") instanceof Number n ? n.doubleValue() : 0;
            double rightSpace = current.get("right_space") instanceof Number n ? n.doubleValue() : 0;
            int texSize = current.get("texSize") instanceof Number n ? n.intValue() : 64;

            List<List<Double>> tex = new ArrayList<>();
            if (current.get("tex") instanceof List<?> texRaw) {
                for (Object line : texRaw) {
                    if (!(line instanceof List<?> lineList)) continue;
                    List<Double> parsed = new ArrayList<>();
                    for (Object v : lineList) {
                        if (v instanceof Number n) parsed.add(n.doubleValue());
                    }
                    if (!parsed.isEmpty()) tex.add(parsed);
                }
            }

            Map<String, String> subModel = new HashMap<>();
            if (current.get("subModel") instanceof Map<?, ?> m) {
                if (m.get("left") instanceof String s) subModel.put("left", s);
                if (m.get("center") instanceof String s) subModel.put("center", s);
                if (m.get("right") instanceof String s) subModel.put("right", s);
            }

            Map<String, Object> doorlight = new HashMap<>();
            if (current.get("doorlight") instanceof Map<?, ?> m) {
                if (m.get("on") instanceof String s) doorlight.put("on", s);
                if (m.get("off") instanceof String s) doorlight.put("off", s);
                if (m.get("type") instanceof String s) doorlight.put("type", s);
            }

            Map<String, List<Double>> shape = new HashMap<>();
            if (current.get("shape") instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> entry : m.entrySet()) {
                    if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof List<?> list)) continue;
                    List<Double> parsed = new ArrayList<>();
                    for (Object v : list) {
                        if (v instanceof Number n) parsed.add(n.doubleValue());
                    }
                    if (parsed.size() == 6) shape.put(key, parsed);
                }
            }

            return new DiaobanDisplayInfo(model, flipV, unit, leftSpace, rightSpace, texSize, tex, subModel, doorlight, shape);
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

        public Map<String, Object> getDoorlight() {
            return doorlight;
        }

        public Map<String, List<Double>> getShape() {
            return shape;
        }
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
