package com.fangsu.blockEntities.client;

import com.fangsu.MainClient;
import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blockEntities.FunctionalObjBlockEntity;
import com.fangsu.blockEntities.Scriptable;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.render.ShadersModHandler;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.math.PoseStackUtil;
import com.fangsu.render.sowcerext.reuse.DrawScheduler;
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
//#if MC_VERSION >= 11904
import net.minecraft.world.item.ItemDisplayContext;
//#endif
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public class BaseBlockEntityRender<T extends BaseObjBlockEntity> implements BlockEntityRenderer<T> {
    private static final RegistryObject<ItemStack> BARRIER_ITEM_STACK = new RegistryObject<>(() -> new ItemStack(net.minecraft.world.item.Items.BARRIER, 1));

    /**
     * 方块实体渲染距离上限（格）。超出直接跳过，避免为远处方块白白提交异步渲染任务与 draw call。
     * 可用系统属性覆盖：{@code -Dfangsu.blockEntityRenderDistance=<格数>}；设为 0 或负数表示不剔除。
     */
    private static final int MAX_RENDER_DISTANCE = Integer.getInteger("fangsu.blockEntityRenderDistance", 128);
    private static final double MAX_RENDER_DISTANCE_SQ = (double) MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

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

        // 统一走"非阻塞提交 + 上一帧结果兜底"：无论是 FunctionalObjBlockEntity 还是
        // BlockEntityRotatingRail 之类的普通方块实体，渲染线程都不再阻塞等待后台任务。
        blockEntity.tryBeginRendering();

        final BlockPos pos = blockEntity.getBlockPos();
        final Direction facing = IBlock.getStatePropertySafe(world, pos, BaseObjBlock.FACING);

        if (blockEntity.isMarkedError()) {
            matrices.pushPose();
            matrices.translate(pos.getX(), pos.getY(), pos.getZ());
            matrices.translate(0.5f, 0.5f, 0.5f);
            PoseStackUtil.rotY(matrices, (float) ((System.currentTimeMillis() % 1000) * (Math.PI * 2 / 1000)));
            //#if MC_VERSION >= 12000
            Minecraft.getInstance().getItemRenderer().renderStatic(BARRIER_ITEM_STACK.get(), ItemDisplayContext.GROUND, lightToUse, 0, matrices, multiBufferSource, world, 0);
            //#elseif MC_VERSION >= 11904
            //$$ Minecraft.getInstance().getItemRenderer().renderStatic(BARRIER_ITEM_STACK.get(), ItemDisplayContext.GROUND, lightToUse, 0, matrices, multiBufferSource, world, 0);
            //#else
            //$$ Minecraft.getInstance().getItemRenderer().renderStatic(BARRIER_ITEM_STACK.get(), net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GROUND, lightToUse, 0, matrices, multiBufferSource, 0);
            //#endif
            matrices.popPose();
            // whenRendering 在后台可能已开始，需要完成渲染周期
            blockEntity.finishRendering();
            // 消费已就绪标记，避免卡住后续重新提交
            blockEntity.consumeRenderResultIfReady();
            return;
        }

        candyPose.translate(0.5f, 0f, 0.5f);
        if (blockEntity instanceof FunctionalObjBlockEntity functional) {
            candyPose.translate(functional.translateX, functional.translateY, functional.translateZ);
            candyPose.rotateY(-(float) Math.toRadians(facing.toYRot()) + (float) (Math.PI));
            candyPose.rotateX(functional.rotateX);
            candyPose.rotateY(functional.rotateY);
            candyPose.rotateZ(functional.rotateZ);
        }
//        if (prop.model != null) {
//            MainClient.drawScheduler.enqueue(prop.model, candyPose, lightToUse);
//        }
//        if (prop.script != null) {
//            synchronized (blockEntity.scriptContext) {

        if (blockEntity instanceof Scriptable scriptable) {
            scriptable.renderScript();
        }

        if (ShadersModHandler.canUseCustomShader()) {
            blockEntity.scriptContext.scriptResult.commit(MainClient.drawScheduler, candyPose, lightToUse);
        } else {
            blockEntity.scriptContext.scriptResult.renderDirect(multiBufferSource, candyPose, lightToUse);
        }

//            }
//            prop.script.tryCallRenderFunctionAsync(blockEntity.scriptContext);
//        }

        blockEntity.finishRendering();
        // 仅当后台 whenRendering 已完成时才交换双缓冲，避免读到半写入内容；
        // 未就绪时沿用上一帧结果，GL 线程绝不阻塞等待。
        if (blockEntity.consumeRenderResultIfReady()) {
            blockEntity.scriptContext.renderFunctionFinished();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull T blockEntity) {
        return false;
    }

    @Override
    public boolean shouldRender(@NotNull T blockEntity, @NotNull Vec3 vec3) {
        if (MAX_RENDER_DISTANCE <= 0) return true;
        BlockPos pos = blockEntity.getBlockPos();
        double dx = pos.getX() + 0.5 - vec3.x;
        double dy = pos.getY() + 0.5 - vec3.y;
        double dz = pos.getZ() + 0.5 - vec3.z;
        return dx * dx + dy * dy + dz * dz <= MAX_RENDER_DISTANCE_SQ;
    }
}
