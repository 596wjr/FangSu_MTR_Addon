package com.fangsu.platform;

import net.minecraft.nbt.CompoundTag;

/** 统一宿主中的单个构件：物理中心线相对 anchor+.5，角度为弧度；不包含客户端资源对象。 */
public record PlatformDoorComponent(String kind,double dx,double dy,double dz,double yaw,double width,double height,String model,String variant,java.util.Map<String,String> options) {
    public PlatformDoorComponent(String kind,double dx,double dy,double dz,double yaw,double width,double height,String model,String variant){this(kind,dx,dy,dz,yaw,width,height,model,variant,java.util.Map.of());}
    public PlatformDoorComponent {
        options=PlatformDoorSettings.checkOptions(options);
        if(!java.util.Set.of("door","glass","board","door_proxy","glass_proxy","board_proxy").contains(kind)||!Double.isFinite(dx+dy+dz+yaw+width+height)
                ||Math.abs(dx)>8||Math.abs(dy)>12||Math.abs(dz)>8||width<.005||width>8||height<.05||height>8
                ||model==null||model.length()>256||model.contains("..")||!model.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                ||variant==null||!variant.matches("[A-Za-z0-9_.|\\-]{1,192}"))throw new IllegalArgumentException("invalid_door_component");
    }
    public CompoundTag save(){CompoundTag t=new CompoundTag();t.putString("kind",kind);t.putDouble("dx",dx);t.putDouble("dy",dy);t.putDouble("dz",dz);t.putDouble("yaw",yaw);t.putDouble("width",width);t.putDouble("height",height);t.putString("model",model);t.putString("variant",variant);CompoundTag config=new CompoundTag();options.forEach(config::putString);t.put("options",config);return t;}
    public static PlatformDoorComponent load(CompoundTag t){return new PlatformDoorComponent(t.getString("kind"),t.getDouble("dx"),t.getDouble("dy"),t.getDouble("dz"),t.getDouble("yaw"),t.getDouble("width"),t.getDouble("height"),t.getString("model"),t.getString("variant"),options(t));}
    private static java.util.Map<String,String> options(CompoundTag tag){java.util.Map<String,String> values=new java.util.LinkedHashMap<>();var config=tag.getCompound("options");for(String key:config.getAllKeys())values.put(key,config.getString(key));return values;}
}
