package com.fangsu.customItem.contents;

import com.fangsu.Main;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PidsContent extends BaseContent {
    /** 拼接（shouldSpilt）模式的 9 个子模型键，与 AdvBoardContent 保持一致 */
    public static final String[] MODEL_KEYS = new String[]{"left_top", "top", "right_top", "left", "center", "right", "left_bottom", "bottom", "right_bottom"};
    public static final String[] BAR_KEYS = new String[]{"left", "right", "top", "bottom"};

    private final String model;
    private final boolean flipV;
    private final String script;
    private final List<Integer> texSize;
    private final List<List<List<Double>>> slots;
    private final List<List<Double>> shape;
    private final List<JsonObject> extraConfigDefs;
    private final JsonObject scriptSettings;

    // ===== 拼接（shouldSpilt）字段，移植自旧版 pids.js 的宽度/高度拼接功能，参照 AdvBoard =====
    private final boolean shouldSpilt;
    private final Map<String, String> subModels;
    private final double widthUnit;
    private final double heightUnit;
    private final Map<String, Double> faces;
    private final Map<String, Double> bars;
    private final Map<String, List<List<Double>>> shapes;
    private final float[] offset;

    private PidsContent(JsonObject json) {
        super(json);
        model = json.get("model").getAsString();
        flipV = json.has("flipV") && json.get("flipV").getAsBoolean();
        script = json.has("script") ? json.get("script").getAsString() : "";

        scriptSettings = json.has("script_settings") && json.get("script_settings").isJsonObject()
                ? json.getAsJsonObject("script_settings") : new JsonObject();

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

        extraConfigDefs = new ArrayList<>();
        if (json.has("extraConfig") && json.get("extraConfig").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("extraConfig")) {
                if (el != null && el.isJsonObject()) {
                    extraConfigDefs.add(el.getAsJsonObject());
                }
            }
        }

        // ===== 拼接（shouldSpilt）字段解析，参照 AdvBoardContent =====
        this.shouldSpilt = getOrDefault(json, "shouldSpilt", false, JsonElement::getAsBoolean);
        this.widthUnit = getOrDefault(json, "widthUnit", 0d, JsonElement::getAsDouble);
        this.heightUnit = getOrDefault(json, "heightUnit", 0d, JsonElement::getAsDouble);

        this.subModels = new HashMap<>();
        JsonObject subModelJson = getOrDefault(json, "subModels", new JsonObject(), JsonElement::getAsJsonObject);
        for (String key : MODEL_KEYS) {
            String value = getOrDefault(subModelJson, key, key, JsonElement::getAsString);
            this.subModels.put(key, value);
        }

        this.bars = new HashMap<>();
        JsonObject barsJson = getOrDefault(json, "bar", new JsonObject(), JsonElement::getAsJsonObject);
        for (String key : BAR_KEYS) {
            double value = getOrDefault(barsJson, key, 0d, JsonElement::getAsDouble);
            this.bars.put(key, value);
        }

        this.faces = new HashMap<>();
        JsonObject facesJson = getOrDefault(json, "face", new JsonObject(), JsonElement::getAsJsonObject);
        for (Map.Entry<String, JsonElement> entry : facesJson.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                this.faces.put(entry.getKey(), entry.getValue().getAsDouble());
            }
        }

        this.shapes = new HashMap<>();
        // 拼接模式：shape 为对象（子模型键 → 碰撞盒列表），与 AdvBoardContent 一致
        if (json.has("shape") && json.get("shape").isJsonObject()) {
            JsonObject shapesJson = json.getAsJsonObject("shape");
            for (String key : MODEL_KEYS) {
                JsonElement el = shapesJson.get(key);
                if (el == null || !el.isJsonArray()) continue;
                List<List<Double>> boxList = new ArrayList<>();
                for (JsonElement boxEl : el.getAsJsonArray()) {
                    if (boxEl == null || !boxEl.isJsonArray()) continue;
                    List<Double> box = new ArrayList<>();
                    for (JsonElement valueElement : boxEl.getAsJsonArray()) {
                        if (valueElement != null && valueElement.isJsonPrimitive() && valueElement.getAsJsonPrimitive().isNumber()) {
                            // 与 AdvBoardContent 一致：碰撞盒坐标从像素(0~16)换算为世界单位(0~1)
                            box.add(valueElement.getAsDouble() / 16d);
                        }
                    }
                    if (!box.isEmpty()) boxList.add(box);
                }
                this.shapes.put(key, boxList);
            }
        }

        float[] parsedOffset = null;
        if (json.has("offset") && json.get("offset").isJsonArray()) {
            JsonArray offsetArr = json.getAsJsonArray("offset");
            if (offsetArr.size() >= 3) {
                parsedOffset = new float[]{
                        offsetArr.get(0).getAsFloat(),
                        offsetArr.get(1).getAsFloat(),
                        offsetArr.get(2).getAsFloat()
                };
            }
        }
        this.offset = parsedOffset;
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

    public List<JsonObject> getExtraConfigDefs() {
        return Collections.unmodifiableList(extraConfigDefs);
    }

    public JsonObject getScriptSettings() {
        return scriptSettings;
    }

    public boolean isShouldSpilt() {
        return shouldSpilt;
    }

    public Map<String, String> getSubModels() {
        return subModels;
    }

    public double getWidthUnit() {
        return widthUnit;
    }

    public double getHeightUnit() {
        return heightUnit;
    }

    public Map<String, Double> getFaces() {
        return faces;
    }

    public Map<String, Double> getBars() {
        return bars;
    }

    public Map<String, List<List<Double>>> getShapes() {
        return shapes;
    }

    public float[] getOffset() {
        return offset;
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
