package com.fangsu.customItem.contents;

import com.fangsu.Main;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TicketBarrierContent extends BaseContent {
    private final String model;
    private final boolean filpV;
    private final List<TicketBarrierDoorInfo> doors;
    private final List<List<Double>> shape;
    private final List<List<Double>> collisionShape;
    private final List<List<Double>> doorCloseShape;
    private final List<List<Double>> doorCloseCollisionShape;
    private final Double gatePos;
    private final List<Double> cardBox;
    private final List<Double> ticketBox;
    private final TicketBarrierConnectType connectType;

    private TicketBarrierContent(JsonObject json) {
        super(json);
        model = json.get("model").getAsString();
        filpV = json.has("flipV") && json.get("flipV").getAsBoolean();
        doors = parseDoors(json.get("doors"));
        shape = parseBoxList(json.get("shape"));
        collisionShape = parseBoxList(json.get("collisionShape"));
        doorCloseShape = parseBoxList(json.get("doorCloseShape"));
        doorCloseCollisionShape = parseBoxList(json.get("doorCloseCollisionShape"));
        gatePos = json.has("gatePos") ? json.get("gatePos").getAsDouble() : null;
        cardBox = parseSingleBox(json.get("cardBox"));
        ticketBox = parseSingleBox(json.get("ticketBox"));
        connectType = TicketBarrierConnectType.fromInt(json.has("connectType") ? json.get("connectType").getAsInt() : 0);
    }

    public String getModel() {
        return model;
    }

    public boolean getFilpV() {
        return filpV;
    }

    public List<TicketBarrierDoorInfo> getDoors() {
        return doors;
    }

    public List<List<Double>> getShape() {
        return shape;
    }

    public List<List<Double>> getCollisionShape() {
        return collisionShape;
    }

    public List<List<Double>> getDoorCloseShape() {
        return doorCloseShape;
    }

    public List<List<Double>> getDoorCloseCollisionShape() {
        return doorCloseCollisionShape;
    }

    public Double getGatePos() {
        return gatePos;
    }

    public List<Double> getCardBox() {
        return cardBox;
    }

    public List<Double> getTicketBox() {
        return ticketBox;
    }

    public TicketBarrierConnectType getConnectType() {
        return connectType;
    }

    private static List<TicketBarrierDoorInfo> parseDoors(JsonElement doorsElement) {
        List<TicketBarrierDoorInfo> parsed = new ArrayList<>();
        if (doorsElement == null || !doorsElement.isJsonArray()) return parsed;
        for (JsonElement doorElement : doorsElement.getAsJsonArray()) {
            if (doorElement == null || !doorElement.isJsonObject()) continue;
            TicketBarrierDoorInfo info = TicketBarrierDoorInfo.fromJson(doorElement.getAsJsonObject());
            if (info != null) parsed.add(info);
        }
        return parsed;
    }

    @Override
    public String toString() {
        return "TicketBarrierContent [model=" + model + ", filpV=" + filpV + ", doors=" + doors + ", shape=" + shape + ", collisionShape=" + collisionShape + ", doorCloseShape=" + doorCloseShape + ", doorCloseCollisionShape=" + doorCloseCollisionShape + "]";
    }

    private static List<List<Double>> parseBoxList(JsonElement element) {
        List<List<Double>> parsed = new ArrayList<>();
        if (element == null || !element.isJsonArray()) return parsed;
        for (JsonElement boxElement : element.getAsJsonArray()) {
            if (boxElement == null || !boxElement.isJsonArray()) continue;
            List<Double> box = new ArrayList<>();
            for (JsonElement valueElement : boxElement.getAsJsonArray()) {
                if (valueElement != null && valueElement.isJsonPrimitive() && valueElement.getAsJsonPrimitive().isNumber()) {
                    box.add(valueElement.getAsDouble());
                }
            }
            if (!box.isEmpty()) {
                parsed.add(box);
            }
        }
        return parsed;
    }

    private static List<Double> parseSingleBox(JsonElement element) {
        if (element == null || !element.isJsonArray()) return null;
        List<Double> parsed = new ArrayList<>();
        for (JsonElement valueElement : element.getAsJsonArray()) {
            if (valueElement != null && valueElement.isJsonPrimitive() && valueElement.getAsJsonPrimitive().isNumber()) {
                parsed.add(valueElement.getAsDouble());
            }
        }
        return parsed.isEmpty() ? null : parsed;
    }

    public static class TicketBarrierDoorInfo {
        private final String model;
        private final boolean usePartedModel;
        private final boolean flipV;
        private final int doorType;
        private final List<DoorInfo> doors;

        private TicketBarrierDoorInfo(String model, boolean usePartedModel, boolean flipV, int doorType, List<DoorInfo> doors) {
            this.model = model;
            this.usePartedModel = usePartedModel;
            this.flipV = flipV;
            this.doorType = doorType;
            this.doors = doors;
        }

        public static TicketBarrierDoorInfo fromJson(JsonObject json) {
            if (json == null || !json.has("model")) {
                return null;
            }
            String modelPath = json.get("model").getAsString();
            boolean usePartedModel = json.has("use_parted_model") && json.get("use_parted_model").getAsBoolean();
            boolean flipV = json.has("flipV") && json.get("flipV").getAsBoolean();
            int doorType = json.has("doorType") ? json.get("doorType").getAsInt() : 1;
            List<DoorInfo> doors = new ArrayList<>();
            if (json.has("pos") && json.get("pos").isJsonArray()) {
                for (JsonElement posEntry : json.getAsJsonArray("pos")) {
                    if (posEntry != null && posEntry.isJsonObject()) {
                        DoorInfo doorInfo = DoorInfo.fromJson(posEntry.getAsJsonObject());
                        if (doorInfo != null) {
                            doors.add(doorInfo);
                        }
                    }
                }
            }
            return new TicketBarrierDoorInfo(modelPath, usePartedModel, flipV, doorType, doors);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("model", model);
            map.put("use_parted_model", usePartedModel);
            map.put("flipV", flipV);
            map.put("doorType", doorType);
            List<Object> doorList = new ArrayList<>();
            for (DoorInfo doorInfo : doors) {
                doorList.add(doorInfo.toMap());
            }
            map.put("pos", doorList);
            return map;
        }

        public String getModel() {
            return model;
        }

        public boolean isUsePartedModel() {
            return usePartedModel;
        }

        public boolean isFlipV() {
            return flipV;
        }

        public int getDoorType() {
            return doorType;
        }

        public List<DoorInfo> getDoors() {
            return doors;
        }

        public static class DoorInfo {
            private final String subModel;
            private final List<Double> pos;
            private final int side;
            private final double step;

            private DoorInfo(String subModel, List<Double> pos, int side, double step) {
                this.subModel = subModel;
                this.pos = pos;
                this.side = side;
                this.step = step;
            }

            public static DoorInfo fromJson(JsonObject json) {
                if (json == null || !json.has("subModel")) {
                    return null;
                }
                String subModel = json.get("subModel").getAsString();
                if (!json.has("pos") || !json.get("pos").isJsonArray()) {
                    return null;
                }
                List<Double> pos = new ArrayList<>();
                for (JsonElement posElement : json.getAsJsonArray("pos")) {
                    if (posElement == null || !posElement.isJsonPrimitive() || !posElement.getAsJsonPrimitive().isNumber()) {
                        return null;
                    }
                    pos.add(posElement.getAsDouble());
                }
                if (pos.isEmpty()) {
                    return null;
                }
                int side = json.has("side") ? json.get("side").getAsInt() : 0;
                double step = json.has("step") ? json.get("step").getAsDouble() : 1;
                return new DoorInfo(subModel, pos, side, step);
            }

            public Map<String, Object> toMap() {
                Map<String, Object> map = new HashMap<>();
                map.put("subModel", subModel);
                map.put("pos", new ArrayList<>(pos));
                map.put("side", side);
                map.put("step", step);
                return map;
            }

            public String getSubModel() {
                return subModel;
            }

            public List<Double> getPos() {
                return pos;
            }

            public int getSide() {
                return side;
            }

            public double getStep() {
                return step;
            }
        }
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
                    cm.addContent(type, path, new TicketBarrierContent(detailObject));
                }

            }
        }
    }

    public enum TicketBarrierConnectType {
        NONE, LEFT, RIGHT;

        public static TicketBarrierConnectType fromInt(int i) {
            return switch (i) {
                case 1 -> LEFT;
                case 2 -> RIGHT;
                default -> NONE;
            };
        }
    }
}
