package com.fangsu.customItem.contents;

import com.fangsu.Main;
import com.fangsu.customItem.ModelSelectInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TicketBarrierContent extends BaseContent {
    private final String model;
    private final boolean filpV;

    private TicketBarrierContent(JsonObject json) {
        super(json);
        model = json.get("model").getAsString();
        filpV = json.has("flipV") && json.get("flipV").getAsBoolean();

    }

    public String getModel() {
        return model;
    }

    public boolean getFilpV() {
        return filpV;
    }

    public static TicketBarrierDisplayInfo loadDisplayInfo(String mainModel, String subModel) throws Exception {
        Map<String, Map<String, Object>> loaded = ContentResourceLoader.loadMapByPath(new ResourceLocation(mainModel));
        if (loaded == null || !loaded.containsKey(subModel)) {
            return null;
        }
        return TicketBarrierDisplayInfo.fromMap(loaded.get(subModel));
    }

    public static List<ModelSelectInfo> loadModelSelectInfos(String mainModel) {
        List<ModelSelectInfo> infos = new ArrayList<>();
        try {
            Map<String, Map<String, Object>> loaded = ContentResourceLoader.loadMapByPath(new ResourceLocation(mainModel));
            if (loaded == null) {
                return infos;
            }
            for (Map<String, Object> item : loaded.values()) {
                String text = item.get("text") instanceof String s ? s : "";
                String content = item.get("id") instanceof String s ? s : "";
                String contentText = item.get("contentText") instanceof String s ? s : null;
                if (contentText != null) infos.add(new ModelSelectInfo(text, content, contentText));
                else infos.add(new ModelSelectInfo(text, content));
            }
        } catch (Exception ignored) {
        }
        return infos;
    }

    public static class TicketBarrierDisplayInfo {
        private final String model;
        private final boolean flipV;
        private final List<TicketBarrierDoorInfo> doors;
        private final List<?> shape;
        private final List<?> collisionShape;
        private final List<?> doorCloseShape;
        private final List<?> doorCloseCollisionShape;
        private final Number gatePos;
        private final List<?> cardBox;
        private final List<?> ticketBox;

        private TicketBarrierDisplayInfo(String model, boolean flipV, List<TicketBarrierDoorInfo> doors, List<?> shape,
                                         List<?> collisionShape, List<?> doorCloseShape, List<?> doorCloseCollisionShape,
                                         Number gatePos, List<?> cardBox, List<?> ticketBox) {
            this.model = model;
            this.flipV = flipV;
            this.doors = doors;
            this.shape = shape;
            this.collisionShape = collisionShape;
            this.doorCloseShape = doorCloseShape;
            this.doorCloseCollisionShape = doorCloseCollisionShape;
            this.gatePos = gatePos;
            this.cardBox = cardBox;
            this.ticketBox = ticketBox;
        }

        public static TicketBarrierDisplayInfo fromMap(Map<String, Object> current) {
            if (current == null || !(current.get("model") instanceof String model)) return null;
            boolean flipV = current.get("flipV") instanceof Boolean b && b;
            List<TicketBarrierDoorInfo> doors = new ArrayList<>();
            if (current.get("doors") instanceof List<?> doorList) {
                for (Object door : doorList) {
                    if (door instanceof Map<?, ?> d) {
                        TicketBarrierDoorInfo doorInfo = TicketBarrierDoorInfo.fromMap(d);
                        if (doorInfo != null) doors.add(doorInfo);
                    }
                }
            }
            List<?> shape = current.get("shape") instanceof List<?> s ? s : null;
            List<?> collisionShape = current.get("collisionShape") instanceof List<?> s ? s : null;
            List<?> doorCloseShape = current.get("doorCloseShape") instanceof List<?> s ? s : null;
            List<?> doorCloseCollisionShape = current.get("doorCloseCollisionShape") instanceof List<?> s ? s : null;
            Number gatePos = current.get("gatePos") instanceof Number n ? n : null;
            List<?> cardBox = current.get("cardBox") instanceof List<?> s ? s : null;
            List<?> ticketBox = current.get("ticketBox") instanceof List<?> s ? s : null;
            return new TicketBarrierDisplayInfo(model, flipV, doors, shape, collisionShape, doorCloseShape, doorCloseCollisionShape, gatePos, cardBox, ticketBox);
        }

        public String getModel() { return model; }
        public boolean isFlipV() { return flipV; }
        public List<TicketBarrierDoorInfo> getDoors() { return doors; }
        public List<?> getShape() { return shape; }
        public List<?> getCollisionShape() { return collisionShape; }
        public List<?> getDoorCloseShape() { return doorCloseShape; }
        public List<?> getDoorCloseCollisionShape() { return doorCloseCollisionShape; }
        public Number getGatePos() { return gatePos; }
        public List<?> getCardBox() { return cardBox; }
        public List<?> getTicketBox() { return ticketBox; }
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

        public static TicketBarrierDoorInfo fromMap(Map<?, ?> baseMap) {
            if (baseMap == null) {
                return null;
            }
            Object modelObj = baseMap.get("model");
            if (!(modelObj instanceof String modelPath)) {
                return null;
            }
            boolean usePartedModel = Boolean.TRUE.equals(baseMap.get("use_parted_model"));
            boolean flipV = Boolean.TRUE.equals(baseMap.get("flipV"));
            int doorType = 1;
            Object dtObj = baseMap.get("doorType");
            if (dtObj instanceof Number num) {
                doorType = num.intValue();
            }
            List<DoorInfo> doors = new ArrayList<>();
            Object posObj = baseMap.get("pos");
            if (posObj instanceof List<?> mapPos) {
                for (Object posEntry : mapPos) {
                    if (posEntry instanceof Map<?, ?> thisMap) {
                        DoorInfo doorInfo = DoorInfo.fromMap(thisMap);
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

            public static DoorInfo fromMap(Map<?, ?> map) {
                if (map == null) {
                    return null;
                }
                Object subModelObj = map.get("subModel");
                if (!(subModelObj instanceof String subModel)) {
                    return null;
                }
                Object posListObj = map.get("pos");
                if (!(posListObj instanceof List<?> posList) || posList.isEmpty()) {
                    return null;
                }
                List<Double> pos = new ArrayList<>();
                for (Object o : posList) {
                    if (!(o instanceof Number n)) {
                        return null;
                    }
                    pos.add(n.doubleValue());
                }
                int side = 0;
                Object sideObj = map.get("side");
                if (sideObj instanceof Number n) {
                    side = n.intValue();
                }
                double step = 1;
                Object stepObj = map.get("step");
                if (stepObj instanceof Number n) {
                    step = n.doubleValue();
                }
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
}
