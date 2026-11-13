package com.fangsu.blockEntities;

import com.fangsu.blocks.ModBlocks;
import com.fangsu.platform.PlatformGeometryCodec;
import dev.potato.curvedplatform.geometry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** 每格持久化上游网格和布局归属；选择、碰撞和视觉使用同一份几何。 */
public final class BlockEntityCurvedPlatform extends BlockEntity {
    public UUID group;
    public BlockPos first=BlockPos.ZERO,last=BlockPos.ZERO;
    public boolean left=true;
    public PlatformSettings settings=PlatformSettings.DEFAULT;
    public CellGeometry geometry;
    private CompoundTag encodedGeometry;
    private BlockState originalBlock;
    private CompoundTag originalEntity;
    private boolean skin;
    private VoxelShape collision=Shapes.empty(),outline=Shapes.empty();
    public BlockEntityCurvedPlatform(BlockPos pos,BlockState state){super(ModBlocks.BLOCK_ENTITY_CURVED_PLATFORM.get(),pos,state);}
    public VoxelShape collision(){return collision;}
    public VoxelShape outline(){return outline;}
    public boolean skin(){return skin;}
    public boolean floorSkin(){return skin&&!outline.isEmpty()&&outline.min(net.minecraft.core.Direction.Axis.Y)==0;}
    public boolean coverable(){return geometry!=null&&(geometry.replaceableDecoration()||floorSkin());}
    public BlockState originalBlock(){return originalBlock;}
    public void originalBlock(BlockState state){originalBlock=state==null||state.isAir()?null:state;setChanged();}
    public CompoundTag originalEntity(){return originalEntity==null?null:originalEntity.copy();}
    public void originalEntity(CompoundTag tag){originalEntity=tag==null?null:tag.copy();setChanged();}
    public static boolean floorSkin(CellGeometry geometry){
        var boxes=geometry.outlineBoxes();return !boxes.isEmpty()&&geometry.faces().stream().allMatch(f->f.direction()==1)&&boxes.stream().allMatch(b->b.maxY()-b.minY()<=2.44141625E-4)&&boxes.stream().mapToDouble(CellGeometry.Box::minY).min().orElse(1)==0;
    }
    public static boolean coverable(CellGeometry geometry){return geometry!=null&&(geometry.replaceableDecoration()||floorSkin(geometry));}
    public static VoxelShape shape(List<CellGeometry.Box> boxes){VoxelShape result=Shapes.empty();for(var b:boxes)result=Shapes.or(result,Shapes.box(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()));return result;}
    public void configure(UUID group,BlockPos first,BlockPos last,boolean left,PlatformSettings settings,CellGeometry geometry){
        applyGeometry(group,first,last,left,settings,geometry);
        setChanged();
        if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    private void applyGeometry(UUID group,BlockPos first,BlockPos last,boolean left,PlatformSettings settings,CellGeometry geometry){
        this.group=group;this.first=first;this.last=last;this.left=left;this.settings=settings;
        var boxes=geometry.outlineBoxes();outline=shape(boxes);
        skin=!boxes.isEmpty()&&geometry.faces().stream().allMatch(f->f.direction()==1)&&boxes.stream().allMatch(b->b.maxY()-b.minY()<=2.44141625E-4);
        collision=geometry.physicalBoxes()==null&&!geometry.collisionlessBase()?outline:shape(geometry.collisionBoxes());
        // 与上游 Data.create 一致：顶皮的 texture=4 是原生地面的补集，不是站台材质。
        // 它若直接进入站台渲染器，会把曲线外侧重新补成整格的石头纹理。
        var faces=geometry.conforming()?geometry.faces():MeshPartitioner.partition(geometry.faces(),floorSkin());
        if(floorSkin())faces=faces.stream().filter(f->f.texture()!=4).toList();
        this.geometry=com.fangsu.platform.PlatformCellMerger.copyParts(geometry,new CellGeometry(faces,geometry.volumes(),geometry.solidVolume(),true,geometry.collisionlessBase(),geometry.physicalBoxes(),geometry.replaceableDecoration()));
        encodedGeometry=null;
        com.fangsu.platform.PlatformOverhangs.update(level,worldPosition,this.geometry);
    }
    @Override public void setRemoved(){super.setRemoved();com.fangsu.platform.PlatformOverhangs.removed(level,worldPosition);}
    @Override public void setLevel(net.minecraft.world.level.Level value){super.setLevel(value);com.fangsu.platform.PlatformOverhangs.update(value,worldPosition,geometry);}
    @Override protected void saveAdditional(CompoundTag tag){
        super.saveAdditional(tag);if(group==null||geometry==null)return;
        saveCell(tag);
        if(originalBlock!=null)tag.put("originalBlock",net.minecraft.nbt.NbtUtils.writeBlockState(originalBlock));
        if(originalEntity!=null)tag.put("originalEntity",originalEntity.copy());
    }
    private void saveCell(CompoundTag tag){
        if(group==null||geometry==null)return;
        tag.putInt("version",1);tag.putUUID("group",group);tag.putLong("first",first.asLong());tag.putLong("last",last.asLong());tag.putBoolean("left",left);
        tag.putString("settings",PlatformPreset.encode(settings));
        if(encodedGeometry==null)encodedGeometry=PlatformGeometryCodec.write(geometry);
        tag.put("geometry",encodedGeometry.copy());
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);if(tag.getInt("version")!=1)return;
        try{originalBlock=tag.contains("originalBlock",Tag.TAG_COMPOUND)?com.fangsu.platform.PlatformOverlays.state(tag.getCompound("originalBlock")):null;originalEntity=tag.contains("originalEntity",Tag.TAG_COMPOUND)?tag.getCompound("originalEntity").copy():null;applyGeometry(tag.getUUID("group"),BlockPos.of(tag.getLong("first")),BlockPos.of(tag.getLong("last")),tag.getBoolean("left"),PlatformPreset.decode(tag.getString("settings")),PlatformGeometryCodec.read(tag.getCompound("geometry")));}
        catch(RuntimeException e){geometry=null;skin=false;collision=outline=Shapes.empty();com.fangsu.Main.LOGGER.warn("[FangSu] Invalid curved platform cell at {}",worldPosition);}
    }
    public CompoundTag serverData(){CompoundTag tag=new CompoundTag();saveAdditional(tag);return tag;}
    @Override public CompoundTag getUpdateTag(){CompoundTag tag=new CompoundTag();saveCell(tag);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
