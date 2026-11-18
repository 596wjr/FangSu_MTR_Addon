package com.fangsu.network;

import com.fangsu.mtr.RouteServices;
import com.fangsu.scripting.TextUtil;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mtr.Registry;
import mtr.data.RailwayData;
import mtr.data.RailwayDataLoggingModule;
import mtr.data.Route;
import mtr.packet.IPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public final class RouteGroupPackets {
    private static final ResourceLocation EDIT = new ResourceLocation("fangsu", "route_group_edit");
    private static final int MAX_ROUTES = 4096;

    private RouteGroupPackets() {}

    public static void registerServer() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, EDIT, (buffer, context) -> {
            final int count = buffer.readVarInt();
            if (count < 1 || count > MAX_ROUTES || buffer.readableBytes() < count * Long.BYTES + 1) {
                return;
            }
            final var ids = new LinkedHashSet<Long>();
            for (int i = 0; i < count; i++) {
                ids.add(buffer.readLong());
            }
            final boolean delete = buffer.readBoolean();
            final String name = !delete && buffer.readBoolean() ? buffer.readUtf(128).trim() : null;
            final Integer color = !delete && buffer.readBoolean() ? buffer.readInt() : null;
            if (ids.size() != count || name != null && (name.isBlank() || name.contains("||"))
                    || color != null && (color < 0 || color > 0xFFFFFF)) {
                return;
            }
            context.queue(() -> {
                if (context.getPlayer() instanceof ServerPlayer player && !RailwayData.hasNoPermission(player)) {
                    apply(player, ids, delete, name, color);
                }
            });
        });
    }

    public static void send(Collection<Long> ids, boolean delete, String name, Integer color) {
        if (ids.isEmpty() || ids.size() > MAX_ROUTES) {
            return;
        }
        final var buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(ids.size());
        ids.forEach(buffer::writeLong);
        buffer.writeBoolean(delete);
        if (!delete) {
            buffer.writeBoolean(name != null);
            if (name != null) {
                buffer.writeUtf(name, 128);
            }
            buffer.writeBoolean(color != null);
            if (color != null) {
                buffer.writeInt(color);
            }
        }
        NetworkManager.sendToServer(EDIT, buffer);
    }

    private static void apply(ServerPlayer player, Collection<Long> ids, boolean delete, String name, Integer color) {
        //#if MC_VERSION >= 12000
        final Level world = player.level();
        //#else
        //$$ final Level world = player.level;
        //#endif
        final var railway = RailwayData.getInstance(world);
        if (railway == null) {
            return;
        }

        final var routes = new ArrayList<Route>();
        String group = null;
        for (final long id : ids) {
            final var route = railway.dataCache.routeIdMap.get(id);
            if (route == null) return;
            final var relation = RouteServices.relation(route);
            final var parent = relation == null ? null : railway.dataCache.routeIdMap.get(relation.parentRoute());
            final String routeGroup = groupName(parent == null ? route : parent);
            if (group != null && (route.transportMode != routes.get(0).transportMode || !group.equals(routeGroup))) {
                return;
            }
            group = routeGroup;
            routes.add(route);
        }

        if (delete) {
            for (final var child : railway.routes) {
                final var relation = RouteServices.relation(child);
                if (relation != null && ids.contains(relation.parentRoute()) && !ids.contains(child.id)) {
                    routes.add(child);
                }
            }
            final var deleted = routes.stream().map(route -> route.id).collect(java.util.stream.Collectors.toSet());
            final var depots = railway.depots.stream()
                    .filter(depot -> depot.routeIds.stream().anyMatch(deleted::contains)).map(depot -> depot.id).toList();
            for (final var route : routes) {
                railway.railwayDataLoggingModule.addEvent(player, Route.class, route.id, route.name,
                        RailwayDataLoggingModule.getData(route), List.of());
            }
            railway.routes.removeAll(routes);
            railway.dataCache.sync();
            for (final var route : routes) {
                final var packet = new FriendlyByteBuf(Unpooled.buffer());
                packet.writeLong(route.id);
                world.players().forEach(recipient -> Registry.sendToPlayer((ServerPlayer) recipient, IPacket.PACKET_DELETE_ROUTE, packet));
            }
            depots.forEach(id -> railway.railwayDataPathGenerationModule.generatePath(player.getServer(), id));
            return;
        }

        for (final var route : routes) {
            final var previous = RailwayDataLoggingModule.getData(route);
            if (name != null && groupName(route).equals(group)) {
                final String direction = TextUtil.getExtraParts(route.name);
                route.name = name + (direction.isEmpty() ? "" : "||" + direction);
            }
            if (color != null) {
                route.color = color;
            }
            railway.railwayDataLoggingModule.addEvent(player, Route.class, route.id, route.name,
                    previous, RailwayDataLoggingModule.getData(route));
        }
        railway.dataCache.sync();
        for (final var route : routes) {
            route.setExtraData(packet -> world.players().forEach(recipient ->
                    Registry.sendToPlayer((ServerPlayer) recipient, IPacket.PACKET_UPDATE_ROUTE, packet)));
        }
    }

    private static String groupName(Route route) {
        return TextUtil.getNonExtraParts(route.name).trim();
    }
}
