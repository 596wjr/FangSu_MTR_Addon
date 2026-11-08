package com.fangsu.mixin;

import com.fangsu.render.MtrTranslucentPass;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.model.ModelTrainBase;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ModelTrainBase.class, remap = false)
public abstract class MtrTrainTranslucencyMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", ordinal = 0, remap = true,
            target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer fangsu$queueTrainGlass(MultiBufferSource source, RenderType layer) {
        if (MtrTranslucentPass.isCollecting()) {
            return MtrTranslucentPass.getBuffer(layer);
        }
        return source.getBuffer(layer);
    }
}
