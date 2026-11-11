package com.fangsu.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** 原方块、生成签名及单元参数仅保存服务端；部分施工也能在重启后重新编辑或整组恢复。 */
public final class PlatformDoorLayouts extends SavedData {
    public record Cell(CompoundTag original,CompoundTag expected) { }
    public static final class Layout {
        public final UUID id,owner;public CompoundTag metadata;public long revision;public final Map<BlockPos,Cell> cells=new LinkedHashMap<>();
        public Layout(UUID id,UUID owner,CompoundTag metadata){this.id=id;this.owner=owner;this.metadata=metadata.copy();}
    }
    private final Map<UUID,Layout> layouts=new LinkedHashMap<>();
    public static PlatformDoorLayouts get(ServerLevel level){
        //#if MC_VERSION >= 12002
        //$$ return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PlatformDoorLayouts::new,PlatformDoorLayouts::load,null),"fangsu_platform_doors");
        //#else
        return level.getDataStorage().computeIfAbsent(PlatformDoorLayouts::load,PlatformDoorLayouts::new,"fangsu_platform_doors");
        //#endif
    }
    public Layout get(UUID id){return layouts.get(id);}
    public boolean canManage(ServerPlayer player,Layout layout){return layout!=null&&PlatformPlacementPolicy.canEdit(player)&&(player.isCreative()||player.hasPermissions(2)||layout.owner.equals(player.getUUID()));}
    public Layout find(UUID source,BlockPos first,BlockPos last,boolean left){for(var l:layouts.values()){var m=l.metadata;if(m.getBoolean("left")!=left)continue;if(source!=null&&m.hasUUID("source")&&source.equals(m.getUUID("source")))return l;if(source==null&&m.getLong("first")==first.asLong()&&m.getLong("last")==last.asLong())return l;}return null;}
    public void put(Layout layout){layouts.put(layout.id,layout);layout.revision++;setDirty();}
    public void remove(UUID id){layouts.remove(id);setDirty();}
    public static PlatformDoorLayouts load(CompoundTag tag){PlatformDoorLayouts result=new PlatformDoorLayouts();ListTag groups=tag.getList("groups",Tag.TAG_COMPOUND);for(int n=0;n<Math.min(4096,groups.size());n++){var g=groups.getCompound(n);try{
        if(!g.hasUUID("id")||!g.hasUUID("owner"))continue;var meta=g.getCompound("metadata");PlatformDoorSettings.decode(meta.getString("doors"));dev.potato.curvedplatform.geometry.PlatformPreset.decode(meta.getString("platform"));Layout layout=new Layout(g.getUUID("id"),g.getUUID("owner"),meta);layout.revision=g.getLong("revision");ListTag cells=g.getList("cells",Tag.TAG_COMPOUND);if(cells.size()>4096)continue;for(int i=0;i<cells.size();i++){var c=cells.getCompound(i);layout.cells.put(BlockPos.of(c.getLong("pos")),new Cell(c.getCompound("original").copy(),c.getCompound("expected").copy()));}result.layouts.put(layout.id,layout);
    }catch(RuntimeException ignored){}}return result;}
    @Override public CompoundTag save(CompoundTag tag){ListTag groups=new ListTag();for(var l:layouts.values()){CompoundTag g=new CompoundTag();g.putUUID("id",l.id);g.putUUID("owner",l.owner);g.putLong("revision",l.revision);g.put("metadata",l.metadata.copy());ListTag cells=new ListTag();l.cells.forEach((p,c)->{CompoundTag row=new CompoundTag();row.putLong("pos",p.asLong());row.put("original",c.original().copy());row.put("expected",c.expected().copy());cells.add(row);});g.put("cells",cells);groups.add(g);}tag.put("groups",groups);return tag;}
}
