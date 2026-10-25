package com.fangsu.turnout.geometry;

import com.fangsu.Main;
import com.fangsu.levelcrossing.LevelCrossingStyles;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.turnout.TurnoutRailParams;
import java.util.ArrayList;
import java.util.List;

/** 所有钢轨部位共用原 OBJ：保持横向/高度/UV，只沿真实轨道弯曲和按切割面裁切。 */
public final class SingleTurnoutBuilder {
    private static final double EPS=1e-7D;
    private SingleTurnoutBuilder() { }

    public static TurnoutMesh build(List<TurnoutGeometry.Station> straight,List<TurnoutGeometry.Station> branch,
                                    TurnoutRailParams input,int sign,boolean sampled) {
        FsrailParts.Asset asset=FsrailParts.load(input.modelResource());
        if(asset==null||asset.rail()==null) {
            Main.LOGGER.warn("[turnout] 找不到钢轨 OBJ，无法生成与普通轨道一致的道岔：{}",input.modelResource());
            return TurnoutMesh.EMPTY;
        }
        ObjRailProfile profile=new ObjRailProfile(asset,1);
        TurnoutRailParams params=profile.apply(input);
        boolean hasBranch=branch!=null&&branch.size()>=2;
        List<TurnoutGeometry.Station> lead=List.of();
        if(hasBranch) {
            double shared=SharedRailLead.length(straight,branch);
            if(shared>0) {
                if(Math.min(TurnoutGeometry.totalArc(straight),TurnoutGeometry.totalArc(branch))-shared<0.10D) {
                    // 两条进路完全重合时只保留较长的一条，不在同一根直轨上叠出假尖轨。
                    if(TurnoutGeometry.totalArc(branch)>TurnoutGeometry.totalArc(straight))straight=branch;
                    branch=List.of();hasBranch=false;
                } else {
                    lead=TurnoutGeometry.slice(straight,0,shared);
                    straight=SharedRailLead.tail(straight,shared);branch=SharedRailLead.tail(branch,shared);
                }
            }
        }
        if(hasBranch&&!lead.isEmpty()) {
            // 共用引入段也可能弯曲，分岔左右必须相对余下主股判断，不能沿用标记处的全局 Z 符号。
            double spread=0;
            for(TurnoutGeometry.Station station:branch) {
                double offset=lateral(nearest(straight,station.x(),station.z()),station);
                if(Math.abs(offset)>Math.abs(spread))spread=offset;
            }
            if(Math.abs(spread)>1e-6D)sign=spread>0?1:-1;
        }
        FrogBuilder.Plan frog=hasBranch?FrogBuilder.plan(straight,branch,params,sign):null;
        double sh=hasBranch?BladeClipper.heelArc(straight,branch,params,
                frog==null?TurnoutGeometry.totalArc(straight)*0.7D:frog.straightFrom()*0.8D):0;
        double bh=hasBranch?BladeClipper.heelArc(branch,straight,params,
                frog==null?TurnoutGeometry.totalArc(branch)*0.7D:frog.branchFrom()*0.8D):0;
        TurnoutGeometry.Sink fixed=sink(asset),sb=sink(asset),bb=sink(asset);
        if(!lead.isEmpty()) {
            if(enabled("rail"))for(int side:new int[]{-1,1})emitRail(fixed,lead,params,asset,side,0,TurnoutGeometry.totalArc(lead),0);
            furniture(fixed,lead,List.of(),params,asset,sign,0,0);
        }
        if(enabled("rail")) {
            emitRail(fixed,straight,params,asset,-sign,0,TurnoutGeometry.totalArc(straight),0);
            if(hasBranch) {
                emitRail(fixed,branch,params,asset,sign,0,TurnoutGeometry.totalArc(branch),0);
                closure(fixed,straight,params,asset,sign,sh,frog==null?-1:frog.straightFrom(),frog==null?-1:frog.straightTo());
                closure(fixed,branch,params,asset,-sign,bh,frog==null?-1:frog.branchFrom(),frog==null?-1:frog.branchTo());
            } else emitRail(fixed,straight,params,asset,sign,0,TurnoutGeometry.totalArc(straight),0);
        }
        if(hasBranch&&enabled("blade")) {
            emitRail(sb,straight,params,asset,sign,0,sh,sh);
            emitRail(bb,branch,params,asset,-sign,0,bh,bh);
        }
        if(frog!=null) {
            if(enabled("frog")) FrogBuilder.emit(fixed,straight,branch,frog,params,asset,sign);
            if(enabled("check")) CheckRailBuilder.build(fixed,straight,branch,params,asset,frog,sign);
        }
        furniture(fixed,straight,hasBranch?branch:List.of(),params,asset,sign,sh,bh);
        double travel=TurnoutGeometry.clamp(params.gaugeBlocks()*TurnoutGeometry.BLADE_TRAVEL_PER_GAUGE,0.02D,0.12D);
        double angle=hasBranch?Math.asin(Math.min(0.5D,travel/Math.max(0.1D,sh))):0;
        TurnoutMesh.BladeMotion sm=hasBranch?motion(straight,params,sign,sh,1):null;
        TurnoutMesh.BladeMotion bm=hasBranch?motion(branch,params,-sign,bh,
                Math.asin(Math.min(0.5D,travel/Math.max(0.1D,bh)))/Math.max(EPS,angle)):null;
        return new TurnoutMesh(fixed.toModel(),sb.toModel(),bb.toModel(),sm,bm,angle,sign,sampled);
    }

