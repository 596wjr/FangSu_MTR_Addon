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

    public static PidsContent getPidsContent(String mainModel, String subModel) {
        return ContentManager.getInstance().getContentById("pids", mainModel, subModel, PidsContent.class);
    }

    public static DiaobanContent getDiaobanContent(String mainModel, String subModel) {
        return ContentManager.getInstance().getContentById("diaoban", mainModel, subModel, DiaobanContent.class);
    }

    public static TicketBarrierContent getTicketBarrierContent(String mainModel, String subModel) {
        return ContentManager.getInstance().getContentById("ticketBarrier", mainModel, subModel, TicketBarrierContent.class);
    }

    public static DuanmenContent getDuanmenContent(String mainModel, String subModel) {
        return ContentManager.getInstance().getContentById("duanmen", mainModel, subModel, DuanmenContent.class);
    }

    public static SignContent.SignDisplayInfo getSignDisplayInfo(String mainModel, String subModel) {
        return SignContent.SignDisplayInfo.fromMap(CustomItems.getContentInfo(mainModel, "common", subModel));
    }

    public static SignOnWallContent.SignOnWallDisplayInfo getSignOnWallDisplayInfo(String mainModel, String subModel) {
        return SignOnWallContent.SignOnWallDisplayInfo.fromMap(CustomItems.getContentInfo(mainModel, "on_wall", subModel));
    }

    public static RouteInfoSignContent getRisContent(String mainModel, String subModel) {
        return ContentManager.getInstance().getContentById("route_info_sign", mainModel, subModel, RouteInfoSignContent.class);
    }

    public static AdvBoardContent getAdvBoardContent(String mainModel, String subModel) {
        return ContentManager.getInstance().getContentById("adv_board", mainModel, subModel, AdvBoardContent.class);
    }

    public static StationInfoSignContent getSisContent(String mainModel, String subModel) {
        return ContentManager.getInstance().getContentById("station_info_sign", mainModel, subModel, StationInfoSignContent.class);
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
            preloadByType(contentType, info.getContent());
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
