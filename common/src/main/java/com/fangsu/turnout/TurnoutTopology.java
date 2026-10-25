package com.fangsu.turnout;

import com.fangsu.items.ItemNodeMarker;
import com.fangsu.turnout.marker.MarkerColor;
import com.fangsu.turnout.marker.NodeMarker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.Consumer;

/** 七种道岔的共用判定。连接必须来自真实 MTR 轨道，不再使用 0.5~35 度的弦夹角门槛。 */
public final class TurnoutTopology {
    public static final double MAX_DISTANCE=32D;
    public static final int MAX_MARKERS=64,MAX_HOPS=16;
    public static final double MAX_PATH_LENGTH=96D;
    private TurnoutTopology() { }

    public static TurnoutValidation validateClient(Level level,BlockPos pos,List<NodeMarker> markers,String model) {
        return validateWith(level,pos,markers,model,TurnoutRailAccess.client(level,pos));
    }
    /** 服务端所有放置和重建都走异步权威入口，MTR4 查询 TSC 后回到服务器主线程。 */
    public static void validateServer(Level level,BlockPos pos,List<NodeMarker> markers,String model,
                                       List<TurnoutRailGraph.Link> hints,Consumer<TurnoutValidation> result) {
        TurnoutRailAccess.server(level,pos,hints,graph->result.accept(validateWith(level,pos,markers,model,graph)));
    }
    public static TurnoutValidation validate(Level level,BlockPos pos,List<NodeMarker> markers,String model) {
        return level!=null&&level.isClientSide?validateClient(level,pos,markers,model):fail(TurnoutValidation.HINT_NO_RAIL_DATA);
    }
    public static List<TurnoutRailGraph.Link> candidates(Level level,BlockPos pos,List<NodeMarker> markers) {
        TurnoutRailGraph graph=TurnoutRailAccess.client(level,pos);
        TurnoutValidation check=validateWith(level,pos,markers,null,graph);
        if(check.isSupported()) {
            Set<TurnoutRailGraph.Link> links=new LinkedHashSet<>();
            for(TurnoutTrack track:check.binding().tracks())for(int i=1;i<track.nodes().size();i++)links.add(new TurnoutRailGraph.Link(track.nodes().get(i-1),track.nodes().get(i)));
            return List.copyOf(links);
        }
        List<TurnoutRailGraph.Link> links=new ArrayList<>(graph.links());
        links.sort(Comparator.comparingDouble(link->Math.min(link.a().distSqr(pos),link.b().distSqr(pos))));
        return List.copyOf(links.subList(0,Math.min(links.size(),TurnoutRailAccess.MAX_LINKS)));
    }
    public static List<BlockPos> findPath(Level level,BlockPos from,BlockPos to) {
        return path(TurnoutRailAccess.client(level,from),from,to,Set.of());
    }
    private static TurnoutValidation fail(String reason){return TurnoutValidation.fail(TurnoutKind.INVALID,reason);}

