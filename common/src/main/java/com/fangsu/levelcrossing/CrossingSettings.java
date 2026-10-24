package com.fangsu.levelcrossing;

import com.fangsu.blockEntities.BlockEntityLevelCrossing;

/**
 * 平交路口的不可变配置快照。UI 每次改动都会写回 extraConfig，
 * 这里统一做一次读出 + 钳制，避免采样/建模代码各自再写一遍范围判断。
 */
public final class CrossingSettings {

    /** 铺面样式 id，见 {@link LevelCrossingStyles}。 */
    public final String styleId;
    /** 铺面外扩格数（0..2）。 */
    public final int outerExtend;
    /** 铺面沿轨向的分段数（1..32）。 */
    public final int stepCount;
    /**
     * 斜坡水平长度（格）：从格边向格外延伸多少走完落差。
     * 默认 1.0（用户口径「在 1 格内延伸到地面」），钳制区间 0.25..1；太小会变成陡坡（曾默认 0.15 ≈ 67°）。
     */
    public final double bevelWidth;
    /** 默认垂直下沉量（格）：轨面比沥青路面高 10mm（用户口径 0.01），槽再按 slotDepth 往路面以下凹。 */
    public static final float DEFAULT_DECK_INSET = 0.010F;
    /**
     * 铺面相对<b>轨顶</b>的垂直下沉量（格，>=0）。
     * <p>
     * 注意：这个值<b>只</b>用于垂直方向（{@code deckTopY = railTopY - deckInset}）。
     * 历史上它还被当成水平内缩用过，那会造成相邻格之间出现 2×deckInset 的缝，已彻底拆开：
     * 水平方向现在一律铺满 0..1（见 {@code DeckSurface.deckRect}）。
     */
    public final double deckInset;
    /** 轨顶高度覆盖值；<0 表示按轨道自动。 */
    public final double railTopOverride;
    /**
     * 槽深（{@code slotDepth}）：槽底相对<b>沥青路面</b>下凹的深度（格），负值表示按轨道自动。
     * <p>
     * 历史键名沿用 {@code grooveDepthOverride}（不破坏已有 NBT），但语义已从
     * 「相对轨顶」改成「相对路面」——否则会出现「槽底比路面还高」。
     */
    public final double grooveDepthOverride;
    /**
     * 斜坡（道口路肩）落点相对本方块底面的高度，单位格。
     * <p>
     * 默认 {@code 0} = 落到「本方块下方那个方块的顶面」，也就是方块底面所在的平面。
     * 下方是半砖/台阶时地面更矮，把这个值调成 {@code -0.5} 之类即可让坡脚真正踩在脚下那层的顶面。
     * 取值范围 [-1, 0.5]（再极端就没有视觉意义了）。
     */
    public final double rampFootY;
    /**
     * <b>钢轨顶面偏置</b>（格，默认 +0.002 = 2mm）：我们的钢轨顶面比采样到的原版轨顶高多少。
     * <p>
     * 用户要求「轨面做到和原版轨面完全对齐，但稍高一点」：原版轨顶由 {@code TrackSlice.railTopY} 给出，
     * 这里只把<b>整个钢轨断面</b>整体上移这么点 —— 刚好盖住原版轨顶（不闪、不悬浮）。
     * 范围 -0.02..0.05。
     */
    public final double railTopBias;
    /** 横向缩放倍率（乘在 {@code rail_gauge.json} 的 lateralScale 上，默认 1.0 = 不干预）。 */
    public final double lateralScaleMul;
    /** 横向偏移增量（加在 {@code rail_gauge.json} 的 lateralOffset 上，默认 0.0 = 不干预）。 */
    public final double lateralOffsetAdd;
    /**
     * 四条格边的邻居是否<b>也是平交路口</b>，顺序与 {@code DeckSurface.SIDE_*} 一致：
     * <pre>
     *   0 = -X 边（x 最小侧）
     *   1 = +X 边（x 最大侧）
     *   2 = -Z 边（z 最小侧）
     *   3 = +Z 边（z 最大侧）
     * </pre>
     * 邻居是同类时该边：铺面/地面顶面一路延伸到格界（不再内缩）、不出坡、不画裙边，
     * 这样一整片道口在视觉上才连成一块。
     */
    public final boolean[] neighbourCrossing;

    public CrossingSettings(String styleId, int outerExtend, int stepCount, double bevelWidth,
                            double deckInset, double railTopOverride, double grooveDepthOverride) {
        this(styleId, outerExtend, stepCount, bevelWidth, deckInset, railTopOverride, grooveDepthOverride,
                0, new boolean[4]);
    }

