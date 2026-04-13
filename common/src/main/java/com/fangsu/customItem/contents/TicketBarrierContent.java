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
