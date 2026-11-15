package com.fangsu.mtr;

import com.fangsu.mtr.rail.ServiceRail;
import mtr.data.DataCache;
import mtr.data.Station;
import mtr.data.Rail;
import net.minecraft.core.BlockPos;
import java.util.Map;

public final class RouteBypass {
    private RouteBypass() {}

    public static ServiceRail settings(Map<BlockPos, Map<BlockPos, Rail>> rails, ServiceStop.Bypass bypass) {
        final Rail rail = DataCache.tryGet(rails, bypass.start(), bypass.end());
        final var settings = ServiceRail.get(rail);
        return settings != null && settings.kind() == ServiceRail.Kind.BYPASS ? settings : null;
    }

    public static long waypointId(ServiceStop.Bypass bypass) {
        final long first = bypass.start().asLong();
        final long second = bypass.end().asLong();
        // 快车经过的轨段拥有独立的寻路标识，可与同站普通站台相邻。
        return java.util.UUID.nameUUIDFromBytes(java.nio.ByteBuffer.allocate(16)
                .putLong(Math.min(first, second)).putLong(Math.max(first, second)).array()).getMostSignificantBits();
    }

    public static boolean sameStation(Station station, ServiceStop.Bypass bypass) {
        if (station == null) {
            return false;
        }
        final BlockPos start = bypass.start();
        final BlockPos end = bypass.end();
        return station.inArea(start.getX(), start.getZ()) && station.inArea(end.getX(), end.getZ());
    }
}
