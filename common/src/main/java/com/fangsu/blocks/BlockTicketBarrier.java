package com.fangsu.blocks;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blockEntities.BlockEntityTicketBarrier;
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

    @Override public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null : (world,pos,blockState,entity) -> {
            if(entity instanceof BlockEntityTicketBarrier gate) {
                gate.serverTick();
                if(world.getGameTime()%20==0) com.fangsu.ticketSystem.v2.TicketGate.bind(world,pos,gate.ticketSettings());
            }
        };
    }
    @Override public void onRemove(BlockState state,net.minecraft.world.level.Level world,BlockPos pos,BlockState replacement,boolean moving) {
        if(state.getBlock()!=replacement.getBlock()) com.fangsu.ticketSystem.v2.TicketGate.unbind(world,pos);
        super.onRemove(state,world,pos,replacement,moving);
    }

}
