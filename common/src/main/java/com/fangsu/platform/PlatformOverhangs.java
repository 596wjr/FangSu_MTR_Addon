package com.fangsu.platform;

import com.fangsu.blockEntities.BlockEntityCurvedPlatform;
import dev.potato.curvedplatform.geometry.CellGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** 附属片只登记形状查询地址，世界里不放置代理方块，也不另存一个布局成员。 */
public final class PlatformOverhangs {
    private static final class Index {
        final Map<BlockPos,Set<BlockPos>> targets=new HashMap<>(),owners=new HashMap<>();
        void remove(BlockPos owner){var old=owners.remove(owner);if(old!=null)for(var target:old){var list=targets.get(target);if(list!=null){list.remove(owner);if(list.isEmpty())targets.remove(target);}}}
    }
    private static final Map<Level,Index> WORLDS=new WeakHashMap<>();
    public static synchronized void update(Level level,BlockPos owner,CellGeometry geometry){
        if(level==null)return;var parts=PlatformCellMerger.parts(geometry);var index=WORLDS.get(level);
        if(index!=null)index.remove(owner);if(parts.isEmpty())return;
        if(index==null){index=new Index();WORLDS.put(level,index);}BlockPos immutable=owner.immutable();Set<BlockPos> targets=new HashSet<>();
        for(var part:parts){BlockPos target=immutable.offset(part.dx(),0,part.dz());targets.add(target);index.targets.computeIfAbsent(target,k->new HashSet<>()).add(immutable);}index.owners.put(immutable,targets);
    }
    private static synchronized List<BlockPos> owners(Level level,BlockPos target){var index=WORLDS.get(level);return index==null?List.of():List.copyOf(index.targets.getOrDefault(target,Set.of()));}
    private static CellGeometry geometry(Level level,BlockPos owner){
        if(!level.hasChunkAt(owner))return null;
        if(level.getBlockEntity(owner) instanceof BlockEntityCurvedPlatform cell&&!cell.isRemoved())return cell.geometry;
        if(level instanceof ServerLevel server){var entry=PlatformOverlays.get(server).entry(owner);return entry!=null&&entry.matches(level.getBlockState(owner))?entry.geometry():null;}
        var cell=PlatformOverlayClient.at(owner);return cell==null?null:cell.geometry;
    }
    public static VoxelShape shape(Level level,BlockPos target,boolean outline){
        VoxelShape result=Shapes.empty();for(var owner:owners(level,target)){
            if(!level.hasChunkAt(owner))continue;var geometry=geometry(level,owner);if(geometry==null){update(level,owner,null);continue;}
            for(var part:PlatformCellMerger.parts(geometry))if(owner.getX()+part.dx()==target.getX()&&owner.getY()==target.getY()&&owner.getZ()+part.dz()==target.getZ())
                result=Shapes.or(result,BlockEntityCurvedPlatform.shape(outline?part.geometry().outlineBoxes():part.geometry().collisionBoxes()));
        }return result;
    }
    public static void removed(Level level,BlockPos owner){
        if(level==null)return;
        if(level instanceof ServerLevel server){var entry=PlatformOverlays.get(server).entry(owner);update(level,owner,entry==null?null:entry.geometry());}
        else{var overlay=PlatformOverlayClient.at(owner);update(level,owner,overlay==null?null:overlay.geometry);}
    }
    /** 配置器与整组拆除可把附属片命中的空格映射回真正的主体。 */
    public static BlockPos owner(Level level,BlockPos target){if(!level.hasChunkAt(target)||!level.getBlockState(target).isAir())return null;for(var owner:owners(level,target))if(geometry(level,owner)!=null)return owner;return null;}
    private PlatformOverhangs(){ }
}
