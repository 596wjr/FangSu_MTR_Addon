package com.fangsu.platform;

import com.fangsu.blockEntities.BlockEntityCurvedPlatform;
import com.fangsu.blockEntities.BlockEntityAutoPlatformComponent;
import com.fangsu.items.ModItems;
import com.fangsu.mappings.ComponentHelper;
import dev.potato.curvedplatform.geometry.PlatformSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import java.util.*;

/** 屏蔽门配置器的独立编辑状态，不改变站台构建器的节点或预览。 */
public final class PlatformDoorClient {
    public static PlatformDoorSettings settings=PlatformDoorSettings.DEFAULT;
    public static List<BlockPos> nodes=List.of();
    public static PlatformSettings platform=PlatformSettings.DEFAULT;
    public static boolean left=true;
    public static UUID sourceGroup,group;
    public static String pending="",token="";
    public static Component status=ComponentHelper.translatable("ui.fangsu.curve.door_hint");
    public static List<CurvedPlatformPlanner.Point> preview=List.of();
    public static List<BlockPos> conflicts=List.of();
    public static int doors,glass;
    public static boolean running;
    private static boolean initialized;
    private static Object world;
    private static int particleTick;
    private static long expires,requestExpires;
    private static String activeRequest="";
    private record Selection(List<BlockPos> nodes,PlatformSettings platform,boolean left,UUID source) { }
    private static Selection lastSelection;
    private static BlockPos lastClicked;
    private static final long REQUEST_TIMEOUT=35000,WORK_TIMEOUT=120000;
    private PlatformDoorClient() { }

