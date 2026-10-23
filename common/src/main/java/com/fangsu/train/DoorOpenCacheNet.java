package com.fangsu.train;

import com.fangsu.Main;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import mtr.data.RailwayData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 开门方向缓存网络层（双端共享，MTR3 版）。
 * <p>
 * 四条通道：
 * <ul>
 *   <li>{@link #REQUEST}（C2S）：客户端把「当前世界里出现的列车配置」发给服务端，请求一份快照；</li>
 *   <li>{@link #SNAPSHOT}（S2C）：服务端回传「站台 id → 记录列表」的裁剪后快照；</li>
 *   <li>{@link #OBSERVE}（C2S）：客户端上报一次真实观测（车门朝哪侧开），服务端校验并落库；</li>
 *   <li>{@link #UPDATE}（S2C）：服务端把落库后的记录广播给全服玩家（也用于回填上报者自己的记录）。</li>
 * </ul>
 * <b>本类是双端共享的</b>：禁止引用任何客户端类（{@code net.minecraft.client.*}、
 * {@code org.mtr.mod.client.*}、{@code mtr.client.*}）与 {@link DoorOpenCacheClient}；
 * S2C 接收器与 handler 全部在 {@code DoorOpenCacheClient} 里注册。
 * <p>
 * 读写工具（{@link #writeRecord}/{@link #readRecord}/{@link #writeSnapshot}/{@link #readSnapshot}）
 * 必须严格对称：写与读在同一个类里成对出现，避免「顺序写错但 javac 通过」。
 * <p>
 * MTR3 的站台存在性校验走 {@link RailwayData#getInstance(Level)} 的 {@code dataCache.platformIdMap}
 * （MTR4 版走 TSC 的 {@code Simulator}，两份除这一处外逐字相同）。
 */
public final class DoorOpenCacheNet {

    /** C2S：请求快照（附客户端当前看到的列车配置，服务端据此优先下发命中的记录） */
    public static final ResourceLocation REQUEST = new ResourceLocation("fangsu", "door_side_cache_request");
    /** S2C：服务端回传快照 */
    public static final ResourceLocation SNAPSHOT = new ResourceLocation("fangsu", "door_side_cache_snapshot");
    /** C2S：上报一次车门方向观测 */
    public static final ResourceLocation OBSERVE = new ResourceLocation("fangsu", "door_side_cache_observe");
    /** S2C：服务端广播一条落库后的记录 */
    public static final ResourceLocation UPDATE = new ResourceLocation("fangsu", "door_side_cache_update");

    /** REQUEST 里最多携带的配置项数（契约固定 64，超出部分丢弃） */
    private static final int MAX_REQUEST_CONFIGS = 64;
    /** 读取 REQUEST 时的硬上界：超过这个数只读到这个数为止（防恶意超长 varint 撑爆内存） */
    private static final int MAX_REQUEST_CONFIGS_READ = 512;
    /** 单条记录读入时允许的车节数硬上界（正常 ≤ {@link DoorOpenCacheData#MAX_CARS}，留余量给越界判定） */
    private static final int MAX_SIDES_ON_WIRE = 4096;
    /** 单份快照允许的站台数硬上界（正常 ≤ {@link DoorOpenCacheData#MAX_PLATFORMS}） */
    private static final int MAX_SNAPSHOT_PLATFORMS_READ = 8192;
    /** 单个站台允许的记录数硬上界（正常 ≤ {@link DoorOpenCacheData#MAX_PER_PLATFORM}） */
    private static final int MAX_SNAPSHOT_RECORDS_READ = 64;

    /** 同一玩家两次上报的最小间隔（毫秒）：更密的直接丢弃，防刷包 */
    private static final long MIN_OBSERVE_INTERVAL_MS = 500L;
    /** 同内容记录的广播去重窗口（毫秒） */
    private static final long BROADCAST_DEDUP_MS = 30_000L;
    /** 限流表 / 去重表的清理阈值（超过就整表清空，成本可忽略且不会无限增长） */
    private static final int MAX_TRACKED_ENTRIES = 4096;

    /** 每玩家最近一次上报时间（服务端内存表，不落盘） */
    private static final Map<UUID, Long> LAST_OBSERVE_AT = new HashMap<>();
    /** 「站台 + 配置」最近一次广播时间（30s 去重） */
    private static final Map<String, Long> LAST_BROADCAST_AT = new HashMap<>();

    private DoorOpenCacheNet() {
    }

    /** 客户端声明的列车配置（用于让服务端优先下发命中项） */
    public record ConfigKey(String trainTypeId, int trainCars, int lengthMeters) {
    }

    // ==================== 注册 ====================

    /** 服务端：C2S 接收器注册（由 {@code com.fangsu.network.ModNetwork#init} 末尾调用） */
    public static void registerServer() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, REQUEST, DoorOpenCacheNet::handleRequest);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, OBSERVE, DoorOpenCacheNet::handleObserve);
    }

    // ==================== 客户端发送 ====================

    /** 客户端：请求快照。preferred 为「当前世界里的列车配置」，服务端据此把命中项排到最前。 */
    public static void requestSnapshotC2S(List<ConfigKey> preferred) {
        final List<ConfigKey> list = preferred == null ? Collections.emptyList() : preferred;
        final int count = Math.min(list.size(), MAX_REQUEST_CONFIGS);
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            final ConfigKey key = list.get(i);
            if (key == null) {
                buf.writeUtf("");
                buf.writeVarInt(0);
                buf.writeVarInt(0);
                continue;
            }
            buf.writeUtf(cutType(key.trainTypeId()));
            buf.writeVarInt(key.trainCars());
            buf.writeVarInt(key.lengthMeters());
        }
        NetworkManager.sendToServer(REQUEST, buf);
    }

    /** 客户端：上报一次观测（observedAt 只是占位，服务端会覆盖成服务端时间） */
    public static void sendObserveC2S(long platformId, DoorOpenRecord record) {
        if (record == null) {
            return;
        }
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeLong(platformId);
        writeRecord(buf, record);
        NetworkManager.sendToServer(OBSERVE, buf);
    }

    // ==================== 读写工具 ====================

    /** 记录线格式：type / cars / len / reversed / observedAt / sides。 */
    public static void writeRecord(FriendlyByteBuf buf, DoorOpenRecord record) {
        final DoorOpenRecord.DoorOpenSide[] sides = record == null ? null : record.sides();
        final int sideCount = sides == null ? 0 : sides.length;
        buf.writeUtf(cutType(record == null ? null : record.trainTypeId()));
        buf.writeVarInt(record == null ? 0 : record.trainCars());
        buf.writeVarInt(record == null ? 0 : record.lengthMeters());
        buf.writeBoolean(record != null && record.reversed());
        buf.writeLong(record == null ? 0L : record.observedAt());
        buf.writeVarInt(sideCount);
        for (int i = 0; i < sideCount; i++) {
            final DoorOpenRecord.DoorOpenSide side = sides[i];
            // null 视作 NONE：线上不传 null，读侧再按 ordinal 安全还原
            buf.writeByte((side == null ? DoorOpenRecord.DoorOpenSide.NONE : side).ordinal());
        }
    }

    public static DoorOpenRecord readRecord(FriendlyByteBuf buf) {
        final String trainTypeId = buf.readUtf();
        final int trainCars = buf.readVarInt();
        final int lengthMeters = buf.readVarInt();
        final boolean reversed = buf.readBoolean();
        final long observedAt = buf.readLong();
        final int wireCount = buf.readVarInt();
        final int sideCount = Math.max(0, Math.min(wireCount, MAX_SIDES_ON_WIRE));
        final DoorOpenRecord.DoorOpenSide[] sides = new DoorOpenRecord.DoorOpenSide[sideCount];
        for (int i = 0; i < sideCount; i++) {
            sides[i] = DoorOpenRecord.DoorOpenSide.fromOrdinalSafe(buf.readByte());
        }
        return new DoorOpenRecord(sides, trainTypeId, trainCars, lengthMeters, reversed, observedAt);
    }

    /** 快照线格式：站台数 + ×(platformId, 记录数, 记录)。 */
    public static void writeSnapshot(FriendlyByteBuf buf, Map<Long, List<DoorOpenRecord>> snapshot) {
        final Map<Long, List<DoorOpenRecord>> map = snapshot == null ? Collections.emptyMap() : snapshot;
        buf.writeVarInt(map.size());
        for (final Map.Entry<Long, List<DoorOpenRecord>> entry : map.entrySet()) {
            final long platformId = entry.getKey() == null ? 0L : entry.getKey();
            final List<DoorOpenRecord> records = entry.getValue() == null ? Collections.emptyList() : entry.getValue();
            buf.writeLong(platformId);
            buf.writeVarInt(records.size());
            for (final DoorOpenRecord record : records) {
                writeRecord(buf, record);
            }
        }
    }

    public static Map<Long, List<DoorOpenRecord>> readSnapshot(FriendlyByteBuf buf) {
        final int wirePlatforms = buf.readVarInt();
        final int platformCount = Math.max(0, Math.min(wirePlatforms, MAX_SNAPSHOT_PLATFORMS_READ));
        final Map<Long, List<DoorOpenRecord>> result = new LinkedHashMap<>();
        for (int p = 0; p < platformCount; p++) {
            final long platformId = buf.readLong();
            final int wireRecords = buf.readVarInt();
            final int recordCount = Math.max(0, Math.min(wireRecords, MAX_SNAPSHOT_RECORDS_READ));
            final List<DoorOpenRecord> records = new ArrayList<>(recordCount);
            for (int r = 0; r < recordCount; r++) {
                records.add(readRecord(buf));
            }
            result.put(platformId, records);
        }
        return result;
    }

    // ==================== 服务端 handler ====================

    private static void handleRequest(FriendlyByteBuf buf, NetworkManager.PacketContext ctx) {
        // 载荷必须在主线程排队之前读完：排队回调可能在网络缓冲区释放之后才执行
        final List<ConfigKey> preferred = readPreferred(buf);

        ctx.queue(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) {
                return;
            }
            final DoorOpenCacheData data;
            try {
                data = DoorOpenCacheData.get(player.server);
            } catch (Throwable t) {
                Main.LOGGER.warn("[DoorOpenCache] 读取缓存数据失败，跳过本次快照请求", t);
                return;
            }
            if (data == null) {
                return;
            }
            final Map<Long, List<DoorOpenRecord>> payload = buildSnapshot(data.snapshot(), preferred);
            final FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
            writeSnapshot(out, payload);
            NetworkManager.sendToPlayer(player, SNAPSHOT, out);
            Main.debug("[DoorOpenCache] 下发快照：{} 个站台（优先配置 {} 条）", payload.size(), preferred.size());
        });
    }

    /** 读取 REQUEST 的配置列表（与 {@link #requestSnapshotC2S} 严格对称）。 */
    private static List<ConfigKey> readPreferred(FriendlyByteBuf buf) {
        final int wireCount = buf.readVarInt();
        final int count = Math.max(0, Math.min(wireCount, MAX_REQUEST_CONFIGS_READ));
        final List<ConfigKey> preferred = new ArrayList<>(Math.min(count, MAX_REQUEST_CONFIGS));
        for (int i = 0; i < count; i++) {
            final String type = buf.readUtf();
            final int cars = buf.readVarInt();
            final int length = buf.readVarInt();
            if (preferred.size() < MAX_REQUEST_CONFIGS) {
                preferred.add(new ConfigKey(type, cars, length));
            }
        }
        return preferred;
    }

    private static void handleObserve(FriendlyByteBuf buf, NetworkManager.PacketContext ctx) {
        // 所有字段在 ctx.queue 之前读完
        final long platformId = buf.readLong();
        final DoorOpenRecord record = readRecord(buf);

        ctx.queue(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) {
                return;
            }
            // 1) 范围校验：非法数据不入库、不广播
            if (platformId <= 0L) {
                Main.debug("[DoorOpenCache] 丢弃上报：platformId 非法（{}）", platformId);
                return;
            }
            if (record == null || !DoorOpenCacheData.isValidRecord(record)) {
                Main.debug("[DoorOpenCache] 丢弃上报：记录超出允许范围 @ {}", platformId);
                return;
            }
            // 车节数组长度必须与声明的编组一致，否则「第 i 节」的语义不成立
            if (record.sides().length != record.trainCars()) {
                Main.debug("[DoorOpenCache] 丢弃上报：车节数与编组数不符（{} != {}）@ {}",
                        record.sides().length, record.trainCars(), platformId);
                return;
            }
            // 2) 每玩家限流：正常观测最多每秒几次，低于 500ms 的一律当刷包
            final UUID uuid = player.getUUID();
            final long now = System.currentTimeMillis();
            final Long last = LAST_OBSERVE_AT.get(uuid);
            if (last != null && now - last < MIN_OBSERVE_INTERVAL_MS) {
                return;
            }
            LAST_OBSERVE_AT.put(uuid, now);
            if (LAST_OBSERVE_AT.size() > MAX_TRACKED_ENTRIES) {
                LAST_OBSERVE_AT.clear();
            }
            // 3) 服务端盖章：客户端时间戳只作占位，一律以服务端时间为准
            final DoorOpenRecord stamped = new DoorOpenRecord(
                    record.sides(), record.trainTypeId(), record.trainCars(),
                    record.lengthMeters(), record.reversed(), now);
            // 4) 站台存在性校验（MTR3：查服务端 RailwayData 的站台表；表空则放行）
            if (!platformExists(player, platformId)) {
                Main.debug("[DoorOpenCache] 丢弃上报：站台 {} 不在服务端数据里", platformId);
                return;
            }
            // 5) 落库 + 广播
            commitAndBroadcast(player, platformId, stamped);
        });
    }

    /**
     * 站台是否存在于服务端数据里。
     * <p>
     * MTR3 的站台表在 {@code RailwayData.getInstance(level).dataCache.platformIdMap}，
     * 属于服务端主线程数据，直接 {@code containsKey} 即可。
     * <b>表为空（世界还没同步/无站台）时一律放行</b>：此时无法判定，宁可入库也不丢数据。
     */
    private static boolean platformExists(ServerPlayer player, long platformId) {
        //#if MC_VERSION >= 12000
        final Level level = player.level();
        //#else
        //$$ final Level level = player.level;
        //#endif
        if (level == null) {
            return true;
        }
        try {
            final RailwayData railwayData = RailwayData.getInstance(level);
            if (railwayData == null || railwayData.dataCache == null) {
                return true;
            }
            final Map<Long, ?> platformIdMap = railwayData.dataCache.platformIdMap;
            // 表为空 = 服务端还没有站台数据，无法判定 → 放行
            return platformIdMap.isEmpty() || platformIdMap.containsKey(platformId);
        } catch (Throwable t) {
            // 读取失败（世界正在卸载等）不应阻断上报，放行并记 debug
            Main.debug("[DoorOpenCache] 站台校验异常，放行：{}", t.getClass().getSimpleName());
            return true;
        }
    }

    /** 落库 + 广播：同内容 30s 内不重复广播，其余情况全服逐个下发 UPDATE。 */
    private static void commitAndBroadcast(ServerPlayer player, long platformId, DoorOpenRecord record) {
        final DoorOpenCacheData data;
        try {
            data = DoorOpenCacheData.get(player.server);
        } catch (Throwable t) {
            Main.LOGGER.warn("[DoorOpenCache] 读取缓存数据失败，丢弃本次观测 @ {}", platformId, t);
            return;
        }
        if (data == null) {
            return;
        }
        // 先看「落库前后是否同内容」：同内容的记录 30s 内不再广播，避免到站期间刷包
        boolean samePayload = false;
        final List<DoorOpenRecord> existing = data.snapshot().get(platformId);
        if (existing != null) {
            for (final DoorOpenRecord old : existing) {
                if (old != null && old.samePayload(record)) {
                    samePayload = true;
                    break;
                }
            }
        }
        data.upsert(platformId, record);

        final long now = System.currentTimeMillis();
        final String dedupKey = platformId + "|" + record.trainTypeId() + "|" + record.trainCars() + "|" + record.lengthMeters();
        final Long lastBroadcast = LAST_BROADCAST_AT.get(dedupKey);
        if (samePayload && lastBroadcast != null && now - lastBroadcast < BROADCAST_DEDUP_MS) {
            return;
        }
        LAST_BROADCAST_AT.put(dedupKey, now);
        if (LAST_BROADCAST_AT.size() > MAX_TRACKED_ENTRIES) {
            LAST_BROADCAST_AT.clear();
        }

        // 载荷只序列化一次，再给每个玩家包一份独立的读取视图：
        // Architectury 的 sendToPlayer 会消费缓冲区，同一个 buf 连发多人时第二人起会拿到空包
        final FriendlyByteBuf template = new FriendlyByteBuf(Unpooled.buffer());
        template.writeLong(platformId);
        writeRecord(template, record);
        final byte[] payload = new byte[template.readableBytes()];
        template.readBytes(payload);

        for (final ServerPlayer target : player.server.getPlayerList().getPlayers()) {
            if (target == null) {
                continue;
            }
            final FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
            NetworkManager.sendToPlayer(target, UPDATE, out);
        }
        Main.debug("[DoorOpenCache] 站台 {} 记录已入库并广播（{} 节，type={}）",
                platformId, record.trainCars(), record.trainTypeId());
    }

    /** 快照裁剪：剔除含 UNKNOWN / 非法的记录，按「命中客户端配置优先、再按 observedAt 降序」裁剪到上限。 */
    private static Map<Long, List<DoorOpenRecord>> buildSnapshot(Map<Long, List<DoorOpenRecord>> source, List<ConfigKey> preferred) {
        final Map<Long, List<DoorOpenRecord>> result = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        for (final Map.Entry<Long, List<DoorOpenRecord>> entry : source.entrySet()) {
            final Long platformId = entry.getKey();
            if (platformId == null || platformId <= 0L) {
                continue;
            }
            final List<DoorOpenRecord> records = entry.getValue();
            if (records == null || records.isEmpty()) {
                continue;
            }
            final List<DoorOpenRecord> usable = new ArrayList<>(records.size());
            for (final DoorOpenRecord record : records) {
                // 契约：快照里禁止出现 UNKNOWN；非法条目一律跳过
                if (record == null || hasUnknownSide(record) || !DoorOpenCacheData.isValidRecord(record)) {
                    continue;
                }
                usable.add(record);
            }
            if (usable.isEmpty()) {
                continue;
            }
            usable.sort((a, b) -> {
                final boolean aHit = matchesPreferred(a, preferred);
                final boolean bHit = matchesPreferred(b, preferred);
                if (aHit != bHit) {
                    return aHit ? -1 : 1;
                }
                return Long.compare(b.observedAt(), a.observedAt());
            });
            final int limit = Math.min(usable.size(), DoorOpenCacheData.MAX_PER_PLATFORM);
            final List<DoorOpenRecord> trimmed = new ArrayList<>(limit);
            for (int i = 0; i < limit; i++) {
                trimmed.add(usable.get(i));
            }
            result.put(platformId, trimmed);
        }
        return result;
    }

    private static boolean matchesPreferred(DoorOpenRecord record, List<ConfigKey> preferred) {
        if (preferred == null || preferred.isEmpty()) {
            return false;
        }
        for (final ConfigKey key : preferred) {
            if (key == null || key.trainTypeId() == null) {
                continue;
            }
            if (record.matchesConsist(key.trainTypeId(), key.trainCars(), key.lengthMeters())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnknownSide(DoorOpenRecord record) {
        final DoorOpenRecord.DoorOpenSide[] sides = record.sides();
        if (sides == null || sides.length == 0) {
            return true;
        }
        for (final DoorOpenRecord.DoorOpenSide side : sides) {
            if (!DoorOpenRecord.DoorOpenSide.isStorable(side)) {
                return true;
            }
        }
        return false;
    }

    /** 车型 id 截断：线上用 {@code writeUtf}，超长字符串既浪费带宽也容易被当成异常数据。 */
    private static String cutType(String trainTypeId) {
        if (trainTypeId == null) {
            return "";
        }
        return trainTypeId.length() > DoorOpenCacheData.MAX_TYPE_LEN
                ? trainTypeId.substring(0, DoorOpenCacheData.MAX_TYPE_LEN)
                : trainTypeId;
    }
}
