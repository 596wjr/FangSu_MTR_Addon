package com.fangsu.mixin;

import com.fangsu.mtr.RouteServices;
import com.fangsu.mtr.RouteBypass;
import com.fangsu.mtr.ServiceStop;
import com.fangsu.data.hybrid.RailActionsModuleExtraSupplier;
import mtr.Registry;
import mtr.data.RailwayData;
import mtr.packet.IPacket;
import mtr.packet.PacketTrainDataGuiServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;

@Mixin(value = PacketTrainDataGuiServer.class, remap = false)
public abstract class ServiceRouteUpdateMixin {
    @Inject(method = "lambda$receiveUpdateOrDeleteC2S$9", at = @At("TAIL"))
    private static void fangsu$synchronizeChildren(Level world, ServerPlayer player, ResourceLocation channel,
                                                    RailwayData railway, FriendlyByteBuf update, FriendlyByteBuf full, CallbackInfo ci) {
        if (!IPacket.PACKET_UPDATE_ROUTE.equals(channel) || full.readableBytes() < Long.BYTES) {
            return;
        }
        final int index = full.readerIndex();
        final long routeId;
        final String key;
        try {
            routeId = full.readLong();
            full.readUtf();
            key = full.readUtf();
        } finally {
            full.readerIndex(index);
        }
        if (!"platform_ids".equals(key)) {
            return;
        }
        railway.dataCache.sync();
        final var edited = railway.dataCache.routeIdMap.get(routeId);
        if (edited == null) {
            return;
        }
        final java.util.function.LongUnaryOperator stationForPlatform = id -> {
            final var station = railway.dataCache.platformIdToStation.get(id);
            return station == null ? 0 : station.id;
        };
        final var rails = ((RailActionsModuleExtraSupplier) railway.railwayDataRailActionsModule).getRails();
        final java.util.function.BiPredicate<Long, ServiceStop.Bypass> validBypass = (platform, bypass) ->
                RouteBypass.sameStation(railway.dataCache.platformIdToStation.get(platform), bypass)
                        && RouteBypass.settings(rails, bypass) != null;
        final var changed = new HashSet<Long>();
        final var pathsToRegenerate = new HashSet<Long>();
        pathsToRegenerate.add(edited.id);
        final var editedRelation = RouteServices.relation(edited);
        if (editedRelation != null) {
            final var parent = railway.dataCache.routeIdMap.get(editedRelation.parentRoute());
            if (parent != null && RouteServices.synchronize(parent, edited, stationForPlatform, validBypass)) {
                changed.add(edited.id);
            }
        }
        for (final var child : railway.routes) {
            final var relation = RouteServices.relation(child);
            if (relation != null && relation.parentRoute() == edited.id
                    && RouteServices.synchronize(edited, child, stationForPlatform, validBypass)) {
                changed.add(child.id);
            }
        }
        for (final var route : railway.routes) {
            if (changed.contains(route.id)) {
                route.setPlatformIds(packet -> world.players().forEach(recipient ->
                        Registry.sendToPlayer((ServerPlayer) recipient, IPacket.PACKET_UPDATE_ROUTE, packet)));
            }
        }
        railway.dataCache.sync();
        pathsToRegenerate.addAll(changed);
        for (final var depot : railway.depots) {
            if (depot.routeIds.stream().anyMatch(pathsToRegenerate::contains)) {
                railway.railwayDataPathGenerationModule.generatePath(player.getServer(), depot.id);
            }
        }
    }
}
