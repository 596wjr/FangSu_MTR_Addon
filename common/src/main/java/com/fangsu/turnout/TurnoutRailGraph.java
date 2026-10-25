package com.fangsu.turnout;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** 平台无关的只读轨道快照。valid 由对应版本 MTR/方速建轨判据给出。 */
public final class TurnoutRailGraph {
    public record Edge(BlockPos a,BlockPos b,boolean valid,List<Vec3> points) {
        public Edge { points=List.copyOf(points); }
        public double length(){double total=0;for(int i=1;i<points.size();i++)total+=points.get(i).distanceTo(points.get(i-1));return total;}
        public Vec3 at(BlockPos from){return from.equals(a)?points.get(0):points.get(points.size()-1);}
        public List<Vec3> from(BlockPos from){if(from.equals(a))return points;List<Vec3> out=new ArrayList<>(points);Collections.reverse(out);return out;}
    }
    public record Link(BlockPos a,BlockPos b) {
        public Link {if(a.asLong()>b.asLong()){BlockPos swap=a;a=b;b=swap;}}
    }
    private final Map<BlockPos,Map<BlockPos,Edge>> map=new LinkedHashMap<>();
    public void add(Edge edge){if(edge==null||edge.points().size()<2)return;map.computeIfAbsent(edge.a(),k->new LinkedHashMap<>()).put(edge.b(),edge);map.computeIfAbsent(edge.b(),k->new LinkedHashMap<>()).put(edge.a(),edge);}
    public Edge edge(BlockPos a,BlockPos b){return map.getOrDefault(a,Map.of()).get(b);}
    public Set<BlockPos> nodes(){return Collections.unmodifiableSet(map.keySet());}
    public Map<BlockPos,Edge> neighbors(BlockPos pos){return map.getOrDefault(pos,Map.of());}
    public boolean isEmpty(){return map.isEmpty();}
    public List<Link> links(){Set<Link> out=new LinkedHashSet<>();map.forEach((a,edges)->edges.keySet().forEach(b->out.add(new Link(a,b))));return List.copyOf(out);}
    public List<Vec3> points(List<BlockPos> path){List<Vec3> out=new ArrayList<>();for(int i=1;i<path.size();i++){Edge edge=edge(path.get(i-1),path.get(i));if(edge==null||!edge.valid())return List.of();for(Vec3 point:edge.from(path.get(i-1)))if(out.isEmpty()||out.get(out.size()-1).distanceToSqr(point)>1e-12D)out.add(point);}return out;}
}
