package com.fangsu.ticketSystem.v2.client;

import com.fangsu.blockEntities.*;
import com.fangsu.blockEntities.client.BaseBlockEntityRender;
import com.fangsu.blocks.BlockTicketMachine;
import com.fangsu.render.sowcer.math.PoseStackUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;

public final class TicketMachineRenderer extends BaseBlockEntityRender<BaseObjBlockEntity> {
    public TicketMachineRenderer(BlockEntityRenderDispatcher dispatcher){super(dispatcher);}
    @Override public void render(BaseObjBlockEntity entity,float delta,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        super.render(entity,delta,pose,buffers,light,overlay);
        if(!(entity instanceof BlockEntityTicketMachine machine)||machine.getLevel()==null)return;
        var c=machine.ticketConfig;
        String id="machine/"+machine.getLevel().dimension().location()+"/"+machine.getBlockPos().asLong();
        var texture=TicketTextures.screen(id,machine.screenText(),c.screenPixelsX,c.screenPixelsY,machine.kind());
        pose.pushPose();pose.translate(.5,0,.5);
        PoseStackUtil.rotY(pose,(float)Math.toRadians(-machine.getBlockState().getValue(BlockTicketMachine.FACING).toYRot()+180));
        pose.translate(c.screenX,c.screenY,c.screenZ);
        PoseStackUtil.rotX(pose,(float)Math.toRadians(c.screenPitch));
        TicketTextures.quad(pose,buffers,texture,(float)c.screenWidth,(float)c.screenHeight,0,LightTexture.FULL_BRIGHT,overlay,true);
        pose.popPose();
        if(c.model.endsWith("ticket_machine_old.bbmodel")) {
            var led=TicketTextures.screen(id+"/led",c.text==null?"正常服务":c.text.replace('\n',' '),256,32,-1);
            pose.pushPose();pose.translate(.5,0,.5);
            PoseStackUtil.rotY(pose,(float)Math.toRadians(-machine.getBlockState().getValue(BlockTicketMachine.FACING).toYRot()+180));
            pose.translate(0,1.8594,-.065);
            TicketTextures.quad(pose,buffers,led,.625f,.1f,0,LightTexture.FULL_BRIGHT,overlay,true);pose.popPose();
        }
    }
}
