package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.client.ClientHooks;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.network.ModNetwork;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 功能性方块实体的抽象基类，继承自 {@link BaseObjBlockEntity}。
 * <p>
 * 定义了 extraConfig 的存取、客户端↔服务端同步、配置屏幕等通用功能。
 * 所有需要 extraConfig 的方块实体应继承此类。
 */
public abstract class FunctionalObjBlockEntity extends BaseObjBlockEntity implements Syncable {

    protected Map<String, String> extraConfigs = new ConcurrentHashMap<>();

    public float translateX = 0, translateY = 0, translateZ = 0;
    public float rotateX = 0, rotateY = 0, rotateZ = 0;

    /**
     * 共享后台线程池，用于将 whenLoading 中的 JSON 解析、模型加载等操作从主线程移走。
     */
    private static final ExecutorService LOADING_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fangsu-loading-async");
        t.setDaemon(true);
        return t;
    });

    /**
     * 渲染专用后台线程池，用于将 {@link #whenRendering()} 中的
     * 模型变换、Java2D 绘制等 CPU 密集型操作从渲染线程移走，
     * 避免阻塞主线程的帧率。
     * <p>
     * 注意：不涉及 OpenGL 调用的操作（如矩阵变换、drawModel 入队）
     * 可以安全地在后台线程执行。涉及 GL 的操作仍必须在渲染线程执行。
     */
    private static final ExecutorService RENDERING_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fangsu-rendering-async");
        t.setDaemon(true);
        return t;
    });

    /**
     * 进行中的异步加载任务，用于 whenLoading 异步化以及 whenDisposing 时取消。
     */
    private CompletableFuture<Void> loadingFuture;

    /**
     * 标记异步加载（whenLoading）是否已完成。
     * 在 {@link #whenRendering()} 中检查此标记，未完成时直接 return。
     */
    private volatile boolean loadingComplete = false;

    /**
     * 异步渲染任务。当 {@link #tryBeginRendering()} 返回 true 时，
     * {@link #whenRendering()} 被提交到此 future 在后台线程执行，
     * 渲染线程可继续处理其他方块实体的渲染收集阶段。
     */
    private CompletableFuture<Void> renderingTask;

    /**
     * 最近一次后台 whenRendering 是否已把结果填充进 scriptResultWriting。
     * 渲染线程只在结果为 true 时才交换双缓冲并提交，避免 GL 线程阻塞等待。
     */
    private volatile boolean renderResultReady = false;

    public FunctionalObjBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    // ==================== ExtraConfig 存取 ====================

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

    public final boolean getExtraConfigBool(String key, boolean defaultValue) {
        String value = extraConfigs.get(key);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value);
    }

    public final int getExtraConfigInt(String key, int defaultValue) {
        String value = extraConfigs.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public final float getExtraConfigFloat(String key, float defaultValue) {
        String value = extraConfigs.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    // ==================== NBT 持久化 ====================

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putFloat("translateX", translateX);
        tag.putFloat("translateY", translateY);
        tag.putFloat("translateZ", translateZ);
        tag.putFloat("rotateX", rotateX);
        tag.putFloat("rotateY", rotateY);
        tag.putFloat("rotateZ", rotateZ);

        try {
            this.whenSaving(extraConfigs);
        } catch (Exception e) {
            Main.LOGGER.error("Failed to save extra configs for {} at {}", getClass().getSimpleName(), getBlockPos(), e);
        }

        if (extraConfigs != null) {
            CompoundTag subConfigTag = new CompoundTag();
            for (String key : extraConfigs.keySet()) {
                String value = extraConfigs.get(key);
                if (value != null) subConfigTag.putString(key, value);
            }
            tag.put("extraConfig", subConfigTag);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);

        translateX = tag.contains("translateX") ? tag.getFloat("translateX") : 0;
        translateY = tag.contains("translateY") ? tag.getFloat("translateY") : 0;
        translateZ = tag.contains("translateZ") ? tag.getFloat("translateZ") : 0;
        rotateX = tag.contains("rotateX") ? tag.getFloat("rotateX") : 0;
        rotateY = tag.contains("rotateY") ? tag.getFloat("rotateY") : 0;
        rotateZ = tag.contains("rotateZ") ? tag.getFloat("rotateZ") : 0;

        extraConfigs.clear();
        if (tag.contains("extraConfig")) {
            CompoundTag subConfigTag = tag.getCompound("extraConfig");
            for (String key : subConfigTag.getAllKeys()) {
                extraConfigs.put(key, subConfigTag.getString(key));
            }
        }

        triggerAsyncLoading();
    }

    /**
     * 保存前回调，允许子类在 extraConfigs 写入 NBT 之前补充或修改配置。
     */
    public void whenSaving(Map<String, String> extraConfigs) {
    }

    // ==================== 网络同步 (C2S) ====================

    @Override
    public void writeC2S(FriendlyByteBuf buf) {
        buf.writeFloat(translateX);
        buf.writeFloat(translateY);
        buf.writeFloat(translateZ);
        buf.writeFloat(rotateX);
        buf.writeFloat(rotateY);
        buf.writeFloat(rotateZ);
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

        int smSize = Math.min(subModels.size(), 256);
        buf.writeInt(smSize);
        count = 0;
        for (String key : subModels.keySet()) {
            if (count >= smSize) break;
            String value = subModels.get(key);
            buf.writeUtf(key != null ? key : "");
            buf.writeUtf(value != null ? value : "");
            count++;
        }
    }

    @Override
    public void readC2S(FriendlyByteBuf buf) {
        translateX = buf.readFloat();
        translateY = buf.readFloat();
        translateZ = buf.readFloat();
        rotateX = buf.readFloat();
        rotateY = buf.readFloat();
        rotateZ = buf.readFloat();
        mainModel = buf.readUtf();
        if (mainModel.isEmpty()) mainModel = null;

        int size = Math.min(buf.readInt(), 256);
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            String value = buf.readUtf(1024);
            if (key != null && !key.isEmpty()) {
                extraConfigs.put(key, value != null ? value : "");
            }
        }

        size = Math.min(buf.readInt(), 256);
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            String value = buf.readUtf(1024);
            if (key != null && !key.isEmpty()) {
                subModels.put(key, value != null ? value : "");
            }
        }

        // 重新加载配置到字段（确保 isolation/doorOpenOverride 等同步）
        triggerAsyncLoading();
        this.setChanged();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    3
            );
        }
        this.setChanged();
    }

    /**
     * 触发异步加载，将 {@link #whenLoading()} 的执行移至后台线程，
     * 避免阻塞主线程（世界加载 / 网络同步）。
     * <p>
     * 如果已有进行中的异步加载任务，会先取消上一个任务再提交新的。
     */
    protected final void triggerAsyncLoading() {
        // 取消上一个进行中的异步加载任务
        cancelPendingAsyncLoading();

        loadingFuture = CompletableFuture.runAsync(() -> {
            try {
                this.whenLoading();
            } catch (Exception e) {
                Main.LOGGER.error("Failed to load block entity {} at {}", getClass().getSimpleName(), getBlockPos(), e);
            }
        }, LOADING_EXECUTOR).thenRun(() -> loadingComplete = true);
    }

    /**
     * 取消进行中的异步加载任务。
     */
    protected final void cancelPendingAsyncLoading() {
        if (loadingFuture != null && !loadingFuture.isDone()) {
            loadingFuture.cancel(true);
        }
        loadingFuture = null;
        loadingComplete = false;
    }

    /**
     * 检查异步加载是否已完成。
     *
     * @return 如果没有异步加载任务或任务已完成，返回 true
     */
    protected final boolean isAsyncLoadingDone() {
        return loadingFuture == null || loadingFuture.isDone();
    }

    /**
     * 同步等待异步加载完成（阻塞当前线程）。
     * 仅在明确需要确保加载完成后的副作用时使用。
     */
    protected final void awaitAsyncLoading() {
        if (loadingFuture != null && !loadingFuture.isDone()) {
            try {
                loadingFuture.get();
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== whenRendering 异步化辅助 ====================

    /**
     * 尝试在后台线程执行一次 whenRendering，绝不阻塞 GL 渲染线程。
     * <p>
     * 若加载未完成、上一次 whenRendering 仍在执行、或上一次结果尚未被交换提交，
     * 则不重复提交（直接复用上一帧已就绪的结果）。
     *
     * @return 是否提交了新的后台渲染任务
     */
    public final boolean tryBeginRendering() {
        if (!loadingComplete) return false;
        if (renderResultReady) return false;
        if (renderingTask != null && !renderingTask.isDone()) return false;
        renderResultReady = false;
        renderingTask = CompletableFuture.runAsync(() -> {
            try {
                this.whenRendering();
            } catch (Exception e) {
                Main.LOGGER.error("Async whenRendering error for {} at {}: {}",
                        getClass().getSimpleName(), getBlockPos(), e.getMessage());
            } finally {
                renderResultReady = true;
            }
        }, RENDERING_EXECUTOR);
        return true;
    }

    /**
     * 后台 whenRendering 是否已把结果填充进 scriptResultWriting（可交换提交）。
     */
    public final boolean isRenderResultReady() {
        return renderResultReady;
    }

    /**
     * 若后台结果已就绪则消费该标记并返回 true（此时渲染线程应执行双缓冲交换），
     * 否则返回 false（复用上一帧结果，不交换，避免读到半写入缓冲）。
     */
    public final boolean consumeRenderResultIfReady() {
        if (renderResultReady) {
            renderResultReady = false;
            return true;
        }
        return false;
    }

    /**
     * 标记当帧渲染已完成，允许下一帧继续触发渲染。
     * 仅当后台任务已完成才清空引用，避免与仍在执行的 whenRendering 冲突。
     */
    public final void finishRendering() {
        if (renderingTask != null && renderingTask.isDone()) {
            renderingTask = null;
        }
    }

    public abstract void whenLoading();

    /**
     * Called when this block entity is being disposed (e.g. chunk unload / removal).
     */
    public void whenDisposing() {
    }

    @Override
    public void setRemoved() {
        if (!disposed) {
            whenDisposing();
            cancelPendingAsyncLoading();
            // 取消进行中的异步渲染任务
            if (renderingTask != null && !renderingTask.isDone()) {
                renderingTask.cancel(true);
                renderingTask = null;
            }
        }
        super.setRemoved();
    }

    void syncToServer() {
        if (level != null && level.isClientSide) {
            this.whenSaving(this.extraConfigs);
            if (!level.hasChunk(getBlockPos().getX() >> 4, getBlockPos().getZ() >> 4)) return;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBlockPos(getBlockPos());
            writeC2S(buf);
            NetworkManager.sendToServer(ModNetwork.BE_SYNC, buf);
        }
    }

    public void sendUpdateC2S() {
        if (level != null && level.isClientSide)
            syncToServer();
        this.setChanged();
        this.markShapeDirty();
    }

    // ==================== 坐标变换 ====================

    public Vec3 worldToLocal(Vec3 worldPos) {
        Level level = this.getLevel();
        if (level == null) return Vec3.ZERO;

        BlockPos pos = this.getBlockPos();
        Direction facing = level.getBlockState(pos)
                .getValue(BaseObjBlock.FACING);

        // 1. 世界坐标 → 碰撞箱原点
        Vec3 v = worldPos.subtract(
                pos.getX() + 0.5,
                pos.getY(),
                pos.getZ() + 0.5
        );

        // 2. 反向高级平移
        v = v.subtract(
                this.translateX,
                this.translateY,
                this.translateZ
        );

        // 3. 反向高级旋转（顺序必须和渲染相反）
        v = rotateZ(v, -this.rotateZ);
        v = rotateY(v, -this.rotateY);
        v = rotateX(v, -this.rotateX);

        // 4. 反向方块朝向
        v = rotateY(v, (float) Math.toRadians(facing.toYRot()));

        return v;
    }

    private static Vec3 rotateX(Vec3 v, float rad) {
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new Vec3(
                v.x,
                v.y * cos - v.z * sin,
                v.y * sin + v.z * cos
        );
    }

    private static Vec3 rotateY(Vec3 v, float rad) {
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new Vec3(
                v.x * cos + v.z * sin,
                v.y,
                -v.x * sin + v.z * cos
        );
    }

    private static Vec3 rotateZ(Vec3 v, float rad) {
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new Vec3(
                v.x * cos - v.y * sin,
                v.x * sin + v.y * cos,
                v.z
        );
    }

    // ==================== 配置屏幕 ====================

    @Override
    public InteractionResult useWithWrench(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            ClientHooks.openObjBlockConfigScreen(this);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public List<ConfigEntry<?>> getConfigs() {
        return null;
    }
}
