package com.fangsu.customItem.contents;

import com.fangsu.customItem.ModelSelectInfo;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public final class ScreendoorDoorContent {
    private ScreendoorDoorContent() {
    }

    public static ScreendoorDoorDisplayInfo loadDisplayInfo(String mainModel, String subModel, int doorSide) {
        String path = switch (doorSide) {
            case 0 -> "door.left";
            case 1 -> "door.right";
            default -> "door.flex";
        };
        Map<String, Map<String, Object>> loaded = ContentResourceLoader.loadMapByPath(new ResourceLocation(mainModel), path);
        if (loaded == null || !loaded.containsKey(subModel)) return null;
        Map<String, Object> current = loaded.get(subModel);
        List<DoorInfo> doors = new ArrayList<>();
        if (current.get("doors") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    DoorInfo info = DoorInfo.fromMap(m);
                    if (info != null) doors.add(info);
                }
            }
        }
        return new ScreendoorDoorDisplayInfo(doors);
    }

    public static List<ModelSelectInfo> loadModelSelectInfos(String mainModel, int doorSide) {
        String path = switch (doorSide) {
            case 0 -> "door.left";
            case 1 -> "door.right";
            default -> "door.flex";
        };
        return ContentResourceLoader.loadModelSelectInfos(new ResourceLocation(mainModel), path);
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
