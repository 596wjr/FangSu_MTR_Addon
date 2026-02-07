package com.fangsu.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.AABB;
import mtr.Items;
import org.jetbrains.annotations.NotNull;

public class BlockCollisionCompensator extends Block {
    private static final int SEARCH_RADIUS = 2;
    private static final double EPSILON = 1.0E-7;

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
        VoxelShape bestWorldShape = null;
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
                    VoxelShape targetWorldShape = toWorldShape(interactionShape, targetPos);
                    VoxelShape clippedWorld = getClippedWorldShape(targetWorldShape, pos);
                    if (clippedWorld.isEmpty() && !targetState.getCollisionShape(level, targetPos).isEmpty()) {
                        VoxelShape collisionShape = targetState.getCollisionShape(level, targetPos);
                        targetWorldShape = toWorldShape(collisionShape, targetPos);
                        clippedWorld = getClippedWorldShape(targetWorldShape, pos);
                    }
                    if (clippedWorld.isEmpty() || !containsPoint(clippedWorld, hitLocation)) {
                        continue;
                    }
                    double distance = distanceToShapeSquared(clippedWorld, hitLocation);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = targetPos;
                        bestState = targetState;
                        bestWorldShape = targetWorldShape;
                    }
                }
            }
        }
        if (bestPos == null || bestState == null || bestWorldShape == null) {
            return InteractionResult.PASS;
        }
        Direction redirectedDirection = resolveHitDirection(bestWorldShape, hitLocation, hit.getDirection());
        BlockHitResult redirectedHit = new BlockHitResult(hitLocation, redirectedDirection, bestPos, hit.isInside());
        return bestState.getBlock().use(bestState, level, bestPos, player, hand, redirectedHit);
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(
            @NotNull BlockState state,
            @NotNull BlockGetter world,
            @NotNull BlockPos pos,
            @NotNull CollisionContext context
    ) {
        return getCompensationShape(world, pos, context, true);
    }

    @Override
    public @NotNull VoxelShape getShape(
            @NotNull BlockState state,
            @NotNull BlockGetter world,
            @NotNull BlockPos pos,
            @NotNull CollisionContext context
    ) {
        return getCompensationShape(world, pos, context, false);
    }

    private VoxelShape getCompensationShape(BlockGetter world, BlockPos pos, CollisionContext context, boolean collision) {
        if (!collision && context instanceof EntityCollisionContext entityContext
                && entityContext.isHoldingItem(Items.BRUSH.get())) {
            return Shapes.block();
        }
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
                            ? targetState.getCollisionShape(world, targetPos, context)
                            : targetState.getShape(world, targetPos, context);
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
        VoxelShape targetWorldShape = toWorldShape(targetShape, targetPos);
        VoxelShape clippedWorld = getClippedWorldShape(targetWorldShape, pos);
        if (clippedWorld.isEmpty()) {
            return Shapes.empty();
        }
        return clippedWorld.move(-pos.getX(), -pos.getY(), -pos.getZ());
    }

    private VoxelShape getClippedWorldShape(VoxelShape targetWorldShape, BlockPos pos) {
        if (targetWorldShape.isEmpty()) {
            return Shapes.empty();
        }
        AABB blockBox = new AABB(pos);
        VoxelShape clipped = Shapes.empty();
        for (AABB box : targetWorldShape.toAabbs()) {
            AABB intersection = box.intersect(blockBox);
            if ((intersection.maxX - intersection.minX) > EPSILON
                    && (intersection.maxY - intersection.minY) > EPSILON
                    && (intersection.maxZ - intersection.minZ) > EPSILON) {
                clipped = Shapes.or(clipped, Shapes.create(intersection));
            }
        }
        return clipped;
    }

    private VoxelShape toWorldShape(VoxelShape shape, BlockPos targetPos) {
        if (shape.isEmpty()) {
            return Shapes.empty();
        }
        return shape.move(targetPos.getX(), targetPos.getY(), targetPos.getZ());
    }

    private boolean containsPoint(VoxelShape shape, Vec3 point) {
        for (AABB box : shape.toAabbs()) {
            if (box.inflate(EPSILON).contains(point)) {
                return true;
            }
        }
        return false;
    }

    private Direction resolveHitDirection(VoxelShape targetWorldShape, Vec3 hitLocation, Direction fallback) {
        Direction bestDirection = null;
        double bestDistance = Double.MAX_VALUE;
        for (AABB box : targetWorldShape.toAabbs()) {
            if (isWithin(hitLocation.y, box.minY, box.maxY) && isWithin(hitLocation.z, box.minZ, box.maxZ)) {
                BestFace best = updateBestDirection(hitLocation.x, box.minX, Direction.WEST, bestDirection, bestDistance);
                bestDirection = best.direction;
                bestDistance = best.distance;
                best = updateBestDirection(hitLocation.x, box.maxX, Direction.EAST, bestDirection, bestDistance);
                bestDirection = best.direction;
                bestDistance = best.distance;
            }
            if (isWithin(hitLocation.x, box.minX, box.maxX) && isWithin(hitLocation.z, box.minZ, box.maxZ)) {
                BestFace best = updateBestDirection(hitLocation.y, box.minY, Direction.DOWN, bestDirection, bestDistance);
                bestDirection = best.direction;
                bestDistance = best.distance;
                best = updateBestDirection(hitLocation.y, box.maxY, Direction.UP, bestDirection, bestDistance);
                bestDirection = best.direction;
                bestDistance = best.distance;
            }
            if (isWithin(hitLocation.x, box.minX, box.maxX) && isWithin(hitLocation.y, box.minY, box.maxY)) {
                BestFace best = updateBestDirection(hitLocation.z, box.minZ, Direction.NORTH, bestDirection, bestDistance);
                bestDirection = best.direction;
                bestDistance = best.distance;
                best = updateBestDirection(hitLocation.z, box.maxZ, Direction.SOUTH, bestDirection, bestDistance);
                bestDirection = best.direction;
                bestDistance = best.distance;
            }
        }
        return bestDirection == null ? fallback : bestDirection;
    }

    private boolean isWithin(double value, double min, double max) {
        return value >= min - EPSILON && value <= max + EPSILON;
    }

    private BestFace updateBestDirection(double value, double face, Direction direction, Direction bestDirection, double bestDistance) {
        double distance = Math.abs(value - face);
        if (distance < bestDistance) {
            return new BestFace(direction, distance);
        }
        return new BestFace(bestDirection, bestDistance);
    }

    private static class BestFace {
        private final Direction direction;
        private final double distance;

        private BestFace(Direction direction, double distance) {
            this.direction = direction;
            this.distance = distance;
        }
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
