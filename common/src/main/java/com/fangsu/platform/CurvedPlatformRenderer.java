package com.fangsu.platform;

import com.fangsu.blockEntities.BlockEntityCurvedPlatform;
import com.mojang.blaze3d.vertex.*;
import dev.potato.curvedplatform.geometry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** 同一套原始三角网在 Fabric / Forge 渲染；UV 保留沿曲线和世界对齐两种模式。 */
public final class CurvedPlatformRenderer implements BlockEntityRenderer<BlockEntityCurvedPlatform> {
    private record Masked(Object geometry,net.minecraft.world.level.block.state.BlockState state,List<CellGeometry.Face> faces) { }
    private static final Map<BlockEntityCurvedPlatform,Masked> MASKS=new WeakHashMap<>();
    private static Object models;
    private static final Map<String,TextureAtlasSprite> TILES=new LinkedHashMap<>(){
        @Override protected boolean removeEldestEntry(Map.Entry<String,TextureAtlasSprite> entry){return size()>512;}
    };
    private static final String[] SUFFIX={"_top","_front","_inner","_bottom"};
    @Override public void render(BlockEntityCurvedPlatform cell,float delta,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(cell.geometry==null)return;renderMesh(cell,cell.geometry,0,0,poses,buffers,light,overlay);
        for(var part:PlatformCellMerger.parts(cell.geometry)){poses.pushPose();poses.translate(part.dx(),0,part.dz());renderMesh(cell,part.geometry(),part.dx(),part.dz(),poses,buffers,light,overlay);poses.popPose();}
    }
    @Override public boolean shouldRenderOffScreen(BlockEntityCurvedPlatform cell){return !PlatformCellMerger.parts(cell.geometry).isEmpty();}
    private void renderMesh(BlockEntityCurvedPlatform cell,CellGeometry geometry,int dx,int dz,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        var position=cell.getBlockPos().offset(dx,0,dz);boolean body=dx==0&&dz==0;
        java.util.List<CellGeometry.Face> faces=geometry.faces();
        if(cell.getLevel()!=null&&!cell.getLevel().getBlockState(position).is(com.fangsu.blocks.ModBlocks.BLOCK_CURVED_PLATFORM.get())){
            var state=cell.getLevel().getBlockState(position);var cached=body?MASKS.get(cell):null;
            if(cached!=null&&cached.geometry()==geometry&&cached.state()==state)faces=cached.faces();
            else{
                var nativeShape=PlatformOverlayCollision.nativeShape(state,cell.getLevel(),position);
                if(state.canOcclude()&&net.minecraft.world.level.block.Block.isShapeFullBlock(nativeShape))faces=List.of();
                else if(!nativeShape.isEmpty()){
                    var mask=nativeShape.toAabbs().stream().map(b->new CellGeometry.Box(Math.max(0,b.minX),Math.max(0,b.minY),Math.max(0,b.minZ),Math.min(1,b.maxX),Math.min(1,b.maxY),Math.min(1,b.maxZ))).filter(b->b.minX()<b.maxX()&&b.minY()<b.maxY()&&b.minZ()<b.maxZ()).toList();
                    faces=faces.stream().flatMap(face->SurfaceMask.outside(face,mask).stream()).toList();
                }
                if(body)MASKS.put(cell,new Masked(geometry,state,faces));
            }
        }
        var settings=cell.settings;var vertex=buffers.getBuffer(RenderType.cutoutMipped());
        for(var face:faces){
            var sprite=sprite(cell,face);var points=face.vertices();if(points.size()<3)continue;
            var a=points.get(0);var b=points.get(1);var c=points.get(2);
            double nx=(b.y()-a.y())*(c.z()-a.z())-(b.z()-a.z())*(c.y()-a.y()),ny=(b.z()-a.z())*(c.x()-a.x())-(b.x()-a.x())*(c.z()-a.z()),nz=(b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x());
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);if(length<1e-12)continue;
            float shade=cell.getLevel()==null?1:cell.getLevel().getShade(Direction.from3DDataValue(face.direction()),true);
            boolean grid=(face.texture()==2||face.texture()==5)?settings.rearMode()==PlatformSettings.RearMode.GRID:settings.customMaterial()&&settings.customMode()==PlatformSettings.RearMode.GRID;
            double baseU=Math.floor(points.stream().mapToDouble(p->grid?gridU(position,face,p):p.u()).min().orElse(0)+1e-8);
            double baseV=Math.floor(points.stream().mapToDouble(p->grid?gridV(position,face,p):p.v()).min().orElse(0)+1e-8);
            for(int i=1;i+1<points.size();i++)for(var p:List.of(a,points.get(i),points.get(i+1),points.get(i+1))){
                double u=grid?gridU(position,face,p):p.u(),v=grid?gridV(position,face,p):p.v();
                // 上游顶点在网格边界已切片，整数处保留 1，避免边缘 UV 跳回 0。
                float uu=(float)(Math.max(0,Math.min(1,u-baseU))),vv=(float)(Math.max(0,Math.min(1,v-baseV)));
                vertex.vertex(poses.last().pose(),(float)p.x(),(float)p.y(),(float)p.z()).color(shade,shade,shade,1).uv(sprite.getU(uu*16),sprite.getV(vv*16)).overlayCoords(overlay).uv2(light).normal(poses.last().normal(),(float)(nx/length),(float)(ny/length),(float)(nz/length)).endVertex();
            }
        }
    }
    private static double gridU(net.minecraft.core.BlockPos pos,CellGeometry.Face face,CellGeometry.Vertex p){return face.direction()>=4?pos.getZ()+p.z():pos.getX()+p.x();}
    private static double gridV(net.minecraft.core.BlockPos pos,CellGeometry.Face face,CellGeometry.Vertex p){return face.direction()>=2?1-pos.getY()-p.y():pos.getZ()+p.z();}
    private static TextureAtlasSprite sprite(BlockEntityCurvedPlatform cell,CellGeometry.Face face){
        var settings=cell.settings;String material="";int texture=face.texture();
        if(texture==0&&settings.customMaterial())material=settings.customBlock();
        if(texture==2||texture==5)material=settings.rearBlock();
        if(texture==3)material=settings.innerBlock().isEmpty()?settings.style().source().innerDefault():settings.innerBlock();
        if(texture>=6){material=texture==8?"":settings.endBlock(texture==6);if(material.isEmpty())material=face.direction()==1?settings.rearBlock():settings.innerBlock().isEmpty()?settings.style().source().innerDefault():settings.innerBlock();if(material.equals(PlatformSettings.FOLLOW_PLATFORM))material="";}
        if(material.isEmpty())material=settings.style().blockMaterial();
        var client=Minecraft.getInstance();
        Object current=client.getBlockRenderer().getBlockModel(Blocks.STONE.defaultBlockState());if(models!=current){models=current;TILES.clear();}
        if(!material.isEmpty()){
            String key=material+"/"+face.direction();var cached=TILES.get(key);if(cached!=null)return cached;
            var state=Blocks.STONE.defaultBlockState();
            try{
                //#if MC_VERSION >= 11903
                state=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new ResourceLocation(material)).defaultBlockState();
                //#else
                //$$ state=net.minecraft.core.Registry.BLOCK.get(new ResourceLocation(material)).defaultBlockState();
                //#endif
                if(state.isAir())state=Blocks.STONE.defaultBlockState();
                var model=client.getBlockRenderer().getBlockModel(state);
                var quads=model.getQuads(state,Direction.from3DDataValue(face.direction()),
                    //#if MC_VERSION >= 11900
                    net.minecraft.util.RandomSource.create(0));
                    //#else
                    //$$ new Random(0));
                    //#endif
                var tile=quads.isEmpty()?model.getParticleIcon():quads.get(0).getSprite();TILES.put(key,tile);return tile;
            }catch(RuntimeException e){return client.getBlockRenderer().getBlockModel(Blocks.STONE.defaultBlockState()).getParticleIcon();}
        }
        int index=texture==0?0:texture==1?(settings.profile()==PlatformSettings.Profile.INDENTED?2:1):3;
        return client.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(new ResourceLocation("potato_curved_platform","block/"+settings.style().texture+SUFFIX[index]));
    }
}
