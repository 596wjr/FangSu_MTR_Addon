package com.fangsu.render.sowcerext.reuse;

import com.fangsu.Main;
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
            // <b>热修（2026-07，第二处状态泄漏）</b>：旧写法是「capture → commitRaw → restore」直线式，
            // commitRaw 每条排队绘制都会跑（每帧、每方块），任何一次抛异常都会跳过 restore()，
            // 让 GlStateTracker.isStateProtected 永久为真 → 之后所有走这条渲染栈的方速方块集体不渲染。
            // 这里：① restore 无条件在 finally 执行；② 异常吞掉并记录（一个坏模型不该让整帧全不画）；
            // ③ 每帧路径的日志去重，同一类异常只打一次并累计次数。
            boolean needProtection = !GlStateTracker.isStateProtected;
            if (needProtection) GlStateTracker.capture();
            try {
                commitRaw(drawContext);
            } catch (Throwable t) {
                warnCommitFailed(t);
            } finally {
                if (needProtection) GlStateTracker.restore();
            }
        }
        drawCalls.clear();
    }

    /** 同一类提交异常只打一次（每帧路径，不能刷屏），并累计失败次数。 */
    private static final java.util.Set<String> COMMIT_FAILED_KEYS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static int commitFailedCount;

    private static void warnCommitFailed(Throwable t) {
        commitFailedCount++;
        String msg = t.getMessage();
        String head = msg == null ? "" : (msg.contains("\n") ? msg.substring(0, msg.indexOf('\n')) : msg);
        String key = t.getClass().getSimpleName() + ": " + head;
        if (COMMIT_FAILED_KEYS.add(key)) {
            Main.LOGGER.warn("[fangsu] commitRaw failed (累计 {} 次；同类异常只打一次) {}", commitFailedCount, key, t);
        }
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
