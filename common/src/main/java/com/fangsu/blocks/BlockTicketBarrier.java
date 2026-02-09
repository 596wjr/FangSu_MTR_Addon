package com.fangsu.blocks;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blockEntities.BlockEntityTicketBarrier;
import mtr.mappings.BlockEntityMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockTicketBarrier extends BaseObjBlock {
    public BlockTicketBarrier(Properties properties) {
        super(properties);
    }

    public BlockTicketBarrier() {
        super();
    }

    @Override
    public @Nullable BaseObjBlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BlockEntityTicketBarrier(pos, state);
    }

    @Override
    public BlockEntityMapper createBlockEntity(BlockPos blockPos, BlockState blockState) {
        BlockEntityTicketBarrier be = new BlockEntityTicketBarrier(blockPos, blockState);
        return be;
    }
}
