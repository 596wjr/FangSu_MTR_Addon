package com.fangsu.forge.blocks;

import com.fangsu.blocks.TicketBarrier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ForgeTicketBarrier extends BaseEyeCandyFunctionalBlock implements TicketBarrier {
    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos blockPos, @NotNull BlockState blockState) {
        return new FunctionaBlockEntityEyeCandy(blockPos, blockState);
    }
}
