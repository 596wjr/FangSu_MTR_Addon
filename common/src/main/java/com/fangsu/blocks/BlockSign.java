package com.fangsu.blocks;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blockEntities.BlockEntitySign;
import com.fangsu.blockEntities.IPlatformDoor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
//#if MC_VERSION >= 11903
import net.minecraft.util.RandomSource;
//#else
import java.util.Random;
//#endif
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockSign extends BaseObjBlock {
    public BlockSign(Properties properties) {
        super(properties);
    }

    public BlockSign() {
        super();
    }

    @Override
    public @Nullable BaseObjBlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BlockEntitySign(pos, state);
    }
}
