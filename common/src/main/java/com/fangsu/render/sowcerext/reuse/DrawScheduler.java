package com.fangsu.render.sowcerext.reuse;

import com.fangsu.MainClient;
import com.fangsu.render.sowcer.batch.BatchManager;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.shader.ShaderManager;
import com.fangsu.render.sowcer.util.GlStateTracker;
import com.fangsu.render.sowcer.util.DrawContext;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.model.integration.BufferSourceProxy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.renderer.MultiBufferSource;
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
        if (model == null) return;
        drawCalls.add(new ClusterDrawCall(model, pose, light));
    }

    public void commit(BufferSourceProxy vertexConsumers, DrawContext drawContext) {
        // ===== 兼容模式：使用私有 Immediate BufferSource =====
        if (MainClient.is_nte_loaded) {
            // 1. 创建私有的 Immediate BufferSource
            MultiBufferSource.BufferSource immediate =
                    MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());

            BufferSourceProxy safeProxy = new BufferSourceProxy(immediate);

            // 2. 复用原有 enqueue 逻辑（不动模型代码）
            for (ClusterDrawCall drawCall : drawCalls) {
                if (drawCall.model == null) continue;

                drawCall.model.enqueueOpaqueBlaze(
                        safeProxy,
                        drawCall.pose,
                        drawCall.light,
                        drawContext
                );
            }

            for (ClusterDrawCall drawCall : drawCalls) {
                if (drawCall.model == null) continue;

                drawCall.model.enqueueTranslucentBlaze(
                        safeProxy,
                        drawCall.pose,
                        drawCall.light,
                        drawContext
                );
            }

            // 3. ★关键：把 FaceList 写入 BufferSource
            safeProxy.commit();

            // 4. ★关键：只提交“我自己的”顶点
            immediate.endBatch();

            drawCalls.clear();
            return;
        }
        if (!drawContext.drawWithBlaze && !shaderManager.isReady()) return;
        if (drawCalls.isEmpty()) return;
        if (drawContext.drawWithBlaze) {
            for (ClusterDrawCall drawCall : drawCalls) {
                if (drawCall.model == null) continue;
                drawCall.model.enqueueOpaqueBlaze(vertexConsumers, drawCall.pose, drawCall.light, drawContext);
            }
        } else {
            for (ClusterDrawCall drawCall : drawCalls) {
                if (drawCall.model == null) continue;
                drawCall.model.enqueueOpaqueGl(batchManager, drawCall.pose, drawCall.light, drawContext);
            }
        }
        if (drawContext.drawWithBlaze || drawContext.sortTranslucentFaces) {
            for (ClusterDrawCall drawCall : drawCalls) {
                if (drawCall.model == null) continue;
                drawCall.model.enqueueTranslucentBlaze(vertexConsumers, drawCall.pose, drawCall.light, drawContext);
            }
        } else {
            for (ClusterDrawCall drawCall : drawCalls) {
                if (drawCall.model == null) continue;
                drawCall.model.enqueueTranslucentGl(batchManager, drawCall.pose, drawCall.light, drawContext);
            }
        }
        if (!drawContext.drawWithBlaze) {
            GlStateTracker.capture();
            commitRaw(drawContext);
            GlStateTracker.restore();
        }
        drawCalls.clear();
    }

    public void commitRaw(DrawContext drawContext) {
        if (MainClient.is_nte_loaded) {

        }
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
