package com.fangsu.turnout.geometry;

import com.fangsu.turnout.TurnoutRailParams;
import java.util.ArrayList;
import java.util.List;

/** 护轨直接复制钢轨 OBJ，只改变摆放中心线和端部弯折，不另造断面或 UV。 */
public final class CheckRailBuilder {
    private CheckRailBuilder() { }
    public record Guard(List<TurnoutGeometry.Station> line,RailMesh mesh) { }
    public static void build(TurnoutGeometry.Sink sink,List<TurnoutGeometry.Station> straight,
                              List<TurnoutGeometry.Station> branch,TurnoutRailParams params,
                              FsrailParts.Asset asset,FrogBuilder.Plan frog,int sign) {
        guard(sink,straight,params,asset,frog.straightArc(),-sign,
                Math.max(TurnoutGeometry.CHECK_RAIL_LENGTH,frog.straightTo()-frog.straightFrom()));
        guard(sink,branch,params,asset,frog.branchArc(),sign,
                Math.max(TurnoutGeometry.CHECK_RAIL_LENGTH,frog.branchTo()-frog.branchFrom()));
    }
    public static void guard(TurnoutGeometry.Sink sink,List<TurnoutGeometry.Station> route,TurnoutRailParams params,
                                FsrailParts.Asset asset,double arc,int side,double length) {
        Guard guard=mesh(route,params,asset,arc,side,length);
        if(guard!=null)SingleTurnoutBuilder.emit(sink,guard.mesh(),asset,params);
    }
    /** 返回未提交的护轨，复合道岔还需让开其他进路的轮缘通道。 */
    public static Guard mesh(List<TurnoutGeometry.Station> route,TurnoutRailParams params,
                             FsrailParts.Asset asset,double arc,int side,double length) {
        length=Math.min(3.0D,length+0.30D);
        double from=Math.max(0,arc-length*0.5D),to=Math.min(TurnoutGeometry.totalArc(route),arc+length*0.5D);
        return meshRange(route,params,asset,from,to,side);
    }
    /** 合并后的区间只在真正的两端外撇，区间中间不再重复生成喇叭口。 */
    public static Guard meshRange(List<TurnoutGeometry.Station> route,TurnoutRailParams params,
                                  FsrailParts.Asset asset,double from,double to,int side) {
        from=Math.max(0,from);to=Math.min(TurnoutGeometry.totalArc(route),to);
        if(to-from<0.2D) return null;
        int count=Math.max(4,(int)Math.ceil((to-from)/0.05D));
        double flareLength=Math.min(0.30D,(to-from)*0.25D);
        double flareWidth=Math.min(0.10D,(to-from)*0.12D);
        List<TurnoutGeometry.Station> line=new ArrayList<>();
        for(int i=0;i<=count;i++) {
            double s=from+(to-from)*i/count;
            TurnoutGeometry.Station st=TurnoutGeometry.interpolate(route,s);
            // 短护轨也保留中间平直段，不能沿用长护轨的固定外撇量挤满小菱形。
            double flare=flareWidth*(1-TurnoutGeometry.smoothstep(Math.min(s-from,to-s)/flareLength));
            double shift=-side*(2*TurnoutGeometry.beam(params).headHalf()+TurnoutGeometry.FLANGEWAY+flare);
            double[] p=SingleTurnoutBuilder.modelPoint(route,s,-shift,0,0);
            line.add(new TurnoutGeometry.Station(p[0],p[1],p[2],st.tx(),st.tz(),st.tilt(),s-from));
        }
        return new Guard(List.copyOf(line),SingleTurnoutBuilder.railMesh(line,params,asset,side,0,to-from,0));
    }

    /** 钢轨完整断面的局部占用盒。护轨避让其他钢轨时不仅要让开轮缘槽，还要让开轨头、轨腰和轨脚。 */
    public static List<List<RailMesh.Plane>> clearance(List<TurnoutGeometry.Station> route,TurnoutRailParams p,
                                                       int side,double from,double to) {
        List<List<RailMesh.Plane>> out=new ArrayList<>();
        from=Math.max(0,from);to=Math.min(TurnoutGeometry.totalArc(route),to);if(to<=from)return out;
        int count=Math.max(1,(int)Math.ceil((to-from)/0.20D));
        double base=TurnoutGeometry.beamBaseY(p),top=TurnoutGeometry.beamTopY(p);
        for(int i=0;i<count;i++) {
            double a=from+(to-from)*i/count,b=from+(to-from)*(i+1)/count,mid=(a+b)*0.5D;
            double rawX=-side*TurnoutGeometry.railCenter(p),rawY=(base+top)*0.5D+p.modelYOffset();
            double[] center=SingleTurnoutBuilder.modelPoint(route,mid,rawX,rawY,p.railTopOffset());
            double[] pa=SingleTurnoutBuilder.modelPoint(route,a,rawX,rawY,p.railTopOffset());
            double[] pb=SingleTurnoutBuilder.modelPoint(route,b,rawX,rawY,p.railTopOffset());
            double length=Math.sqrt(Math.pow(pb[0]-pa[0],2)+Math.pow(pb[1]-pa[1],2)+Math.pow(pb[2]-pa[2],2));
            List<RailMesh.Plane> volume=new ArrayList<>();
            axis(volume,center,SingleTurnoutBuilder.modelNormal(route,mid,1,0,0),Math.max(TurnoutGeometry.beam(p).footHalf(),TurnoutGeometry.beam(p).headHalf())+0.004D);
            axis(volume,center,SingleTurnoutBuilder.modelNormal(route,mid,0,1,0),(top-base)*0.5D+0.004D);
            axis(volume,center,SingleTurnoutBuilder.modelNormal(route,mid,0,0,1),length*0.5D+0.02D);
            out.add(volume);
        }
        return out;
    }
    private static void axis(List<RailMesh.Plane> volume,double[] center,double[] axis,double half) {
        double dot=axis[0]*center[0]+axis[1]*center[1]+axis[2]*center[2];
        volume.add(new RailMesh.Plane(axis[0],axis[1],axis[2],half-dot));
        volume.add(new RailMesh.Plane(-axis[0],-axis[1],-axis[2],half+dot));
    }
}
