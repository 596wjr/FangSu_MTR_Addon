package com.fangsu.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

public class BlockCollisionCompensator extends Block {
    private static final int SEARCH_RADIUS = 2;

    public BlockCollisionCompensator(Properties properties) {
        super(properties);
    }

    public BlockCollisionCompensator() {
        this(BlockBehaviour.Properties.of().strength(2).noOcclusion());
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
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos targetPos = pos.offset(dx, dy, dz);
                    BlockState targetState = level.getBlockState(targetPos);
                    if (targetState.getBlock() == this) {
                        continue;
                    }
                    VoxelShape interactionShape = targetState.getInteractionShape(level, targetPos);
                    VoxelShape clippedWorld = getClippedWorldShape(interactionShape, pos, targetPos);
                    if (clippedWorld.isEmpty() && !targetState.getCollisionShape(level, targetPos).isEmpty()) {
                        clippedWorld = getClippedWorldShape(targetState.getCollisionShape(level, targetPos), pos, targetPos);
                    }
                    if (clippedWorld.isEmpty() || !containsPoint(clippedWorld, hitLocation)) {
                        continue;
                    }
                    double distance = distanceToShapeSquared(clippedWorld, hitLocation);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = targetPos;
                        bestState = targetState;
                    }
                }
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
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos targetPos = pos.offset(dx, dy, dz);
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

    private double distanceToShapeSquared(VoxelShape shape, Vec3 point) {
        double best = Double.MAX_VALUE;
        for (AABB box : shape.toAabbs()) {
            double dx = distanceToRange(point.x, box.minX, box.maxX);
            double dy = distanceToRange(point.y, box.minY, box.maxY);
            double dz = distanceToRange(point.z, box.minZ, box.maxZ);
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < best) {
                best = distance;
            }
        }
        return best;
    }

    private double distanceToRange(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0;
    }
}
