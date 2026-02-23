package com.fangsu.render.scripting.util;

import com.fangsu.MainClient;
import com.fangsu.render.sowcer.util.GlStateTracker;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.reuse.ModelManager;
import com.mojang.blaze3d.systems.RenderSystem;

public class DynamicModelHolder {

    private ModelCluster uploadedModel;

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
}