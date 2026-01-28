package com.fangsu.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import static com.fangsu.Main.LOGGER;

public interface TicketBarrier extends IEyeCandyFunctionalBlock {
    public default void onPlaced(Level level, BlockPos pos) {
        applyPrefab(level, pos, "fangsu:gate_closed", true);

    }

    public default InteractionResult onUse(Level level, BlockPos pos, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if(be instanceof IBlockEntityEyeCandy ibeec){
            boolean isOpen="true".equals(ibeec.getCustomConfig("isOpen"));
            isOpen=!isOpen;
            LOGGER.info("isOpen:{}", isOpen);
            ibeec.setCustomConfig("isOpen",isOpen?"true":"false");
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