    public static void init(){
        if(initialized)return;initialized=true;
        dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(client->{
            checkWorld();if(client.level==null||client.player==null)return;
            long now=System.currentTimeMillis();
            if(!activeRequest.isEmpty()&&now>requestExpires){
                // 迟到结果会被请求号丢弃；服务器仍须撤销本请求，不能在本地假定已停工。
                PlatformDoorPackets.cancel(UUID.randomUUID().toString());running=false;invalidate();status=ComponentHelper.translatable("ui.fangsu.curve.door_timeout");refresh();
            }
            if(!token.isEmpty()&&now>expires){token="";status=ComponentHelper.translatable("ui.fangsu.curve.door_stale");refresh();}
            if(++particleTick%4!=0||!client.player.getMainHandItem().is(ModItems.ITEM_SCREENDOOR_CONFIGURATOR.get()))return;
            int conflictStride=Math.max(1,(conflicts.size()+63)/64);
            for(int i=(particleTick/4)%conflictStride;i<conflicts.size();i+=conflictStride){BlockPos pos=conflicts.get(i);client.level.addParticle(ParticleTypes.ANGRY_VILLAGER,pos.getX()+.5,pos.getY()+1.1,pos.getZ()+.5,0,0,0);}
            // 点已经位于各构件真实边界；轮换取样相位，长站台也能看到所有高度。
            int stride=Math.max(1,(preview.size()+287)/288);
            for(int i=(particleTick/4)%stride;i<preview.size();i+=stride){var p=preview.get(i);client.level.addParticle(ParticleTypes.END_ROD,p.x(),p.y(),p.z(),0,0,0);}
        });
    }
    private static void checkWorld(){Object current=Minecraft.getInstance().level;if(world!=current){world=current;nodes=List.of();sourceGroup=null;group=null;running=false;lastSelection=null;lastClicked=null;invalidate();}}
    private static Selection selection(){return new Selection(CurvedPlatformClient.nodes(),CurvedPlatformClient.settings,CurvedPlatformClient.draftSide==1,CurvedPlatformClient.editingGroup);}
    private static void select(Selection value){nodes=value.nodes();platform=value.platform();left=value.left();sourceGroup=value.source();group=null;lastClicked=null;lastSelection=value;settings=settings.freshFacing();invalidate();}
    private static void begin(String request,boolean work){activeRequest=request;pending=request;requestExpires=System.currentTimeMillis()+(work?WORK_TIMEOUT:REQUEST_TIMEOUT);}
    private static boolean sameTarget(BlockPos clicked){
        if(clicked.equals(lastClicked))return true;if(lastClicked==null&&token.isEmpty()&&pending.isEmpty()&&group==null)return false;var level=Minecraft.getInstance().level;if(level==null)return false;var owner=PlatformOverhangs.owner(level,clicked);if(owner!=null)clicked=owner;var entity=level.getBlockEntity(clicked);
        if(entity instanceof BlockEntityAutoPlatformComponent host)return group!=null&&group.equals(host.group);
        if(entity instanceof BlockEntityCurvedPlatform host)return sourceGroup!=null&&sourceGroup.equals(host.group);
        var overlay=PlatformOverlayClient.at(clicked);return sourceGroup!=null&&overlay!=null&&sourceGroup.equals(overlay.group);
    }
    public static void open(){
        init();checkWorld();if(running){show();return;}var selected=selection();
        // 工具收起后再打开同一编辑上下文，保留已获服务器许可的预览及未完成请求。
        if(!selected.equals(lastSelection))select(selected);show();
    }
    public static void open(BlockEntityCurvedPlatform host){
        open(host.getBlockPos());
    }
    public static void open(BlockPos clicked){
        init();checkWorld();if(running){show();return;}
        var selected=selection();if(selected.equals(lastSelection)&&sameTarget(clicked)){show();return;}
        select(selected);lastClicked=clicked.immutable();String request=UUID.randomUUID().toString();begin(request,false);
        status=ComponentHelper.translatable("ui.fangsu.curve.door_loading");show();PlatformDoorPackets.query(clicked,request);refresh();
    }
    private static void show(){Minecraft client=Minecraft.getInstance();client.setScreen(new PlatformDoorScreen(client.screen));}
    public static void invalidate(){token="";preview=List.of();conflicts=List.of();doors=0;glass=0;expires=0;if(!running){pending="";activeRequest="";requestExpires=0;status=ComponentHelper.translatable("ui.fangsu.curve.door_hint");}}
    public static void requestPreview(PlatformDoorSettings value){
        if(running||!pending.isEmpty())return;if(nodes.size()!=2){status=ComponentHelper.translatable("ui.fangsu.curve.door_need_nodes");refresh();return;}
        if(!value.directionChosen()&&Minecraft.getInstance().player!=null){var facing=Minecraft.getInstance().player.getLookAngle();var first=nodes.get(0);var last=nodes.get(1);value=value.withFacing((last.getX()-first.getX())*facing.x+(last.getZ()-first.getZ())*facing.z>0);}
        settings=value;invalidate();begin(UUID.randomUUID().toString(),false);status=ComponentHelper.translatable("ui.fangsu.curve.preview_pending");
        PlatformDoorPackets.preview(nodes,platform,left,sourceGroup,group,settings,pending);if(Minecraft.getInstance().screen instanceof PlatformDoorScreen screen)screen.captureFacing(settings);refresh();
    }
    public static boolean canBuild(){return !running&&!token.isEmpty()&&pending.isEmpty()&&System.currentTimeMillis()<=expires;}
    public static void build(){if(!canBuild())return;String ready=token;token="";begin(UUID.randomUUID().toString(),true);running=true;status=ComponentHelper.translatable("ui.fangsu.curve.door_building");PlatformDoorPackets.build(ready,pending);refresh();}
    public static void cancel(){running=false;invalidate();begin(UUID.randomUUID().toString(),true);running=true;status=ComponentHelper.translatable("ui.fangsu.curve.door_cancelling");PlatformDoorPackets.cancel(pending);refresh();}
    public static void remove(){if(group==null||running||!pending.isEmpty())return;begin(UUID.randomUUID().toString(),true);running=true;token="";preview=List.of();conflicts=List.of();status=ComponentHelper.translatable("ui.fangsu.curve.door_removing");PlatformDoorPackets.remove(group,pending);refresh();}
    public static void receive(String request,String key,String ready,UUID layout,int doorCount,int glassCount,List<CurvedPlatformPlanner.Point> points,List<BlockPos> blocked){
        checkWorld();if(request.isEmpty()||!request.equals(activeRequest))return;
        running=key.equals("ui.fangsu.curve.door_building")||key.equals("ui.fangsu.curve.door_removing")||key.equals("ui.fangsu.curve.door_cancelling");
        if(running)requestExpires=System.currentTimeMillis()+WORK_TIMEOUT;else{activeRequest="";requestExpires=0;}
        pending="";token=ready==null?"":ready;group=key.equals("ui.fangsu.curve.door_removed")?null:layout==null?group:layout;doors=doorCount;glass=glassCount;
        preview=List.copyOf(points);conflicts=List.copyOf(blocked);expires=System.currentTimeMillis()+120000;
        status=ComponentHelper.translatable(key,doors,glass,conflicts.size());refresh();
    }
    public static void loaded(String request,UUID source,UUID layout,List<BlockPos> selected,PlatformSettings surface,boolean side,PlatformDoorSettings value){
        checkWorld();if(request.isEmpty()||!request.equals(activeRequest))return;
        invalidate();sourceGroup=source;group=layout;nodes=List.copyOf(selected);platform=surface;left=side;settings=value;
        status=ComponentHelper.translatable(layout==null?"ui.fangsu.curve.door_hint":"ui.fangsu.curve.door_editing");
        if(Minecraft.getInstance().screen instanceof PlatformDoorScreen screen)screen.loadSettings();
    }
    public static void refresh(){if(Minecraft.getInstance().screen instanceof PlatformDoorScreen screen)screen.updatePreview();}
}
