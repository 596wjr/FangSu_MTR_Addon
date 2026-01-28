package com.fangsu.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface IEyeCandyUtil {
    static void applyPrefab(Level level, BlockPos pos, String prefabId, boolean fullLight) {
        throw new AssertionError();
    }
    static String getCustomConfig(Level level, BlockPos pos, String key){
        throw new AssertionError();
    }
    static void setCustomConfig(Level level, BlockPos pos, String key, String value){
        throw new AssertionError();
    }
}