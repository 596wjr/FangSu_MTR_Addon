package com.fangsu.platform;

import dev.potato.curvedplatform.geometry.RailSnapshot;
import java.util.*;

/** 与 Potato 1.1.2 一致：按实测弦长重采样至 0.25 米，并使用真实节点朝向作为端部切线。 */
public record PlatformRailSample(List<CurvedPlatformPlanner.Point> points,double startX,double startZ,double endX,double endZ) {
    public PlatformRailSample { points=List.copyOf(points); }
    public static PlatformRailSample empty(){return new PlatformRailSample(List.of(),0,0,0,0);}
    public RailSnapshot snapshot(int floor){
        if(points.size()<2||points.size()>8193)throw new IllegalArgumentException("invalid_rail");
        List<RailSnapshot.Point> raw=new ArrayList<>();double measured=0,y=points.get(0).y();
        for(var p:points){
            if(!Double.isFinite(p.x()+p.y()+p.z())||Math.abs(p.y()-y)>.001)throw new IllegalArgumentException("sloped_platform");
            if(!raw.isEmpty()){var previous=raw.get(raw.size()-1);double step=Math.hypot(p.x()-previous.x(),p.z()-previous.z());if(step<1e-10)continue;if(step>1||(measured+=step)>2048)throw new IllegalArgumentException("invalid_rail");}
            raw.add(new RailSnapshot.Point(p.x(),p.z(),measured));
        }
        if(raw.size()<2||measured<.05)throw new IllegalArgumentException("invalid_rail");
        List<RailSnapshot.Point> sampled=new ArrayList<>();int segment=1,intervals=Math.max(1,(int)Math.ceil((measured-1e-7)*4));
        for(int i=0;i<=intervals;i++){
            double distance=i==intervals?measured:i*.25;while(segment<raw.size()-1&&raw.get(segment).distance()<distance)segment++;
            var a=raw.get(segment-1);var b=raw.get(segment);double t=(distance-a.distance())/(b.distance()-a.distance());sampled.add(new RailSnapshot.Point(a.x()+(b.x()-a.x())*t,a.z()+(b.z()-a.z())*t,distance));
        }
        return new RailSnapshot(sampled,floor,heading(startX,startZ,sampled.get(0),sampled.get(1)),heading(endX,endZ,sampled.get(sampled.size()-2),sampled.get(sampled.size()-1)));
    }
    private static RailSnapshot.Heading heading(double x,double z,RailSnapshot.Point a,RailSnapshot.Point b){double sign=x*(b.x()-a.x())+z*(b.z()-a.z())<0?-1:1;return new RailSnapshot.Heading(x*sign,z*sign);}
}
