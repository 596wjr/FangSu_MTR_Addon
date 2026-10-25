package com.fangsu.turnout.geometry;

import com.fangsu.levelcrossing.geometry.PolyOps;
import com.fangsu.levelcrossing.geometry.Vec2;
import com.fangsu.turnout.TurnoutRailParams;
import java.util.ArrayList;
import java.util.List;

/** 在 OBJ 的 z=0 平面切取真实钢轨断面，包括圆角、中间顶点和各面的 UV。 */
public final class ObjRailProfile {
    private static final double EPS=1e-6D;
    public record Edge(RailMesh.Vertex a,RailMesh.Vertex b,String material,double duDz,double dvDz) { }
    public final List<Edge> edges=new ArrayList<>();
    public final List<Vec2> ring=new ArrayList<>();
    public final double center,base,top,footHalf,headHalf,topHalf,webHalf,footTop,webTop;

    public ObjRailProfile(FsrailParts.Asset asset,int side) {
        FsrailParts.Part part=asset.railSide(side);
        if(part==null) throw new IllegalArgumentException("轨道 OBJ 没有钢轨对象");
        for(FsrailParts.Tri tri:part.tris()) {
            FsrailParts.Vertex[] src={tri.a(),tri.b(),tri.c()};
            List<RailMesh.Vertex> hits=new ArrayList<>();
            double du=0,dv=0;
            for(int i=0;i<3;i++) {
                FsrailParts.Vertex a=src[i],b=src[(i+1)%3];
                if(Math.abs(a.x()-b.x())<EPS&&Math.abs(a.y()-b.y())<EPS&&Math.abs(a.z()-b.z())>EPS) {
                    du=(b.u()-a.u())/(b.z()-a.z());dv=(b.v()-a.v())/(b.z()-a.z());
                }
                if(a.z()*b.z()<0) {
                    double t=-a.z()/(b.z()-a.z());
                    hits.add(vertex(a).mix(vertex(b),t));
                }
            }
            if(hits.size()==2) edges.add(new Edge(hits.get(0),hits.get(1),tri.material(),du,dv));
        }
        if(edges.size()<3) throw new IllegalArgumentException("轨道 OBJ 没有闭合的纵向断面");
        List<Edge> left=new ArrayList<>(edges);
        Edge first=left.remove(0);ring.add(new Vec2(first.a.x(),first.a.y()));
        Vec2 current=new Vec2(first.b.x(),first.b.y());
        while(current.sub(ring.get(0)).length()>EPS&&!left.isEmpty()) {
            ring.add(current);int next=-1;Vec2 end=null;
            for(int i=0;i<left.size();i++) {
                Edge e=left.get(i);Vec2 a=new Vec2(e.a.x(),e.a.y()),b=new Vec2(e.b.x(),e.b.y());
                if(a.sub(current).length()<EPS) {next=i;end=b;break;}
                if(b.sub(current).length()<EPS) {next=i;end=a;break;}
            }
            if(next<0) throw new IllegalArgumentException("轨道 OBJ 断面不连续");
            left.remove(next);current=end;
        }
        if(current.sub(ring.get(0)).length()>EPS) throw new IllegalArgumentException("轨道 OBJ 断面未闭合");
        double lo=Double.POSITIVE_INFINITY,hi=-lo;
        for(Vec2 p:ring){lo=Math.min(lo,p.z());hi=Math.max(hi,p.z());}
        base=lo;top=hi;
        double x0=Double.POSITIVE_INFINITY,x1=-x0;
        for(Vec2 p:ring) if(p.z()>top-EPS){x0=Math.min(x0,p.x());x1=Math.max(x1,p.x());}
        center=(x0+x1)*0.5D;topHalf=(x1-x0)*0.5D;
        double fy=base,wy=top,wh=0,longest=0;
        for(Vec2 p:ring) {
            double low=Double.POSITIVE_INFINITY,high=-low;
            for(Edge e:edges) if(Math.abs(e.a.x()-p.x())<EPS&&Math.abs(e.b.x()-p.x())<EPS) {
                low=Math.min(low,Math.min(e.a.y(),e.b.y()));high=Math.max(high,Math.max(e.a.y(),e.b.y()));
            }
            if(high-low>longest){longest=high-low;fy=low;wy=high;wh=Math.abs(p.x()-center);}
        }
        // MTR 普通轨道的轨腰略带斜度，没有竖直边；从真实截面最窄区间求元数据。
        if(longest<EPS) {
            double narrow=Double.POSITIVE_INFINITY;
            List<double[]> widths=new ArrayList<>();
            for(Vec2 p:ring) {
                double xMin=Double.POSITIVE_INFINITY,xMax=-xMin;
                for(int i=0;i<ring.size();i++) {
                    Vec2 a=ring.get(i),b=ring.get((i+1)%ring.size());
                    if(Math.abs(b.z()-a.z())<EPS||p.z()<Math.min(a.z(),b.z())-EPS||p.z()>Math.max(a.z(),b.z())+EPS) continue;
                    double x=a.x()+(b.x()-a.x())*(p.z()-a.z())/(b.z()-a.z());
                    xMin=Math.min(xMin,x);xMax=Math.max(xMax,x);
                }
                double half=(xMax-xMin)*0.5D;
                if(Double.isFinite(half)&&half>EPS) {widths.add(new double[]{p.z(),half});narrow=Math.min(narrow,half);}
            }
            fy=Double.POSITIVE_INFINITY;wy=-fy;wh=narrow;
            for(double[] w:widths) if(w[1]<=narrow*2) {fy=Math.min(fy,w[0]);wy=Math.max(wy,w[0]);}
        }
        footTop=fy;webTop=wy;webHalf=wh;
        double fh=0,hh=0;
        for(Vec2 p:ring){fh=Math.max(fh,Math.abs(p.x()-center));if(p.z()>=webTop-EPS)hh=Math.max(hh,Math.abs(p.x()-center));}
        footHalf=fh;headHalf=hh;
    }

