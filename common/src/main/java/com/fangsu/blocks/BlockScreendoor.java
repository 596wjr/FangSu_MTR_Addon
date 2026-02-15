package com.fangsu.blocks;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blockEntities.BlockEntityScreendoor;
import com.fangsu.blockEntities.IPlatformDoor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockScreendoor extends BaseObjBlock implements IBlockPlatform {
    public BlockScreendoor(Properties properties) {
        super(properties);
    }

    public BlockScreendoor() {
        super();
    }

    @Override
    public @Nullable BaseObjBlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BlockEntityScreendoor(pos, state);
    }

    @Override
    public void tick(
            @NotNull BlockState state,
            @NotNull ServerLevel level,
            @NotNull BlockPos pos,
            @NotNull RandomSource random
    ) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IPlatformDoor platform) {
            platform.setDoorValue(0);
        }
    }
}
