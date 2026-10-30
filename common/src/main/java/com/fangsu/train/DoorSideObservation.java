package com.fangsu.train;

/** 实时门侧必须绑定站台和反向状态，不能将上一站/上一方向的结果拿来兜底。 */
record DoorSideObservation(long platformId, boolean reversed, DoorOpenRecord.DoorOpenSide[] sides) {
    DoorSideObservation {
        sides = sides.clone();
    }

    @Override
    public DoorOpenRecord.DoorOpenSide[] sides() {
        return sides.clone();
    }

    DoorOpenRecord.DoorOpenSide[] forStop(long platform, boolean reverse) {
        return platformId == platform && reversed == reverse ? sides() : null;
    }
}
