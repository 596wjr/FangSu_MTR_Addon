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
    private final String script;
    private final List<Integer> texSize;
    private final List<List<List<Double>>> slots;
    private final List<List<Double>> shape;

    private PidsContent(JsonObject json) {
        super(json);
        model = json.get("model").getAsString();
        flipV = json.has("flipV") && json.get("flipV").getAsBoolean();
        script = json.has("script") ? json.get("script").getAsString() : "";

        texSize = new ArrayList<>();
        if (json.has("texSize") && json.get("texSize").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("texSize")) {
                if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                    texSize.add(element.getAsInt());
                }
            }
        }

        slots = new ArrayList<>();
        if (json.has("slots") && json.get("slots").isJsonArray()) {
            for (JsonElement slotElement : json.getAsJsonArray("slots")) {
                if (slotElement == null || !slotElement.isJsonArray()) continue;
                List<List<Double>> quad = new ArrayList<>();
                for (JsonElement pointElement : slotElement.getAsJsonArray()) {
                    if (pointElement == null || !pointElement.isJsonArray()) continue;
                    List<Double> point = new ArrayList<>();
                    for (JsonElement valueElement : pointElement.getAsJsonArray()) {
                        if (valueElement != null && valueElement.isJsonPrimitive() && valueElement.getAsJsonPrimitive().isNumber()) {
                            point.add(valueElement.getAsDouble());
                        }
                    }
                    if (point.size() == 3) {
                        quad.add(point);
                    }
                }
                if (!quad.isEmpty()) {
                    slots.add(quad);
                }
            }
        }

        shape = new ArrayList<>();
        if (json.has("shape") && json.get("shape").isJsonArray()) {
            for (JsonElement shapeElement : json.getAsJsonArray("shape")) {
                if (shapeElement == null || !shapeElement.isJsonArray()) continue;
                List<Double> box = new ArrayList<>();
                for (JsonElement valueElement : shapeElement.getAsJsonArray()) {
                    if (valueElement != null && valueElement.isJsonPrimitive() && valueElement.getAsJsonPrimitive().isNumber()) {
                        box.add(valueElement.getAsDouble());
                    }
                }
                if (!box.isEmpty()) {
                    shape.add(box);
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

    public String getScript() {
        return script;
    }

    public List<Integer> getTexSize() {
        return texSize;
    }

    public List<List<List<Double>>> getSlots() {
        return slots;
    }

    public List<List<Double>> getShape() {
        return shape;
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
