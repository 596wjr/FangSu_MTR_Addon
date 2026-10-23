package com.fangsu.train;

import com.fangsu.Main;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开门方向缓存的客户端存储（{@code @Environment(EnvType.CLIENT)}）。
 * <p>
 * 职责：
 * <ul>
 *   <li>注册 S2C 接收器（{@link DoorOpenCacheNet#SNAPSHOT} / {@link DoorOpenCacheNet#UPDATE}），
 *       收到后 {@code ctx.queue(...)} 切到客户端主线程再动缓存；</li>
 *   <li>安装 {@link ClientTickEvent#CLIENT_POST}：检测世界切换（{@link ClientLevel} 引用变化）、
 *       回调观测端的 tick、按列车配置变化请求刷新快照；</li>
 *   <li>对外提供 {@link #query}，供 LCD（{@code TrainStatus}）读「预测开门方向」；</li>
 *   <li>{@link #applyUpdate} 同时服务「本地乐观写入」与「服务端广播回填」两条路径。</li>
 * </ul>
 * <b>本类不直接依赖 MTR 的列车 / 车辆类</b>：观测端的 tick 与「当前列车配置」
 * 由各版本观测端通过 {@link #init(Runnable, TrainConfigSource)} 注入，
 * 因此 MTR3 / MTR4 两份文件可以逐字相同。
 * <p>
 * 并发：{@link #byPlatform} 是 {@code volatile} 的不可变快照，写时整表替换、读无锁；
 * 所有写操作都发生在客户端主线程（S2C 走 {@code ctx.queue}，观测走 tick），因此不需要锁。
 */
@Environment(EnvType.CLIENT)
public final class DoorOpenCacheClient {

    /** 空表：世界退出/进入时复用同一个不可变实例，避免反复分配 */
    private static final Map<Long, List<DoorOpenRecord>> EMPTY = Collections.emptyMap();

    /** 观测端注入：遍历当前世界里的列车，返回去重后的配置列表（可能为空表） */
    public interface TrainConfigSource {
        List<DoorOpenCacheNet.ConfigKey> currentConfigs();
    }

    /** 站台 id → 记录列表（不可变快照，写时整表替换） */
    private static volatile Map<Long, List<DoorOpenRecord>> byPlatform = EMPTY;

    private static Runnable ticker;
    private static TrainConfigSource configSource;

    /** 上次看到的客户端世界引用（世界切换检测点，只由 tick 更新） */
    private static ClientLevel lastLevel;

    /** 上次请求快照时携带的配置列表 */
    private static List<DoorOpenCacheNet.ConfigKey> lastRequestedConfigs = Collections.emptyList();
    /** 上次请求快照的时间戳（毫秒） */
    private static long lastRequestAt = 0L;

    /**
     * 缓存内容版本号：每次快照 / 单条写入 / 进出世界都自增。
     * <p>
     * LCD 的「预测值」在站台 id 不变时，也需要感知「本次到站的观测刚写进缓存」
     * （用户要求：到站开门后这一站立刻从 UNKNOWN 变为实际方向），因此用它做一次廉价的失效判断。
     */
    private static volatile long generation = 0L;

    /** 防止重复注册（平台入口/热重载可能调用两次） */
    private static boolean registered = false;

    private DoorOpenCacheClient() {
    }

    // ==================== 初始化 ====================

    /**
     * 客户端入口（各版本 {@code MainClient.initClient()} 里调用一次）：
     * 注册 S2C 接收器 + 安装 {@link ClientTickEvent#CLIENT_POST}。
     */
    public static void initClient() {
        if (registered) {
            return;
        }
        registered = true;
        // S2C：快照与单条更新。载荷必须在 ctx.queue 之前读完（排队回调可能在缓冲区释放之后才执行）
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DoorOpenCacheNet.SNAPSHOT, (buf, ctx) -> {
            final Map<Long, List<DoorOpenRecord>> snapshot = DoorOpenCacheNet.readSnapshot(buf);
            ctx.queue(() -> applySnapshot(snapshot));
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, DoorOpenCacheNet.UPDATE, (buf, ctx) -> {
            final long platformId = buf.readLong();
            final DoorOpenRecord record = DoorOpenCacheNet.readRecord(buf);
            ctx.queue(() -> applyUpdate(platformId, record));
        });
        ClientTickEvent.CLIENT_POST.register(client -> tick(client));
        Main.debug("[DoorOpenCache] 客户端通道已就绪（SNAPSHOT/UPDATE + 世界切换检测）");
    }

    /** 观测端注入 tick 与配置来源（不注入时 tick 只做世界切换与刷新，不观测）。 */
    public static void init(Runnable tick, TrainConfigSource source) {
        ticker = tick;
        configSource = source;
    }

    /** 只注入配置来源（没有独立 tick 的版本用）。 */
    public static void init(TrainConfigSource source) {
        init(null, source);
    }

    // ==================== 世界切换 ====================

    public static void onWorldJoin() {
        // 新世界的缓存与服务端数据都可能不同：整表清空，等快照回来再填
        byPlatform = EMPTY;
        lastRequestedConfigs = Collections.emptyList();
        // 主动拉一次「无优选配置」的全量快照：刚进世界时列车列表往往还没同步，
        // 而 requestRefreshIfTrainsChanged 在配置为空时直接跳过，会让「上次运行已有缓存」
        // 的情况下第一帧 LCD 仍闪 UNKNOWN。空配置请求由服务端返回全部站台缓存。
        lastRequestAt = System.currentTimeMillis();
        try {
            DoorOpenCacheNet.requestSnapshotC2S(Collections.emptyList());
        } catch (Throwable t) {
            // 进世界瞬间连接可能还没就绪：失败不影响后续「按配置刷新」那条路径
            Main.debug("[DoorOpenCache] 进入世界时的全量快照请求失败：{}", t.getClass().getSimpleName());
        }
        Main.debug("[DoorOpenCache] 进入世界，缓存已清空并已请求全量快照");
        generation++;
    }

    public static void onWorldLeave() {
        byPlatform = EMPTY;
        lastRequestedConfigs = Collections.emptyList();
        lastRequestAt = 0L;
        generation++;
        Main.debug("[DoorOpenCache] 离开世界，缓存已清空");
    }

    private static void tick(Minecraft client) {
        if (client == null) {
            return;
        }
        // 世界切换检测：引用变化即视为进入/退出世界（与 MainClient 里的隐藏线路检测同款）
        final ClientLevel level = client.level;
        if (level != lastLevel) {
            lastLevel = level;
            if (level == null) {
                onWorldLeave();
            } else {
                onWorldJoin();
            }
        }
        // 观测端的 tick（由各版本注入；内部可能上报观测）
        final Runnable currentTicker = ticker;
        if (currentTicker != null) {
            try {
                currentTicker.run();
            } catch (Throwable t) {
                // 观测异常不能拖垮客户端 tick：记一次错误日志后继续
                Main.LOGGER.error("[DoorOpenCache] 观测端 tick 抛出异常", t);
            }
        }
        requestRefreshIfTrainsChanged();
    }

    // ==================== 缓存写入 ====================

    /** 整份快照覆盖（服务端下发）：剔除非法条目，按契约上限裁剪。 */
    public static void applySnapshot(Map<Long, List<DoorOpenRecord>> map) {
        generation++;
        if (map == null || map.isEmpty()) {
            byPlatform = EMPTY;
            return;
        }
        final Map<Long, List<DoorOpenRecord>> next = new HashMap<>();
        for (final Map.Entry<Long, List<DoorOpenRecord>> entry : map.entrySet()) {
            if (next.size() >= DoorOpenCacheData.MAX_PLATFORMS) {
                break;
            }
            final Long platformId = entry.getKey();
            if (platformId == null || platformId <= 0L) {
                continue;
            }
            final List<DoorOpenRecord> records = sanitize(entry.getValue());
            if (records.isEmpty()) {
                continue;
            }
            next.put(platformId, records);
        }
        byPlatform = next.isEmpty() ? EMPTY : Collections.unmodifiableMap(next);
        Main.debug("[DoorOpenCache] 应用快照：{} 个站台", next.size());
    }

    /**
     * 单条记录写入（乐观写入 / 服务端广播共用）。
     * <p>
     * 按 {@code (trainTypeId, trainCars, lengthMeters)} 去重覆盖、新记录排最前、
     * 单站台记录数不超过 {@link DoorOpenCacheData#MAX_PER_PLATFORM}。
     */
    public static void applyUpdate(long platformId, DoorOpenRecord record) {
        if (platformId <= 0L || record == null) {
            return;
        }
        // 契约：缓存里不保存含 UNKNOWN 的记录（UNKNOWN 只由 query 在「无记录」时整组返回）
        if (!isStorable(record)) {
            Main.debug("[DoorOpenCache] 忽略含 UNKNOWN 的记录 @ {}", platformId);
            return;
        }
        final Map<Long, List<DoorOpenRecord>> current = byPlatform;
        final List<DoorOpenRecord> existing = current.get(platformId);
        final List<DoorOpenRecord> merged = new ArrayList<>(DoorOpenCacheData.MAX_PER_PLATFORM);
        merged.add(record);
        if (existing != null) {
            for (final DoorOpenRecord old : existing) {
                if (old == null) {
                    continue;
                }
                // 同配置（车型 + 编组 + 长度 ±2m）= 同一条记录，直接覆盖
                if (old.matchesConsist(record.trainTypeId(), record.trainCars(), record.lengthMeters())) {
                    continue;
                }
                if (merged.size() >= DoorOpenCacheData.MAX_PER_PLATFORM) {
                    break;
                }
                merged.add(old);
            }
        }
        final Map<Long, List<DoorOpenRecord>> next = new HashMap<>(current);
        next.put(platformId, Collections.unmodifiableList(merged));
        byPlatform = Collections.unmodifiableMap(next);
        generation++;
    }

    /** 缓存内容版本号（见 {@link #generation} 的说明）；LCD 侧据此判断是否需要重新查询预测值。 */
    public static long generation() {
        return generation;
    }

    // ==================== 快照请求 ====================

    /**
     * 列车配置变化时请求一次快照。
     * <p>
     * 契约：{@code configSource} 未注入则跳过；配置与上次相同且 5s 内则跳过；否则发 REQUEST。
     */
    public static void requestRefreshIfTrainsChanged() {
        final TrainConfigSource source = configSource;
        if (source == null) {
            return;
        }
        // 不在世界里发 C2S 包会抛异常：先确认有连接
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            return;
        }
        final List<DoorOpenCacheNet.ConfigKey> configs;
        try {
            configs = source.currentConfigs();
        } catch (Throwable t) {
            Main.LOGGER.error("[DoorOpenCache] 读取当前列车配置失败", t);
            return;
        }
        if (configs == null || configs.isEmpty()) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (configs.equals(lastRequestedConfigs) && now - lastRequestAt < 5_000L) {
            return;
        }
        lastRequestedConfigs = Collections.unmodifiableList(new ArrayList<>(configs));
        lastRequestAt = now;
        try {
            DoorOpenCacheNet.requestSnapshotC2S(configs);
        } catch (Throwable t) {
            Main.LOGGER.error("[DoorOpenCache] 发送快照请求失败", t);
        }
    }

    // ==================== 查询 ====================

    /**
     * 查询某站台某配置的开门方向（每车节一个）。
     * <p>
     * 评分：车型相同 +4、编组数相同 +2、长度差 ≤2m +1；同分取 {@code observedAt} 更新者。
     * 该站台<b>完全没有记录</b>时整组返回 {@link DoorOpenRecord.DoorOpenSide#UNKNOWN}；
     * 有记录时用 {@link DoorOpenRecord#sideAt(int)}（绝不返回 UNKNOWN，越界取末尾）。
     */
    public static DoorOpenRecord.DoorOpenSide[] query(long platformId, String trainTypeId, int trainCars, int lengthMeters) {
        final int cars = Math.max(0, Math.min(trainCars, DoorOpenCacheData.MAX_CARS));
        final DoorOpenRecord.DoorOpenSide[] unknown = new DoorOpenRecord.DoorOpenSide[cars];
        for (int i = 0; i < cars; i++) {
            unknown[i] = DoorOpenRecord.DoorOpenSide.UNKNOWN;
        }
        if (platformId <= 0L) {
            return unknown;
        }
        final List<DoorOpenRecord> records = byPlatform.get(platformId);
        if (records == null || records.isEmpty()) {
            return unknown;
        }
        DoorOpenRecord best = null;
        int bestScore = -1;
        for (final DoorOpenRecord record : records) {
            if (record == null) {
                continue;
            }
            int score = 0;
            if (trainTypeId != null && trainTypeId.equals(record.trainTypeId())) {
                score += 4;
            }
            if (record.trainCars() == trainCars) {
                score += 2;
            }
            if (Math.abs(record.lengthMeters() - lengthMeters) <= 2) {
                score += 1;
            }
            if (score > bestScore || (score == bestScore && best != null && record.observedAt() > best.observedAt())) {
                best = record;
                bestScore = score;
            }
        }
        if (best == null) {
            return unknown;
        }
        final DoorOpenRecord.DoorOpenSide[] result = new DoorOpenRecord.DoorOpenSide[cars];
        for (int i = 0; i < cars; i++) {
            result[i] = best.sideAt(i);
        }
        return result;
    }

    // ==================== 内部工具 ====================

    /** 列表清洗：丢弃 null / 含 UNKNOWN 的记录，裁剪到单站台上限（保持原顺序）。 */
    private static List<DoorOpenRecord> sanitize(List<DoorOpenRecord> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        final List<DoorOpenRecord> result = new ArrayList<>(Math.min(records.size(), DoorOpenCacheData.MAX_PER_PLATFORM));
        for (final DoorOpenRecord record : records) {
            if (record == null || !isStorable(record)) {
                continue;
            }
            if (result.size() >= DoorOpenCacheData.MAX_PER_PLATFORM) {
                break;
            }
            result.add(record);
        }
        return result.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(result);
    }

    /** 记录的所有车节都是可存储值（LEFT/RIGHT/BOTH/NONE），且至少一节。 */
    private static boolean isStorable(DoorOpenRecord record) {
        final DoorOpenRecord.DoorOpenSide[] sides = record.sides();
        if (sides == null || sides.length == 0) {
            return false;
        }
        for (final DoorOpenRecord.DoorOpenSide side : sides) {
            if (!DoorOpenRecord.DoorOpenSide.isStorable(side)) {
                return false;
            }
        }
        return true;
    }
}
