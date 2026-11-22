package com.fangsu.mixin;

import com.fangsu.blocks.BlockTicketMachine;
import com.fangsu.blocks.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class TicketMachineChunkMixin {
    @Inject(method = "registerAllBlockEntitiesAfterLevelLoad", at = @At("TAIL"))
    private void fangsu$restoreLegacyTicketMachines(CallbackInfo ci) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (!(chunk.getLevel() instanceof ServerLevel)) return;

        var machine = ModBlocks.BLOCK_TICKET_MACHINE.get();
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        // 旧版本的售票机下半部没有方块实体。只扫描调色板里有售票机的区段。
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (!section.maybeHas(state -> state.is(machine))) continue;
            int minY = chunk.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.is(machine) || state.getValue(BlockTicketMachine.HALF) != DoubleBlockHalf.LOWER) continue;
                        BlockPos pos = new BlockPos(minX + x, minY + y, minZ + z);
                        if (chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) == null
                                && chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE) != null) {
                            chunk.setUnsaved(true);
                        }
                    }
                }
            }
        }
    }
}
