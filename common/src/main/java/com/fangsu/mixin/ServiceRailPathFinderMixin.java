package com.fangsu.mixin;

import com.fangsu.mtr.rail.ServiceRail;
import com.fangsu.mtr.RouteStopStatus;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mtr.data.Rail;
import mtr.data.RailType;
import mtr.data.SavedRailBase;
import mtr.path.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PathFinder.class, remap = false)
public abstract class ServiceRailPathFinderMixin {
    @org.spongepowered.asm.mixin.injection.Inject(
            method = "findPath(Ljava/util/Map;Ljava/util/Set;Lmtr/data/SavedRailBase;Lmtr/data/SavedRailBase;IIZ)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private static void fangsu$validateWaypoint(java.util.Map<net.minecraft.core.BlockPos, java.util.Map<net.minecraft.core.BlockPos, Rail>> rails,
                                                java.util.Set<net.minecraft.core.BlockPos> runways, SavedRailBase start,
                                                SavedRailBase end, int stopIndex, int cruisingAltitude, boolean fast,
                                                org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.List<mtr.path.PathData>> cir) {
        if (!fangsu$validWaypoint(rails, start) || !fangsu$validWaypoint(rails, end)) {
            cir.setReturnValue(new java.util.ArrayList<>());
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean fangsu$validWaypoint(java.util.Map<net.minecraft.core.BlockPos, java.util.Map<net.minecraft.core.BlockPos, Rail>> rails,
                                                SavedRailBase waypoint) {
        if (RouteStopStatus.of(waypoint) != RouteStopStatus.SKIPPED) {
            return true;
        }
        final var start = waypoint.getOtherPosition(waypoint.getMidPos());
        final var end = waypoint.getOtherPosition(start);
        return com.fangsu.mtr.RouteBypass.settings(rails, new com.fangsu.mtr.ServiceStop.Bypass(start, end)) != null;
    }

    @org.spongepowered.asm.mixin.injection.Inject(
            method = "findPath(Ljava/util/Map;Ljava/util/Set;Lmtr/data/SavedRailBase;Lmtr/data/SavedRailBase;IIZ)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private static void fangsu$requireBypassEdge(java.util.Map<net.minecraft.core.BlockPos, java.util.Map<net.minecraft.core.BlockPos, Rail>> rails,
            java.util.Set<net.minecraft.core.BlockPos> runways, SavedRailBase start, SavedRailBase end, int stopIndex,
            int cruisingAltitude, boolean fast,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.List<mtr.path.PathData>> cir) {
        final var path = cir.getReturnValue();
        if (!path.isEmpty() && (!fangsu$matchesEdge(start, path.get(0))
                || !fangsu$matchesEdge(end, path.get(path.size() - 1)))) {
            cir.setReturnValue(new java.util.ArrayList<>());
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean fangsu$matchesEdge(SavedRailBase stop, mtr.path.PathData path) {
        if (RouteStopStatus.of(stop) != RouteStopStatus.SKIPPED) return true;
        final var first = stop.getOtherPosition(stop.getMidPos());
        return mtr.path.PathData.getRailProduct(first, stop.getOtherPosition(first)).equals(path.getRailProduct());
    }

    @WrapOperation(method = "findPath(Ljava/util/Map;Ljava/util/Set;Lmtr/data/SavedRailBase;Lmtr/data/SavedRailBase;IIZ)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lmtr/data/SavedRailBase;getDwellTime()I"))
    private static int fangsu$skipDwell(SavedRailBase waypoint, Operation<Integer> original) {
        return RouteStopStatus.of(waypoint) == RouteStopStatus.NORMAL ? original.call(waypoint) : 0;
    }

    @WrapOperation(method = "lambda$findPath$2", at = @At(value = "FIELD", target = "Lmtr/data/Rail;railType:Lmtr/data/RailType;"))
    private static RailType fangsu$useBypassLimit(Rail rail, Operation<RailType> original) {
        final var settings = ServiceRail.get(rail);
        if (settings != null && settings.kind() == ServiceRail.Kind.BYPASS) {
            for (final RailType type : RailType.values()) {
                if (type.canAccelerate && type.hasSignal && type.railSlopeStyle != RailType.RailSlopeStyle.CABLE
                        && type.speedLimit == settings.value()) {
                    return type;
                }
            }
        }
        return original.call(rail);
    }
}
