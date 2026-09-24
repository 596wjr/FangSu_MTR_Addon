package com.fangsu.blocks;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blockEntities.BlockEntityTrafficLight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockTrafficLight extends BaseObjBlock {
    public BlockTrafficLight(Properties properties) {
        super(properties);
    }

    public BlockTrafficLight() {
        super();
    }

    @Override
    public @Nullable BaseObjBlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BlockEntityTrafficLight(pos, state);
    }
}
