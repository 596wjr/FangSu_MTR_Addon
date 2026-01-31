package com.fangsu.blocks;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import mtr.mappings.EntityBlockMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
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

public abstract class BaseObjBlock extends HorizontalDirectionalBlock implements EntityBlockMapper {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public BaseObjBlock(Properties properties) {
        super(properties);
    }

    public BaseObjBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(2)
                .noOcclusion());
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
        if (player.getMainHandItem().is(mtr.Items.BRUSH.get())) {
            return useWithBrush(level, player, hand, hit);
        } else {
            BaseObjBlockEntity blockEntity = (BaseObjBlockEntity) level.getBlockEntity(pos);
            if (blockEntity != null) {
                return blockEntity.whenUseWithinBrush(level, pos, player, hand, hit);
            } else return InteractionResult.PASS;
        }
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

    protected InteractionResult useWithBrush(Level level, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        else {
            player.sendSystemMessage(Component.translatable("还没做"));
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public VoxelShape getCollisionShape(
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
    public VoxelShape getShape(
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
}
