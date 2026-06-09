package com.fangsu.blocks;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.items.ModItems;
import mtr.mappings.BlockEntityMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseObjBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public BaseObjBlock(Properties properties) {
        super(properties);
    }

    public BaseObjBlock() {
        super(BlockBehaviour.Properties.of(
                //#if MC_VERSION >= 12000
                )
                //#else
                //$$ net.minecraft.world.level.material.Material.METAL)
                //#endif
                .strength(2)
                .noOcclusion()
                .dynamicShape()
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Nullable
    @Override
    public abstract BaseObjBlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state);

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ModItems.ITEM_WRENCH.get())) {
            BaseObjBlockEntity blockEntity = (BaseObjBlockEntity) level.getBlockEntity(pos);
            if (blockEntity != null) {
                return blockEntity.useWithWrench(state, level, pos, player, hand, hit);
            }
        } else if (stack.is(mtr.Items.BRUSH.get())) {
            BaseObjBlockEntity blockEntity = (BaseObjBlockEntity) level.getBlockEntity(pos);
            if (blockEntity != null) {
                return blockEntity.whenUseWithBrush(level, pos, player, hand, hit);
            }
        } else {
            BaseObjBlockEntity blockEntity = (BaseObjBlockEntity) level.getBlockEntity(pos);
            if (blockEntity != null) {
                return blockEntity.whenUseWithOther(level, pos, player, hand, hit);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState blockState) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public void entityInside(
            BlockState state,
            Level level,
            BlockPos pos,
            Entity entity
    ) {
        if (level.isClientSide()) return;
        if (entity instanceof Player) {
            Player player = (Player) entity;
            BaseObjBlockEntity blockEntity = (BaseObjBlockEntity) level.getBlockEntity(pos);
            if (blockEntity != null) blockEntity.whenEntityInside(player);
        }
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof BaseObjBlockEntity baseBE) {
                baseBE.serverTick();
            }
        };
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter world,
            BlockPos pos,
            CollisionContext context
    ) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof BaseObjBlockEntity obj) {
            VoxelShape shape = obj.getCollisionShapeInternal(state);
            return shape == null ? Shapes.block() : shape;
        }
        return Shapes.empty();
    }

    @Override
    public @NotNull VoxelShape getShape(
            BlockState state,
            BlockGetter world,
            BlockPos pos,
            CollisionContext context
    ) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof BaseObjBlockEntity obj) {
            VoxelShape shape = obj.getShapeInternal(state);
            return shape == null ? Shapes.block() : shape;
        }
        return Shapes.block();
    }

    public BlockState rotate(BlockState blockState, Rotation rotation) {
        return (BlockState) blockState.setValue(FACING, rotation.rotate((Direction) blockState.getValue(FACING)));
    }

    public BlockState mirror(BlockState blockState, Mirror mirror) {
        return blockState.rotate(mirror.getRotation((Direction) blockState.getValue(FACING)));
    }

    <T extends BlockEntityMapper> void tick(Level world, BlockPos pos, T blockEntity) {
    }

    BlockEntityType<? extends BlockEntity> getType() {
        return null;
    }
}
