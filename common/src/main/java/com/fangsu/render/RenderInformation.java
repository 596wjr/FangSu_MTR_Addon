package com.fangsu.render;

import com.mojang.math.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;

/**
 * 渲染信息封装类，包含模型、纹理、变换等信息
 */
public class RenderInformation {
    private ResourceLocation modelLocation;
    private OBJModel model;
    private Transform transform;
    private BlockPos blockPos;
    private boolean visible = true;
    private int lightLevel = 15;
    private int overlay = 0;
    private Map<String, ResourceLocation> materialTextures = new HashMap<>();

    public RenderInformation() {}

    public RenderInformation(ResourceLocation modelLocation, Transform transform, BlockPos blockPos) {
        this.modelLocation = modelLocation;
        this.transform = transform;
        this.blockPos = blockPos;
    }

    // 从文件加载模型
    public RenderInformation(java.io.File modelFile, Transform transform, BlockPos blockPos) {
        try {
            this.model = OBJModel.loadFromFile(modelFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.transform = transform;
        this.blockPos = blockPos;
    }

    public RenderInformation setModel(ResourceLocation modelLocation) {
        this.modelLocation = modelLocation;
        this.model = null; // 清除缓存的模型，下次重新加载
        return this;
    }

    public RenderInformation setModel(OBJModel model) {
        this.model = model;
        this.modelLocation = null;
        return this;
    }

    public RenderInformation setTransform(Transform transform) {
        this.transform = transform;
        return this;
    }

    public RenderInformation setBlockPos(BlockPos blockPos) {
        this.blockPos = blockPos;
        return this;
    }

    public RenderInformation setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public RenderInformation setLightLevel(int lightLevel) {
        this.lightLevel = lightLevel;
        return this;
    }

    public RenderInformation setOverlay(int overlay) {
        this.overlay = overlay;
        return this;
    }

    /**
     * 设置特定材质的纹理
     */
    public RenderInformation setMaterialTexture(String materialName, ResourceLocation texture) {
        materialTextures.put(materialName, texture);
        return this;
    }

    /**
     * 批量设置材质纹理
     */
    public RenderInformation setMaterialTextures(Map<String, ResourceLocation> textures) {
        materialTextures.putAll(textures);
        return this;
    }

    /**
     * 获取模型，如果未加载则自动加载
     */
    public OBJModel getModel() {
        if (model == null && modelLocation != null) {
            try {
                model = OBJModel.loadFromResource(modelLocation);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return model;
    }

    /**
     * 获取特定材质的纹理
     */
    public ResourceLocation getMaterialTexture(String materialName) {
        return materialTextures.get(materialName);
    }

    /**
     * 获取所有材质纹理
     */
    public Map<String, ResourceLocation> getMaterialTextures() {
        return new HashMap<>(materialTextures);
    }

    // Getter方法
    public ResourceLocation getModelLocation() { return modelLocation; }
    public Transform getTransform() { return transform; }
    public BlockPos getBlockPos() { return blockPos; }
    public boolean isVisible() { return visible; }
    public int getLightLevel() { return lightLevel; }
    public int getOverlay() { return overlay; }

    public RenderInformation copy() {
        RenderInformation copy = new RenderInformation();
        copy.modelLocation = modelLocation;
        copy.model = model; // 注意：这是浅拷贝
        copy.transform = transform != null ? transform.copy() : null;
        copy.blockPos = blockPos;
        copy.visible = visible;
        copy.lightLevel = lightLevel;
        copy.overlay = overlay;
        copy.materialTextures = new HashMap<>(materialTextures);
        return copy;
    }
}