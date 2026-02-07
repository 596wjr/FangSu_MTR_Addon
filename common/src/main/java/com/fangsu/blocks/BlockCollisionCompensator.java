package com.fangsu.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockCollisionCompensator extends Block {
    public static final DirectionProperty TARGET_DIRECTION = DirectionProperty.create("target_direction", Direction.values());

    public BlockCollisionCompensator(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TARGET_DIRECTION, Direction.DOWN));
    }

    public BlockCollisionCompensator() {
        this(BlockBehaviour.Properties.of().strength(2).noOcclusion());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TARGET_DIRECTION);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction targetDirection = context.getClickedFace().getOpposite();
        return defaultBlockState().setValue(TARGET_DIRECTION, targetDirection);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public @NotNull InteractionResult use(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull Player player,
            @NotNull InteractionHand hand,
            @NotNull BlockHitResult hit
    ) {
        Vec3 hitLocation = hit.getLocation();
        BlockPos bestPos = null;
        BlockState bestState = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = pos.relative(direction);
            BlockState targetState = level.getBlockState(targetPos);
            if (targetState.getBlock() == this) {
                continue;
            }
            VoxelShape targetShape = targetState.getShape(level, targetPos);
            VoxelShape clippedWorld = getClippedWorldShape(targetShape, pos, targetPos);
            if (clippedWorld.isEmpty() && !targetState.getCollisionShape(level, targetPos).isEmpty()) {
                clippedWorld = getClippedWorldShape(targetState.getCollisionShape(level, targetPos), pos, targetPos);
            }
            if (clippedWorld.isEmpty() || !containsPoint(clippedWorld, hitLocation)) {
                continue;
            }
            Vec3 targetCenter = new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
            double distance = targetCenter.distanceToSqr(hitLocation);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPos = targetPos;
                bestState = targetState;
            }
        }
        if (bestPos == null || bestState == null) {
            return InteractionResult.PASS;
        }
        BlockHitResult redirectedHit = new BlockHitResult(hitLocation, hit.getDirection(), bestPos, hit.isInside());
        return bestState.getBlock().use(bestState, level, bestPos, player, hand, redirectedHit);
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(
            @NotNull BlockState state,
            @NotNull BlockGetter world,
            @NotNull BlockPos pos,
            @NotNull CollisionContext context
    ) {
        return getCompensationShape(world, pos, true);
    }

    @Override
    public @NotNull VoxelShape getShape(
            @NotNull BlockState state,
            @NotNull BlockGetter world,
            @NotNull BlockPos pos,
            @NotNull CollisionContext context
    ) {
        return getCompensationShape(world, pos, false);
    }

    private VoxelShape getCompensationShape(BlockGetter world, BlockPos pos, boolean collision) {
        VoxelShape merged = Shapes.empty();
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = pos.relative(direction);
            BlockState targetState = world.getBlockState(targetPos);
            if (targetState.getBlock() == this) {
                continue;
            }
            VoxelShape targetShape = collision
                    ? targetState.getCollisionShape(world, targetPos)
                    : targetState.getShape(world, targetPos);
            if (targetShape.isEmpty()) {
                continue;
            }
            VoxelShape clippedLocal = getClippedLocalShape(targetShape, pos, targetPos);
            if (!clippedLocal.isEmpty()) {
                merged = Shapes.or(merged, clippedLocal);
            }
        }
        return merged;
    }

    private VoxelShape getClippedLocalShape(VoxelShape targetShape, BlockPos pos, BlockPos targetPos) {
        VoxelShape clippedWorld = getClippedWorldShape(targetShape, pos, targetPos);
        if (clippedWorld.isEmpty()) {
            return Shapes.empty();
        }
        return clippedWorld.move(-pos.getX(), -pos.getY(), -pos.getZ());
    }

    private VoxelShape getClippedWorldShape(VoxelShape targetShape, BlockPos pos, BlockPos targetPos) {
        if (targetShape.isEmpty()) {
            return Shapes.empty();
        }
        VoxelShape targetWorldShape = targetShape.move(targetPos.getX(), targetPos.getY(), targetPos.getZ());
        VoxelShape blockWorldShape = Shapes.block().move(pos.getX(), pos.getY(), pos.getZ());
        return Shapes.joinUnoptimized(targetWorldShape, blockWorldShape, BooleanOp.AND);
    }

    private boolean containsPoint(VoxelShape shape, Vec3 point) {
        for (AABB box : shape.toAabbs()) {
            if (box.contains(point)) {
                return true;
            }
        }
        return false;
    }
}
