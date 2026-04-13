package com.fangsu.utils;

import com.fangsu.customItem.CustomItems;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.contents.*;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ContentInfoUtil {
    private ContentInfoUtil() {
    }

    public static PidsContent.PidsDisplayInfo getPidsDisplayInfo(String mainModel, String subModel) {
        return PidsContent.PidsDisplayInfo.fromMap(CustomItems.getContentInfo(mainModel, "content", subModel));
    }

    public static DiaobanContent.DiaobanDisplayInfo getDiaobanDisplayInfo(String mainModel, String subModel) {
        return DiaobanContent.DiaobanDisplayInfo.fromMap(CustomItems.getContentInfo(mainModel, "content", subModel));
    }

    public static TicketBarrierContent.TicketBarrierDisplayInfo getTicketBarrierDisplayInfo(String mainModel, String subModel) {
        return TicketBarrierContent.TicketBarrierDisplayInfo.fromMap(CustomItems.getContentInfo(mainModel, "", subModel));
    }

    public static SignContent.SignDisplayInfo getSignDisplayInfo(String mainModel, String subModel) {
        return SignContent.SignDisplayInfo.fromMap(CustomItems.getContentInfo(mainModel, "common", subModel));
    }

    public static SignOnWallContent.SignOnWallDisplayInfo getSignOnWallDisplayInfo(String mainModel, String subModel) {
        return SignOnWallContent.SignOnWallDisplayInfo.fromMap(CustomItems.getContentInfo(mainModel, "on_wall", subModel));
    }

    public static ScreendoorDoorContent.ScreendoorDoorDisplayInfo getScreendoorDisplayInfo(String mainModel, String subModel, int doorSide) {
        Map<String, Object> current = CustomItems.getContentInfo(mainModel, getScreendoorContentPath(doorSide), subModel);
        if (current == null) return null;
        List<ScreendoorDoorContent.DoorInfo> doors = new ArrayList<>();
        if (current.get("doors") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    ScreendoorDoorContent.DoorInfo info = ScreendoorDoorContent.DoorInfo.fromMap(m);
                    if (info != null) doors.add(info);
                }
            }
        }
        return new ScreendoorDoorContent.ScreendoorDoorDisplayInfo(doors);
    }

    public static String getScreendoorContentPath(int doorSide) {
        return switch (doorSide) {
            case 0 -> "door.left";
            case 1 -> "door.right";
            default -> "door.flex";
        };
    }

    public static void preloadByType(String contentType, List<ModelSelectInfo> infos) {
        if (infos == null || infos.isEmpty()) return;
        for (ModelSelectInfo info : infos) {
            preloadByType(contentType, info.content());
        }
    }

    public static void preloadByType(String contentType, String modelPath) {
        ResourceLocation location = new ResourceLocation(modelPath);
        switch (contentType) {
            case "ticketBarrier" -> ContentResourceLoader.loadMapByPath(location, "");
            case "pids", "diaoban" -> ContentResourceLoader.loadMapByPath(location, "content");
            case "screendoor" -> {
                ContentResourceLoader.loadMapByPath(location, "door.left");
                ContentResourceLoader.loadMapByPath(location, "door.right");
                ContentResourceLoader.loadMapByPath(location, "door.flex");
                ContentResourceLoader.loadMapByPath(location, "glass.left");
                ContentResourceLoader.loadMapByPath(location, "glass.right");
            }
            case "sign" -> {
                ContentResourceLoader.loadMapByPath(location, "common");
                ContentResourceLoader.loadMapByPath(location, "on_wall");
            }
            default -> ContentResourceLoader.loadRoot(location);
        }
    }
}
