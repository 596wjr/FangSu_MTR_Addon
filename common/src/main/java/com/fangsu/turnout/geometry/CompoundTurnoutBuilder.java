package com.fangsu.turnout.geometry;

import com.fangsu.turnout.TurnoutBinding;
import com.fangsu.turnout.TurnoutKind;
import com.fangsu.turnout.TurnoutRailParams;
import net.minecraft.core.BlockPos;
import java.util.*;
import static com.fangsu.turnout.geometry.SingleTurnoutBuilder.*;
import static com.fangsu.turnout.geometry.TurnoutGeometry.*;

/** 复合道岔按真实节点拆为共用轨段；岔尖、四个钢轨交点和扣件均复用原 OBJ。 */
public final class CompoundTurnoutBuilder {
    private CompoundTurnoutBuilder() { }
    private record End(int route,boolean reversed,List<Station> line) { }
    private record Rail(int route,int side) { }
    private record Crossing(Rail a,double aa,Rail b,double ba,double half) { }
    private record Gap(double from,double to) { }
    private record Blade(End end,int side,double from,double heel,int state,boolean branch,double gapFrom) { }
    private record Fragment(Rail rail,double from,double to,RailMesh mesh) { }

    public static TurnoutMesh build(TurnoutBinding binding,List<List<Station>> routes,TurnoutRailParams input) {
        FsrailParts.Asset asset=FsrailParts.load(input.modelResource());
        if(asset==null||asset.rail()==null||routes.size()!=binding.tracks().size())return TurnoutMesh.EMPTY;
        TurnoutRailParams p=new ObjRailProfile(asset,1).apply(input);
        List<Crossing> crossings=crossings(routes,p);
        List<Blade> blades=blades(binding,routes,p,crossings);
        Map<Rail,List<Gap>> gaps=new HashMap<>();
        for(Blade blade:blades) {
            End end=blade.end();double length=totalArc(routes.get(end.route()));
            Rail rail=new Rail(end.route(),end.reversed()?-blade.side():blade.side());
            // 三开第二组错后起尖，前面的这一小段也必须留空，不能把它补成完整固定轨。
            gaps.computeIfAbsent(rail,key->new ArrayList<>()).add(end.reversed()?new Gap(length-blade.heel(),length-blade.gapFrom()):new Gap(blade.gapFrom(),blade.heel()));
        }
        Sink fixed=sink(asset);List<Fragment> fragments=new ArrayList<>();
        for(int route=0;route<routes.size();route++)for(int side:new int[]{-1,1}) {
            Rail rail=new Rail(route,side);List<Station> line=routes.get(route);
            TreeSet<Double> cuts=new TreeSet<>(List.of(0D,totalArc(line)));
            for(Gap gap:gaps.getOrDefault(rail,List.of())){cuts.add(gap.from());cuts.add(gap.to());}
            for(Crossing cross:crossings) {
                double arc=cross.a().equals(rail)?cross.aa():cross.b().equals(rail)?cross.ba():-1;
                if(arc<0)continue;cuts.add(Math.max(0,arc-cross.half()));cuts.add(Math.min(totalArc(line),arc+cross.half()));
            }
            List<Double> boundaries=new ArrayList<>(cuts);
            RailMesh.Surface surface=cutSurface(line,p,new ObjRailProfile(asset,-side));
            for(int i=1;i<boundaries.size();i++) {
                double from=boundaries.get(i-1),to=boundaries.get(i),mid=(from+to)*0.5D;
                if(to-from<1e-6D||inGap(gaps,rail,mid))continue;
                RailMesh mesh=railMesh(line,p,asset,side,from,to,0);
                for(Crossing cross:crossings) {
                    boolean first=cross.a().equals(rail),second=cross.b().equals(rail);if(!first&&!second)continue;
                    double arc=first?cross.aa():cross.ba();if(to<arc-cross.half()||from>arc+cross.half())continue;
                    Rail other=first?cross.b():cross.a();double otherArc=first?cross.ba():cross.aa();
                    for(List<RailMesh.Plane> volume:FrogBuilder.slots(routes.get(other.route()),p,other.side(),otherArc-cross.half()-0.25D,otherArc+cross.half()+0.25D))mesh=mesh.subtract(volume,surface);
                }
                fragments.add(new Fragment(rail,from,to,mesh));
            }
        }
        // 同一条 MTR 直线尾段可属于不同逻辑轨段；按实际共面表面去重，不只按节点对去重。
        CoplanarMeshOwner steel=new CoplanarMeshOwner();
        for(Fragment fragment:fragments)emit(fixed,steel.take(fragment.mesh()),asset,p);
        guards(fixed,routes,p,asset,crossings,gaps,steel);
        furniture(fixed,routes,p,asset,gaps);
        List<TurnoutMesh.BladePart> moving=new ArrayList<>();
        for(Blade blade:blades) {
            List<Station> line=blade.end().line();List<Station> portion=relativeSlice(line,blade.from(),blade.heel());
            double length=totalArc(portion);if(length<0.05D)continue;
            Sink out=sink(asset);emitRail(out,portion,p,asset,blade.side(),0,length,length);
            double[] pivot=modelPoint(line,blade.heel(),-blade.side()*railCenter(p),beamTopY(p)+p.modelYOffset(),p.railTopOffset());
            double travel=clamp(p.gaugeBlocks()*BLADE_TRAVEL_PER_GAUGE,0.02D,0.12D);
            moving.add(new TurnoutMesh.BladePart(out.toModel(),new TurnoutMesh.BladeMotion(pivot[0],pivot[1],pivot[2],-blade.side()),
                    Math.asin(Math.min(0.5D,travel/length)),blade.state(),blade.branch(),binding.kind()==TurnoutKind.THREE_WAY));
        }
        return new TurnoutMesh(fixed.toModel(),null,null,null,null,0,1,true,moving);
    }