    public CrossingSettings(String styleId, int outerExtend, int stepCount, double bevelWidth,
                            double deckInset, double railTopOverride, double grooveDepthOverride,
                            boolean[] neighbourCrossing) {
        this(styleId, outerExtend, stepCount, bevelWidth, deckInset, railTopOverride, grooveDepthOverride,
                0, neighbourCrossing);
    }

    public CrossingSettings(String styleId, int outerExtend, int stepCount, double bevelWidth,
                            double deckInset, double railTopOverride, double grooveDepthOverride,
                            double rampFootY, boolean[] neighbourCrossing) {
        this(styleId, outerExtend, stepCount, bevelWidth, deckInset, railTopOverride, grooveDepthOverride,
                rampFootY, neighbourCrossing, 0.002, 1.0, 0.0);
    }

    /** 全参构造（含对齐参数：轨顶偏置 / 横向缩放倍率 / 横向偏移增量）。 */
    public CrossingSettings(String styleId, int outerExtend, int stepCount, double bevelWidth,
                            double deckInset, double railTopOverride, double grooveDepthOverride,
                            double rampFootY, boolean[] neighbourCrossing,
                            double railTopBias, double lateralScaleMul, double lateralOffsetAdd) {
        this.styleId = styleId;
        this.outerExtend = outerExtend;
        this.stepCount = stepCount;
        this.bevelWidth = bevelWidth;
        this.deckInset = deckInset;
        this.railTopOverride = railTopOverride;
        this.grooveDepthOverride = grooveDepthOverride;
        this.rampFootY = Math.max(-1, Math.min(0.5, rampFootY));
        this.railTopBias = clampDouble(railTopBias, -0.02, 0.05);
        this.lateralScaleMul = clampDouble(lateralScaleMul, 0.5, 2.0);
        this.lateralOffsetAdd = clampDouble(lateralOffsetAdd, -0.2, 0.2);
        // 防御性拷贝：调用方（方块实体）每次查询都新建数组，这里不共享引用
        this.neighbourCrossing = new boolean[4];
        if (neighbourCrossing != null) {
            for (int i = 0; i < 4 && i < neighbourCrossing.length; i++) {
                this.neighbourCrossing[i] = neighbourCrossing[i];
            }
        }
    }

    /** 从方块实体的 extraConfig 读出配置，并把越界值钳回合法区间。 */
    public static CrossingSettings from(BlockEntityLevelCrossing be) {
        return new CrossingSettings(
                LevelCrossingStyles.byId(be.getExtraConfig(BlockEntityLevelCrossing.KEY_STYLE,
                        LevelCrossingStyles.ASPHALT.id())).id(),
                clampInt(be.getExtraConfigInt(BlockEntityLevelCrossing.KEY_OUTER_EXTEND, 0), 0, 2),
                clampInt(be.getExtraConfigInt(BlockEntityLevelCrossing.KEY_STEP_COUNT, 8), 1, 32),
                Math.max(0, be.getExtraConfigFloat(BlockEntityLevelCrossing.KEY_BEVEL_WIDTH, 1.0f)),
                Math.max(0, be.getExtraConfigFloat(BlockEntityLevelCrossing.KEY_DECK_INSET, DEFAULT_DECK_INSET)),
                normalizeAuto(be.getExtraConfigFloat(BlockEntityLevelCrossing.KEY_RAIL_TOP_OVERRIDE, -1f)),
                normalizeAuto(be.getExtraConfigFloat(BlockEntityLevelCrossing.KEY_GROOVE_DEPTH_OVERRIDE, -1f)),
                be.getExtraConfigFloat(BlockEntityLevelCrossing.KEY_RAMP_FOOT, 0f),
                be.neighbourCrossings(),
                be.getExtraConfigFloat(BlockEntityLevelCrossing.KEY_RAIL_TOP_BIAS,
                        (float) DEFAULT_RAIL_TOP_BIAS),
                be.getExtraConfigFloat(BlockEntityLevelCrossing.KEY_LATERAL_SCALE, 1f),
                be.getExtraConfigFloat(BlockEntityLevelCrossing.KEY_LATERAL_OFFSET, 0f)
        );
    }

    /** 轨顶偏置默认值：+2mm（用户口径「完全对齐但稍高一点」）。 */
    public static final double DEFAULT_RAIL_TOP_BIAS = 0.002;

    private static double clampDouble(double value, double min, double max) {
        if (!Double.isFinite(value)) return 0;
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 负值统一收敛成 -1（自动），避免 UI 里输入 -3 这类没意义的负数。 */
    private static double normalizeAuto(double value) {
        return value < 0 ? -1 : value;
    }
}
