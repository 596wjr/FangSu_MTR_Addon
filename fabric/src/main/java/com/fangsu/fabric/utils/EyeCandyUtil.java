package com.fangsu.fabric.utils;

import com.fangsu.blocks.IBlockEntityEyeCandy;
import com.fangsu.utils.IEyeCandyUtil;
import fabric.cn.zbx1425.mtrsteamloco.block.BlockEyeCandy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class EyeCandyUtil implements IEyeCandyUtil {
    public static void applyPrefab(Level level, BlockPos pos, String prefabId, boolean fullLight) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BlockEyeCandy.BlockEntityEyeCandy ec) {
            ec.prefabId = prefabId;
            ec.fullLight = fullLight;
            ec.setChanged();
        }
    }
    public static String getCustomConfig(Level level, BlockPos pos, String key){
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IBlockEntityEyeCandy ec) {
           return ec.getCustomConfig(key);
        }
        return null;
    }

    public static void setCustomConfig(Level level, BlockPos pos, String key, String value){
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IBlockEntityEyeCandy ec) {
            ec.setCustomConfig(key, value);
        }
    }
}
