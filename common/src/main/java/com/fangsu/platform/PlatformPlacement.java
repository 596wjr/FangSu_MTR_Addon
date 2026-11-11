package com.fangsu.platform;

import com.fangsu.blockEntities.BlockEntityCurvedPlatform;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.*;

/** 原版 underSkin：薄顶皮下方为空且目标方块可在那里存活时，下放一格。 */
public final class PlatformPlacement {
    public static BlockPlaceContext underSkin(BlockItem item,BlockPlaceContext original){
        if(original==null)return null;
        var player=original.getPlayer();var level=original.getLevel();var pos=original.getClickedPos();
        if(player==null||player.isShiftKeyDown()||!(level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell)||!cell.floorSkin()||cell.outline().max(Direction.Axis.Y)>2.44150625E-4)return original;
        BlockPos floor=pos.below();
        if(!level.getBlockState(floor).isAir()||!item.getBlock().defaultBlockState().canSurvive(level,floor))return original;
        if(level instanceof ServerLevel server&&(!player.hasPermissions(2)||!server.mayInteract(player,floor)||CurvedPlatformPackets.busy(server,cell.group)))return null;
        return new BlockPlaceContext(player,original.getHand(),original.getItemInHand(),new BlockHitResult(Vec3.atCenterOf(floor),Direction.UP,floor,false));
    }
    private PlatformPlacement(){ }
}
