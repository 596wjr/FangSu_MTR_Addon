package com.fangsu.train.data;

import com.fangsu.blockEntities.BlockEntityTrainDataSender;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TrainDataRuntime {
    private static final int TIMEOUT = 200;
    private static final Map<ServerLevel, WorldData> WORLDS = new HashMap<>();

    private TrainDataRuntime() {}

    /** order 是一次模拟更新中沿车头实际行进路径排列的轨道片段。 */
    public record Slice(Vec3 from, Vec3 to, long route, long station, boolean reverse) {}
    public record SensorRange(int horizontal, int below, int above) {
        AABB bounds(BlockPos pos) {
            return new AABB(pos.getX() - horizontal, pos.getY() - above, pos.getZ() - horizontal,
                    pos.getX() + 1 + horizontal, pos.getY() + 1 + below, pos.getZ() + 1 + horizontal);
        }
    }

    private record Crossing(double progress, BlockPos sender, Slice track) {}

    private static final class VehicleData {
        final Map<ServerPlayer, Integer> viewers = new HashMap<>();
        Set<BlockPos> inside = new HashSet<>();
        TrainDataSnapshot snapshot = TrainDataSnapshot.EMPTY;
        Vec3 head;
        String consist = "";
        long station;
        long route;
        boolean reverse;
        int seen;
    }

    private static final class WorldData {
        final Map<Long, Map<BlockPos, TrainDataSenderConfig>> senders = new HashMap<>();
        final Map<Long, VehicleData> vehicles = new HashMap<>();
    }

    public static void register() {
        LifecycleEvent.SERVER_STOPPED.register(server -> WORLDS.keySet().removeIf(level -> level.getServer() == server));
        TickEvent.SERVER_POST.register(TrainDataRuntime::cleanup);
    }

    public static void observe(ServerLevel level, BlockPos position, TrainDataSenderConfig config) {
        WorldData world = WORLDS.computeIfAbsent(level, ignored -> new WorldData());
        long chunk = ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
        world.senders.computeIfAbsent(chunk, ignored -> new HashMap<>()).put(position.immutable(), config);
    }

    public static void forget(ServerLevel level, BlockPos position) {
        WorldData world = WORLDS.get(level);
        if (world == null) return;
        long chunk = ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
        Map<BlockPos, TrainDataSenderConfig> senders = world.senders.get(chunk);
        if (senders != null) {
            senders.remove(position);
            if (senders.isEmpty()) world.senders.remove(chunk);
        }
    }

    public static void move(ServerLevel level, long id, String consist, List<Slice> slices, double speed, SensorRange range) {
        if (slices.isEmpty()) return;
        WorldData world = WORLDS.computeIfAbsent(level, ignored -> new WorldData());
        VehicleData vehicle = world.vehicles.computeIfAbsent(id, ignored -> new VehicleData());
        TrainDataSnapshot before = vehicle.snapshot;
        if (!vehicle.consist.equals(consist)) {
            vehicle.snapshot = TrainDataSnapshot.EMPTY;
            vehicle.inside.clear();
            vehicle.station = 0;
            vehicle.consist = consist;
        }
        vehicle.head = slices.get(slices.size() - 1).to();
        vehicle.seen = level.getServer().getTickCount();
        for (Slice slice : slices) process(level, world, vehicle, slice, speed, range);
        if (!before.equals(vehicle.snapshot)) {
            vehicle.viewers.keySet().removeIf(player -> !canTrack(level, vehicle, player));
            for (ServerPlayer player : vehicle.viewers.keySet()) TrainDataNetwork.send(player, id, vehicle.snapshot, false);
        }
    }

    public static void subscribe(ServerLevel level, long id, ServerPlayer player) {
        WorldData world = WORLDS.get(level);
        VehicleData vehicle = world == null ? null : world.vehicles.get(id);
        if (vehicle == null || !canTrack(level, vehicle, player)) return;
        int time = level.getServer().getTickCount();
        boolean first = vehicle.viewers.put(player, time) == null;
        TrainDataNetwork.send(player, id, vehicle.snapshot, first);
    }

    private static boolean canTrack(ServerLevel level, VehicleData vehicle, ServerPlayer player) {
        return TrainDataNetwork.level(player) == level && !player.isRemoved() && vehicle.head != null
                && player.position().distanceToSqr(vehicle.head) <= 2048 * 2048;
    }

    private static void process(ServerLevel level, WorldData world, VehicleData vehicle, Slice slice, double speed, SensorRange range) {
        List<Crossing> crossings = new ArrayList<>();
        if (vehicle.station != slice.station() || vehicle.route != slice.route() || vehicle.reverse != slice.reverse()) {
            vehicle.station = slice.station();
            vehicle.route = slice.route();
            vehicle.reverse = slice.reverse();
            if (slice.station() != 0 && slice.route() != 0) crossings.add(new Crossing(0, null, slice));
        }

        Set<BlockPos> inside = new HashSet<>();
        Vec3 from = slice.from();
        Vec3 to = slice.to();
        double lengthSquared = from.distanceToSqr(to);
        int minX = ((int) Math.floor(Math.min(from.x, to.x)) - range.horizontal()) >> 4;
        int maxX = ((int) Math.floor(Math.max(from.x, to.x)) + range.horizontal()) >> 4;
        int minZ = ((int) Math.floor(Math.min(from.z, to.z)) - range.horizontal()) >> 4;
        int maxZ = ((int) Math.floor(Math.max(from.z, to.z)) + range.horizontal()) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Map<BlockPos, TrainDataSenderConfig> senders = world.senders.get(ChunkPos.asLong(x, z));
                if (senders == null) continue;
                senders.forEach((pos, config) -> {
                    if (!config.matches(slice.route(), speed)) return;
                    if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof BlockEntityTrainDataSender)) return;
                    AABB bounds = range.bounds(pos);
                    boolean startsInside = bounds.contains(from);
                    boolean endsInside = bounds.contains(to);
                    if (endsInside) inside.add(pos);
                    if (startsInside && vehicle.inside.contains(pos)) return;
                    if (startsInside) crossings.add(new Crossing(0, pos, slice));
                    else if (lengthSquared > 0) bounds.clip(from, to).ifPresent(point -> crossings.add(new Crossing(
                            from.distanceToSqr(point) / lengthSquared, pos, slice)));
                });
            }
        }
        vehicle.inside = inside;
        crossings.sort(Comparator.comparingDouble(Crossing::progress)
                .thenComparing(crossing -> crossing.sender() == null)
                .thenComparingLong(crossing -> crossing.sender() == null ? 0 : crossing.sender().asLong()));
        for (Crossing crossing : crossings) {
            if (crossing.sender() != null) {
                BlockPos pos = crossing.sender();
                TrainDataSenderConfig config = world.senders.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)).get(pos);
                vehicle.snapshot = vehicle.snapshot.overlay(config.data());
            } else {
                Slice track = crossing.track();
                TrainStationData.Key key = new TrainStationData.Key(level.dimension().location().toString(),
                        track.station(), track.route(), track.reverse(), vehicle.consist);
                vehicle.snapshot = TrainStationData.get(level.getServer()).visit(key, vehicle.snapshot);
            }
        }
    }

    private static void cleanup(MinecraftServer server) {
        int time = server.getTickCount();
        if (time % 40 != 0) return;
        WORLDS.forEach((level, world) -> {
            if (level.getServer() != server) return;
            world.vehicles.entrySet().removeIf(entry -> time - entry.getValue().seen > TIMEOUT);
            world.vehicles.values().forEach(vehicle -> vehicle.viewers.entrySet().removeIf(
                    entry -> time - entry.getValue() > TIMEOUT || !canTrack(level, vehicle, entry.getKey())));
        });
    }
}
