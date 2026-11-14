package com.fangsu.platform;

import dev.potato.curvedplatform.geometry.RailSnapshot;
import java.util.*;

/** 按实测弦长重采样至 0.25 米，并沿真实节点朝向将站台延伸至节点方块外缘。 */
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
        var first=raw.get(0);
        var last=raw.get(raw.size()-1);
        var start=heading(startX,startZ,first,raw.get(1));
        var end=heading(endX,endZ,raw.get(raw.size()-2),last);
        // MTR 的轨道端点在节点方块中心。斜向轨道沿切线走到第一个方块边界。
        double before=.5/Math.max(Math.abs(start.x()),Math.abs(start.z()));
        double after=.5/Math.max(Math.abs(end.x()),Math.abs(end.z()));
        if(measured+before+after<=RailSnapshot.MAX_LENGTH){
            raw.add(0,new RailSnapshot.Point(first.x()-start.x()*before,first.z()-start.z()*before,-before));
            raw.add(new RailSnapshot.Point(last.x()+end.x()*after,last.z()+end.z()*after,measured+after));
        }else{
            // 保持内核长度上限，也保留原先已支持的极限长度轨道。
            before=after=0;
        }
        double length=measured+before+after;
        List<RailSnapshot.Point> sampled=new ArrayList<>();int segment=1,intervals=Math.max(1,(int)Math.ceil((length-1e-7)*4));
        for(int i=0;i<=intervals;i++){
            double mileage=i==intervals?length:i*.25;
            double distance=i==intervals?measured+after:mileage-before;
            while(segment<raw.size()-1&&raw.get(segment).distance()<distance)segment++;
            var a=raw.get(segment-1);var b=raw.get(segment);double t=(distance-a.distance())/(b.distance()-a.distance());sampled.add(new RailSnapshot.Point(a.x()+(b.x()-a.x())*t,a.z()+(b.z()-a.z())*t,mileage));
        }
        return new RailSnapshot(sampled,floor,start,end);
    }
    private static RailSnapshot.Heading heading(double x,double z,RailSnapshot.Point a,RailSnapshot.Point b){double sign=x*(b.x()-a.x())+z*(b.z()-a.z())<0?-1:1;return new RailSnapshot.Heading(x*sign,z*sign);}
}
