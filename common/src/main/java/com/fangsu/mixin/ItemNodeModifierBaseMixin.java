package com.fangsu.mixin;

import com.fangsu.Main;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.util.NodeConnector;
import mtr.block.BlockNode;
import mtr.data.RailAngle;
import mtr.data.RailwayData;
import mtr.data.TransportMode;
import mtr.item.ItemNodeModifierBase;
import mtr.item.ItemRailModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 让 MTR3 的轨道连接器 / 轨道删除器识别“万向节点”。
 * <p>
 * 自 NTE mtrsteamloco {@code mixin/ItemNodeModifierBaseMixin.java} 移植
 * （MIT License, Copyright (c) 2022-present Zbx1425），角度求解与降级策略照 MTR4 版
 * {@code com.fangsu.mixin.ItemNodeModifierBaseMixin} 的行为对齐。
 * <p>
 * 为什么需要接管：MTR3 原版 {@code onEndClick} 用 {@link BlockNode#getAngle(BlockState)}
 * 从 blockstate 取角度（只有 22.5° 栅格），完全读不到万向节点 BE 里的任意角度。
 * 这里在 HEAD 处接管，只在“两端都是轨道节点、运输方式一致、且至少一端是万向节点”时生效，
 * 其余情况一律交还原版逻辑。
 * <p>
 * 角度策略（与 MTR4 版一致）：
 * <ul>
 *   <li>两端都未绑定 → 直线</li>
 *   <li>仅一端未绑定 → 未绑定端取最大半径圆弧切向角（平滑衔接），几何不成立再退化为直线</li>
 *   <li>两端都已绑定 → 用两端角度；几何不成立且有一端是普通节点（无绑定意图）时，
 *       降级为该端取最大半径圆弧切向，再退化为直线；两端均为万向节点时不降级
 *       （绑定角度是用户明确意图，冲突时提示“无效方向”）</li>
 * </ul>
 * 建轨成功后（几何预检通过）才把未绑定端绑定为实际使用的角度。
 */
@Mixin(value = ItemNodeModifierBase.class, remap = false)
public abstract class ItemNodeModifierBaseMixin {

    /**
     * 原版 {@code ItemNodeModifierBase} 的起始点击标记键（源码中为 private，此处按字面量复刻）。
     */
    private static final String TAG_TRANSPORT_MODE = "transport_mode";

    /** 是否为连接器（false 时为轨道删除器，如 {@code ItemRailModifier()} 的无参实例）。 */
    @Shadow(remap = false)
    protected boolean isConnector;

    /** 原版建轨入口，由 {@link ItemRailModifierMixin} 接管实现。 */
    @Shadow(remap = false)
    protected abstract void onConnect(Level world, ItemStack stack, TransportMode transportMode, BlockState stateStart, BlockState stateEnd, BlockPos posStart, BlockPos posEnd, RailAngle facingStart, RailAngle facingEnd, Player player, RailwayData railwayData);

    /** 原版删轨入口（轨道删除器路径复用原版逻辑）。 */
    @Shadow(remap = false)
    protected abstract void onRemove(Level world, BlockPos posStart, BlockPos posEnd, Player player, RailwayData railwayData);

    @Inject(method = "onEndClick", at = @At("HEAD"), cancellable = true)
    private void fangsu$onEndClick(UseOnContext context, BlockPos posEnd, CompoundTag compoundTag, CallbackInfo info) {
        final Level world = context.getLevel();
        // 原版 onEndClick 只在服务端被 ItemBlockClickingBase 调用，这里再兜一层防御
        if (world == null || world.isClientSide) {
            return;
        }
        final RailwayData railwayData = RailwayData.getInstance(world);
        final BlockPos posStart = context.getClickedPos();
        final BlockState stateStart = world.getBlockState(posStart);
        final BlockState stateEnd = world.getBlockState(posEnd);

        if (railwayData == null || !(stateStart.getBlock() instanceof BlockNode) || !(stateEnd.getBlock() instanceof BlockNode)) {
            return;
        }
        final TransportMode transportMode = ((BlockNode) stateStart.getBlock()).transportMode;
        if (!transportMode.toString().equals(compoundTag.getString(TAG_TRANSPORT_MODE))) {
            return;
        }
        // 与万向节点无关的连接完全交还原版（含线缆车厢节点等特殊分支）
        if (!NodeConnector.isMultiDirectionNode(world, posStart) && !NodeConnector.isMultiDirectionNode(world, posEnd)) {
            return;
        }
        // 只接管轨道连接器 / 轨道删除器（ItemRailModifier，含无参构造的删除器实例）；
        // 信号连接器 / 桥梁隧道创建器等不建轨、不消费角度，一律交还原版逻辑
        if (!(context.getItemInHand().getItem() instanceof ItemRailModifier)) {
            return;
        }

        final Player player = context.getPlayer();

        if (!isConnector) {
            // 轨道删除器：复用原版删轨逻辑
            onRemove(world, posStart, posEnd, player, railwayData);
            compoundTag.remove(TAG_TRANSPORT_MODE);
            info.cancel();
            return;
        }

        if (posStart.equals(posEnd)) {
            compoundTag.remove(TAG_TRANSPORT_MODE);
            info.cancel();
            return;
        }

        final boolean startIsNode = NodeConnector.isMultiDirectionNode(world, posStart);
        final boolean endIsNode = NodeConnector.isMultiDirectionNode(world, posEnd);
        final boolean startBonded = NodeConnector.isBonded(world, posStart);
        final boolean endBonded = NodeConnector.isBonded(world, posEnd);
        final double startAngle = NodeConnector.getDirectionDegrees(world, posStart);
        final double endAngle = NodeConnector.getDirectionDegrees(world, posEnd);
        Main.debug("[MultiDirectionNode] 连接 {} (绑定={}) -> {} (绑定={})", posStart, startBonded, posEnd, endBonded);

        final List<double[]> candidates = new ArrayList<>();
        final double straight = NodeConnector.straightAngle(posStart, posEnd);
        if (!startBonded && !endBonded) {
            // 两端均未绑定 → 直线
            candidates.add(new double[]{straight, straight});
        } else if (!startBonded) {
            // 起点未绑定，终点固定 → 起点取最大半径圆弧切向（平滑衔接），失败退化为直线
            addCandidate(candidates, NodeConnector.maxRadiusTangentAngle(posEnd, endAngle, posStart), endAngle);
            candidates.add(new double[]{straight, straight});
        } else if (!endBonded) {
            // 终点未绑定，起点固定 → 终点取最大半径圆弧切向（平滑衔接），失败退化为直线
            addCandidate(candidates, startAngle, NodeConnector.maxRadiusTangentAngle(posStart, startAngle, posEnd));
            candidates.add(new double[]{straight, straight});
        } else {
            // 两端均已绑定 → 使用既有角度
            candidates.add(new double[]{startAngle, endAngle});
            if (!startIsNode) {
                addCandidate(candidates, NodeConnector.maxRadiusTangentAngle(posEnd, endAngle, posStart), endAngle);
            } else if (!endIsNode) {
                addCandidate(candidates, startAngle, NodeConnector.maxRadiusTangentAngle(posStart, startAngle, posEnd));
            }
            if (!startIsNode || !endIsNode) {
                candidates.add(new double[]{straight, straight});
            }
        }

        for (final double[] candidate : candidates) {
            // 先做几何预检：不成立的组合直接跳过，绝不改变节点绑定状态
            if (!NodeConnector.isGeometryValid(posStart, candidate[0], posEnd, candidate[1])) {
                continue;
            }
            // 建轨成功在即，才把未绑定端绑定为实际使用的角度
            if (startIsNode && !startBonded) {
                NodeConnector.bindNode(world, posStart, candidate[0]);
            }
            if (endIsNode && !endBonded) {
                NodeConnector.bindNode(world, posEnd, candidate[1]);
            }
            final double[] aligned = NodeConnector.alignToConnection(posStart, candidate[0], posEnd, candidate[1]);
            onConnect(world, context.getItemInHand(), transportMode, stateStart, stateEnd, posStart, posEnd,
                    NodeConnector.angleFor(aligned[0]), NodeConnector.angleFor(aligned[1]), player, railwayData);
            compoundTag.remove(TAG_TRANSPORT_MODE);
            info.cancel();
            return;
        }

        // 全部角度组合均不成立（如两端万向节点绑定方向与连线冲突）：与原版一致提示“无效方向”
        if (player != null) {
            player.displayClientMessage(ComponentHelper.translatable("gui.mtr.invalid_orientation"), true);
        }
        Main.debug("[MultiDirectionNode] 全部角度组合均无解 {}->{}", posStart, posEnd);
        compoundTag.remove(TAG_TRANSPORT_MODE);
        info.cancel();
    }

    /**
     * 最大半径切向角可能无解（{@code null}），此时不加入候选。
     */
    private static void addCandidate(List<double[]> candidates, Double angleA, double angleB) {
        if (angleA != null && Double.isFinite(angleA)) {
            candidates.add(new double[]{angleA, angleB});
        }
    }

    private static void addCandidate(List<double[]> candidates, double angleA, Double angleB) {
        if (angleB != null && Double.isFinite(angleB)) {
            candidates.add(new double[]{angleA, angleB});
        }
    }
}
