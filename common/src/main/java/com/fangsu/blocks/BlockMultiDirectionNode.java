package com.fangsu.blocks;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blockEntities.BlockEntityMultiDirectionNode;
import com.fangsu.items.ModItems;
import mtr.block.BlockNode;
import mtr.data.TransportMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 万向节点方块。
 * <p>
 * 自 NTE mtrsteamloco {@code BlockDirectNode} 移植（MIT License, Copyright (c) 2022-present Zbx1425）。
 * <p>
 * 方块本体继承 MTR 的 {@link BlockNode}（TRAIN 模式），因此 MTR 的轨道连接器 / 轨道删除 /
 * 连接状态 blockstate 全部原生可用；任意角度与绑定状态存放在
 * {@link BlockEntityMultiDirectionNode} 的 NBT 中（direction / connected / directionBonded）。
 * <p>
 * 与 ANTE 蓝本的差异：ANTE 用 {@code EntityBlockMapper}（MTR mappings 的方块实体工厂，
 * 要求返回 {@code BlockEntityMapper}）；FangSu 的方块实体统一继承 {@code BaseObjBlockEntity}
 * （vanilla {@link BlockEntity} 子类，配合 BaseBlockEntityRender 渲染 OBJ），
 * 故这里直接实现 vanilla {@link EntityBlock}。
 */
public class BlockMultiDirectionNode extends BlockNode implements EntityBlock {

    public BlockMultiDirectionNode() {
        super(TransportMode.TRAIN);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BlockEntityMultiDirectionNode(pos, state);
    }

    /**
     * 使用实体渲染（OBJ 模型由 BaseBlockEntityRender 绘制）；不生成静态方块模型。
     * <p>
     * 对应地，本项目不提供 blockstates/multi_direction_node.json，只提供物品模型。
     */
    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState blockState) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        final BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof BaseObjBlockEntity objEntity)) {
            return InteractionResult.PASS;
        }
        final ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ModItems.ITEM_WRENCH.get())) {
            return objEntity.useWithWrench(state, level, pos, player, hand, hit);
        } else if (stack.is(mtr.Items.BRUSH.get())) {
            return objEntity.whenUseWithBrush(level, pos, player, hand, hit);
        }
        return objEntity.whenUseWithOther(level, pos, player, hand, hit);
    }
}
