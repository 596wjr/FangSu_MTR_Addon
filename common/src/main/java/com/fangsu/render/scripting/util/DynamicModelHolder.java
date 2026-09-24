package com.fangsu.render.scripting.util;

import com.fangsu.MainClient;
import com.fangsu.render.sowcer.util.GlStateTracker;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.reuse.ModelManager;
import com.mojang.blaze3d.systems.RenderSystem;

public class DynamicModelHolder {

    private ModelCluster uploadedModel;

    /**
     * 是否由 {@code ResourceUtil} 的共享缓存持有。
     * <p>
     * 共享 holder 可能被多个方块实例同时引用，方块销毁时**不能**关闭它，
     * 必须由缓存统一回收（见 {@code ResourceUtil.init}）。否则一个方块被拆掉会连带
     * 让其他使用同一模型的方块丢失几何。
     */
    private volatile boolean shared = false;

    /**
     * 标记为共享缓存持有。仅供 {@code ResourceUtil} 内部调用。
     */
    public DynamicModelHolder markShared() {
        this.shared = true;
        return this;
    }

    public boolean isShared() {
        return shared;
    }

    public void uploadLater(RawModel rawModel) {
        RawModel finalRawModel = rawModel.copyForMaterialChanges();
        finalRawModel.sourceLocation = null;
        RenderSystem.recordRenderCall(() -> {
            boolean needProtection = !GlStateTracker.isStateProtected;
            if (needProtection) GlStateTracker.capture();
            ModelCluster lastUploadedModel = uploadedModel;
            uploadedModel = new ModelCluster(finalRawModel, ModelManager.DEFAULT_MAPPING, MainClient.modelManager);
            if (lastUploadedModel != null) lastUploadedModel.close();
            if (needProtection) GlStateTracker.restore();
        });
    }

    public ModelCluster getUploadedModel() {
        return uploadedModel;
    }

    public void close() {
        RenderSystem.recordRenderCall(() -> {
            if (uploadedModel != null) {
                uploadedModel.close();
                uploadedModel = null;
            }
        });
    }

    /**
     * 仅当本 holder 由调用方独占持有时才关闭。
     * 共享缓存的 holder 由 {@code ResourceUtil} 统一回收，方块销毁时调用本方法即可安全跳过。
     */
    public void closeIfOwned() {
        if (!shared) close();
    }

    /**
     * 是否仍然持有已上传的模型。用于调试与生命周期判断。
     */
    public boolean hasUploadedModel() {
        return uploadedModel != null;
    }
}