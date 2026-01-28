package com.fangsu.fabric.blocks;

import com.fangsu.Main;
import com.fangsu.blocks.IBlockEntityEyeCandy;
import com.fangsu.blocks.IEyeCandyFunctionalBlock;
import com.fangsu.fabric.utils.EyeCandyUtil;
import com.fangsu.utils.nte.StringMapSerializer;
import com.llamalad7.mixinextras.lib.apache.commons.StringUtils;
import fabric.cn.zbx1425.mtrsteamloco.block.BlockEyeCandy;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseEyeCandyFunctionalBlock extends BlockEyeCandy implements IEyeCandyFunctionalBlock {
    @Override
    public void applyPrefab(Level level, BlockPos pos, String prefabId, boolean fullLight){
        EyeCandyUtil.applyPrefab(level, pos, prefabId, fullLight);
    }

    public static class FunctionaBlockEntityEyeCandy extends BlockEntityEyeCandy implements IBlockEntityEyeCandy {
        public boolean asPlatform = false;
        public float doorValue = 0;
        public boolean doorTarget = false;

        private String shape = "0, 0, 0, 16, 16, 16";
        private String collisionShape = "0, 0, 0, 0, 0, 0";

        public boolean fixedMatrix = false;
        private int lightLevel = 0;
        private Map<String, String> customConfigs;

        public FunctionaBlockEntityEyeCandy(BlockPos pos, BlockState state) {
            super(pos, state);
            customConfigs = new HashMap<>();
        }

        @Override
        public String getCustomConfig(String key) {
            if (customConfigs == null) return null;
            return customConfigs.get(key);
        }

        @Override
        public void setCustomConfig(String key, String value) {
            if (customConfigs == null) customConfigs = new HashMap<>();
            customConfigs.put(key, value);
        }

        @Override
        public void readCompoundTag(CompoundTag compoundTag) {
            String id = compoundTag.getString("prefabId");
            if (StringUtils.isEmpty(id)) id = null;
            fullLight = compoundTag.getBoolean("fullLight");
            try {
                if (compoundTag.contains("customConfigs")) {
                    byte[] dataBytes = compoundTag.getByteArray("customConfigs");
                    customConfigs = StringMapSerializer.deserialize(dataBytes);
                } else if (compoundTag.contains("data")) {
                    byte[] configBytes = compoundTag.getByteArray("data");
                    customConfigs = StringMapSerializer.deserialize(configBytes);
                }
            }catch (IOException e) {
            }

            translateX = compoundTag.contains("translateX") ? compoundTag.getFloat("translateX") : 0;
            translateY = compoundTag.contains("translateY") ? compoundTag.getFloat("translateY") : 0;
            translateZ = compoundTag.contains("translateZ") ? compoundTag.getFloat("translateZ") : 0;
            rotateX = compoundTag.contains("rotateX") ? compoundTag.getFloat("rotateX") : 0;
            rotateY = compoundTag.contains("rotateY") ? compoundTag.getFloat("rotateY") : 0;
            rotateZ = compoundTag.contains("rotateZ") ? compoundTag.getFloat("rotateZ") : 0;
            asPlatform = compoundTag.contains("asPlatform") ? compoundTag.getBoolean("asPlatform") : false;
            // doorValue = compoundTag.contains("doorValue") ? compoundTag.getFloat("doorValue") : 0;
            // doorTarget = compoundTag.contains("doorTarget") ? compoundTag.getBoolean("doorTarget") : false;
            shape = compoundTag.contains("shape") ? compoundTag.getString("shape") : "0, 0, 0, 16, 16, 16";
            collisionShape = compoundTag.contains("collisionShape") ? compoundTag.getString("collisionShape") : "0, 0, 0, 0, 0, 0";
            fixedMatrix = compoundTag.contains("fixedMatrix") ? compoundTag.getBoolean("fixedMatrix") : false;
            lightLevel = compoundTag.contains("lightLevel") ? compoundTag.getInt("lightLevel") : 0;
        }

        @Override
        public void writeCompoundTag(CompoundTag compoundTag) {
            compoundTag.putString("prefabId", prefabId == null ? "" : prefabId);
            compoundTag.putBoolean("fullLight", fullLight);
            try {
                byte[] configBytes = StringMapSerializer.serializeToByteArray(customConfigs);
                compoundTag.putByteArray("customConfigs", configBytes);
            }catch (IOException e) {
                Main.LOGGER.warn("Failed to write config tag", e);
            }

            compoundTag.putFloat("translateX", translateX);
            compoundTag.putFloat("translateY", translateY);
            compoundTag.putFloat("translateZ", translateZ);
            compoundTag.putFloat("rotateX", rotateX);
            compoundTag.putFloat("rotateY", rotateY);
            compoundTag.putFloat("rotateZ", rotateZ);
            compoundTag.putBoolean("asPlatform", asPlatform);
            // compoundTag.putFloat("doorValue", doorValue);
            // compoundTag.putBoolean("doorTarget", doorTarget);
            compoundTag.putString("shape", shape);
            compoundTag.putString("collisionShape", collisionShape);
            compoundTag.putBoolean("fixedMatrix", fixedMatrix);
            compoundTag.putInt("lightLevel", lightLevel);
        }
    }
}
