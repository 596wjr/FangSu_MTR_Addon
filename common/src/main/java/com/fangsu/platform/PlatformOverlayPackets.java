package com.fangsu.platform;

import dev.architectury.networking.NetworkManager;
import dev.architectury.event.events.common.TickEvent;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import java.util.*;
import java.io.*;
import java.util.zip.*;

/** 覆盖层使用附近区块增量版本请求，快照分帧发送；世界原生方块和它们的碰撞始终保持原样。 */
public final class PlatformOverlayPackets {
    private static final ResourceLocation REQUEST=new ResourceLocation("fangsu","platform_overlay_delta_request"),SYNC=new ResourceLocation("fangsu","platform_overlay_delta");
    private record ChunkStamp(long revision,int cells) { }
    private record Sent(ServerPlayer player,ServerLevel level,Map<Long,ChunkStamp> chunks) { }
    private static final class Delivery {
        final Sent sent;final Iterator<BlockPos> positions;final UUID id=UUID.randomUUID();
        Delivery(Sent sent,List<BlockPos> positions){this.sent=sent;this.positions=positions.iterator();}
    }
    private static final Map<UUID,Delivery> PENDING=new HashMap<>();
    private static final Map<UUID,Sent> SENT=new HashMap<>();
    private static final Map<UUID,Long> REQUESTED=new HashMap<>();
    private static int roundRobin;
    public static void registerServer(){
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,REQUEST,(buffer,context)->{
            if(buffer.readableBytes()!=0)return;
            context.queue(()->{
                if(!(context.getPlayer() instanceof ServerPlayer player)||PENDING.containsKey(player.getUUID()))return;
                long now=System.currentTimeMillis();if(now-REQUESTED.getOrDefault(player.getUUID(),0L)<1000)return;REQUESTED.put(player.getUUID(),now);
                queue(player);
            });
        });
        TickEvent.SERVER_POST.register(server->{
            int budget=16,bytesBudget=256*1024;var players=new ArrayList<>(PENDING.keySet());if(players.isEmpty())return;
            Collections.rotate(players,-Math.floorMod(roundRobin++,players.size()));
            for(UUID player:players){if(budget<=0||bytesBudget<=0)break;var d=PENDING.get(player);if(d==null||d.sent.level().getServer()!=server)continue;
                var recipient=d.sent.player();
                if(recipient.isRemoved()||world(recipient)!=d.sent.level()){SENT.remove(player);PENDING.remove(player);continue;}
                int perPlayer=4;
                while(d.positions.hasNext()&&budget>0&&bytesBudget>0&&perPlayer-->0){budget--;BlockPos pos=d.positions.next();var source=PlatformOverlays.get(d.sent.level());if(source.entry(pos)==null)continue;
                    try{
                        byte[] encoded=encode(source.clientPacket(pos));
                        bytesBudget-=encoded.length;
                        var packet=new FriendlyByteBuf(Unpooled.buffer());packet.writeVarInt(1);packet.writeUUID(d.id);packet.writeByteArray(encoded);
                        NetworkManager.sendToPlayer(recipient,SYNC,packet);
                    }catch(IOException e){com.fangsu.Main.LOGGER.warn("[FangSu] Platform overlay packet rejected at {}",pos);}
                }
                if(!d.positions.hasNext()){var end=new FriendlyByteBuf(Unpooled.buffer());end.writeVarInt(2);end.writeUUID(d.id);NetworkManager.sendToPlayer(recipient,SYNC,end);SENT.put(player,d.sent);PENDING.remove(player);}
            }
        });
        dev.architectury.event.events.common.PlayerEvent.PLAYER_QUIT.register(player->{REQUESTED.remove(player.getUUID());SENT.remove(player.getUUID());PENDING.remove(player.getUUID());});
        dev.architectury.event.events.common.LifecycleEvent.SERVER_STOPPED.register(server->{PENDING.clear();SENT.clear();REQUESTED.clear();});
    }
    private static void queue(ServerPlayer player){
        ServerLevel level=world(player);
        var overlays=PlatformOverlays.get(level);
        Map<Long,List<BlockPos>> byChunk=new LinkedHashMap<>();
        for(var pos:overlays.nearby(level,player.blockPosition()))byChunk.computeIfAbsent(PlatformOverlays.chunk(pos),key->new ArrayList<>()).add(pos);
        Sent previous=SENT.get(player.getUUID());
        boolean reset=previous==null||previous.player()!=player||previous.level()!=level;
        Map<Long,ChunkStamp> known=reset?Map.of():previous.chunks();
        Map<Long,ChunkStamp> current=new HashMap<>();
        Set<Long> replaced=new HashSet<>(known.keySet());
        List<BlockPos> positions=new ArrayList<>();
        byChunk.forEach((chunk,cells)->{
            var stamp=new ChunkStamp(overlays.chunkRevision(chunk),cells.size());
            current.put(chunk,stamp);
            if(stamp.equals(known.get(chunk)))replaced.remove(chunk);
            else{replaced.add(chunk);positions.addAll(cells);}
        });
        if(!reset&&replaced.isEmpty())return;
        var delivery=new Delivery(new Sent(player,level,current),positions);
        PENDING.put(player.getUUID(),delivery);
        var begin=new FriendlyByteBuf(Unpooled.buffer());begin.writeVarInt(0);begin.writeUUID(delivery.id);begin.writeResourceLocation(level.dimension().location());
        begin.writeBoolean(reset);begin.writeVarInt(replaced.size());replaced.forEach(begin::writeLong);
        NetworkManager.sendToPlayer(player,SYNC,begin);
    }
    private static ServerLevel world(ServerPlayer player){
        //#if MC_VERSION >= 12000
        return (ServerLevel)player.level();
        //#else
        //$$ return (ServerLevel)player.level;
        //#endif
    }
    private static byte[] encode(net.minecraft.nbt.CompoundTag tag) throws IOException {
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());byte[] raw;
        try{buffer.writeNbt(tag);if(buffer.readableBytes()>2*1024*1024)throw new IOException("overlay_too_large");raw=new byte[buffer.readableBytes()];buffer.readBytes(raw);}finally{buffer.release();}
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(GZIPOutputStream zip=new GZIPOutputStream(out)){zip.write(raw);}byte[] bytes=out.toByteArray();if(bytes.length>900000)throw new IOException("overlay_too_large");return bytes;
    }
    private static net.minecraft.nbt.CompoundTag decode(byte[] bytes) throws IOException {
        byte[] raw;try(GZIPInputStream zip=new GZIPInputStream(new ByteArrayInputStream(bytes))){raw=zip.readNBytes(2*1024*1024+1);}if(raw.length>2*1024*1024)throw new IOException("overlay_too_large");
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.wrappedBuffer(raw));try{return buffer.readNbt();}finally{buffer.release();}
    }
    public static void request(){NetworkManager.sendToServer(REQUEST,new FriendlyByteBuf(Unpooled.buffer()));}
    public static void registerClient(){NetworkManager.registerReceiver(NetworkManager.Side.S2C,SYNC,(buffer,context)->{
        int kind=buffer.readVarInt();UUID id=buffer.readUUID();ResourceLocation dimension=kind==0?buffer.readResourceLocation():null;
        boolean reset=kind==0&&buffer.readBoolean();
        Set<Long> replaced=kind==0?new HashSet<>():Set.of();
        if(kind==0){int count=buffer.readVarInt();if(count<0||count>242)return;for(int i=0;i<count;i++)replaced.add(buffer.readLong());}
        net.minecraft.nbt.CompoundTag cell;try{cell=kind==1?decode(buffer.readByteArray(900000)):null;}catch(IOException|RuntimeException e){return;}
        context.queue(()->PlatformOverlayClient.receive(kind,id,dimension,cell,reset,replaced));
    });}
}
