package com.fangsu.turnout.geometry;

import java.util.ArrayList;
import java.util.List;
import static com.fangsu.turnout.geometry.TurnoutGeometry.*;

/** MTR 一条曲线可以含直线尾段；反向采样后这段成为多股进路共用的引入段。 */
public final class SharedRailLead {
    private static final double POSITION_EPS=1e-6D;
    private SharedRailLead() { }

    public static double length(List<Station> a,List<Station> b) {
        if(a==null||b==null||a.size()<2||b.size()<2||!same(a,b,0))return 0;
        double limit=Math.min(totalArc(a),totalArc(b));if(!Double.isFinite(limit))return 0;
        double last=0;
        while(last<limit) {
            double next=Math.min(limit,last+SAMPLE_STEP);
            if(!same(a,b,next)) {
                double lo=last,hi=next;
                for(int i=0;i<18;i++){double mid=(lo+hi)*0.5D;if(same(a,b,mid))lo=mid;else hi=mid;}
                // 普通曲线在起点就分离时，不把浮点近似的一小截当成独立引入段。
                return lo>=0.10D?lo:0;
            }
            last=next;
        }
        return last>=0.10D?last:0;
    }
    private static boolean same(List<Station> a,List<Station> b,double arc) {
        Station p=interpolate(a,arc),q=interpolate(b,arc);
        double dx=p.x()-q.x(),dy=p.y()-q.y(),dz=p.z()-q.z();
        return dx*dx+dy*dy+dz*dz<=POSITION_EPS*POSITION_EPS&&Math.abs(p.tilt()-q.tilt())<1e-5D&&p.tx()*q.tx()+p.tz()*q.tz()>0.99999D;
    }
    /** 保留世界/道岔局部坐标，仅将弧长原点移到实际分岔处。 */
    public static List<Station> tail(List<Station> line,double from) {
        List<Station> out=new ArrayList<>();
        for(Station s:slice(line,from,totalArc(line)))out.add(new Station(s.x(),s.y(),s.z(),s.tx(),s.tz(),s.tilt(),s.arc()-from));
        return out;
    }
}
