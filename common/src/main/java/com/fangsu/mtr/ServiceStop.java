package com.fangsu.mtr;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/** 站点身份及子线路对母线站点的覆盖。一个车站在环线上可有多个独立条目。 */
public record ServiceStop(UUID id, long parentRoute, UUID parentStop, boolean express,
                          boolean platformOverride, RouteStopStatus statusOverride, Bypass bypass) {
    public static final String KEY = "fangsu_service_stop";

    public record Bypass(BlockPos start, BlockPos end) {}

    public interface Holder {
        ServiceStop fangsu$getServiceStop();
        void fangsu$setServiceStop(ServiceStop stop);
    }

    public static ServiceStop get(Object entry) {
        return ((Holder) entry).fangsu$getServiceStop();
    }

    public static void set(Object entry, ServiceStop stop) {
        ((Holder) entry).fangsu$setServiceStop(stop);
    }

    public static ServiceStop ensure(Object entry) {
        var stop = get(entry);
        if (stop == null) {
            stop = new ServiceStop(UUID.randomUUID(), 0, null, false, false, null, null);
            set(entry, stop);
        }
        return stop;
    }

    public ServiceStop child(long routeId, boolean express) {
        return new ServiceStop(UUID.randomUUID(), routeId, id, express, false, null, null);
    }

    public ServiceStop override(boolean platform, RouteStopStatus status, Bypass rail) {
        return new ServiceStop(id, parentRoute, parentStop, express, platform, status, rail);
    }

    public String encode() {
        return id + "," + parentRoute + "," + (parentStop == null ? "" : parentStop) + ","
                + express + "," + platformOverride + "," + (statusOverride == null ? "" : statusOverride.name()) + ","
                + (bypass == null ? "" : bypass.start.asLong() + "," + bypass.end.asLong());
    }

    public static ServiceStop decode(String text) {
        if (text.isEmpty()) {
            return null;
        }
        final String[] fields = text.split(",", -1);
        if (fields.length != 7 && fields.length != 8) {
            return null;
        }
        try {
            final UUID parentStop = fields[2].isEmpty() ? null : UUID.fromString(fields[2]);
            final var status = fields[5].isEmpty() ? null : RouteStopStatus.valueOf(fields[5]);
            final var bypass = fields.length == 7 ? null
                    : new Bypass(BlockPos.of(Long.parseLong(fields[6])), BlockPos.of(Long.parseLong(fields[7])));
            return new ServiceStop(UUID.fromString(fields[0]), Long.parseLong(fields[1]), parentStop,
                    Boolean.parseBoolean(fields[3]), Boolean.parseBoolean(fields[4]), status, bypass);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
