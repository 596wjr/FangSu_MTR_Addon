package com.fangsu.mixin;

import com.fangsu.mtr.DrawableRoute;
import com.fangsu.mtr.RouteStopStatus;
import com.fangsu.mtr.ServiceStop;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mtr.data.Route;
import net.minecraft.network.FriendlyByteBuf;
import org.msgpack.value.Value;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(value = Route.class, remap = false)
public abstract class RouteStatusMixin {
    // 3.2.2 将每条线路的站台 ID 和目的地分开保存，沿用目的地的字符串载荷。
    @Unique private static final String FANGSU_STOP_PREFIX = "\u0000fangsu:stop:";
    @Shadow @Final public List<Route.RoutePlatform> platformIds;

    @WrapOperation(method = {"lambda$writePacket$2", "lambda$setPlatformIds$3"},
            at = @At(value = "FIELD", target = "Lmtr/data/Route$RoutePlatform;customDestination:Ljava/lang/String;"))
    private static String fangsu$encodeStatus(Route.RoutePlatform stop, Operation<String> original) {
        final String destination = original.call(stop);
        final var status = RouteStopStatus.of(stop);
        final var metadata = ServiceStop.get(stop);
        return status == RouteStopStatus.NORMAL && metadata == null ? destination
                : FANGSU_STOP_PREFIX + status.name() + ":" + (metadata == null ? "" : metadata.encode()) + ":" + destination;
    }

    @WrapOperation(method = "toMessagePack",
            at = @At(value = "FIELD", target = "Lmtr/data/Route$RoutePlatform;customDestination:Ljava/lang/String;"))
    private String fangsu$writeDiskStatus(Route.RoutePlatform stop, Operation<String> original) {
        return fangsu$encodeStatus(stop, original);
    }

    @Inject(method = "<init>(Ljava/util/Map;)V", at = @At("RETURN"))
    private void fangsu$loadStatus(Map<String, Value> map, CallbackInfo ci) {
        fangsu$decodeStatus();
    }

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("RETURN"), remap = true)
    private void fangsu$readStatus(FriendlyByteBuf packet, CallbackInfo ci) {
        fangsu$decodeStatus();
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void fangsu$updateStatus(String key, FriendlyByteBuf packet, CallbackInfo ci) {
        if ("platform_ids".equals(key)) {
            fangsu$decodeStatus();
        }
        DrawableRoute.clearCache();
    }

    @Unique
    private void fangsu$decodeStatus() {
        for (final var stop : platformIds) {
            if (stop.customDestination.startsWith(FANGSU_STOP_PREFIX)) {
                final int separator = stop.customDestination.indexOf(':', FANGSU_STOP_PREFIX.length());
                final int destinationStart = separator < 0 ? -1 : stop.customDestination.indexOf(':', separator + 1);
                if (destinationStart >= 0) {
                    try {
                        final var status = RouteStopStatus.valueOf(stop.customDestination.substring(FANGSU_STOP_PREFIX.length(), separator));
                        RouteStopStatus.set(stop, status);
                        ServiceStop.set(stop, ServiceStop.decode(stop.customDestination.substring(separator + 1, destinationStart)));
                        stop.customDestination = stop.customDestination.substring(destinationStart + 1);
                    } catch (IllegalArgumentException ignored) {
                        // 保留未知格式的自定义目的地。
                    }
                }
            }
        }
    }
}
