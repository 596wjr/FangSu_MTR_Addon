package com.fangsu.mixin;

import com.fangsu.ticketSystem.v2.client.TicketItemRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC_VERSION >= 11904
import net.minecraft.world.item.ItemDisplayContext;
//#endif

@Mixin(ItemRenderer.class)
public abstract class TicketItemRendererMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    //#if MC_VERSION >= 11904
    private void fangsu$ticket(ItemStack stack,ItemDisplayContext context,boolean left,PoseStack pose,MultiBufferSource buffers,int light,int overlay,BakedModel model,CallbackInfo ci){
    //#else
    //$$ private void fangsu$ticket(ItemStack stack,net.minecraft.client.renderer.block.model.ItemTransforms.TransformType context,boolean left,PoseStack pose,MultiBufferSource buffers,int light,int overlay,BakedModel model,CallbackInfo ci){
    //#endif
        if(TicketItemRenderer.render(stack,context,left,pose,buffers,light,overlay,model))ci.cancel();
    }
}
