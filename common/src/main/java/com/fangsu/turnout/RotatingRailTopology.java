package com.fangsu.turnout;

import com.fangsu.items.ItemNodeMarker;
import com.fangsu.turnout.marker.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Consumer;

/** 活动轨道沿用道岔的权威轨道查询；颜色决定机构类型，几何全部来自实际轨道。 */
public final class RotatingRailTopology {
    private RotatingRailTopology() { }
    public static TurnoutValidation validateClient(Level level,BlockPos pos,List<NodeMarker> markers,String model) {
        return validateWith(level,pos,markers,model,TurnoutRailAccess.client(level,pos));
    }
    public static void validateServer(Level level,BlockPos pos,List<NodeMarker> markers,String model,List<TurnoutRailGraph.Link> hints,Consumer<TurnoutValidation> result) {
        TurnoutRailAccess.server(level,pos,hints,graph->result.accept(validateWith(level,pos,markers,model,graph)));
    }
    private static TurnoutValidation fail(String key){return TurnoutValidation.fail(TurnoutKind.INVALID,key);}
    public static TurnoutValidation validateWith(Level level,BlockPos pos,List<NodeMarker> markers,String model,TurnoutRailGraph graph) {
        if(level==null||markers==null||markers.size()>TurnoutTopology.MAX_MARKERS||markers.stream().anyMatch(m->m==null||m.pos()==null||m.color()==null))return fail(TurnoutValidation.HINT_INVALID_NODES);
        List<NodeMarker> selected=markers.stream().filter(m->m.pos().distSqr(pos)<=1024).sorted(Comparator.comparingLong(m->m.pos().asLong())).toList();
        long red=selected.stream().filter(m->m.color()==MarkerColor.RED).count();
        boolean rotation=selected.size()==4&&red==4;
        if(!rotation&&!(selected.size()==3&&red==2))return fail("ui.fangsu.rotating_rail.invalid_markers");
        Set<BlockPos> stops=new HashSet<>();
        for(NodeMarker marker:selected)if(!stops.add(marker.pos())||!level.hasChunkAt(marker.pos())||!ItemNodeMarker.isRailNode(level.getBlockState(marker.pos()).getBlock()))return fail(TurnoutValidation.HINT_INVALID_NODES);
        if(graph==null||graph.isEmpty())return fail(TurnoutValidation.HINT_NO_RAIL_DATA);
        List<TurnoutTrack> tracks=new ArrayList<>();
        if(!rotation) {
            BlockPos fixed=selected.stream().filter(m->m.color()==MarkerColor.BLUE).findFirst().orElseThrow().pos();
            for(NodeMarker redEnd:selected)if(redEnd.color()==MarkerColor.RED) {
                List<BlockPos> path=TurnoutTopology.path(graph,fixed,redEnd.pos(),stops);
                if(path.size()<2)return fail(TurnoutValidation.HINT_NO_RAIL);
                tracks.add(new TurnoutTrack(path));
            }
            tracks.sort(Comparator.comparingDouble(t->bend(graph.points(t.nodes()))));
            List<Vec3> a=graph.points(tracks.get(0).nodes()),b=graph.points(tracks.get(1).nodes());
            if(a.get(1).subtract(a.get(0)).dot(b.get(1).subtract(b.get(0)))<=0)return fail(TurnoutValidation.HINT_BAD_ANGLE);
        } else {
            // 四红只接受两条以同一点为中心的直轨，旋转后两端才能准确接合。
            for(int j=1;j<4&&tracks.isEmpty();j++) {
                List<BlockPos> remaining=new ArrayList<>(selected.stream().map(NodeMarker::pos).toList());
                BlockPos a=remaining.remove(j),b=remaining.remove(0);
                List<BlockPos> p=TurnoutTopology.path(graph,b,a,stops),q=TurnoutTopology.path(graph,remaining.get(0),remaining.get(1),stops);
                if(p.size()<2||q.size()<2)continue;
                List<Vec3> x=graph.points(p),y=graph.points(q);
                Vec3 x0=x.get(0),x1=x.get(x.size()-1),y0=y.get(0),y1=y.get(y.size()-1);
                if(bend(x)>0.05||bend(y)>0.05||x0.add(x1).scale(0.5).distanceTo(y0.add(y1).scale(0.5))>0.1||Math.abs(x0.distanceTo(x1)-y0.distanceTo(y1))>0.2)continue;
                if(x1.subtract(x0).dot(y1.subtract(y0))<0){q=new ArrayList<>(q);Collections.reverse(q);}
                Vec3 target=graph.points(q).get(0);
                if(Math.abs(x0.y-target.y)>0.1)continue;
                tracks.add(new TurnoutTrack(p));tracks.add(new TurnoutTrack(q));
            }
            if(tracks.isEmpty())return fail("ui.fangsu.rotating_rail.invalid_rotation");
        }
        TurnoutTrack first=tracks.get(0),second=tracks.get(1);List<Vec3> points=graph.points(first.nodes());Vec3 d=points.get(1).subtract(points.get(0));
        return TurnoutValidation.ok(new TurnoutBinding(first.start(),first.end(),second.end(),model,points.get(0),(float)Math.toDegrees(Math.atan2(d.z,d.x)),first.nodes(),second.nodes(),TurnoutKind.SINGLE,tracks,selected));
    }
    private static double bend(List<Vec3> points) {
        double length=0;for(int i=1;i<points.size();i++)length+=points.get(i).distanceTo(points.get(i-1));
        return length-points.get(0).distanceTo(points.get(points.size()-1));
    }
}
