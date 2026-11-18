package com.fangsu.ticketSystem.v2.client;

import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.ticketSystem.v2.TicketDefinitions.*;
import com.fangsu.ticketSystem.v2.TicketDefinitions;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 按票卡数据和资源版本缓存，逐帧渲染不重建图像。 */
public final class TicketTextures {
    private record Entry(String state,GraphicsTexture texture) { }
    private static final LinkedHashMap<String,Entry> CACHE=new LinkedHashMap<>(16,.75f,true);
    private static long version=-1;
    private static void checkReload(){long current=ResourceUtil.getReloadVersion();if(current!=version){clear();version=current;}}
    public static void clear(){for(Entry entry:CACHE.values())entry.texture.close();CACHE.clear();}
    private static GraphicsTexture cached(String id,String state,java.util.function.Supplier<GraphicsTexture> make){
        checkReload();Entry previous=CACHE.get(id);
        if(previous!=null && previous.state.equals(state))return previous.texture;
        if(previous!=null)previous.texture.close();
        GraphicsTexture texture=make.get();CACHE.put(id,new Entry(state,texture));
        while(CACHE.size()>128){var iterator=CACHE.entrySet().iterator();var entry=iterator.next();entry.getValue().texture.close();iterator.remove();}
        return texture;
    }
    public static void remove(String id){Entry entry=CACHE.remove(id);if(entry!=null)entry.texture.close();}
    public static ResourceLocation card(String raw,Card card,boolean back){
        return cached("card/"+card.serial+(back?"/back":"/front"),raw,()->{
            JsonObject style;
            try{style=JsonParser.parseString(new String(ResourceUtil.loadResourceBytes(new ResourceLocation(card.product.style)),StandardCharsets.UTF_8)).getAsJsonObject();}
            catch(Exception ex){style=new JsonObject();}
            int w=bounded(style,"width",256,16,512),h=bounded(style,"height",160,16,512);
            GraphicsTexture texture=new GraphicsTexture(w,h);Graphics2D g=texture.graphics;
            try {
            g.setColor(new Color(0xE3EDF0));g.fillRect(0,0,w,h);
            String imageKey=back&&style.has("backBackground")?"backBackground":"background";
            if(style.has(imageKey))try{
                byte[] bytes=ResourceUtil.loadResourceBytes(new ResourceLocation(style.get(imageKey).getAsString()));
                BufferedImage base=javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));g.drawImage(base,0,0,w,h,null);
            }catch(Exception ex){com.fangsu.Main.LOGGER.debug("票面底图不可用: {}",card.product.style);}
            Map<String,String> values=new HashMap<>();values.put("name",card.product.name);values.put("balance",card.balance+"元");values.put("fare",card.allowance+"元");
            values.put("network",String.join(" / ",card.product.networks));values.put("serial",card.serial.substring(0,Math.min(12,card.serial.length())));
            values.put("expiry",card.activated==0?"未激活":(card.product.kind==Kind.DAY||card.product.validHours>0)?new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(card.activated+(card.product.kind==Kind.DAY?24:card.product.validHours)*3600000L)):"不限期");
            values.put("state",card.journey==null?"未进站":"已进站");values.put("rights",Integer.toString(card.product.legs.size()-card.used.size()));
            values.put("union",card.product.badges.stream().map(b->b.name).collect(java.util.stream.Collectors.joining(" / ")));
            values.put("kindEn",switch(card.product.kind){case SINGLE->"Single Journey Ticket";case IC->"IC Card";case DAY->"Day Pass";case ITINERARY->"Itinerary Ticket";});
            String fieldsKey=back?"backFields":"fields";
            if(style.has(fieldsKey))for(JsonElement element:style.getAsJsonArray(fieldsKey)){
                JsonObject field=element.getAsJsonObject();
                if(field.has("kinds") && !field.getAsJsonArray("kinds").contains(new JsonPrimitive(card.product.kind.name())))continue;
                if(field.has("clear")) {
                    JsonObject clear=field.getAsJsonObject("clear");g.setColor(Color.decode(clear.get("color").getAsString()));
                    g.fillRect(bounded(clear,"x",0,0,w),bounded(clear,"y",0,0,h),bounded(clear,"width",w,0,w),bounded(clear,"height",h,0,h));
                }
                String key=field.get("field").getAsString();
                String text=(field.has("prefix")?field.get("prefix").getAsString():"")+values.getOrDefault(key,"");
                int size=bounded(field,"size",10,6,128),x=bounded(field,"x",8,0,w),y=bounded(field,"y",20,0,h);
                g.setFont(new Font("Microsoft YaHei",Font.PLAIN,size));
                try{g.setColor(Color.decode(field.has("color")?field.get("color").getAsString():"#12334b"));}catch(RuntimeException ex){g.setColor(Color.DARK_GRAY);}
                int max=bounded(field,"maxWidth",w-x,0,w);while(!text.isEmpty()&&g.getFontMetrics().stringWidth(text)>max)text=text.substring(0,text.length()-1);
                g.drawString(text,x,y);
            }else if(!back){g.setColor(Color.DARK_GRAY);g.setFont(new Font("Microsoft YaHei",Font.PLAIN,12));g.drawString(card.product.name,8,24);g.drawString(card.product.kind==Kind.IC?"余额 "+card.balance+"元":"票额 "+card.allowance+"元",8,h-16);}
            if(!back && style.has("badges")) {
                JsonObject slot=style.getAsJsonObject("badges");int x=bounded(slot,"x",190,0,w),y=bounded(slot,"y",6,0,h);
                int bw=bounded(slot,"width",58,1,w),bh=bounded(slot,"height",32,1,h);
                for(Badge badge:card.product.badges)try{
                    if(y+bh>h)break;
                    BufferedImage logo=javax.imageio.ImageIO.read(new ByteArrayInputStream(ResourceUtil.loadResourceBytes(new ResourceLocation(badge.texture))));
                    g.drawImage(logo,x,y,bw,bh,null);y+=bh+3;
                }catch(Exception ex){com.fangsu.Main.LOGGER.debug("联合标识贴图不可用: {}",badge.texture);}
            }
            } catch (RuntimeException ex) { com.fangsu.Main.LOGGER.debug("票面配置绘制失败: {}",card.product.style,ex); }
            texture.upload();return texture;
        }).identifier;
    }
    private static int bounded(JsonObject json,String key,int fallback,int min,int max){return Math.max(min,Math.min(max,json.has(key)?json.get(key).getAsInt():fallback));}
    public static ResourceLocation screen(String id,String text,int w,int h,int kind){
        return cached(id,text+"/"+w+"/"+h+"/"+kind,()->{
            GraphicsTexture texture=new GraphicsTexture(w,h);Graphics2D g=texture.graphics;
            g.setColor(new Color(0x143B58));g.fillRect(0,0,w,h);g.setColor(new Color(0xDCEFF6));
            if(kind==-1){g.setColor(new Color(0x101613));g.fillRect(0,0,w,h);g.setColor(new Color(0xE9C740));g.setFont(new Font("Microsoft YaHei",Font.BOLD,22));g.drawString(text,8,25);texture.upload();return texture;}
            int header=Math.max(24,h/5);int size=Math.max(8,Math.min(16,w/11));g.setFont(new Font("Microsoft YaHei",Font.BOLD,size));g.setColor(new Color(0xFFCB55));int y=size+5;
            for(String line:text.split("\n",-1)) {String draw=line;while(!draw.isEmpty()&&g.getFontMetrics().stringWidth(draw)>w-12)draw=draw.substring(0,draw.length()-1);g.drawString(draw,6,y);y+=size+3;if(y>header)break;}
            g.setColor(new Color(0xF0F5F9));g.fillRect(0,header,w,h-header);
            g.setColor(new Color(0x52687C));g.setFont(new Font("Microsoft YaHei",Font.PLAIN,Math.max(7,w/22)));g.drawString("请选择需要的服务",5,header+16);
            String[] labels=kind==2?new String[]{"补票","充值","票卡查询","出站修复"}:kind==1?new String[]{"购卡","充值","票卡查询"}:new String[]{"购票","充值","购卡","票卡查询"};
            int[] colors={0xE04950,0x7274CE,0x318FCD,0x20A9B2};int gap=4,tileW=(w-3*gap)/2,tileH=Math.max(18,(h-header-38)/2);
            for(int i=0;i<labels.length;i++){
                int x=gap+(i%2)*(tileW+gap),ty=header+24+(i/2)*(tileH+gap);g.setColor(new Color(colors[i]));g.fillRect(x,ty,tileW,tileH);
                g.setColor(Color.WHITE);g.setFont(new Font("Microsoft YaHei",Font.BOLD,Math.max(8,w/13)));g.drawString(labels[i],x+4,ty+Math.min(tileH-3,18));
            }
            texture.upload();return texture;
        }).identifier;
    }
    public static void quad(PoseStack pose,MultiBufferSource buffers,ResourceLocation texture,float width,float height,float z,int light,int overlay,boolean back){
        VertexConsumer vertices=buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        float x=width/2,y=height/2;
        if(back){vertex(pose,vertices,-x,-y,z,1,1,light,overlay,-1);vertex(pose,vertices,-x,y,z,1,0,light,overlay,-1);vertex(pose,vertices,x,y,z,0,0,light,overlay,-1);vertex(pose,vertices,x,-y,z,0,1,light,overlay,-1);}
        else{vertex(pose,vertices,-x,-y,z,0,1,light,overlay,1);vertex(pose,vertices,x,-y,z,1,1,light,overlay,1);vertex(pose,vertices,x,y,z,1,0,light,overlay,1);vertex(pose,vertices,-x,y,z,0,0,light,overlay,1);}
    }
    private static void vertex(PoseStack pose,VertexConsumer vertices,float x,float y,float z,float u,float v,int light,int overlay,float normal){
        vertices.vertex(pose.last().pose(),x,y,z).color(255,255,255,255).uv(u,v).overlayCoords(overlay).uv2(light).normal(pose.last().normal(),0,0,normal).endVertex();
    }
    private TicketTextures(){ }
}
