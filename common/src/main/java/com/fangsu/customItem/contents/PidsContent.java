package com.fangsu.customItem.contents;

import com.fangsu.Main;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PidsContent extends BaseContent {
    private final String model;
    private final boolean flipV;

    private PidsContent(JsonObject json) {
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

    public static class PidsDisplayInfo {
        private final String model;
        private final boolean flipV;
        private final String script;
        private final List<Integer> texSize;
        private final List<List<List<Double>>> slots;
        private final List<?> shape;

        private PidsDisplayInfo(String model, boolean flipV, String script, List<Integer> texSize, List<List<List<Double>>> slots, List<?> shape) {
            this.model = model;
            this.flipV = flipV;
            this.script = script;
            this.texSize = texSize;
            this.slots = slots;
            this.shape = shape;
        }

        public static PidsDisplayInfo fromMap(Map<String, Object> current) {
            if (current == null || !(current.get("model") instanceof String model)) return null;
            boolean flipV = current.get("flipV") instanceof Boolean b && b;
            String script = current.get("script") instanceof String s ? s : "";

            List<Integer> texSize = new ArrayList<>();
            if (current.get("texSize") instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Number n) texSize.add(n.intValue());
                }
            }

            List<List<List<Double>>> slots = new ArrayList<>();
            if (current.get("slots") instanceof List<?> slotList) {
                for (Object slot : slotList) {
                    if (!(slot instanceof List<?> quadList)) continue;
                    List<List<Double>> quad = new ArrayList<>();
                    for (Object point : quadList) {
                        if (!(point instanceof List<?> pointRaw)) continue;
                        List<Double> pos = new ArrayList<>();
                        for (Object value : pointRaw) {
                            if (value instanceof Number n) pos.add(n.doubleValue());
                        }
                        if (pos.size() == 3) quad.add(pos);
                    }
                    if (!quad.isEmpty()) slots.add(quad);
                }
            }

            List<?> shape = current.get("shape") instanceof List<?> s ? s : null;
            return new PidsDisplayInfo(model, flipV, script, texSize, slots, shape);
        }

        public String getModel() {
            return model;
        }

        public boolean isFlipV() {
            return flipV;
        }

        public String getScript() {
            return script;
        }

        public List<Integer> getTexSize() {
            return texSize;
        }

        public List<List<List<Double>>> getSlots() {
            return slots;
        }

        public List<?> getShape() {
            return shape;
        }
    }

    protected static class PidsLoader extends BaseLoader {
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
                    cm.addContent(type, path, new PidsContent(detailElement.getAsJsonObject()));
                }
            }
        }
    }
}
