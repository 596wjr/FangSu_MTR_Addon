package com.fangsu.platform;

import com.fangsu.blockEntities.BlockEntityCurvedPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** 仅保存被玩家方块覆盖的曲面数据；新施工使用真实曲面宿主。 */
public final class PlatformOverlays extends SavedData {
    public static final class Entry {
        private BlockState expected;private final boolean covered;private final CompoundTag cell;private final dev.potato.curvedplatform.geometry.CellGeometry geometry;
        private net.minecraft.world.phys.shapes.VoxelShape collision,outline;
        Entry(BlockState expected,CompoundTag cell,boolean covered){if(cell.getInt("version")!=1||!cell.hasUUID("group"))throw new IllegalArgumentException("invalid_overlay");dev.potato.curvedplatform.geometry.PlatformPreset.decode(cell.getString("settings"));geometry=PlatformGeometryCodec.read(cell.getCompound("geometry"));this.expected=expected;this.cell=cell;this.covered=covered;}
        public dev.potato.curvedplatform.geometry.CellGeometry geometry(){return geometry;}
        public BlockState expected(){return expected;}
        public CompoundTag cell(){return cell;}
        public boolean covered(){return covered;}
        public boolean matches(BlockState state){return covered?!state.isAir()&&!state.is(com.fangsu.blocks.ModBlocks.BLOCK_CURVED_PLATFORM.get()):state.equals(expected);}
        public net.minecraft.world.phys.shapes.VoxelShape collision(){if(collision==null)collision=BlockEntityCurvedPlatform.shape(geometry.collisionBoxes());return collision;}
        public net.minecraft.world.phys.shapes.VoxelShape outline(){if(outline==null)outline=BlockEntityCurvedPlatform.shape(geometry.outlineBoxes());return outline;}
    }
    private ServerLevel registeredLevel;
    private final Map<BlockPos,Entry> entries=new HashMap<>();
    private final Map<Long,Set<BlockPos>> chunks=new HashMap<>();
    private final Map<Long,Long> chunkRevisions=new HashMap<>();
    private long revision;
    long chunkRevision(long chunk){return chunkRevisions.getOrDefault(chunk,0L);}
    public static PlatformOverlays get(ServerLevel level){
        //#if MC_VERSION >= 12002
        //$$ var result=level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PlatformOverlays::new,PlatformOverlays::load,null),"fangsu_platform_overlays");
        //#else
        var result=level.getDataStorage().computeIfAbsent(PlatformOverlays::load,PlatformOverlays::new,"fangsu_platform_overlays");
        //#endif
        if(result.registeredLevel!=level){result.registeredLevel=level;result.entries.forEach((pos,entry)->PlatformOverhangs.update(level,pos,entry.geometry()));}return result;
    }
    static long chunk(BlockPos pos){return net.minecraft.world.level.ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4);}
    public Entry entry(BlockPos pos){return entries.get(pos);}
    public boolean owns(BlockPos pos,UUID group){var entry=entries.get(pos);return entry!=null&&entry.cell().hasUUID("group")&&group.equals(entry.cell().getUUID("group"));}
    public void put(BlockPos pos,BlockState expected,CompoundTag cell){
        put(pos,expected,cell,false);
    }
    private void put(BlockPos pos,BlockState expected,CompoundTag cell,boolean covered){entries.put(pos.immutable(),new Entry(expected,cell.copy(),covered));chunks.computeIfAbsent(chunk(pos),k->new HashSet<>()).add(pos.immutable());chunkRevisions.put(chunk(pos),++revision);setDirty();if(registeredLevel!=null)PlatformOverhangs.update(registeredLevel,pos,entries.get(pos).geometry());}
    public void cover(BlockPos pos,BlockState expected,CompoundTag cell){put(pos,expected,cell,true);}
    public void changed(ServerLevel level,BlockPos pos,BlockState next){
        if(PlatformMutations.internal())return;var entry=entries.get(pos);if(entry==null)return;
        if(entry.covered()){
            if(next.isAir())PlatformMutations.restoreSoon(level,pos);
            else if(!next.equals(entry.expected())){entry.expected=next;chunkRevisions.put(chunk(pos),++revision);setDirty();}
        }else if(!next.equals(entry.expected()))remove(pos);
    }
    public void remove(BlockPos pos){
        if(entries.remove(pos)==null)return;
        long key=chunk(pos);
        var points=chunks.get(key);
        points.remove(pos);
        if(points.isEmpty()){chunks.remove(key);chunkRevisions.remove(key);}
        else chunkRevisions.put(key,++revision);
        setDirty();
    }
    public List<BlockPos> nearby(ServerLevel level,BlockPos center){
        List<BlockPos> result=new ArrayList<>();int cx=center.getX()>>4,cz=center.getZ()>>4;
        for(int x=cx-5;x<=cx+5;x++)for(int z=cz-5;z<=cz+5;z++)for(var pos:new ArrayList<>(chunks.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(x,z),Set.of()))){
            if(!level.hasChunkAt(pos))continue;var entry=entries.get(pos);
            if(entry!=null&&entry.covered()&&level.getBlockState(pos).isAir()){PlatformMutations.restoreSoon(level,pos);continue;}
            if(entry!=null&&entry.matches(level.getBlockState(pos))){result.add(pos);if(result.size()>=65536)return result;}else remove(pos);
        }
        return result;
    }
    public CompoundTag packet(BlockPos pos){var entry=entries.get(pos);CompoundTag result=new CompoundTag();result.putLong("pos",pos.asLong());result.put("expected",net.minecraft.nbt.NbtUtils.writeBlockState(entry.expected()));result.putBoolean("covered",entry.covered());result.put("cell",entry.cell());return result;}
    CompoundTag clientPacket(BlockPos pos){
        var packet=packet(pos);
        var cell=packet.getCompound("cell");
        var visible=new CompoundTag();
        for(String key:cell.getAllKeys())if(!key.equals("originalBlock")&&!key.equals("originalEntity"))visible.put(key,cell.get(key));
        packet.put("cell",visible);
        return packet;
    }
    public static BlockState state(CompoundTag tag){
        //#if MC_VERSION >= 11903
        return net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),tag);
        //#else
        //$$ return net.minecraft.nbt.NbtUtils.readBlockState(tag);
        //#endif
    }
    public static PlatformOverlays load(CompoundTag tag){var result=new PlatformOverlays();var list=tag.getList("entries",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){var row=list.getCompound(i);var pos=BlockPos.of(row.getLong("pos"));try{result.put(pos,state(row.getCompound("expected")),row.getCompound("cell"),row.getBoolean("covered"));}catch(RuntimeException ignored){}}return result;}
    @Override public CompoundTag save(CompoundTag tag){ListTag list=new ListTag();for(var pos:entries.keySet())list.add(packet(pos));tag.put("entries",list);return tag;}
}
