package com.fangsu.network;

import com.fangsu.blockEntities.BlockEntityTurnout;
import com.fangsu.turnout.*;
import com.fangsu.turnout.marker.NodeMarker;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.List;

/** 已放置控制器的重建/解绑。初次放置由 TurnoutPlacementPayload 一次性完成。 */
public final class TurnoutBuildRequestPayload {
    public static final ResourceLocation CHANNEL=new ResourceLocation("fangsu","turnout_build_request");
    public record Payload(BlockPos pos,List<NodeMarker> markers,String railModelId,boolean unbind,List<TurnoutRailGraph.Link> hints) {
        public static Payload ofBuild(BlockPos pos,List<NodeMarker> markers,String model){return new Payload(pos,List.copyOf(markers),model,false,List.of());}
        public static Payload ofUnbind(BlockPos pos,String model){return new Payload(pos,List.of(),model,true,List.of());}
    }
    private TurnoutBuildRequestPayload() { }
    public static void registerServer(){NetworkManager.registerReceiver(NetworkManager.Side.C2S,CHANNEL,TurnoutBuildRequestPayload::receive);}
    public static void sendToServer(Payload payload) {
        FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());buf.writeBlockPos(payload.pos());buf.writeUtf(payload.railModelId()==null?"":payload.railModelId());buf.writeBoolean(payload.unbind());TurnoutPackets.writeMarkers(buf,payload.markers());
        var level=net.minecraft.client.Minecraft.getInstance().level;
        List<TurnoutRailGraph.Link> hints=!payload.hints().isEmpty()?payload.hints():payload.unbind()||level==null?List.of():TurnoutTopology.candidates(level,payload.pos(),payload.markers());
        TurnoutPackets.writeLinks(buf,hints);NetworkManager.sendToServer(CHANNEL,buf);
    }
    private static void receive(FriendlyByteBuf buf,NetworkManager.PacketContext ctx) {
        BlockPos pos=buf.readBlockPos();String model=buf.readUtf();boolean unbind=buf.readBoolean();List<NodeMarker> markers=TurnoutPackets.readMarkers(buf);List<TurnoutRailGraph.Link> hints=TurnoutPackets.readLinks(buf);
        ctx.queue(()->{
            if(!(ctx.getPlayer() instanceof ServerPlayer player))return;
            //#if MC_VERSION >= 12000
            Level level=player.level();
            //#else
            //$$ Level level=player.level;
            //#endif
            if(!level.hasChunkAt(pos)||player.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)>64||!(level.getBlockEntity(pos) instanceof BlockEntityTurnout entity))return;
            if(unbind){entity.clearBinding();TurnoutBindingPayload.sendSuccess(player,pos,null);return;}
            java.util.function.Consumer<TurnoutValidation> commit=result->{
                //#if MC_VERSION >= 12000
                if(player.level()!=level)return;
                //#else
                //$$ if(player.level!=level)return;
                //#endif
                if(!player.isAlive()||player.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)>64||level.getBlockEntity(pos)!=entity||entity.isRemoved()||!level.mayInteract(player,pos))return;
                if(result.isSupported()){entity.setBinding(result.binding());entity.refreshPowered();TurnoutBindingPayload.sendSuccess(player,pos,result.binding());}
                else TurnoutBindingPayload.sendFailure(player,pos,result.reasonKey(),"");
            };
            if(entity instanceof com.fangsu.blockEntities.BlockEntityRotatingRail)RotatingRailTopology.validateServer(level,pos,markers,model,hints,commit);
            else TurnoutTopology.validateServer(level,pos,markers,model,hints,commit);
        });
    }
}
