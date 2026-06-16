package com.fangsu.customItem.contents;

import com.fangsu.Main;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

public class ScreendoorDoorContent extends BaseContent {
    private final String text;
    private final String contentText;
    private final String side;
    private final List<DoorInfo> doors;

    private ScreendoorDoorContent(JsonObject json, String side) {
        super(side + "." + json.get("id").getAsString());
        this.side = side;
        this.text = getOrDefault(json, "text", "", JsonElement::getAsString);
        this.contentText = getOrDefault(json, "contentText", (String) null, JsonElement::getAsString);
        this.doors = parseDoors(json);
    }

    private static List<DoorInfo> parseDoors(JsonObject json) {
        List<DoorInfo> result = new ArrayList<>();
        if (!json.has("doors") || !json.get("doors").isJsonArray()) return result;
        JsonArray arr = json.getAsJsonArray("doors");
        for (int i = 0; i < arr.size(); i++) {
            JsonElement elem = arr.get(i);
            if (!elem.isJsonObject()) continue;
            DoorInfo info = DoorInfo.fromJson(elem.getAsJsonObject());
            if (info != null) result.add(info);
        }
        return result;
    }

    public String getText() {
        return text;
    }

    public String getContentText() {
        return contentText;
    }

    public String getSide() {
        return side;
    }

    public List<DoorInfo> getDoors() {
        return doors;
    }

    @Override
    public String toString() {
        return "ScreendoorDoorContent_" + hashCode() + "[" +
                "id: " + getId() + ", " +
                "side: " + side + ", " +
                "doors: " + doors.size() +
                "]";
    }

    public static class ScreendoorDoorLoader {
        public static void load(String type, String path, JsonObject content) {
            ContentManager cm = ContentManager.getInstance();
            if (!content.has("door") || !content.get("door").isJsonObject()) return;
            JsonObject door = content.getAsJsonObject("door");
            for (String side : new String[]{"left", "right", "flex"}) {
                if (!door.has(side) || !door.get(side).isJsonArray()) continue;
                JsonArray arr = door.getAsJsonArray(side);
                for (int i = 0; i < arr.size(); i++) {
                    JsonElement elem = arr.get(i);
                    if (elem == null || !elem.isJsonObject()) {
                        Main.LOGGER.warn("Failed to load screendoor door entry index {} in {}: not an object", i, side);
                        continue;
                    }
                    cm.addContent(type, path, new ScreendoorDoorContent(elem.getAsJsonObject(), side));
                }
            }
        }
    }

    public record DoorInfo(String subModel, float step, List<?> shape) {
        public static DoorInfo fromJson(JsonObject json) {
            if (!json.has("subModel")) return null;
            String subModel = json.get("subModel").getAsString();
            float step = json.has("step") ? json.get("step").getAsFloat() : 0;
            List<?> shape = json.has("shape") && json.get("shape").isJsonArray()
                    ? parseShapeArray(json.getAsJsonArray("shape")) : null;
            return new DoorInfo(subModel, step, shape);
        }

        public static DoorInfo fromMap(Map<?, ?> map) {
            if (!(map.get("subModel") instanceof String subModel)) return null;
            float step = map.get("step") instanceof Number n ? n.floatValue() : 0;
            List<?> shape = map.get("shape") instanceof List<?> l ? l : null;
            return new DoorInfo(subModel, step, shape);
        }

        private static List<List<Double>> parseShapeArray(JsonArray arr) {
            List<List<Double>> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonElement elem = arr.get(i);
                if (!elem.isJsonArray()) continue;
                List<Double> inner = new ArrayList<>();
                for (int j = 0; j < elem.getAsJsonArray().size(); j++) {
                    inner.add(elem.getAsJsonArray().get(j).getAsDouble());
                }
                result.add(inner);
            }
            return result;
        }
    }
}
