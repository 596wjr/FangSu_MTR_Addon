package com.fangsu.mixin;

import com.fangsu.MainClient;
import com.fangsu.render.sowcerext.model.integration.BufferSourceProxy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
//#if MC_VERSION >= 11903
import org.joml.Matrix4f;
//#else
//$$import com.mojang.math.Matrix4f;
//#endif
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 101)
public class LevelRendererMixin {

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Inject(method = "renderLevel", at = @At(value = "CONSTANT", args = "stringValue=destroyProgress", ordinal = 0))
    private void afterBlockEntities(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        Minecraft.getInstance().level.getProfiler().popPush("FangSuBlockEntities");
        BufferSourceProxy vertexConsumersProxy = new BufferSourceProxy(renderBuffers.bufferSource());
        MainClient.drawScheduler.commit(vertexConsumersProxy, MainClient.drawContext);
        if (!MainClient.is_nte_loaded)
            vertexConsumersProxy.commit();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void renderLevelLast(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        MainClient.drawContext.resetFrameProfiler();
    }


}