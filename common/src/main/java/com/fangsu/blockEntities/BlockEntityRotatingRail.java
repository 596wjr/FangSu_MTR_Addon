package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.NteRailManager.NteRail;
import com.fangsu.customItem.NteRailManager;
import com.fangsu.extraConfig.*;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.network.ModNetwork;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.render.sowcerext.model.RawModel;

import com.fangsu.utils.ResourceUtil;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_ROTATING_RAIL;

/**
 * 旋转轨道方块实体。
 * <p>
 * 支持三种模式（通过 extraConfig "mode" 切换）：
 * <ul>
 *   <li><b>f</b> — 直轨模式：两条平行轨道 + 弦弧线分布</li>
 *   <li><b>r</b> — 旋转模式：逐段平移+旋转放置轨道</li>
 *   <li><b>crossing</b> — 道口模式：弧线交叉轨道</li>
 * </ul>
 * <p>
 * 红石控制：有信号 → 偏移归零（platform侧）；无信号 → 偏移 2*间隔（非platform侧）。
 */
public class BlockEntityRotatingRail extends BaseObjBlockEntity implements Syncable {

    // ==================== 默认值 ====================
    private static final String DEFAULT_RAIL_ID = "pujiang_line_track_only";
    private static final String DEFAULT_FALLBACK_MODEL = "mtrsteamloco:rails/pujiang_line_track_only.obj";
    private static final String MODE_F = "f";
    private static final String MODE_R = "r";
    private static final String MODE_CROSSING = "crossing";

    // ==================== ExtraConfig ====================
    protected Map<String, String> extraConfigs = new ConcurrentHashMap<>();

    public final String getExtraConfig(String key) {
        return extraConfigs.get(key);
    }

    public final String getExtraConfig(String key, String defaultValue) {
        return extraConfigs.getOrDefault(key, defaultValue);
    }

    public final void setExtraConfig(String key, String value) {
        extraConfigs.put(key, value);
    }

    public final void ensureExtraConfig(String key, String value) {
        extraConfigs.putIfAbsent(key, value);
    }