    private static List<Station> relativeSlice(List<Station> line,double from,double to) {
        List<Station> out=new ArrayList<>();
        for(Station s:slice(line,from,to))out.add(new Station(s.x(),s.y(),s.z(),s.tx(),s.tz(),s.tilt(),s.arc()-from));
        return out;
    }
    private static List<Station> reverse(List<Station> line) {
        List<Station> out=new ArrayList<>();double length=totalArc(line);
        for(int i=line.size()-1;i>=0;i--){Station s=line.get(i);out.add(new Station(s.x(),s.y(),s.z(),-s.tx(),-s.tz(),-s.tilt(),length-s.arc()));}
        return out;
    }
    private static double bend(List<Station> line) {
        double sum=0;for(int i=1;i<line.size();i++){Station a=line.get(i-1),b=line.get(i);sum+=Math.abs(Math.atan2(a.tx()*b.tz()-a.tz()*b.tx(),a.tx()*b.tx()+a.tz()*b.tz()));}return sum;
    }
    private static List<Blade> blades(TurnoutBinding binding,List<List<Station>> routes,TurnoutRailParams p,List<Crossing> crossings) {
        Map<BlockPos,List<End>> ends=new LinkedHashMap<>();
        for(int i=0;i<routes.size();i++) {
            ends.computeIfAbsent(binding.tracks().get(i).start(),key->new ArrayList<>()).add(new End(i,false,routes.get(i)));
            ends.computeIfAbsent(binding.tracks().get(i).end(),key->new ArrayList<>()).add(new End(i,true,reverse(routes.get(i))));
        }
        List<Blade> out=new ArrayList<>();Set<String> used=new HashSet<>();
        for(List<End> junction:ends.values()) {
            if(junction.size()<2)continue;
            // 同向的两条出口才是一组岔尖，来向那条基本轨不重复生成。
            List<List<End>> groups=new ArrayList<>();
            for(End end:junction){List<End> group=null;for(List<End> candidate:groups)if(dot(candidate.get(0).line().get(0),end.line().get(0))>0){group=candidate;break;}if(group==null){group=new ArrayList<>();groups.add(group);}group.add(end);}
            for(List<End> group:groups) {
                if(group.size()<2)continue;group.sort(Comparator.comparingDouble(end->bend(end.line())));
                End main=group.get(0);
                if(binding.kind()==TurnoutKind.THREE_WAY&&group.size()==3) {
                    // 三开的“中股”按实际横向顺序选，不能把曲率最小但位于最外侧的一股当中股。
                    Station origin=group.get(0).line().get(0);
                    double shortest=group.stream().mapToDouble(end->totalArc(end.line())).min().orElse(0);
                    // 共用直线可能超过全长八成，排序点要靠近最短股的末端，确保位于实际分岔之后。
                    double arc=Math.max(0,shortest-Math.min(0.05D,shortest*0.01D));
                    group.sort(Comparator.comparingDouble(end->lateral(origin,interpolate(end.line(),arc))));
                    main=group.get(1);
                }
                List<End> branches=new ArrayList<>(group);branches.remove(main);int state=0;
                for(End branch:branches) {
                    int sign=divergence(main.line(),branch.line());state++;
                    double stagger=binding.kind()==TurnoutKind.THREE_WAY&&state>1?0.75D:0;
                    addBlade(out,used,main,sign,branch,p,crossings,stagger,state,false);
                    addBlade(out,used,branch,-sign,main,p,crossings,stagger,state,true);
                }
            }
        }
        return out;
    }
    private static void addBlade(List<Blade> out,Set<String> used,End end,int side,End stock,TurnoutRailParams p,List<Crossing> crossings,
                                 double stagger,int state,boolean branch) {
        int originalSide=end.reversed()?-side:side;
        String key=end.route()+"/"+originalSide+"/"+end.reversed();if(used.contains(key))return;
        double length=totalArc(end.line());
        double lead=SharedRailLead.length(end.line(),stock.line());
        if(Math.min(length,totalArc(stock.line()))-lead<0.10D)return;
        // 两头都有岔尖的交分轨段，各自最多占四成，固定连接段始终保留。
        double limit=lead+(length-lead)*0.4D;
        for(Crossing crossing:crossings) {
            Rail rail=new Rail(end.route(),originalSide);
            double arc=crossing.a().equals(rail)?crossing.aa():crossing.b().equals(rail)?crossing.ba():-1;
            if(arc<0)continue;if(end.reversed())arc=length-arc;
            if(arc>lead+0.3D)limit=Math.min(limit,lead+Math.max(0.1D,arc-crossing.half()-lead)*0.8D);
        }
        double heel=Math.min(length,lead+BladeClipper.heelArc(SharedRailLead.tail(end.line(),lead),SharedRailLead.tail(stock.line(),lead),p,limit-lead));
        stagger=Math.min(stagger,(heel-lead)*0.25D);
        if(heel-lead-stagger>0.05D){used.add(key);out.add(new Blade(end,side,lead+stagger,heel,state,branch,lead));}
    }
    private static int divergence(List<Station> main,List<Station> branch) {
        double largest=0;
        for(Station s:branch){Station near=nearest(main,s.x(),s.z());double value=lateral(near,s);if(Math.abs(value)>Math.abs(largest))largest=value;}
        return largest>=0?1:-1;
    }
    private static double dot(Station a,Station b){return a.tx()*b.tx()+a.tz()*b.tz();}
    private static boolean inGap(Map<Rail,List<Gap>> gaps,Rail rail,double arc){for(Gap gap:gaps.getOrDefault(rail,List.of()))if(arc>=gap.from()-1e-7D&&arc<gap.to()-1e-7D)return true;return false;}

