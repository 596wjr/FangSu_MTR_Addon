package com.fangsu.turnout.geometry;

import com.fangsu.turnout.TurnoutRailParams;
import java.util.List;

/** 尖轨是完整长钢轨被纵向斜平面劈开后保留的内侧部分，断面不做缩放或压扁。 */
public final class BladeClipper {
    private BladeClipper() { }

    public static double heelArc(List<TurnoutGeometry.Station> route,List<TurnoutGeometry.Station> stock,
                                 TurnoutRailParams params,double limit) {
        double max=Math.min(TurnoutGeometry.totalArc(route),limit);
        double required=2*TurnoutGeometry.beam(params).footHalf()+TurnoutGeometry.FLANGEWAY;
        double heel=Math.min(TurnoutGeometry.BLADE_LENGTH,max);
        for(double arc=heel;arc<max;arc+=TurnoutGeometry.SAMPLE_STEP) {
            TurnoutGeometry.Station s=TurnoutGeometry.interpolate(route,arc);heel=arc;
            if(TurnoutGeometry.distanceToPolyline(s.x(),s.z(),stock)>=required) break;
        }
        return Math.max(0.1D,Math.min(heel,max));
    }

    public static RailMesh cut(RailMesh rail,ObjRailProfile profile,int side,double heel,double period) {
        // 刀尖：平面过轨头内侧边；跟端：到达轨底外沿，恢复原 OBJ 的完整断面。
        double start=Math.abs(profile.center)-profile.topHalf;
        double slope=(profile.footHalf+profile.topHalf)/Math.max(0.1D,heel);
        RailMesh.Plane knife=new RailMesh.Plane(-side,0,slope,start);
        return rail.clip(knife,true,new RailMesh.Surface("rail_side",v->profile.sideUv(v,period)));
    }
}
