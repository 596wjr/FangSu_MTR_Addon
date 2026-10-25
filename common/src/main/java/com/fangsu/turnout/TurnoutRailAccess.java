package com.fangsu.turnout;

import com.fangsu.Main;
import com.fangsu.data.hybrid.RailActionsModuleExtraSupplier;
import com.fangsu.util.NodeConnector;
import mtr.data.Rail;
import mtr.data.RailwayData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Consumer;

/** MTR3 只读适配：客户端用于预检，服务端始终读取 RailwayData 的权威轨道表。 */
public final class TurnoutRailAccess {
    public static final int MAX_LINKS=512;
    private TurnoutRailAccess() { }
    public static TurnoutRailGraph client(Level level,BlockPos center) {
        if(level==null||!level.isClientSide)return new TurnoutRailGraph();
        synchronized(mtr.client.ClientData.RAILS){return snapshot(mtr.client.ClientData.RAILS,center);}
    }
    @SuppressWarnings("unchecked")
    public static void server(Level level,BlockPos center,List<TurnoutRailGraph.Link> candidates,Consumer<TurnoutRailGraph> result) {
        try {
            RailwayData data=RailwayData.getInstance(level);
            if(data==null){result.accept(new TurnoutRailGraph());return;}
            Map<BlockPos,Map<BlockPos,Rail>> rails;
            if(data.railwayDataRailActionsModule instanceof RailActionsModuleExtraSupplier access)rails=access.getRails();
            else {java.lang.reflect.Field field=RailwayData.class.getDeclaredField("rails");field.setAccessible(true);rails=(Map<BlockPos,Map<BlockPos,Rail>>)field.get(data);}
            result.accept(snapshot(rails,center));
        } catch(ReflectiveOperationException|RuntimeException e){Main.LOGGER.warn("[turnout] 服务端轨道快照失败",e);result.accept(new TurnoutRailGraph());}
    }
    private static TurnoutRailGraph snapshot(Map<BlockPos,Map<BlockPos,Rail>> rails,BlockPos center) {
        TurnoutRailGraph graph=new TurnoutRailGraph();Set<TurnoutRailGraph.Link> visited=new HashSet<>();
        try {
            for(var row:rails.entrySet()) {
                if(row.getKey()==null||row.getValue()==null)continue;
                for(var entry:row.getValue().entrySet()) {
                    BlockPos a=row.getKey(),b=entry.getKey();Rail rail=entry.getValue();
                    if(b==null||rail==null||a.distSqr(center)>4096||b.distSqr(center)>4096)continue;
                    if(!visited.add(new TurnoutRailGraph.Link(a,b)))continue;
                    TurnoutRailGraph.Edge edge=sample(a,b,rail);if(edge!=null)graph.add(edge);
                }
            }
        } catch(RuntimeException e){Main.LOGGER.debug("[turnout] 轨道同步中，等待下次快照",e);}
        return graph;
    }
    private static TurnoutRailGraph.Edge sample(BlockPos a,BlockPos b,Rail rail) {
        try {
            double length=rail.getLength();if(!Double.isFinite(length)||length<=1e-6D)return null;
            List<Vec3> points=new ArrayList<>();int count=Math.max(2,Math.min(256,(int)Math.ceil(length/0.5D)));
            for(int i=0;i<=count;i++){Vec3 p=rail.getPosition(length*i/count);if(p==null||!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z))return null;points.add(p);}
            if(points.get(0).distanceToSqr(Vec3.atCenterOf(a))>points.get(points.size()-1).distanceToSqr(Vec3.atCenterOf(a)))Collections.reverse(points);
            // 与方速万向节点调用 MTR 建轨时相同的判据，不能用任意角度不适用的枚举引用比较。
            return new TurnoutRailGraph.Edge(a,b,NodeConnector.isGeometryUsable(rail),points);
        } catch(RuntimeException e){return null;}
    }
}
