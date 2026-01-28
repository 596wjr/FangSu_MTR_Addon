package com.fangsu.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public interface IEyeCandyFunctionalBlock {
    /** 放置时调用 */
    public void onPlaced(Level level, BlockPos pos);

    /** 右键调用 */
    public default InteractionResult onUse(Level level, BlockPos pos, Player player) {
        return InteractionResult.PASS;
    }

    void applyPrefab(Level level, BlockPos pos, String prefabId, boolean fullLight);
}
