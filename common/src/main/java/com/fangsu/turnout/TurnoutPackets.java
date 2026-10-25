package com.fangsu.turnout;
import com.fangsu.turnout.marker.MarkerColor;
import com.fangsu.turnout.marker.NodeMarker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.List;
/** C2S 的有界公共编码。拒绝超限，不能截短后继续按错位数据解包。 */
public final class TurnoutPackets {
    private TurnoutPackets() { }
    public static void writeMarkers(FriendlyByteBuf buf,List<NodeMarker> list){buf.writeVarInt(list.size());for(NodeMarker m:list){buf.writeBlockPos(m.pos());buf.writeBoolean(m.color()==MarkerColor.BLUE);}}
    public static List<NodeMarker> readMarkers(FriendlyByteBuf buf){int size=buf.readVarInt();if(size<0||size>TurnoutTopology.MAX_MARKERS)throw new IllegalArgumentException("标记数超限");List<NodeMarker> out=new ArrayList<>();for(int i=0;i<size;i++)out.add(new NodeMarker(buf.readBlockPos(),buf.readBoolean()?MarkerColor.BLUE:MarkerColor.RED));return List.copyOf(out);}
    public static void writeLinks(FriendlyByteBuf buf,List<TurnoutRailGraph.Link> list){buf.writeVarInt(list.size());for(var link:list){buf.writeBlockPos(link.a());buf.writeBlockPos(link.b());}}
    public static List<TurnoutRailGraph.Link> readLinks(FriendlyByteBuf buf){int size=buf.readVarInt();if(size<0||size>TurnoutRailAccess.MAX_LINKS)throw new IllegalArgumentException("轨道查询数超限");List<TurnoutRailGraph.Link> out=new ArrayList<>();for(int i=0;i<size;i++)out.add(new TurnoutRailGraph.Link(buf.readBlockPos(),buf.readBlockPos()));return List.copyOf(out);}
}
