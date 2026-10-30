package com.fangsu.train;

/** Java/JS 共用的门侧解析：本站实时值优先，缓存的规范帧只翻转一次。 */
public final class DoorSideResolver {
    private DoorSideResolver() {
    }

    public static DoorOpenRecord.DoorOpenSide resolve(DoorOpenRecord.DoorOpenSide[] live,
                                                      DoorOpenRecord.DoorOpenSide[] predicted,
                                                      int car, boolean reversed, boolean doorsOpen) {
        final DoorOpenRecord.DoorOpenSide actual = at(live, car);
        if (doorsOpen && actual != DoorOpenRecord.DoorOpenSide.UNKNOWN) return actual;
        final DoorOpenRecord.DoorOpenSide cached = at(predicted, car);
        return reversed ? cached.toOpposite() : cached;
    }

    private static DoorOpenRecord.DoorOpenSide at(DoorOpenRecord.DoorOpenSide[] sides, int car) {
        if (sides == null || car < 0 || car >= sides.length || sides[car] == null) {
            return DoorOpenRecord.DoorOpenSide.UNKNOWN;
        }
        return sides[car];
    }
}
