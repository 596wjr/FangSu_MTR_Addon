package com.fangsu.blockEntities.client;

import com.fangsu.blockEntities.BlockEntityTrainDataSender;
import com.fangsu.utils.ResourceUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public final class TrainDataSenderRenderer implements BlockEntityRenderer<BlockEntityTrainDataSender> {
    private static final ResourceLocation DEFAULT_TEXTURE = new ResourceLocation("fangsu:textures/block/train_data_sender.png");
    private static final float EPSILON = 0.003125F;

    @Override
    public void render(BlockEntityTrainDataSender sender, float tickDelta, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        var level = sender.getLevel();
        if (level == null) return;
        ResourceLocation replacement = sender.faceTexture();
        ResourceLocation texture = replacement != null && ResourceUtil.hasResources(replacement) ? replacement : DEFAULT_TEXTURE;
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutout(texture));
        BlockPos pos = sender.getBlockPos();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(side);
            if (!Block.shouldRenderFace(sender.getBlockState(), level, pos, side, neighbor)) continue;
            int faceLight = LevelRenderer.getLightColor(level, neighbor);
            switch (side) {
                case NORTH -> face(vertices, poses.last(), side, 1, -EPSILON, 0, -EPSILON, faceLight, overlay);
                case SOUTH -> face(vertices, poses.last(), side, 0, 1 + EPSILON, 1, 1 + EPSILON, faceLight, overlay);
                case WEST -> face(vertices, poses.last(), side, -EPSILON, 0, -EPSILON, 1, faceLight, overlay);
                case EAST -> face(vertices, poses.last(), side, 1 + EPSILON, 1, 1 + EPSILON, 0, faceLight, overlay);
            }
        }
    }

    private static void face(VertexConsumer consumer, PoseStack.Pose pose, Direction side, float x0, float z0,
                             float x1, float z1, int light, int overlay) {
        vertex(consumer, pose, side, x0, 1, z0, 0, 0, light, overlay);
        vertex(consumer, pose, side, x0, 0, z0, 0, 1, light, overlay);
        vertex(consumer, pose, side, x1, 0, z1, 1, 1, light, overlay);
        vertex(consumer, pose, side, x1, 1, z1, 1, 0, light, overlay);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Direction side,
                               float x, float y, float z, float u, float v, int light, int overlay) {
        consumer.vertex(pose.pose(), x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(overlay).uv2(light)
                .normal(pose.normal(), side.getStepX(), 0, side.getStepZ()).endVertex();
    }
}