    /** 轨头中心线交点以空间网格索引，避免长渡线对每两段采样作全量平方比较。 */
    private static List<Crossing> crossings(List<List<Station>> routes,TurnoutRailParams p) {
        Map<Rail,List<Station>> rails=new LinkedHashMap<>();
        for(int r=0;r<routes.size();r++)for(int side:new int[]{-1,1}) {
            List<Station> line=new ArrayList<>();for(Station s:routes.get(r)) {
                double[] v=modelPoint(routes.get(r),s.arc(),-side*railCenter(p),beamTopY(p)+p.modelYOffset(),p.railTopOffset());
                line.add(new Station(v[0],v[1],v[2],s.tx(),s.tz(),s.tilt(),s.arc()));
            }rails.put(new Rail(r,side),line);
        }
        List<Rail> keys=new ArrayList<>(rails.keySet());List<Crossing> out=new ArrayList<>();
        for(int ai=0;ai<keys.size();ai++)for(int bi=ai+1;bi<keys.size();bi++) {
            Rail ka=keys.get(ai),kb=keys.get(bi);if(ka.route()==kb.route())continue;
            List<Station> a=rails.get(ka),b=rails.get(kb);Map<Long,List<Integer>> index=new HashMap<>();
            for(int j=1;j<b.size();j++)for(long cell:cells(b.get(j-1),b.get(j)))index.computeIfAbsent(cell,k->new ArrayList<>()).add(j);
            Set<String> seen=new HashSet<>();
            for(int i=1;i<a.size();i++) {
                Station p0=a.get(i-1),p1=a.get(i);Set<Integer> candidates=new HashSet<>();for(long cell:cells(p0,p1))candidates.addAll(index.getOrDefault(cell,List.of()));
                for(int j:candidates) {
                    Station q=b.get(j-1),q1=b.get(j);double rx=p1.x()-p0.x(),rz=p1.z()-p0.z(),sx=q1.x()-q.x(),sz=q1.z()-q.z(),det=rx*sz-rz*sx;
                    if(Math.abs(det)<1e-12D)continue;double dx=q.x()-p0.x(),dz=q.z()-p0.z(),t=(dx*sz-dz*sx)/det,u=(dx*rz-dz*rx)/det;
                    if(t< -1e-7D||t>1.0000001D||u< -1e-7D||u>1.0000001D||Math.abs(p0.y()+(p1.y()-p0.y())*t-q.y()-(q1.y()-q.y())*u)>0.25D)continue;
                    double aa=p0.arc()+(p1.arc()-p0.arc())*t,ba=q.arc()+(q1.arc()-q.arc())*u;
                    // 共用节点的同一根基本轨仅是接缝，不是需要开槽的辙叉。
                    if(Math.min(aa,totalArc(a)-aa)<0.12D&&Math.min(ba,totalArc(b)-ba)<0.12D)continue;
                    if(!seen.add(Math.round(aa*10000)+"/"+Math.round(ba*10000)))continue;
                    double sin=Math.abs(p0.tx()*q.tz()-p0.tz()*q.tx());if(sin<0.005D)continue;
                    double half=clamp((2*beam(p).headHalf()+2*FLANGEWAY)/sin,0.35D,2.5D);
                    out.add(new Crossing(ka,aa,kb,ba,half));
                }
            }
        }
        return out;
    }
    private static List<Long> cells(Station a,Station b) {
        List<Long> out=new ArrayList<>();
        for(int x=(int)Math.floor(Math.min(a.x(),b.x())*2-1e-6D);x<=(int)Math.floor(Math.max(a.x(),b.x())*2+1e-6D);x++)
            for(int z=(int)Math.floor(Math.min(a.z(),b.z())*2-1e-6D);z<=(int)Math.floor(Math.max(a.z(),b.z())*2+1e-6D);z++)out.add(((long)x<<32)^(z&0xffffffffL));
        return out;
    }
    private static void guards(Sink fixed,List<List<Station>> routes,TurnoutRailParams p,FsrailParts.Asset asset,List<Crossing> crosses,Map<Rail,List<Gap>> gaps,CoplanarMeshOwner steel) {
        Map<Rail,List<Gap>> requests=new LinkedHashMap<>();
        for(Crossing cross:crosses)for(boolean first:new boolean[]{true,false}) {
            Rail intersected=first?cross.a():cross.b();double arc=first?cross.aa():cross.ba();
            Rail rail=new Rail(intersected.route(),-intersected.side());
            double length=Math.min(3D,Math.max(CHECK_RAIL_LENGTH,cross.half()*2)+0.30D);
            requests.computeIfAbsent(rail,key->new ArrayList<>()).add(new Gap(Math.max(0,arc-length*0.5D),
                    Math.min(totalArc(routes.get(rail.route())),arc+length*0.5D)));
        }
        List<List<RailMesh.Plane>> occupiedGuards=new ArrayList<>();
        for(var entry:requests.entrySet()) {
            Rail rail=entry.getKey();List<Station> ownRoute=routes.get(rail.route());
            for(Gap merged:merge(entry.getValue()))for(Gap span:fixedSpans(merged,gaps.getOrDefault(rail,List.of()))) {
                CheckRailBuilder.Guard guard=CheckRailBuilder.meshRange(ownRoute,p,asset,span.from(),span.to(),rail.side());
                if(guard==null)continue;
                RailMesh mesh=guard.mesh();RailMesh.Surface surface=cutSurface(guard.line(),p,new ObjRailProfile(asset,-rail.side()));
                Station center=interpolate(guard.line(),totalArc(guard.line())*0.5D);
                for(int other=0;other<routes.size();other++)if(other!=rail.route()) {
                    List<Station> route=routes.get(other);Station near=nearest(route,center.x(),center.z());
                    double reach=totalArc(guard.line())+p.gaugeBlocks();
                    if(Math.hypot(near.x()-center.x(),near.z()-center.z())>reach||Math.abs(near.y()-center.y())>0.25D)continue;
                    for(int side:new int[]{-1,1}) {
                        for(List<RailMesh.Plane> volume:FrogBuilder.slots(route,p,side,near.arc()-reach,near.arc()+reach))mesh=mesh.subtract(volume,surface);
                        for(List<RailMesh.Plane> volume:CheckRailBuilder.clearance(route,p,side,near.arc()-reach,near.arc()+reach))mesh=mesh.subtract(volume,surface);
                    }
                }
                // 紧凑菱形中不同方向的护轨也会相交，后生成的护轨为已存在的一根让位。
                for(List<RailMesh.Plane> volume:occupiedGuards)mesh=mesh.subtract(volume,surface);
                emit(fixed,steel.take(mesh),asset,p);
                occupiedGuards.addAll(CheckRailBuilder.clearance(guard.line(),p,rail.side(),0,totalArc(guard.line())));
            }
        }
    }
    private static List<Gap> merge(List<Gap> intervals) {
        List<Gap> sorted=new ArrayList<>(intervals);sorted.sort(Comparator.comparingDouble(Gap::from));
        List<Gap> out=new ArrayList<>();
        for(Gap span:sorted) {
            if(span.to()<=span.from())continue;
            if(out.isEmpty()||span.from()>out.get(out.size()-1).to()+0.05D)out.add(span);
            else {Gap previous=out.remove(out.size()-1);out.add(new Gap(previous.from(),Math.max(previous.to(),span.to())));}
        }
        return out;
    }
    /** 整段护轨避开活动区间，不能只检查护轨中心点是否落在尖轨内。 */
    private static List<Gap> fixedSpans(Gap interval,List<Gap> moving) {
        List<Gap> out=new ArrayList<>();double from=interval.from();
        for(Gap gap:merge(moving)) {
            if(gap.to()<=from||gap.from()>=interval.to())continue;
            if(gap.from()>from)out.add(new Gap(from,Math.min(interval.to(),gap.from())));
            from=Math.max(from,gap.to());if(from>=interval.to())break;
        }
        if(from<interval.to())out.add(new Gap(from,interval.to()));return out;
    }