    public static TurnoutGeometry.Sink sink(FsrailParts.Asset asset) {
        return new TurnoutGeometry.Sink(asset.textureOf("rail_top")!=null?asset.textureOf("rail_top"):LevelCrossingStyles.railTopTexture(),
                asset.textureOf("rail_side")!=null?asset.textureOf("rail_side"):LevelCrossingStyles.railSideTexture());
    }
    private static boolean enabled(String part) {
        String debug=System.getProperty("fangsu.turnout.debugPart","all");return "all".equals(debug)||part.equals(debug);
    }
    private static void closure(TurnoutGeometry.Sink out,List<TurnoutGeometry.Station> route,TurnoutRailParams p,
                                FsrailParts.Asset asset,int side,double heel,double from,double to) {
        if(from<0) emitRail(out,route,p,asset,side,heel,TurnoutGeometry.totalArc(route),0);
        else {
            emitRail(out,route,p,asset,side,heel,from,0);
            emitRail(out,route,p,asset,side,to,TurnoutGeometry.totalArc(route),0);
        }
    }

    /** 跟普通轨道相同的 OBJ 坐标，不做 SourceProfile 的宽度/高度归一化。 */
    public static RailMesh railMesh(List<TurnoutGeometry.Station> route,TurnoutRailParams p,
                                    FsrailParts.Asset asset,int side,double from,double to,double heel) {
        RailMesh local=new RailMesh();
        if(to-from<EPS) return local;
        // MTR 模型 +X 是行进方向右侧；本地 L 是左法向，二者符号相反。
        FsrailParts.Part part=asset.railSide(-side);
        ObjRailProfile profile=new ObjRailProfile(asset,-side);
        double period=period(route,p);
        for(double tile=Math.floor(from/period)*period;tile<to-EPS;tile+=period) {
            double lo=Math.max(from,tile),hi=Math.min(to,tile+period),origin=tile+period*0.5D;
            for(FsrailParts.Tri tri:part.tris()) {
                // 原素材的纵向端盖不能重复铺进钢轨内部，首尾由真实断面统一封口。
                if(Math.max(tri.a().z(),Math.max(tri.b().z(),tri.c().z()))-
                        Math.min(tri.a().z(),Math.min(tri.b().z(),tri.c().z()))<1e-5D) continue;
                List<RailMesh.Vertex> poly=new ArrayList<>(List.of(ObjRailProfile.vertex(tri.a()),ObjRailProfile.vertex(tri.b()),ObjRailProfile.vertex(tri.c())));
                poly=clipArc(poly,lo-origin,true);poly=clipArc(poly,hi-origin,false);
                for(int i=1;i+1<poly.size();i++) local.triangle(tri.material(),
                        along(poly.get(0),origin),along(poly.get(i),origin),along(poly.get(i+1),origin));
            }
        }
        profile.cap(local,from,true,period);profile.cap(local,to,false,period);
        if(heel>0) local=BladeClipper.cut(local,profile,-side,heel,period);
        return local.transform(v->{
            TurnoutGeometry.Station st=TurnoutGeometry.interpolate(route,v.z());
            double x=v.x();
            if(heel>0) x+=side*0.0015D*(1-TurnoutGeometry.clamp(v.z()/heel,0,1));
            double[] pos=modelPoint(route,v.z(),x,v.y()+p.modelYOffset(),p.railTopOffset());
            double[] n=modelNormal(route,v.z(),v.nx(),v.ny(),v.nz());
            return new RailMesh.Vertex(pos[0],pos[1],pos[2],n[0],n[1],n[2],v.u(),v.v());
        });
    }
    public static void emitRail(TurnoutGeometry.Sink out,List<TurnoutGeometry.Station> route,TurnoutRailParams p,
                                FsrailParts.Asset asset,int side,double from,double to,double heel) {
        emit(out,railMesh(route,p,asset,side,from,to,heel),asset,p);
    }
    public static void emit(TurnoutGeometry.Sink sink,RailMesh mesh,FsrailParts.Asset asset,TurnoutRailParams p) {
        for(RailMesh.Face face:mesh.faces) {
            RailMesh.Vertex[] vs={face.a(),face.b(),face.c()};
            double[][] pos=new double[3][];float[][] uv=new float[3][],norm=new float[3][];
            for(int i=0;i<3;i++) {
                RailMesh.Vertex v=vs[i];pos[i]=new double[]{v.x(),v.y(),v.z()};
                uv[i]=new float[]{(float)v.u(),(float)(p.flipV()?1-v.v():v.v())};
                norm[i]=new float[]{(float)v.nx(),(float)v.ny(),(float)v.nz()};
            }
            sink.emitRaw(sink.bucket(asset.textureOf(face.material())),pos,uv,norm);
        }
    }
    private static RailMesh.Vertex along(RailMesh.Vertex v,double origin) {return v.at(v.x(),v.y(),v.z()+origin);}
    private static List<RailMesh.Vertex> clipArc(List<RailMesh.Vertex> poly,double edge,boolean greater) {
        List<RailMesh.Vertex> out=new ArrayList<>();
        for(int i=0;i<poly.size();i++) {
            RailMesh.Vertex a=poly.get(i),b=poly.get((i+1)%poly.size());
            double da=greater?a.z()-edge:edge-a.z(),db=greater?b.z()-edge:edge-b.z();
            if(da>=0) out.add(a);
            if((da>=0)!=(db>=0)) out.add(a.mix(b,da/(da-db)));
        }
        return out;
    }
    public static double period(List<TurnoutGeometry.Station> route,TurnoutRailParams p) {
        double length=TurnoutGeometry.totalArc(route),requested=Math.max(0.05D,p.repeatInterval());
        // 与 NTE 一样把重复次数取整，使起终点不残留半个周期。
        return length/Math.max(1,Math.round(length/requested));
    }
    public static TurnoutGeometry.Station nearest(List<TurnoutGeometry.Station> line,double x,double z) {
        double best=Double.POSITIVE_INFINITY,arc=0;
        for(int i=0;i+1<line.size();i++) {
            TurnoutGeometry.Station a=line.get(i),b=line.get(i+1);double dx=b.x()-a.x(),dz=b.z()-a.z();
            double t=TurnoutGeometry.clamp(((x-a.x())*dx+(z-a.z())*dz)/Math.max(EPS,dx*dx+dz*dz),0,1);
            double ex=x-a.x()-dx*t,ez=z-a.z()-dz*t,d=ex*ex+ez*ez;
            if(d<best){best=d;arc=a.arc()+(b.arc()-a.arc())*t;}
        }
        return TurnoutGeometry.interpolate(line,arc);
    }
    public static RailMesh.Surface cutSurface(List<TurnoutGeometry.Station> route,TurnoutRailParams p,
                                              ObjRailProfile profile) {
        double period=period(route,p);
        return new RailMesh.Surface("rail_side",v->{
            TurnoutGeometry.Station s=nearest(route,v.x(),v.z());
            // 用同一模型基的逆变换取新切面的 UV。
            double[] right=modelNormal(route,s.arc(),1,0,0),normal=modelNormal(route,s.arc(),0,1,0);
            double dx=v.x()-s.x(),dy=v.y()-s.y()-p.railTopOffset(),dz=v.z()-s.z();
            double x=dx*right[0]+dy*right[1]+dz*right[2];
            double y=dx*normal[0]+dy*normal[1]+dz*normal[2]-p.modelYOffset();
            RailMesh.Vertex uv=profile.sideUv(new RailMesh.Vertex(x,y,s.arc(),0,0,0,0,0),period);
            return new RailMesh.Vertex(v.x(),v.y(),v.z(),v.nx(),v.ny(),v.nz(),uv.u(),uv.v());
        });
    }
    /** 与普通轨道一致的右手模型基，包含纵坡与横坡；模型断面在端点不再竖直错接。 */
    public static double[] modelNormal(List<TurnoutGeometry.Station> route,double arc,double x,double y,double z) {
        TurnoutGeometry.Station s=TurnoutGeometry.interpolate(route,arc);
        TurnoutGeometry.Station a=TurnoutGeometry.interpolate(route,Math.max(0,arc-0.0005D));
        TurnoutGeometry.Station b=TurnoutGeometry.interpolate(route,Math.min(TurnoutGeometry.totalArc(route),arc+0.0005D));
        double pitch=Math.atan2(b.y()-a.y(),Math.hypot(b.x()-a.x(),b.z()-a.z()));
        double cp=Math.cos(pitch),sp=Math.sin(pitch),cr=Math.cos(s.tilt()),sr=Math.sin(s.tilt());
        double rx=-s.lateralX(),rz=-s.lateralZ(),ux=-s.tx()*sp,uy=cp,uz=-s.tz()*sp;
        double r=x*cr-y*sr,u=x*sr+y*cr;
        double nx=rx*r+ux*u+s.tx()*cp*z,ny=uy*u+sp*z,nz=rz*r+uz*u+s.tz()*cp*z;
        double len=Math.max(EPS,Math.sqrt(nx*nx+ny*ny+nz*nz));
        return new double[]{nx/len,ny/len,nz/len};
    }
    public static double[] modelPoint(List<TurnoutGeometry.Station> route,double arc,double x,double y,double lift) {
        TurnoutGeometry.Station s=TurnoutGeometry.interpolate(route,arc);
        double[] right=modelNormal(route,arc,1,0,0),up=modelNormal(route,arc,0,1,0);
        return new double[]{s.x()+right[0]*x+up[0]*y,s.y()+right[1]*x+up[1]*y+lift,s.z()+right[2]*x+up[2]*y};
    }
    private static TurnoutMesh.BladeMotion motion(List<TurnoutGeometry.Station> route,TurnoutRailParams p,int side,double heel,double ratio) {
        double[] pos=modelPoint(route,heel,-side*TurnoutGeometry.railCenter(p),
                TurnoutGeometry.beamTopY(p)+p.modelYOffset(),p.railTopOffset());
        return new TurnoutMesh.BladeMotion(pos[0],pos[1],pos[2],(float)(-side*ratio));
    }

