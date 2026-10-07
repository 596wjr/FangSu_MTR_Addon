package com.fangsu.mixin;

import com.fangsu.Main;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.util.NodeConnector;
import mtr.block.BlockNode;
import mtr.data.Rail;
import mtr.data.RailAngle;
import mtr.data.RailType;
import mtr.data.RailwayData;
import mtr.data.TransportMode;
import mtr.item.ItemRailModifier;
import mtr.packet.PacketTrainDataGuiServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 重新实现 MTR3 {@code ItemRailModifier.onConnect} 的建轨流程，使万向节点的任意角度能真正落库。
 * <p>
 * 自 NTE mtrsteamloco {@code mixin/ItemRailModifierMixin.java} 移植
 * （MIT License, Copyright (c) 2022-present Zbx1425），逻辑逐行对应 MTR3 原版
 * {@code ItemRailModifier.onConnect}，仅改一处关键判定：
 * <p>
 * 原版用 {@code rail1.isValid() && rail2.isValid()}，而 {@code Rail.isValid()} 内部是
 * {@code facingStart == getRailAngle(false)} 这样的枚举<b>引用相等</b>比较——
 * 万向节点用的是 {@code RailAngleExtra} 伪造的任意角度实例（ordinal = -1），
 * 与 {@code getRailAngle()} 返回的枚举常量永远不相等，任意角度轨道会被判为非法而无法创建。
 * 这里改为几何退化判定（长度非零且有限），等价保留原版“几何无解则不建轨”的意图
 * （与 ANTE 蓝本重写 {@code Rail.isValid()} 为容差比较是同一目的）。
 * <p>
 * 仅在连接涉及万向节点时接管；普通 MTR 节点之间的行为完全交还原版。
 */
@Mixin(value = ItemRailModifier.class, remap = false)
public abstract class ItemRailModifierMixin {

    /** 连接器类型（限速 / 单向 / 站台 / 侧线 / 折返等），原版为 private final。
     * 注意：只写 {@code @Final} 注解，不写 Java 关键字 final ——
     * 无构造器赋值的 final 实例字段 javac 会直接报错（照 MTR4 版 mixin 的同款经验）。 */
    @Shadow(remap = false)
    @Final
    private RailType railType;

    /** 是否单向轨道。 */
    @Shadow(remap = false)
    @Final
    private boolean isOneWay;

    @Inject(method = "onConnect", at = @At("HEAD"), cancellable = true)
    private void fangsu$onConnect(
            Level world, ItemStack stack, TransportMode transportMode,
            BlockState stateStart, BlockState stateEnd, BlockPos posStart, BlockPos posEnd,
            RailAngle facingStart, RailAngle facingEnd, Player player, RailwayData railwayData,
            CallbackInfo ci
    ) {
        if (!NodeConnector.isMultiDirectionNode(world, posStart) && !NodeConnector.isMultiDirectionNode(world, posEnd)) {
            return;
        }
        if (railType == null) {
            Main.LOGGER.warn("[MultiDirectionNode] 连接器 railType 为空，无法建轨 {}->{}", posStart, posEnd);
            ci.cancel();
            return;
        }

        if (railType.hasSavedRail && (railwayData.hasSavedRail(posStart) || railwayData.hasSavedRail(posEnd))) {
            if (player != null) {
                player.displayClientMessage(ComponentHelper.translatable("gui.mtr.platform_or_siding_exists"), true);
            }
            ci.cancel();
            return;
        }

        final boolean isValidContinuousMovement;
        final RailType newRailType;
        if (transportMode.continuousMovement) {
            final Block blockStart = stateStart.getBlock();
            final Block blockEnd = stateEnd.getBlock();

            if (blockStart instanceof BlockNode.BlockContinuousMovementNode && blockEnd instanceof BlockNode.BlockContinuousMovementNode) {
                if (((BlockNode.BlockContinuousMovementNode) blockStart).isStation && ((BlockNode.BlockContinuousMovementNode) blockEnd).isStation) {
                    isValidContinuousMovement = true;
                    newRailType = railType.hasSavedRail ? railType : RailType.CABLE_CAR_STATION;
                } else {
                    final int differenceX = posEnd.getX() - posStart.getX();
                    final int differenceZ = posEnd.getZ() - posStart.getZ();
                    isValidContinuousMovement = !railType.hasSavedRail && facingStart.isParallel(facingEnd)
                            && ((facingStart == RailAngle.N || facingStart == RailAngle.S) && differenceX == 0
                            || (facingStart == RailAngle.E || facingStart == RailAngle.W) && differenceZ == 0
                            || (facingStart == RailAngle.NE || facingStart == RailAngle.SW) && differenceX == -differenceZ
                            || (facingStart == RailAngle.SE || facingStart == RailAngle.NW) && differenceX == differenceZ);
                    newRailType = RailType.CABLE_CAR;
                }
            } else {
                isValidContinuousMovement = false;
                newRailType = railType;
            }
        } else {
            isValidContinuousMovement = true;
            newRailType = railType;
        }

        final Rail rail1 = new Rail(posStart, facingStart, posEnd, facingEnd, isOneWay ? RailType.NONE : newRailType, transportMode);
        final Rail rail2 = new Rail(posEnd, facingEnd, posStart, facingStart, newRailType, transportMode);

        final boolean goodRadius = rail1.goodRadius() && rail2.goodRadius();
        // 任意角度下 Rail.isValid() 恒为 false，改用几何退化判定（长度非零且有限）
        final boolean isValid = NodeConnector.isGeometryUsable(rail1) && NodeConnector.isGeometryUsable(rail2);

        if (goodRadius && isValid && isValidContinuousMovement) {
            railwayData.addRail(player, transportMode, posStart, posEnd, rail1, false);
            final long newId = railwayData.addRail(player, transportMode, posEnd, posStart, rail2, true);
            world.setBlockAndUpdate(posStart, stateStart.setValue(BlockNode.IS_CONNECTED, true));
            world.setBlockAndUpdate(posEnd, stateEnd.setValue(BlockNode.IS_CONNECTED, true));
            // 万向节点：把 blockstate 的连接状态同步进 BE 缓存 / NBT
            NodeConnector.syncConnectedCache(world, posStart);
            NodeConnector.syncConnectedCache(world, posEnd);
            PacketTrainDataGuiServer.createRailS2C(world, transportMode, posStart, posEnd, rail1, rail2, newId);
            Main.debug("[MultiDirectionNode] 建轨成功 {}->{}", posStart, posEnd);
        } else if (player != null) {
            player.displayClientMessage(ComponentHelper.translatable(isValidContinuousMovement
                    ? goodRadius ? "gui.mtr.invalid_orientation" : "gui.mtr.radius_too_small"
                    : "gui.mtr.cable_car_invalid_orientation"), true);
        }
        ci.cancel();
    }
}
