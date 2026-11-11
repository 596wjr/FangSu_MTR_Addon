package com.fangsu.platform;

import com.fangsu.blockEntities.BlockEntityCurvedPlatform;
import com.fangsu.items.*;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import java.util.*;

public final class CurvedPlatformClient {
    public static dev.potato.curvedplatform.geometry.PlatformSettings settings=dev.potato.curvedplatform.geometry.PlatformSettings.DEFAULT;
    public static UUID editingGroup;
    public static void edit(com.fangsu.blockEntities.BlockEntityCurvedPlatform cell){nodes();NODES.clear();NODES.add(cell.first);NODES.add(cell.last);settings=cell.settings;draftSide=cell.left?1:-1;editingGroup=cell.group;invalidatePreview();open();}
    private static Object world;
    private static final List<BlockPos> NODES=new ArrayList<>();
    public static String previewToken="";
    public static Component status=ComponentHelper.translatable("ui.fangsu.curve.instructions");
    public static BlockPos conflict;
    public static List<BlockPos> conflicts=List.of();
    public static boolean previewAttempted;
    static boolean jobRunning;
    public static int blocks;
    public static String pending="",draftWidth="4",draftOffset="1.5",draftHeight="1",draftMaterial="minecraft:smooth_stone";
    public static int draftSide=1;
    public static double visualWidth=4,visualOffset=1.5;public static int visualHeight=1,visualSide=1;
    private static int particleTick;
    private static long lastOverhangBreak;
    public static long expires;
    public static List<CurvedPlatformPlanner.Point> preview=List.of();
    private static final java.nio.file.Path PRESET=dev.architectury.platform.Platform.getConfigFolder().resolve("fangsu/curved_platform.json");
    private static final java.util.concurrent.ExecutorService PRESET_IO=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"fangsu-platform-presets");t.setDaemon(true);return t;});
    public static void saveSettings(){var value=settings;java.util.concurrent.CompletableFuture.runAsync(()->{try{dev.potato.curvedplatform.geometry.PlatformPreset.save(PRESET,value);}catch(java.io.IOException e){com.fangsu.Main.LOGGER.warn("[FangSu] Platform preset could not be saved ({})",e.getClass().getSimpleName());}},PRESET_IO);}
    public static void init(){
        PlatformOverlayClient.init();
        if(java.nio.file.Files.isRegularFile(PRESET))try{settings=dev.potato.curvedplatform.geometry.PlatformPreset.load(PRESET);}catch(java.io.IOException|RuntimeException e){com.fangsu.Main.LOGGER.warn("[FangSu] Invalid platform preset; using defaults");}
        dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(client->{
            nodes();if(client.level==null||client.player==null)return;
            // 原生攻击路径会跳过 AIR；附属薄片没有代理方块，潜行攻击需主动转到主体。
            if(client.screen==null&&client.player.isShiftKeyDown()&&client.options.keyAttack.isDown()&&client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit){
                var owner=PlatformOverhangs.owner(client.level,hit.getBlockPos());long now=System.currentTimeMillis();
                if(owner!=null&&now-lastOverhangBreak>350){lastOverhangBreak=now;CurvedPlatformPackets.breakAt(owner);}
            }
            if(++particleTick%4!=0)return;
            if(client.player.getMainHandItem().is(ModItems.ITEM_PLATFORM_RAIL_SELECTOR.get()))for(var pos:NODES)
                client.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,pos.getX()+.5,pos.getY()+1.2,pos.getZ()+.5,0,.01,0);
            if(System.currentTimeMillis()>expires){
                boolean expired=!previewToken.isEmpty();
                previewToken="";preview=List.of();conflict=null;conflicts=List.of();
                if(expired){
                    status=ComponentHelper.translatable("ui.fangsu.curve.stale");
                    if(client.screen instanceof CurvedPlatformScreen screen)screen.updatePreview();
                }
                return;
            }
            for(var blocked:conflicts)client.level.addParticle(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,blocked.getX()+.5,blocked.getY()+1.1,blocked.getZ()+.5,0,0,0);
            int stride=Math.max(1,preview.size()/32);
            for(int i=0;i<preview.size();i+=stride){var p=preview.get(i);var a=preview.get(Math.max(0,i-1));var b=preview.get(Math.min(preview.size()-1,i+1));double dx=b.x()-a.x(),dz=b.z()-a.z(),length=Math.hypot(dx,dz);if(length<1e-6)continue;
                for(double across:new double[]{0,visualWidth}){double d=visualSide*(visualOffset+across);client.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,p.x()+dz/length*d,Math.floor(p.y())+visualHeight+.05,p.z()-dx/length*d,0,0,0);}
            }
        });
    }
    public static void useToolOn(ItemPlatformTool.Role role,Player player,BlockPos pos){
        var level=Minecraft.getInstance().level;
        if(level!=null){var owner=PlatformOverhangs.owner(level,pos);if(owner!=null)pos=owner;}
        if(role==ItemPlatformTool.Role.SCREEN_DOOR)PlatformDoorClient.open(pos);
        else if(role==ItemPlatformTool.Role.CONFIGURE&&level!=null&&level.getBlockEntity(pos) instanceof com.fangsu.blockEntities.BlockEntityCurvedPlatform cell&&cell.group!=null)edit(cell);
        else if(role==ItemPlatformTool.Role.CONFIGURE&&PlatformOverlayClient.at(pos)!=null)edit(PlatformOverlayClient.at(pos));
        else useTool(role,player);
    }
    public static List<BlockPos> nodes(){var level=Minecraft.getInstance().level;if(world!=level){world=level;jobRunning=false;clear();}return List.copyOf(NODES);}
    public static void clear(){NODES.clear();editingGroup=null;invalidatePreview();}
    public static void invalidatePreview(){preview=List.of();previewToken="";pending="";blocks=0;conflict=null;conflicts=List.of();previewAttempted=false;status=ComponentHelper.translatable("ui.fangsu.curve.instructions");}
    public static void click(Player player,BlockPos pos){
        nodes();var level=Minecraft.getInstance().level;if(player==null||level==null)return;
        if(player.isShiftKeyDown()){clearSelection(player);return;}
        if(!ItemNodeMarker.isRailNode(level.getBlockState(pos).getBlock()))return;
        if(NODES.size()==2){NODES.clear();editingGroup=null;}
        if(!NODES.contains(pos)){NODES.add(pos.immutable());invalidatePreview();}
        player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.curve.selected",NODES.size()),true);
    }
    public static void clearSelection(Player player){clear();if(player!=null)player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.curve.cleared"),true);}
    public static void useTool(ItemPlatformTool.Role role,Player player){
        nodes();if(player==null)return;
        switch(role){
            case CONFIGURE->open();
            case SCREEN_DOOR->PlatformDoorClient.open();
            case BUILD->{
                if(player.isShiftKeyDown()){CurvedPlatformPackets.action(2);previewToken="";pending="";return;}
                if(!pending.isEmpty()){player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.curve.preview_pending"),true);return;}
                if(!previewToken.isEmpty()&&System.currentTimeMillis()>expires){previewToken="";status=ComponentHelper.translatable("ui.fangsu.curve.stale");}
                if(previewToken.isEmpty()){player.displayClientMessage(previewAttempted?status:ComponentHelper.translatable("ui.fangsu.curve.need_preview"),conflict==null);return;}
                status=ComponentHelper.translatable("ui.fangsu.curve.building");
                CurvedPlatformPackets.action(1);previewToken="";pending="";
            }
        }
    }
    public static void open(){Minecraft.getInstance().setScreen(new CurvedPlatformScreen(Minecraft.getInstance().screen));}
    public static void openWithWrench(BlockEntityCurvedPlatform cell){
        nodes();
        var client=Minecraft.getInstance();
        client.setScreen(new CurvedPlatformScreen(client.screen,cell));
    }
}
