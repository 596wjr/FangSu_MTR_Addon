package com.fangsu.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** 在原生方块形状上合并曲面，不改变方块类型、朝向、方块交互或原本的碰撞。 */
public final class PlatformOverlayCollision {
    private static final ThreadLocal<Boolean> NATIVE=ThreadLocal.withInitial(()->false);
    public static VoxelShape nativeShape(BlockState state,BlockGetter view,BlockPos pos){
        boolean previous=NATIVE.get();NATIVE.set(true);try{return state.getShape(view,pos);}finally{NATIVE.set(previous);}
    }
    public static VoxelShape add(BlockGetter view,BlockPos pos,VoxelShape original,boolean outline){
        if(NATIVE.get()||!(view instanceof Level level))return original;
        VoxelShape extra=null;
        if(level instanceof ServerLevel server){var entry=PlatformOverlays.get(server).entry(pos);if(entry!=null&&entry.matches(level.getBlockState(pos)))extra=outline?entry.outline():entry.collision();}
        else if(level.isClientSide){var cell=PlatformOverlayClient.at(pos);if(cell!=null)extra=outline?cell.outline():cell.collision();}
        var overhang=PlatformOverhangs.shape(level,pos,outline);if(!overhang.isEmpty())extra=extra==null?overhang:Shapes.or(extra,overhang);
        return extra==null||extra.isEmpty()?original:Shapes.or(original,extra);
    }
}
