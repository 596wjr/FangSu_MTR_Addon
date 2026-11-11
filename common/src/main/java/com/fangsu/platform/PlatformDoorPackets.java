package com.fangsu.platform;

import com.fangsu.blockEntities.*;
import com.fangsu.items.ItemPlatformTool;
import com.fangsu.mappings.ComponentHelper;
import dev.architectury.event.events.common.*;
import dev.architectury.networking.NetworkManager;
import dev.potato.curvedplatform.geometry.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import java.util.*;
import java.util.concurrent.*;

/** 独立自动构件协议；客户端只提交两个节点和单元参数，服务器采轨、预览、校验和分帧施工。 */
public final class PlatformDoorPackets {
    private static final ResourceLocation REQUEST=new ResourceLocation("fangsu","platform_door_request"),RESULT=new ResourceLocation("fangsu","platform_door_result"),LOADED=new ResourceLocation("fangsu","platform_door_loaded");
    private static final ExecutorService GEOMETRY=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"fangsu-door-geometry");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private record Plan(UUID token,UUID group,ServerLevel level,List<BlockPos> nodes,PlatformRailSample rail,CompoundTag metadata,PlatformDoorPlanner.Plan geometry,Map<BlockPos,List<PlatformDoorComponent>> cells,Map<BlockPos,CompoundTag> expected,long revision,long expires) { }
    private static final Map<UUID,Plan> PLANS=new HashMap<>();
    private record Pending(ServerPlayer player,String request,long expires) { }
    private static final Map<UUID,Pending> PENDING=new HashMap<>();
    private static Pending begin(ServerPlayer player,String request){var pending=new Pending(player,request,System.currentTimeMillis()+30000);PENDING.put(player.getUUID(),pending);return pending;}
    private static boolean waiting(Pending pending){return pending.equals(PENDING.get(pending.player().getUUID()));}
    private static boolean complete(Pending pending){return PENDING.remove(pending.player().getUUID(),pending);}
    private static final Map<UUID,Job> JOBS=new HashMap<>();
    private static final class Undo {
        final CompoundTag world;final PlatformDoorLayouts.Cell previous;CompoundTag applied;
        Undo(ServerLevel level,BlockPos pos,PlatformDoorLayouts.Cell previous){world=PlatformBlockSnapshot.capture(level,pos);this.previous=previous;}
    }
    private static final class Job {
        final ServerPlayer player;final Plan plan;final PlatformDoorLayouts.Layout layout;final List<BlockPos> positions;final CompoundTag oldMetadata;final Map<BlockPos,Undo> undo=new LinkedHashMap<>();final ArrayDeque<BlockPos> rollback=new ArrayDeque<>();
        int index,placed,skipped;boolean cancelling,removing;String responseRequest;
        Job(ServerPlayer player,Plan plan,boolean removing,String request){
            this.player=player;this.plan=plan;this.removing=removing;responseRequest=request;var store=PlatformDoorLayouts.get(plan.level());var previous=store.get(plan.group());
            layout=previous==null?new PlatformDoorLayouts.Layout(plan.group(),player.getUUID(),plan.metadata()):previous;oldMetadata=layout.metadata.copy();layout.metadata=plan.metadata().copy();
            LinkedHashSet<BlockPos> all=new LinkedHashSet<>(plan.cells().keySet());all.addAll(layout.cells.keySet());positions=List.copyOf(all);store.put(layout);
        }
    }
    public static void registerServer(){
        LifecycleEvent.SERVER_STOPPED.register(server->{PLANS.entrySet().removeIf(e->e.getValue().level().getServer()==server);JOBS.entrySet().removeIf(e->e.getValue().plan.level().getServer()==server);PENDING.clear();});
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,REQUEST,(buffer,context)->{
            final int action;final String request,token,platform,doors;final List<BlockPos> nodes;final boolean left;final UUID source,group;final BlockPos clicked;
            try{
                action=buffer.readVarInt();request=buffer.readUtf(36);UUID.fromString(request);
                if(action==0){nodes=List.of(buffer.readBlockPos(),buffer.readBlockPos());platform=buffer.readUtf(32767);doors=buffer.readUtf(32767);left=buffer.readBoolean();source=buffer.readBoolean()?buffer.readUUID():null;group=buffer.readBoolean()?buffer.readUUID():null;token="";clicked=null;}
                else if(action>=1&&action<=4){clicked=action==4?buffer.readBlockPos():null;token=action==1?buffer.readUtf(36):"";group=action==3?buffer.readUUID():null;nodes=List.of();platform=doors="";left=true;source=null;}else return;
                if(buffer.readableBytes()!=0)return;
            }catch(RuntimeException e){return;}
            context.queue(()->{
                if(!(context.getPlayer() instanceof ServerPlayer player))return;
                if(!PlatformPlacementPolicy.canEdit(player)){respond(player,request,"permission",null,List.of());return;}
                if(!(player.getMainHandItem().getItem() instanceof ItemPlatformTool tool)||tool.role!=ItemPlatformTool.Role.SCREEN_DOOR){respond(player,request,"hold_tool",null,List.of());return;}
                ServerLevel level=level(player);
                if(action==4){query(player,clicked,request);return;}
                if(action==2){PENDING.remove(player.getUUID());PLANS.remove(player.getUUID());Job job=JOBS.get(player.getUUID());if(job==null)respond(player,request,"cancelled",null,List.of());else{job.cancelling=true;job.responseRequest=request;respond(player,request,"cancelling",job.plan,List.of(),false);}return;}
                if(JOBS.containsKey(player.getUUID())){respond(player,request,"busy",null,List.of());return;}
                if(action==3){var layout=PlatformDoorLayouts.get(level).get(group);if(!manageable(player,layout)||!nearLayout(player,layout)){respond(player,request,"permission",null,List.of());return;}if(busy(level,group)){respond(player,request,"busy",null,List.of());return;}
                    Map<BlockPos,CompoundTag> expected=new LinkedHashMap<>();for(BlockPos pos:layout.cells.keySet()){if(!PlatformPlacementPolicy.accessible(level,player,pos)){respond(player,request,"blocked",null,List.of(pos));return;}expected.put(pos,PlatformDoorAdapter.signature(level,pos));}
                    Plan removal=new Plan(UUID.randomUUID(),group,level,List.of(),PlatformRailSample.empty(),layout.metadata.copy(),null,Map.of(),expected,layout.revision,Long.MAX_VALUE);JOBS.put(player.getUUID(),new Job(player,removal,true,request));respond(player,request,"removing",removal,List.of());return;
                }
                if(action==0){prepare(player,request,nodes,platform,doors,left,source,group);return;}
                Plan plan=PLANS.remove(player.getUUID());if(plan==null||plan.level()!=level||plan.expires()<System.currentTimeMillis()||!plan.token().toString().equals(token)||revision(level,plan.group())!=plan.revision()||busy(level,plan.group())){respond(player,request,"stale",null,List.of());return;}
                var existing=PlatformDoorLayouts.get(level).get(plan.group());if(existing!=null&&!manageable(player,existing)){respond(player,request,"permission",null,List.of());return;}
                Pending pending=begin(player,request);PlatformRailSampler.sample(level,plan.nodes(),rail->{
                    if(!complete(pending)||player.isRemoved()||level(player)!=level)return;
                    if(!rail.equals(plan.rail())||revision(level,plan.group())!=plan.revision()||busy(level,plan.group())||!validate(player,plan)){respond(player,request,"stale",null,List.of());return;}
                    JOBS.put(player.getUUID(),new Job(player,plan,false,request));respond(player,request,"building",plan,List.of());
                });
            });
        });
        TickEvent.SERVER_POST.register(server->{
            long now=System.currentTimeMillis();PLANS.entrySet().removeIf(e->e.getValue().expires()<now);
            for(var pendingIterator=PENDING.values().iterator();pendingIterator.hasNext();){var pending=pendingIterator.next();if(level(pending.player()).getServer()!=server)continue;
                if(pending.player().isRemoved()||pending.expires()<now){pendingIterator.remove();if(!pending.player().isRemoved())respond(pending.player(),pending.request(),"timeout",null,List.of());}
            }int budget=64;
            for(var it=JOBS.values().iterator();it.hasNext()&&budget>0;){Job job=it.next();if(job.plan.level().getServer()!=server)continue;
                if(job.player.isRemoved()||level(job.player)!=job.plan.level()||!PlatformPlacementPolicy.canEdit(job.player)){if(!job.player.isRemoved())respond(job.player,job.responseRequest,"permission",job.plan,List.of(),false);it.remove();continue;}
                if(job.cancelling){
                    while(!job.rollback.isEmpty()&&budget-->0){BlockPos pos=job.rollback.removeLast();Undo undo=job.undo.get(pos);if(PlatformPlacementPolicy.accessible(job.plan.level(),job.player,pos)&&undo.applied!=null&&PlatformDoorAdapter.signature(job.plan.level(),pos).equals(undo.applied)&&PlatformBlockSnapshot.restore(job.plan.level(),pos,undo.world)){if(undo.previous==null)job.layout.cells.remove(pos);else job.layout.cells.put(pos,undo.previous);}else job.skipped++;}
                    if(job.rollback.isEmpty()){job.layout.metadata=job.oldMetadata;finish(job,"cancelled");it.remove();}continue;
                }
                while(job.index<job.positions.size()&&budget-->0){BlockPos pos=job.positions.get(job.index++);apply(job,pos);}
                PlatformDoorLayouts.get(job.plan.level()).put(job.layout);
                if(job.index==job.positions.size()){finish(job,job.removing?"removed":"finished");it.remove();}
            }
        });
    }
    private static void prepare(ServerPlayer player,String request,List<BlockPos> nodes,String platform,String doors,boolean left,UUID source,UUID editing){
        ServerLevel level=level(player);if(nodes.get(0).equals(nodes.get(1))||!near(player,nodes.get(0),64)||nodes.stream().anyMatch(p->!PlatformPlacementPolicy.accessible(level,player,p))){respond(player,request,"need_nodes",null,List.of());return;}
        final PlatformSettings platformSettings;final PlatformDoorSettings settings;try{platformSettings=PlatformPreset.decode(platform);settings=PlatformDoorSettings.decode(doors);}catch(RuntimeException e){respond(player,request,"invalid",null,List.of());return;}
        var store=PlatformDoorLayouts.get(level);var selected=editing==null?null:store.get(editing);final var existing=selected==null?store.find(source,nodes.get(0),nodes.get(1),left):selected;
        if(existing!=null&&!manageable(player,existing)){respond(player,request,"permission",null,List.of());return;}UUID group=existing==null?UUID.randomUUID():existing.id;if(busy(level,group)){respond(player,request,"busy",null,List.of());return;}
        long revision=existing==null?0:existing.revision;CompoundTag metadata=existing==null?new CompoundTag():existing.metadata.copy();metadata.putLong("first",nodes.get(0).asLong());metadata.putLong("last",nodes.get(1).asLong());metadata.putBoolean("left",left);metadata.putString("platform",platform);metadata.putString("doors",doors);if(source!=null)metadata.putUUID("source",source);else metadata.remove("source");metadata.putString("routes",settings.boardRoutes());metadata.putString("drawScript",settings.boardScript());
        Pending pending=begin(player,request);PLANS.remove(player.getUUID());PlatformRailSampler.sample(level,nodes,rail->{
            if(!waiting(pending))return;
            try{GEOMETRY.execute(()->{
                PlatformDoorPlanner.Plan geometry;try{geometry=PlatformDoorPlanner.plan(rail.points(),platformSettings.edgeOffset(),nodes.get(0).getY()+platformSettings.top(),left,settings);}catch(RuntimeException e){level.getServer().execute(()->{if(complete(pending))respond(player,request,"invalid",null,List.of());});return;}
                level.getServer().execute(()->{
                    if(!complete(pending)||player.isRemoved()||level(player)!=level)return;if(revision(level,group)!=revision){respond(player,request,"stale",null,List.of());return;}
                    Map<BlockPos,List<PlatformDoorComponent>> cells=new LinkedHashMap<>();geometry.cells().forEach((p,list)->cells.put(new BlockPos(p.x(),p.y(),p.z()),list.stream().map(c->new PlatformDoorComponent(c.kind(),c.dx(),c.dy(),c.dz(),c.yaw(),c.width(),c.height(),c.model(),c.variant(),c.options())).toList()));
                    LinkedHashSet<BlockPos> all=new LinkedHashSet<>(cells.keySet());if(existing!=null)all.addAll(existing.cells.keySet());Map<BlockPos,CompoundTag> expected=new LinkedHashMap<>();List<BlockPos> conflicts=new ArrayList<>();boolean accessible=true;
                    for(BlockPos pos:all){if(!PlatformPlacementPolicy.accessible(level,player,pos)){accessible=false;if(conflicts.size()<128)conflicts.add(pos);continue;}boolean collision=collision(player,group,pos,existing,cells.get(pos));if(collision&&conflicts.size()<128)conflicts.add(pos);expected.put(pos,PlatformDoorAdapter.signature(level,pos));}
                    Plan plan=new Plan(UUID.randomUUID(),group,level,List.copyOf(nodes),rail,metadata,geometry,Collections.unmodifiableMap(cells),expected,revision,System.currentTimeMillis()+120000);
                    String failure=!geometry.executable()?geometry.issues().get(0):!accessible||!conflicts.isEmpty()&&!PlatformPlacementPolicy.force(player)?"blocked":"";
                    if(!failure.isEmpty()){respond(player,request,failure,plan,conflicts,false);return;}
                    PLANS.put(player.getUUID(),plan);respond(player,request,"preview_ready",plan,conflicts);
                });
            });}catch(RejectedExecutionException e){if(complete(pending))respond(player,request,"busy",null,List.of());}
        });
    }
    private static boolean collision(ServerPlayer player,UUID group,BlockPos pos,PlatformDoorLayouts.Layout existing,List<PlatformDoorComponent> desired){
        ServerLevel level=level(player);if(desired==null)return false;
        if(existing!=null&&existing.cells.containsKey(pos)&&!PlatformDoorAdapter.matches(level,pos,group,existing.cells.get(pos).expected()))return true;
        if(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform curve&&PlatformDoorAdapter.canCoverCurve(curve,desired)&&!PlatformLayouts.get(level).canManage(player,curve.group))return true;
        return PlatformDoorAdapter.collision(level,pos,group,desired);
    }
    private static boolean validate(ServerPlayer player,Plan plan){var existing=PlatformDoorLayouts.get(plan.level()).get(plan.group());for(var e:plan.expected().entrySet())if(!PlatformPlacementPolicy.accessible(plan.level(),player,e.getKey())||(!PlatformPlacementPolicy.force(player)&&!PlatformDoorAdapter.signature(plan.level(),e.getKey()).equals(e.getValue()))||collision(player,plan.group(),e.getKey(),existing,plan.cells().get(e.getKey()))&&!PlatformPlacementPolicy.force(player))return false;return true;}
    private static void apply(Job job,BlockPos pos){
        ServerLevel level=job.plan.level();if(!PlatformPlacementPolicy.accessible(level,job.player,pos)||(!PlatformPlacementPolicy.force(job.player)&&!PlatformDoorAdapter.signature(level,pos).equals(job.plan.expected().get(pos)))){job.skipped++;return;}
        var previous=job.layout.cells.get(pos);var desired=job.plan.cells().get(pos);Undo undo=new Undo(level,pos,previous);
        if(desired!=null){
            if(collision(job.player,job.plan.group(),pos,job.layout,desired)&&!PlatformPlacementPolicy.force(job.player)){job.skipped++;return;}
            CompoundTag original=previous==null||!PlatformDoorAdapter.matches(level,pos,job.plan.group(),previous.expected())?undo.world:previous.original();if(!PlatformDoorAdapter.place(level,pos,job.plan.group(),desired,job.plan.metadata())){PlatformBlockSnapshot.restore(level,pos,undo.world);job.skipped++;return;}
            var expected=PlatformDoorAdapter.signature(level,pos);job.layout.cells.put(pos,new PlatformDoorLayouts.Cell(original.copy(),expected.copy()));job.placed++;
        }else if(previous!=null){
            if(!PlatformDoorAdapter.matches(level,pos,job.plan.group(),previous.expected())){job.layout.cells.remove(pos);job.skipped++;return;}
            if(!PlatformBlockSnapshot.restore(level,pos,previous.original())){job.skipped++;return;}job.layout.cells.remove(pos);
        }else return;
        undo.applied=PlatformDoorAdapter.signature(level,pos);job.undo.put(pos,undo);job.rollback.addLast(pos);
    }
    private static void finish(Job job,String key){var store=PlatformDoorLayouts.get(job.plan.level());if(job.layout.cells.isEmpty())store.remove(job.layout.id);else store.put(job.layout);respond(job.player,job.responseRequest,key,job.plan,List.of(),false);}
    private static void query(ServerPlayer player,BlockPos pos,String request){
        ServerLevel level=level(player);if(!near(player,pos,8)||!level.hasChunkAt(pos)){respond(player,request,"hint",null,List.of());return;}
        var owner=PlatformOverhangs.owner(level,pos);if(owner!=null)pos=owner;
        var store=PlatformDoorLayouts.get(level);PlatformDoorLayouts.Layout layout=null;CompoundTag metadata=null;
        if(level.getBlockEntity(pos) instanceof BlockEntityAutoPlatformComponent host)layout=store.get(host.group);
        if(layout!=null){if(!manageable(player,layout)){respond(player,request,"permission",null,List.of());return;}metadata=layout.metadata;}
        else{
            CompoundTag curve=level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform host?host.serverData():PlatformOverlays.get(level).entry(pos)==null?null:PlatformOverlays.get(level).entry(pos).cell();
            if(curve!=null&&curve.hasUUID("group")){UUID source=curve.getUUID("group");BlockPos first=BlockPos.of(curve.getLong("first")),last=BlockPos.of(curve.getLong("last"));boolean left=curve.getBoolean("left");layout=store.find(source,first,last,left);if(layout!=null){if(!manageable(player,layout)){respond(player,request,"permission",null,List.of());return;}metadata=layout.metadata;}else{metadata=new CompoundTag();metadata.putUUID("source",source);metadata.putLong("first",first.asLong());metadata.putLong("last",last.asLong());metadata.putBoolean("left",left);metadata.putString("platform",curve.getString("settings"));metadata.putString("doors",PlatformDoorSettings.encode(PlatformDoorSettings.DEFAULT));}}
        }
        if(metadata==null){respond(player,request,"hint",null,List.of());return;}FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());b.writeUtf(request);b.writeBoolean(metadata.hasUUID("source"));if(metadata.hasUUID("source"))b.writeUUID(metadata.getUUID("source"));b.writeBoolean(layout!=null);if(layout!=null)b.writeUUID(layout.id);b.writeBlockPos(BlockPos.of(metadata.getLong("first")));b.writeBlockPos(BlockPos.of(metadata.getLong("last")));b.writeBoolean(metadata.getBoolean("left"));b.writeUtf(metadata.getString("platform"));b.writeUtf(metadata.getString("doors"));NetworkManager.sendToPlayer(player,LOADED,b);
    }
    private static boolean manageable(ServerPlayer player,PlatformDoorLayouts.Layout layout){return PlatformDoorLayouts.get(level(player)).canManage(player,layout);}
    private static boolean nearLayout(ServerPlayer player,PlatformDoorLayouts.Layout layout){for(var pos:layout.cells.keySet())if(near(player,pos,64))return true;return false;}
    private static boolean near(ServerPlayer player,BlockPos pos,int radius){return player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=radius*(double)radius;}
    private static long revision(ServerLevel level,UUID group){var layout=PlatformDoorLayouts.get(level).get(group);return layout==null?0:layout.revision;}
    private static boolean busy(ServerLevel level,UUID group){return JOBS.values().stream().anyMatch(j->j.plan.level()==level&&j.plan.group().equals(group));}
    private static ServerLevel level(ServerPlayer player){
        //#if MC_VERSION >= 12000
        return (ServerLevel)player.level();
        //#else
        //$$ return (ServerLevel)player.level;
        //#endif
    }
    private static void respond(ServerPlayer player,String request,String key,Plan plan,List<BlockPos> conflicts){respond(player,request,key,plan,conflicts,key.equals("preview_ready"));}
    private static void respond(ServerPlayer player,String request,String key,Plan plan,List<BlockPos> conflicts,boolean token){
        FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());b.writeUtf(request);b.writeUtf("ui.fangsu.curve.door_"+key);b.writeUtf(token&&plan!=null?plan.token().toString():"");b.writeBoolean(plan!=null);if(plan!=null)b.writeUUID(plan.group());b.writeVarInt(plan==null||plan.geometry()==null?0:plan.geometry().doors());b.writeVarInt(plan==null||plan.geometry()==null?0:plan.geometry().glass());var points=plan==null||plan.geometry()==null?List.<CurvedPlatformPlanner.Point>of():plan.geometry().points();b.writeVarInt(points.size());for(var p:points){b.writeDouble(p.x());b.writeDouble(p.y());b.writeDouble(p.z());}b.writeVarInt(conflicts.size());for(var pos:conflicts)b.writeBlockPos(pos);NetworkManager.sendToPlayer(player,RESULT,b);
    }
    public static void registerClient(){
        NetworkManager.registerReceiver(NetworkManager.Side.S2C,RESULT,(b,context)->{try{String request=b.readUtf(36),key=b.readUtf(128),token=b.readUtf(36);UUID group=b.readBoolean()?b.readUUID():null;int doors=b.readVarInt(),glass=b.readVarInt(),count=b.readVarInt();if(!key.startsWith("ui.fangsu.curve.door_")||doors<0||glass<0||count<0||count>4096)return;List<CurvedPlatformPlanner.Point> points=new ArrayList<>();for(int i=0;i<count;i++)points.add(new CurvedPlatformPlanner.Point(b.readDouble(),b.readDouble(),b.readDouble()));int n=b.readVarInt();if(n<0||n>128)return;List<BlockPos> conflicts=new ArrayList<>();for(int i=0;i<n;i++)conflicts.add(b.readBlockPos());if(b.readableBytes()!=0)return;context.queue(()->PlatformDoorClient.receive(request,key,token,group,doors,glass,List.copyOf(points),List.copyOf(conflicts)));}catch(RuntimeException ignored){}});
        NetworkManager.registerReceiver(NetworkManager.Side.S2C,LOADED,(b,context)->{try{String request=b.readUtf(36);UUID source=b.readBoolean()?b.readUUID():null,group=b.readBoolean()?b.readUUID():null;List<BlockPos> nodes=List.of(b.readBlockPos(),b.readBlockPos());boolean left=b.readBoolean();PlatformSettings platform=PlatformPreset.decode(b.readUtf(32767));PlatformDoorSettings doors=PlatformDoorSettings.decode(b.readUtf(32767));if(b.readableBytes()!=0)return;context.queue(()->PlatformDoorClient.loaded(request,source,group,nodes,platform,left,doors));}catch(RuntimeException ignored){}});
    }
    public static void preview(List<BlockPos> nodes,PlatformSettings platform,boolean left,UUID source,UUID group,PlatformDoorSettings doors,String request){if(nodes.size()!=2)return;FriendlyByteBuf b=buffer(0,request);nodes.forEach(b::writeBlockPos);b.writeUtf(PlatformPreset.encode(platform));b.writeUtf(PlatformDoorSettings.encode(doors));b.writeBoolean(left);b.writeBoolean(source!=null);if(source!=null)b.writeUUID(source);b.writeBoolean(group!=null);if(group!=null)b.writeUUID(group);send(b);}
    public static void build(String token,String request){FriendlyByteBuf b=buffer(1,request);b.writeUtf(token);send(b);}
    public static void cancel(String request){send(buffer(2,request));}
    public static void remove(UUID group,String request){if(group==null)return;FriendlyByteBuf b=buffer(3,request);b.writeUUID(group);send(b);}
    public static void query(BlockPos pos,String request){FriendlyByteBuf b=buffer(4,request);b.writeBlockPos(pos);send(b);}
    private static FriendlyByteBuf buffer(int action,String request){FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());b.writeVarInt(action);b.writeUtf(request);return b;}
    private static void send(FriendlyByteBuf b){NetworkManager.sendToServer(REQUEST,b);}
    private PlatformDoorPackets(){ }
}
