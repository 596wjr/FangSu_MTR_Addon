package com.fangsu.mtr;

/** 暂缓开通是车站属性，线路仍保存每个站台原有的停靠方式。 */
public interface StationOpening {
    String KEY = "fangsu_station_deferred";

    boolean fangsu$isDeferred();

    void fangsu$setDeferred(boolean deferred);

    static boolean isDeferred(Object station) {
        return station instanceof StationOpening opening && opening.fangsu$isDeferred();
    }

    static RouteStopStatus displayStatus(Object stop, Object station) {
        return isDeferred(station) ? RouteStopStatus.DISABLED : RouteStopStatus.of(stop);
    }

    static RouteStopStatus pathStatus(Object stop, Object station) {
        final var status = RouteStopStatus.of(stop);
        // 暂缓期间列车仍走已配置的越行轨道。
        return status == RouteStopStatus.NORMAL ? displayStatus(stop, station) : status;
    }
