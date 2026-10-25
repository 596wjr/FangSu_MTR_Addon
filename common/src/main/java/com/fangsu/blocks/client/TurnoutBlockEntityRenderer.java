package com.fangsu.blocks.client;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blockEntities.BlockEntityTurnout;
import com.fangsu.blockEntities.client.BaseBlockEntityRender;
import com.fangsu.blocks.BlockTurnout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** 保留异步模型提交，额外按 MTR4 装饰方块方式绘制朝向视角的定位图标。 */
public class TurnoutBlockEntityRenderer extends BaseBlockEntityRender<BaseObjBlockEntity> {
    private static final ResourceLocation ICON=new ResourceLocation("fangsu","textures/item/turnout.png");
    public TurnoutBlockEntityRenderer(BlockEntityRenderDispatcher dispatcher){super(dispatcher);}

    @Override public void render(BaseObjBlockEntity entity,float partial,PoseStack matrices,MultiBufferSource buffers,int light,int overlay) {
        super.render(entity,partial,matrices,buffers,light,overlay);
        Minecraft minecraft=Minecraft.getInstance();
        if(!(entity instanceof BlockEntityTurnout)||minecraft.screen!=null||!BlockTurnout.isHoldingTool(minecraft.player))return;
        matrices.pushPose();matrices.translate(0.5D,0.5D,0.5D);
        matrices.mulPose(minecraft.gameRenderer.getMainCamera().rotation());
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(ICON));PoseStack.Pose pose=matrices.last();
        iconVertex(out,pose,-0.5F,-0.5F,0,1);iconVertex(out,pose,0.5F,-0.5F,1,1);
        iconVertex(out,pose,0.5F,0.5F,1,0);iconVertex(out,pose,-0.5F,0.5F,0,0);
        matrices.popPose();
    }
    private static void iconVertex(VertexConsumer out,PoseStack.Pose pose,float x,float y,float u,float v) {
        out.vertex(pose.pose(),x,y,0).color(255,255,255,255).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(),0,0,1).endVertex();
    }
    // 道岔可跨多个区段，控制器所在区段离开视野时仍须保留远端模型。
    @Override public boolean shouldRenderOffScreen(@NotNull BaseObjBlockEntity entity){return entity instanceof BlockEntityTurnout;}
}
