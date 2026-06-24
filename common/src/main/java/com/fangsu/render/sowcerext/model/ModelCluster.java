package com.fangsu.render.sowcerext.model;

import com.fangsu.render.sowcer.batch.BatchManager;
import com.fangsu.render.sowcer.batch.EnqueueProp;
import com.fangsu.render.sowcer.batch.ShaderProp;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.model.VertArrays;
import com.fangsu.render.sowcer.util.AttrUtil;
import com.fangsu.render.sowcer.util.DrawContext;
import com.fangsu.render.sowcer.vertex.VertAttrMapping;
import com.fangsu.render.sowcer.vertex.VertAttrState;
import com.fangsu.render.sowcerext.model.integration.BufferSourceProxy;
import com.fangsu.render.sowcerext.reuse.ModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.Closeable;
import java.nio.charset.MalformedInputException;

public class ModelCluster implements Closeable {

    public final VertArrays uploadedOpaqueParts;
    public final RawModel opaqueParts;
    public final VertArrays uploadedTranslucentParts;
    public final RawModel translucentParts;
    private boolean isClosed = false;

    public ModelCluster(RawModel source, VertAttrMapping mapping, ModelManager modelManager) {
        this.translucentParts = new RawModel();
        this.opaqueParts = new RawModel();
        for (RawMesh mesh : source.meshList.values()) {
            if (mesh.materialProp.translucent) {
                translucentParts.append(mesh);
            } else {
                opaqueParts.append(mesh);
            }
        }
        this.uploadedOpaqueParts = VertArrays.createAll(
                modelManager.uploadModel(opaqueParts), mapping, null);
        this.uploadedTranslucentParts = VertArrays.createAll(
                modelManager.uploadModel(translucentParts), mapping, null);
    }

    private ModelCluster(VertArrays uploadedOpaqueParts, RawModel opaqueParts, VertArrays uploadedTranslucentParts, RawModel translucentParts) {
        this.uploadedOpaqueParts = uploadedOpaqueParts;
        this.opaqueParts = opaqueParts;
        this.uploadedTranslucentParts = uploadedTranslucentParts;
        this.translucentParts = translucentParts;
    }

    public void enqueueOpaqueGl(BatchManager batchManager, Matrix4f pose, int light, DrawContext drawContext) {
        // KHRDebug.glDebugMessageInsert(KHRDebug.GL_DEBUG_SOURCE_APPLICATION, KHRDebug.GL_DEBUG_TYPE_MARKER,
        //        0, KHRDebug.GL_DEBUG_SEVERITY_NOTIFICATION, "RenderOptimized " + (source.sourceLocation == null ? "unknown" : source.sourceLocation.toString()));
        int shaderLightmapUV = AttrUtil.exchangeLightmapUVBits(light);
        batchManager.enqueue(uploadedOpaqueParts, new EnqueueProp(
                new VertAttrState()
                        .setColor(255, 255, 255, 255).setOverlayUVNoOverlay()
                        .setLightmapUV(shaderLightmapUV).setModelMatrix(pose)
        ), ShaderProp.DEFAULT);
    }

    public void enqueueOpaqueBlaze(BufferSourceProxy vertexConsumers, Matrix4f pose, int light, DrawContext drawContext) {
        opaqueParts.writeBlazeBuffer(vertexConsumers, pose, light, drawContext);
    }

    public void enqueueTranslucentGl(BatchManager batchManager, Matrix4f matrix4f, int light, DrawContext drawContext) {
        int shaderLightmapUV = AttrUtil.exchangeLightmapUVBits(light);
        batchManager.enqueue(uploadedTranslucentParts, new EnqueueProp(
                new VertAttrState()
                        .setColor(255, 255, 255, 255).setOverlayUVNoOverlay()
                        .setLightmapUV(shaderLightmapUV).setModelMatrix(matrix4f)
        ), ShaderProp.DEFAULT);
    }

    public void enqueueTranslucentBlaze(BufferSourceProxy vertexConsumers, Matrix4f pose, int light, DrawContext drawContext) {
        translucentParts.writeBlazeBuffer(vertexConsumers, pose, light, drawContext);
    }

    @Override
    public void close() {
        uploadedOpaqueParts.close();
        isClosed = true;
    }

    public boolean isClosed() {
        return isClosed;
    }

    public void replaceTexture(String oldTexture, ResourceLocation newTexture) {
        uploadedOpaqueParts.replaceTexture(oldTexture, newTexture);
        opaqueParts.replaceTexture(oldTexture, newTexture);
        uploadedTranslucentParts.replaceTexture(oldTexture, newTexture);
        translucentParts.replaceTexture(oldTexture, newTexture);
    }

    public void replaceAllTexture(ResourceLocation newTexture) {
        uploadedOpaqueParts.replaceAllTexture(newTexture);
        opaqueParts.replaceAllTexture(newTexture);
        uploadedTranslucentParts.replaceAllTexture(newTexture);
        translucentParts.replaceAllTexture(newTexture);
    }

    public ModelCluster copyForMaterialChanges() {
        return new ModelCluster(
                uploadedOpaqueParts.copyForMaterialChanges(),
                opaqueParts.copyForMaterialChanges(),
                uploadedTranslucentParts.copyForMaterialChanges(),
                translucentParts.copyForMaterialChanges()
        );
    }
}
