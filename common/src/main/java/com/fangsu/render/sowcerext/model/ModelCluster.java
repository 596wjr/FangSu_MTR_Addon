package com.fangsu.render.sowcerext.model;

import com.fangsu.render.sowcer.batch.BatchManager;
import com.fangsu.render.sowcer.batch.EnqueueProp;
import com.fangsu.render.sowcer.batch.ShaderProp;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.model.Model;
import com.fangsu.render.sowcer.model.VertArrays;
import com.fangsu.render.sowcer.util.AttrUtil;
import com.fangsu.render.sowcer.util.DrawContext;
import com.fangsu.render.sowcer.vertex.VertAttrMapping;
import com.fangsu.render.sowcer.vertex.VertAttrState;
import com.fangsu.render.sowcerext.model.integration.BufferSourceProxy;
import com.fangsu.render.sowcerext.reuse.ModelManager;
import net.minecraft.resources.ResourceLocation;

import java.io.Closeable;

public class ModelCluster implements Closeable {

    public final VertArrays uploadedOpaqueParts;
    public final RawModel opaqueParts;
    public final VertArrays uploadedTranslucentParts;
    public final RawModel translucentParts;
    private boolean isClosed = false;

    /**
     * 本 cluster 是否拥有 uploaded*Parts 里的 VAO。
     * {@link #copyForMaterialChanges()} 产生的副本与源共享同一批 GL 对象（包括 VAO 名字），
     * 因此副本不拥有资源，close() 时绝不能释放它们，否则会连带破坏源 ModelCluster。
     */
    private final boolean ownsVertArrays;

    /**
     * 匿名上传（rawModel.sourceLocation == null）时由本 cluster 独占持有的底层 Model。
     * 只有 Model 才持有 VBO / 索引缓冲 —— 必须在这里一起 close，
     * 否则每次 uploadLater 替换模型都会泄漏一组 GPU 缓冲。
     */
    private final Model opaqueModel;
    private final Model translucentModel;
    private final boolean ownsOpaqueModel;
    private final boolean ownsTranslucentModel;

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
        this.ownsVertArrays = true;
        // 匿名模型由本 cluster 独占；具名模型走全局缓存共享，不能由这里释放
        this.ownsOpaqueModel = opaqueParts.sourceLocation == null;
        this.ownsTranslucentModel = translucentParts.sourceLocation == null;
        this.opaqueModel = modelManager.uploadModel(opaqueParts);
        this.translucentModel = modelManager.uploadModel(translucentParts);
        this.uploadedOpaqueParts = VertArrays.createAll(opaqueModel, mapping, null);
        this.uploadedTranslucentParts = VertArrays.createAll(translucentModel, mapping, null);
    }

    private ModelCluster(VertArrays uploadedOpaqueParts, RawModel opaqueParts, VertArrays uploadedTranslucentParts, RawModel translucentParts) {
        this.uploadedOpaqueParts = uploadedOpaqueParts;
        this.opaqueParts = opaqueParts;
        this.uploadedTranslucentParts = uploadedTranslucentParts;
        this.translucentParts = translucentParts;
        // 副本与源共享 GL 对象，不拥有任何资源
        this.ownsVertArrays = false;
        this.opaqueModel = null;
        this.translucentModel = null;
        this.ownsOpaqueModel = false;
        this.ownsTranslucentModel = false;
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

    /**
     * 释放本 cluster 独占的 GL 资源（VAO + VBO + 索引缓冲）。
     * 幂等；对 {@link #copyForMaterialChanges()} 产生的共享副本是空操作。
     */
    @Override
    public void close() {
        if (isClosed) return;
        isClosed = true;

        if (ownsVertArrays) {
            uploadedOpaqueParts.close();
            uploadedTranslucentParts.close();
        }
        // Model 才持有 VBO / IBO，必须一并释放
        if (ownsOpaqueModel && opaqueModel != null) opaqueModel.close();
        if (ownsTranslucentModel && translucentModel != null) translucentModel.close();
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
