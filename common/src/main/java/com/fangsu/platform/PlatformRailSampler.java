package com.fangsu.platform;

import mtr.data.*;
import com.fangsu.data.hybrid.RailActionsModuleExtraSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.Consumer;

/** MTR3 从服务端真实轨道读取四分之一米采样及两端节点朝向。 */
public final class PlatformRailSampler {
    @SuppressWarnings("unchecked")
    public static void sample(ServerLevel level,List<BlockPos> nodes,Consumer<PlatformRailSample> complete){
        if(nodes.size()!=2||nodes.get(0).equals(nodes.get(1))){complete.accept(PlatformRailSample.empty());return;}
        try{
            RailwayData data=RailwayData.getInstance(level);Map<BlockPos,Map<BlockPos,Rail>> rails;
            if(data.railwayDataRailActionsModule instanceof RailActionsModuleExtraSupplier extra)rails=extra.getRails();
            else{var field=RailwayData.class.getDeclaredField("rails");field.setAccessible(true);rails=(Map<BlockPos,Map<BlockPos,Rail>>)field.get(data);}
            BlockPos a=nodes.get(0),b=nodes.get(1);Rail rail=rails.getOrDefault(a,Map.of()).get(b);if(rail==null)rail=rails.getOrDefault(b,Map.of()).get(a);if(rail==null)throw new IllegalArgumentException();
            double length=rail.getLength();if(!Double.isFinite(length)||length<.05||length>2048)throw new IllegalArgumentException();
            var first=rail.getPosition(0);var last=rail.getPosition(length);boolean reverse=Math.hypot(first.x-a.getX()-.5,first.z-a.getZ()-.5)>Math.hypot(last.x-a.getX()-.5,last.z-a.getZ()-.5);
            List<CurvedPlatformPlanner.Point> points=new ArrayList<>();int steps=(int)Math.ceil(length*4);
            for(int i=0;i<=steps;i++){double s=Math.min(i*.25,length);var p=rail.getPosition(reverse?length-s:s);points.add(new CurvedPlatformPlanner.Point(p.x,p.y,p.z));}
            var start=reverse?rail.facingEnd:rail.facingStart;var end=reverse?rail.facingStart:rail.facingEnd;
            complete.accept(new PlatformRailSample(points,start.cos,start.sin,end.cos,end.sin));
        }catch(Exception e){complete.accept(PlatformRailSample.empty());}
    }
}