    /** 平行且贴近的股道共用横枕；覆盖真实外侧轨脚后即分开，扣件避开活动尖轨。 */
    private static void furniture(Sink out,List<List<Station>> routes,TurnoutRailParams p,FsrailParts.Asset asset,Map<Rail,List<Gap>> gaps) {
        if(asset.sleeper()==null)return;
        double min=Double.POSITIVE_INFINITY,max=-min;
        for(FsrailParts.Tri f:asset.sleeper().tris())for(FsrailParts.Vertex v:List.of(f.a(),f.b(),f.c())){min=Math.min(min,-v.x());max=Math.max(max,-v.x());}
        double steel=railCenter(p)+beam(p).footHalf(),split=2*steel+0.08D,overhang=Math.max(0,(max-min)*0.5D-steel);
        boolean depot=asset.source().endsWith("mtr_siding.obj");
        CoplanarMeshOwner sleepers=new CoplanarMeshOwner();
        for(int i=0;i<routes.size();i++) {
            List<Station> route=routes.get(i);double step=period(route,p);
            for(double arc=step*0.5D;arc<totalArc(route);arc+=step) {
                Station s=interpolate(route,arc);
                if(depot) {
                    for(int side:new int[]{-1,1})if(!inGap(gaps,new Rail(i,side),arc)){emit(out,sleepers.take(partSideMesh(asset.sleeper(),route,s,p,side)),asset,p);fasteners(out,asset,route,s,p,side);}continue;
                }
                double lo=min,hi=max;boolean owned=true;Map<Integer,Station> shared=new LinkedHashMap<>();shared.put(i,s);
                for(int j=0;j<routes.size();j++)if(i!=j) {
                    Station other=crossSection(routes.get(j),s);if(other==null||Math.abs(s.y()-other.y())>0.15D||Math.abs(dot(s,other))<0.8D)continue;
                    double lateral=lateral(s,other),gap=Math.abs(lateral);
                    if(gap<split) {
                        if(j<i)owned=false;shared.put(j,other);
                        double extent=railCenter(p)*Math.abs(dot(s,other))+beam(p).footHalf()+overhang;
                        lo=Math.min(lo,lateral-extent);hi=Math.max(hi,lateral+extent);
                    }else if(gap<max-min) {
                        if(lateral>0)hi=Math.max(steel+0.02D,Math.min(hi,gap*0.5D));else lo=-Math.max(steel+0.02D,Math.min(-lo,gap*0.5D));
                    }
                }
                if(!owned)continue;emit(out,sleepers.take(sleeperMesh(asset,route,s,p,min,max,lo,hi)),asset,p);
                for(var entry:shared.entrySet())for(int side:new int[]{-1,1})if(!inGap(gaps,new Rail(entry.getKey(),side),entry.getValue().arc()))fasteners(out,asset,routes.get(entry.getKey()),entry.getValue(),p,side);
            }
        }
    }
}
