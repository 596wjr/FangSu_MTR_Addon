package com.fangsu.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** 原方块、生成签名及单元参数仅保存服务端；部分施工也能在重启后重新编辑或整组恢复。 */
public final class PlatformDoorLayouts extends SavedData {
    public record Cell(CompoundTag original,CompoundTag expected) { }
    public record CentralControl(BlockPos controller,boolean open) { }
    public static final class Layout {
        public final UUID id,owner;public CompoundTag metadata;public long revision;public final Map<BlockPos,Cell> cells=new LinkedHashMap<>();
        public Layout(UUID id,UUID owner,CompoundTag metadata){this.id=id;this.owner=owner;this.metadata=metadata.copy();}
    }
    private final Map<UUID,Layout> layouts=new LinkedHashMap<>();
    private final Map<UUID,CentralControl> centralControls=new HashMap<>();
    public static PlatformDoorLayouts get(ServerLevel level){
        //#if MC_VERSION >= 12002
        //$$ return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PlatformDoorLayouts::new,PlatformDoorLayouts::load,null),"fangsu_platform_doors");
        //#else
        return level.getDataStorage().computeIfAbsent(PlatformDoorLayouts::load,PlatformDoorLayouts::new,"fangsu_platform_doors");
        //#endif
    }
    public Layout get(UUID id){return layouts.get(id);}
    public Layout at(BlockPos pos){
        for(var layout:layouts.values())if(layout.cells.containsKey(pos))return layout;
        return null;
    }
    public CentralControl centralControl(UUID group){return centralControls.get(group);}
    public void updateCentralControl(BlockPos controller, Set<UUID> groups, boolean isolated, boolean open) {
        boolean changed = centralControls.entrySet().removeIf(entry -> entry.getValue().controller().equals(controller)
                && (!isolated || !groups.contains(entry.getKey())));
        if (isolated) {
            var command = new CentralControl(controller.immutable(), open);
            for (var group : groups) {
                if (layouts.containsKey(group) && !command.equals(centralControls.put(group, command))) changed = true;
            }
        }
        if (changed) setDirty();
    }
    public boolean canManage(ServerPlayer player,Layout layout){return layout!=null&&PlatformPlacementPolicy.canEdit(player)&&(player.isCreative()||player.hasPermissions(2)||layout.owner.equals(player.getUUID()));}
    public Layout find(UUID source,BlockPos first,BlockPos last,boolean left){for(var l:layouts.values()){var m=l.metadata;if(m.getBoolean("left")!=left)continue;if(source!=null&&m.hasUUID("source")&&source.equals(m.getUUID("source")))return l;if(source==null&&m.getLong("first")==first.asLong()&&m.getLong("last")==last.asLong())return l;}return null;}
    public void put(Layout layout){layouts.put(layout.id,layout);layout.revision++;setDirty();}
    public void remove(UUID id){layouts.remove(id);centralControls.remove(id);setDirty();}
    public static PlatformDoorLayouts load(CompoundTag tag){PlatformDoorLayouts result=new PlatformDoorLayouts();ListTag groups=tag.getList("groups",Tag.TAG_COMPOUND);for(int n=0;n<Math.min(4096,groups.size());n++){var g=groups.getCompound(n);try{
        if(!g.hasUUID("id")||!g.hasUUID("owner"))continue;var meta=g.getCompound("metadata");PlatformDoorSettings.decode(meta.getString("doors"));dev.potato.curvedplatform.geometry.PlatformPreset.decode(meta.getString("platform"));Layout layout=new Layout(g.getUUID("id"),g.getUUID("owner"),meta);layout.revision=g.getLong("revision");ListTag cells=g.getList("cells",Tag.TAG_COMPOUND);if(cells.size()>4096)continue;for(int i=0;i<cells.size();i++){var c=cells.getCompound(i);layout.cells.put(BlockPos.of(c.getLong("pos")),new Cell(c.getCompound("original").copy(),c.getCompound("expected").copy()));}result.layouts.put(layout.id,layout);
    }catch(RuntimeException ignored){}}
        ListTag controls=tag.getList("centralControls",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(4096,controls.size());i++){
            var row=controls.getCompound(i);
            if(!row.hasUUID("group")||!row.contains("controller",Tag.TAG_LONG))continue;
            var group=row.getUUID("group");
            if(result.layouts.containsKey(group))result.centralControls.put(group,new CentralControl(BlockPos.of(row.getLong("controller")),row.getBoolean("open")));
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag){ListTag groups=new ListTag();for(var l:layouts.values()){CompoundTag g=new CompoundTag();g.putUUID("id",l.id);g.putUUID("owner",l.owner);g.putLong("revision",l.revision);g.put("metadata",l.metadata.copy());ListTag cells=new ListTag();l.cells.forEach((p,c)->{CompoundTag row=new CompoundTag();row.putLong("pos",p.asLong());row.put("original",c.original().copy());row.put("expected",c.expected().copy());cells.add(row);});g.put("cells",cells);groups.add(g);}tag.put("groups",groups);
        ListTag controls=new ListTag();
        centralControls.forEach((group,command)->{
            var row=new CompoundTag();
            row.putUUID("group",group);
            row.putLong("controller",command.controller().asLong());
            row.putBoolean("open",command.open());
            controls.add(row);
        });
        tag.put("centralControls",controls);
        return tag;
    }
}
