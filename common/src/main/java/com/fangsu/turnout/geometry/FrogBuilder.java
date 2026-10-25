package com.fangsu.turnout.geometry;

import com.fangsu.levelcrossing.geometry.PolyOps;
import com.fangsu.levelcrossing.geometry.Vec2;
import com.fangsu.turnout.TurnoutRailParams;
import java.util.ArrayList;
import java.util.List;

/** 辙叉沿用两根原 OBJ 钢轨，仅裁切真实轮缘槽；交叉顶面的重合部分由主轨负责。 */
public final class FrogBuilder {
    private FrogBuilder() { }

    public record Plan(double branchArc, double straightArc, double branchFrom, double branchTo,
                       double straightFrom, double straightTo, double crossX, double crossZ) { }

    public static Plan plan(List<TurnoutGeometry.Station> straight, List<TurnoutGeometry.Station> branch,
                             TurnoutRailParams params, int sign) {
        List<TurnoutGeometry.Station> a = railLine(straight, params, sign);
        List<TurnoutGeometry.Station> b = railLine(branch, params, -sign);
        for (int i=0; i+1<a.size(); i++) {
            TurnoutGeometry.Station a0=a.get(i), a1=a.get(i+1);
            Vec2 p=new Vec2(a0.x(),a0.z()), r=new Vec2(a1.x()-a0.x(),a1.z()-a0.z());
            for (int j=0; j+1<b.size(); j++) {
                TurnoutGeometry.Station b0=b.get(j), b1=b.get(j+1);
                if(Math.max(a0.x(),a1.x())<Math.min(b0.x(),b1.x())-1e-7D||
                        Math.max(b0.x(),b1.x())<Math.min(a0.x(),a1.x())-1e-7D||
                        Math.max(a0.z(),a1.z())<Math.min(b0.z(),b1.z())-1e-7D||
                        Math.max(b0.z(),b1.z())<Math.min(a0.z(),a1.z())-1e-7D) continue;
                if(Math.max(a0.x(),a1.x())<Math.min(b0.x(),b1.x())-1e-7D||
                        Math.max(b0.x(),b1.x())<Math.min(a0.x(),a1.x())-1e-7D||
                        Math.max(a0.z(),a1.z())<Math.min(b0.z(),b1.z())-1e-7D||
                        Math.max(b0.z(),b1.z())<Math.min(a0.z(),a1.z())-1e-7D) continue;
                Vec2 q=new Vec2(b0.x(),b0.z()), s=new Vec2(b1.x()-b0.x(),b1.z()-b0.z());
                double det=r.cross(s);
                if (Math.abs(det)<1e-12D) continue;
                double t=q.sub(p).cross(s)/det, u=q.sub(p).cross(r)/det;
                if (t < -1e-7D || t > 1.0000001D || u < -1e-7D || u > 1.0000001D) continue;
                double aa=a0.arc()+(a1.arc()-a0.arc())*t, ba=b0.arc()+(b1.arc()-b0.arc())*u;
                double sin=Math.abs(a0.tx()*b0.tz()-a0.tz()*b0.tx());
                // 以实际交角决定长度，避免只在交点附近挤出一个小铁块。
                double half=TurnoutGeometry.clamp((2*TurnoutGeometry.beam(params).headHalf()
                        +2*TurnoutGeometry.FLANGEWAY)/Math.max(0.03D,sin),0.45D,2.5D);
                // 急角短道岔也要给尖轨留位置，不能让辙叉的替换范围倒退到 toe。
                half=Math.min(half,Math.min(aa,ba)*0.45D);
                Vec2 cross=p.add(r.mul(t));
                return new Plan(ba,aa,Math.max(0,ba-half),Math.min(TurnoutGeometry.totalArc(branch),ba+half),
                        Math.max(0,aa-half),Math.min(TurnoutGeometry.totalArc(straight),aa+half),cross.x(),cross.z());
            }
        }
        return null;
    }

    /** 中心线在轨顶处的真实位置；弧长仍保留原股道参数，裁切端点与 OBJ 使用同一参数。 */
    private static List<TurnoutGeometry.Station> railLine(List<TurnoutGeometry.Station> route,
                                                          TurnoutRailParams params,int side) {
        List<TurnoutGeometry.Station> out=new ArrayList<>();
        if(route==null) return out;
        for(TurnoutGeometry.Station s:route) {
            double[] p=SingleTurnoutBuilder.modelPoint(route,s.arc(),-side*TurnoutGeometry.railCenter(params),
                    TurnoutGeometry.beamTopY(params)+params.modelYOffset(),params.railTopOffset());
            out.add(new TurnoutGeometry.Station(p[0],p[1],p[2],s.tx(),s.tz(),s.tilt(),s.arc()));
        }
        return out;
    }

