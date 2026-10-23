package com.fangsu.train;

import com.fangsu.Main;
import mtr.client.ClientData;
import mtr.data.TrainClient;
import mtr.path.PathData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MTR3 端的「开门方向观测器」。
 * <p>
 * <b>数据来源</b>：{@code mtr.data.TrainClient.simulateCar(...)}（见 {@link com.fangsu.mixin.TrainClientDoorSideMixin}）。
 * 该方法是每节车厢每帧唯一的入口，并且拿到了 {@code doorLeftOpen / doorRightOpen} 这对
 * 「本帧这一节车厢实际贴着哪一侧站台」的布尔值——这正是我们要缓存的东西。
 * <p>
 * <b>线程模型</b>：{@code onCarObserved} 在渲染线程被 mixin 调用，因此它<b>只写内存状态，绝不发包</b>；
 * 真正的上报（落本地缓存 + 发 C2S）只在客户端 tick（{@link #tick()}）里做。
 * 迭代 {@code ClientData.TRAINS} 一律先做防御性快照（它是普通 {@link java.util.HashSet}，
 * 网络线程会并发增删）。
 * <p>
 * <b>一个「观测回合」的生命周期</b>：
 * <ol>
 *   <li>列车停站开门时，{@code simulateCar} 每帧都会带着 {@code doorLeftOpen/doorRightOpen} 进来，
 *       累积到 {@link Episode}；</li>
 *   <li>一整列车的所有车节都已观测到、且与上次上报的内容不同 → 立即上报；</li>
 *   <li>超过 {@link #EPISODE_TIMEOUT_TICKS} tick 没有新观测 → 收尾（未上报过就补发一次）；</li>
 *   <li>列车从 {@code ClientData.TRAINS} 消失 → 连同 {@link #LAST_SIDES} 一起清理。</li>
 * </ol>
 * <p>
 * <b>为什么只上报完整回合</b>：{@link DoorOpenRecord} 里不允许出现 {@code UNKNOWN}（服务端
 * {@code isValidRecord} 会丢弃，本地缓存写入也会污染 {@code sideAt} 的越界兜底），
 * 而 {@code simulateCar} 只对「位置解算成功」的车节调用，极少数情况下会缺节。
 * 缺节时宁可这一趟不落库，也不写入一条半真半假的记录。
 */
@Environment(EnvType.CLIENT)
public final class DoorSideObserverMtr3 {

    /** 一个回合「静默」这么多 tick 后收尾（列车出站关门后不再有新的开门观测）。 */
    private static final int EPISODE_TIMEOUT_TICKS = 24;

    /** 看门狗：这么久（tick）还一次观测都没收到，就 warn 一次（基本等于 mixin 没生效）。 */
    private static final int WATCHDOG_TICKS = 1200;

    /** 从车头位置起，最多向后看几项 path 才放弃平台解析。 */
    private static final int PLATFORM_LOOKAHEAD = 3;

    /** 防御性上限：车节索引荒谬大时直接丢弃（MTR3 的 trainCars 远小于此值）。 */
    private static final int MAX_CAR_INDEX = 512;

    /** 每列车一个进行中的观测回合。 */
    private static final Map<Long, Episode> EPISODES = new ConcurrentHashMap<>();

    /** 每列车最近一次<b>实际上报</b>的原始（未规范化）开门方向数组，供 LCD 实时值使用。 */
    private static final Map<Long, DoorOpenRecord.DoorOpenSide[]> LAST_SIDES = new ConcurrentHashMap<>();

    /** 客户端 tick 计数（只有 {@link #tick()} 会推进）。 */
    private static int tickCounter;

    /** 是否已经把自己注册进 {@link DoorOpenCacheClient}。 */
    private static volatile boolean initialized;

    /** 是否收到过任何一次 {@code onCarObserved}（看门狗用）。 */
    private static boolean anyObservation;

    /** 看门狗是否已经报过（只报一次）。 */
    private static boolean watchdogWarned;

    /** onCarObserved 里的异常是否已经报过（渲染线程逐帧调用，只报一次以免刷屏）。 */
    private static boolean observeErrorLogged;

    private DoorSideObserverMtr3() {
    }

    /**
     * 把自己挂到 {@link DoorOpenCacheClient} 的客户端 tick 上（幂等）。
     * <p>
     * 注意：契约没有规定「谁调用本方法」。为了不让观测端静默失效，本类在
     * {@link #onCarObserved} 与 {@code TrainStatus.updateRoute()} 里都做了幂等兜底调用；
     * 上层若愿意显式接线，重复调用也是安全的。
     */
    public static void init() {
        // 快路径：初始化只在第一帧付一次锁开销，之后是一次 volatile 读
        if (initialized) {
            return;
        }
        initSlow();
    }

    private static synchronized void initSlow() {
        if (initialized) {
            return;
        }
        // 先置位再注册：DoorOpenCacheClient.init 内部可能立刻回调 currentConfigs()，避免递归。
        initialized = true;
        try {
            DoorOpenCacheClient.init(DoorSideObserverMtr3::tick, DoorSideObserverMtr3::currentConfigs);
            Main.debug("[FangSu 开门缓存] MTR3 观测端已注册");
        } catch (Throwable t) {
            Main.LOGGER.warn("[FangSu 开门缓存] MTR3 观测端注册失败，开门方向缓存将不可用", t);
        }
    }

    /**
     * mixin 的落点：每节车厢每帧调用一次。<b>只写内存，不发包</b>。
     *
     * @param trainId  列车 id（MTR3 的 {@code NameColorDataBase.id}）
     * @param carIndex 车节索引（0 = 车头方向的第一节，与 {@code TrainStatus} 的数组下标一致）
     */
    public static void onCarObserved(long trainId, int carIndex, boolean left, boolean right) {
        try {
            if (!initialized) {
                init();
            }
            anyObservation = true;
            if (carIndex < 0 || carIndex > MAX_CAR_INDEX) {
                return;
            }
            final Episode episode = EPISODES.computeIfAbsent(trainId, id -> new Episode());
            synchronized (episode) {
                episode.ensureCapacity(carIndex + 1);
                if (!episode.seen[carIndex]) {
                    episode.seen[carIndex] = true;
                    episode.seenCount++;
                }
                episode.sides[carIndex] = DoorOpenRecord.DoorOpenSide.of(left, right);
                episode.lastSeenTick = tickCounter;
            }
        } catch (Throwable t) {
            // 渲染线程里绝不能把异常抛回 MTR，否则会打断整帧渲染；只记一条，避免逐帧刷屏
            if (!observeErrorLogged) {
                observeErrorLogged = true;
                Main.LOGGER.warn("[FangSu 开门缓存] MTR3 观测记录失败（train={}，car={}），后续同类错误不再打印",
                        trainId, carIndex, t);
            }
        }
    }

    /**
     * 客户端 tick：上报 / 收尾 / 看门狗。由 {@link DoorOpenCacheClient} 的事件回调驱动。
     */
    public static void tick() {
        tickCounter++;
        try {
            final List<TrainClient> trains = snapshotTrains();
            if (trains == null) {
                // 快照失败（TRAINS 正在被网络线程改动）：本 tick 什么都不做，免得误清状态
                return;
            }

            // 看门狗：有车在跑、但一次开门观测都没收到 —— 基本等于 mixin 没注上（require=0 会静默失效）
            if (!watchdogWarned && !anyObservation && tickCounter >= WATCHDOG_TICKS && !trains.isEmpty()) {
                watchdogWarned = true;
                Main.LOGGER.warn("[FangSu 开门缓存] MTR3 已 {} tick 未收到任何 simulateCar 观测，"
                                + "TrainClientDoorSideMixin 可能未生效，开门方向缓存将保持为空",
                        WATCHDOG_TICKS);
            }

            if (EPISODES.isEmpty()) {
                return;
            }

            // 清理已被销毁的列车（ClientData.TRAINS 是事实来源）
            final Set<Long> alive = new LinkedHashSet<>();
            for (final TrainClient train : trains) {
                alive.add(train.id);
            }
            EPISODES.keySet().removeIf(id -> !alive.contains(id));
            LAST_SIDES.keySet().removeIf(id -> !alive.contains(id));

            if (EPISODES.isEmpty()) {
                return;
            }

            for (final Map.Entry<Long, Episode> entry : EPISODES.entrySet()) {
                final long trainId = entry.getKey();
                final Episode episode = entry.getValue();
                final TrainClient train = findTrain(trains, trainId);
                if (train == null) {
                    // 这一帧没找到（可能正在重建），交给上面的清理逻辑处理
                    continue;
                }

                final int trainCars = train.trainCars;
                final boolean complete;
                final boolean timedOut;
                synchronized (episode) {
                    complete = trainCars > 0 && isComplete(episode, trainCars);
                    timedOut = tickCounter - episode.lastSeenTick > EPISODE_TIMEOUT_TICKS;
                }

                if (complete) {
                    report(train, episode);
                } else if (timedOut) {
                    // 收尾：还没上报过就补发一次（只有完整回合才真的会写缓存/发包）
                    if (!episode.reported) {
                        report(train, episode);
                    }
                    EPISODES.remove(trainId);
                }
            }
        } catch (Throwable t) {
            Main.LOGGER.warn("[FangSu 开门缓存] MTR3 观测端 tick 失败", t);
        }
    }

    /**
     * 最近一次<b>实际上报</b>的原始帧开门方向数组（不翻转、不规范化），没有则 {@code null}。
     * 返回副本，调用方可随意持有。
     */
    public static DoorOpenRecord.DoorOpenSide[] lastSides(long trainId) {
        final DoorOpenRecord.DoorOpenSide[] sides = LAST_SIDES.get(trainId);
        return sides == null ? null : sides.clone();
    }

    /**
     * 当前世界里所有列车配置（去重），供 {@link DoorOpenCacheClient} 请求快照时优先生成。
     */
    private static List<DoorOpenCacheNet.ConfigKey> currentConfigs() {
        final Set<DoorOpenCacheNet.ConfigKey> keys = new LinkedHashSet<>();
        final List<TrainClient> trains = snapshotTrains();
        if (trains == null) {
            return new ArrayList<>();
        }
        for (final TrainClient train : trains) {
            final int cars = train.trainCars;
            if (cars <= 0) {
                continue;
            }
            keys.add(new DoorOpenCacheNet.ConfigKey(trainTypeOf(train), cars, lengthOf(train)));
        }
        return new ArrayList<>(keys);
    }

    // ==================== 内部实现 ====================

    /**
     * 把一个完整回合写进本地缓存并上报服务端。
     * <p>
     * 顺序是硬性要求：<b>先</b> {@code applyUpdate}（本地乐观写入，LCD 立刻能看到）
     * <b>再</b> {@code sendObserveC2S}（服务端盖章后广播给所有人）。
     */
    private static void report(TrainClient train, Episode episode) {
        final int trainCars = train.trainCars;
        final DoorOpenRecord.DoorOpenSide[] raw;
        synchronized (episode) {
            if (trainCars <= 0 || !isComplete(episode, trainCars)) {
                // 缺节：不落库（记录里不允许出现 UNKNOWN）
                return;
            }
            raw = Arrays.copyOf(episode.sides, trainCars);
            if (sameSides(episode.lastReported, raw)) {
                episode.reported = true;
                return;
            }
        }

        if (allNone(raw)) {
            // 全车节都不开门 → 没有缓存价值，不落库
            synchronized (episode) {
                episode.reported = true;
                episode.lastReported = raw.clone();
            }
            return;
        }

        final long platformId = resolvePlatformId(train);
        if (platformId <= 0) {
            // 还没进入任何站台（例如出库途中），保持未上报，等下一次 tick 再试
            return;
        }

        final DoorOpenRecord record = new DoorOpenRecord(
                raw,
                trainTypeOf(train),
                trainCars,
                lengthOf(train),
                train.isReversed(),
                System.currentTimeMillis()
        ).normalized();

        // 原始数组（未规范化）留给 LCD 的实时值
        LAST_SIDES.put(train.id, raw.clone());
        synchronized (episode) {
            episode.lastReported = raw.clone();
            episode.reported = true;
        }

        try {
            DoorOpenCacheClient.applyUpdate(platformId, record);
            DoorOpenCacheNet.sendObserveC2S(platformId, record);
        } catch (Throwable t) {
            Main.LOGGER.warn("[FangSu 开门缓存] MTR3 上报失败（platform={}，train={}）", platformId, train.id, t);
        }
        Main.debug("[FangSu 开门缓存] MTR3 上报站台 " + platformId + "，列车 " + record.trainTypeId()
                + "，车厢数 " + trainCars);
    }

    /**
     * 解析列车当前（或即将停靠的）站台 id。
     * <p>
     * 车头所在 path 项未必是站台项，因此从 {@code headIndex} 起最多向后找
     * {@link #PLATFORM_LOOKAHEAD} 项，取第一个「有停站时间且不是本列车侧线（siding）」的项。
     */
    private static long resolvePlatformId(TrainClient train) {
        try {
            final List<PathData> path = train.path;
            if (path == null || path.isEmpty()) {
                return 0;
            }
            final int headIndex = train.getIndex(0, train.spacing, true);
            for (int offset = 0; offset < PLATFORM_LOOKAHEAD; offset++) {
                final int index = headIndex + offset;
                if (index < 0 || index >= path.size()) {
                    break;
                }
                final PathData pathData = path.get(index);
                if (pathData == null) {
                    continue;
                }
                if (pathData.dwellTime > 0 && pathData.savedRailBaseId != train.sidingId) {
                    return pathData.savedRailBaseId;
                }
            }
        } catch (Throwable t) {
            Main.LOGGER.warn("[FangSu 开门缓存] MTR3 站台解析失败（train={}）", train.id, t);
        }
        return 0;
    }

    /** MTR3 的「列车配置」标识：优先玩家自定义 id，空则退回基础车型。 */
    private static String trainTypeOf(TrainClient train) {
        final String trainId = train.trainId;
        return trainId == null || trainId.isEmpty() ? train.baseTrainType : trainId;
    }

    /** 总长度（米，四舍五入）：MTR3 没有直接的总长字段，用「车节数 × 节间距」近似。 */
    private static int lengthOf(TrainClient train) {
        return (int) Math.round(train.trainCars * (double) train.spacing);
    }

    /**
     * 防御性快照 {@code ClientData.TRAINS}：网络线程会并发增删这个 HashSet。
     * 失败时返回 {@code null}（调用方据此跳过本 tick，而不是当成「一列车都没有」）。
     */
    private static List<TrainClient> snapshotTrains() {
        try {
            return new ArrayList<>(ClientData.TRAINS);
        } catch (Throwable t) {
            return null;
        }
    }

    private static TrainClient findTrain(List<TrainClient> trains, long trainId) {
        for (final TrainClient train : trains) {
            if (train.id == trainId) {
                return train;
            }
        }
        return null;
    }

    /** 这一回合是否已把 {@code trainCars} 节全部观测到且没有 UNKNOWN。 */
    private static boolean isComplete(Episode episode, int trainCars) {
        if (episode.seenCount < trainCars || episode.sides.length < trainCars) {
            return false;
        }
        for (int i = 0; i < trainCars; i++) {
            final DoorOpenRecord.DoorOpenSide side = episode.sides[i];
            if (!episode.seen[i] || side == null || side == DoorOpenRecord.DoorOpenSide.UNKNOWN) {
                return false;
            }
        }
        return true;
    }

    private static boolean allNone(DoorOpenRecord.DoorOpenSide[] sides) {
        for (final DoorOpenRecord.DoorOpenSide side : sides) {
            if (side != DoorOpenRecord.DoorOpenSide.NONE) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameSides(DoorOpenRecord.DoorOpenSide[] a, DoorOpenRecord.DoorOpenSide[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    /** 一列车一次「开门停留」的累积状态。 */
    private static final class Episode {
        /** 下标 = 车节索引；未观测到的位置保持 {@code UNKNOWN}。 */
        DoorOpenRecord.DoorOpenSide[] sides = new DoorOpenRecord.DoorOpenSide[0];
        /** 同下标的「是否已观测到」。 */
        boolean[] seen = new boolean[0];
        int seenCount;
        int lastSeenTick;
        boolean reported;
        /** 上一次真的上报出去的原始数组，用于 24 tick 内的重复去重。 */
        DoorOpenRecord.DoorOpenSide[] lastReported;

        void ensureCapacity(int size) {
            if (sides.length >= size) {
                return;
            }
            final int oldLength = sides.length;
            sides = Arrays.copyOf(sides, size);
            seen = Arrays.copyOf(seen, size);
            Arrays.fill(sides, oldLength, size, DoorOpenRecord.DoorOpenSide.UNKNOWN);
        }
    }
}
