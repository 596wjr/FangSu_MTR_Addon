package com.fangsu.ticketSystem.v2.client;

import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.TextUtil;
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
    private static final Map<String,Long> SCREEN_STARTED=new HashMap<>();
    private static final java.awt.font.FontRenderContext DOT_METRICS=new java.awt.font.FontRenderContext(null,false,false);
    private static void checkReload(){long current=ResourceUtil.getReloadVersion();if(current!=version){clear();version=current;}}
    public static void clear(){for(Entry entry:CACHE.values())entry.texture.close();CACHE.clear();SCREEN_STARTED.clear();}
    private static GraphicsTexture cached(String id,String state,java.util.function.Supplier<GraphicsTexture> make){
        checkReload();Entry previous=CACHE.get(id);
        if(previous!=null && previous.state.equals(state))return previous.texture;
        if(previous!=null)previous.texture.close();
        GraphicsTexture texture=make.get();CACHE.put(id,new Entry(state,texture));
        while(CACHE.size()>128){var iterator=CACHE.entrySet().iterator();var entry=iterator.next();entry.getValue().texture.close();SCREEN_STARTED.remove(entry.getKey());iterator.remove();}
        return texture;
    }
    public static void remove(String id){SCREEN_STARTED.remove(id);Entry entry=CACHE.remove(id);if(entry!=null)entry.texture.close();}
    public static ResourceLocation card(String raw,Card card,boolean back){
        return cardFace(raw, card, back).identifier;
    }
    static GraphicsTexture cardFace(String raw, Card card, boolean back) {
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
            Map<String,String> values=new HashMap<>();values.put("name",card.product.name);values.put("balance","$"+card.balance);values.put("fare","$"+card.allowance);
            values.put("network",String.join(" / ",card.product.networks));values.put("serial",card.serial.substring(0,Math.min(12,card.serial.length())));
            String ticketNumber = card.serial.substring(0, Math.min(7, card.serial.length())).toUpperCase(Locale.ROOT);
            values.put("ticketNumber", ticketNumber);
            values.put("fareYuan", "￥ " + card.allowance + ".0元");
            values.put("origin", printedStationName(TextUtil.getCjkParts(card.originName)));
            values.put("originLatin", TextUtil.getNonCjkParts(card.originName));
            values.put("destination", printedStationName(TextUtil.getCjkParts(card.destinationName)));
            values.put("destinationLatin", TextUtil.getNonCjkParts(card.destinationName));
            String soldAt = card.salesStationName == null ? card.originName : card.salesStationName;
            String salesStation = printedStationName(TextUtil.getCjkParts(soldAt));
            if (salesStation.isEmpty()) salesStation = TextUtil.getNonCjkParts(soldAt);
            values.put("salesStation", salesStation);
            String separator = ticketNumber.isEmpty() || salesStation.isEmpty() ? "" : " · ";
            values.put("ticketIssueLine", ticketNumber + separator + salesStation);
            values.put("expiry",card.activated==0?"未激活":(card.product.kind==Kind.DAY||card.product.validHours>0)?new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(card.activated+card.product.durationHours()*3600000L)):"不限期");
            values.put("state",card.journey==null?"未进站":"已进站");values.put("rights",Integer.toString(card.product.legs.size()-card.used.size()));
            values.put("union",card.product.badges.stream().map(b->b.name).collect(java.util.stream.Collectors.joining(" / ")));
            values.put("kindEn",switch(card.product.kind){case SINGLE->"Single Journey Ticket";case IC->"IC Card";case DAY->"Day Pass";case ITINERARY->"Itinerary Ticket";});
            String fieldsKey=back?"backFields":"fields";
            if (!back && style.has("frame")) {
                JsonObject frame = style.getAsJsonObject("frame");
                Stroke previousStroke = g.getStroke();
                int dash = bounded(frame, "dash", 0, 0, 32);
                if (dash > 0) {
                    int stroke = bounded(frame, "stroke", 1, 1, 8);
                    g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                            new float[]{dash, Math.max(1, dash / 2f)}, 0));
                }
                g.setColor(Color.decode(frame.get("color").getAsString()));
                g.drawRect(bounded(frame, "x", 0, 0, w), bounded(frame, "y", 0, 0, h),
                        bounded(frame, "width", 0, 0, w), bounded(frame, "height", 0, 0, h));
                g.setStroke(previousStroke);
            }
            if (!back && style.has("arrow")) {
                JsonObject arrow = style.getAsJsonObject("arrow");
                int x = bounded(arrow, "x", 0, 0, w), y = bounded(arrow, "y", 0, 0, h);
                int width = bounded(arrow, "width", 0, 0, w - x), height = bounded(arrow, "height", 0, 0, h - y);
                int base = y + height;
                g.setColor(Color.decode(arrow.get("color").getAsString()));
                g.fillPolygon(new int[]{x, x + width * 4 / 5, x + width * 7 / 10, x + width, x},
                        new int[]{base - 2, base - 2, y, base, base}, 5);
            }
            if (!back && style.has("image")) {
                JsonObject image = style.getAsJsonObject("image");
                try {
                    byte[] bytes = ResourceUtil.loadResourceBytes(new ResourceLocation(image.get("texture").getAsString()));
                    BufferedImage overlay = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
                    int x = bounded(image, "x", 0, 0, w), y = bounded(image, "y", 0, 0, h);
                    g.drawImage(overlay, x, y, bounded(image, "width", overlay.getWidth(), 1, w),
                            bounded(image, "height", overlay.getHeight(), 1, h), null);
                } catch (Exception ex) {
                    com.fangsu.Main.LOGGER.debug("票面附图不可用: {}", card.product.style, ex);
                }
            }
            if(style.has(fieldsKey))for(JsonElement element:style.getAsJsonArray(fieldsKey)){
                JsonObject field=element.getAsJsonObject();
                if(field.has("kinds") && !field.getAsJsonArray("kinds").contains(new JsonPrimitive(card.product.kind.name())))continue;
                if(field.has("clear")) {
                    JsonObject clear=field.getAsJsonObject("clear");g.setColor(Color.decode(clear.get("color").getAsString()));
                    g.fillRect(bounded(clear,"x",0,0,w),bounded(clear,"y",0,0,h),bounded(clear,"width",w,0,w),bounded(clear,"height",h,0,h));
                }
                String key=field.get("field").getAsString();
                String value = values.getOrDefault(key, "");
                String text = (field.has("prefix") ? field.get("prefix").getAsString() : "") + value
                        + (!value.isEmpty() && field.has("suffix") ? field.get("suffix").getAsString() : "");
                if (text.isEmpty()) continue;
                int size=bounded(field,"size",10,6,128),x=bounded(field,"x",8,0,w),y=bounded(field,"y",20,0,h);
                String fontName = field.has("font") ? field.get("font").getAsString() : "Microsoft YaHei";
                int weight = field.has("bold") && field.get("bold").getAsBoolean() ? Font.BOLD : Font.PLAIN;
                g.setFont(new Font(fontName, weight, size));
                try{g.setColor(Color.decode(field.has("color")?field.get("color").getAsString():"#12334b"));}catch(RuntimeException ex){g.setColor(Color.DARK_GRAY);}
                int max = bounded(field, "maxWidth", w - x, 0, w);
                int minSize = bounded(field, "minSize", size, 6, size);
                while (size > minSize && g.getFontMetrics().stringWidth(text) > max) {
                    g.setFont(new Font(fontName, weight, --size));
                }
                while (!text.isEmpty() && g.getFontMetrics().stringWidth(text) > max) text = text.substring(0, text.length() - 1);
                if (field.has("center") && field.get("center").getAsBoolean()) x += (max - g.getFontMetrics().stringWidth(text)) / 2;
                g.drawString(text,x,y);
            }else if(!back){g.setColor(Color.DARK_GRAY);g.setFont(new Font("Microsoft YaHei",Font.PLAIN,12));g.drawString(card.product.name,8,24);g.drawString(card.product.kind==Kind.IC?"余额 "+"$"+card.balance:"票额 "+"$"+card.allowance,8,h-16);}
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
        });
    }
    private static String printedStationName(String name) {
        return name.isEmpty() || name.endsWith("站") ? name : name + "站";
    }
    private static int bounded(JsonObject json,String key,int fallback,int min,int max){return Math.max(min,Math.min(max,json.has(key)?json.get(key).getAsInt():fallback));}
    public static ResourceLocation screen(String id,String text,int w,int h,int kind,int cycleSeconds){
        checkReload();long now=System.currentTimeMillis();
        long started=SCREEN_STARTED.computeIfAbsent(id,key->now);
        String[] pages=kind==2 ? new String[]{text.replace('|', '\n')} : text.split("\\|",-1);
        String page=pages[(int)((now-started)/(Math.max(1,cycleSeconds)*1000L)%pages.length)];
        int textHeight=kind==-1?h:Math.max(32,h/4)-6;
        String[] lines=page.split("\\n",-1);int maskWidth=Math.max(1,(kind==-1?w:w-8)/2);
        int fontSize=Math.max(6,Math.min(20,textHeight/2/lines.length-2));
        Font font=new Font("Microsoft YaHei",Font.PLAIN,fontSize);
        boolean scrolling=Arrays.stream(lines).anyMatch(line->font.getStringBounds(line,DOT_METRICS).getWidth()>maskWidth-4);
        long frame=scrolling?(now-started)/100:0;
        return cached(id,page+"/"+w+"/"+h+"/"+kind+"/"+frame,()->{
            GraphicsTexture texture=new GraphicsTexture(w,h);Graphics2D g=texture.graphics;
            g.setColor(new Color(kind == -1 ? 0x061510 : 0x101A1D));g.fillRect(0,0,w,h);
            if(kind==-1){dotText(g,page,0,0,w,h,frame,0x65FF8C);texture.upload();return texture;}
            int header=Math.max(32,h/4);dotText(g,page,4,3,w-8,header-6,frame,0xF2C94C);
            g.setColor(new Color(0xEDF4F6));g.fillRect(0,header,w,h-header);
            g.setColor(new Color(0x19304B));g.setFont(new Font("Microsoft YaHei",Font.PLAIN,Math.max(9,w/19)));
            centered(g,"请选择服务 / Select a service",0,header+6,w,Math.max(20,h/12));
            String[][] labels=kind==2?new String[][]{{"补票","Fare Adjustment"}}:kind==1?new String[][]{{"购卡","Buy Card"},{"充值","Top Up"}}:new String[][]{{"购票","Buy Ticket"}};
            int gap=Math.max(5,w/24),tileW=w-gap*2,tileH=Math.max(16,(h-header-Math.max(25,h/10)-gap*(labels.length+1))/labels.length);
            for(int i=0;i<labels.length;i++){
                int y=header+Math.max(25,h/10)+gap+i*(tileH+gap);
                g.setColor(new Color(i==0?0x308EBB:0xE99836));g.fillRoundRect(gap,y,tileW,tileH,6,6);
                g.setColor(Color.WHITE);
                int chineseHeight=tileH*3/5,englishHeight=tileH-chineseHeight;
                g.setFont(new Font("Microsoft YaHei",Font.BOLD,Math.max(10,Math.min(chineseHeight-4,w/7))));
                centered(g,labels[i][0],gap,y,tileW,chineseHeight);
                g.setFont(new Font("SansSerif",Font.PLAIN,Math.max(7,Math.min(englishHeight-3,w/16))));
                centered(g,labels[i][1],gap,y+chineseHeight-2,tileW,englishHeight);
            }
            texture.upload();return texture;
        }).identifier;
    }
    private static void centered(Graphics2D g,String text,int x,int y,int w,int h){
        FontMetrics fm=g.getFontMetrics();g.drawString(text,x+(w-fm.stringWidth(text))/2,y+(h-fm.getHeight())/2+fm.getAscent());
    }
    /** 保持字号，短文本居中，长文本在点阵视窗内滚动，换行不参与语言轮播。 */
    private static void dotText(Graphics2D target,String text,int x,int y,int w,int h,long frame,int litColor){
        int step=2,mw=Math.max(1,w/step),mh=Math.max(1,h/step);
        BufferedImage mask=new BufferedImage(mw,mh,BufferedImage.TYPE_INT_RGB);Graphics2D ink=mask.createGraphics();
        try{
            ink.setColor(Color.BLACK);ink.fillRect(0,0,mw,mh);ink.setColor(Color.WHITE);
            String[] lines=text.split("\\n",-1);int row=Math.max(1,mh/lines.length);
            int size=Math.max(6,Math.min(20,row-2));ink.setFont(new Font("Microsoft YaHei",Font.PLAIN,size));
            FontMetrics metrics=ink.getFontMetrics();
            for(int i=0;i<lines.length;i++){
                String line=lines[i];int width=metrics.stringWidth(line);
                int px=width<=mw-4?(mw-width)/2:mw-(int)(frame%(width+mw+12));
                ink.drawString(line,px,i*row+(row-metrics.getHeight())/2+metrics.getAscent());
            }
            for(int py=0;py<mh;py++)for(int px=0;px<mw;px++){
                target.setColor(new Color((mask.getRGB(px,py)&0xFFFFFF)==0?0x27302A:litColor));target.fillRect(x+px*step,y+py*step,1,1);
            }
        }finally{ink.dispose();}
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
