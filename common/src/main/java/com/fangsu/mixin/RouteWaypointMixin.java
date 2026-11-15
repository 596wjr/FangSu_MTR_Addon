package com.fangsu.mixin;

import com.fangsu.mtr.RouteStopStatus;
import mtr.data.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = Platform.class, remap = false)
public abstract class RouteWaypointMixin implements RouteStopStatus.Holder {
    @Unique private RouteStopStatus fangsu$stopStatus;

    @Override
    public RouteStopStatus fangsu$getStopStatus() {
        return fangsu$stopStatus == null ? RouteStopStatus.NORMAL : fangsu$stopStatus;
    }

    @Override
    public void fangsu$setStopStatus(RouteStopStatus status) {
        fangsu$stopStatus = status;
    }
}
