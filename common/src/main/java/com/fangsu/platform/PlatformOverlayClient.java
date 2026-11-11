package com.fangsu.platform;

import com.fangsu.blockEntities.BlockEntityCurvedPlatform;
import com.fangsu.blocks.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** 原生方块覆盖层独立绘制；快照完成前保留旧图，禁止半批结果闪烁。 */
public final class PlatformOverlayClient {
    private record Cell(BlockState expected,BlockEntityCurvedPlatform mesh,boolean covered) {
        boolean matches(BlockState state){return covered?!state.isAir()&&!state.is(ModBlocks.BLOCK_CURVED_PLATFORM.get()):state.equals(expected);}
    }
    private static final Map<BlockPos,Cell> CELLS=new HashMap<>(),INCOMING=new HashMap<>();
    private static Object world;private static UUID receiving;private static int ticks;
    private static final CurvedPlatformRenderer RENDERER=new CurvedPlatformRenderer();
    public static void init(){dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(client->{
        if(world!=client.level){if(world instanceof net.minecraft.world.level.Level previous)for(var pos:CELLS.keySet())PlatformOverhangs.update(previous,pos,null);world=client.level;CELLS.clear();INCOMING.clear();receiving=null;ticks=0;}
        if(client.level!=null&&client.player!=null&&++ticks%60==1)PlatformOverlayPackets.request();
    });}
    public static void receive(int kind,UUID id,ResourceLocation dimension,CompoundTag tag){
        var client=Minecraft.getInstance();if(client.level==null)return;
        if(world!=client.level){if(world instanceof net.minecraft.world.level.Level previous)for(var pos:CELLS.keySet())PlatformOverhangs.update(previous,pos,null);world=client.level;CELLS.clear();INCOMING.clear();receiving=null;ticks=0;}
        if(kind==0){if(!client.level.dimension().location().equals(dimension))return;receiving=id;INCOMING.clear();}
        else if(!id.equals(receiving))return;
        else if(kind==1&&tag!=null&&INCOMING.size()<65536){
            try{BlockPos pos=BlockPos.of(tag.getLong("pos"));var cell=new BlockEntityCurvedPlatform(pos,ModBlocks.BLOCK_CURVED_PLATFORM.get().defaultBlockState());cell.load(tag.getCompound("cell"));if(cell.geometry!=null)INCOMING.put(pos,new Cell(PlatformOverlays.state(tag.getCompound("expected")),cell,tag.getBoolean("covered")));}catch(RuntimeException ignored){}
        }else if(kind==2){
            for(var pos:CELLS.keySet())if(!INCOMING.containsKey(pos)){
                var host=client.level.getBlockEntity(pos);PlatformOverhangs.update(client.level,pos,host instanceof BlockEntityCurvedPlatform body&&!body.isRemoved()?body.geometry:null);
            }
            CELLS.clear();CELLS.putAll(INCOMING);for(var entry:CELLS.values())entry.mesh().setLevel(client.level);INCOMING.clear();receiving=null;
        }
    }
    public static BlockEntityCurvedPlatform at(BlockPos pos){var c=CELLS.get(pos);var level=Minecraft.getInstance().level;return c!=null&&level!=null&&c.matches(level.getBlockState(pos))?c.mesh():null;}
    public static boolean covered(BlockPos pos){var c=CELLS.get(pos);return c!=null&&c.covered()&&at(pos)!=null;}
    public static void render(PoseStack poses,float delta){
        var client=Minecraft.getInstance();if(client.level==null||client.player==null)return;var camera=client.gameRenderer.getMainCamera().getPosition();var buffers=client.renderBuffers().bufferSource();var frustum=((com.fangsu.mixin.PlatformFrustumAccessor)client.levelRenderer).fangsu$platformFrustum();
        for(var entry:CELLS.entrySet()){
            BlockPos pos=entry.getKey();double dx=pos.getX()+.5-camera.x,dy=pos.getY()+.5-camera.y,dz=pos.getZ()+.5-camera.z;if(dx*dx+dy*dy+dz*dz>96*96||frustum!=null&&!frustum.isVisible(new net.minecraft.world.phys.AABB(pos).inflate(PlatformCellMerger.parts(entry.getValue().mesh().geometry).isEmpty()?.001:1.001))||!client.level.hasChunkAt(pos)||!entry.getValue().matches(client.level.getBlockState(pos)))continue;
            poses.pushPose();poses.translate(pos.getX()-camera.x,pos.getY()-camera.y+.0003,pos.getZ()-camera.z);
            RENDERER.render(entry.getValue().mesh(),delta,poses,buffers,LevelRenderer.getLightColor(client.level,pos),OverlayTexture.NO_OVERLAY);poses.popPose();
        }
        buffers.endBatch(net.minecraft.client.renderer.RenderType.cutoutMipped());
    }
}
