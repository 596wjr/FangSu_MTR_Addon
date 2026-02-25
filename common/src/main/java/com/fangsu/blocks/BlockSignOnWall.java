package com.fangsu.blocks;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blockEntities.BlockEntitySign;
import com.fangsu.blockEntities.BlockEntitySignOnWall;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockSignOnWall extends BaseObjBlock {
    public BlockSignOnWall(Properties properties) {
        super(properties);
    }

    public BlockSignOnWall() {
        super();
    }

    @Override
    public @Nullable BaseObjBlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BlockEntitySignOnWall(pos, state);
    }
}
