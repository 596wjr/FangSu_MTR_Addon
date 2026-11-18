package com.fangsu.blocks;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.client.ClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockTicketMachine extends Block implements net.minecraft.world.level.block.EntityBlock {
    public final int ticketMachineKind;
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty CUSTOM_MODEL = net.minecraft.world.level.block.state.properties.BooleanProperty.create("custom_model");
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape SHAPE_NORTH = Block.box(0, 0, 8, 16, 16, 16);
    private static final VoxelShape SHAPE_EAST  = Block.box(0, 0, 0, 8, 16, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(0, 0, 0, 16, 16, 8);
    private static final VoxelShape SHAPE_WEST  = Block.box(8, 0, 0, 16, 16, 16);

    public BlockTicketMachine() { this(0); }
    public BlockTicketMachine(int kind) {
        super(BlockBehaviour.Properties.of(
                //#if MC_VERSION >= 12000
                )
                //#else
                //$$ net.minecraft.world.level.material.Material.METAL)
                //#endif
                .strength(2).noOcclusion());
        ticketMachineKind = kind;
        registerDefaultState(this.defaultBlockState().setValue(CUSTOM_MODEL, true).setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, CUSTOM_MODEL);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos blockPos = context.getClickedPos();
        Level level = context.getLevel();
        if (blockPos.getY() >= level.getMaxBuildHeight() - 1) return null;
        if (!level.getBlockState(blockPos.above()).canBeReplaced(context)) return null;
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public @NotNull BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() != Direction.Axis.Y)
            return super.updateShape(state, direction, neighborState, level, pos, neighborPos);

        if (half == DoubleBlockHalf.LOWER && direction == Direction.UP) {
            if (!neighborState.is(this) || neighborState.getValue(HALF) != DoubleBlockHalf.UPPER) {
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
        }
        if (half == DoubleBlockHalf.UPPER && direction == Direction.DOWN) {
            if (!neighborState.is(this) || neighborState.getValue(HALF) != DoubleBlockHalf.LOWER) {
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        DoubleBlockHalf half = state.getValue(HALF);
        BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        BlockState otherState = level.getBlockState(otherPos);
        if (otherState.is(this) && otherState.getValue(HALF) != half) {
            level.setBlock(otherPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 35);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockPos base = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (state.getValue(CUSTOM_MODEL) && level.getBlockEntity(base) instanceof com.fangsu.blockEntities.BlockEntityTicketMachine machine)
            return com.fangsu.ticketSystem.v2.TicketShapes.rotate(machine.ticketShape(state.getValue(HALF) == DoubleBlockHalf.UPPER), state.getValue(FACING));
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case EAST  -> SHAPE_EAST;
            case SOUTH -> SHAPE_SOUTH;
            case WEST  -> SHAPE_WEST;
            default    -> SHAPE_NORTH;
        };
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new com.fangsu.blockEntities.BlockEntityTicketMachine(pos,state) : null;
    }
    @Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return state.getValue(CUSTOM_MODEL) ? net.minecraft.world.level.block.RenderShape.INVISIBLE : net.minecraft.world.level.block.RenderShape.MODEL;
    }
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            BlockPos base = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            try { com.fangsu.ticketSystem.v2.TicketPackets.open(serverPlayer,base); }
            catch(RuntimeException ex) { player.displayClientMessage(ComponentHelper.literal("票务设备打开失败："+ex.getMessage()),true); }
        }
        return InteractionResult.SUCCESS;
    }
}
