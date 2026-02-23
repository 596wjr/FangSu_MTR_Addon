package com.fangsu.blockEntities.client;

import com.fangsu.Main;
import com.fangsu.MainClient;
import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.math.PoseStackUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import mtr.RegistryObject;
import mtr.block.IBlock;
import mtr.mappings.BlockEntityRendererMapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public class BaseBlockEntityRender<T extends BaseObjBlockEntity> implements BlockEntityRenderer<T> {
    private static final RegistryObject<ItemStack> BARRIER_ITEM_STACK = new RegistryObject<>(() -> new ItemStack(net.minecraft.world.item.Items.BARRIER, 1));

    public BaseBlockEntityRender(BlockEntityRenderDispatcher dispatcher) {
        super();
    }

    @Override
    public void render(@NotNull T blockEntity, float f, @NotNull PoseStack matrices, @NotNull MultiBufferSource multiBufferSource, int light, int overlay) {
        final Level world = blockEntity.getLevel();
        if (world == null) return;

        int lightToUse = blockEntity.fullLight ? LightTexture.pack(15, 15) : light;
        Matrix4f candyPose = new Matrix4f(matrices.last().pose()).copy();

        BaseObjBlockEntity.ObjBlockProperty prop = blockEntity.getProperty();

//        Minecraft.getInstance().getItemRenderer().renderStatic(BARRIER_ITEM_STACK.get(), ItemDisplayContext.GROUND, lightToUse, 0, matrices, multiBufferSource, world, 0);

//        if (prop == null) return;

        try {
            blockEntity.whenRendering();
        } catch (Exception e) {
            Main.LOGGER.error(e.getMessage());
        }

        final BlockPos pos = blockEntity.getBlockPos();
        final Direction facing = IBlock.getStatePropertySafe(world, pos, BaseObjBlock.FACING);

        if (blockEntity.isMarkedError()) {
            matrices.pushPose();
            matrices.translate(pos.getX(), pos.getY(), pos.getZ());
            matrices.translate(0.5f, 0.5f, 0.5f);
            PoseStackUtil.rotY(matrices, (float) ((System.currentTimeMillis() % 1000) * (Math.PI * 2 / 1000)));
            Minecraft.getInstance().getItemRenderer().renderStatic(BARRIER_ITEM_STACK.get(), ItemDisplayContext.GROUND, lightToUse, 0, matrices, multiBufferSource, world, 0);
            matrices.popPose();
            return;
        }

        candyPose.translate(0.5f, 0f, 0.5f);
        candyPose.translate(blockEntity.translateX, blockEntity.translateY, blockEntity.translateZ);
        candyPose.rotateY(-(float) Math.toRadians(facing.toYRot()) + (float) (Math.PI));
        candyPose.rotateX(blockEntity.rotateX);
        candyPose.rotateY(blockEntity.rotateY);
        candyPose.rotateZ(blockEntity.rotateZ);
//        if (prop.model != null) {
//            MainClient.drawScheduler.enqueue(prop.model, candyPose, lightToUse);
//        }
//        if (prop.script != null) {
//            synchronized (blockEntity.scriptContext) {
        try {
            blockEntity.scriptContext.scriptResult.commit(MainClient.drawScheduler, candyPose, lightToUse);

        } catch (Exception e) {
        }
//            }
//            prop.script.tryCallRenderFunctionAsync(blockEntity.scriptContext);
//        }
        blockEntity.scriptContext.renderFunctionFinished();
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull T blockEntity) {
        return true;
    }

    @Override
    public boolean shouldRender(@NotNull T blockEntity, @NotNull Vec3 vec3) {
        return true;
    }
}
