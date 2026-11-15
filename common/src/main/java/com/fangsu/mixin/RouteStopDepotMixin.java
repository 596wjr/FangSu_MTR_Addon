package com.fangsu.mixin;

import com.fangsu.mtr.RouteStopStatus;
import com.fangsu.mtr.RouteServices;
import mtr.data.SavedRailBase;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.fangsu.mtr.RouteBypass;
import com.fangsu.mtr.ServiceStop;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mtr.data.Depot;
import mtr.data.DataCache;
import mtr.data.Platform;
import mtr.data.Route;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(value = Depot.class, remap = false)
public abstract class RouteStopDepotMixin {
    @Inject(method = "generateMainRoute",
            at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER))
    private void fangsu$rejectInvalidSections(net.minecraft.server.MinecraftServer server, net.minecraft.world.level.Level world,
            DataCache cache, java.util.Map<net.minecraft.core.BlockPos, java.util.Map<net.minecraft.core.BlockPos, mtr.data.Rail>> rails,
            java.util.Set<mtr.data.Siding> sidings, java.util.function.Consumer<Thread> callback,
            CallbackInfo ci, @Local List<SavedRailBase> waypoints) {
        for (final long routeId : ((Depot) (Object) this).routeIds) {
            final var route = cache.routeIdMap.get(routeId);
            final var relation = route == null ? null : RouteServices.relation(route);
            if (relation != null && !RouteServices.hasRunningSection(cache.routeIdMap.get(relation.parentRoute()), route)) {
                waypoints.clear();
                return;
            }
        }
    }

    @WrapOperation(method = "lambda$generateMainRoute$4",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object fangsu$copyPathStop(java.util.Map<?, ?> platforms, Object id, Operation<Object> original,
                                              @Local(argsOnly = true) Route.RoutePlatform stop, @Local(argsOnly = true) DataCache cache) {
        final Object value = original.call(platforms, id);
        final var status = RouteStopStatus.of(stop);
        if (value instanceof Platform platform && status != RouteStopStatus.NORMAL) {
            final var metadata = ServiceStop.get(stop);
            final var bypass = metadata == null ? null : metadata.bypass();
            final var station = cache.platformIdToStation.get(platform.id);
            final boolean useBypass = status == RouteStopStatus.SKIPPED && bypass != null && metadata.express()
                    && RouteBypass.sameStation(station, bypass);
            final var first = useBypass ? bypass.start() : platform.getOtherPosition(platform.getMidPos());
            final var last = useBypass ? bypass.end() : platform.getOtherPosition(first);
            final Platform waypoint = new Platform(useBypass ? RouteBypass.waypointId(bypass) : platform.id, platform.transportMode, first, last);
            RouteStopStatus.set(waypoint, status);
            return waypoint;
        }
        return value;
    }
}
