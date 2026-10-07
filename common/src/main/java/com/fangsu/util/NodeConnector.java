package com.fangsu.util;

import com.fangsu.Main;
import com.fangsu.blockEntities.BlockEntityMultiDirectionNode;
import com.fangsu.data.hybrid.RailActionsModuleExtraSupplier;
import com.fangsu.mtr.RailAngleExtra;
import com.fangsu.mtr.RailCalculator;
import mtr.block.BlockNode;
import mtr.data.Rail;
import mtr.data.RailAngle;
import mtr.data.RailType;
import mtr.data.RailwayData;
import mtr.data.TransportMode;
import mtr.packet.PacketTrainDataGuiServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

/**
 * 万向节点连接几何与建轨工具（MTR3 版）。
 * <p>
 * 结构对应 MTR4 版的 {@code com.fangsu.util.NodeConnector}，但底层换成 MTR3 的
 * {@link Rail} / {@link RailAngle} / {@link RailwayData} API：
 * <ul>
 *   <li>{@link #straightAngle(BlockPos, BlockPos)} — 两端点水平连线角度（直线轨道方向）</li>
 *   <li>{@link #maxRadiusTangentAngle(BlockPos, double, BlockPos)} — 未绑定端点的最大半径圆弧切向角
 *       （委托 {@link RailCalculator#calculateMaxRadiusAngle}）</li>
 *   <li>{@link #getDirectionDegrees(Level, BlockPos)} / {@link #isBonded(Level, BlockPos)} — 读取节点状态</li>
 *   <li>{@link #findConnectedEndpoints(BlockPos)} — 客户端查找连接到节点位置的其他端点</li>
 *   <li>{@link #createAndSendRail} — 服务端按任意角度构建并派发铁轨（对应 MTR3 原版
 *       {@code ItemRailModifier.onConnect} 的建轨流程，但跳过 {@code isValid()} 的枚举引用相等判定）</li>
 *   <li>{@link #rebuildRailsAtNode} — 服务端按节点当前绑定角度重建连接到该节点的轨道
 *       （世界重载 / 角度改绑后修正几何）</li>
 * </ul>
 */
public final class NodeConnector {

    /**
     * 几何有效性阈值：{@code Rail} 构造在无解时会退化为全 0 参数，此时轨道长度为 0。
     */
    public static final double LENGTH_EPSILON = 1.0E-4;

    /**
     * 22.5° 栅格（MTR 原生 16 方向步进）。
     */
    private static final double ANGLE_GRID = 22.5D;

    private NodeConnector() {
    }

    // ==================== 基础几何 ====================

    /**
     * 水平面上两端点的连线角度（度，0=东, 90=南, 180=西, 270=北）。用于直线轨道方向绑定。
     */
    public static double straightAngle(BlockPos a, BlockPos b) {
        final double dx = b.getX() - a.getX();
        final double dz = b.getZ() - a.getZ();
        return normalizeDegrees(Math.toDegrees(Math.atan2(dz, dx)));
    }

    /**
     * 归一化角度到 [0, 360)。
     */
    public static double normalizeDegrees(double deg) {
        deg = deg % 360.0;
        if (deg < 0) deg += 360.0;
        return deg;
    }

    /**
     * 计算“最大半径圆弧”在未绑定端点处的切向角。
     * <p>
     * 几何：固定端点 fixed 的切向为 fixedAngle，求过 free 点且在该处与方向相切的圆（唯一解），
     * 返回该圆在 free 点处的切向角。这是满足平滑连接的“最大半径”圆弧。
     * <p>
     * 直接委托 NTE 移植的 {@link RailCalculator#calculateMaxRadiusAngle}，避免另写一份算法。
     *
     * @param fixed      已绑定端点位置
     * @param fixedAngle 已绑定端点方向（度）
     * @param free       未绑定端点位置
     * @return free 点处的切向角（度）；无解时返回 {@code null}
     */
    public static Double maxRadiusTangentAngle(BlockPos fixed, double fixedAngle, BlockPos free) {
        return RailCalculator.calculateMaxRadiusAngle(
                fixed.getX(), fixed.getZ(), free.getX(), free.getZ(), Math.toRadians(fixedAngle));
    }

    /**
     * 把“度”转成 {@link RailAngle}。
     * <p>
     * 落在 22.5° 栅格上的角度仍取真实枚举常量（保持 MTR 内部基于引用的判断语义），
     * 其余角度用 {@link RailAngleExtra} 伪造任意角度实例。
     */
    public static RailAngle angleFor(double degrees) {
        final double normalized = RailAngleExtra.normalizeDegrees(degrees);
        final double grid = normalized / ANGLE_GRID;
        if (Math.abs(grid - Math.rint(grid)) < 1.0E-6D) {
            return RailAngle.fromAngle((float) normalized);
        }
        return RailAngleExtra.fromDegrees(normalized);
    }

