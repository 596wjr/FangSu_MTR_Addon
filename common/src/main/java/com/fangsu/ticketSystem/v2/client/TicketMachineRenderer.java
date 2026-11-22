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
        boolean legacy=c.model.endsWith("ticket_machine_old.bbmodel");
        String id="machine/"+machine.getLevel().dimension().location()+"/"+machine.getBlockPos().asLong();
        // 经典款保留原有印刷操作面，只在顶部LED叠加文字，不能再生成一块业务屏。
        var texture = TicketMachineModels.screenTexture(c);
        if (texture == null)
            texture = TicketTextures.screen(id+(legacy?"/led":""), machine.screenText(),
                    legacy ? TicketMachineModels.CLASSIC_LED_PIXELS_X : c.screenPixelsX,
                    legacy ? TicketMachineModels.CLASSIC_LED_PIXELS_Y : c.screenPixelsY,
                    legacy ? -1 : machine.kind(), c.screenCycleSeconds);
        pose.pushPose();
        try{
            pose.translate(.5,0,.5);
            PoseStackUtil.rotY(pose,(float)Math.toRadians(-machine.getBlockState().getValue(BlockTicketMachine.FACING).toYRot()+180));
            if(legacy)pose.translate(0, TicketMachineModels.CLASSIC_LED_Y, TicketMachineModels.CLASSIC_LED_Z + c.modelOffsetZ);
            else{pose.translate(c.screenX,c.screenY,c.screenZ+c.modelOffsetZ);PoseStackUtil.rotX(pose,(float)Math.toRadians(c.screenPitch));}
            TicketTextures.quad(pose, buffers, texture,
                    legacy ? TicketMachineModels.CLASSIC_LED_WIDTH : (float)c.screenWidth,
                    legacy ? TicketMachineModels.CLASSIC_LED_HEIGHT : (float)c.screenHeight,
                    0, LightTexture.FULL_BRIGHT, overlay, true);
        }finally{pose.popPose();}
    }
}
