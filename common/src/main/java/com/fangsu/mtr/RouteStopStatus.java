package com.fangsu.mtr;

/** 当前线路在一个站点条目上的停靠方式。 */
public enum RouteStopStatus {
    DISABLED, // 普通站台被划线，列车通过
    SKIPPED,  // 列车走同站越行轨道
    NORMAL;   // 普通停靠

    public interface Holder {
        RouteStopStatus fangsu$getStopStatus();
        void fangsu$setStopStatus(RouteStopStatus status);
    }

    public static RouteStopStatus of(Object stop) {
        return stop instanceof Holder holder ? holder.fangsu$getStopStatus() : NORMAL;
    }

    public static void set(Object stop, RouteStopStatus status) {
        ((Holder) stop).fangsu$setStopStatus(status);
    }
}
