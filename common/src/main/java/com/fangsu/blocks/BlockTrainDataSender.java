package com.fangsu.blocks;

import com.fangsu.blockEntities.BlockEntityTrainDataSender;
import com.fangsu.train.data.TrainDataNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

public final class BlockTrainDataSender extends Block implements EntityBlock {
    public BlockTrainDataSender() {
        super(Properties.of(
                //#if MC_VERSION < 12000
                //$$ net.minecraft.world.level.material.Material.METAL
                //#endif
        ).strength(2));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityTrainDataSender(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!isConfigTool(player.getItemInHand(hand))) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer && TrainDataNetwork.canEdit(serverPlayer, pos)
                && level.getBlockEntity(pos) instanceof BlockEntityTrainDataSender sender) {
            TrainDataNetwork.open(serverPlayer, pos, sender.config());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean isConfigTool(ItemStack stack) {
        return stack.is(com.fangsu.items.ModItems.ITEM_WRENCH.get()) || stack.is(mtr.Items.BRUSH.get());
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide && type == ModBlocks.BLOCK_ENTITY_TRAIN_DATA_SENDER.get()
                ? (world, pos, block, entity) -> ((BlockEntityTrainDataSender) entity).registerSender() : null;
    }
}