    public static RailMesh.Vertex vertex(FsrailParts.Vertex v) {
        return new RailMesh.Vertex(v.x(),v.y(),v.z(),v.nx(),v.ny(),v.nz(),v.u(),v.v());
    }

    /** 裁切新面从相邻的原 rail_side 面取 UV，不再给钢轨套世界坐标或 0..1 的任意 UV。 */
    public RailMesh.Vertex sideUv(RailMesh.Vertex p,double period) {
        Edge best=null;double tBest=0,dist=Double.POSITIVE_INFINITY;
        for(Edge e:edges) {
            if(!e.material.contains("side")) continue;
            double dx=e.b.x()-e.a.x(),dy=e.b.y()-e.a.y();
            double t=TurnoutGeometry.clamp(((p.x()-e.a.x())*dx+(p.y()-e.a.y())*dy)/Math.max(1e-12D,dx*dx+dy*dy),0,1);
            double d=Math.hypot(p.x()-e.a.x()-dx*t,p.y()-e.a.y()-dy*t);
            if(d<dist){dist=d;best=e;tBest=t;}
        }
        if(best==null) best=edges.get(0);
        RailMesh.Vertex sample=best.a.mix(best.b,tBest);
        double z=p.z()-(Math.floor(p.z()/period)+0.5D)*period;
        return new RailMesh.Vertex(p.x(),p.y(),p.z(),p.nx(),p.ny(),p.nz(),
                sample.u()+best.duDz*z,sample.v()+best.dvDz*z);
    }

    public void cap(RailMesh mesh,double arc,boolean start,double period) {
        for(int[] tri:PolyOps.triangulate(ring)) {
            RailMesh.Vertex[] v=new RailMesh.Vertex[3];
            for(int i=0;i<3;i++) {
                Vec2 p=ring.get(tri[i]);v[i]=sideUv(new RailMesh.Vertex(p.x(),p.z(),arc,0,0,start?-1:1,0,0),period);
            }
            mesh.triangle("rail_side",v[0],v[start?2:1],v[start?1:2]);
        }
    }

    public TurnoutRailParams apply(TurnoutRailParams p) {
        TurnoutRailParams.BeamProfile beam=new TurnoutRailParams.BeamProfile(base,footHalf,footTop,webHalf,
                webTop,headHalf,topHalf,top,Math.abs(center));
        return new TurnoutRailParams(p.railId(),p.modelResource(),p.textureResource(),p.repeatInterval(),p.flipV(),
                p.color(),p.modelYOffset(),p.gaugeMm(),p.gaugeBlocks(),Math.abs(center)+headHalf,
                Math.abs(center)-headHalf,p.grooveDepth(),top,headHalf*2,p.halfWidth(),beam,
                p.railTopOffset(),Math.abs(center)-p.gaugeBlocks()*0.5D);
    }
}