    /** 与某一枕木横断面的真实交点；最近点投影会把弯曲远端错误地算入加长量。 */
    public static TurnoutGeometry.Station crossSection(List<TurnoutGeometry.Station> route,TurnoutGeometry.Station s) {
        for(int i=0;i+1<route.size();i++) {
            TurnoutGeometry.Station a=route.get(i),b=route.get(i+1);
            double da=(a.x()-s.x())*s.tx()+(a.z()-s.z())*s.tz(),db=(b.x()-s.x())*s.tx()+(b.z()-s.z())*s.tz();
            if(da*db>0||Math.abs(db-da)<EPS) continue;
            double t=TurnoutGeometry.clamp(-da/(db-da),0,1);
            return TurnoutGeometry.interpolate(route,a.arc()+(b.arc()-a.arc())*t);
        }
        return null;
    }
    public static double lateral(TurnoutGeometry.Station s,TurnoutGeometry.Station other) {
        return other==null?Double.POSITIVE_INFINITY:(other.x()-s.x())*s.lateralX()+(other.z()-s.z())*s.lateralZ();
    }
    private static void furniture(TurnoutGeometry.Sink out,List<TurnoutGeometry.Station> straight,
                                   List<TurnoutGeometry.Station> branch,TurnoutRailParams p,FsrailParts.Asset asset,
                                   int sign,double sh,double bh) {
        if(asset.sleeper()==null) return;
        if(asset.source().endsWith("mtr_siding.obj")) {
            depotFurniture(out,straight,p,asset,-sign,sh);
            if(branch.size()>=2) depotFurniture(out,branch,p,asset,sign,bh);
            return;
        }
        double min=Double.POSITIVE_INFINITY,max=-min;
        for(FsrailParts.Tri f:asset.sleeper().tris()) for(FsrailParts.Vertex v:List.of(f.a(),f.b(),f.c())) {min=Math.min(min,-v.x());max=Math.max(max,-v.x());}
        double steel=TurnoutGeometry.railCenter(p)+TurnoutGeometry.beam(p).footHalf();
        // 轨脚已可分置两排枕木时立即拆分，不把整根枕木一直拉到两个普通枕木宽。
        double split=2*steel+0.08D;
        double step=period(straight,p);
        for(double arc=step*0.5D;arc<TurnoutGeometry.totalArc(straight);arc+=step) {
            TurnoutGeometry.Station s=TurnoutGeometry.interpolate(straight,arc),other=crossSection(branch,s);
            double gap=sign*lateral(s,other),lo=min,hi=max;
            boolean shared=other!=null&&gap>=0&&gap<split;
            if(shared) {
                // 原枕木两端余量保持不变，只覆盖同一横断面上实际存在的两根最外侧钢轨。
                double projection=other.lateralX()*s.lateralX()+other.lateralZ()*s.lateralZ();
                double edge=gap+TurnoutGeometry.railCenter(p)*Math.abs(projection)+TurnoutGeometry.beam(p).footHalf();
                double overhang=(max-min)*0.5D-steel;
                if(sign>0) hi=Math.max(max,edge+overhang);else lo=Math.min(min,-edge-overhang);
            } else if(other!=null&&gap<max-min) {
                if(sign>0) hi=Math.max(steel+0.02D,Math.min(max,gap*0.5D));
                else lo=-Math.max(steel+0.02D,Math.min(-min,gap*0.5D));
            }
            if(enabled("sleeper")) emitSleeper(out,asset,straight,s,p,min,max,lo,hi);
            if(enabled("spike")) {
                fasteners(out,asset,straight,s,p,-sign);
                if(arc>=sh) fasteners(out,asset,straight,s,p,sign);
                if(shared) {
                    fasteners(out,asset,branch,other,p,sign);
                    if(other.arc()>=bh) fasteners(out,asset,branch,other,p,-sign);
                }
            }
        }
        if(branch.size()<2) return;
        step=period(branch,p);
        for(double arc=step*0.5D;arc<TurnoutGeometry.totalArc(branch);arc+=step) {
            TurnoutGeometry.Station s=TurnoutGeometry.interpolate(branch,arc),other=crossSection(straight,s);
            double gap=Math.abs(lateral(s,other));
            if(other==null||gap>=split) {
                double lo=min,hi=max;
                if(other!=null&&gap<max-min) {
                    if(sign>0) lo=-Math.max(steel+0.02D,Math.min(-min,gap*0.5D));
                    else hi=Math.max(steel+0.02D,Math.min(max,gap*0.5D));
                }
                if(enabled("sleeper")) emitSleeper(out,asset,branch,s,p,min,max,lo,hi);
            }
            // 即使共用加长枕木，也保留曲股上实际固定钢轨下的扣件。
            if(enabled("spike")&&(other==null||gap>=split)) {
                fasteners(out,asset,branch,s,p,sign);
                if(arc>=bh) fasteners(out,asset,branch,s,p,-sign);
            }
        }
    }
    public static void emitSleeper(TurnoutGeometry.Sink sink,FsrailParts.Asset asset,List<TurnoutGeometry.Station> route,TurnoutGeometry.Station s,
                                     TurnoutRailParams p,double min,double max,double lo,double hi) {
        emit(sink,sleeperMesh(asset,route,s,p,min,max,lo,hi),asset,p);
    }
    /** 先生成网格，复合道岔才能在提交前消除交叉枕木的共面覆盖。 */
    public static RailMesh sleeperMesh(FsrailParts.Asset asset,List<TurnoutGeometry.Station> route,TurnoutGeometry.Station s,
                                        TurnoutRailParams p,double min,double max,double lo,double hi) {
        RailMesh mesh=new RailMesh();
        for(FsrailParts.Tri f:asset.sleeper().tris()) {
            FsrailParts.Vertex[] vs={f.a(),f.b(),f.c()};RailMesh.Vertex[] out=new RailMesh.Vertex[3];
            double du=0,dv=0;
            for(int i=0;i<3;i++) {
                FsrailParts.Vertex a=vs[i],b=vs[(i+1)%3];
                if(Math.abs(a.y()-b.y())<EPS&&Math.abs(a.z()-b.z())<EPS&&Math.abs(a.x()-b.x())>EPS) {
                    du=(b.u()-a.u())/(b.x()-a.x());dv=(b.v()-a.v())/(b.x()-a.x());
                }
            }
            for(int i=0;i<3;i++) {
                FsrailParts.Vertex v=vs[i];double x=-(lo+(-v.x()-min)/(max-min)*(hi-lo));
                double[] pos=modelPoint(route,s.arc(),x,v.y()+p.modelYOffset(),0),n=modelNormal(route,s.arc(),v.nx(),v.ny(),v.nz());
                double[] forward=modelNormal(route,s.arc(),0,0,1);
                out[i]=new RailMesh.Vertex(pos[0]+forward[0]*v.z(),pos[1]+forward[1]*v.z(),pos[2]+forward[2]*v.z(),n[0],n[1],n[2],
                        v.u()+du*(x-v.x()),v.v()+dv*(x-v.x()));
            }
            mesh.triangle(f.material(),out[0],out[1],out[2]);
        }
        return mesh;
    }
    public static void fasteners(TurnoutGeometry.Sink sink,FsrailParts.Asset asset,List<TurnoutGeometry.Station> route,
                                   TurnoutGeometry.Station s,TurnoutRailParams p,int side) {
        emitPartSide(sink,asset,asset.spikes(),route,s,p,side);
    }
    /** 车厂轨道的原模型是独立支座，不横向拉伸成贯通枕木。 */
    private static void depotFurniture(TurnoutGeometry.Sink out,List<TurnoutGeometry.Station> route,
                                        TurnoutRailParams p,FsrailParts.Asset asset,int stockSide,double heel) {
        double step=period(route,p);
        for(double arc=step*0.5D;arc<TurnoutGeometry.totalArc(route);arc+=step) {
            TurnoutGeometry.Station s=TurnoutGeometry.interpolate(route,arc);
            if(enabled("sleeper")) {
                emitPartSide(out,asset,asset.sleeper(),route,s,p,stockSide);
                if(arc>=heel) emitPartSide(out,asset,asset.sleeper(),route,s,p,-stockSide);
            }
            if(enabled("spike")) {
                fasteners(out,asset,route,s,p,stockSide);
                if(arc>=heel) fasteners(out,asset,route,s,p,-stockSide);
            }
        }
    }
    public static void emitPartSide(TurnoutGeometry.Sink sink,FsrailParts.Asset asset,FsrailParts.Part part,
                                      List<TurnoutGeometry.Station> route,TurnoutGeometry.Station s,TurnoutRailParams p,int side) {
        emit(sink,partSideMesh(part,route,s,p,side),asset,p);
    }
    public static RailMesh partSideMesh(FsrailParts.Part part,List<TurnoutGeometry.Station> route,
                                         TurnoutGeometry.Station s,TurnoutRailParams p,int side) {
        RailMesh mesh=new RailMesh();
        if(part==null) return mesh;
        for(FsrailParts.Tri f:part.tris()) {
            if((f.a().x()+f.b().x()+f.c().x())*side>=0) continue;
            FsrailParts.Vertex[] vs={f.a(),f.b(),f.c()};RailMesh.Vertex[] v=new RailMesh.Vertex[3];
            for(int i=0;i<3;i++) {
                FsrailParts.Vertex src=vs[i];double[] pos=modelPoint(route,s.arc(),src.x(),src.y()+p.modelYOffset(),0);
                double[] n=modelNormal(route,s.arc(),src.nx(),src.ny(),src.nz());
                double[] forward=modelNormal(route,s.arc(),0,0,1);
                v[i]=new RailMesh.Vertex(pos[0]+forward[0]*src.z(),pos[1]+forward[1]*src.z(),pos[2]+forward[2]*src.z(),n[0],n[1],n[2],src.u(),src.v());
            }
            mesh.triangle(f.material(),v[0],v[1],v[2]);
        }
        return mesh;
    }
}
