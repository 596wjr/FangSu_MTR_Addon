package com.fangsu.customItem.contents;

import java.util.*;

public final class ScreendoorDoorContent {
    private ScreendoorDoorContent() {
    }

    public record ScreendoorDoorDisplayInfo(List<DoorInfo> doors) {
    }

    public record DoorInfo(String subModel, float step, List<?> shape) {
        public static DoorInfo fromMap(Map<?, ?> map) {
            if (!(map.get("subModel") instanceof String subModel)) return null;
            float step = map.get("step") instanceof Number n ? n.floatValue() : 0;
            List<?> shape = map.get("shape") instanceof List<?> l ? l : null;
            return new DoorInfo(subModel, step, shape);
        }
    }
}
