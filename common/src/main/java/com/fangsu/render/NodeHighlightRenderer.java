package com.fangsu.render;

import com.fangsu.items.ItemDisplacementTool;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * 手持位移工具 / 轨道模型工具时,在轨道节点方块处绘制白色线框高亮,方便定位节点。
 * <p>
 * 数据源:MTR 客户端轨道数据中所有出现过的节点位置（RAILS 的 key,有轨道相连的节点
 * 都在内）。渲染用 vanilla LINES 图元画 12 条边,独立 Immediate BufferSource 立即提交,
 * 不依赖方速主渲染栈,无论是否加载 NTE 均正常显示。
 */
public class NodeHighlightRenderer {

    /** 节点高亮的最大绘制距离（格,平方后比较） */
    private static final long MAX_DRAW_DISTANCE_SQUARED = 64L * 64;

    /** 由 LevelRendererMixin 的 renderLevel TAIL 调用（此时 poseStack 已含相机旋转与平移） */
    public static void renderLevel(PoseStack poseStack, Camera camera) {
        final Player player = Minecraft.getInstance().player;
        if (player == null || !holdingTool(player)) return;

        final MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        renderNodes(buffer, poseStack, player);
        buffer.endBatch();
    }

    private static void renderNodes(MultiBufferSource vertexConsumers, PoseStack poseStack, Player player) {
        final VertexConsumer vc = vertexConsumers.getBuffer(RenderType.lines());
        final PoseStack.Pose pose = poseStack.last();
        final BlockPos playerPos = player.blockPosition();

        // 锁内拷贝节点 key 快照（MTR3 网络线程写 RAILS,拷贝后再于锁外渲染,避免并发修改异常）
        final Set<BlockPos> nodePositions;
        synchronized (mtr.client.ClientData.RAILS) {
            nodePositions = new HashSet<>(mtr.client.ClientData.RAILS.keySet());
        }
        for (final BlockPos pos : nodePositions) {
            if (playerPos.distSqr(pos) > MAX_DRAW_DISTANCE_SQUARED) continue;
            drawNodeBox(vc, pose, pos);
        }
    }

    /** 绘制一个节点方块的白色线框（12 条边,略缩进避免与方块面 z-fighting） */
    private static void drawNodeBox(VertexConsumer vc, PoseStack.Pose pose, BlockPos pos) {
        final float x0 = pos.getX() + 0.02F, y0 = pos.getY() + 0.02F, z0 = pos.getZ() + 0.02F;
        final float x1 = pos.getX() + 0.98F, y1 = pos.getY() + 0.98F, z1 = pos.getZ() + 0.98F;
        // 底面
        line(vc, pose, x0, y0, z0, x1, y0, z0);
        line(vc, pose, x1, y0, z0, x1, y0, z1);
        line(vc, pose, x1, y0, z1, x0, y0, z1);
        line(vc, pose, x0, y0, z1, x0, y0, z0);
        // 顶面
        line(vc, pose, x0, y1, z0, x1, y1, z0);
        line(vc, pose, x1, y1, z0, x1, y1, z1);
        line(vc, pose, x1, y1, z1, x0, y1, z1);
        line(vc, pose, x0, y1, z1, x0, y1, z0);
        // 竖边
        line(vc, pose, x0, y0, z0, x0, y1, z0);
        line(vc, pose, x1, y0, z0, x1, y1, z0);
        line(vc, pose, x1, y0, z1, x1, y1, z1);
        line(vc, pose, x0, y0, z1, x0, y1, z1);
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2) {
        // 1.18.2~1.20.1 均有 vertex(Matrix4f, x, y, z);pose.pose() 返回类型随版本（mojang/org.joml），调用方透明，无需条件编译
        vc.vertex(pose.pose(), x1, y1, z1).color(255, 255, 255, 255);
        vc.vertex(pose.pose(), x2, y2, z2).color(255, 255, 255, 255);
    }

    /** 主手或副手持位移工具时显示节点（MTR3 版无轨道模型工具） */
    private static boolean holdingTool(Player player) {
        final ItemStack main = player.getMainHandItem();
        final ItemStack off = player.getOffhandItem();
        return main.getItem() instanceof ItemDisplacementTool || off.getItem() instanceof ItemDisplacementTool;
    }
}