    public static TurnoutValidation validateWith(Level level,BlockPos pos,List<NodeMarker> markers,String model,TurnoutRailGraph graph) {
        if(level==null||pos==null||markers==null||markers.isEmpty()||markers.size()>MAX_MARKERS)return fail(TurnoutValidation.HINT_INVALID_NODES);
        Map<BlockPos,NodeMarker> nearby=new LinkedHashMap<>();
        for(NodeMarker marker:markers) {
            if(marker==null||marker.pos()==null||marker.color()==null)return fail(TurnoutValidation.HINT_INVALID_NODES);
            if(marker.pos().distSqr(pos)>MAX_DISTANCE*MAX_DISTANCE)continue;
            if(!level.hasChunkAt(marker.pos())||!ItemNodeMarker.isRailNode(level.getBlockState(marker.pos()).getBlock()))return fail(TurnoutValidation.HINT_INVALID_NODES);
            if(nearby.put(marker.pos(),marker)!=null)return fail(TurnoutValidation.HINT_INVALID_NODES);
        }
        if(nearby.isEmpty())return fail(TurnoutValidation.HINT_TOO_FAR);
        if(nearby.size()!=3&&nearby.size()!=4)return fail(TurnoutValidation.HINT_INVALID_NODES);
        if(graph==null||graph.isEmpty())return fail(TurnoutValidation.HINT_NO_RAIL_DATA);
        List<NodeMarker> ordered=new ArrayList<>(nearby.values());ordered.sort(Comparator.comparingDouble(m->m.pos().distSqr(pos)));
        if(ordered.size()==3&&ordered.stream().filter(m->m.color()==MarkerColor.BLUE).count()!=1)return fail(TurnoutValidation.HINT_INVALID_NODES);
        // 四端复合道岔优先，避免把单/双交分中的一个岔尖误识别成独立单开。
        if(ordered.size()>=4) {
            List<NodeMarker> group=List.copyOf(ordered.subList(0,4));
            long blues=group.stream().filter(m->m.color()==MarkerColor.BLUE).count();
            if(blues==1) {
                NodeMarker toe=group.stream().filter(m->m.color()==MarkerColor.BLUE).findFirst().orElseThrow();
                List<NodeMarker> reds=group.stream().filter(m->m.color()==MarkerColor.RED).toList();
                TurnoutValidation three=local(toe,reds,group,model,graph);
                if(three.isSupported()&&three.kind()==TurnoutKind.THREE_WAY)return three;
            }
            TurnoutValidation compound=compound(group,model,graph);
            if(compound.isSupported())return compound;
            return compound;
        }
        Set<BlockPos> all=new HashSet<>(nearby.keySet());
        for(NodeMarker toe:ordered) {
            if(toe.color()!=MarkerColor.BLUE)continue;
            List<NodeMarker> reds=new ArrayList<>();
            for(NodeMarker marker:ordered)if(marker.color()==MarkerColor.RED&&!path(graph,toe.pos(),marker.pos(),all).isEmpty())reds.add(marker);
            if(reds.size()==2||reds.size()==3) {
                List<NodeMarker> group=new ArrayList<>(List.of(toe));group.addAll(reds);
                TurnoutValidation candidate=local(toe,reds,group,model,graph);if(candidate.isSupported())return candidate;
            }
        }
        return fail(TurnoutValidation.HINT_NO_RAIL);
    }

    private static TurnoutValidation local(NodeMarker toe,List<NodeMarker> reds,List<NodeMarker> selected,String model,TurnoutRailGraph graph) {
        if(reds.size()<2||reds.size()>3)return fail(TurnoutValidation.HINT_INVALID_NODES);
        Set<BlockPos> stops=new HashSet<>();selected.forEach(m->stops.add(m.pos()));
        List<TurnoutTrack> routes=new ArrayList<>();Set<BlockPos> departures=new HashSet<>();
        for(NodeMarker red:reds) {
            List<BlockPos> path=path(graph,toe.pos(),red.pos(),stops);
            if(path.size()<2)return fail(TurnoutValidation.HINT_NO_RAIL);
            // 蓝标记必须是实际岔尖，不能把同一条进路的中间节点当成几个不同分支。
            if(!departures.add(path.get(1)))return fail(TurnoutValidation.HINT_INVALID_NODES);
            routes.add(new TurnoutTrack(path));
        }
        routes.sort(Comparator.comparingDouble(track->bend(graph.points(track.nodes()))));
        List<Vec3> reference=graph.points(routes.get(0).nodes());
        Vec3 direction=reference.get(1).subtract(reference.get(0));
        if(direction.horizontalDistanceSqr()<1e-10D)return fail(TurnoutValidation.HINT_BAD_ANGLE);
        List<Double> lateral=new ArrayList<>();
        for(TurnoutTrack route:routes) {
            List<Vec3> points=graph.points(route.nodes());Vec3 first=points.get(1).subtract(points.get(0));
            // 相反方向属于进、出向两端，不是一处分岔。实际曲线合法性由 MTR 适配层判断。
            if(first.x*direction.x+first.z*direction.z<=0)return fail(TurnoutValidation.HINT_INVALID_NODES);
            Vec3 end=points.get(points.size()-1).subtract(points.get(0));lateral.add(direction.x*end.z-direction.z*end.x);
        }
        TurnoutKind kind;
        if(routes.size()==3)kind=TurnoutKind.THREE_WAY;
        else kind=lateral.get(0)*lateral.get(1)<-1e-6D?TurnoutKind.DOUBLE:TurnoutKind.SINGLE;
        return bind(kind,routes,selected,model,graph);
    }

