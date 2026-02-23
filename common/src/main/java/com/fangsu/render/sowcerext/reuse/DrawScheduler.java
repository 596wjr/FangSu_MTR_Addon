package com.fangsu.render.sowcerext.reuse;

import com.fangsu.render.sowcer.batch.BatchManager;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.shader.ShaderManager;
import com.fangsu.render.sowcer.util.GlStateTracker;
import com.fangsu.render.sowcer.util.DrawContext;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.model.integration.BufferSourceProxy;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class DrawScheduler {

    public final BatchManager batchManager = new BatchManager();
    public final ShaderManager shaderManager = new ShaderManager();

    private final List<ClusterDrawCall> drawCalls = new LinkedList<>();

    public void reloadShaders(ResourceManager resourceManager) throws IOException {
        shaderManager.reloadShaders(resourceManager);
    }

    public void enqueue(ModelCluster model, Matrix4f pose, int light) {
        drawCalls.add(new ClusterDrawCall(model, pose, light));
    }

    public void commit(BufferSourceProxy vertexConsumers, DrawContext drawContext) {
        if (!drawContext.drawWithBlaze && !shaderManager.isReady()) return;
        if (drawCalls.isEmpty()) return;
        if (drawContext.drawWithBlaze) {
            for (ClusterDrawCall drawCall : drawCalls)
                drawCall.model.enqueueOpaqueBlaze(vertexConsumers, drawCall.pose, drawCall.light, drawContext);
        } else {
            for (ClusterDrawCall drawCall : drawCalls)
                drawCall.model.enqueueOpaqueGl(batchManager, drawCall.pose, drawCall.light, drawContext);
        }
        if (drawContext.drawWithBlaze || drawContext.sortTranslucentFaces) {
            for (ClusterDrawCall drawCall : drawCalls)
                drawCall.model.enqueueTranslucentBlaze(vertexConsumers, drawCall.pose, drawCall.light, drawContext);
        } else {
            for (ClusterDrawCall drawCall : drawCalls)
                drawCall.model.enqueueTranslucentGl(batchManager, drawCall.pose, drawCall.light, drawContext);
        }
        if (!drawContext.drawWithBlaze) {
            GlStateTracker.capture();
            commitRaw(drawContext);
            GlStateTracker.restore();
        }
        drawCalls.clear();
    }

    public void commitRaw(DrawContext drawContext) {
        batchManager.drawAll(shaderManager, drawContext);
    }

    private static class ClusterDrawCall {
        public ModelCluster model;
        public Matrix4f pose;
        public int light;

        public ClusterDrawCall(ModelCluster model, Matrix4f pose, int light) {
            this.model = model;
            this.pose = pose;
            this.light = light;
        }
    }
}
