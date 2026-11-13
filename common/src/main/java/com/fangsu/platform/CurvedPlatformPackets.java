package com.fangsu.platform;

import com.fangsu.items.ModItems;
import com.fangsu.blockEntities.BlockEntityCurvedPlatform;
import com.fangsu.blocks.ModBlocks;
import com.fangsu.mappings.ComponentHelper;
import dev.architectury.networking.NetworkManager;
import dev.architectury.event.events.common.TickEvent;
import dev.potato.curvedplatform.geometry.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
import java.util.concurrent.*;

/** Potato 几何内核的服务端适配：客户端只交参数，真实轨道、冲突、归属与分帧写入由服务器决定。 */
public final class CurvedPlatformPackets {
    private static final ResourceLocation REQUEST=new ResourceLocation("fangsu","curved_platform_request"),RESULT=new ResourceLocation("fangsu","curved_platform_result");
    private static final ExecutorService GEOMETRY=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"fangsu-platform-geometry");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private record Plan(UUID token,UUID group,ServerLevel level,List<BlockPos> nodes,PlatformRailSample samples,Map<BlockPos,CellGeometry> cells,PlatformSettings settings,boolean left,boolean editing,long expires,long revision,List<BlockPos> conflicts) { }
    private record Pending(ServerLevel level,boolean editing) { }
    private static final Map<UUID,Plan> PLANS=new HashMap<>();
    private static final Map<UUID,Pending> REQUESTS=new HashMap<>();
    private static final Map<UUID,Future<?>> PREPARING=new HashMap<>();
    private static void stopPreparing(UUID player){var task=PREPARING.remove(player);if(task!=null)task.cancel(true);}
    private static final Map<UUID,Job> JOBS=new HashMap<>();
    private static final TicketType<UUID> BUILD_TICKET=TicketType.create("fangsu_platform_build",Comparator.<UUID>naturalOrder());
    private record ChunkWait(ServerPlayer player,Pending pending,BuildChunks chunks,long deadline,Runnable ready) { }
    private static final List<ChunkWait> CHUNK_WAITS=new ArrayList<>();

    /** 临时保留施工和回滚需要的区块，按批提交加载请求。 */
    private static final class BuildChunks {
        final ServerLevel level;
        final UUID ticket=UUID.randomUUID();
        final Map<ChunkPos,BlockPos> positions=new LinkedHashMap<>();
        final List<ChunkPos> held=new ArrayList<>();
        final Iterator<ChunkPos> queue;
        boolean checking,transferred;
        BuildChunks(Plan plan){
            level=plan.level();
            plan.cells().keySet().forEach(this::add);
            for(long member:PlatformLayouts.get(level).cells(plan.group()))add(BlockPos.of(member));
            queue=positions.keySet().iterator();
        }
        void add(BlockPos pos){positions.putIfAbsent(new ChunkPos(pos),pos);}
        int acquire(int budget){
            while(budget>0&&queue.hasNext()){
                var pos=queue.next();
                level.getChunkSource().addRegionTicket(BUILD_TICKET,pos,0,ticket);
                held.add(pos);budget--;
            }
            return budget;
        }
        boolean ready(){return !queue.hasNext()&&positions.values().stream().allMatch(pos->PlatformPlacementPolicy.loaded(level,pos));}
        void release(){
            for(var pos:held)level.getChunkSource().removeRegionTicket(BUILD_TICKET,pos,0,ticket);
            held.clear();
        }
    }
    private static void tickChunkWaits(net.minecraft.server.MinecraftServer server){
        int budget=8;
        for(var it=CHUNK_WAITS.iterator();it.hasNext();){
            var wait=it.next();var player=wait.player();var pending=wait.pending();UUID id=player.getUUID();
            if(pending.level().getServer()!=server)continue;
            if(REQUESTS.get(id)!=pending||player.isRemoved()||currentLevel(player)!=pending.level()){
                it.remove();REQUESTS.remove(id,pending);if(!wait.chunks().transferred)wait.chunks().release();continue;
            }
            if(System.currentTimeMillis()>wait.deadline()){
                it.remove();REQUESTS.remove(id,pending);wait.chunks().release();
                respond(player,"ui.fangsu.curve.chunk_load_timeout",null,"");continue;
            }
            if(!wait.chunks().checking){
                budget=wait.chunks().acquire(budget);
                if(wait.chunks().ready()){
                    wait.chunks().checking=true;
                    try{wait.ready().run();}
                    catch(RuntimeException e){
                        it.remove();REQUESTS.remove(id,pending);
                        if(!wait.chunks().transferred)wait.chunks().release();
                        com.fangsu.Main.LOGGER.warn("[FangSu] Unable to start curved platform construction",e);
                        respond(player,"ui.fangsu.curve.invalid",null,"");
                    }
                }
            }
        }
    }
    public static boolean busy(ServerLevel level,UUID group){return JOBS.values().stream().anyMatch(job->job.plan.level()==level&&job.plan.group().equals(group));}
    public static void cancelGroup(ServerLevel level,UUID group){
        JOBS.values().forEach(job->{if(job.plan.level()==level&&job.plan.group().equals(group))job.cancelled=true;});
        PLANS.values().removeIf(plan->plan.level()==level&&plan.group().equals(group));
    }
    private static final class Job {
        final ServerPlayer player;final Plan plan;final List<BlockPos> positions;BuildChunks chunks;int index,placed,skipped;boolean cancelled,rollingBack;BlockPos failed;
        final Map<BlockPos,Backup> journal=new LinkedHashMap<>();final ArrayDeque<BlockPos> undo=new ArrayDeque<>();
        Job(ServerPlayer player,Plan plan){this.player=player;this.plan=plan;LinkedHashSet<BlockPos> all=new LinkedHashSet<>(plan.cells().keySet());for(long p:PlatformLayouts.get(plan.level()).cells(plan.group()))all.add(BlockPos.of(p));positions=List.copyOf(all);PlatformLayouts.get(plan.level()).ownerIfAbsent(plan.group(),player.getUUID());PlatformLayouts.get(plan.level()).put(plan.group(),positions);}
        void release(){if(chunks!=null)chunks.release();}
        void remember(BlockPos pos){if(!journal.containsKey(pos)){journal.put(pos,new Backup(plan.level(),pos));undo.addLast(pos);}}
        void applied(BlockPos pos){journal.get(pos).expected=plan.level().getBlockState(pos);}
        void skip(BlockPos pos){skipped++;if(plan.editing()){failed=pos;rollingBack=true;}}
    }
    private static final class Backup {
        final net.minecraft.nbt.CompoundTag snapshot;
        net.minecraft.world.level.block.state.BlockState expected;
        Backup(ServerLevel level,BlockPos pos){snapshot=PlatformBlockSnapshot.capture(level,pos);}
        void restore(ServerLevel level,BlockPos pos,UUID group){
            if(expected==null||!level.hasChunkAt(pos)||!level.getBlockState(pos).equals(expected))return;
            if(expected.is(ModBlocks.BLOCK_CURVED_PLATFORM.get())&&(!(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform host)||!group.equals(host.group)))return;
            PlatformBlockSnapshot.restore(level,pos,snapshot);
        }
    }
    public static void registerServer(){
        PlatformOverlayPackets.registerServer();
        PlatformBreakHandling.init();
        dev.architectury.event.events.common.LifecycleEvent.SERVER_STOPPING.register(server->{
            CHUNK_WAITS.removeIf(wait->{if(wait.pending().level().getServer()!=server)return false;if(!wait.chunks().transferred)wait.chunks().release();return true;});
            JOBS.values().stream().filter(job->job.plan.level().getServer()==server).forEach(Job::release);
        });
        dev.architectury.event.events.common.LifecycleEvent.SERVER_STOPPED.register(server->{
            PlatformMutations.clear(server);
            PLANS.entrySet().removeIf(e->e.getValue().level().getServer()==server);JOBS.entrySet().removeIf(e->e.getValue().plan.level().getServer()==server);
            for(var task:PREPARING.values())task.cancel(true);PREPARING.clear();REQUESTS.clear();
        });
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,REQUEST,(buffer,context)->{
            final int action;final String request,token,config;final List<BlockPos> nodes=new ArrayList<>();final boolean left;final UUID group;final BlockPos clicked;
            try{
                action=buffer.readVarInt();if(action==0){request=buffer.readUtf(36);nodes.add(buffer.readBlockPos());nodes.add(buffer.readBlockPos());config=buffer.readUtf(32767);left=buffer.readBoolean();group=buffer.readBoolean()?buffer.readUUID():null;token="";clicked=null;}
                else if(action>=1&&action<=4){request="";token=action==1?buffer.readUtf(36):"";group=action==3?buffer.readUUID():null;clicked=action==4?buffer.readBlockPos():null;config="";left=true;}else return;
                if(buffer.readableBytes()!=0)return;
            }catch(RuntimeException e){return;}
            context.queue(()->{
                if(!(context.getPlayer() instanceof ServerPlayer player))return;
                if(action==4){requestBreak(player,clicked);return;}
                if(!PlatformPlacementPolicy.canEdit(player)){respond(player,"ui.fangsu.curve.permission",null,request);return;}
                ServerLevel level=currentLevel(player);
                UUID playerId=player.getUUID();
                Plan prepared=PLANS.get(playerId);
                Pending preparing=REQUESTS.get(playerId);
                Job running=JOBS.get(playerId);
                var held=player.getMainHandItem();
                boolean builder=held.is(ModItems.ITEM_CURVED_PLATFORM.get()),editor=held.is(ModItems.ITEM_PLATFORM_CONFIGURATOR.get());
                boolean wrench=held.is(ModItems.ITEM_WRENCH.get());
                boolean allowed=switch(action){
                    case 0->editor||wrench&&group!=null;
                    case 1->builder||wrench&&isEdit(prepared,level);
                    case 2->editor||builder||wrench&&(isEdit(prepared,level)
                            ||preparing!=null&&preparing.editing()&&preparing.level()==level
                            ||running!=null&&isEdit(running.plan,level));
                    case 3->editor;
                    default->false;
                };
                if(!allowed){respond(player,"ui.fangsu.curve.hold_tool",null,request);return;}
                if(action==2){
                    stopPreparing(playerId);REQUESTS.remove(playerId);PLANS.remove(playerId);
                    if(running!=null)running.rollingBack=true;
                    else respond(player,"ui.fangsu.curve.cancelled",null,request);
                    return;
                }
                if(running!=null){respond(player,"ui.fangsu.curve.building",null,request);return;}
                var layouts=PlatformLayouts.get(level);
                if(action==3){
                    long[] members=layouts.cells(group);if(members.length==0||!anyNear(player,members)||!layouts.canManage(player,group))return;
                    for(long packed:members){BlockPos pos=BlockPos.of(packed);if(!level.hasChunkAt(pos)||!level.mayInteract(player,pos)){respond(player,"ui.fangsu.curve.stale",null,request);return;}}
                    if(busy(level,group)){respond(player,"ui.fangsu.curve.stale",null,request);return;}
                    Plan removal=new Plan(UUID.randomUUID(),group,level,List.of(),PlatformRailSample.empty(),Map.of(),PlatformSettings.DEFAULT,true,false,Long.MAX_VALUE,0,List.of());
                    stopPreparing(playerId);REQUESTS.remove(playerId);PLANS.remove(playerId);
                    JOBS.put(playerId,new Job(player,removal));return;
                }
                if(action==0){
                    if(nodes.get(0).equals(nodes.get(1))){respond(player,"ui.fangsu.curve.same_node",null,request);return;}
                    final PlatformSettings settings;try{settings=PlatformPreset.decode(config);}catch(RuntimeException e){respond(player,"ui.fangsu.curve.invalid",null,request);return;}
                    boolean editing=group!=null;
                    UUID id=editing?group:UUID.randomUUID();
                    if(editing&&!validateEdit(level,player,id,nodes,request,false))return;
                    long revision=layouts.revision(id);
                    Pending pending=new Pending(level,editing);
                    stopPreparing(playerId);REQUESTS.put(playerId,pending);PLANS.remove(playerId);
                    PlatformRailSampler.sample(level,List.copyOf(nodes),samples->{
                        if(REQUESTS.get(playerId)!=pending)return;
                        if(player.isRemoved()||currentLevel(player)!=level){REQUESTS.remove(playerId);return;}
                        if(samples.points().isEmpty()){
                            REQUESTS.remove(playerId);respond(player,"ui.fangsu.curve.rail_unavailable",null,request);return;
                        }
                        try{PREPARING.put(playerId,GEOMETRY.submit(()->{
                            Map<BlockPos,CellGeometry> cells;String problem="";
                            try{cells=compile(samples,nodes.get(0).getY(),left,settings);}catch(RuntimeException e){cells=Map.of();problem="ui.fangsu.curve.invalid";}
                            Map<BlockPos,CellGeometry> ready=cells;String error=problem;
                            level.getServer().execute(()->{
                                if(REQUESTS.get(playerId)!=pending)return;
                                REQUESTS.remove(playerId);PREPARING.remove(playerId);
                                if(player.isRemoved()||currentLevel(player)!=level)return;
                                if(!error.isEmpty()){respond(player,error,null,request);return;}
                                if(ready.isEmpty()){respond(player,"ui.fangsu.curve.invalid",null,request);return;}
                                if(layouts.revision(id)!=revision){respond(player,"ui.fangsu.curve.stale",null,request);return;}
                                if(editing&&!validateEdit(level,player,id,nodes,request,false))return;
                                List<BlockPos> conflicts=new ArrayList<>();
                                boolean unloaded=false;
                                for(var entry:ready.entrySet()){
                                    if(!PlatformPlacementPolicy.permitted(level,player,entry.getKey())){
                                        blocked(player,entry.getKey(),request,ready.size(),samples.points());return;
                                    }
                                    if(!PlatformPlacementPolicy.loaded(level,entry.getKey())){unloaded=true;continue;}
                                    if(!canPlaceNormally(level,player,entry.getKey(),id,entry.getValue())&&conflicts.size()<256)conflicts.add(entry.getKey());
                                }
                                if(!conflicts.isEmpty()&&!PlatformPlacementPolicy.force(player)){
                                    sendWithConflicts(player,request,"ui.fangsu.curve.preview_blocked",ComponentHelper.translatable("ui.fangsu.curve.mesh_blocked",conflicts.get(0).toShortString()),"",ready.size(),samples.points(),conflicts);return;
                                }
                                Plan plan=new Plan(UUID.randomUUID(),id,level,List.copyOf(nodes),samples,ready,settings,left,editing,System.currentTimeMillis()+120000,revision,List.copyOf(conflicts));
                                PLANS.put(playerId,plan);
                                String prefix=wrench?"ui.fangsu.curve.edit_preview_":"ui.fangsu.curve.preview_";
                                respond(player,unloaded?"ui.fangsu.curve.preview_deferred":prefix+(conflicts.isEmpty()?"ready":"force"),plan,request);
                            });
                        }));}catch(RejectedExecutionException e){REQUESTS.remove(playerId);respond(player,"ui.fangsu.curve.preview_pending",null,request);}
                    });
                }else{
                    Plan plan=prepared;
                    if(plan==null||plan.level()!=level||plan.expires()<System.currentTimeMillis()||!plan.token().toString().equals(token)||layouts.revision(plan.group())!=plan.revision()){respond(player,"ui.fangsu.curve.stale",null,request);return;}
                    PLANS.remove(playerId);
                    if(plan.editing()&&!validateEdit(level,player,plan.group(),plan.nodes(),request,false))return;
                    for(var pos:plan.cells().keySet())if(!PlatformPlacementPolicy.permitted(level,player,pos)){
                        blocked(player,pos,request);return;
                    }
                    Pending pending=new Pending(level,plan.editing());
                    REQUESTS.put(playerId,pending);
                    var chunks=new BuildChunks(plan);
                    CHUNK_WAITS.add(new ChunkWait(player,pending,chunks,System.currentTimeMillis()+45000,()->
                        PlatformRailSampler.sample(level,plan.nodes(),samples->{
                            if(!REQUESTS.remove(playerId,pending)||player.isRemoved()||currentLevel(player)!=level)return;
                            if(!samples.equals(plan.samples())||layouts.revision(plan.group())!=plan.revision()||busy(level,plan.group())){
                                respond(player,"ui.fangsu.curve.stale",null,request);return;
                            }
                            if(plan.editing()&&!validateEdit(level,player,plan.group(),plan.nodes(),request,true))return;
                            for(var entry:plan.cells().entrySet())if(!canPlace(level,player,entry.getKey(),plan.group(),entry.getValue())){
                                blocked(player,entry.getKey(),request);return;
                            }
                            var job=new Job(player,plan);job.chunks=chunks;JOBS.put(playerId,job);
                            chunks.transferred=true;respond(player,"ui.fangsu.curve.building",null,request);
                        })));
                    respond(player,"ui.fangsu.curve.chunk_loading",null,request);
                }
            });
        });
        TickEvent.SERVER_POST.register(server->{
            PlatformMutations.tick(server);
            tickChunkWaits(server);
            PLANS.entrySet().removeIf(e->e.getValue().expires()<System.currentTimeMillis());int budget=128;
            for(var iterator=JOBS.values().iterator();iterator.hasNext()&&budget>0;){Job job=iterator.next();var plan=job.plan;if(plan.level().getServer()!=server)continue;
                if(job.cancelled){job.release();iterator.remove();continue;}
                boolean inLevel=!job.player.isRemoved()&&currentLevel(job.player)==plan.level();
                boolean canContinue=inLevel&&PlatformLayouts.get(plan.level()).canManage(job.player,plan.group());
                if(!canContinue){if(!plan.editing()){updateMembers(job);job.release();iterator.remove();continue;}job.rollingBack=true;}
                if(job.rollingBack){
                    while(!job.undo.isEmpty()&&budget-->0){BlockPos pos=job.undo.removeLast();if(plan.level().mayInteract(job.player,pos))job.journal.get(pos).restore(plan.level(),pos,plan.group());}
                    if(job.undo.isEmpty()){
                        updateMembers(job);job.release();iterator.remove();
                        if(inLevel){
                            if(job.failed!=null)blocked(job.player,job.failed,"");
                            else respond(job.player,canContinue?"ui.fangsu.curve.cancelled":"ui.fangsu.curve.stale",null,"");
                        }
                    }continue;
                }
                while(!job.cancelled&&!job.rollingBack&&job.index<job.positions.size()&&budget-->0){BlockPos pos=job.positions.get(job.index++);CellGeometry geometry=plan.cells().get(pos);
                    if(geometry!=null){if(!canPlace(plan.level(),job.player,pos,plan.group(),geometry)){job.skip(pos);continue;}
                        job.remember(pos);
                        var state=plan.level().getBlockState(pos);
                        var existing=PlatformOverlays.get(plan.level()).entry(pos);
                        if(existing!=null&&existing.covered()&&PlatformOverlays.get(plan.level()).owns(pos,plan.group())&&BlockEntityCurvedPlatform.coverable(geometry)){
                            var cell=new BlockEntityCurvedPlatform(pos,ModBlocks.BLOCK_CURVED_PLATFORM.get().defaultBlockState());cell.configure(plan.group(),plan.nodes().get(0),plan.nodes().get(1),plan.left(),plan.settings(),geometry);
                            var tag=cell.serverData();if(existing.cell().contains("originalBlock"))tag.put("originalBlock",existing.cell().getCompound("originalBlock").copy());if(existing.cell().contains("originalEntity"))tag.put("originalEntity",existing.cell().getCompound("originalEntity").copy());PlatformOverlays.get(plan.level()).cover(pos,state,tag);job.placed++;
                        }else{
                            if(plan.level().getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell&&plan.group().equals(cell.group)){cell.configure(plan.group(),plan.nodes().get(0),plan.nodes().get(1),plan.left(),plan.settings(),geometry);job.placed++;}
                            else{
                                var cell=new BlockEntityCurvedPlatform(pos,ModBlocks.BLOCK_CURVED_PLATFORM.get().defaultBlockState());cell.configure(plan.group(),plan.nodes().get(0),plan.nodes().get(1),plan.left(),plan.settings(),geometry);
                                if(PlatformMutations.materialize(plan.level(),pos,cell.serverData(),state))job.placed++;else job.skip(pos);
                            }
                        }
                    }else if(PlatformPlacementPolicy.accessible(plan.level(),job.player,pos)){
                        job.remember(pos);
                        PlatformMutations.removeCell(plan.level(),pos,plan.group());
                    }else if(plan.editing())job.skip(pos);
                    if(job.journal.containsKey(pos))job.applied(pos);
                }
                if(job.cancelled){job.release();iterator.remove();continue;}
                if(job.rollingBack)continue;
                if(job.index==job.positions.size()){
                    updateMembers(job);
                    job.player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.curve.done",job.placed,0,job.skipped),false);respond(job.player,"ui.fangsu.curve.finished",null,"");job.release();iterator.remove();
                }
            }
        });
    }
    private static void updateMembers(Job job){
        var plan=job.plan;List<BlockPos> retained=job.positions.stream().filter(pos->!plan.level().hasChunkAt(pos)||(plan.level().getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell&&plan.group().equals(cell.group)||PlatformOverlays.get(plan.level()).owns(pos,plan.group()))).toList();
        if(retained.isEmpty())PlatformLayouts.get(plan.level()).remove(plan.group());else PlatformLayouts.get(plan.level()).put(plan.group(),retained);
    }
    private static Map<BlockPos,CellGeometry> compile(PlatformRailSample samples,int floor,boolean left,PlatformSettings settings){
        Map<BlockPos,CellGeometry> result=new LinkedHashMap<>();PlatformCellMerger.merge(PlatformCompiler.compile(samples.snapshot(floor),left,settings)).forEach((pos,geometry)->result.put(new BlockPos(pos.x(),pos.y(),pos.z()),geometry));return Collections.unmodifiableMap(result);
    }
    private static boolean isEdit(Plan plan,ServerLevel level){return plan!=null&&plan.editing()&&plan.level()==level;}
    private static boolean validateEdit(ServerLevel level,ServerPlayer player,UUID group,List<BlockPos> nodes,String request,boolean requireLoaded){
        var layouts=PlatformLayouts.get(level);
        if(!layouts.canManage(player,group)){respond(player,"ui.fangsu.curve.permission",null,request);return false;}
        if(layouts.deleting(group)||busy(level,group)){respond(player,"ui.fangsu.curve.stale",null,request);return false;}
        // 只接受已建站台保存的有序节点；调整侧向时仍使用原连接的方向。
        BlockPos unloaded=null;
        boolean matched=false;
        var overlays=PlatformOverlays.get(level);
        for(long packed:layouts.cells(group)){
            BlockPos pos=BlockPos.of(packed);
            if(!PlatformPlacementPolicy.permitted(level,player,pos)){blocked(player,pos,request);return false;}
            if(!PlatformPlacementPolicy.loaded(level,pos)){if(unloaded==null)unloaded=pos;continue;}
            if(matched)continue;
            if(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell){
                matched=cell.geometry!=null&&group.equals(cell.group)&&nodes.get(0).equals(cell.first)&&nodes.get(1).equals(cell.last);
            }else{
                var entry=overlays.entry(pos);
                if(entry!=null&&entry.matches(level.getBlockState(pos))){
                    var data=entry.cell();
                    matched=group.equals(data.getUUID("group"))&&nodes.get(0).asLong()==data.getLong("first")&&nodes.get(1).asLong()==data.getLong("last");
                }
            }
        }
        if(requireLoaded&&unloaded!=null){blocked(player,unloaded,request);return false;}
        if(!matched&&(unloaded==null||requireLoaded)){respond(player,"ui.fangsu.curve.edit_target_missing",null,request);return false;}
        return true;
    }
    private static boolean anyNear(ServerPlayer player,long[] members){for(long packed:members)if(near(player,BlockPos.of(packed),64))return true;return false;}
    private static boolean near(ServerPlayer player,BlockPos pos,int distance){return player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=distance*(double)distance;}
    private static boolean nativeTarget(net.minecraft.world.level.block.state.BlockState state,ServerLevel level,BlockPos pos){
        if(state.isAir())return false;
        //#if MC_VERSION >= 11904
        if(state.canBeReplaced())return false;
        //#else
        //$$ if(state.getMaterial().isReplaceable())return false;
        //#endif
        return !PlatformOverlayCollision.nativeShape(state,level,pos).isEmpty();
    }
    private static boolean canPlace(ServerLevel level,ServerPlayer player,BlockPos pos,UUID group,CellGeometry desired){
        return !PlatformLayouts.get(level).deleting(group)&&PlatformPlacementPolicy.accessible(level,player,pos)&&(PlatformPlacementPolicy.force(player)||canPlaceNormally(level,player,pos,group,desired));
    }
    private static boolean canPlaceNormally(ServerLevel level,ServerPlayer player,BlockPos pos,UUID group,CellGeometry desired){
        if(PlatformLayouts.get(level).deleting(group))return false;
        if(!PlatformPlacementPolicy.accessible(level,player,pos))return false;
        if(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell)return group.equals(cell.group);
        var state=level.getBlockState(pos);var overlay=PlatformOverlays.get(level).entry(pos);
        if(overlay!=null&&(!PlatformOverlays.get(level).owns(pos,group)||!overlay.matches(state)))return false;
        // 已覆盖的顶皮允许编辑，屏蔽门自己的方块实体始终保留。
        if(overlay!=null&&overlay.covered())return BlockEntityCurvedPlatform.coverable(desired);
        if(state.hasBlockEntity()||!state.getFluidState().isEmpty()||com.fangsu.items.ItemNodeMarker.isRailNode(state.getBlock()))return false;
        if(state.isAir()||nativeTarget(state,level,pos))return true;
        //#if MC_VERSION >= 11904
        return state.canBeReplaced();
        //#else
        //$$ return state.getMaterial().isReplaceable();
        //#endif
    }
    private static ServerLevel currentLevel(ServerPlayer player){
        //#if MC_VERSION >= 12000
        return (ServerLevel)player.level();
        //#else
        //$$ return (ServerLevel)player.level;
        //#endif
    }
    public static void requestBreak(ServerPlayer player,BlockPos pos){
        ServerLevel level=currentLevel(player);
        if(pos==null||!player.isShiftKeyDown()||player.isSpectator()||!PlatformPlacementPolicy.canEdit(player)||!near(player,pos,8)||!level.hasChunkAt(pos)||!level.mayInteract(player,pos))return;
        var owner=PlatformOverhangs.owner(level,pos);if(owner!=null)pos=owner;
        UUID group=null;
        if(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell)group=cell.group;
        else{var entry=PlatformOverlays.get(level).entry(pos);if(entry!=null&&entry.matches(level.getBlockState(pos)))group=entry.cell().getUUID("group");}
        if(group==null||PlatformLayouts.get(level).deleting(group)||!PlatformLayouts.get(level).canManage(player,group))return;
        long[] members=PlatformLayouts.get(level).cells(group);boolean owns=false;
        for(long packed:members){BlockPos member=BlockPos.of(packed);if(member.equals(pos))owns=true;if(level.hasChunkAt(member)&&!level.mayInteract(player,member))return;}
        if(!owns)return;PlatformMutations.removeGroup(level,group);respond(player,"ui.fangsu.curve.removing",null,"");
    }
    public static void breakAt(BlockPos pos){FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());b.writeVarInt(4);b.writeBlockPos(pos);NetworkManager.sendToServer(REQUEST,b);}
    private static void blocked(ServerPlayer player,BlockPos pos,String request){blocked(player,pos,request,0,List.of());}
    private static void blocked(ServerPlayer player,BlockPos pos,String request,int count,List<CurvedPlatformPlanner.Point> points){
        var level=currentLevel(player);
        String key=PlatformPlacementPolicy.permitted(level,player,pos)&&!PlatformPlacementPolicy.loaded(level,pos)
                ?"ui.fangsu.curve.chunk_unloaded":"ui.fangsu.curve.mesh_blocked";
        send(player,request,"ui.fangsu.curve.preview_blocked",ComponentHelper.translatable(key,pos.toShortString()),"",count,points,pos);
    }
    private static void respond(ServerPlayer player,String key,Plan plan,String request){sendWithConflicts(player,request,key,ComponentHelper.translatable(key,plan==null?0:plan.cells().size()),plan==null?"":plan.token().toString(),plan==null?0:plan.cells().size(),plan==null?List.of():plan.samples().points(),plan==null?List.of():plan.conflicts());}
    private static void send(ServerPlayer player,String request,String key,Component text,String token,int count,List<CurvedPlatformPlanner.Point> points,BlockPos conflict){
        sendWithConflicts(player,request,key,text,token,count,points,conflict==null?List.of():List.of(conflict));
    }
    private static void sendWithConflicts(ServerPlayer player,String request,String key,Component text,String token,int count,List<CurvedPlatformPlanner.Point> points,List<BlockPos> conflicts){
        FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());b.writeUtf(request);b.writeUtf(key);b.writeComponent(text);b.writeBoolean(!conflicts.isEmpty());if(!conflicts.isEmpty())b.writeBlockPos(conflicts.get(0));b.writeUtf(token);b.writeVarInt(count);b.writeVarInt(points.size());for(var p:points){b.writeDouble(p.x());b.writeDouble(p.y());b.writeDouble(p.z());}b.writeVarInt(conflicts.size());conflicts.forEach(b::writeBlockPos);NetworkManager.sendToPlayer(player,RESULT,b);
    }
    public static void registerClient(){PlatformOverlayPackets.registerClient();NetworkManager.registerReceiver(NetworkManager.Side.S2C,RESULT,(b,context)->{
        String request=b.readUtf(36),key=b.readUtf(128);Component text=b.readComponent();BlockPos conflict=b.readBoolean()?b.readBlockPos():null;String token=b.readUtf(36);int count=b.readVarInt(),size=b.readVarInt();
        if(count<0||count>65536||size<0||size>8193||!key.startsWith("ui.fangsu.curve."))return;List<CurvedPlatformPlanner.Point> points=new ArrayList<>();for(int i=0;i<size;i++)points.add(new CurvedPlatformPlanner.Point(b.readDouble(),b.readDouble(),b.readDouble()));
        int conflictCount=b.readVarInt();if(conflictCount<0||conflictCount>256)return;List<BlockPos> conflicts=new ArrayList<>();for(int i=0;i<conflictCount;i++)conflicts.add(b.readBlockPos());if(b.readableBytes()!=0)return;
        context.queue(()->{if(!request.isEmpty()&&!request.equals(CurvedPlatformClient.pending))return;CurvedPlatformClient.pending="";CurvedPlatformClient.previewAttempted=true;CurvedPlatformClient.expires=System.currentTimeMillis()+120000;CurvedPlatformClient.status=text;CurvedPlatformClient.conflict=conflict;CurvedPlatformClient.conflicts=List.copyOf(conflicts);CurvedPlatformClient.previewToken=token;CurvedPlatformClient.blocks=count;CurvedPlatformClient.preview=List.copyOf(points);
            CurvedPlatformClient.jobRunning=key.equals("ui.fangsu.curve.building")||key.equals("ui.fangsu.curve.chunk_loading");
            var client=net.minecraft.client.Minecraft.getInstance();if(client.screen instanceof CurvedPlatformScreen screen)screen.updatePreview();if(client.player!=null)client.player.displayClientMessage(text,conflict==null);
        });
    });}
    public static void preview(List<BlockPos> nodes,PlatformSettings settings,boolean left,UUID group){
        if(nodes.size()!=2)return;CurvedPlatformClient.invalidatePreview();CurvedPlatformClient.previewAttempted=true;CurvedPlatformClient.pending=UUID.randomUUID().toString();CurvedPlatformClient.status=ComponentHelper.translatable("ui.fangsu.curve.preview_pending");
        FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());b.writeVarInt(0);b.writeUtf(CurvedPlatformClient.pending);nodes.forEach(b::writeBlockPos);b.writeUtf(PlatformPreset.encode(settings));b.writeBoolean(left);b.writeBoolean(group!=null);if(group!=null)b.writeUUID(group);NetworkManager.sendToServer(REQUEST,b);
    }
    public static void action(int action){FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());b.writeVarInt(action);if(action==1)b.writeUtf(CurvedPlatformClient.previewToken);if(action==3){if(CurvedPlatformClient.editingGroup==null)return;b.writeUUID(CurvedPlatformClient.editingGroup);}NetworkManager.sendToServer(REQUEST,b);}
}