    private static TurnoutValidation compound(List<NodeMarker> selected,String model,TurnoutRailGraph graph) {
        Set<BlockPos> stops=new HashSet<>();selected.forEach(m->stops.add(m.pos()));List<TurnoutTrack> routes=new ArrayList<>();
        for(int i=0;i<4;i++)for(int j=i+1;j<4;j++) {
            List<BlockPos> path=path(graph,selected.get(i).pos(),selected.get(j).pos(),stops);
            if(path.size()>=2)routes.add(new TurnoutTrack(path));
        }
        if(routes.size()<2||routes.size()>4)return fail(TurnoutValidation.HINT_INVALID_NODES);
        List<int[]> matchings=new ArrayList<>();
        for(int i=0;i<routes.size();i++)for(int j=i+1;j<routes.size();j++) {
            TurnoutTrack a=routes.get(i),b=routes.get(j);
            if(new HashSet<>(List.of(a.start(),a.end(),b.start(),b.end())).size()==4)matchings.add(new int[]{i,j});
        }
        if(matchings.isEmpty())return fail(TurnoutValidation.HINT_INVALID_NODES);
        matchings.sort(Comparator.comparingDouble(pair->bend(graph.points(routes.get(pair[0]).nodes()))+bend(graph.points(routes.get(pair[1]).nodes()))));
        int[] main=matchings.get(0);boolean crossing=crosses(graph.points(routes.get(main[0]).nodes()),graph.points(routes.get(main[1]).nodes()));
        TurnoutKind kind;
        if(routes.size()==2) {if(!crossing)return fail(TurnoutValidation.HINT_INVALID_NODES);kind=TurnoutKind.DIAMOND;}
        else if(routes.size()==3) {if(!crossing)return fail(TurnoutValidation.HINT_INVALID_NODES);kind=TurnoutKind.SINGLE_SLIP;}
        else if(crossing)kind=TurnoutKind.DOUBLE_SLIP;
        else {
            boolean alternate=false;for(int[] pair:matchings)alternate|=crosses(graph.points(routes.get(pair[0]).nodes()),graph.points(routes.get(pair[1]).nodes()));
            if(!alternate)return fail(TurnoutValidation.HINT_INVALID_NODES);kind=TurnoutKind.SCISSORS_CROSSOVER;
        }
        return bind(kind,chains(routes,stops),selected,model,graph);
    }

    private static TurnoutValidation bind(TurnoutKind kind,List<TurnoutTrack> routes,List<NodeMarker> selected,String model,TurnoutRailGraph graph) {
        if(routes.size()<2||routes.size()>64)return fail(TurnoutValidation.HINT_INVALID_NODES);
        TurnoutTrack first=routes.get(0),second=routes.get(1);List<Vec3> points=graph.points(first.nodes());
        if(points.size()<2)return fail(TurnoutValidation.HINT_BAD_ANGLE);
        Vec3 anchor=points.get(0),d=points.get(1).subtract(anchor);
        TurnoutBinding binding=new TurnoutBinding(first.start(),first.end(),second.end(),model,anchor,
                (float)Math.toDegrees(Math.atan2(d.z,d.x)),first.nodes(),second.nodes(),kind,routes,selected);
        return TurnoutValidation.ok(binding);
    }

    /** 将重复使用的 MTR 段去重并压成链，交分/渡线的共用基本轨只会渲染一次。 */
    private static List<TurnoutTrack> chains(List<TurnoutTrack> paths,Set<BlockPos> marked) {
        Map<BlockPos,Set<BlockPos>> graph=new HashMap<>();
        for(TurnoutTrack path:paths)for(int i=1;i<path.nodes().size();i++){BlockPos a=path.nodes().get(i-1),b=path.nodes().get(i);graph.computeIfAbsent(a,k->new TreeSet<>(Comparator.comparingLong(BlockPos::asLong))).add(b);graph.computeIfAbsent(b,k->new TreeSet<>(Comparator.comparingLong(BlockPos::asLong))).add(a);}
        Set<BlockPos> stops=new HashSet<>(marked);graph.forEach((node,next)->{if(next.size()!=2)stops.add(node);});
        Set<TurnoutRailGraph.Link> used=new HashSet<>();List<TurnoutTrack> out=new ArrayList<>();
        List<BlockPos> sorted=new ArrayList<>(stops);sorted.sort(Comparator.comparingLong(BlockPos::asLong));
        for(BlockPos start:sorted)for(BlockPos first:graph.getOrDefault(start,Set.of())) {
            if(used.contains(new TurnoutRailGraph.Link(start,first)))continue;
            List<BlockPos> path=new ArrayList<>(List.of(start));BlockPos previous=start,current=first;
            for(int guard=0;guard<64;guard++) {
                used.add(new TurnoutRailGraph.Link(previous,current));path.add(current);if(stops.contains(current))break;
                BlockPos next=null;for(BlockPos candidate:graph.getOrDefault(current,Set.of()))if(!candidate.equals(previous)){next=candidate;break;}
                if(next==null)break;previous=current;current=next;
            }
            out.add(new TurnoutTrack(path));
        }
        return out;
    }

