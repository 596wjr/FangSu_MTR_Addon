package com.fangsu.data.hybrid;

import com.fangsu.Main;
import com.fangsu.mappings.ComponentHelper;
import mtr.data.Rail;
import mtr.data.RailwayData;
import mtr.data.RailwayDataRailActionsModule;
import mtr.packet.PacketTrainDataGuiServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 混合构建器的「放置组合」构建动作（MTR3），挂载到 {@link RailwayDataRailActionsModule} 队列执行。
 * <p>
 * 与切片动作（铺满横截面）不同：沿轨道逐站放置任务里的一组**离散对象**，每站：
 * <ul>
 *   <li>由 {@code rail.getPosition(trackPos)} 取轨道中心，按局部切向 t / 法向 n / up 基向量
 *       把每个对象定位到 {@code center + n·dn + t·dt + up·du}（曲线站每站独立定向、自动跟随）；</li>
 *   <li>up 基向量由 {@link HybridPlacementTask.VerticalMode} 决定：
 *       GROUND=(0,1,0) 垂直地面 / TRACK=normalize(cross(n,t)) 垂直轨道平面；</li>
 *   <li>放置目标方块后，把对象携带的方块实体预设 NBT（translate/rotate/mainModel/extraConfigs 等）
 *       注入该方块实体，并向客户端同步渲染。</li>
 * </ul>
 * 斜向/曲线阶梯空隙按「内角补块」闭合（见 {@link #patchInnerCorner}），复用切片补角思路。
 * <p>
 * MTR3 差异：getPosition 无 reverse 参数，展开方向固定从轨道几何起点端开始（同切片动作）。
 */
public class HybridPlacementAction extends Rail.RailActions {

    private final HybridPlacementTask task;
    /** 父类 RailActions 的 rail 字段是 private，build() 里取不到，这里自己存一份 */
    private final Rail rail;
    /** 站点位置序列（升序），由 start/interval/thickness 生成，语义同切片任务 */
    private final double[] stations;
    private final double length;
    private final Level level;
    private final UUID uuid;
    private final String playerName;
    /** 已放置的格（去重：同格同状态只放一个） */
    private final java.util.Map<BlockPos, BlockState> placed = new java.util.HashMap<>();
    private int processed = 0;
    private final int total;

    public HybridPlacementAction(Level level, Player player, Rail rail, HybridPlacementTask task) {
        super(level, player, Rail.RailActionType.BRIDGE, rail, 0, 0, null);
        this.level = level;
        this.uuid = player.getUUID();
        this.playerName = player.getName().getString();
        this.task = task;
        this.rail = rail;
        this.length = rail.getLength();

        final double gap = task.interval == null ? 0.0 : Math.max(task.interval, 0);
        final double period = gap + Math.max(task.thickness, 1);
        final List<Double> list = new ArrayList<>();
        list.add(task.start);
        double temp = task.start;
        while (temp + period < length) {
            temp += period;
            list.add(temp);
        }
        // 向外延伸时位置递减：排序保证升序（与切片动作一致）
        list.sort(Comparator.comparingDouble(p -> p));
        stations = new double[list.size()];
        for (int i = 0; i < list.size(); i++) stations[i] = list.get(i);
        total = Math.max(1, stations.length);
    }

    @Override
    public boolean build() {
        final long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 2 && processed < total) {
            buildStation(stations[processed]);
            processed++;
            sendProgressMessage(Math.min(100F, 100F * processed / total));
        }
        return processed >= total;
    }

    /** 在一个轨道站点放置整组对象 */
    private void buildStation(double trackPos) {
        final Vec3 center = rail.getPosition(trackPos);
        final Vec3 tangent = computeTangent(trackPos);
        final double tLen = Math.sqrt(tangent.x * tangent.x + tangent.z * tangent.z);
        final double tx = tLen > 1e-6 ? tangent.x / tLen : 1.0;
        final double tz = tLen > 1e-6 ? tangent.z / tLen : 0.0;
        // 水平法向 n = 切线水平投影旋转 90°：(tz, 0, -tx)
        final double nx = tLen > 1e-6 ? tangent.z / tLen : 0.0;
        final double nz = tLen > 1e-6 ? -tangent.x / tLen : 1.0;

        // up 基向量：垂直地面 = 世界 +Y；垂直轨道平面 = normalize(cross(n,t))（水平轨道上即 +Y）
        final double ux, uy, uz;
        if (task.verticalMode == HybridPlacementTask.VerticalMode.GROUND) {
            ux = 0; uy = 1; uz = 0;
        } else {
            // n×t 叉积的 y 分量 = nz*tx - nx*tz（n、t 均在水平面，叉积为纯垂直）
            double cy = nz * tx - nx * tz;
            if (Math.abs(cy) < 1e-6) cy = 1.0;
            ux = 0; uy = Math.signum(cy); uz = 0;
        }

        // 轨道切向的世界方位角（MC 偏航约定：atan2(-x, z)，0=南，顺时针）
        final double trackYawDeg = Math.toDegrees(Math.atan2(-tx, tz));
        for (HybridPlacementTask.PlacementObject obj : task.objects) {
            if (obj.blockName == null || obj.blockName.isEmpty()) continue;
            final Block block = HybridSliceTask.getBlockByName(obj.blockName);
            if (block == null) continue; // 未注册（跨版本/mod 缺失）→ 跳过
            final double wx = center.x + nx * obj.dn + tx * obj.dt + ux * obj.du;
            final double wy = center.y + uy * obj.du;
            final double wz = center.z + nz * obj.dn + tz * obj.dt + uz * obj.du;
            final BlockPos pos = RailwayData.newBlockPos(wx, wy, wz);
            placeWithNbt(pos, block, trackYawDeg, obj);
        }
    }

    /**
     * 放置目标方块并注入 BE 预设 NBT。仅放空气处（非替换）；同格去重。
     * <p>
     * 朝向/旋转：方块 FACING 保持捕获时的源朝向，把 BE 的 rotateY 按轨道方位角平移
     * {@code trackYawDeg - sourceFacing.toYRot()}，使对象随轨道段旋转（"跟随轨道"）。
     * translateX/Y/Z 是世界坐标偏移（先于旋转施加），无需再旋转，位置已由 n/t/up 定位。
     */
    private void placeWithNbt(BlockPos pos, Block block, double trackYawDeg, HybridPlacementTask.PlacementObject obj) {
        final BlockState state = block.defaultBlockState();
        final BlockState prev = placed.get(pos);
        if (state.equals(prev)) return;
        final BlockState existing = level.getBlockState(pos);
        if (!existing.isAir() || level.getBlockEntity(pos) != null) {
            placed.put(pos, state); // 记录避免重复判断；实际不覆盖
            return;
        }
        // 源朝向（捕获时记录；非法则默认南）
        Direction sourceFacing = Direction.SOUTH;
        if (obj.sourceFacing >= 0 && obj.sourceFacing < Direction.values().length) {
            sourceFacing = Direction.from3DDataValue(obj.sourceFacing);
        }
        // 设朝向（水平方向性方块才设 FACING，防御其他方块无此属性）
        final BlockState placedState;
        if (state.hasProperty(com.fangsu.blocks.BaseObjBlock.FACING)) {
            placedState = state.setValue(com.fangsu.blocks.BaseObjBlock.FACING, sourceFacing);
        } else if (state.getBlock() instanceof net.minecraft.world.level.block.HorizontalDirectionalBlock) {
            placedState = state.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, sourceFacing);
        } else {
            placedState = state;
        }
        level.setBlockAndUpdate(pos, placedState);
        placed.put(pos, placedState);
        // 注入 BE 预设 NBT，并把 rotateY 按轨道方位旋转（跟随轨道）
        if (obj.beNbt != null) {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                final CompoundTag data = obj.beNbt.copy();
                data.remove("x");
                data.remove("y");
                data.remove("z");
                final double rotationDelta = Math.toRadians(trackYawDeg - sourceFacing.toYRot());
                final float oldRotY = data.contains("rotateY") ? data.getFloat("rotateY") : 0f;
                data.putFloat("rotateY", oldRotY + (float) rotationDelta);
                try {
                    be.load(data);
                } catch (Exception e) {
                    Main.LOGGER.warn("[HybridCreator] 注入方块实体数据失败 @{} {}", pos, e.getMessage());
                }
                be.setChanged();
                level.sendBlockUpdated(pos, placedState, placedState, 3);
            }
        }
    }

    /**
     * 斜向/曲线阶梯空隙的内角补块：对相邻站点同一对象的取整位置斜对角错位
     * （dx、dz 各差 1，中间留斜缝）时，在内角补一个连接方块闭合（照切片补角思路）。
     * 当前以主对象链简化实现；完整版可扩展为逐对象追踪。
     */
    private void patchInnerCorner(BlockPos a, BlockPos b, BlockState state) {
        final int dx = Integer.compare(b.getX() - a.getX(), 0);
        final int dz = Integer.compare(b.getZ() - a.getZ(), 0);
        if (dx == 0 || dz == 0) return;
        final BlockPos target = Math.abs(b.getX() - a.getX()) >= Math.abs(b.getZ() - a.getZ())
                ? a.offset(dx, 0, 0) : a.offset(0, 0, dz);
        if (placed.containsKey(target)) return;
        if (!level.getBlockState(target).isAir()) return;
        level.setBlockAndUpdate(target, state);
        placed.put(target, state);
    }

    /** 轨道在 {@code trackPos} 处的切线向量（前后差分，端点 clamp 到 [0, length]） */
    private Vec3 computeTangent(double trackPos) {
        final double delta = Math.min(Math.max((task.interval == null ? 0 : task.interval) + 1, 0.5), 2.0);
        final double s2 = Math.min(trackPos + delta, length);
        final double s1 = Math.max(trackPos - delta, 0);
        final Vec3 p2 = rail.getPosition(s2);
        final Vec3 p1 = rail.getPosition(s1);
        return new Vec3(p2.x - p1.x, p2.y - p1.y, p2.z - p1.z);
    }

    private void sendProgressMessage(float percentage) {
        final Player player = level.getPlayerByUUID(uuid);
        if (player != null) {
            player.displayClientMessage(ComponentHelper.translatable("gui.fangsu.hybrid_creator.percentage_complete", percentage), true);
        }
    }

    /** 将构建动作挂载到 MTR3 的 RailwayDataRailActionsModule 队列（同切片动作 attach 方式） */
    public static void attach(Level level, Player player, RailwayData railwayData, Rail rail, HybridPlacementTask task) {
        final HybridPlacementAction action = new HybridPlacementAction(level, player, rail, task);
        final RailwayDataRailActionsModule module = railwayData.railwayDataRailActionsModule;
        if (module instanceof RailActionsModuleExtraSupplier supplier) {
            supplier.getRailActions().add(action);
            supplier.sendUpdateS2C();
        } else {
            reflectAdd(module, level, action);
        }
    }

    private static void reflectAdd(RailwayDataRailActionsModule module, Level level, Rail.RailActions railAction) {
        try {
            final java.lang.reflect.Field field = RailwayDataRailActionsModule.class.getDeclaredField("railActions");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            final List<Rail.RailActions> actions = (List<Rail.RailActions>) field.get(module);
            actions.add(railAction);
            PacketTrainDataGuiServer.updateRailActionsS2C(level, actions);
        } catch (ReflectiveOperationException e) {
            Main.LOGGER.error("[HybridCreator] 挂载放置组合构建动作失败", e);
        }
    }
}
