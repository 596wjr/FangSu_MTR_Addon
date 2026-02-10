package com.fangsu.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class FacingBlockUtil {
    /* ======================
       Direction
       ====================== */

    public static Direction getFacing(BlockState state) {
        return state.getValue(HorizontalDirectionalBlock.FACING);
    }

    public static Direction getLeftDirection(BlockState state) {
        return getFacing(state).getCounterClockWise();
    }

    public static Direction getRightDirection(BlockState state) {
        return getFacing(state).getClockWise();
    }

    /* ======================
       BlockPos
       ====================== */

    public static BlockPos getLeftPos(BlockPos pos, BlockState state) {
        return pos.relative(getLeftDirection(state));
    }

    public static BlockPos getRightPos(BlockPos pos, BlockState state) {
        return pos.relative(getRightDirection(state));
    }

    /* ======================
       BlockState
       ====================== */

    public static BlockState getLeftState(Level level, BlockPos pos, BlockState state) {
        return level.getBlockState(getLeftPos(pos, state));
    }

    public static BlockState getRightState(Level level, BlockPos pos, BlockState state) {
        return level.getBlockState(getRightPos(pos, state));
    }

    /* ======================
       BlockEntity
       ====================== */

    @Nullable
    public static BlockEntity getLeftBlockEntity(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(getLeftPos(pos, state));
    }

    @Nullable
    public static BlockEntity getRightBlockEntity(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(getRightPos(pos, state));
    }
}