    /** 有界路径搜索；中间节点必须能按 MTR 的行进方向连通，不能在岔尖反向折返换股。 */
    private static List<BlockPos> path(TurnoutRailGraph graph,BlockPos from,BlockPos to,Set<BlockPos> stops) {
        record Step(BlockPos node,BlockPos previous,List<BlockPos> nodes,double length) { }
        PriorityQueue<Step> queue=new PriorityQueue<>(Comparator.comparingInt((Step s)->s.nodes().size()).thenComparingDouble(Step::length));
        queue.add(new Step(from,null,List.of(from),0));Map<String,Double> visited=new HashMap<>();
        while(!queue.isEmpty()) {
            Step state=queue.poll();if(state.node().equals(to))return state.nodes();if(state.nodes().size()>MAX_HOPS)continue;
            String key=state.node().asLong()+"/"+(state.previous()==null?"start":state.previous().asLong());
            if(visited.getOrDefault(key,Double.POSITIVE_INFINITY)<=state.length())continue;visited.put(key,state.length());
            for(var entry:graph.neighbors(state.node()).entrySet()) {
                BlockPos next=entry.getKey();TurnoutRailGraph.Edge edge=entry.getValue();
                if(!edge.valid()||state.nodes().contains(next)||(!next.equals(to)&&stops.contains(next)))continue;
                double length=state.length()+edge.length();if(length>MAX_PATH_LENGTH)continue;
                if(state.previous()!=null) {
                    List<Vec3> prior=graph.edge(state.previous(),state.node()).from(state.previous()),outgoing=edge.from(state.node());
                    Vec3 in=prior.get(prior.size()-1).subtract(prior.get(prior.size()-2)),out=outgoing.get(1).subtract(outgoing.get(0));
                    if(in.x*out.x+in.z*out.z<=0)continue;
                }
                List<BlockPos> nodes=new ArrayList<>(state.nodes());nodes.add(next);queue.add(new Step(next,state.node(),List.copyOf(nodes),length));
            }
        }
        return List.of();
    }
    private static double bend(List<Vec3> points) {
        if(points.size()<2)return Double.POSITIVE_INFINITY;Vec3 a=points.get(0),b=points.get(points.size()-1);double dx=b.x-a.x,dz=b.z-a.z,length=Math.max(1e-8D,Math.hypot(dx,dz)),maximum=0;
        for(Vec3 p:points)maximum=Math.max(maximum,Math.abs(dx*(p.z-a.z)-dz*(p.x-a.x))/length);return maximum;
    }
    private static boolean crosses(List<Vec3> a,List<Vec3> b) {
        for(int i=1;i<a.size();i++)for(int j=1;j<b.size();j++) {
            Vec3 p=a.get(i-1),q=b.get(j-1),r=a.get(i).subtract(p),s=b.get(j).subtract(q);double det=r.x*s.z-r.z*s.x;if(Math.abs(det)<1e-12D)continue;
            double t=((q.x-p.x)*s.z-(q.z-p.z)*s.x)/det,u=((q.x-p.x)*r.z-(q.z-p.z)*r.x)/det;
            if(t>=-1e-7D&&t<=1.0000001D&&u>=-1e-7D&&u<=1.0000001D&&Math.abs(p.y+r.y*t-q.y-s.y*u)<0.25D)return true;
        }
        return false;
    }
}
