package com.fangsu.mixin;

import com.fangsu.MainClient;
import com.fangsu.render.NodeHighlightRenderer;
import com.fangsu.render.MtrTranslucentPass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.fangsu.render.sowcerext.model.integration.BufferSourceProxy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
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

    @Shadow
    private RenderTarget translucentTarget;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void fangsu$beginMtrTranslucentPass(CallbackInfo ci) {
        MtrTranslucentPass.begin();
    }

    //#if MC_VERSION >= 12004
    //$$@Inject(method = "renderSectionLayer", at = @At("HEAD"))
    //#else
    @Inject(method = "renderChunkLayer", at = @At("HEAD"))
    //#endif
    private void fangsu$drawMtrTranslucentPass(RenderType layer, PoseStack poseStack,
                                              double x, double y, double z, Matrix4f projection, CallbackInfo ci) {
        if (layer == RenderType.translucent()) {
            MtrTranslucentPass.flush(Minecraft.useShaderTransparency() ? translucentTarget : null);
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "CONSTANT", args = "stringValue=destroyProgress", ordinal = 0))
    private void afterBlockEntities(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        Minecraft.getInstance().level.getProfiler().popPush("FangSuBlockEntities");
        BufferSourceProxy vertexConsumersProxy = new BufferSourceProxy(renderBuffers.bufferSource());
        MainClient.drawScheduler.commit(vertexConsumersProxy, MainClient.drawContext);
        // 节点线框与标记圆盘：顶点是世界坐标，必须在本注入点（世界系 pose，含相机变换）绘制；
        // buffer 直接用 LevelRenderer 自己的 renderBuffers.bufferSource()（与方速方块实体同一条已验证可见的路径），
        // 不再自建 Tesselator buffer，避免在 renderLevel 中途与其他批次抢同一个共享 builder
        NodeHighlightRenderer.renderLevel(poseStack, camera, renderBuffers.bufferSource());
        if (!MainClient.is_nte_loaded)
            vertexConsumersProxy.commit();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void renderLevelLast(PoseStack poseStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, CallbackInfo ci) {
        // 兼容替换了区块层调用的渲染器，避免将队列带入下一帧。
        MtrTranslucentPass.flush(null);
        MainClient.drawContext.resetFrameProfiler();
    }


}