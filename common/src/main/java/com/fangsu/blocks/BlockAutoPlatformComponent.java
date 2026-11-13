package com.fangsu.blocks;

import com.fangsu.blockEntities.BlockEntityAutoPlatformComponent;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** 一个宿主可承载多个任意切线方向的门扇、固定玻璃和吊板。 */
public final class BlockAutoPlatformComponent extends HorizontalDirectionalBlock implements EntityBlock,IBlockPlatform {
    //#if MC_VERSION >= 12004
    //$$ private static final com.mojang.serialization.MapCodec<BlockAutoPlatformComponent> CODEC =
    //$$         simpleCodec(BlockAutoPlatformComponent::new);
    //$$ @Override protected com.mojang.serialization.MapCodec<BlockAutoPlatformComponent> codec(){return CODEC;}
    //#endif

    public BlockAutoPlatformComponent(){this(BlockBehaviour.Properties.of(
        //#if MC_VERSION >= 12000
        )
        //#else
        //$$ net.minecraft.world.level.material.Material.METAL)
        //#endif
        .strength(2).noOcclusion().dynamicShape());}
    private BlockAutoPlatformComponent(BlockBehaviour.Properties properties){
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new BlockEntityAutoPlatformComponent(pos,state);}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.ENTITYBLOCK_ANIMATED;}
    @Override public VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return world.getBlockEntity(pos) instanceof BlockEntityAutoPlatformComponent host?host.shape():Shapes.empty();}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return getShape(state,world,pos,context);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return (world,pos,current,entity)->{if(entity instanceof BlockEntityAutoPlatformComponent host)host.tick();};}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        var item=player.getItemInHand(hand);if(item.is(com.fangsu.items.ModItems.ITEM_SCREENDOOR_CONFIGURATOR.get()))return InteractionResult.PASS;
        return InteractionResult.PASS;
    }
}
