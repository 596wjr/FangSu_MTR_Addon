package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.client.ClientHooks;
import com.fangsu.blocks.ModBlocks;
import com.fangsu.mtr.RailAngleExtra;
import com.fangsu.network.ModNetwork;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.util.NodeConnector;
import com.fangsu.utils.ResourceUtil;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mtr.block.BlockNode;
import mtr.data.RailAngle;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 万向节点方块实体。
 * <p>
 * 方块本体是 MTR 的 {@link BlockNode} 子类，因此 MTR 的轨道连接器 / 轨道删除逻辑 / 连接状态
 * （blockstate {@code IS_CONNECTED}）全部照常工作；本实体额外承载“任意角度 + 绑定状态”。
 * <p>
 * NBT 存储（与 MTR4 版保持一致）：
 * <ul>
 *   <li>{@code direction} (double) — 当前方向（度，0=东, 90=南, 180=西, 270=北），未绑定时为 0</li>
 *   <li>{@code connected} (bool) — 是否已连接轨道（由 blockstate IS_CONNECTED 派生，单一数据源）</li>
 *   <li>{@code directionBonded} (bool) — 方向是否已绑定；未绑定(false)时模型持续旋转</li>
 * </ul>
 * 交互：扳手 / 刷子右键打开角度配置界面（客户端注入，见 ClientHooks）。
 */
public class BlockEntityMultiDirectionNode extends BaseObjBlockEntity implements Syncable {

    private static final String DEFAULT_MODEL = "fangsu:models/obj/node.obj";
    private static final String CONNECTED_MODEL = "fangsu:models/obj/node_connected.obj";

    // ==================== NBT 键 ====================
    public static final String KEY_DIRECTION = "direction";
    public static final String KEY_CONNECTED = "connected";
    public static final String KEY_DIRECTION_BONDED = "directionBonded";

    // ==================== 运行时状态 ====================
    /** 当前方向（度）。 */
    private double direction;
    /** 方向是否已绑定。 */
    private boolean directionBonded;
    /** 连接状态缓存；真正的数据源是 blockstate IS_CONNECTED，此处仅用于 NBT 与渲染读取。 */
    private boolean connected;

    private DynamicModelHolder modelHolder;
    private DynamicModelHolder connectedModelHolder;

