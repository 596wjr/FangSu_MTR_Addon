package com.fangsu.items;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.network.DisplacementToolPackets;
import com.fangsu.utils.RegisterUtil;
import mtr.data.Rail;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 轨道位移工具（自 NTE 位移工具移植）。
 * <p>
 * 右键轨道节点（MTR 原版 {@code BlockNode}）：选中「终点方向与玩家视线夹角最小」的
 * 轨道，将玩家沿轨道传送到其终点，保持玩家相对轨道起点的方向与位置不变，朝向随轨道
 * 累计转角同步旋转。不修改任何轨道数据。
 * <p>
 * 与原版 NTE 实现的差异（崩服风险修复）：
 * <ul>
 *   <li>全部轨道数据读取与位移计算在客户端完成，服务端只做纯 vanilla 校验与传送，
 *       不再于服务端直读 {@code RailwayData} 私有 rails map（原版通过 mixin 绕过锁）</li>
 *   <li>客户端读取 {@code ClientData.RAILS} 时加锁并捕获并发修改异常，不裸读</li>
 *   <li>计算后校验坐标有限性并限制世界边界，拦截 NaN/Infinity 传送</li>
 *   <li>服务端对包内容独立校验，恶意/损坏包静默丢弃</li>
 * </ul>
 */
public class ItemDisplacementTool extends Item {

    public ItemDisplacementTool() {
        super(RegisterUtil.tabProps(new Item.Properties().stacksTo(1)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        RegisterUtil.addDescTooltip(tooltip, "item.fangsu.displacement_tool.desc");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // 服务端零逻辑：所有计算在客户端完成，只发 C2S 传送请求
        if (!context.getLevel().isClientSide()) return InteractionResult.PASS;

        final Player player = context.getPlayer();
        if (player == null) return InteractionResult.SUCCESS;

        final BlockPos pos = context.getClickedPos();
        final BlockState state = context.getLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof mtr.block.BlockNode)) return InteractionResult.PASS;
        // 异常的玩家朝向直接放弃（选择比较器基于 yaw）
        if (!Float.isFinite(player.getYRot())) return InteractionResult.SUCCESS;

        // 加锁读取单 key 引用（MTR 客户端数据由网络线程写入；内层 map 发布后不再被修改）
        final Map<BlockPos, Rail> railsAtPos;
        synchronized (mtr.client.ClientData.RAILS) {
            railsAtPos = mtr.client.ClientData.RAILS.get(pos);
        }
        if (railsAtPos == null) return InteractionResult.SUCCESS;

        final Optional<Map.Entry<BlockPos, Rail>> closestEntry;
        try {
            closestEntry = railsAtPos.entrySet().stream().min(Comparator.comparingDouble(entry ->
                    Mth.degreesDifferenceAbs(
                            (float) -Math.toDegrees(Math.atan2(entry.getKey().getX() - pos.getX(),
                                    entry.getKey().getZ() - pos.getZ())), player.getYRot())));
        } catch (java.util.ConcurrentModificationException ignored) {
            // 轨道数据更新中，放弃本次操作（不崩）
            return InteractionResult.SUCCESS;
        }
        if (closestEntry.isEmpty()) return InteractionResult.SUCCESS;
        final Rail rail = closestEntry.get().getValue();

        // 位移计算：与原版逐行等价，仅把私有 getRailAngle 换成公开 final 字段
        // facingStart/facingEnd（构造时即 getRailAngle 的缓存值）
        final Vec3 playerPos = player.getPosition(1);
        final Vec3 start = rail.getPosition(0);
        final Vec3 diff = playerPos.subtract(start);
        final double rot = rail.facingEnd.getOpposite().angleRadians - rail.facingStart.angleRadians;
        final Vec3 rotated = diff.yRot((float) -rot);
        final Vec3 t = rotated.add(rail.getPosition(rail.getLength()));
        final double yaw = player.getYRot() + Math.toDegrees(rot);
        final double pitch = player.getXRot();

        // 客户端数值校验：拦截 NaN/Infinity 与越界坐标，防止服务端实体坐标损坏
        if (!Double.isFinite(t.x) || !Double.isFinite(t.y) || !Double.isFinite(t.z)
                || !Double.isFinite(yaw) || !Double.isFinite(pitch)
                || Math.abs(t.x) > 30000000 || Math.abs(t.z) > 30000000 || Math.abs(t.y) > 4096
                || Math.abs(yaw) > 3600f || Math.abs(pitch) > 3600f) {
            player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.displacement_tool.invalid"), true);
            return InteractionResult.SUCCESS;
        }

        DisplacementToolPackets.sendTeleportC2S(pos, t.x, t.y, t.z, (float) yaw, (float) pitch);
        return InteractionResult.SUCCESS;
    }
}
