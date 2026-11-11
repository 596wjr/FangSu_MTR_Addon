package com.fangsu.blocks;

import com.fangsu.blockEntities.BlockEntityCurvedPlatform;
import com.fangsu.platform.PlatformMutations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;

/** 曲面由块实体网格绘制，碰撞形状沿用 Potato 的精细体积，不能退化为整格立方体。 */
public final class BlockCurvedPlatform extends Block implements EntityBlock,IBlockPlatform {
    public BlockCurvedPlatform(){super(BlockBehaviour.Properties.of(
        //#if MC_VERSION >= 12000
        )
        //#else
        //$$ net.minecraft.world.level.material.Material.STONE)
        //#endif
        .strength(2).noOcclusion().dynamicShape().isSuffocating((s,w,p)->false).isViewBlocking((s,w,p)->false));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new BlockEntityCurvedPlatform(pos,state);}
    @Override public boolean canBeReplaced(BlockState state,net.minecraft.world.item.context.BlockPlaceContext context){
        return context.getItemInHand().getItem() instanceof net.minecraft.world.item.BlockItem&&context.getLevel().getBlockEntity(context.getClickedPos()) instanceof BlockEntityCurvedPlatform cell&&cell.floorSkin()&&(!(context.getLevel() instanceof net.minecraft.server.level.ServerLevel server)||!com.fangsu.platform.CurvedPlatformPackets.busy(server,cell.group));
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(state.getBlock()!=next.getBlock()&&level instanceof net.minecraft.server.level.ServerLevel server&&!PlatformMutations.internal()&&level.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell&&cell.geometry!=null){
            if(!next.isAir()&&cell.coverable())com.fangsu.platform.PlatformOverlays.get(server).cover(pos,next,cell.serverData());
            else if(next.isAir())PlatformMutations.restoreOriginalSoon(server,pos,cell.originalBlock(),cell.originalEntity());
        }
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public VoxelShape getBlockSupportShape(BlockState state,BlockGetter world,BlockPos pos){
        if(world.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell&&cell.geometry!=null){
            if(cell.floorSkin())return Shapes.box(0,0,0,1,2.44140625E-4,1);
            if(cell.geometry.faces().stream().anyMatch(face->face.direction()==1&&face.vertices().stream().allMatch(v->Math.abs(v.y()-1)<1e-9)))return Shapes.box(0,1-2.44140625E-4,0,1,1,1);
            if(cell.geometry.solidVolume()>.99999999)return Shapes.block();
        }
        return Shapes.empty();
    }
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.ENTITYBLOCK_ANIMATED;}
    @Override public VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return world.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell?cell.outline():Shapes.empty();}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return world.getBlockEntity(pos) instanceof BlockEntityCurvedPlatform cell?cell.collision():Shapes.empty();}
}
