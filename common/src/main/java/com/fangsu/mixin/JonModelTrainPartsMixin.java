package com.fangsu.mixin;

import com.fangsu.train.parts.TrainPartRenderAdapterMtr3;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import mtr.data.TrainClient;
import mtr.model.ModelBogie;
import mtr.render.JonModelTrainRenderer;
import mtr.render.TrainRendererBase;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JonModelTrainRenderer.class, remap = false)
public abstract class JonModelTrainPartsMixin extends TrainRendererBase {
    @Shadow @Final private TrainClient train;
    private static final String BOGIE = "Lmtr/model/ModelBogie;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

    @Inject(method = "renderCar(IDDDFFZZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LightTexture;pack(II)I", shift = At.Shift.AFTER, remap = true),
            require = 0, remap = true)
    private void fangsu$renderPantographs(int car, double x, double y, double z, float yaw, float pitch,
                                         boolean left, boolean right, CallbackInfo callback, @Local BlockPos position) {
        if (train == null || isTranslucentBatch) return;
        int light = LightTexture.pack(world.getBrightness(LightLayer.BLOCK, position), world.getBrightness(LightLayer.SKY, position));
        TrainPartRenderAdapterMtr3.pantographs(train, car, matrices, light);
    }

    @WrapOperation(method = "renderCar(IDDDFFZZ)V", at = @At(value = "INVOKE", target = BOGIE, remap = true),
            require = 0, remap = true)
    private void fangsu$renderBogie(ModelBogie bogie, PoseStack pose, MultiBufferSource buffers, int light, int position,
                                   Operation<Void> original, @Local(argsOnly = true, ordinal = 0) float yaw,
                                   @Local(argsOnly = true, ordinal = 1) float pitch) {
        int slot = position == 0 ? 2 : position > 0 ? 0 : 1;
        if (train == null || !TrainPartRenderAdapterMtr3.carBogie(train, slot, position / 16.0, pose, yaw, pitch, light)) {
            original.call(bogie, pose, buffers, light, position);
        }
    }

    @WrapOperation(method = "renderConnection(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;DDDFF)V",
            at = @At(value = "INVOKE", target = BOGIE, remap = true), require = 0, remap = true)
    private void fangsu$renderSharedBogie(ModelBogie bogie, PoseStack pose, MultiBufferSource buffers, int light, int position,
                                         Operation<Void> original, @Local(argsOnly = true, ordinal = 0) double x,
                                         @Local(argsOnly = true, ordinal = 1) double y, @Local(argsOnly = true, ordinal = 2) double z,
                                         @Local(argsOnly = true, ordinal = 0) float yaw, @Local(argsOnly = true, ordinal = 1) float pitch) {
        if (train == null || !TrainPartRenderAdapterMtr3.sharedBogie(train, pose, x, y, z, yaw, pitch, light)) {
            original.call(bogie, pose, buffers, light, position);
        }
    }
}
