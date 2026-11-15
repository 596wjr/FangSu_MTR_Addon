package com.fangsu.network;

import com.fangsu.items.ItemServiceRail;
import com.fangsu.mtr.rail.ServiceRail;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import com.fangsu.data.hybrid.RailActionsModuleExtraSupplier;
import com.fangsu.mixin.ServiceRailSidingAccess;
import mtr.data.DataCache;
import mtr.data.RailwayData;
import mtr.packet.PacketTrainDataGuiServer;
import mtr.path.PathData;

import java.util.HashSet;

public final class ServiceRailPackets {
    private static final ResourceLocation UPDATE_TOOL = new ResourceLocation("fangsu", "service_rail_tool");
    private static final ResourceLocation UPDATE_TRACK = new ResourceLocation("fangsu", "service_rail_track");

    private ServiceRailPackets() {
    }

    public static void registerServer() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, UPDATE_TRACK, (buffer, context) -> {
            final BlockPos start = buffer.readBlockPos();
            final BlockPos end = buffer.readBlockPos();
            final ServiceRail settings = ServiceRail.decode(buffer.readUtf(64));
            if (start.equals(end) || settings == null || settings.kind() != ServiceRail.Kind.BYPASS) {
                return;
            }
            context.queue(() -> {
                if (!(context.getPlayer() instanceof ServerPlayer player) || RailwayData.hasNoPermission(player)) {
                    return;
                }
                //#if MC_VERSION >= 12000
                final var level = player.level();
                //#else
                //$$ final var level = player.level;
                //#endif
                if (level instanceof ServerLevel serverLevel) {
                    applyTrack(player, serverLevel, start, end, settings);
                }
            });
        });
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, UPDATE_TOOL, (buffer, context) -> {
            final int handIndex = buffer.readUnsignedByte();
            final ServiceRail settings = ServiceRail.decode(buffer.readUtf(64));
            if (handIndex >= InteractionHand.values().length || settings == null) {
                return;
            }
            final InteractionHand hand = InteractionHand.values()[handIndex];
            context.queue(() -> {
                if (context.getPlayer() instanceof ServerPlayer player) {
                    final var stack = player.getItemInHand(hand);
                    if (stack.getItem() instanceof ItemServiceRail tool && tool.kind() == settings.kind()) {
                        stack.getOrCreateTag().putString(ServiceRail.KEY, settings.encode());
                    }
                }
            });
        });
    }

    public static void updateTool(InteractionHand hand, ServiceRail settings) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeByte(hand.ordinal());
        buffer.writeUtf(settings.encode());
        NetworkManager.sendToServer(UPDATE_TOOL, buffer);
    }

    public static void updateTrack(BlockPos start, BlockPos end, ServiceRail settings) {
        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBlockPos(start);
        buffer.writeBlockPos(end);
        buffer.writeUtf(settings.encode());
        NetworkManager.sendToServer(UPDATE_TRACK, buffer);
    }

    private static void applyTrack(ServerPlayer player, ServerLevel level, BlockPos start, BlockPos end, ServiceRail settings) {
        final var railway = RailwayData.getInstance(level);
        if (railway == null) {
            return;
        }
        final var rails = ((RailActionsModuleExtraSupplier) railway.railwayDataRailActionsModule).getRails();
        final var forward = DataCache.tryGet(rails, start, end);
        final var backward = DataCache.tryGet(rails, end, start);
        final var previous = ServiceRail.get(forward);
        if (backward == null || previous == null || previous.kind() != ServiceRail.Kind.BYPASS || previous.equals(settings)) {
            return;
        }
        ServiceRail.set(forward, settings);
        ServiceRail.set(backward, settings);
        PacketTrainDataGuiServer.createRailS2C(level, forward.transportMode, start, end, forward, backward, 0);
        if (previous.value() != settings.value()) {
            regenerate(railway, player.getServer(), start, end);
        }
    }

    public static void regenerate(RailwayData railway, MinecraftServer server, BlockPos start, BlockPos end) {
        final var depots = new HashSet<Long>();
        final var id = PathData.getRailProduct(start, end);
        for (final var siding : railway.sidings) {
            final var path = ((ServiceRailSidingAccess) (Object) siding).fangsu$getPath();
            if (path.stream().anyMatch(segment -> id.equals(segment.getRailProduct()))) {
                final var depot = railway.dataCache.sidingIdToDepot.get(siding.id);
                if (depot != null) {
                    depots.add(depot.id);
                }
            }
        }
        depots.forEach(depot -> railway.railwayDataPathGenerationModule.generatePath(server, depot));
    }
}
