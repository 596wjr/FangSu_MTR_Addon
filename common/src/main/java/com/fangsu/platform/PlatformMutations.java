package com.fangsu.platform;

import com.fangsu.blockEntities.BlockEntityCurvedPlatform;
import com.fangsu.blocks.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** 上游 internalMutation / removedCell / restoreSkins 的跨加载器适配。 */
public final class PlatformMutations {
    private static final ThreadLocal<Boolean> INTERNAL=ThreadLocal.withInitial(()->false);
    private static final Map<ServerLevel,Map<UUID,ArrayDeque<BlockPos>>> REMOVALS=new HashMap<>();
    private record Skin(ServerLevel level,BlockPos pos) { }
    private static final Set<Skin> RESTORE=new LinkedHashSet<>();
    private record Original(BlockState state,net.minecraft.nbt.CompoundTag entity) { }
    private static final Map<Skin,Original> ORIGINALS=new LinkedHashMap<>();
    public static boolean internal(){return INTERNAL.get();}
    public static void run(Runnable action){boolean old=INTERNAL.get();INTERNAL.set(true);try{action.run();}finally{INTERNAL.set(old);}}
    public static void clear(MinecraftServer server){REMOVALS.keySet().removeIf(level->level.getServer()==server);RESTORE.removeIf(skin->skin.level().getServer()==server);ORIGINALS.keySet().removeIf(skin->skin.level().getServer()==server);}
    public static void removeGroup(ServerLevel level,UUID group){
        if(internal()||group==null)return;
        CurvedPlatformPackets.cancelGroup(level,group);
        // 持久化删除意图；跨未加载区块和服务器重启继续，不强制加载区块。
        PlatformLayouts.get(level).markDeleting(group);
    }
    public static void restoreSoon(ServerLevel level,BlockPos pos){RESTORE.add(new Skin(level,pos.immutable()));}
    public static void restoreOriginalSoon(ServerLevel level,BlockPos pos,BlockState original){restoreOriginalSoon(level,pos,original,null);}
    public static void restoreOriginalSoon(ServerLevel level,BlockPos pos,BlockState original,net.minecraft.nbt.CompoundTag entity){if(original!=null)ORIGINALS.put(new Skin(level,pos.immutable()),new Original(original,entity==null?null:entity.copy()));}
    /** 普通方块改为曲面宿主；原始状态留在宿主 NBT，避免原生整格模型和碰撞透出来。 */
    public static boolean materialize(ServerLevel level,BlockPos pos,net.minecraft.nbt.CompoundTag tag,BlockState original){
        var data=tag.copy();if(original!=null&&!original.isAir())data.put("originalBlock",net.minecraft.nbt.NbtUtils.writeBlockState(original));
        var oldEntity=level.getBlockEntity(pos);if(oldEntity!=null)data.put("originalEntity",oldEntity.saveWithFullMetadata());
        var checked=new BlockEntityCurvedPlatform(pos,ModBlocks.BLOCK_CURVED_PLATFORM.get().defaultBlockState());checked.load(data);if(checked.geometry==null)return false;
        run(()->{
            var state=ModBlocks.BLOCK_CURVED_PLATFORM.get().defaultBlockState();
            if(!level.getBlockState(pos).is(ModBlocks.BLOCK_CURVED_PLATFORM.get()))level.removeBlockEntity(pos);
            boolean updated=level.setBlockAndUpdate(pos,state);
            if(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell){
                cell.load(data);
                cell.setChanged();
                // 相同宿主状态下，原版 setBlock 不会发送更新；几何仍需要同步一次。
                if(!updated)level.sendBlockUpdated(pos,state,state,2);
            }
        });
        boolean placed=level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell&&data.hasUUID("group")&&data.getUUID("group").equals(cell.group);
        if(placed)PlatformOverlays.get(level).remove(pos);return placed;
    }
    public static void removeCell(ServerLevel level,BlockPos pos,UUID group){
        if(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell&&group.equals(cell.group)){
            var original=cell.originalBlock();var snapshot=new net.minecraft.nbt.CompoundTag();snapshot.put("state",net.minecraft.nbt.NbtUtils.writeBlockState(original==null?Blocks.AIR.defaultBlockState():original));if(cell.originalEntity()!=null)snapshot.put("entity",cell.originalEntity());PlatformBlockSnapshot.restore(level,pos,snapshot);
        }
        if(PlatformOverlays.get(level).owns(pos,group))PlatformOverlays.get(level).remove(pos);
    }
    public static void tick(MinecraftServer server){
        int budget=128;List<ServerLevel> levels=new ArrayList<>();server.getAllLevels().forEach(levels::add);
        for(ServerLevel level:levels){
            var layouts=PlatformLayouts.get(level);var groups=REMOVALS.computeIfAbsent(level,k->new LinkedHashMap<>());
            for(UUID group:layouts.deletingGroups())groups.computeIfAbsent(group,id->{ArrayDeque<BlockPos> positions=new ArrayDeque<>();for(long p:layouts.cells(id))positions.add(BlockPos.of(p));return positions;});
            int worldBudget=Math.min(budget,Math.max(1,128/Math.max(1,levels.size())));List<UUID> rotated=new ArrayList<>();
            for(var iterator=groups.entrySet().iterator();iterator.hasNext()&&worldBudget>0;){
                var removal=iterator.next();UUID group=removal.getKey();var positions=removal.getValue();int available=positions.size();
                int batch=Math.min(32,worldBudget);
                while(available-->0&&!positions.isEmpty()&&batch-->0){
                    budget--;worldBudget--;
                    BlockPos pos=positions.removeFirst();
                    // 覆盖层不持有原生方块；删除它永远不会删除后来放上的屏蔽门等物件。
                    if(!level.hasChunkAt(pos)){positions.addLast(pos);continue;}
                    removeCell(level,pos,group);
                }
                if(positions.isEmpty()){layouts.remove(group);iterator.remove();}else rotated.add(group);
            }
            // 未加载的长站台不能一直挡住其他布局或维度的拆除。
            for(UUID id:rotated){var positions=groups.remove(id);groups.put(id,positions);}
        }
        int restoreBudget=64;List<Skin> retry=new ArrayList<>();
        for(var iterator=RESTORE.iterator();iterator.hasNext()&&restoreBudget-->0;){
            Skin skin=iterator.next();if(skin.level().getServer()!=server)continue;iterator.remove();
            ServerLevel level=skin.level();BlockPos pos=skin.pos();var overlay=PlatformOverlays.get(level).entry(pos);
            if(overlay==null||!overlay.covered()||!level.hasChunkAt(pos)||!level.getBlockState(pos).isAir())continue;
            UUID group=overlay.cell().getUUID("group");if(PlatformLayouts.get(level).deleting(group))continue;if(CurvedPlatformPackets.busy(level,group)){retry.add(skin);continue;}
            var data=overlay.cell().copy();
            run(()->{level.setBlockAndUpdate(pos,ModBlocks.BLOCK_CURVED_PLATFORM.get().defaultBlockState());if(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell)cell.load(data);});
            if(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell&&group.equals(cell.group))PlatformOverlays.get(level).remove(pos);
        }
        RESTORE.addAll(retry);
        // 只恢复仍为空的格，不覆盖玩家在事件之后放上的方块。
        int originalBudget=64;
        for(var iterator=ORIGINALS.entrySet().iterator();iterator.hasNext()&&originalBudget-->0;){
            var row=iterator.next();Skin skin=row.getKey();if(skin.level().getServer()!=server)continue;iterator.remove();
            if(skin.level().hasChunkAt(skin.pos())&&skin.level().getBlockState(skin.pos()).isAir()){
                var snapshot=new net.minecraft.nbt.CompoundTag();snapshot.put("state",net.minecraft.nbt.NbtUtils.writeBlockState(row.getValue().state()));if(row.getValue().entity()!=null)snapshot.put("entity",row.getValue().entity());PlatformBlockSnapshot.restore(skin.level(),skin.pos(),snapshot);
            }
        }
    }
    private PlatformMutations(){ }
}