    // ==================== 异步加载（照 BlockEntityRotatingRail） ====================
    private static final ExecutorService LOADING_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fangsu-node-loading-async");
        t.setDaemon(true);
        return t;
    });

    private CompletableFuture<Void> loadingFuture;

    public BlockEntityMultiDirectionNode(BlockPos pos, BlockState state) {
        super(ModBlocks.BLOCK_ENTITY_MULTI_DIRECTION_NODE.get(), pos, state);
    }

    // ==================== 供连接器 / 角度界面读取的公开接口 ====================

    /** 当前方向（度）。 */
    public double getDirectionDegrees() {
        return direction;
    }

    /** 方向是否已绑定。 */
    public boolean isDirectionBonded() {
        return directionBonded;
    }

    /**
     * 是否已连接轨道。
     * <p>
     * 以 blockstate {@link BlockNode#IS_CONNECTED} 为准：MTR 的连接器建轨、轨道删除器、
     * 拆除节点等逻辑都直接改写该属性，BE 侧的 {@code connected} 只是派生缓存。
     */
    public boolean isConnected() {
        final BlockState state = getBlockState();
        if (state.getBlock() instanceof BlockNode && state.hasProperty(BlockNode.IS_CONNECTED)) {
            return state.getValue(BlockNode.IS_CONNECTED);
        }
        return connected;
    }

    /** 已绑定时返回任意角度 {@link RailAngle}；未绑定返回 null（连接器据此判定“自由端”）。 */
    public RailAngle getRailAngle() {
        return directionBonded ? NodeConnector.angleFor(direction) : null;
    }

    /** 绑定角度（度）。服务端直接刷新方块数据，客户端走 C2S 同步。 */
    public void bind(double degrees) {
        this.direction = RailAngleExtra.normalizeNodeDegrees(degrees);
        this.directionBonded = true;
        markChangedAndSync();
    }

    /** 与 MTR4 版同名的便捷入口。 */
    public void setDirectionAndBind(double degrees) {
        bind(degrees);
    }

    /** 与 MTR4 版同名的便捷入口（连接器建轨成功后写入实际使用的角度）。 */
    public void setDirectionBonded(double degrees) {
        bind(degrees);
    }

    /**
     * 两端均未绑定时，把两个节点绑定到彼此连线的方向（直线轨道）。
     * <p>
     * 照 ANTE 蓝本：本端先计算到对方的方位角并绑定，再触发对方对称绑定；
     * 对方回过来调用本端时本端已绑定，直接返回，不会递归。
     */
    public void bind(BlockEntityMultiDirectionNode other) {
        if (directionBonded) {
            return;
        }
        final BlockPos self = getBlockPos();
        final BlockPos target = other.getBlockPos();
        bind(Math.toDegrees(Math.atan2(target.getZ() - self.getZ(), target.getX() - self.getX())));
        other.bind(this);
    }

    /** 解除绑定（已连接轨道的节点不允许解除，避免轨道几何失去约束）。 */
    public void unbind() {
        if (isConnected()) {
            return;
        }
        this.directionBonded = false;
        this.direction = 0D;
        markChangedAndSync();
    }

    /** 设置连接状态（写 blockstate，保持与 MTR 语义一致）。 */
    public void setConnected(boolean value) {
        final Level level = getLevel();
        if (level == null) {
            return;
        }
        final BlockState state = getBlockState();
        if (state.getBlock() instanceof BlockNode && state.hasProperty(BlockNode.IS_CONNECTED)
                && state.getValue(BlockNode.IS_CONNECTED) != value) {
            level.setBlockAndUpdate(getBlockPos(), state.setValue(BlockNode.IS_CONNECTED, value));
        }
        syncConnectedCache();
    }

    /** 由 blockstate 刷新 BE 的连接状态缓存并落盘（MTR 连接器改 blockstate 后调用）。 */
    public void syncConnectedCache() {
        final boolean nowConnected = isConnected();
        if (this.connected != nowConnected) {
            this.connected = nowConnected;
            this.setChanged();
        }
    }

    private void markChangedAndSync() {
        this.setChanged();
        final Level level = getLevel();
        if (level == null) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            // 服务端：通知客户端方块数据变化（触发 getUpdatePacket）
            serverLevel.getChunkSource().blockChanged(getBlockPos());
        } else {
            sendUpdateC2S();
        }
    }

    // ==================== 网络同步 ====================

    /** 客户端 → 服务端：发送当前方向 / 绑定状态（复用 ModNetwork.BE_SYNC）。 */
    public void sendUpdateC2S() {
        if (level != null && level.isClientSide) {
            final BlockPos pos = getBlockPos();
            if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeBlockPos(pos);
                writeC2S(buf);
                NetworkManager.sendToServer(ModNetwork.BE_SYNC, buf);
            }
        }
        this.setChanged();
    }

    @Override
    public void writeC2S(FriendlyByteBuf buf) {
        buf.writeDouble(direction);
        buf.writeBoolean(directionBonded);
    }

    @Override
    public void readC2S(FriendlyByteBuf buf) {
        final double newDirection = buf.readDouble();
        // C2S 数据不可信：非法数值直接丢弃（否则会写出 NaN 几何的轨道）
        if (!Double.isFinite(newDirection)) {
            Main.LOGGER.warn("[MultiDirectionNode] 收到非法角度 {} @ {}", newDirection, getBlockPos());
            return;
        }
        this.direction = RailAngleExtra.normalizeNodeDegrees(newDirection);
        this.directionBonded = buf.readBoolean();
        syncConnectedCache();
        this.setChanged();
        // 服务端收到改绑后立刻按新角度重建已连接轨道
        NodeConnector.rebuildRailsAtNode(level, getBlockPos());
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // ==================== NBT 持久化 ====================

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (markedError) {
            return;
        }
        tag.putDouble(KEY_DIRECTION, direction);
        // connected 以 blockstate 为准，写入 NBT 只是为了与 MTR4 版数据形状一致
        tag.putBoolean(KEY_CONNECTED, isConnected());
        tag.putBoolean(KEY_DIRECTION_BONDED, directionBonded);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        markedError = false;
        this.direction = tag.getDouble(KEY_DIRECTION);
        this.directionBonded = tag.getBoolean(KEY_DIRECTION_BONDED);
        this.connected = tag.getBoolean(KEY_CONNECTED);
        // blockstate 是连接状态的权威来源（服务端 / 客户端都会同步到）
        syncConnectedCache();
        triggerAsyncLoading();
        // 服务端：世界重载后按任意角度重建连接到本节点的轨道（MTR 反序列化会把角度量化到 22.5°）
        if (level instanceof ServerLevel) {
            NodeConnector.rebuildRailsAtNode(level, getBlockPos());
        }
    }

    // ==================== 生命周期 ====================

    private void triggerAsyncLoading() {
        cancelPendingAsyncLoading();
        loadingFuture = CompletableFuture.runAsync(() -> {
            try {
                this.whenLoading();
            } catch (Exception e) {
                Main.LOGGER.error("加载万向节点方块实体失败 {} @ {}", getClass().getSimpleName(), getBlockPos(), e);
            }
        }, LOADING_EXECUTOR);
    }

    private void cancelPendingAsyncLoading() {
        if (loadingFuture != null && !loadingFuture.isDone()) {
            loadingFuture.cancel(true);
        }
        loadingFuture = null;
    }

    private boolean isAsyncLoadingDone() {
        return loadingFuture == null || loadingFuture.isDone();
    }

    /** 渲染就绪条件：模型异步加载已完成（避免渲染线程读到未就绪的模型）。 */
    @Override
    protected boolean isRenderReady() {
        return isAsyncLoadingDone();
    }

    public void whenLoading() {
        // 仅客户端需要模型：服务端加载 OBJ（文件 IO + 解析）会阻塞主线程
        if (level == null || !level.isClientSide) {
            return;
        }
        ensureModelReady();
    }

    /** 确保未连接 / 已连接两套模型已就绪（照 BlockEntityRotatingRail 的 uploadLater 模式）。 */
    private void ensureModelReady() {
        if (modelHolder == null || modelHolder.getUploadedModel() == null) {
            try {
                final RawModel rawModel = ResourceUtil.loadModel(new ResourceLocation(DEFAULT_MODEL), false);
                if (rawModel != null) {
                    if (modelHolder == null) {
                        modelHolder = new DynamicModelHolder();
                    }
                    modelHolder.uploadLater(rawModel);
                }
            } catch (Exception e) {
                Main.LOGGER.warn("[MultiDirectionNode] 加载模型 {} 失败: {}", DEFAULT_MODEL, e.getMessage(), e);
            }
        }

        if (connectedModelHolder == null || connectedModelHolder.getUploadedModel() == null) {
            try {
                final RawModel rawModel = ResourceUtil.loadModel(new ResourceLocation(CONNECTED_MODEL), false);
                if (rawModel != null) {
                    if (connectedModelHolder == null) {
                        connectedModelHolder = new DynamicModelHolder();
                    }
                    connectedModelHolder.uploadLater(rawModel);
                }
            } catch (Exception e) {
                Main.LOGGER.warn("[MultiDirectionNode] 加载模型 {} 失败: {}", CONNECTED_MODEL, e.getMessage(), e);
            }
        }
    }

    /**
     * 渲染逻辑（运行在方块实体的异步渲染线程上，禁止触碰客户端专属类）。
     * <p>
     * 未连接：绘制 node.obj —— 未绑定时绕 Y 轴匀速旋转（2 秒一圈），已绑定时按方向固定；
     * 已连接：绘制 node_connected.obj（始终绘制）。
     * <p>
     * 与 MTR4 版的差异：MTR4 在“已连接”时默认隐藏模型、仅手持轨道道具时显示
     * （因为 MTR4 本体会在此时渲染节点）；而 MTR3 的节点是常显的方块模型，
     * 若照搬隐藏逻辑，已连接节点将彻底不可见，故这里始终绘制。
     */
    @Override
    public void whenRendering() {
        final ObjBlockScriptContext ctx = this.scriptContext;
        if (ctx == null || level == null) {
            return;
        }
        ensureModelReady();

        final boolean connectedNow = isConnected();
        final DynamicModelHolder holder = connectedNow ? connectedModelHolder : modelHolder;
        if (holder == null || holder.getUploadedModel() == null) {
            return;
        }

        final double rotation;
        if (connectedNow || directionBonded) {
            // 与 MTR 节点显示一致：方向为顺时针罗盘角，rotateY 取负（并补正模型自身朝向）
            rotation = -Math.toRadians(direction) + Math.PI / 2;
        } else {
            // 未绑定：绕 Y 轴匀速 360° 旋转
            rotation = (System.currentTimeMillis() % 2000L) / 2000.0 * (Math.PI * 2);
        }

        final Matrices mat = new Matrices();
        // 原点平移由 BaseBlockEntityRender 统一施加（candyPose.translate(0.5, 0, 0.5)），此处不能再平移
        mat.rotateY((float) rotation);
        ctx.drawModel(holder, mat);
    }

    public void whenDisposing() {
        if (modelHolder != null) {
            modelHolder.close();
            modelHolder = null;
        }
        if (connectedModelHolder != null) {
            connectedModelHolder.close();
            connectedModelHolder = null;
        }
    }

    @Override
    public void setRemoved() {
        if (!disposed) {
            whenDisposing();
            cancelPendingAsyncLoading();
        }
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level == null || !level.isClientSide) {
            return;
        }
        ensureModelReady();
    }

    // ==================== 交互 ====================

    @Override
    public InteractionResult useWithWrench(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) {
            ClientHooks.openMultiDirectionNodeScreen(this);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResult whenUseWithBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // 刷子与扳手同样打开角度界面（MTR3 无原版轨道形状编辑界面可复用）
        if (level.isClientSide) {
            ClientHooks.openMultiDirectionNodeScreen(this);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ==================== BaseObjBlockEntity 抽象方法 ====================

    @Override
    public String getMainModelKey() {
        return "multiDirectionNode";
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        // 已连接时形状极薄（与原版 MTR 节点一致），避免阻挡玩家视线追踪轨道
        if (isConnected()) {
            return Shapes.box(0.1, 0, 0.1, 0.9, 0.0625, 0.9);
        }
        return Shapes.block();
    }
}
