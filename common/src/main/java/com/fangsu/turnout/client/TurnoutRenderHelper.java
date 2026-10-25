package com.fangsu.turnout.client;

import com.fangsu.blockEntities.BlockEntityTurnout;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.turnout.TurnoutBinding;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 道岔渲染侧的纯辅助：pose 矩阵与切换音效。
 * <p>
 * <b>pose 的推导</b>（照 {@code BlockEntityLevelCrossing.levelCrossingPose()} 的乘法约定，
 * 已读码确认不是凭直觉）：基类 {@code BaseBlockEntityRender} 对<b>非 Functional</b> 的方块实体只做
 * <pre>base = T(0.5, 0, 0.5)</pre>
 * （{@code BaseBlockEntityRender.java:88}；FACING 旋转只在 {@code FunctionalObjBlockEntity} 分支里加，
 * 本方块实体不是它）。{@link Matrices} 的 translate/rotateY 都是<b>右乘</b>，所以源码顺序 = 复合顺序。
 * 我们的几何是「以 anchor 为原点、直股沿本地 +X」生成的，因此抵消 + 摆放要写成
 * <pre>M = T(-0.5, 0, -0.5) · T(anchor - blockPos) · Ry(-baseYaw)</pre>
 * 展开得 {@code base · M = T(anchor) · Ry(-baseYaw)}，即几何正好落在 anchor 上、朝向与直股一致。
 * <p>
 * {@code Ry(-yaw)} 的符号：本地 +X 必须映到世界方向 {@code (cos yaw, 0, sin yaw)}，
 * 而 {@code rotateY(θ)} 把 (1,0,0) 映到 {@code (cos θ, 0, -sin θ)}，故 θ = -yaw。
 */
public final class TurnoutRenderHelper {

    /** MTR 自带扳道音效的候选 id（探测到哪一个就用哪一个，都没有就静音）。 */
    private static final ResourceLocation SWITCH_SOUND_1 = new ResourceLocation("mtr", "point_move");
    private static final ResourceLocation SWITCH_SOUND_2 = new ResourceLocation("mtr", "railway_point");

    private TurnoutRenderHelper() {
    }

    // ==================== pose ====================

    /** 道岔几何的整体 pose（本地几何 → 世界）。 */
    public static Matrices turnoutPose(BlockEntityTurnout entity, TurnoutBinding binding) {
        Matrices pose = new Matrices();
        BlockPos pos = entity.getBlockPos();
        Vec3 anchor = binding.anchor();
        // 抵消基类的 T(0.5,0,0.5)，再挪到 anchor（方块内部偏移用相对坐标，避免 float 精度损失）
        pose.translate((float) (anchor.x - pos.getX() - 0.5D),
                (float) (anchor.y - pos.getY()),
                (float) (anchor.z - pos.getZ() - 0.5D));
        pose.rotateY((float) -Math.toRadians(binding.baseYawDeg()));
        return pose;
    }

    /** 当前帧的部分 tick（尖轨缓动用；{@code Minecraft.getFrameTime()} 在 1.18~1.20.1 都存在）。 */
    public static float partialTick() {
        try {
            return net.minecraft.client.Minecraft.getInstance().getFrameTime();
        } catch (Throwable t) {
            return 0.0F;
        }
    }

    // ==================== 音效 ====================

    /**
     * MTR 自带的扳道音效 id（存在才返回，否则 null = 静音）。
     * <p>
     * 需求明确「不新增音频文件」：这里只探测 MTR 注册表里已有的候选 id，
     * 探测不到就不播——绝不 {@code playSound} 一个未注册的事件（那会每帧刷 warn）。
     */
    @Nullable
    public static ResourceLocation switchSoundOrNull() {
        if (soundExists(SWITCH_SOUND_1)) return SWITCH_SOUND_1;
        if (soundExists(SWITCH_SOUND_2)) return SWITCH_SOUND_2;
        return null;
    }

    private static boolean soundExists(ResourceLocation id) {
        try {
            //#if MC_VERSION >= 11903
            return net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(id);
            //#else
            //$$ return net.minecraft.core.Registry.SOUND_EVENT.containsKey(id);
            //#endif
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== 道具提示 ====================

    /**
     * 玩家是否手持扳手或 MTR 刷子（决定要不要显示定位标志）。
     * <p>
     * <b>判定唯一来源</b>是 {@link com.fangsu.blocks.BlockTurnout#isHoldingTool}——
     * 方块选中框（{@code BlockTurnout.getShape}）用的是同一条规则，
     * 因此不会出现"有框无图标 / 有图标无框"。
     */
    public static boolean holdingTool() {
        try {
            return com.fangsu.blocks.BlockTurnout.isHoldingTool(
                    net.minecraft.client.Minecraft.getInstance().player);
        } catch (Throwable t) {
            return false;
        }
    }

}
