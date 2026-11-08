package com.fangsu.render;

import com.fangsu.config.FangSuConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MtrTranslucentPass {
    private static final Map<RenderType, RenderType> LAYERS = new IdentityHashMap<>();
    private static final Map<RenderType, Batch> BATCHES = new LinkedHashMap<>();
    private static final ArrayDeque<Batch> AVAILABLE = new ArrayDeque<>();
    private static boolean collecting;

    private MtrTranslucentPass() {
    }

    public static void begin() {
        collecting = FangSuConfig.optimizeTranslucentFaces();
    }

    public static boolean isCollecting() {
        return collecting;
    }

    public static VertexConsumer getBuffer(RenderType original) {
        RenderType layer = LAYERS.computeIfAbsent(original, MtrTranslucentPass::colorOnly);
        return BATCHES.computeIfAbsent(layer, key -> {
            Batch reusable = AVAILABLE.pollFirst();
            return reusable == null ? new Batch() : reusable;
        }).source.getBuffer(layer);
    }

    public static void flush(RenderTarget target) {
        collecting = false;
        if (BATCHES.isEmpty()) return;

        boolean writeDepth = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            var ordered = new ArrayList<>(BATCHES.entrySet());
            ordered.sort(Comparator.comparingDouble(
                    (Map.Entry<RenderType, Batch> entry) -> entry.getValue().distanceSquared()).reversed());
            for (var entry : ordered) {
                if (target != null) target.bindWrite(false);
                Batch batch = entry.getValue();
                batch.source.endBatch(entry.getKey());
                batch.resetCenter();
                AVAILABLE.addLast(batch);
            }
        } finally {
            BATCHES.clear();
            RenderSystem.depthMask(writeDepth);
            if (target != null) Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        }
    }

    // 纹理批次按几何中心排序，避免为不同车厢重复拆分同一材质。
    private static final class Batch extends BufferBuilder {
        private final MultiBufferSource.BufferSource source = MultiBufferSource.immediate(this);
        private double xSum, ySum, zSum;
        private int count;

        private Batch() {
            super(256);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            addPosition(x, y, z);
            return super.vertex(x, y, z);
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha,
                           float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
            int before = count;
            super.vertex(x, y, z, red, green, blue, alpha, u, v, overlay, light, normalX, normalY, normalZ);
            // 快速写入路径直接写缓冲，普通路径已通过上面的 vertex 计入。
            if (count == before) addPosition(x, y, z);
        }

        private void addPosition(double x, double y, double z) {
            xSum += x;
            ySum += y;
            zSum += z;
            count++;
        }

        private double distanceSquared() {
            return count == 0 ? 0 : (xSum * xSum + ySum * ySum + zSum * zSum) / ((double) count * count);
        }

        private void resetCenter() {
            xSum = ySum = zSum = 0;
            count = 0;
        }
    }

    // 保留原材质的 shader、混合和剔除设置。四边形批次仍由原版排序。
    private static RenderType colorOnly(RenderType original) {
        return new RenderType("fangsu_mtr_translucent", original.format(), original.mode(),
                original.bufferSize(), false, true, () -> {
                    original.setupRenderState();
                    RenderSystem.depthMask(false);
                }, original::clearRenderState) { };
    }
}
