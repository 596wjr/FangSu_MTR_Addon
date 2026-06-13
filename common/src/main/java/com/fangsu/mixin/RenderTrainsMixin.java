package com.fangsu.mixin;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.*;

import static mtr.data.IGui.SMALL_OFFSET;

@Mixin(value = RenderTrains.class, remap = false)
public class RenderTrainsMixin {
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
            new ModernTexturedLift(lift, lift.liftHeight, lift.liftWidth, lift.liftDepth, lift.isDoubleSided).render(matrices, vertexConsumers, lift, light, frontDoorValue, backDoorValue);

            for (int i = 0; i < (lift.isDoubleSided ? 2 : 1); i++) {
                UtilitiesClient.rotateYDegrees(matrices, 180);
                matrices.pushPose();
                matrices.translate(0.875F, -1.5, lift.liftDepth / 2F - 0.25 - SMALL_OFFSET);
//                renderLiftDisplay(matrices, vertexConsumers, posAverage, ClientData.DATA_CACHE.requestLiftFloorText(lift.getCurrentFloorBlockPos())[0], lift.getLiftDirection(), 0.1875F, 0.3125F);
                fangsu$renderLiftDisplayWithColor(matrices, vertexConsumers, posAverage,
                        ClientData.DATA_CACHE.requestLiftFloorText(lift.getCurrentFloorBlockPos())[0],
                        lift.getLiftDirection(), 0.1875F, 0.3125F, lift);
                matrices.popPose();
            }

            matrices.popPose();
        }, newLastFrameDuration);
        ci.cancel();
    }

    /**
     * 通过反射调用 YMTR 的 renderLiftDisplay，优先适配带 DisplayColor 参数的新版本，
     * 若反射失败则自动回退到旧版无颜色参数的方法。
     */
    @Unique
    private static void fangsu$renderLiftDisplayWithColor(PoseStack matrices, MultiBufferSource vertexConsumers,
                                                          BlockPos pos, String floorNumber,
                                                          Lift.LiftDirection liftDirection,
                                                          float maxWidth, float height, LiftClient lift) {
        try {
            // 1. 尝试反射获取 Lift 实例上的 displayColor 字段
            Field displayColorField = lift.getClass().getField("displayColor");
            Object displayColor = displayColorField.get(lift);

            // 2. 尝试获取带 DisplayColor 参数的新方法
            // 注意：DisplayColor 是 Lift 的内部枚举，直接用 displayColor 的类来定位参数类型
            Method newMethod = TrainRendererBase.class.getMethod("renderLiftDisplay",
                    PoseStack.class, MultiBufferSource.class, BlockPos.class, String.class,
                    Lift.LiftDirection.class, displayColor.getClass(), float.class, float.class);

            // 3. 调用新方法
            newMethod.invoke(null, matrices, vertexConsumers, pos, floorNumber,
                    liftDirection, displayColor, maxWidth, height);

        } catch (NoSuchFieldException | NoSuchMethodException e) {
            // 如果不存在 displayColor 字段或新方法，说明是旧版 MTR 或旧版 YMTR，回退到旧方法
            renderLiftDisplay(matrices, vertexConsumers, pos, floorNumber, liftDirection, maxWidth, height);
        } catch (IllegalAccessException | InvocationTargetException e) {
            // 反射调用出错（例如权限问题或方法内部抛出异常），同样回退并打印日志
            e.printStackTrace();
            renderLiftDisplay(matrices, vertexConsumers, pos, floorNumber, liftDirection, maxWidth, height);
        }
    }

}
