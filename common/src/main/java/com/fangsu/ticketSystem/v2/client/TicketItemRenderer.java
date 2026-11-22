package com.fangsu.ticketSystem.v2.client;

import com.fangsu.ticketSystem.v2.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
//#if MC_VERSION >= 11904
import net.minecraft.world.item.ItemDisplayContext;
//#endif

public final class TicketItemRenderer {
    private static final com.fangsu.render.sowcer.util.DrawContext DEVICE_CONTEXT=new com.fangsu.render.sowcer.util.DrawContext();
    private static final java.util.LinkedHashMap<String,TicketDefinitions.Card> PARSED=new java.util.LinkedHashMap<>(16,.75f,true);
    public static void init(){
        dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(client->{if(client.level==null){TicketTextures.clear();PARSED.clear();}});
    }
    //#if MC_VERSION >= 11904
    public static boolean render(ItemStack stack,ItemDisplayContext context,boolean left,PoseStack pose,MultiBufferSource buffers,int light,int overlay,BakedModel model){
    //#else
    //$$ public static boolean render(ItemStack stack,net.minecraft.client.renderer.block.model.ItemTransforms.TransformType context,boolean left,PoseStack pose,MultiBufferSource buffers,int light,int overlay,BakedModel model){
    //#endif
        if(stack.getItem() instanceof net.minecraft.world.item.BlockItem item && item.getBlock() instanceof com.fangsu.blocks.BlockTicketMachine machine){
            // 物品直接使用完整机身，避免旧方块下半格模型被当成整台设备。
            var config=com.fangsu.blockEntities.BlockEntityTicketMachine.defaults(machine.ticketMachineKind);
            try{
                var holder=com.fangsu.utils.ResourceUtil.loadDmh(new net.minecraft.resources.ResourceLocation(config.model),config.flipV);
                var cluster=holder.getUploadedModel();if(cluster==null)return false;
                pose.pushPose();
                try{
                    model.getTransforms().getTransform(context).apply(left,pose);pose.scale(.5f,.5f,.5f);pose.translate(0,-1,0);
                    var proxy=new com.fangsu.render.sowcerext.model.integration.BufferSourceProxy(buffers);
                    var matrix=new com.fangsu.render.sowcer.math.Matrix4f(pose.last().pose()).copy();
                    cluster.enqueueOpaqueBlaze(proxy,matrix,light,DEVICE_CONTEXT);cluster.enqueueTranslucentBlaze(proxy,matrix,light,DEVICE_CONTEXT);proxy.commit();
                    boolean legacy=config.model.endsWith("ticket_machine_old.bbmodel");
                    var screen = TicketTextures.screen("item/machine/"+machine.ticketMachineKind+(legacy?"/led":""),
                            TicketMachineConfig.defaultText(machine.ticketMachineKind),
                            legacy ? TicketMachineModels.CLASSIC_LED_PIXELS_X : config.screenPixelsX,
                            legacy ? TicketMachineModels.CLASSIC_LED_PIXELS_Y : config.screenPixelsY,
                            legacy ? -1 : machine.ticketMachineKind, 5);
                    if(legacy)pose.translate(0, TicketMachineModels.CLASSIC_LED_Y, TicketMachineModels.CLASSIC_LED_Z);
                    else{
                        pose.translate(config.screenX,config.screenY,config.screenZ);
                        com.fangsu.render.sowcer.math.PoseStackUtil.rotX(pose,(float)Math.toRadians(config.screenPitch));
                    }
                    TicketTextures.quad(pose, buffers, screen,
                            legacy ? TicketMachineModels.CLASSIC_LED_WIDTH : (float)config.screenWidth,
                            legacy ? TicketMachineModels.CLASSIC_LED_HEIGHT : (float)config.screenHeight,
                            0, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay, true);
                }finally{pose.popPose();}
                return true;
            }catch(java.io.IOException|RuntimeException ex){return false;}
        }
        if(!TicketCards.isNative(stack)||stack.getTag()==null||!stack.getTag().contains(TicketCards.KEY))return false;
        try{
            String raw=stack.getTag().getString(TicketCards.KEY);var card=PARSED.get(raw);
            if(card==null){card=TicketCards.read(stack,null);PARSED.put(raw,card);while(PARSED.size()>128)PARSED.remove(PARSED.keySet().iterator().next());}
            var texture=TicketTextures.card(raw,card,false);var back=TicketTextures.card(raw,card,true);pose.pushPose();
            try {
                model.getTransforms().getTransform(context).apply(left,pose);
                TicketTextures.quad(pose,buffers,texture,1,.625f,.004f,light,overlay,false);
                TicketTextures.quad(pose,buffers,back,1,.625f,-.004f,light,overlay,true);
            } finally { pose.popPose(); }
            return true;
        }catch(RuntimeException ex){return false;}
    }
    private TicketItemRenderer(){ }
}
