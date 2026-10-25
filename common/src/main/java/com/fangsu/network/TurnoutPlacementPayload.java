package com.fangsu.network;

import com.fangsu.blockEntities.BlockEntityTurnout;
import com.fangsu.items.ItemTurnout;
import com.fangsu.turnout.*;
import com.fangsu.turnout.marker.NodeMarker;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** 放置意图与标记同包发送。失败时不产生方块、不扣物品，也没有“先放再等构建”的竞态。 */
public final class TurnoutPlacementPayload {
    public static final ResourceLocation CHANNEL=new ResourceLocation("fangsu","turnout_place");
    private TurnoutPlacementPayload() { }
    public static void registerServer(){NetworkManager.registerReceiver(NetworkManager.Side.C2S,CHANNEL,TurnoutPlacementPayload::receive);}
    public static void send(UseOnContext context,BlockPos target,List<NodeMarker> markers,TurnoutBinding checked) {
        FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf(context.getLevel().dimension().location().toString());buf.writeBlockPos(context.getClickedPos());
        buf.writeEnum(context.getClickedFace());buf.writeEnum(context.getHand());
        Vec3 hit=context.getClickLocation();buf.writeDouble(hit.x);buf.writeDouble(hit.y);buf.writeDouble(hit.z);buf.writeBoolean(context.isInside());
        java.util.Set<TurnoutRailGraph.Link> links=new java.util.LinkedHashSet<>();
        for(TurnoutTrack track:checked.tracks())for(int i=1;i<track.nodes().size();i++)links.add(new TurnoutRailGraph.Link(track.nodes().get(i-1),track.nodes().get(i)));
        TurnoutPackets.writeMarkers(buf,markers);TurnoutPackets.writeLinks(buf,List.copyOf(links));
        NetworkManager.sendToServer(CHANNEL,buf);
    }
    private static void receive(FriendlyByteBuf buf,NetworkManager.PacketContext ctx) {
        String dimension=buf.readUtf();BlockPos clicked=buf.readBlockPos();Direction face=buf.readEnum(Direction.class);InteractionHand hand=buf.readEnum(InteractionHand.class);
        Vec3 hit=new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble());boolean inside=buf.readBoolean();
        List<NodeMarker> markers=TurnoutPackets.readMarkers(buf);List<TurnoutRailGraph.Link> hints=TurnoutPackets.readLinks(buf);
        ctx.queue(()->{
            if(!(ctx.getPlayer() instanceof ServerPlayer player))return;
            //#if MC_VERSION >= 12000
            Level level=player.level();
            //#else
            //$$ Level level=player.level;
            //#endif
            if(!dimension.equals(level.dimension().location().toString())||!level.hasChunkAt(clicked)||player.distanceToSqr(clicked.getX()+0.5,clicked.getY()+0.5,clicked.getZ()+0.5)>64||
                    !Double.isFinite(hit.x)||!Double.isFinite(hit.y)||!Double.isFinite(hit.z)||hit.distanceToSqr(Vec3.atCenterOf(clicked))>4||!(player.getItemInHand(hand).getItem() instanceof ItemTurnout))return;
            BlockHitResult target=new BlockHitResult(hit,face,clicked,inside);
            BlockPlaceContext placement=new BlockPlaceContext(new UseOnContext(player,hand,target));BlockPos pos=placement.getClickedPos();
            if(!placement.canPlace()||!level.mayInteract(player,pos)){reject(player,pos);return;}
            TurnoutTopology.validateServer(level,pos,markers,BlockEntityTurnout.DEFAULT_RAIL_MODEL,hints,result->{
                // TSC 回包期间玩家可能换手、离开或切换维度，提交前再检查一次。
                //#if MC_VERSION >= 12000
                boolean sameWorld=player.level()==level;
                //#else
                //$$ boolean sameWorld=player.level==level;
                //#endif
                if(!sameWorld||!player.isAlive()||player.distanceToSqr(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5)>64)return;
                if(!result.isSupported()){TurnoutBindingPayload.sendFailure(player,pos,result.reasonKey(),"");return;}
                if(!(player.getItemInHand(hand).getItem() instanceof ItemTurnout item)){reject(player,pos);return;}
                BlockPlaceContext current=new BlockPlaceContext(new UseOnContext(player,hand,target));
                if(!current.getClickedPos().equals(pos)||!current.canPlace()||!level.mayInteract(player,pos)){reject(player,pos);return;}
                if(!item.placeValidated(current,result.binding()).consumesAction()){reject(player,pos);return;}
                if(level.getBlockEntity(pos) instanceof BlockEntityTurnout entity){if(!entity.hasBinding())entity.setBinding(result.binding());entity.refreshPowered();TurnoutBindingPayload.sendSuccess(player,pos,result.binding());}
            });
        });
    }
    private static void reject(ServerPlayer player,BlockPos pos){TurnoutBindingPayload.sendFailure(player,pos,TurnoutValidation.HINT_CANNOT_PLACE,"");}
}
