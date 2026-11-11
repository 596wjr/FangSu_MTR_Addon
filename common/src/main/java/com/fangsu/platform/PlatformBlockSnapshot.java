package com.fangsu.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** 仅服务端保存完整原方块；强制施工和回滚不能遗失方块实体内容。 */
public final class PlatformBlockSnapshot {
    public static CompoundTag capture(ServerLevel level,BlockPos pos){
        CompoundTag result=new CompoundTag();result.put("state",net.minecraft.nbt.NbtUtils.writeBlockState(level.getBlockState(pos)));
        var entity=level.getBlockEntity(pos);if(entity!=null)result.put("entity",entity.saveWithFullMetadata());
        if(PlatformOverlays.get(level).entry(pos)!=null)result.put("overlay",PlatformOverlays.get(level).packet(pos));
        return result;
    }
    public static BlockState state(CompoundTag snapshot){return PlatformOverlays.state(snapshot.getCompound("state"));}
    public static boolean restore(ServerLevel level,BlockPos pos,CompoundTag snapshot){
        if(!level.hasChunkAt(pos))return false;
        BlockState state=state(snapshot);
        // 来源站台已经被整组拆除时，恢复其地基，不复活已删除的曲面宿主。
        if(state.is(com.fangsu.blocks.ModBlocks.BLOCK_CURVED_PLATFORM.get())&&snapshot.contains("entity",Tag.TAG_COMPOUND)){
            var entity=snapshot.getCompound("entity");
            if(entity.hasUUID("group")&&(PlatformLayouts.get(level).deleting(entity.getUUID("group"))||PlatformLayouts.get(level).cells(entity.getUUID("group")).length==0)){
                CompoundTag foundation=new CompoundTag();foundation.put("state",entity.contains("originalBlock",Tag.TAG_COMPOUND)?entity.getCompound("originalBlock").copy():net.minecraft.nbt.NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));if(entity.contains("originalEntity",Tag.TAG_COMPOUND))foundation.put("entity",entity.getCompound("originalEntity").copy());return restore(level,pos,foundation);
            }
        }
        PlatformMutations.run(()->{
            if(!level.getBlockState(pos).equals(state))level.removeBlockEntity(pos);level.setBlockAndUpdate(pos,state);
            if(level.getBlockEntity(pos)==null&&state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock block){var entity=block.newBlockEntity(pos,state);if(entity!=null)level.setBlockEntity(entity);}
            if(snapshot.contains("entity",Tag.TAG_COMPOUND)&&level.getBlockEntity(pos)!=null){level.getBlockEntity(pos).load(snapshot.getCompound("entity").copy());level.getBlockEntity(pos).setChanged();}
        });
        if(!level.getBlockState(pos).equals(state))return false;
        PlatformOverlays.get(level).remove(pos);
        if(snapshot.contains("overlay",Tag.TAG_COMPOUND)){
            var overlay=snapshot.getCompound("overlay");var expected=PlatformOverlays.state(overlay.getCompound("expected"));
            if(overlay.getBoolean("covered"))PlatformOverlays.get(level).cover(pos,expected,overlay.getCompound("cell"));
            else PlatformOverlays.get(level).put(pos,expected,overlay.getCompound("cell"));
        }
        level.sendBlockUpdated(pos,state,state,3);return true;
    }
    private PlatformBlockSnapshot(){ }
}
