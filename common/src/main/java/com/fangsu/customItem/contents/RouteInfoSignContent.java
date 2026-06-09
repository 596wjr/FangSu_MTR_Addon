package com.fangsu.customItem.contents;

import com.fangsu.Main;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RouteInfoSignContent extends BaseContent {
    private final String model;
    private final boolean flipV;
    private final String script;

    private final float[][] shape;
    private final float[][][] slots;
    private final int[] texSize;
    private final List<JsonObject> extraConfigDefs;
    private final JsonObject scriptSettings;

    private RouteInfoSignContent(JsonObject json) {
        super(json);

        this.model = json.get("model").getAsString();
        this.flipV = json.has("flipV") && json.get("flipV").getAsBoolean();
        this.script = json.get("script").getAsString();

        scriptSettings = json.has("script_settings") && json.get("script_settings").isJsonObject()
                ? json.getAsJsonObject("script_settings") : new JsonObject();

        if (json.has("shape") && json.get("shape").isJsonArray()) {
            JsonArray shapeArray1 = json.get("shape").getAsJsonArray();
            float[][] shapes = new float[shapeArray1.size()][];
            for (int i = 0; i < shapeArray1.size(); i++) {
                float[] shape = new float[6];
                JsonElement shapeElement = shapeArray1.get(i);
                if (shapeElement.isJsonArray()) {
                    JsonArray shapeArray2 = shapeElement.getAsJsonArray();
                    for (int j = 0; j < 6; j++) {
                        shape[j] = shapeArray2.get(j).getAsFloat();
                    }
                }
                shapes[i] = shape;
            }
            this.shape = shapes;
        } else {
            this.shape = new float[0][0];
        }
        if (json.has("slots") && json.get("slots").isJsonArray()) {
            JsonArray slotsArray1 = json.get("slots").getAsJsonArray();
            float[][][] slots = new float[slotsArray1.size()][][];
            for (int i = 0; i < slotsArray1.size(); i++) {
                JsonElement slotElement2 = slotsArray1.get(i);
                if (slotElement2.isJsonArray()) {
                    JsonArray slotArray2 = slotElement2.getAsJsonArray();
                    float[][] slot = new float[5][];
                    for (int j = 0; j < 5; j++) {
                        if (j == 4) {
                            if (slotArray2.size() < 5) {
                                slot[j] = new float[]{0, 0, 1, 1};
                                continue;
                            } else {
                                JsonElement slotElement3 = slotArray2.get(j);
                                if (slotElement3.isJsonArray()) {
                                    JsonArray slotArray3 = slotElement3.getAsJsonArray();
                                    float[] slotPos = new float[4];
                                    for (int k = 0; k < 4; k++) {
                                        slotPos[k] = slotArray3.get(k).getAsFloat();
                                    }
                                    slot[j] = slotPos;
                                }
                            }
                            continue;
                        }
                        JsonElement slotElement3 = slotArray2.get(j);
                        if (slotElement3.isJsonArray()) {
                            JsonArray slotArray3 = slotElement3.getAsJsonArray();
                            float[] slotPos = new float[3];
                            for (int k = 0; k < 3; k++) {
                                slotPos[k] = slotArray3.get(k).getAsFloat();
                            }
                            slot[j] = slotPos;
                        }
                    }
                    slots[i] = slot;
                }
            }
            this.slots = slots;
        } else {
            this.slots = new float[0][0][0];
        }
        if (json.has("texSize") && json.get("texSize").isJsonArray()) {
            JsonArray texSizeArray = json.get("texSize").getAsJsonArray();
            int[] texSize = new int[2];
            for (int i = 0; i < 2; i++) {
                texSize[i] = texSizeArray.get(i).getAsInt();
            }
            this.texSize = texSize;
        } else {
            this.texSize = new int[]{0, 0};
        }

        extraConfigDefs = new ArrayList<>();
        if (json.has("extraConfig") && json.get("extraConfig").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("extraConfig")) {
                if (el != null && el.isJsonObject()) {
                    extraConfigDefs.add(el.getAsJsonObject());
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

    public float[][] getShape() {
        return shape;
    }

    public float[][][] getSlots() {
        return slots;
    }

    public int[] getTexSize() {
        return texSize;
    }

    public List<JsonObject> getExtraConfigDefs() {
        return Collections.unmodifiableList(extraConfigDefs);
    }

    public JsonObject getScriptSettings() {
        return scriptSettings;
    }

    protected static class RouteInfoSignLoader extends BaseLoader {
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
                    cm.addContent(type, path, new RouteInfoSignContent(detailObject));
                }

            }
        }
    }
}
