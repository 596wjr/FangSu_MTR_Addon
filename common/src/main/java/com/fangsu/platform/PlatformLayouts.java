package com.fangsu.platform;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** 独立布局索引让重启后的编辑、缩小覆盖范围及整组移除不依赖扫描整个世界。 */
public final class PlatformLayouts extends SavedData {
    private final Map<UUID,long[]> groups=new HashMap<>();
    private final Map<UUID,Long> revisions=new HashMap<>();
    private final Map<UUID,UUID> owners=new HashMap<>();
    private final Set<UUID> deleting=new HashSet<>();
    public long revision(UUID id){return revisions.getOrDefault(id,0L);}
    public static PlatformLayouts get(ServerLevel level){
        //#if MC_VERSION >= 12002
        //$$ return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PlatformLayouts::new,PlatformLayouts::load,null),"fangsu_curved_platforms");
        //#else
        return level.getDataStorage().computeIfAbsent(PlatformLayouts::load,PlatformLayouts::new,"fangsu_curved_platforms");
        //#endif
    }
    public long[] cells(UUID id){return groups.getOrDefault(id,new long[0]).clone();}
    public boolean canManage(net.minecraft.server.level.ServerPlayer player,UUID id){return PlatformPlacementPolicy.canEdit(player)&&(player.isCreative()||player.hasPermissions(2)||player.getUUID().equals(owners.get(id)));}
    public void ownerIfAbsent(UUID id,UUID owner){if(owners.putIfAbsent(id,owner)==null)setDirty();}
    public boolean deleting(UUID id){return deleting.contains(id);}
    public Set<UUID> deletingGroups(){return Set.copyOf(deleting);}
    public void markDeleting(UUID id){if(groups.containsKey(id)&&deleting.add(id)){revisions.merge(id,1L,Long::sum);setDirty();}}
    public void put(UUID id,Collection<net.minecraft.core.BlockPos> positions){revisions.merge(id,1L,Long::sum);groups.put(id,positions.stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());setDirty();}
    public void remove(UUID id){groups.remove(id);owners.remove(id);deleting.remove(id);revisions.merge(id,1L,Long::sum);setDirty();}
    public static PlatformLayouts load(CompoundTag tag){PlatformLayouts result=new PlatformLayouts();ListTag list=tag.getList("groups",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){var g=list.getCompound(i);if(g.hasUUID("id")){long[] cells=g.getLongArray("cells");if(cells.length<=131072){UUID id=g.getUUID("id");result.groups.put(id,cells);if(g.hasUUID("owner"))result.owners.put(id,g.getUUID("owner"));if(g.getBoolean("deleting"))result.deleting.add(id);}}}return result;}
    @Override public CompoundTag save(CompoundTag tag){ListTag list=new ListTag();groups.forEach((id,cells)->{CompoundTag group=new CompoundTag();group.putUUID("id",id);group.putLongArray("cells",cells);group.putBoolean("deleting",deleting.contains(id));if(owners.containsKey(id))group.putUUID("owner",owners.get(id));list.add(group);});tag.put("groups",list);return tag;}
}
