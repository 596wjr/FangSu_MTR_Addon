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
    private static final java.util.LinkedHashMap<String,TicketDefinitions.Card> PARSED=new java.util.LinkedHashMap<>(16,.75f,true);
    public static void init(){
        dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(client->{if(client.level==null){TicketTextures.clear();PARSED.clear();}});
    }
    //#if MC_VERSION >= 11904
    public static boolean render(ItemStack stack,ItemDisplayContext context,boolean left,PoseStack pose,MultiBufferSource buffers,int light,int overlay,BakedModel model){
    //#else
    //$$ public static boolean render(ItemStack stack,net.minecraft.client.renderer.block.model.ItemTransforms.TransformType context,boolean left,PoseStack pose,MultiBufferSource buffers,int light,int overlay,BakedModel model){
    //#endif
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