    public static void emit(TurnoutGeometry.Sink sink,List<TurnoutGeometry.Station> straight,
                             List<TurnoutGeometry.Station> branch,Plan plan,TurnoutRailParams params,
                             FsrailParts.Asset asset,int sign) {
        RailMesh a=SingleTurnoutBuilder.railMesh(straight,params,asset,sign,plan.straightFrom(),plan.straightTo(),0);
        RailMesh b=SingleTurnoutBuilder.railMesh(branch,params,asset,-sign,plan.branchFrom(),plan.branchTo(),0);
        RailMesh.Surface sa=SingleTurnoutBuilder.cutSurface(straight,params,new ObjRailProfile(asset,-sign));
        RailMesh.Surface sb=SingleTurnoutBuilder.cutSurface(branch,params,new ObjRailProfile(asset,sign));
        // 自己的轮缘槽在本轨轨头外，不需要反复切自己；每根只让开对方的轮缘通道。
        for(List<RailMesh.Plane> volume:slots(branch,params,-sign,plan.branchFrom()-0.25D,plan.branchTo()+0.25D))
            a=a.subtract(volume,sa);
        for(List<RailMesh.Plane> volume:slots(straight,params,sign,plan.straightFrom()-0.25D,plan.straightTo()+0.25D))
            b=b.subtract(volume,sb);
        // 主轨已经开槽；副轨只移除与其真实顶面重合的面，不生成替代断面的白色铸块。
        b=removeCoveredTop(b,a);
        SingleTurnoutBuilder.emit(sink,a,asset,params);
        SingleTurnoutBuilder.emit(sink,b,asset,params);
    }

    public static List<List<RailMesh.Plane>> slots(List<TurnoutGeometry.Station> route,
                                                    TurnoutRailParams p,int side,double from,double to) {
        List<List<RailMesh.Plane>> out=new ArrayList<>();
        double lo=Math.max(0,from),hi=Math.min(TurnoutGeometry.totalArc(route),to);
        int count=Math.max(1,(int)Math.ceil((hi-lo)/0.25D));
        double center=side*(TurnoutGeometry.railCenter(p)-TurnoutGeometry.beam(p).headHalf()-TurnoutGeometry.FLANGEWAY*0.5D);
        double width=TurnoutGeometry.FLANGEWAY*0.5D;
        for(int i=0;i<count;i++) {
            TurnoutGeometry.Station a=TurnoutGeometry.interpolate(route,lo+(hi-lo)*i/count);
            TurnoutGeometry.Station b=TurnoutGeometry.interpolate(route,lo+(hi-lo)*(i+1)/count);
            double top=TurnoutGeometry.beamTopY(p)+p.modelYOffset();
            double[][] corners={SingleTurnoutBuilder.modelPoint(route,a.arc(),-center+width,top,p.railTopOffset()),
                    SingleTurnoutBuilder.modelPoint(route,b.arc(),-center+width,top,p.railTopOffset()),
                    SingleTurnoutBuilder.modelPoint(route,b.arc(),-center-width,top,p.railTopOffset()),
                    SingleTurnoutBuilder.modelPoint(route,a.arc(),-center-width,top,p.railTopOffset())};
            double[] up=SingleTurnoutBuilder.modelNormal(route,a.arc(),0,1,0);
            double nx=up[0],ny=up[1],nz=up[2];
            List<RailMesh.Plane> volume=new ArrayList<>();
            for(int j=0;j<4;j++) {
                double[] v=corners[j],w=corners[(j+1)%4];
                double dx=w[0]-v[0],dy=w[1]-v[1],dz=w[2]-v[2];
                double px=dy*nz-dz*ny,py=dz*nx-dx*nz,pz=dx*ny-dy*nx;
                double len=Math.max(1e-9D,Math.sqrt(px*px+py*py+pz*pz));px/=len;py/=len;pz/=len;
                volume.add(new RailMesh.Plane(px,py,pz,-px*v[0]-py*v[1]-pz*v[2]));
            }
            double[] bottom=SingleTurnoutBuilder.modelPoint(route,a.arc(),-center,top-p.grooveDepth(),p.railTopOffset());
            volume.add(new RailMesh.Plane(nx,ny,nz,-nx*bottom[0]-ny*bottom[1]-nz*bottom[2]));
            out.add(volume);
        }
        return out;
    }

    /** 只在共面时做顶面差集，保留副轨其余原始侧面、轨腰和轨底。 */
    public static RailMesh removeCoveredTop(RailMesh secondary,RailMesh primary) {
        RailMesh out=secondary;
        for(RailMesh.Face top:primary.faces) {
            if(top.a().ny()<0.999D||top.b().ny()<0.999D||top.c().ny()<0.999D) continue;
            List<Vec2> poly=new ArrayList<>(List.of(new Vec2(top.a().x(),top.a().z()),
                    new Vec2(top.b().x(),top.b().z()),new Vec2(top.c().x(),top.c().z())));
            if(Math.abs(PolyOps.area(poly))<PolyOps.AREA_EPS) continue;
            if(PolyOps.area(poly)<0) java.util.Collections.reverse(poly);
            List<RailMesh.Plane> prism=new ArrayList<>();
            for(int i=0;i<3;i++) {
                Vec2 a=poly.get(i),b=poly.get((i+1)%3);double dx=b.x()-a.x(),dz=b.z()-a.z();
                prism.add(new RailMesh.Plane(-dz,0,dx,dz*a.x()-dx*a.z()));
            }
            RailMesh next=new RailMesh();
            for(RailMesh.Face f:out.faces) {
                if(f.a().ny()>0.999D&&f.b().ny()>0.999D&&f.c().ny()>0.999D&&
                        Math.abs(f.a().y()-top.a().y())<1e-5D&&Math.abs(f.b().y()-top.a().y())<1e-5D&&
                        Math.abs(f.c().y()-top.a().y())<1e-5D) {
                    RailMesh face=new RailMesh();face.faces.add(f);next.append(face.subtract(prism,null));
                } else next.faces.add(f);
            }
            out=next;
        }
        return out;
    }
}
