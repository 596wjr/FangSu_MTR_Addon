package com.fangsu.levelcrossing;

import com.fangsu.blocks.BlockLevelCrossing;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.InteractionEvent;

/** 潜行时原版会跳过方块 use，因此在物品交互之前接收刷新操作。 */
public final class LevelCrossingInteractions {
    private LevelCrossingInteractions() { }

    public static void init() {
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            //#if MC_VERSION >= 12000
            net.minecraft.world.level.Level level = player.level();
            //#else
            //$$ net.minecraft.world.level.Level level = player.level;
            //#endif
            if (!player.isShiftKeyDown() || !(level.getBlockState(pos).getBlock() instanceof BlockLevelCrossing)) {
                return EventResult.pass();
            }
            BlockLevelCrossing.refreshNearby(level, pos);
            return EventResult.interruptTrue();
        });
    }
}
