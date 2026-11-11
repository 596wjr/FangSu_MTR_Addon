/* Copyright (c) 2026 Potato Curved Platform contributors. MIT; see third_party/potato-platform/LICENSE. */
package dev.potato.curvedplatform.geometry;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 保持原目录数据与记录签名，手工读取以兼容 Minecraft 1.18 自带的旧 Gson。 */
public record PlatformSource(double front,double width,double repeat,List<SourceBox> boxes,String innerDefault) {
    private static final Map<String,PlatformSource> SOURCES=load();
    private static Map<String,PlatformSource> load(){
        try(InputStream input=PlatformSource.class.getResourceAsStream("/data/fangsu/potato/platform_sources.json")){
            if(input==null)throw new IOException("Missing platform source catalogue");
            var root=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String,PlatformSource> result=new LinkedHashMap<>();
            for(var entry:root.entrySet()){
                var v=entry.getValue().getAsJsonObject();List<SourceBox> boxes=new ArrayList<>();
                for(var box:v.getAsJsonArray("boxes")){var b=box.getAsJsonObject();boxes.add(new SourceBox(b.get("x0").getAsDouble(),b.get("y0").getAsDouble(),b.get("z0").getAsDouble(),b.get("x1").getAsDouble(),b.get("y1").getAsDouble(),b.get("z1").getAsDouble()));}
                double width=v.get("width").getAsDouble(),front=v.get("front").getAsDouble(),repeat=v.get("repeat").getAsDouble();
                if(width<=0||width>1||front<0||boxes.isEmpty()||boxes.size()>256||repeat<1||repeat>8)throw new IOException("Invalid source model");
                result.put(entry.getKey(),new PlatformSource(front,width,repeat,List.copyOf(boxes),v.get("innerDefault").getAsString()));
            }
            return Map.copyOf(result);
        }catch(IOException|RuntimeException e){throw new ExceptionInInitializerError(e);}
    }
    public static PlatformSource get(String name){return Objects.requireNonNull(SOURCES.get(name),name);}
    public record SourceBox(double x0,double y0,double z0,double x1,double y1,double z1) { }
}
