package com.fangsu.platform;

import com.fangsu.blockEntities.*;
import com.fangsu.blocks.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import java.util.*;

/** 统一构件宿主适配；原始库存与曲面覆盖数据由服务端布局快照保存。 */
public final class PlatformDoorAdapter {
    public static CompoundTag signature(ServerLevel level,BlockPos pos){
        CompoundTag result=new CompoundTag();result.put("state",net.minecraft.nbt.NbtUtils.writeBlockState(level.getBlockState(pos)));
        if(level.getBlockEntity(pos) instanceof BlockEntityAutoPlatformComponent host&&host.group!=null){result.putUUID("group",host.group);ListTag parts=new ListTag();for(var p:host.components())parts.add(p.save());result.put("components",parts);result.put("metadata",host.metadata());}
        else{var entity=level.getBlockEntity(pos);if(entity!=null)result.put("entity",entity.saveWithFullMetadata());}
        var overlay=PlatformOverlays.get(level).entry(pos);if(overlay!=null)result.put("overlay",PlatformOverlays.get(level).packet(pos));return result;
    }
    public static boolean matches(ServerLevel level,BlockPos pos,UUID group,CompoundTag expected){
        if(!(level.getBlockEntity(pos) instanceof BlockEntityAutoPlatformComponent host)||!group.equals(host.group))return false;
        var current=signature(level,pos);
        if(current.equals(expected))return true;
        // 旧布局的预期签名仍可能保存整份施工配置。
        var metadata=expected.getCompound("metadata");
        var compact=BlockEntityAutoPlatformComponent.compactMetadata(metadata);
        if(metadata.equals(compact))return false;
        var migrated=expected.copy();migrated.put("metadata",compact);
        return current.equals(migrated);
    }
    public static boolean canCoverCurve(BlockEntityCurvedPlatform cell,List<PlatformDoorComponent> components){
        return cell.geometry!=null&&(cell.coverable()||!components.isEmpty()&&components.stream().allMatch(p->p.dy()+1e-6>=cell.outline().max(Direction.Axis.Y)));
    }
    public static boolean collision(ServerLevel level,BlockPos pos,UUID group,List<PlatformDoorComponent> components){
        if(level.getBlockEntity(pos) instanceof BlockEntityAutoPlatformComponent host&&group.equals(host.group))return false;
        if(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform curve&&canCoverCurve(curve,components))return false;
        var state=level.getBlockState(pos);if(state.hasBlockEntity()||!state.getFluidState().isEmpty())return true;
        //#if MC_VERSION >= 11904
        return !state.isAir()&&!state.canBeReplaced();
        //#else
        //$$ return !state.isAir()&&!state.getMaterial().isReplaceable();
        //#endif
    }
    public static boolean place(ServerLevel level,BlockPos pos,UUID group,List<PlatformDoorComponent> components,CompoundTag metadata){
        CompoundTag curve=null;if(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell&&canCoverCurve(cell,components))curve=cell.serverData();else{var overlay=PlatformOverlays.get(level).entry(pos);if(overlay!=null)curve=overlay.cell().copy();}
        var state=ModBlocks.BLOCK_AUTO_PLATFORM_COMPONENT.get().defaultBlockState().setValue(BaseObjBlock.FACING,Direction.NORTH);
        final CompoundTag underlay=curve;PlatformMutations.run(()->{
            if(!(level.getBlockEntity(pos) instanceof BlockEntityAutoPlatformComponent))level.removeBlockEntity(pos);
            level.setBlockAndUpdate(pos,state);
            if(level.getBlockEntity(pos) instanceof BlockEntityAutoPlatformComponent host)host.configure(group,components,metadata);
        });
        if(!(level.getBlockEntity(pos) instanceof BlockEntityAutoPlatformComponent host)||!group.equals(host.group))return false;
        if(underlay!=null)PlatformOverlays.get(level).cover(pos,state,underlay);
        return true;
    }
    private PlatformDoorAdapter(){ }
}
