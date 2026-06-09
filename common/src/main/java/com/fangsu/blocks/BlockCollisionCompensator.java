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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import mtr.Items;
import org.jetbrains.annotations.NotNull;

import static com.fangsu.blocks.ModBlocks.ITEM_COLLISION_COMPENSATOR;

public class BlockCollisionCompensator extends Block {

    private static final int SEARCH_RADIUS = 2;

    public BlockCollisionCompensator(Properties properties) {
        super(properties);
    }

    public BlockCollisionCompensator() {
        this(BlockBehaviour.Properties.of(
                //#if MC_VERSION >= 12000
                )
                //#else
                //$$ net.minecraft.world.level.material.Material.METAL)
                //#endif
                .strength(2).noOcclusion());
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
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    BlockPos targetPos = pos.offset(dx, dy, dz);
                    BlockState targetState = level.getBlockState(targetPos);
                    Block targetBlock = targetState.getBlock();

                    if (targetBlock == this || !isObjBasedBlock(targetBlock)) continue;

                    VoxelShape shape = targetState.getInteractionShape(level, targetPos);
                    if (shape.isEmpty()) {
                        shape = targetState.getCollisionShape(level, targetPos);
                    }
                    if (shape.isEmpty()) continue;

                    VoxelShape worldShape = toWorldShape(shape, targetPos);
                    VoxelShape clipped = clipToBlock(worldShape, pos);

                    if (clipped.isEmpty() || !containsPoint(clipped, hitLocation)) continue;

                    double distance = distanceToShapeSquared(clipped, hitLocation);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = targetPos;
                        bestState = targetState;
                        bestWorldShape = worldShape;
                    }
                }
            }
        }

        if (bestPos == null) {
            return InteractionResult.PASS;
        }

        Direction redirectedDirection = resolveHitDirection(bestWorldShape, hitLocation, hit.getDirection());
        Vec3 redirectedLocation = redirectHitLocation(hitLocation, pos, bestPos);
        BlockHitResult redirectedHit = new BlockHitResult(
                redirectedLocation, redirectedDirection, bestPos, hit.isInside()
        );

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
        if (!collision && context instanceof EntityCollisionContext ecc
                && (ecc.isHoldingItem(Items.BRUSH.get()) || ecc.isHoldingItem(ITEM_COLLISION_COMPENSATOR.get()))) {
            return Shapes.block();
        }

        VoxelShape merged = Shapes.empty();

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    BlockPos targetPos = pos.offset(dx, dy, dz);
                    BlockState targetState = world.getBlockState(targetPos);
                    Block targetBlock = targetState.getBlock();

                    if (targetBlock == this || !isObjBasedBlock(targetBlock)) continue;

                    VoxelShape shape = collision
                            ? targetState.getCollisionShape(world, targetPos, context)
                            : targetState.getShape(world, targetPos, context);

                    if (shape.isEmpty()) continue;

                    VoxelShape local = clipToBlock(
                            toWorldShape(shape, targetPos),
                            pos
                    ).move(-pos.getX(), -pos.getY(), -pos.getZ());

                    if (!local.isEmpty()) {
                        merged = Shapes.or(merged, local);
                    }
                }
            }
        }

        return merged;
    }

    /**
     * 核心：严格裁剪到当前方块的 world-space 立方体
     */
    private VoxelShape clipToBlock(VoxelShape worldShape, BlockPos blockPos) {
        if (worldShape.isEmpty()) return Shapes.empty();
        VoxelShape blockWorld = Shapes.create(new AABB(blockPos));
        return Shapes.join(worldShape, blockWorld, BooleanOp.AND);
    }

    private VoxelShape toWorldShape(VoxelShape shape, BlockPos pos) {
        return shape.move(pos.getX(), pos.getY(), pos.getZ());
    }

    private Vec3 redirectHitLocation(Vec3 hitLocation, BlockPos sourcePos, BlockPos targetPos) {
        return hitLocation.add(
                targetPos.getX() - sourcePos.getX(),
                targetPos.getY() - sourcePos.getY(),
                targetPos.getZ() - sourcePos.getZ()
        );
    }

    private boolean isObjBasedBlock(Block block) {
        return block instanceof BaseObjBlock;
    }

    private boolean containsPoint(VoxelShape shape, Vec3 point) {
        for (AABB box : shape.toAabbs()) {
            if (box.contains(point)) return true;
        }
        return false;
    }

    private Direction resolveHitDirection(VoxelShape worldShape, Vec3 hit, Direction fallback) {
        Direction best = null;
        double bestDist = Double.MAX_VALUE;

        for (AABB box : worldShape.toAabbs()) {
            best = updateFace(hit.x, box.minX, Direction.WEST, best, bestDist);
            bestDist = updateDist(hit.x, box.minX, bestDist);

            best = updateFace(hit.x, box.maxX, Direction.EAST, best, bestDist);
            bestDist = updateDist(hit.x, box.maxX, bestDist);

            best = updateFace(hit.y, box.minY, Direction.DOWN, best, bestDist);
            bestDist = updateDist(hit.y, box.minY, bestDist);

            best = updateFace(hit.y, box.maxY, Direction.UP, best, bestDist);
            bestDist = updateDist(hit.y, box.maxY, bestDist);

            best = updateFace(hit.z, box.minZ, Direction.NORTH, best, bestDist);
            bestDist = updateDist(hit.z, box.minZ, bestDist);

            best = updateFace(hit.z, box.maxZ, Direction.SOUTH, best, bestDist);
            bestDist = updateDist(hit.z, box.maxZ, bestDist);
        }

        return best == null ? fallback : best;
    }

    private Direction updateFace(double value, double face, Direction dir, Direction current, double bestDist) {
        double d = Math.abs(value - face);
        return d < bestDist ? dir : current;
    }

    private double updateDist(double value, double face, double bestDist) {
        return Math.min(bestDist, Math.abs(value - face));
    }

    private double distanceToShapeSquared(VoxelShape shape, Vec3 point) {
        double best = Double.MAX_VALUE;
        for (AABB box : shape.toAabbs()) {
            double dx = Math.max(0, Math.max(box.minX - point.x, point.x - box.maxX));
            double dy = Math.max(0, Math.max(box.minY - point.y, point.y - box.maxY));
            double dz = Math.max(0, Math.max(box.minZ - point.z, point.z - box.maxZ));
            best = Math.min(best, dx * dx + dy * dy + dz * dz);
        }
        return best;
    }
}