    /**
     * 按 MTR 原版语义，把节点存储方向对齐到连线方向：
     * 起点方向若与连线“同向”则保持不变，否则取反；终点方向若与连线“同向”则取反（终点朝向来路）。
     */
    public static double[] alignToConnection(BlockPos posStart, double startDegrees, BlockPos posEnd, double endDegrees) {
        final double angleDifference = straightAngle(posStart, posEnd);
        final double start = normalizeDegrees(startDegrees
                + (RailAngle.similarFacing((float) angleDifference, (float) startDegrees) ? 0 : 180));
        final double end = normalizeDegrees(endDegrees
                + (RailAngle.similarFacing((float) angleDifference, (float) endDegrees) ? 180 : 0));
        return new double[]{start, end};
    }

    // ==================== 节点状态读写 ====================

    /**
     * 该位置是否为我们注册的万向节点。
     */
    public static boolean isMultiDirectionNode(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof com.fangsu.blocks.BlockMultiDirectionNode;
    }

    /**
     * 读取节点方向的度数。万向节点取 BE 存储值；普通 MTR 节点取 blockstate 角度。
     */
    public static double getDirectionDegrees(Level level, BlockPos pos) {
        final BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BlockEntityMultiDirectionNode node) {
            return node.getDirectionDegrees();
        }
        return BlockNode.getAngle(level.getBlockState(pos));
    }

    /**
     * 读取节点方向。万向节点取 BE 的任意角度；普通 MTR 节点取 blockstate 角度（22.5° 栅格枚举）。
     */
    public static RailAngle railAngleAt(Level level, BlockPos pos) {
        final BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BlockEntityMultiDirectionNode node) {
            return node.getRailAngle();
        }
        if (level.getBlockState(pos).getBlock() instanceof BlockNode) {
            return RailAngle.fromAngle(BlockNode.getAngle(level.getBlockState(pos)));
        }
        return null;
    }

    /**
     * 方向是否已绑定。
     * <p>
     * 万向节点读 BE NBT；普通节点恒 true —— blockstate 角度即其绑定方向（与原版节点语义一致），
     * 连接时视为已绑定、保留角度。
     */
    public static boolean isBonded(Level level, BlockPos pos) {
        final BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BlockEntityMultiDirectionNode node) {
            return node.isDirectionBonded();
        }
        return true;
    }

    /**
     * 读取连接状态。万向节点读 blockstate IS_CONNECTED（由 MTR 连接器 / 轨道删除逻辑维护）；
     * 普通节点同样读 blockstate。
     */
    public static boolean isConnectedAt(Level level, BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockNode && state.hasProperty(BlockNode.IS_CONNECTED)) {
            return state.getValue(BlockNode.IS_CONNECTED);
        }
        return false;
    }

    /**
     * 把万向节点绑定到指定角度（普通节点忽略）。
     */
    public static void bindNode(Level level, BlockPos pos, double degrees) {
        final BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BlockEntityMultiDirectionNode node) {
            node.bind(degrees);
        }
    }

    /**
     * 标记节点已连接：blockstate IS_CONNECTED = true，并同步万向节点 BE 的缓存状态。
     * <p>
     * 仅改 blockstate 而不重建方块（{@code setBlockAndUpdate} 对同一 EntityBlock 会保留方块实体）。
     */
    public static void markConnected(Level level, BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockNode && state.hasProperty(BlockNode.IS_CONNECTED)
                && !state.getValue(BlockNode.IS_CONNECTED)) {
            level.setBlockAndUpdate(pos, state.setValue(BlockNode.IS_CONNECTED, true));
        }
        syncConnectedCache(level, pos);
    }

    /**
     * 把 blockstate 的连接状态同步进万向节点 BE 的缓存 / NBT（普通节点为空操作）。
     */
    public static void syncConnectedCache(Level level, BlockPos pos) {
        final BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof BlockEntityMultiDirectionNode node) {
            node.syncConnectedCache();
        }
    }

    // ==================== 客户端 ====================

    /**
     * 客户端：查找连接到某节点位置的其他端点（读取 {@code ClientData.RAILS}）。
     * <p>
     * MTR 客户端数据由网络线程写入，这里加锁读取并对并发修改做兜底（照 ItemDisplacementTool）。
     */
    public static List<BlockPos> findConnectedEndpoints(BlockPos nodePos) {
        final List<BlockPos> result = new ArrayList<>();
        final Map<BlockPos, Rail> connections;
        synchronized (mtr.client.ClientData.RAILS) {
            connections = mtr.client.ClientData.RAILS.get(nodePos);
        }
        if (connections == null) {
            return result;
        }
        try {
            result.addAll(connections.keySet());
        } catch (ConcurrentModificationException ignored) {
            // 轨道数据正在更新，放弃本次查询（不崩）
        }
        return result;
    }

    /**
     * 客户端：查询连接到本节点的第一条轨道（供角度界面显示轨道编辑信息用）。
     */
    public static Rail findFirstConnectedRail(BlockPos nodePos) {
        synchronized (mtr.client.ClientData.RAILS) {
            final Map<BlockPos, Rail> connections = mtr.client.ClientData.RAILS.get(nodePos);
            if (connections == null || connections.isEmpty()) {
                return null;
            }
            try {
                return connections.values().iterator().next();
            } catch (ConcurrentModificationException ignored) {
                return null;
            }
        }
    }

    // ==================== 服务端：建轨 / 重建 ====================

    /**
     * 服务端：以两个端点位置 + 两个角度，按连接器类型构建一条铁轨并派发到服务端数据。
     * <p>
     * 角度用 {@link RailAngleExtra} 生成精确值，避免 22.5° 快照，使万向节点建出的轨道摆脱
     * 原版 16×22.5° 的离散限制。合法性判定不使用 {@code Rail.isValid()}——它内部用
     * {@code facingStart == getRailAngle(false)} 做枚举引用相等比较，对任意角度必然为假；
     * 这里改为几何退化判定（长度非零且有限）。
     *
     * @return 轨道是否创建成功（false = 几何不成立，如两端绑定方向与连线冲突）
     */
    public static boolean createAndSendRail(
            Level level,
            Player player,
            TransportMode transportMode,
            BlockPos posStart, double startDegrees,
            BlockPos posEnd, double endDegrees,
            RailType railType, boolean isOneWay
    ) {
        if (!(level instanceof ServerLevel)) {
            return false;
        }
        final RailwayData railwayData = RailwayData.getInstance(level);
        if (railwayData == null) {
            return false;
        }

        final double[] aligned = alignToConnection(posStart, startDegrees, posEnd, endDegrees);
        final RailAngle facingStart = angleFor(aligned[0]);
        final RailAngle facingEnd = angleFor(aligned[1]);

        final Rail rail1 = new Rail(posStart, facingStart, posEnd, facingEnd,
                isOneWay ? RailType.NONE : railType, transportMode);
        final Rail rail2 = new Rail(posEnd, facingEnd, posStart, facingStart, railType, transportMode);

        if (!isGeometryUsable(rail1) || !isGeometryUsable(rail2)) {
            Main.debug("[NodeConnector] 轨道几何不成立 {}->{} 角度 {} / {}", posStart, posEnd, aligned[0], aligned[1]);
            return false;
        }

        railwayData.addRail(player, transportMode, posStart, posEnd, rail1, false);
        final long newId = railwayData.addRail(player, transportMode, posEnd, posStart, rail2, true);
        markConnected(level, posStart);
        markConnected(level, posEnd);
        PacketTrainDataGuiServer.createRailS2C(level, transportMode, posStart, posEnd, rail1, rail2, newId);
        Main.debug("[NodeConnector] 已建轨 {}->{} 角度 {} / {}", posStart, posEnd, aligned[0], aligned[1]);
        return true;
    }

    /**
     * 服务端：按节点当前绑定角度，重建所有连接到该节点的轨道。
     * <p>
     * 用在两个场景：
     * <ul>
     *   <li>世界重载 —— MTR 反序列化轨道时 {@code facingStart/facingEnd} 会经
     *       {@code RailAngle.fromAngle} 量化到 22.5°，重建可恢复精确角度</li>
     *   <li>玩家在角度界面改绑方向 —— 已连接轨道的曲线随之刷新</li>
     * </ul>
     * 另一端角度语义：万向节点取绑定角度，普通节点取 blockstate 角度（均视为固定方向）。
     * 注意 MTR3 的 {@code validateData()} 为私有且只清理无效站台/侧线，此处重建只替换同一连接，
     * 无需触发（照 MTR4 版的实现也不触发）。
     */
    public static void rebuildRailsAtNode(Level level, BlockPos nodePos) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        final BlockEntity entity = level.getBlockEntity(nodePos);
        if (!(entity instanceof BlockEntityMultiDirectionNode node)) {
            return;
        }
        if (!node.isDirectionBonded() || !node.isConnected()) {
            return;
        }
        if (node.getRailAngle() == null) {
            return;
        }

        final RailwayData railwayData = RailwayData.getInstance(level);
        if (railwayData == null) {
            return;
        }
        final Map<BlockPos, Map<BlockPos, Rail>> rails = getRails(railwayData);
        if (rails == null) {
            return;
        }
        final Map<BlockPos, Rail> connections = rails.get(nodePos);
        if (connections == null || connections.isEmpty()) {
            return;
        }

        final List<BlockPos> targets = new ArrayList<>(connections.keySet());
        int rebuilt = 0;
        for (final BlockPos target : targets) {
            final Rail forward = connections.get(target);
            final Map<BlockPos, Rail> backConnections = rails.get(target);
            final Rail backward = backConnections == null ? null : backConnections.get(nodePos);
            if (forward == null || backward == null) {
                continue;
            }
            final RailAngle angleTarget = railAngleAt(level, target);
            if (angleTarget == null) {
                continue;
            }
            // 与建轨时同一套朝向语义：节点存储的是“离开节点的方向”，Rail 需要对齐后的角度
            final double[] aligned = alignToConnection(nodePos, node.getDirectionDegrees(), target, getDirectionDegrees(level, target));
            final RailAngle facingStart = angleFor(aligned[0]);
            final RailAngle facingEnd = angleFor(aligned[1]);

            final Rail newForward = new Rail(nodePos, facingStart, target, facingEnd, forward.railType, forward.transportMode);
            final Rail newBackward = new Rail(target, facingEnd, nodePos, facingStart, backward.railType, backward.transportMode);
            if (!isGeometryUsable(newForward) || !isGeometryUsable(newBackward)) {
                Main.LOGGER.warn("[NodeConnector] 重建轨道几何不成立，跳过 {}->{}", nodePos, target);
                continue;
            }

            railwayData.addRail(null, forward.transportMode, nodePos, target, newForward, false);
            railwayData.addRail(null, backward.transportMode, target, nodePos, newBackward, false);
            PacketTrainDataGuiServer.createRailS2C(level, forward.transportMode, nodePos, target, newForward, newBackward, 0);
            rebuilt++;
        }
        if (rebuilt > 0) {
            Main.debug("[NodeConnector] 节点 {} 重建了 {} 条轨道", nodePos, rebuilt);
        }
    }

    /**
     * 几何预检：给定两端<b>原始</b>节点角度（未对齐），判断按 MTR 朝向语义对齐后能否建出有效轨道。
     * <p>
     * 无副作用（只构造临时 {@code Rail} 做长度判定），供连接器在“决定绑定哪个角度”之前调用，
     * 从而做到“建轨成功才绑定”，失败不改变节点绑定状态。
     */
    public static boolean isGeometryValid(BlockPos posStart, double startDegrees, BlockPos posEnd, double endDegrees) {
        final double[] aligned = alignToConnection(posStart, startDegrees, posEnd, endDegrees);
        final RailAngle facingStart = angleFor(aligned[0]);
        final RailAngle facingEnd = angleFor(aligned[1]);
        final Rail forward = new Rail(posStart, facingStart, posEnd, facingEnd, RailType.IRON, TransportMode.TRAIN);
        final Rail backward = new Rail(posEnd, facingEnd, posStart, facingStart, RailType.IRON, TransportMode.TRAIN);
        return isGeometryUsable(forward) && isGeometryUsable(backward);
    }

    /**
     * 几何是否可用：长度非零且有限（{@code Rail} 构造无解时退化为全 0 参数，长度为 0；
     * 出现 NaN 时长度同样非有限）。
     */
    public static boolean isGeometryUsable(Rail rail) {
        if (rail == null) {
            return false;
        }
        final double length = rail.getLength();
        return Double.isFinite(length) && length > LENGTH_EPSILON;
    }

    /**
     * 读取 MTR3 服务端的轨道表：优先走 mixin 接口（Fabric 端 mixin 生效）；
     * Forge 端不加载该 mixin，回退反射 {@link RailwayData} 的 private {@code rails} 字段
     * （MTR 为 official mappings，字段名即源码名）。照 ItemHybridCreator 的既有做法。
     */
    @SuppressWarnings("unchecked")
    private static Map<BlockPos, Map<BlockPos, Rail>> getRails(RailwayData railwayData) {
        if (railwayData.railwayDataRailActionsModule instanceof RailActionsModuleExtraSupplier supplier) {
            return supplier.getRails();
        }
        try {
            final Field field = RailwayData.class.getDeclaredField("rails");
            field.setAccessible(true);
            return (Map<BlockPos, Map<BlockPos, Rail>>) field.get(railwayData);
        } catch (ReflectiveOperationException e) {
            Main.LOGGER.error("[NodeConnector] 读取轨道表失败", e);
            return null;
        }
    }
}
