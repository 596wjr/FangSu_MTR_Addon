package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.blocks.BlockLevelCrossing;
import com.fangsu.blocks.ModBlocks;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigSpec;
import com.fangsu.extraConfig.EnumConfig;
import com.fangsu.extraConfig.NumberInputConfig;
import com.fangsu.extraConfig.RunnableConfig;
import com.fangsu.levelcrossing.CellSnapshot;
import com.fangsu.levelcrossing.CrossingSettings;
import com.fangsu.levelcrossing.LevelCrossingDump;
import com.fangsu.levelcrossing.LevelCrossingStyles;
import com.fangsu.levelcrossing.TrackSlice;
import com.fangsu.levelcrossing.geometry.CrossingCollisionBuilder;
import com.fangsu.levelcrossing.geometry.DeckSurface;
import com.fangsu.levelcrossing.geometry.CrossingMeshBuilder;
import com.fangsu.levelcrossing.geometry.GroovedRailProfile;
import com.fangsu.levelcrossing.sample.LevelCrossingSampler;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.render.sowcerext.model.RawMesh;
import com.fangsu.render.sowcerext.model.RawModel;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 平交路口方块实体。
 * <p>
 * 几何链路：{@link LevelCrossingSampler} 采样出 {@link CellSnapshot} → 客户端用它建网格
 * （{@link CrossingMeshBuilder}）与碰撞箱（{@link CrossingCollisionBuilder}）→ 同一份快照经
 * {@link #writeC2S} 同步给服务端，服务端只做校验与碰撞箱，不重算几何（服务端没有 MTR 轨道数据）。
 */
public class BlockEntityLevelCrossing extends FunctionalObjBlockEntity {

    // extraConfig 键集中在这里，避免 UI、采样、存盘各处手写字符串写歪
    public static final String KEY_STYLE = "styleId";
    public static final String KEY_OUTER_EXTEND = "outerExtend";
    public static final String KEY_STEP_COUNT = "stepCount";
    public static final String KEY_BEVEL_WIDTH = "bevelWidth";
    public static final String KEY_DECK_INSET = "deckInset";
    public static final String KEY_RAIL_TOP_OVERRIDE = "railTopOverride";
    public static final String KEY_GROOVE_DEPTH_OVERRIDE = "grooveDepthOverride";
    /**
     * 斜坡坡脚高度（相对本方块底面，0 = 下方方块顶面；下方是半砖时填 -0.5 之类）。
     */
    public static final String KEY_RAMP_FOOT = "rampFoot";
    /** 钢轨顶面偏置（格）：我们的轨顶比原版轨顶高多少，默认 +0.002（2mm）。 */
    public static final String KEY_RAIL_TOP_BIAS = "railTopBias";
    /** 横向缩放倍率（乘在轨道表给的 lateralScale 上，默认 1.0）。 */
    public static final String KEY_LATERAL_SCALE = "lateralScale";
    /** 横向偏移增量（加在轨道表给的 lateralOffset 上，默认 0.0）。 */
    public static final String KEY_LATERAL_OFFSET = "lateralOffset";

    /**
     * 几何快照的 NBT 键，前缀 lc_ 以免和其它方块实体的键撞车。
     */
    private static final String NBT_SNAPSHOT = "lc_snap";

    /**
     * 单条轨道的中心线点数上限，与采样器的输出上限一致。
     */
    private static final int MAX_POINTS_PER_TRACK = 64;

    private static final VoxelShape DEFAULT_SHAPE = Shapes.block();

    /**
     * 碰撞/轮廓形状；快照重建后由 {@link #setCollisionShape(VoxelShape)} 替换。
     */
    private VoxelShape collisionShape = DEFAULT_SHAPE;

    /**
     * 几何是否需要重建。渲染线程读取后立刻清零，写入方只置位，不参与其它状态判断。
     */
    private volatile boolean meshDirty = true;

    /**
     * 快照版本号：{@link #applyComputedResult} 每次写入新快照就自增。
     * <p>
     * 与 {@link #builtVersion} 配套用于<b>杜绝重复重建</b>：实机日志里同一格 1 秒内建了 3 次、
     * stats 完全相同（13796 顶点），就是「渲染回调 + 标脏 + C2S 回环」各自触发了一次构建。
     * 现在只有「快照版本 ≠ 已建版本」或几何被显式标脏时才重建。
     */
    private volatile int snapshotVersion;
    /**
     * 已经成功构建成网格的快照版本；-1 表示还没建过。
     */
    private volatile int builtVersion = -1;

    /**
     * 一次性日志标记：本方块是否已经报过「单个任务耗时 > 阈值」。
     */
    private boolean slowTaskLogged;

    /** 一次性日志标记：「对齐数值」那行 info 每个方块只打一次（用户要拿它定标）。 */
    private boolean alignLogged;

    /**
     * 降级级别：0 = 全明细；1 = 下一次重建改用「中心线隔点取稀 + 碎渣瘦身」。
     * <p>
     * 只在下一次重建生效（{@link CrossingMeshBuilder#lastBuildOverBudget()} 为真时抬一级）——
     * 同一次里绝不建两遍（17 秒的任务里一半是白烧就是这么来的）。
     */
    private int degradeLevel;

    /**
     * 「有快照但一直没建出网格」的方块集合（弱引用，方块卸载/重建后自动消失）。
     * 渲染回调只登记，不再每个方块各刷一条 warn（实机一屏刷了 548 条）。
     */
    private static final java.util.Set<BlockEntityLevelCrossing> NEVER_BUILT =
            java.util.Collections.synchronizedSet(
                    java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    /**
     * 每多少 tick 汇总一次「mesh never built」。
     */
    private static final int NEVER_BUILT_REPORT_TICKS = 200;
    /**
     * 汇总计数（tickHooks 每 tick +1）。
     */
    private static int neverBuiltReportTick;

    /**
     * 放置兜底是否已尝试。{@code setPlacedBy} 那一刻方块实体在客户端不一定已经就绪，
     * 所以第一帧渲染时再补一次采样；置位后不再重试，避免采样失败时每帧重算。
     */
    private boolean autoComputeTried;

    /**
     * 一次性诊断标记：recompute 进入 / 网格构建完成 / 首次渲染各只打一次，避免刷屏。
     */
    private boolean recomputeEnteredLogged;
    private boolean meshBuiltLogged;
    /**
     * 「提前构建」路径（主线程 recompute 里）的一次性日志，与渲染回调路径分开，实机一眼能看出走哪条。
     */
    private boolean meshBuiltEagerLogged;
    private boolean firstRenderLogged;
    private boolean summaryLogged;
    /**
     * 渲染回调里「有快照但 holder 一直为空」的重试计数 + 一次性告警标记。
     */
    private int meshNeverBuiltTicks;
    private boolean meshNeverBuiltLogged;

    /**
     * 一次性日志标记：本方块是否已经因为「提交速率配额」被推迟过。
     */
    private boolean fallbackDeferLogged;

    // ==================== 工作线程（采样 + 构网离线） ====================
    // 用户要求「尽量用独立线程，基本不要在主线程计算」。
    // 纯 CPU 的两步 —— 采样（RailSampleSources.nearby 扫轨道表）与构网（CrossingMeshBuilder.build）——
    // 全部搬到这条单线程上；主线程只做必须主线程的事：
    //   markShapeDirty / sendBlockUpdated / sendUpdateC2S（网络） / uploadLater（内部已转 GL 线程）。
    // 单线程保证「同一时刻只有一个方块在算」，不需要锁也不用担心 CPU 打满。

    /**
     * 专用工作线程：单线程、daemon、有名字（崩溃栈里一眼能认出）。
     */
    private static final java.util.concurrent.ExecutorService LC_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "fangsu-levelcrossing-worker");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });

    /**
     * 每 tick 最多向执行器<b>提交</b>几个任务（只是提交速率，不占主线程 CPU）。
     * 老存档/批量刷新时避免一次把几百个任务塞进队列。
     */
    private static final int SUBMIT_BUDGET_PER_TICK = 2;
    private static int submitsThisTick;
    private static long submitBudgetTick = Long.MIN_VALUE;

    /**
     * 因提交配额被推迟的方块（只是一个「待提交」队列，真正的工作仍在工作线程上）。
     */
    private static final java.util.ArrayDeque<BlockEntityLevelCrossing> PENDING_SUBMITS =
            new java.util.ArrayDeque<>();

    /**
     * <b>优先队列</b>：用户显式动作（放置方块 {@code setPlacedBy} → {@link #recompute()}、配置面板重算）
     * 触发的提交走这里，{@link #tickHooks()} 会<b>先</b>消费它。
     * <p>
     * 实机症状：新放置的方块一直不显示、碰撞箱还是整块 —— 因为它的采样任务排在前几个
     * 1.38 万顶点的大任务后面，等不到执行。放到队首就能立刻开工。
     */
    private static final java.util.ArrayDeque<BlockEntityLevelCrossing> PRIORITY_SUBMITS =
            new java.util.ArrayDeque<>();

    /**
     * 普通待提交队列上限：worker 积压时不让队列无限膨胀（超了丢最老的普通任务，优先队列不丢）。
     */
    private static final int PENDING_SUBMITS_MAX = 64;
    /**
     * 因队列上限被丢弃的普通任务数（诊断用）。
     */
    private static final java.util.concurrent.atomic.AtomicLong DROPPED_SUBMITS =
            new java.util.concurrent.atomic.AtomicLong();

    public static long droppedSubmits() {
        return DROPPED_SUBMITS.get();
    }

    /**
     * 本 BE 是否已在待提交队列里。
     */
    private volatile boolean submitQueued;

    /**
     * 本 BE 是否有任务在执行器里排队/运行（去重）。
     */
    private volatile boolean pendingTask;
    /**
     * 待做任务是否需要重新采样（false = 只重构建网格）。
     */
    private volatile boolean pendingSample;
    /**
     * 主线程捕获的配置快照（工作线程只读它，绝不碰 BlockEntity 的 map / getBlockState）。
     */
    private volatile CrossingSettings pendingSettings;

    /**
     * 邻居查询缓存：<b>只在主线程刷新</b>（getBlockState 不是线程安全的），工作线程只读。
     */
    private volatile boolean[] neighbourCache;
    private long neighbourCacheTick = -1L;
    /**
     * 触发一次「mesh never built」告警所需的渲染回调次数。
     */
    private static final int MESH_NEVER_BUILT_WARN_TICKS = 20;

    /**
     * 单个工作线程任务超过这个耗时（毫秒）就 warn 一次（每方块一次），用于现场定位卡顿。
     */
    private static final long SLOW_TASK_MS = 200L;

    /**
     * 轨顶未解析（工作线程拿不到 NTE 模型包围盒）时的<b>自愈重采样上限</b>。
     * 超过就保留默认值并 warn 一次，避免死循环。
     */
    private static final int RAIL_TOP_RESOLVE_MAX_RETRIES = 2;
    /**
     * 本 BE 已经为「解析轨顶」重新采样过几次。
     */
    private int railTopResolveRetries;
    /**
     * 一次性日志标记：轨顶解析重试用尽（warn）。
     */
    private boolean railTopResolveWarned;

    /**
     * 是否已经排过一次「首帧补算」任务（避免每帧往主线程排队）。
     */
    private boolean initialComputeScheduled;

    /**
     * 当前几何快照，null 表示还没算过。
     * 外部写它请走 {@link #setSnapshot(CellSnapshot)}，否则几何不会被标脏。
     */
    @Nullable
    public CellSnapshot snapshot;

    /**
     * 当前网格的持有者；{@link #whenDisposing()} 必须 close，否则 VAO 泄漏（仓库已知坑）。
     */
    @Nullable
    private DynamicModelHolder meshHolder;

    public BlockEntityLevelCrossing(BlockPos pos, BlockState state) {
        super(ModBlocks.BLOCK_ENTITY_LEVEL_CROSSING.get(), pos, state);
        // 新放置的方块不会走 load()，默认配置必须先在这里落地，否则首次保存会漏键
        ensureDefaults();
    }

    private void ensureDefaults() {
        ensureExtraConfig(KEY_STYLE, LevelCrossingStyles.ASPHALT.id());
        ensureExtraConfig(KEY_OUTER_EXTEND, "0");
        ensureExtraConfig(KEY_STEP_COUNT, "8");
        ensureExtraConfig(KEY_BEVEL_WIDTH, "1.0");
        ensureExtraConfig(KEY_DECK_INSET, "0.010");
        ensureExtraConfig(KEY_RAIL_TOP_OVERRIDE, "-1");
        ensureExtraConfig(KEY_GROOVE_DEPTH_OVERRIDE, "-1");
        ensureExtraConfig(KEY_RAMP_FOOT, "0");
        ensureExtraConfig(KEY_RAIL_TOP_BIAS, "0.002");
        ensureExtraConfig(KEY_LATERAL_SCALE, "1.0");
        ensureExtraConfig(KEY_LATERAL_OFFSET, "0.0");
    }

    /**
     * 渲染就绪判定：<b>恒为 true</b>。
     * <p>
     * 基类 {@code FunctionalObjBlockEntity.isRenderReady()} 返回 {@code loadingComplete}，而后者只在
     * {@code load() -> triggerAsyncLoading() -> whenLoading()} 跑完后才置 true。
     * 新放置的方块如果没走到 {@code load()}（没有 NBT、时序不同），{@code loadingComplete} 恒为 false
     * → {@code tryBeginRendering()} 直接 return false → {@code whenRendering()} 永远不被调用 → <b>新放置完全不显示</b>。
     * <p>
     * 本方块实体的 {@code whenLoading()} 只负责补配置默认值，不依赖任何异步加载产物，
     * 因此不需要等 {@code loadingComplete}。
     */
    @Override
    protected boolean isRenderReady() {
        return true;
    }

    @Override
    public void whenLoading() {
        // load() 会先清空 extraConfigs 再触发这里，所以默认值要在这里补一遍
        ensureDefaults();
    }

    // ==================== NBT 持久化 ====================

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (snapshot != null) {
            tag.put(NBT_SNAPSHOT, snapshot.toTag());
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        snapshot = tag.contains(NBT_SNAPSHOT) ? CellSnapshot.fromTag(tag.getCompound(NBT_SNAPSHOT)) : null;
        meshDirty = true;
        // 服务端永远不会自己采样（没有轨道数据），碰撞箱只能靠存盘快照重建；
        // 快照缺失/过期时保持整格，宁可挡路也不要让玩家掉进方块。
        // 这里刻意不走 setCollisionShape：load() 发生在区块加载中，
        // markShapeDirty 会再发一次方块更新，区块还没就绪时容易引出递归/空区块访问。
        if (snapshot != null && isSnapshotValid(snapshot)) {
            this.collisionShape = safeCollision(snapshot, CrossingSettings.from(this));
            enqueueBuild();
        }
        // 标记需要构建并入队（构建交给客户端 tick 队列，与本方块是否被渲染无关）。
        // <b>性能修复（2026-07）</b>：load() 里<b>绝不采样、绝不建网格</b>。
        // 老存档一进来就要加载成百上千块平交路口，如果每块都在这里同步建网格（每块几百面），
        // 主线程会被压死（实测 "Can't keep up! Running 2005ms or 40 ticks behind"）。
        // load() 只标记 meshDirty=true + 入队，真正的构建交给<b>客户端 tick 队列</b>
        // （每 tick 3ms 时间预算，与「有没有被渲染」无关，不会饿死）；
        // 用户显式动作（放置/点重算/配置变更/批量刷新）走 recompute，但构网同样落到 tick 队列（见 recompute）。
    }

    // ==================== 配置界面 ====================

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        Map<String, String> extra = this.extraConfigs;

        List<Component> styleEntries = new ArrayList<>();
        List<String> styleValues = new ArrayList<>();
        for (LevelCrossingStyles style : LevelCrossingStyles.values()) {
            styleEntries.add(ComponentHelper.translatable("ui.fangsu.level_crossing.style." + style.id()));
            styleValues.add(style.id());
        }
        configs.add(new EnumConfig(
                ComponentHelper.translatable("ui.fangsu.level_crossing.style"),
                new ConfigSpec("list"),
                styleEntries,
                () -> Math.max(0, styleValues.indexOf(
                        extra.getOrDefault(KEY_STYLE, LevelCrossingStyles.ASPHALT.id()))),
                idx -> {
                    extra.put(KEY_STYLE, styleValues.get(idx));
                    invalidateMesh();
                    sendUpdateC2S();
                }
        ).setSaveOnChange(true));

        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.level_crossing.outer_extend"),
                new ConfigSpec("number").setParam("isInt", new JsonPrimitive(true)),
                () -> (float) getExtraConfigInt(KEY_OUTER_EXTEND, 0),
                val -> {
                    extra.put(KEY_OUTER_EXTEND, String.valueOf(Math.round(val)));
                    // 外扩改变采样半径与铺面范围，必须重算而不是只重建网格；
                    // 走队列（最坏 1 tick 后生效），与批量刷新共用同一套限流
                    requestRecompute(this);
                }
        ));

        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.level_crossing.step_count"),
                new ConfigSpec("number").setParam("isInt", new JsonPrimitive(true)),
                () -> (float) getExtraConfigInt(KEY_STEP_COUNT, 8),
                val -> {
                    extra.put(KEY_STEP_COUNT, String.valueOf(Math.round(val)));
                    invalidateMesh();
                    sendUpdateC2S();
                }
        ));

        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.level_crossing.bevel"),
                new ConfigSpec("number"),
                () -> getExtraConfigFloat(KEY_BEVEL_WIDTH, 1.0f),
                val -> {
                    extra.put(KEY_BEVEL_WIDTH, String.valueOf(val));
                    invalidateMesh();
                    sendUpdateC2S();
                }
        ));

        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.level_crossing.deck_inset"),
                new ConfigSpec("number"),
                () -> getExtraConfigFloat(KEY_DECK_INSET, CrossingSettings.DEFAULT_DECK_INSET),
                val -> {
                    extra.put(KEY_DECK_INSET, String.valueOf(val));
                    // deckTopY 由 deckInset 推导，改它等于改高程
                    requestRecompute(this);
                }
        ));

        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.level_crossing.rail_top"),
                new ConfigSpec("number"),
                () -> getExtraConfigFloat(KEY_RAIL_TOP_OVERRIDE, -1f),
                val -> {
                    extra.put(KEY_RAIL_TOP_OVERRIDE, String.valueOf(val));
                    requestRecompute(this);
                }
        ));

        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.level_crossing.groove_depth"),
                new ConfigSpec("number"),
                () -> getExtraConfigFloat(KEY_GROOVE_DEPTH_OVERRIDE, -1f),
                val -> {
                    extra.put(KEY_GROOVE_DEPTH_OVERRIDE, String.valueOf(val));
                    requestRecompute(this);
                }
        ));

        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.level_crossing.ramp_foot"),
                new ConfigSpec("number"),
                () -> (float) getExtraConfigFloat(KEY_RAMP_FOOT, 0f),
                val -> {
                    extra.put(KEY_RAMP_FOOT, String.valueOf(val));
                    // 坡脚高度参与几何，改它必须重算
                    requestRecompute(this);
                }
        ));

        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.level_crossing.rail_top_bias"),
                new ConfigSpec("number"),
                () -> getExtraConfigFloat(KEY_RAIL_TOP_BIAS, (float) CrossingSettings.DEFAULT_RAIL_TOP_BIAS),
                val -> {
                    extra.put(KEY_RAIL_TOP_BIAS, String.valueOf(val));
                    // 轨顶偏置直接进几何（钢轨整体上移），改它必须重建
                    requestRecompute(this);
                }
        ));

        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.level_crossing.lateral_scale"),
                new ConfigSpec("number"),
                () -> getExtraConfigFloat(KEY_LATERAL_SCALE, 1.0f),
                val -> {
                    extra.put(KEY_LATERAL_SCALE, String.valueOf(val));
                    // 横向缩放改的是采样出来的断面横坐标，必须重新采样
                    requestRecompute(this);
                }
        ));

        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.level_crossing.lateral_offset"),
                new ConfigSpec("number"),
                () -> getExtraConfigFloat(KEY_LATERAL_OFFSET, 0.0f),
                val -> {
                    extra.put(KEY_LATERAL_OFFSET, String.valueOf(val));
                    requestRecompute(this);
                }
        ));

        configs.add(new RunnableConfig(
                ComponentHelper.translatable("ui.fangsu.level_crossing.recompute"),
                new ConfigSpec("func"),
                () -> requestRecompute(this)
        ).setButtonText(ComponentHelper.translatable("ui.fangsu.level_crossing.recompute")));

        return configs;
    }

    // ==================== 渲染 ====================

    @Override
    public void whenRendering() {
        CellSnapshot current = this.snapshot;
        if (current == null) {
            if (!firstRenderLogged) {
                firstRenderLogged = true;
                Main.LOGGER.info("[levelcrossing] first render pos={} snapshot=null", getBlockPos());
            }
            // 放置兜底：正常放置时 setPlacedBy 已经算过；这里覆盖"放下时方块实体还没就绪"的时序。
            // <p>
            // <b>不能在本线程直接 recompute()</b>：whenRendering 跑在共享的 RENDERING_EXECUTOR 上，
            // 而 recompute() 会 markShapeDirty()/sendBlockUpdated() 与 sendUpdateC2S()（网络），
            // 这些必须在主线程。所以排队到主线程，并且只排一次（initialComputeScheduled）。
            // 兜底：把「采样 + 构网」提交到工作线程（提交速率受限，主线程零计算）
            if (!initialComputeScheduled && level != null && level.isClientSide && canSubmitNow(level)) {
                initialComputeScheduled = true;
                submit(this, true);
            }
            return;
        }
        if (!firstRenderLogged) {
            firstRenderLogged = true;
            Main.LOGGER.info("[levelcrossing] first render pos={} snapshot=pending", getBlockPos());
        }
        // <b>渲染回调里绝不构建</b>：需要几何就提交到工作线程（与「有没有被渲染到」无关，不会饿死）。
        // 触发条件 = 没网格 / 被显式标脏 / 快照版本比已建版本新（版本一致就绝不再提交，杜绝重复重建）
        if (this.meshHolder == null || meshDirty || builtVersion != snapshotVersion) {
            submit(this, false);
            if (this.meshHolder == null) {
                meshNeverBuiltTicks++;
                // 不再每个方块各刷一条 warn（实机刷了 548 条）：登记进汇总集合，由 tickHooks 每 200 tick 报一次
                NEVER_BUILT.add(this);
                return;
            }
        }
        DynamicModelHolder holder = this.meshHolder;
        if (holder == null) {
            return;
        }
        // 不能传 new Matrices()（单位阵）：基类渲染器已经把原点挪到方块中心并按 FACING 转过，
        // 而本方块的面是按"方块最小角为原点、XZ∈[0,1]²"生成的，必须给它一个逆变换。
        this.scriptContext.drawModel(holder, levelCrossingPose());
    }

    /**
     * 抵消 {@code BaseBlockEntityRender} 施加的方块局部变换，使布面精确落在方块最小角。
     * <p>
     * <b>基类做了什么</b>（{@code BaseBlockEntityRender.java:88-95}，两树一致）：
     * <pre>
     * base = T(0.5, 0, 0.5) · T(tx, ty, tz) · Ry(-yaw + π) · Rx(rx) · Ry(ry) · Rz(rz)
     * </pre>
     * 也就是先把原点挪到方块 XZ 中心、再按 FACING 绕 Y 转 180°-yaw，再叠加本方块实体的模型平移/旋转。
     * 我们的几何是以方块最小角为原点、XZ∈[0,1]² 生成的，直接用单位阵就会整体偏半格，并且随玩家放置方向转 90°。
     * <p>
     * <b>乘法约定</b>（读码确认，不是凭直觉）：{@link Matrices#translate} / {@link Matrices#rotateX} /
     * {@link Matrices#rotateY} / {@link Matrices#rotateZ} 都直接委托 {@code Matrix4f}，而后者在 MC≥1.19.3 分支走
     * JOML 的 {@code impl.translate/rotateX/rotateY/rotateZ}（都是<b>右乘</b>：this = this·X），
     * MC&lt;1.19.3 分支走 {@code impl.multiplyWithTranslation} 与 {@code impl.multiply(Quaternion)}（同样是右乘）。
     * 因此<b>源码顺序就是列向量约定下的复合顺序</b>。
     * <p>
     * <b>逆矩阵</b>：base 的逆是把五步反着写、角度取负：
     * <pre>
     * M = Rz(-rz) · Ry(-ry) · Rx(-rx) · Ry(yaw - π) · T(-(0.5+tx), -ty, -(0.5+tz))
     * </pre>
     * 画出来就是 {@code base · M = I}，即我们传给 {@code drawModel} 的矩阵把基类变换完全抵消。
     * 该结论已用真实 JOML 1.10.5 离线数值验证（FACING 四向 × 3 组平移/旋转，8 个立方体角点最大误差 2.4e-7）。
     */
    private Matrices levelCrossingPose() {
        // 注意 facing 取的是当前方块状态而不是"假设 NORTH"：朝向被 BlockLevelCrossing 固定成常量，
        // 但这里仍按状态实际值求逆，避免以后放开朝向时又埋一次半格偏移。
        Direction facing = getBlockState().getValue(BaseObjBlock.FACING);
        float yaw = (float) Math.toRadians(facing.toYRot());
        Matrices pose = new Matrices();
        pose.rotateZ(-rotateZ);
        pose.rotateY(-rotateY);
        pose.rotateX(-rotateX);
        pose.rotateY(yaw - (float) Math.PI);
        pose.translate(-(0.5f + translateX), -translateY, -(0.5f + translateZ));
        return pose;
    }

    /**
     * 把本格的网格与「复现所需输入」导出到 {@code <gameDir>/fangsu_lc_dump/cell_<x>_<y>_<z>.{obj,txt}}。
     * <p>
     * 只在 {@link LevelCrossingDump#ENABLED} 时调用；任何异常都只 warn，绝不影响渲染。
     */
    private void dumpCell(RawModel model, CellSnapshot snap, int verts, int faces) {
        try {
            BlockPos pos = getBlockPos();
            java.nio.file.Path dir = net.minecraft.client.Minecraft.getInstance()
                    .gameDirectory.toPath().resolve(LevelCrossingDump.DIR_NAME);
            String name = LevelCrossingDump.cellName(pos.getX(), pos.getY(), pos.getZ());
            LevelCrossingDump.write(dir, name, model, snap, neighbourCrossings());
            String railIds = snap == null || snap.tracks == null ? "-" : snap.tracks.stream()
                    .map(t -> t == null ? "?" : t.railId)
                    .reduce((a, b) -> a + "," + b).orElse("-");
            Main.LOGGER.info("[levelcrossing] dump cell pos={} file={} verts={} faces={} tracks={} railIds={}",
                    pos, dir.resolve(name + ".obj"), verts, faces,
                    snap == null || snap.tracks == null ? 0 : snap.tracks.size(), railIds);
        } catch (Throwable t) {
            Main.LOGGER.warn("[levelcrossing] dump failed pos={} error={}", getBlockPos(), t.toString());
        }
    }

    /**
     * 把工作线程算好的模型挂到 holder 上（<b>主线程</b>）。
     * <p>
     * {@code uploadLater} 内部走 {@code RenderSystem.recordRenderCall}，真正的 GL 上传在渲染线程完成，
     * 所以这里在主线程调用是安全的。空网格时保留旧 holder —— 别把上一帧能显示的也清掉。
     */
    private void applyModel(RawModel model, CellSnapshot snap) {
        if (model == null) {
            Main.LOGGER.warn("[levelcrossing] mesh build returned null pos={}", getBlockPos());
            return;
        }
        int verts = 0;
        int faces = 0;
        for (RawMesh mesh : model.meshList.values()) {
            if (mesh == null) continue;
            if (mesh.vertices != null) verts += mesh.vertices.size();
            if (mesh.faces != null) faces += mesh.faces.size();
        }
        if (model.meshList.isEmpty() || faces == 0) {
            Main.LOGGER.warn("[levelcrossing] mesh empty pos={} tracks={} deck={} railTop={} verts={} faces={} mats={}",
                    getBlockPos(),
                    snap == null || snap.tracks == null ? -1 : snap.tracks.size(),
                    snap == null ? Double.NaN : snap.deckTopY,
                    snap == null || snap.tracks == null || snap.tracks.isEmpty()
                            || snap.tracks.get(0) == null ? Double.NaN : snap.tracks.get(0).railTopY,
                    verts, faces, model.meshList.size());
            return;
        }
        Main.debug("[levelcrossing] mesh pos={} verts={} faces={} mats={}",
                getBlockPos(), verts, faces, model.meshList.size());
        // 诊断导出（默认关闭，-Dfangsu.levelcrossing.dump=true 才走）：把真实网格 + 复现所需输入落盘
        if (LevelCrossingDump.ENABLED) {
            dumpCell(model, snap, verts, faces);
        }
        if (!meshBuiltLogged) {
            meshBuiltLogged = true;
            Main.LOGGER.info("[levelcrossing] mesh built pos={} verts={} faces={} (worker)",
                    getBlockPos(), verts, faces);
        }
        if (meshHolder == null) {
            meshHolder = new DynamicModelHolder();
        }
        meshHolder.uploadLater(model);
        meshDirty = false;
        // 记下「这份几何对应哪个快照版本」：同一版本不再重复构建
        builtVersion = snapshotVersion;
        meshNeverBuiltTicks = 0;
        NEVER_BUILT.remove(this);
        // 超预算的那次已经照常发射（几何完整）；下一次重建才用降级明细
        degradeLevel = CrossingMeshBuilder.lastBuildOverBudget() ? 1 : 0;
    }

    /**
     * 工作线程算完之后<b>切回主线程</b>应用结果：写快照 / 更新碰撞 / 上传模型 / 同步服务端。
     * 这里所有调用都是「必须主线程」的那几件事。
     */
    private void applyComputedResult(@Nullable CellSnapshot snap, @Nullable RawModel model, boolean sampled) {
        if (isRemoved()) return;
        // 钢轨贴图存在性检查（主线程；缺图会 warn 一次并让两桶回退兜底贴图）
        LevelCrossingStyles.ensureRailTextures();
        // 像素风：本方块用到的贴图关掉 mipmap/线性过滤（主线程、逐张只处理一次、异常只 debug）
        LevelCrossingStyles.ensureNearestFilter();
        // <b>轨顶自愈</b>：工作线程不能碰 NTE 资源，采样时可能把某些轨的轨顶标成「未解析」。
        // 这里在主线程把这些 railId 的模型包围盒解析进缓存，然后重新提交一次采样（最多 2 次）。
        if (sampled && snap != null && hasUnresolvedRailTop(snap)) {
            resolveRailTopsOnMainThread(snap);
            if (railTopResolveRetries < RAIL_TOP_RESOLVE_MAX_RETRIES) {
                railTopResolveRetries++;
                Main.debug("[levelcrossing] railTop unresolved, re-sample pos={} rail={} retry={}",
                        getBlockPos(), firstUnresolvedRailId(snap), railTopResolveRetries);
                // 模型先按当前（默认值）快照挂上，避免这一帧完全没几何；下一轮会换成正确高度
                if (model != null) applyModel(model, snap);
                submit(this, true);
                return;
            }
            if (!railTopResolveWarned) {
                railTopResolveWarned = true;
                Main.LOGGER.warn("[levelcrossing] railTop unresolved after {} retries pos={} rail={}",
                        RAIL_TOP_RESOLVE_MAX_RETRIES, getBlockPos(), firstUnresolvedRailId(snap));
            }
        }
        if (sampled && snap != null) {
            try {
                // 直接写字段（本方法就在主线程）：不走 setSnapshot 的 invalidateMesh，
                // 否则会再触发一轮网格构建（模型已经算好了，白白多做一次）。
                // 这里只把「快照版本」推进一格，随后 applyModel 会把 builtVersion 追上它。
                this.snapshot = snap;
                this.snapshotVersion++;
                rebuildCollision(snap, CrossingSettings.from(this));
                sendUpdateSafely();
            } catch (RuntimeException e) {
                Main.LOGGER.warn("[levelcrossing] apply stage=snapshot pos={} error={}",
                        getBlockPos(), e.getClass().getSimpleName(), e);
            }
        }
        if (model != null) {
            applyModel(model, snap != null ? snap : this.snapshot);
        }
    }

    /**
     * 请求重算（<b>不在主线程计算</b>）：采样与构网都提交到工作线程，
     * 主线程只在 {@link #applyComputedResult} 里做写快照/碰撞/上传/同步。
     */
    public void recompute() {
        if (level == null || !level.isClientSide) {
            return;
        }
        if (!recomputeEnteredLogged) {
            recomputeEnteredLogged = true;
            Main.LOGGER.info("[levelcrossing] recompute entered pos={} client={}", getBlockPos(), level.isClientSide);
        }
        // 显式动作（放置方块 setPlacedBy / 配置面板重算）→ 优先队列：
        // 新放置的方块不能被前面几个「1 万多顶点的大任务」堵在后面（实机症状：一直不显示、碰撞箱还是整块）
        submit(this, true, true);
    }

    /**
     * 快照里是否有「轨顶未解析」的轨道。
     */
    private static boolean hasUnresolvedRailTop(@Nullable CellSnapshot snap) {
        if (snap == null || snap.tracks == null) return false;
        for (TrackSlice slice : snap.tracks) {
            if (slice != null && !slice.railTopResolved) return true;
        }
        return false;
    }

    @Nullable
    private static String firstUnresolvedRailId(@Nullable CellSnapshot snap) {
        if (snap == null || snap.tracks == null) return null;
        for (TrackSlice slice : snap.tracks) {
            if (slice != null && !slice.railTopResolved) return slice.railId;
        }
        return null;
    }

    /**
     * <b>主线程</b>解析这些轨道的 NTE 模型包围盒顶（写进采样器的静态缓存）。
     */
    private static void resolveRailTopsOnMainThread(@Nullable CellSnapshot snap) {
        if (snap == null || snap.tracks == null) return;
        for (TrackSlice slice : snap.tracks) {
            if (slice == null || slice.railTopResolved) continue;
            try {
                LevelCrossingSampler.resolveModelTop(slice.railId);
            } catch (RuntimeException e) {
                Main.LOGGER.warn("[levelcrossing] railTop resolve failed rail={} error={}",
                        slice.railId, e.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 同步快照到服务端；失败只记录，不向上抛（recompute 的其它阶段已经各自隔离）。
     */
    private void sendUpdateSafely() {
        try {
            sendUpdateC2S();
        } catch (RuntimeException e) {
            Main.LOGGER.warn("[levelcrossing] recompute stage=sendC2S pos={} error={}",
                    getBlockPos(), e.getClass().getSimpleName(), e);
        }
    }

    /**
     * 四条格边的邻居是否<b>也是平交路口</b>，顺序与 {@code DeckSurface.SIDE_*} 一致
     * （0 = -X，1 = +X，2 = -Z，3 = +Z）。
     * <p>
     * 几何要靠它决定「这一侧平接还是出坡」：邻居是同类时铺面一路延伸到格界、不画竖面也不出坡，
     * 一整片道口才连成一块。服务端也读得到方块状态，所以两端都能算，不存在同步问题。
     */
    public boolean[] neighbourCrossings() {
        boolean[] cached = this.neighbourCache;
        if (cached != null) {
            return cached.clone();
        }
        // 缓存还没建（例如工作线程先到）：返回"都不是同类"的安全默认值，
        // <b>绝不在这里 getBlockState</b> —— 那不是线程安全的。缓存由主线程在 submit() 前刷新。
        return new boolean[4];
    }

    /**
     * 邻居是不是平交路口。<b>按方块状态判断而不是方块实体</b>：
     * 渲染那一帧邻居的方块实体可能还没创建（区块刚加载 / 数据包还没同步），
     * 用 getBlockEntity 会得到 false —— 于是两侧各画一遍竖面与斜坡，
     * 接缝处就露出「一条缝 + 下方地形」的沙色坑（用户截图）。
     * 方块状态在区块一加载就存在，判定稳定得多。
     */
    private boolean isCrossingAt(BlockPos neighbour) {
        return level != null && level.getBlockState(neighbour).getBlock() instanceof BlockLevelCrossing;
    }

    // ==================== 工作线程调度 ====================

    /**
     * 安装（客户端入口调一次）。用 Architectury 的 {@code ClientTickEvent.CLIENT_POST}：
     * 现在只用来<b>刷新邻居缓存 + 限流提交</b>（真正干活在工作线程）。
     * 两树（Fabric/Forge）都可用，项目里 {@code DoorOpenCacheClient} 已经在用同一个事件。
     */
    public static void initBuildQueue() {
        if (buildQueueInstalled) return;
        buildQueueInstalled = true;
        dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(client -> tickHooks());
        Main.LOGGER.info("[levelcrossing] worker thread installed (fangsu-levelcrossing-worker)");
    }

    /**
     * 是否有已安装（防止重复注册）。
     */
    private static boolean buildQueueInstalled;

    /**
     * 每 tick 一次：把上一 tick 因配额被推迟的提交补上（每 tick 最多 2 个，<b>优先队列先</b>）。
     */
    private static void tickHooks() {
        drainSubmitQueue(PRIORITY_SUBMITS);
        drainSubmitQueue(PENDING_SUBMITS);
        // 每 200 tick 汇总一次「有快照但没建出网格」的方块数（替代每个方块一条 warn）
        if (++neverBuiltReportTick >= NEVER_BUILT_REPORT_TICKS) {
            neverBuiltReportTick = 0;
            int pending = PRIORITY_SUBMITS.size() + PENDING_SUBMITS.size();
            if (!NEVER_BUILT.isEmpty() || pending > 0) {
                Main.LOGGER.warn("[levelcrossing] mesh never built: {} blocks pending={}",
                        NEVER_BUILT.size(), pending);
            }
        }
    }

    /**
     * 消费一个待提交队列（优先队列先被消费，新放置的方块因此不会被大任务堵住）。
     */
    private static void drainSubmitQueue(java.util.ArrayDeque<BlockEntityLevelCrossing> queue) {
        while (!queue.isEmpty() && submitsThisTick < SUBMIT_BUDGET_PER_TICK) {
            BlockEntityLevelCrossing be = queue.peekFirst();
            if (be == null) {
                queue.pollFirst();
                continue;
            }
            if (be.isRemoved() || be.getLevel() == null || !be.getLevel().isClientSide) {
                queue.pollFirst();
                be.submitQueued = false;
                be.pendingSample = false;
                continue;
            }
            if (!canSubmitNow(be.getLevel())) break;
            queue.pollFirst();
            be.submitQueued = false;
            boolean sample = be.pendingSample;
            be.pendingSample = false;
            if (be.pendingTask) continue;
            startTask(be, sample);
        }
    }

    /**
     * 提交速率限流：每 tick 最多 {@value #SUBMIT_BUDGET_PER_TICK} 个任务。
     */
    private static boolean canSubmitNow(net.minecraft.world.level.Level level) {
        long now = level == null ? 0L : level.getGameTime();
        if (now != submitBudgetTick) {
            submitBudgetTick = now;
            submitsThisTick = 0;
        }
        if (submitsThisTick >= SUBMIT_BUDGET_PER_TICK) {
            return false;
        }
        submitsThisTick++;
        return true;
    }

    /**
     * 请求「采样 +（可选）构网」（<b>任意线程安全</b>，但只有主线程会真正读 level/配置）。
     * <p>
     * 幂等：同一 BE 已有任务在跑时只把 {@code pendingSample} 合并进去，不再重复提交。
     */
    public static void submit(@Nullable BlockEntityLevelCrossing be, boolean sample) {
        submit(be, sample, false);
    }

    /**
     * @param priority true = 用户显式动作（放置/配置重算），排进 {@link #PRIORITY_SUBMITS} 先做，
     *                 不会被历史大任务堵住
     */
    public static void submit(@Nullable BlockEntityLevelCrossing be, boolean sample, boolean priority) {
        if (be == null || be.isRemoved()) return;
        if (be.getLevel() == null || !be.getLevel().isClientSide) return;
        if (be.pendingTask) {
            if (sample) be.pendingSample = true;
            return;
        }
        // <b>去重</b>：只要不重新采样且「网格已对应当前快照版本、也没被标脏」，就没什么可做的。
        // 这是「同一快照版本连续 10 次 whenRendering 只构建 1 次」的关键（T45）。
        if (!sample && be.meshHolder != null && be.builtVersion == be.snapshotVersion && !be.meshDirty
                && !be.submitQueued) {
            return;
        }
        if (!canSubmitNow(be.getLevel())) {
            // 本 tick 提交配额用尽：先记下来，由 tickHooks 在后续 tick 里按 2 个/tick 补交。
            // 注意这只是一个「提交排队」，真正的采样/构网仍在工作线程上，不占主线程 CPU。
            be.pendingSample |= sample;
            if (!be.submitQueued) {
                be.submitQueued = true;
                if (priority) {
                    PRIORITY_SUBMITS.addLast(be);
                } else {
                    // 防膨胀：普通队列上限 64，超了丢最老的。「越跑越卡」就是 worker 积压时队列无限增长
                    while (PENDING_SUBMITS.size() >= PENDING_SUBMITS_MAX) {
                        BlockEntityLevelCrossing dropped = PENDING_SUBMITS.pollFirst();
                        if (dropped == null) break;
                        dropped.submitQueued = false;
                        dropped.pendingSample = false;
                        DROPPED_SUBMITS.incrementAndGet();
                    }
                    PENDING_SUBMITS.addLast(be);
                }
            } else if (priority && PENDING_SUBMITS.remove(be)) {
                // 已在普通队列里、现在被显式动作提升优先级：挪到优先队列，别继续等
                PRIORITY_SUBMITS.addLast(be);
            }
            return;
        }
        startTask(be, sample);
    }

    /**
     * 真正起一个工作线程任务（主线程调用）：先把配置与邻居缓存准备好。
     */
    private static void startTask(BlockEntityLevelCrossing be, boolean sample) {
        be.refreshNeighbourCache();
        be.pendingSettings = CrossingSettings.from(be);
        be.pendingSample = sample;
        be.pendingTask = true;
        try {
            LC_EXECUTOR.submit(() -> work(be));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            be.pendingTask = false;
            Main.LOGGER.warn("[levelcrossing] worker rejected pos={}", be.getBlockPos());
        }
    }

    /**
     * 兼容旧调用点：只重构建网格。
     */
    private void enqueueBuild() {
        submit(this, false);
    }

    /**
     * 兼容旧调用点：重算（采样 + 构网）。
     */
    public static void requestRecompute(@Nullable BlockEntityLevelCrossing be) {
        submit(be, true);
    }

    /**
     * 工作线程主循环：<b>只做纯 CPU</b>（采样 + 构网），算完切回主线程应用。
     * <p>
     * 线程安全红线：这里<b>不得</b>调用 {@code level.getBlockState} / {@code ResourceUtil.loadModel} /
     * {@code Minecraft.*} / {@code MainClient.modelManager} / {@code markShapeDirty} / 任何网络发包。
     * 邻居状态用主线程刷新好的 {@link #pendingSettings}，采样器的「NTE 模型高度」分支已改为
     * 非主线程直接退回默认高度（见 {@code LevelCrossingSampler#modelTop} 注释）。
     */
    private static void work(BlockEntityLevelCrossing be) {
        try {
            while (true) {
                long taskStartedAt = System.nanoTime();
                boolean sample;
                CrossingSettings settings;
                synchronized (be) {
                    if (!be.pendingTask) return;
                    sample = be.pendingSample;
                    be.pendingTask = false;
                    be.pendingSample = false;
                    settings = be.pendingSettings;
                }
                if (be.isRemoved()) return;
                net.minecraft.world.level.Level level = be.getLevel();
                if (level == null) return;
                if (settings == null) settings = CrossingSettings.from(be);
                CellSnapshot snap = be.snapshot;
                // 分段计时用：采样前的槽铣切累计耗时（构网阶段才知道 cuts 占多少）
                long cutsNanosBefore = GroovedRailProfile.auditSlotCutNanos();
                long subtractCallsBefore = GroovedRailProfile.auditSubtractCalls();
                if (sample || snap == null) {
                    try {
                        snap = LevelCrossingSampler.snapshot(level, be.getBlockPos(), settings);
                    } catch (RuntimeException e) {
                        Main.LOGGER.warn("[levelcrossing] worker sample failed pos={} error={}",
                                be.getBlockPos(), e.getClass().getSimpleName(), e);
                        continue;
                    }
                    if (snap == null || invalidReason(snap) != null) {
                        Main.LOGGER.warn("[levelcrossing] worker snapshot rejected pos={} reason={}",
                                be.getBlockPos(), snap == null ? "null" : invalidReason(snap));
                        continue;
                    }
                }
                long sampleDoneAt = System.nanoTime();
                RawModel model = null;
                if (snap != null) {
                    try {
                        // degradeLevel 只在「上一次 build 超预算」之后才为 1：同一次里绝不建两遍
                        // 钢轨两个桶各自一张图：水平面 = rail_top（完整 0→1），竖直面 = rail_side（随机窗口）
                        model = CrossingMeshBuilder.build(snap, settings,
                                LevelCrossingStyles.byId(settings.styleId).topTexture(),
                                LevelCrossingStyles.railTopTexture(),
                                LevelCrossingStyles.railSideTexture(),
                                LevelCrossingStyles.GROUND_TEXTURE,
                                be.degradeLevel,
                                be.getBlockPos().asLong());
                    } catch (RuntimeException e) {
                        Main.LOGGER.warn("[levelcrossing] worker build failed pos={} error={}",
                                be.getBlockPos(), e.getClass().getSimpleName(), e);
                    }
                }
                long buildDoneAt = System.nanoTime();
                // ---- 对齐数值（定标唯一依据，每方块只打一次 info）----
                // railTopWorld = 采样到的原版轨顶世界 Y（未加 bias）；steelTopWorld = 我们的钢轨顶面世界 Y；
                // dy = 两者之差（应等于 railTopBias）；latCenterDist = 断面相对原版中心线的横向偏移；
                // deckWorld = 沥青面世界 Y。用户在直线段放一块、把这行发来就能精确知道差多少、往哪边调。
                if (model != null && snap != null && !be.alignLogged) {
                    be.alignLogged = true;
                    TrackSlice mainSlice = null;
                    if (snap.tracks != null) {
                        for (TrackSlice s : snap.tracks) {
                            if (s == null) continue;
                            if (mainSlice == null || s.primary) mainSlice = s;
                            if (s.primary) break;
                        }
                    }
                    if (mainSlice != null) {
                        int blockY = be.getBlockPos().getY();
                        double railTopWorld = blockY + mainSlice.railTopY;
                        double steelTopWorld = railTopWorld + snap.railTopBias;
                        Main.LOGGER.info("[levelcrossing] align pos={} railId={} railTopWorld={} steelTopWorld={}"
                                        + " dy={} latCenterDist={} deckWorld={}",
                                be.getBlockPos(), mainSlice.railId,
                                String.format(java.util.Locale.ROOT, "%.6f", railTopWorld),
                                String.format(java.util.Locale.ROOT, "%.6f", steelTopWorld),
                                String.format(java.util.Locale.ROOT, "%.6f", steelTopWorld - railTopWorld),
                                String.format(java.util.Locale.ROOT, "%.6f", Math.abs(mainSlice.lateralOffset)),
                                String.format(java.util.Locale.ROOT, "%.6f", blockY + snap.deckTopY));
                    }
                }
                final CellSnapshot finalSnap = snap;
                final RawModel finalModel = model;
                final boolean finalSample = sample;
                // ---- 分段计时（热点定位）----
                // total / sample / cuts / build 四段：cuts = 槽铣切（slotCuts + 补集裁片）累计耗时，
                // build = 构网总耗时，sample = 采样。任务总耗时 > 100ms 才打，避免刷屏。
                long totalMs = (buildDoneAt - taskStartedAt) / 1_000_000L;
                if (totalMs > 100L) {
                    long sampleMs = (sampleDoneAt - taskStartedAt) / 1_000_000L;
                    long cutsMs = (GroovedRailProfile.auditSlotCutNanos() - cutsNanosBefore) / 1_000_000L;
                    long buildMs = (buildDoneAt - sampleDoneAt) / 1_000_000L;
                    Main.LOGGER.warn("[levelcrossing] stage times pos={} total={}ms sample={}ms cuts={}ms build={}ms verts={}",
                            be.getBlockPos(), totalMs, sampleMs, cutsMs, buildMs,
                            model == null ? 0 : model.getVertexCount());
                    Main.debug("[levelcrossing] stage times detail pos={} subtract={} calls={}",
                            be.getBlockPos(),
                            GroovedRailProfile.auditSubtractCalls() - subtractCallsBefore,
                            GroovedRailProfile.auditSlotCutCalls());
                }
                // 慢任务定位：单个任务 > 200ms 就 warn 一次（每 BE 一次），下次卡顿可以直接对着 pos 查
                long elapsedMs = (System.nanoTime() - taskStartedAt) / 1_000_000L;
                if (elapsedMs > SLOW_TASK_MS && !be.slowTaskLogged) {
                    be.slowTaskLogged = true;
                    Main.LOGGER.warn("[levelcrossing] slow task pos={} ms={} verts={}",
                            be.getBlockPos(), elapsedMs, model == null ? 0 : model.getVertexCount());
                }
                // 切回主线程：只做必须主线程的几件事
                net.minecraft.client.Minecraft.getInstance().execute(
                        () -> be.applyComputedResult(finalSnap, finalModel, finalSample));
            }
        } finally {
            synchronized (be) {
                be.pendingTask = false;
                be.pendingSample = false;
            }
        }
    }

    /**
     * 主线程刷新邻居缓存（{@code getBlockState} 只能在这里调）。
     */
    public void refreshNeighbourCache() {
        if (level == null) return;
        long now = level.getGameTime();
        if (this.neighbourCache != null && this.neighbourCacheTick == now) return;
        BlockPos pos = getBlockPos();
        boolean[] result = new boolean[4];
        result[DeckSurface.SIDE_NEG_X] = isCrossingAt(pos.west());
        result[DeckSurface.SIDE_POS_X] = isCrossingAt(pos.east());
        result[DeckSurface.SIDE_NEG_Z] = isCrossingAt(pos.north());
        result[DeckSurface.SIDE_POS_Z] = isCrossingAt(pos.south());
        this.neighbourCache = result;
        this.neighbourCacheTick = now;
    }

    /**
     * 让几何在下一帧重建（相邻方块变化、配置变化、快照更新时调用）。
     * <p>
     * 同时让<b>邻居缓存失效</b>：{@code BlockLevelCrossing.neighborChanged} 会调用本方法，
     * 因此「邻居被放/拆/改」这条路径一定会让缓存重查。
     */
    public void invalidateMesh() {
        this.meshDirty = true;
        this.neighbourCacheTick = -1L;
        // 标脏的同时入队：不依赖「下一帧会不会被渲染到」，否则背对/被遮挡的方块会永远不重建
        enqueueBuild();
        markShapeDirty();
    }

    public VoxelShape getCachedShape() {
        return collisionShape;
    }

    /**
     * 供采样/同步链路替换碰撞箱；P0 时是 protected，跨包写不进来，故放开为 public。
     */
    public void setCollisionShape(@Nullable VoxelShape shape) {
        // 只把 null 兜成整格；空形状是几何算出来的合法结果，不能替换成整格，
        // 否则"轨道正好铺满"的格子会突然变成一堵墙。
        this.collisionShape = shape != null ? shape : DEFAULT_SHAPE;
        markShapeDirty();
    }

    private void rebuildCollision(CellSnapshot cellSnapshot, CrossingSettings settings) {
        setCollisionShape(safeCollision(cellSnapshot, settings));
    }

    /**
     * 构建碰撞箱；几何算炸时退回整格，绝不让方块实体抛异常。
     */
    private VoxelShape safeCollision(CellSnapshot cellSnapshot, CrossingSettings settings) {
        try {
            return CrossingCollisionBuilder.build(cellSnapshot, settings);
        } catch (RuntimeException e) {
            Main.LOGGER.warn("[FangSu] Level crossing: collision build failed at {}", getBlockPos(), e);
            return DEFAULT_SHAPE;
        }
    }

    // BaseObjBlock 的碰撞/轮廓查询最终会走到这两个方法
    @Override
    public VoxelShape setShape(BlockState state) {
        return collisionShape;
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
        return collisionShape;
    }

    @Nullable
    public CellSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(@Nullable CellSnapshot snapshot) {
        this.snapshot = snapshot;
        invalidateMesh();
    }

    @Override
    public void whenDisposing() {
        DynamicModelHolder holder = this.meshHolder;
        this.meshHolder = null;
        if (holder != null) {
            // 不 close 会让 ModelCluster 持有的 VAO/VBO 永远留在显存里（仓库已知坑）
            holder.close();
        }
    }

    @Override
    public String getMainModelKey() {
        return "levelCrossing";
    }

    // ==================== 网络同步 ====================

    @Override
    public void writeC2S(FriendlyByteBuf buf) {
        super.writeC2S(buf);
        CellSnapshot current = this.snapshot;
        buf.writeBoolean(current != null);
        if (current != null) {
            buf.writeNbt(current.toTag());
        }
    }

    @Override
    public void readC2S(FriendlyByteBuf buf) {
        super.readC2S(buf);
        CompoundTag tag = buf.readBoolean() ? buf.readNbt() : null;
        CellSnapshot incoming = tag == null ? null : CellSnapshot.fromTag(tag);
        if (incoming == null || !isSnapshotValid(incoming)) {
            // 快照来自客户端，必须当成不可信输入：拒收比带着 NaN 去建几何安全得多
            Main.LOGGER.warn("[FangSu] Level crossing: rejecting malformed snapshot at {}", getBlockPos());
            return;
        }
        setSnapshot(incoming);
        rebuildCollision(incoming, CrossingSettings.from(this));
        // 客户端收到同步快照只标记 dirty，不同步构建网格：
        // 同步构建会在网络包处理路径上占主线程（老存档一次性同步很多块时同样会卡），
        // 交给 whenRendering()（RENDERING_EXECUTOR + 每 tick 预算）按可见性摊开。
        if (level != null && level.isClientSide) {
            meshDirty = true;
            enqueueBuild();
        }
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * 快照合法性校验（客户端发出前 + 服务端收到后共用同一份规则）。
     * <p>
     * 规则与渲染/碰撞数学的假设一一对应：数值有限、条数与点数有上限、
     * 槽的横向区间必须嵌套（内沿 &lt; 外沿），高度与角度在方块能表达的范围内。
     */
    public static boolean isSnapshotValid(@Nullable CellSnapshot cellSnapshot) {
        return invalidReason(cellSnapshot) == null;
    }

    /**
     * 与 {@link #isSnapshotValid} 完全同一份规则，但返回<b>第一个不满足的类别</b>（合法时返回 {@code null}）。
     * <p>
     * 之所以让两者共用这一个实现：线上诊断需要"为什么被拒"的类别，
     * 而把规则写两份迟早会漂移（漂移的结果是"校验说合法、诊断说非法"）。
     * 类别值是固定短标识，便于 grep：
     * {@code null} / {@code pitch_angle} / {@code roll_angle} / {@code deck_height} /
     * {@code ground_height} / {@code track_count} / {@code slice:*}。
     */
    @Nullable
    public static String invalidReason(@Nullable CellSnapshot cellSnapshot) {
        if (cellSnapshot == null) {
            return "null";
        }
        if (!isFinite(cellSnapshot.pitchDeg) || Math.abs(cellSnapshot.pitchDeg) > 45.0D) {
            return "pitch_angle";
        }
        if (!isFinite(cellSnapshot.rollDeg) || Math.abs(cellSnapshot.rollDeg) > 20.0D) {
            return "roll_angle";
        }
        if (!isFinite(cellSnapshot.deckTopY) || cellSnapshot.deckTopY < 0.0D || cellSnapshot.deckTopY > 1.0D) {
            return "deck_height";
        }
        if (!isFinite(cellSnapshot.groundTopY)
                || cellSnapshot.groundTopY < -0.2D
                || cellSnapshot.groundTopY > cellSnapshot.deckTopY) {
            return "ground_height";
        }
        if (cellSnapshot.tracks == null || cellSnapshot.tracks.size() > CellSnapshot.MAX_TRACKS) {
            return "track_count";
        }
        for (TrackSlice slice : cellSnapshot.tracks) {
            String sliceReason = sliceInvalidReason(slice);
            if (sliceReason != null) {
                return sliceReason;
            }
        }
        return null;
    }

    /**
     * 单条轨道的拒收类别；合法返回 {@code null}。
     */
    @Nullable
    private static String sliceInvalidReason(@Nullable TrackSlice slice) {
        if (slice == null || slice.xs == null || slice.zs == null) {
            return "slice:null";
        }
        if (slice.xs.length != slice.zs.length || slice.xs.length > MAX_POINTS_PER_TRACK) {
            return "slice:points";
        }
        for (int i = 0; i < slice.xs.length; i++) {
            if (!isFinite(slice.xs[i]) || !isFinite(slice.zs[i])) {
                return "slice:finite";
            }
        }
        if (!isFinite(slice.yawDeg) || !isFinite(slice.railTopY)) {
            return "slice:finite";
        }
        if (!isFinite(slice.gauge) || slice.gauge <= 0.5D || slice.gauge > 3.0D) {
            return "slice:gauge";
        }
        if (!isFinite(slice.grooveOuter) || slice.grooveOuter <= 0.0D || slice.grooveOuter > 2.0D) {
            return "slice:groove";
        }
        if (!isFinite(slice.grooveInner) || slice.grooveInner <= 0.0D || slice.grooveInner >= slice.grooveOuter) {
            return "slice:groove";
        }
        // 柔和提示（不 warn、不拒收）：旧快照里可能存着早期估算的断面宽度（0.68/0.82 → 宽 0.14，
        // 是 rail.obj 真轨头 0.068383 的两倍）。用户点一次「重算」就会按新的 rail_gauge.json 重新采样。
        if (slice.grooveOuter - slice.grooveInner > 0.09D) {
            Main.debug("[levelcrossing] profile looks oversized railId={} width={} (consider recompute)",
                    slice.railId, slice.grooveOuter - slice.grooveInner);
        }
        // 槽深上限原来写 0.05，但用户口径已把 slotDepth 提到 1.5 像素 = 0.09375，
        // 不放宽会导致【每一格快照都被判 slice:depth 拒收】= 道口完全不显示。
        // 这里放宽到 0.2：真正的"不切穿轨头"约束由几何侧钳制负责。
        if (!isFinite(slice.grooveDepth) || slice.grooveDepth <= 0.0D || slice.grooveDepth > 0.2D) {
            return "slice:depth";
        }
        if (!isFinite(slice.lateralScale) || slice.lateralScale < 0.5D || slice.lateralScale > 2.0D) {
            return "slice:lateral";
        }
        if (!isFinite(slice.lateralOffset) || Math.abs(slice.lateralOffset) > 0.5D) {
            return "slice:lateral";
        }
        return null;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
