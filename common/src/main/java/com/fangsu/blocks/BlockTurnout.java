package com.fangsu.blocks;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blockEntities.BlockEntityTurnout;
import com.fangsu.items.ItemTurnout;
import com.fangsu.items.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** 无碰撞的道岔控制器。仅在持工具时可选中，普通放置由服务端校验事务授权。 */
public class BlockTurnout extends BaseObjBlock {
    public BlockTurnout(){super();}
    @Override public @Nullable BlockState getStateForPlacement(BlockPlaceContext context){return ItemTurnout.placementBinding()==null?null:defaultBlockState().setValue(FACING,Direction.NORTH);}
    @Override public @Nullable BaseObjBlockEntity newBlockEntity(BlockPos pos,BlockState state){return new BlockEntityTurnout(pos,state);}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.empty();}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return context instanceof EntityCollisionContext entity&&entity.getEntity() instanceof Player player&&isHoldingTool(player)?Shapes.block():Shapes.empty();}
    @Override public VoxelShape getVisualShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.empty();}
    @Override public VoxelShape getOcclusionShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    @Override public VoxelShape getInteractionShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.empty();}
    // BER 不依赖方块的模型形态；INVISIBLE 同时关闭无意义的破坏粒子，避免粒子对空形状取 bounds。
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override public BlockState rotate(BlockState state,Rotation rotation){return state;}
    @Override public BlockState mirror(BlockState state,Mirror mirror){return state;}
    public static boolean isHoldingTool(@Nullable Player player){return player!=null&&(isTool(player.getMainHandItem())||isTool(player.getOffhandItem()));}
    public static boolean isTool(@Nullable ItemStack stack){return stack!=null&&!stack.isEmpty()&&(stack.is(ModItems.ITEM_WRENCH.get())||stack.is(mtr.Items.BRUSH.get()));}
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block from,BlockPos source,boolean moving){if(!level.isClientSide&&level.getBlockEntity(pos) instanceof BlockEntityTurnout turnout)turnout.refreshPowered();}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,@Nullable LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(!level.isClientSide&&ItemTurnout.placementBinding()!=null&&level.getBlockEntity(pos) instanceof BlockEntityTurnout turnout){turnout.setBinding(ItemTurnout.placementBinding());turnout.refreshPowered();}}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:(world,pos,block,entity)->{if(entity instanceof BlockEntityTurnout turnout)turnout.serverTick();};}
}
