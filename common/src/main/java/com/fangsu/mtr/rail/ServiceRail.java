package com.fangsu.mtr.rail;

import java.util.Arrays;
import java.util.List;

import mtr.data.RailType;

/** 随轨道保存和同步的线路设置。普通轨道没有此数据。 */
public record ServiceRail(Kind kind, int value, String number) {
    public static final String KEY = "fangsu_service_rail";
    public static final String DEFAULT_NUMBER = "R";
    public static final int DEFAULT_SPEED = 80;
    public static final int DEFAULT_SECONDS = 10;

    // MTR 3 的运行时间以 tick 计，需容纳两次累加和列车的时间换算。
    public static final int MAX_SECONDS = Integer.MAX_VALUE / 20;

    public static final List<Integer> SPEEDS = Arrays.stream(RailType.values())
            .filter(type -> type.canAccelerate && type.hasSignal && type.railSlopeStyle != RailType.RailSlopeStyle.CABLE)
            .map(type -> type.speedLimit)
            .distinct()
            .sorted()
            .toList();

    public enum Kind {
        BYPASS("bypass_rail", 0xFF35BCEB),
        PARKING("parking_rail", 0xFFF8AA36);

        public final String itemId;
        public final int overlayColor;

        Kind(String itemId, int overlayColor) {
            this.itemId = itemId;
            this.overlayColor = overlayColor;
        }

        public ServiceRail defaults() {
            return new ServiceRail(this, this == BYPASS ? DEFAULT_SPEED : DEFAULT_SECONDS,
                    this == BYPASS ? DEFAULT_NUMBER : "");
        }
    }

    public ServiceRail {
        java.util.Objects.requireNonNull(kind);
        java.util.Objects.requireNonNull(number);
        if (kind == Kind.BYPASS) {
            if (!SPEEDS.contains(value) || number.isBlank() || number.length() > 10) {
                throw new IllegalArgumentException("Invalid bypass rail settings");
            }
        } else if (value < 1 || value > MAX_SECONDS || !number.isEmpty()) {
            throw new IllegalArgumentException("Invalid parking rail settings");
        }
    }

    public String encode() {
        return kind.name() + ":" + value + ":" + number;
    }

    public static ServiceRail decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }

        final String[] fields = encoded.split(":", 3);
        if (fields.length != 3) {
            return null;
        }
        try {
            return new ServiceRail(Kind.valueOf(fields[0]), Integer.parseInt(fields[1]), fields[2]);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static ServiceRail get(Object rail) {
        return rail instanceof Holder holder ? holder.fangsu$getServiceRail() : null;
    }

    public static void set(Object rail, ServiceRail value) {
        ((Holder) rail).fangsu$setServiceRail(value);
    }

    public interface Holder {
        ServiceRail fangsu$getServiceRail();
        void fangsu$setServiceRail(ServiceRail value);
    }
}