    public final float getExtraConfigFloat(String key, float defaultValue) {
        String value = extraConfigs.get(key);
        if (value == null) return defaultValue;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    // ==================== 网络同步 ====================

    public void sendUpdateC2S() {
        if (level != null && level.isClientSide) {
            this.whenSaving(extraConfigs);
            if (!level.hasChunk(getBlockPos().getX() >> 4, getBlockPos().getZ() >> 4)) return;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBlockPos(getBlockPos());
            writeC2S(buf);
            NetworkManager.sendToServer(ModNetwork.BE_SYNC, buf);
        }
        this.setChanged();
        this.markShapeDirty();
    }

    public void whenSaving(Map<String, String> extraConfigs) {
    }

    @Override
    public void writeC2S(FriendlyByteBuf buf) {
        buf.writeUtf(mainModel != null ? mainModel : "");
        int ecSize = Math.min(extraConfigs.size(), 256);
        buf.writeInt(ecSize);
        int count = 0;
        for (String key : extraConfigs.keySet()) {
            if (count >= ecSize) break;
            String value = extraConfigs.get(key);
            buf.writeUtf(key != null ? key : "");
            buf.writeUtf(value != null ? value : "");
            count++;
        }
        writeNode(buf, fixedNode);
        writeNode(buf, posNode);
        writeNode(buf, negNode);
    }

    @Override
    public void readC2S(FriendlyByteBuf buf) {
        mainModel = buf.readUtf();
        if (mainModel.isEmpty()) mainModel = null;
        int ecSize = buf.readInt();
        for (int i = 0; i < ecSize; i++) {
            String key = buf.readUtf();
            String value = buf.readUtf();
            extraConfigs.put(key, value);
        }
        fixedNode = readNode(buf);
        posNode = readNode(buf);
        negNode = readNode(buf);
        triggerAsyncLoading();
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private static void writeNode(FriendlyByteBuf buf, BlockPos node) {
        buf.writeBoolean(node != null);
        if (node != null) {
            buf.writeInt(node.getX());
            buf.writeInt(node.getY());
            buf.writeInt(node.getZ());
        }
    }

    private static BlockPos readNode(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        return new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
    }

    // ==================== NBT 持久化 ====================

    /**
     * 需要以原生 NBT 字段保存的字符串配置键集合。
     */
    private static final java.util.Set<String> STRING_CONFIG_KEYS = java.util.Set.of(
            "mode", "railId", "t_length", "flipV", "length", "in", "flip",
            "t_count", "r", "r1", "r2"
    );

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (markedError) return;

        try {
            this.whenSaving(extraConfigs);
        } catch (Exception e) {
            Main.LOGGER.error("Failed to save extra configs for {} at {}", getClass().getSimpleName(), getBlockPos(), e);
        }

        // 直接使用游戏原生 NBT，将每个配置作为根标签下的独立字段保存（避免嵌套 extraConfig 层）
        if (extraConfigs != null) {
            for (String key : STRING_CONFIG_KEYS) {
                String value = extraConfigs.get(key);
                if (value != null) {
                    tag.putString(key, value);
                }
            }
        }

        // R 模式：三个节点的位置以原生 int 三元组保存
        saveNode(tag, "fixed", fixedNode);
        saveNode(tag, "pos", posNode);
        saveNode(tag, "neg", negNode);
    }

    private static void saveNode(@NotNull CompoundTag tag, String prefix, BlockPos node) {
        if (node == null) return;
        tag.putInt(prefix + "X", node.getX());
        tag.putInt(prefix + "Y", node.getY());
        tag.putInt(prefix + "Z", node.getZ());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        markedError = false;

        extraConfigs.clear();
        for (String key : STRING_CONFIG_KEYS) {
            if (tag.contains(key)) {
                extraConfigs.put(key, tag.getString(key));
            }
        }

        fixedNode = loadNode(tag, "fixed");
        posNode = loadNode(tag, "pos");
        negNode = loadNode(tag, "neg");

        triggerAsyncLoading();
    }

    private static BlockPos loadNode(@NotNull CompoundTag tag, String prefix) {
        if (!tag.contains(prefix + "X") || !tag.contains(prefix + "Y") || !tag.contains(prefix + "Z")) {
            return null;
        }
        return new BlockPos(tag.getInt(prefix + "X"), tag.getInt(prefix + "Y"), tag.getInt(prefix + "Z"));
    }

    // ==================== 异步加载（从 FunctionalObjBlockEntity 复制） ====================

    private static final java.util.concurrent.ExecutorService LOADING_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fangsu-loading-async");
        t.setDaemon(true);
        return t;
    });

    private java.util.concurrent.CompletableFuture<Void> loadingFuture;
    private java.util.concurrent.CompletableFuture<Void> renderingFuture;

    protected final void triggerAsyncLoading() {
        cancelPendingAsyncLoading();
        loadingFuture = java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                this.whenLoading();
            } catch (Exception e) {
                Main.LOGGER.error("Failed to load block entity {} at {}", getClass().getSimpleName(), getBlockPos(), e);
            }
        }, LOADING_EXECUTOR);
    }

    protected final void cancelPendingAsyncLoading() {
        if (loadingFuture != null && !loadingFuture.isDone()) {
            loadingFuture.cancel(true);
        }
        loadingFuture = null;
    }

    // ==================== 运行时状态 ====================
    private DynamicModelHolder trackModelHolder;
    private RawModel defaultRawModel;
    private boolean modelLoadingFailed = false;

    // R 模式：三个节点位置（不动/正向/反向）
    private BlockPos fixedNode;
    private BlockPos posNode;
    private BlockPos negNode;

    // 直轨模式缓存
    private final Map<String, List<double[]>> chordCache = new ConcurrentHashMap<>();

    // 后台线程池（直轨模式计算弦）
    private ScheduledExecutorService calculatePool;

    // 动画状态
    private double offsetState = 0; // 当前偏移量，用于平滑过渡
    private double angleState = 0;  // 当前角度（道口/旋转模式）

    public BlockEntityRotatingRail(BlockPos pos, BlockState state) {
        super(BLOCK_ENTITY_ROTATING_RAIL.get(), pos, state);
    }

    // ==================== 生命周期 ====================

    public void whenLoading() {
        // 注册默认配置
        ensureExtraConfig("mode", MODE_F);
        ensureExtraConfig("railId", DEFAULT_RAIL_ID);
        ensureExtraConfig("t_length", "1.6");
        ensureExtraConfig("flipV", "1");

        // 直轨模式参数
        ensureExtraConfig("length", "20");
        ensureExtraConfig("in", "4");
        ensureExtraConfig("flip", "1");

        // 旋转模式参数
        ensureExtraConfig("t_count", "20");
        ensureExtraConfig("r", "22.5");

        // 道口模式参数
        ensureExtraConfig("r1", "22.5");
        ensureExtraConfig("r2", "-22.5");

        // 读取当前模式
        String mode = getExtraConfig("mode", MODE_F);

        // 服务端跳过
        if (level == null || !level.isClientSide) return;

        // 加载模型
        loadTrackModel();

        // 直轨模式：启动后台计算线程
        if (MODE_F.equals(mode)) {
            startChordCalculation();
        }
    }

    @Override
    public void whenRendering() {
        ObjBlockScriptContext ctx = this.scriptContext;
        Level world = getLevel();
        if (world == null) return;

        String mode = getExtraConfig("mode", MODE_F);
        boolean hasRedstone = world.getBestNeighborSignal(worldPosition) > 0;

        // 确保模型已加载
        ensureModelReady();

        DynamicModelHolder modelToDraw = trackModelHolder;
        if (modelToDraw == null || modelToDraw.getUploadedModel() == null) {
            return;
        }

        switch (mode) {
            case MODE_F:
                renderModeF(ctx, modelToDraw, hasRedstone);
                break;
            case MODE_R:
                renderModeR(ctx, modelToDraw, hasRedstone);
                break;
            case MODE_CROSSING:
                renderModeCrossing(ctx, modelToDraw, hasRedstone);
                break;
        }
    }

    public void whenDisposing() {
        if (trackModelHolder != null) {
            trackModelHolder.close();
            trackModelHolder = null;
        }
        if (calculatePool != null) {
            calculatePool.shutdown();
            calculatePool = null;
        }
        chordCache.clear();
    }

    // ==================== 模型加载（基于 NteRail 选择） ====================

    /**
     * 返回当前选择的 NteRail，若无效则返回 null。
     */
    @org.jetbrains.annotations.Nullable
    private NteRail getSelectedNteRail() {
        String railId = getExtraConfig("railId", DEFAULT_RAIL_ID);
        NteRail rail = NteRailManager.getInstance().getRail(railId);
        if (rail == null) {
            // 回退到第一个可用轨道
            List<NteRail> rails = NteRailManager.getInstance().getRails();
            if (!rails.isEmpty()) {
                rail = rails.get(0);
            }
        }
        return rail;
    }

    /**
     * 返回每格方块内的轨道段数（segments per block），由所选 NteRail 的 {@link NteRail#repeatInterval()} 推导。
     * repeatInterval 表示每段轨道在方块中的间距，故段密度 = 1 / repeatInterval。
     * 若 NteRail 无效，则回退到配置的 t_length。
     */
    private double getSegmentPerBlock() {
        NteRail rail = getSelectedNteRail();
        if (rail != null && rail.repeatInterval() > 0) {
            return 1.0 / rail.repeatInterval();
        }
        return getExtraConfigFloat("t_length", 1.6f);
    }

    private void loadTrackModel() {
        NteRail rail = getSelectedNteRail();
        if (rail == null || rail.model() == null || rail.model().isEmpty()) {
            Main.LOGGER.warn("[RotatingRail] No valid NteRail selected, using default model");
            loadModelFromPath(DEFAULT_FALLBACK_MODEL, true);
            return;
        }
        loadModelFromPath(rail.model(), rail.flipV());
    }

    private void loadModelFromPath(String modelPath, boolean flipV) {
        try {
            RawModel rawModel = ResourceUtil.loadModel(new ResourceLocation(modelPath), flipV);
            if (rawModel != null) {
                defaultRawModel = rawModel;
                if (trackModelHolder == null) {
                    trackModelHolder = new DynamicModelHolder();
                }
                trackModelHolder.uploadLater(rawModel);
                modelLoadingFailed = false;
            }
        } catch (Exception e) {
            Main.LOGGER.warn("[RotatingRail] Failed to load model {}: {}", modelPath, e.getMessage());
            modelLoadingFailed = true;
        }
    }

    private void ensureModelReady() {
        if (trackModelHolder == null || trackModelHolder.getUploadedModel() == null) {
            loadTrackModel();
        }
    }

    // ==================== 直轨模式 (track_f.js) ====================

    private void renderModeF(ObjBlockScriptContext ctx, DynamicModelHolder model, boolean hasRedstone) {
        double length = getExtraConfigFloat("length", 20f);
        double interval = getExtraConfigFloat("in", 4f);
        double tLength = getSegmentPerBlock();
        String flip = getExtraConfig("flip", "1");

        // 计算目标偏移
        double targetOffset = hasRedstone ? 0 : 2 * interval;

        // 平滑过渡
        if (Math.abs(targetOffset - offsetState) >= 0.025) {
            offsetState += (targetOffset > offsetState ? 1 : -1) * 0.05;
        } else {
            offsetState = targetOffset;
        }

        // 两条平行主轨道：每一条都由正常排布的轨道段组成（不拉伸单个模型）
        double totalSegments = length * tLength;
        int segmentCount = Math.max(1, (int) Math.round(totalSegments));
        double xLeft = offsetState - interval;
        double xRight = offsetState;
        for (int i = 0; i < segmentCount; i++) {
            double z = (i + 0.5) / tLength;

            Matrices left = new Matrices();
            left.translate((float) xLeft, 0, (float) z);
            ctx.drawModel(model, left);

            Matrices right = new Matrices();
            right.translate((float) xRight, 0, (float) z);
            ctx.drawModel(model, right);
        }

        // 获取弦缓存计算结果
        List<double[]> route = getChordResult(length, interval, tLength);

        // 绘制弦上的轨道段（Y+1：原为 -1，现在 0）
        for (double[] q : route) {
            Matrices r = new Matrices();
            float tx = "0".equals(flip)
                    ? (float) (offsetState + q[0])
                    : (float) (offsetState + interval - q[0]);
            r.translate(tx, 0, (float) q[1]);
            r.rotateY("0".equals(flip) ? (float) q[2] : (float) (-q[2]));
            ctx.drawModel(model, r);
        }
    }

    private List<double[]> getChordResult(double length, double interval, double tLength) {
        String key = length + "," + interval + "," + tLength;
        return chordCache.computeIfAbsent(key, k -> generateChords(length, interval, tLength));
    }

    /**
     * 生成圆弧弦上的点，对应 JS 的 generateChords。
     * 两个弧：从 (0,0) 到 (I/2, L/2)，再从 (I/2, L/2) 到 (I, L)
     */
    private List<double[]> generateChords(double length, double interval, double tLength) {
        double step = 1.0 / tLength;

        double r = (length * length + interval * interval) / (4 * interval);
        double[] C1 = {r, 0};
        double[] C2 = {interval - r, length};

        double[] P0 = {0, 0};
        double[] M = {interval / 2, length / 2};
        double[] Pend = {interval, length};

        List<double[]> result = new ArrayList<>();
        result.addAll(collectArc(C1, r, P0, M, step));
        result.addAll(collectArc(C2, r, M, Pend, step));
        return result;
    }

    /**
     * 在圆弧上收集弦的中点位置和角度。
     */
    private List<double[]> collectArc(double[] C, double R, double[] start, double[] end, double step) {
        double eps = 1e-12;
        double startAngle = Math.atan2(start[1] - C[1], start[0] - C[0]);
        double endAngle = Math.atan2(end[1] - C[1], end[0] - C[0]);
        double delta = endAngle - startAngle;
        while (delta > Math.PI) delta -= 2 * Math.PI;
        while (delta < -Math.PI) delta += 2 * Math.PI;

        double sign = delta >= 0 ? 1 : -1;
        double absDelta = Math.abs(delta);
        double alpha = 2 * Math.asin(Math.min(step / (2 * R), 1));

        List<double[]> result = new ArrayList<>();

        if (absDelta <= alpha + eps) {
            double mx = (start[0] + end[0]) / 2;
            double my = (start[1] + end[1]) / 2;
            double d = Math.atan2(Math.abs(end[0] - start[0]), Math.abs(end[1] - start[1]));
            result.add(new double[]{mx, my, d});
            return result;
        }

        double phi = startAngle;
        double curX = start[0];
        double curY = start[1];

        while (true) {
            double nextPhi = phi + sign * alpha;
            double nextX, nextY;
            boolean reachedEnd = false;

            if (sign > 0 && nextPhi >= endAngle - eps) {
                nextPhi = endAngle;
                nextX = end[0];
                nextY = end[1];
                reachedEnd = true;
            } else if (sign < 0 && nextPhi <= endAngle + eps) {
                nextPhi = endAngle;
                nextX = end[0];
                nextY = end[1];
                reachedEnd = true;
            } else {
                nextX = C[0] + R * Math.cos(nextPhi);
                nextY = C[1] + R * Math.sin(nextPhi);
            }

            double mx = (curX + nextX) / 2;
            double my = (curY + nextY) / 2;
            double d = Math.atan2(Math.abs(nextX - curX), Math.abs(nextY - curY));
            result.add(new double[]{mx, my, d});

            if (reachedEnd) break;
            curX = nextX;
            curY = nextY;
            phi = nextPhi;
        }
        return result;
    }

    private void startChordCalculation() {
        if (calculatePool != null && !calculatePool.isShutdown()) {
            return;
        }
        calculatePool = Executors.newScheduledThreadPool(1);
        calculatePool.scheduleAtFixedRate(() -> {
            try {
                double length = getExtraConfigFloat("length", 20f);
                double interval = getExtraConfigFloat("in", 4f);
                double tLength = getSegmentPerBlock();
                getChordResult(length, interval, tLength);
            } catch (Exception e) {
                Main.LOGGER.warn("[RotatingRail] Chord calculation error: {}", e.getMessage());
            }
        }, 0, 10000, TimeUnit.MILLISECONDS);
    }

    // ==================== 旋转模式 (track_r.js) ====================

    /**
     * 旋转模式：与 JS track_r.js 一致——输入弯曲角度（r）与段数（t_count）。
     * 从方块实体原点开始，逐段沿 Z 轴推进并按角度弯曲；红石激活时弯曲归零，未激活时弯曲到 r。
     * 段长（t_length）从所选 NteRail 的 repeatInterval 读取。
     */
    private void renderModeR(ObjBlockScriptContext ctx, DynamicModelHolder model, boolean hasRedstone) {
        double tLength = getSegmentPerBlock();
        double angle = getExtraConfigFloat("r", 22.5f);
        int count = Math.max(1, (int) getExtraConfigFloat("t_count", 20f));
        if (angle == 0 || angle == 180 || Float.isNaN((float) angle)) {
            angle = 22.5;
        }

        // 平滑动画：红石激活 → 角度归零；未激活 → 弯曲到 angle
        double targetAngle = hasRedstone ? 0 : angle;
        if (Math.abs(targetAngle - angleState) >= 0.1) {
            angleState += (targetAngle > angleState ? 1 : -1) * 0.1 * (Math.abs(angle) / angle);
        } else {
            angleState = targetAngle;
        }

        // 与 JS 相同：同一个矩阵上累计 translate + rotateY，逐段绘制
        Matrices r = new Matrices();
        // Y+1（原 JS 为 -1）
        r.translate(0, 0, 0);
        for (int q = 0; q <= count; q++) {
            r.translate(0, 0, (float) (1.0 / tLength));
            r.rotateY((float) (angleState / count * (Math.PI / 180)));
            ctx.drawModel(model, r);
        }
    }

    // ==================== 道口模式 (track_crossing.js) ====================

    private void renderModeCrossing(ObjBlockScriptContext ctx, DynamicModelHolder model, boolean hasRedstone) {
        double tLength = getSegmentPerBlock();
        double length = getExtraConfigFloat("length", 6f);
        double r1 = getExtraConfigFloat("r1", 22.5f);
        double r2 = getExtraConfigFloat("r2", -22.5f);

        // 红石控制角度
        double targetAngle = hasRedstone ? r1 : r2;

        if (Math.abs(targetAngle - angleState) >= 0.4) {
            angleState += (targetAngle > angleState ? 1 : -1) * 0.8;
        } else {
            angleState = targetAngle;
        }

        // 旋转的轨道：按正常轨距排布（不再拉伸单个模型），Y+1 渲染。
        // 先旋转再平移（rotateY 在前），使整段轨道都绕方块实体中心（同一中心）旋转，
        // 而非每个轨道段绕自身中心旋转。
        int segmentCount = Math.max(1, (int) Math.round(length * tLength));
        for (int i = 0; i < segmentCount; i++) {
            Matrices seg = new Matrices();
            seg.rotateY((float) (angleState * (Math.PI / 180)));
            seg.translate(0, 0, (float) ((i + 0.5) / tLength));
            ctx.drawModel(model, seg);
        }
    }

    // ==================== 配置界面 ====================
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        Map<String, String> extra = this.extraConfigs;

        // 模式选择（EnumConfig 使用 int 索引）
        List<Component> modeEntries = List.of(
                ComponentHelper.translatable("ui.fangsu.rotating_rail.mode.f"),
                ComponentHelper.translatable("ui.fangsu.rotating_rail.mode.r"),
                ComponentHelper.translatable("ui.fangsu.rotating_rail.mode.crossing")
        );
        List<String> modeValues = List.of(MODE_F, MODE_R, MODE_CROSSING);
        configs.add(new EnumConfig(
                ComponentHelper.translatable("ui.fangsu.rotating_rail.mode"),
                new ConfigSpec("list"),
                modeEntries,
                () -> {
                    String cur = extra.getOrDefault("mode", MODE_F);
                    return Math.max(0, modeValues.indexOf(cur));
                },
                idx -> {
                    String newMode = modeValues.get(idx);
                    extra.put("mode", newMode);
                    setMode(newMode);
                }
        ).setSaveOnChange(true));

        // 轨道模型选择：使用 ModelSelectScreen 打开（从 NteRailManager 中选择 NteRail）
        configs.add(new RunnableConfig(
                ComponentHelper.translatable("ui.fangsu.rotating_rail.rail"),
                new ConfigSpec("func"),
                () -> ClientHooks.openRotatingRailModelSelectScreen(BlockEntityRotatingRail.this)
        ).setButtonText(ComponentHelper.translatable("ui.fangsu.rotating_rail.rail_select")));

        String currentMode = extra.getOrDefault("mode", MODE_F);

        // 模式特有参数
        switch (currentMode) {
            case MODE_F:
                addFConfigs(configs, extra);
                break;
            case MODE_R:
                addRConfigs(configs, extra);
                break;
            case MODE_CROSSING:
                addCrossingConfigs(configs, extra);
                break;
        }

        return configs;
    }

    private void addFConfigs(List<ConfigEntry<?>> configs, Map<String, String> extra) {
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.rotating_rail.length"),
                new ConfigSpec("number"),
                () -> getExtraConfigFloat("length", 20f),
                val -> {
                    extra.put("length", String.valueOf(val));
                    chordCache.clear();
                    sendUpdateC2S();
                }
        ));
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.rotating_rail.interval"),
                new ConfigSpec("number"),
                () -> getExtraConfigFloat("in", 4f),
                val -> {
                    extra.put("in", String.valueOf(val));
                    chordCache.clear();
                    sendUpdateC2S();
                }
        ));
        configs.add(new BoolConfig(
                ComponentHelper.translatable("ui.fangsu.rotating_rail.flip"),
                new ConfigSpec("bool"),
                () -> "1".equals(extra.getOrDefault("flip", "1")),
                val -> {
                    extra.put("flip", val ? "1" : "0");
                    sendUpdateC2S();
                }
        ).setSaveOnChange(true));
    }

    private void addRConfigs(List<ConfigEntry<?>> configs, Map<String, String> extra) {
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.rotating_rail.t_count"),
                new ConfigSpec("number"),
                () -> getExtraConfigFloat("t_count", 20f),
                val -> {
                    extra.put("t_count", String.valueOf(val));
                    sendUpdateC2S();
                }
        ));
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.rotating_rail.r"),
                new ConfigSpec("number"),
                () -> getExtraConfigFloat("r", 22.5f),
                val -> {
                    extra.put("r", String.valueOf(val));
                    sendUpdateC2S();
                }
        ));
    }

    private void addCrossingConfigs(List<ConfigEntry<?>> configs, Map<String, String> extra) {
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.rotating_rail.length"),
                new ConfigSpec("number"),
                () -> getExtraConfigFloat("length", 6f),
                val -> {
                    extra.put("length", String.valueOf(val));
                    sendUpdateC2S();
                }
        ));
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.rotating_rail.r1"),
                new ConfigSpec("number"),
                () -> getExtraConfigFloat("r1", 22.5f),
                val -> {
                    extra.put("r1", String.valueOf(val));
                    sendUpdateC2S();
                }
        ));
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.rotating_rail.r2"),
                new ConfigSpec("number"),
                () -> getExtraConfigFloat("r2", -22.5f),
                val -> {
                    extra.put("r2", String.valueOf(val));
                    sendUpdateC2S();
                }
        ));
    }

    private void setMode(String newMode) {
        setExtraConfig("mode", newMode);
        // 切换模式时重新计算弦
        if (MODE_F.equals(newMode)) {
            startChordCalculation();
        }
        sendUpdateC2S();
    }

    public void reloadModel() {
        if (trackModelHolder != null) {
            trackModelHolder.close();
            trackModelHolder = null;
        }
        defaultRawModel = null;
        modelLoadingFailed = false;
        loadTrackModel();
    }

    // ==================== 扳手交互 ====================

    @Override
    public InteractionResult useWithWrench(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            ClientHooks.openRotatingRailConfigScreen(this);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ==================== BaseObjBlockEntity 抽象方法 ====================

    @Override
    public String getMainModelKey() {
        return "rotatingRail";
    }

    // ==================== 清理 ====================

    @Override
    public void setRemoved() {
        if (!disposed) {
            whenDisposing();
            cancelPendingAsyncLoading();
        }
        super.setRemoved();
    }

    // ==================== 碰撞箱 ====================

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        return Shapes.block();
    }


}
