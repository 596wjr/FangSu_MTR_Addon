package com.fangsu.platform;

import net.minecraft.world.phys.shapes.*;
import java.util.List;

/** 构件以物理中心线定位，旋转矩形按 1/16 格保守栅格化，服务端无需读取客户端模型。 */
public final class PlatformDoorShape {
    public static VoxelShape create(List<PlatformDoorComponent> components,float progress){
        VoxelShape result=Shapes.empty();double step=1d/16;
        for(var component:components){
            String kind=component.kind().replace("_proxy","");double width=component.width(),depth=kind.equals("board")?5d/16:1d/16;
            float opening=component.options().getOrDefault("isolation","false").equals("true")?(component.options().getOrDefault("doorOpenOverride","false").equals("true")?1:0):progress;
            if(kind.equals("door")&&component.variant().startsWith("flex.")&&opening>=.4f)continue;
            double slide=kind.equals("door")&&!component.variant().startsWith("flex.")?width*opening*((component.variant().equals("right")||component.variant().startsWith("right."))?1:-1):0;
            double cos=Math.cos(component.yaw()),sin=Math.sin(component.yaw());
            double cx=.5+component.dx()+cos*slide,cz=.5+component.dz()-sin*slide;
            double ex=Math.abs(cos)*width/2+Math.abs(sin)*depth/2,ez=Math.abs(sin)*width/2+Math.abs(cos)*depth/2;
            int x0=(int)Math.floor((cx-ex)/step),x1=(int)Math.ceil((cx+ex)/step),z0=(int)Math.floor((cz-ez)/step),z1=(int)Math.ceil((cz+ez)/step);
            double padding=(Math.abs(cos)+Math.abs(sin))*step/2;
            for(int z=z0;z<z1;z++){
                int run=Integer.MIN_VALUE;
                for(int x=x0;x<=x1;x++){
                    double dx=(x+.5)*step-cx,dz=(z+.5)*step-cz;
                    boolean intersects=x<x1&&Math.abs(dx*cos-dz*sin)<=width/2+padding&&Math.abs(dx*sin+dz*cos)<=depth/2+padding;
                    if(intersects&&run==Integer.MIN_VALUE)run=x;
                    if(!intersects&&run!=Integer.MIN_VALUE){result=Shapes.or(result,Shapes.box(run*step,component.dy(),z*step,x*step,component.dy()+component.height(),(z+1)*step));run=Integer.MIN_VALUE;}
                }
            }
        }
        return Shapes.joinUnoptimized(result,Shapes.block(),net.minecraft.world.phys.shapes.BooleanOp.AND);
    }
    private PlatformDoorShape(){ }
}
