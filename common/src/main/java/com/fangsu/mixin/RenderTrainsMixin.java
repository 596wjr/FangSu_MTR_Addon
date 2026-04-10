package com.fangsu.mixin;

import com.fangsu.data.LiftExtraSupplier;
import com.fangsu.mtr.ModernTexturedLift;
import com.mojang.blaze3d.vertex.PoseStack;
import mtr.client.ClientData;
import mtr.data.Lift;
import mtr.data.LiftClient;
import mtr.mappings.UtilitiesClient;
import mtr.render.RenderTrains;
import mtr.render.TrainRendererBase;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static mtr.data.IGui.SMALL_OFFSET;

@Mixin(value = RenderTrains.class, remap = false)
public class RenderTrainsMixin {

    //    @Redirect(
//
//            //            method = "render(Lmtr/entity/EntitySeat;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
//            method = "lambda$render$5",
//            at = @At(
//                    value = "NEW", target = "Lmtr/model/ModelLift1;"
//            ),
//            remap = false
//    )
//    private static ModelLift1 redirectLiftConstructor(
//            int liftHeight, int liftWidth, int liftDepth, boolean isDoubleSided) {
//        return new ModernTexturedLift(liftHeight, liftWidth, liftDepth, isDoubleSided);
//    }
//    @Redirect(
//            method = "lambda$render$5",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lmtr/model/ModelLift1;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lmtr/data/NameColorDataBase;Lnet/minecraft/resources/ResourceLocation;IFFZIIZZZZZ)V")
//    )
//    private static void render(ModelLift1 instance, PoseStack matrices, MultiBufferSource vertexConsumers, NameColorDataBase data, ResourceLocation texture,
//                               int light, float doorLeftValue, float doorRightValue, boolean opening, int currentCar, int trainCars,
//                               boolean head1IsFront, boolean lightsOn, boolean isTranslucent, boolean renderDetails, boolean atPlatform) {
//
//    }

    @Shadow
    public static void renderLiftDisplay(PoseStack matrices, MultiBufferSource vertexConsumers, BlockPos pos, String floorNumber, Lift.LiftDirection liftDirection, float maxWidth, float height) {
    }

    @Inject(
            method = "lambda$render$6",
            cancellable = true,
            at = @At("HEAD")
    )
    private static void renderLift(Level world, PoseStack matrices, MultiBufferSource vertexConsumers, float newLastFrameDuration, LiftClient lift, CallbackInfo ci) {
        lift.tickClient(world, (x, y, z, frontDoorValue, backDoorValue) -> {
            final BlockPos posAverage = TrainRendererBase.applyAverageTransform(lift.getViewOffset(), x, y, z);
            if (posAverage == null) {
                return;
            }

            matrices.translate(x, y, z);
            UtilitiesClient.rotateXDegrees(matrices, 180);
            UtilitiesClient.rotateYDegrees(matrices, 180 + lift.facing.toYRot());
            final int light = LightTexture.pack(world.getBrightness(LightLayer.BLOCK, posAverage), world.getBrightness(LightLayer.SKY, posAverage));
            String liftModel = ((LiftExtraSupplier) lift).fangsu$getModelKey();
            new ModernTexturedLift(lift, lift.liftHeight, lift.liftWidth, lift.liftDepth, lift.isDoubleSided).render(matrices, vertexConsumers, lift, light, frontDoorValue, backDoorValue);

            for (int i = 0; i < (lift.isDoubleSided ? 2 : 1); i++) {
                UtilitiesClient.rotateYDegrees(matrices, 180);
                matrices.pushPose();
                matrices.translate(0.875F, -1.5, lift.liftDepth / 2F - 0.25 - SMALL_OFFSET);
                renderLiftDisplay(matrices, vertexConsumers, posAverage, ClientData.DATA_CACHE.requestLiftFloorText(lift.getCurrentFloorBlockPos())[0], lift.getLiftDirection(), 0.1875F, 0.3125F);
                matrices.popPose();
            }

            matrices.popPose();
        }, newLastFrameDuration);
        ci.cancel();
    }
}
