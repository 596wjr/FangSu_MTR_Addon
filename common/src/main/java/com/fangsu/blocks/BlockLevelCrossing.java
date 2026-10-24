package com.fangsu.blocks;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.blockEntities.BlockEntityLevelCrossing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 平交路口方块：把一段 MTR 轨道与铺面合成一体。
 * <p>
 * 本类只做分发：几何与采样在 {@link BlockEntityLevelCrossing} 里，
 * 相邻方块变化时通知邻居重建，便于一整片铺面保持一致。
 * <p>
 * <b>朝向恒为常量</b>：平交路口的几何完全由附近轨道决定，朝向对它没有任何意义，
 * 却会让渲染器多转一次（{@code BaseBlockEntityRender} 按 FACING 绕 Y 旋转）。因此
 * {@link #getStateForPlacement} 固定返回 NORTH，玩家怎么放都一样。
 * <p>
 * <b>为什么不直接删掉 FACING 属性</b>：共享基类 {@link BaseObjBlock} 及其渲染/同步链路
 * （{@code BaseBlockEntityRender} 的 candyPose、{@code FunctionalObjBlockEntity.worldToLocal}、
 * 配置屏与扳手交互）都假设这个属性存在，删掉会波及全部 ObjBlock；把值钉死是零风险做法。
 */
public class BlockLevelCrossing extends BaseObjBlock {

    /** 潜行右键重算的半径，对应 5×5×1 的范围。 */
    private static final int RECOMPUTE_RADIUS = 2;

    /**
     * 放置时朝向固定为 NORTH —— 朝向对本方块没有语义，钉死常量可以避免"放一次转一次"的视觉回归。
     * <p>
     * 不能省掉这一步只靠渲染端求逆：FACING 还会影响基类的其它路径（如配置屏的模型预览），
     * 让它恒为常量最省心。
     */
    @Override
    public @NotNull BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return this.defaultBlockState().setValue(BaseObjBlock.FACING, Direction.NORTH);
    }

    @Override
    public @Nullable BaseObjBlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BlockEntityLevelCrossing(pos, state);
    }

    /**
     * 放置即自动采样。
     * <p>
     * 采样只在客户端做（轨道数据在客户端），{@link BlockEntityLevelCrossing#recompute()} 内部还会再判一次
     * {@code isClientSide}，服务端调用到这里会直接返回。放下瞬间方块实体可能还没创建好，
     * 那种情况由方块实体自己在第一帧渲染时补算（{@code autoComputeTried} 兜底）。
     */
    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide && level.getBlockEntity(pos) instanceof BlockEntityLevelCrossing crossing) {
            crossing.recompute();
        }
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter world,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof BlockEntityLevelCrossing crossing) {
            return crossing.getCachedShape();
        }
        // 区块/方块实体还没就绪时也要有形状，否则客户端会出现"透视"式穿模
        return Shapes.block();
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        for (Direction direction : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(direction));
            if (neighbor instanceof BlockEntityLevelCrossing crossing) {
                crossing.invalidateMesh();
            }
        }
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                          @NotNull Player player, @NotNull InteractionHand hand,
                                          @NotNull BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            // 采样是纯客户端行为，服务端只回 SUCCESS 让动作生效
            if (level.isClientSide) {
                // <b>只能入队，不能同步 recompute</b>：5×5=25 块各做一次全量轨道采样 + 碰撞 + 构网
                // 会把主线程压死（用户实测「shift 右键严重卡顿」）。
                // 队列每 tick 处理 2 块，与构建共享 3ms 时间预算。
                for (int dx = -RECOMPUTE_RADIUS; dx <= RECOMPUTE_RADIUS; dx++) {
                    for (int dz = -RECOMPUTE_RADIUS; dz <= RECOMPUTE_RADIUS; dz++) {
                        BlockEntity blockEntity = level.getBlockEntity(pos.offset(dx, 0, dz));
                        if (blockEntity instanceof BlockEntityLevelCrossing crossing) {
                            BlockEntityLevelCrossing.requestRecompute(crossing);
                        }
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // 其余交互（扳手 → 配置屏、刷子等）保持基类行为
        return super.use(state, level, pos, player, hand, hit);
    }
}
