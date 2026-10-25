package com.fangsu.turnout;

import com.fangsu.customItem.NteRailManager;
import com.fangsu.customItem.NteRailManager.NteRail;
import com.fangsu.levelcrossing.LevelCrossingGaugeTable;
import com.fangsu.levelcrossing.LevelCrossingGaugeTable.GaugeInfo;
import org.jetbrains.annotations.Nullable;

/**
 * 道岔几何参数：把「轨道 id」合成为几何子代理可直接消费的一组数值。
 *
 * <p><b>单位约定</b>（全篇统一）：</p>
 * <ul>
 *   <li>{@code gaugeBlocks / grooveOuter / grooveInner / grooveDepth / railTopY / railWidth / halfWidth}
 *       —— 「格」，1 格 = 1 米，与 {@code LevelCrossingGaugeTable} 一致；</li>
 *   <li>{@code gaugeMm} —— 毫米，仅作原始声明值保留，几何计算一律用 {@code gaugeBlocks}。</li>
 * </ul>
 *
 * <p><b>断面来源（{@link BeamProfile}）</b>：道岔要的是<b>工字钢</b>（轨头 + 轨腰 + 轨底），
 * 不是平交路口的电车槽轨。两套实测值：</p>
 * <ul>
 *   <li>{@link BeamProfile#FSRAIL} —— 从 {@code assets/mtrsteamloco/fsrail/main_tp1.obj} 的
 *       {@code rail} 对象按 |X| 分箱实测（轨头中心 0.7517、轨顶 0.26428、轨底 0.1000…）；</li>
 *   <li>{@link BeamProfile#MTR} —— {@code mtr:default} 按 {@code rail_gauge.json} 的实测值
 *       （railTopY 0.2643、最宽层 0.213173、肩部 0.133、枕木顶 0.097、轨头带 [0.6675, 0.785883]）折算。</li>
 * </ul>
 *
 * <p><b>高度基准</b>：MTR 的 {@code Rail#getPosition(t).y} 返回的是<b>节点方块 Y</b>（轨道模型原点平面），
 * 不是轨面高度，所以几何里「钢轨顶面」= {@code anchor.y + beam.topY()}，轨底面 = {@code anchor.y + beam.baseY()}。</p>
 *
 * <p><b>缺省来源</b>：</p>
 * <ol>
 *   <li>断面参数（{@code grooveOuter / grooveInner / grooveDepth / railTopY}）取自
 *       {@link LevelCrossingGaugeTable#lookupOrNull(String)}，即
 *       {@code fangsu:rail_gauge.json} + 轨道 JSON 内联参数；表里没有该轨道时回落本类的硬编码缺省。</li>
 *   <li>轨距取自轨道 JSON 的 {@code gaugeMm}，换算只走
 *       {@link LevelCrossingGaugeTable#gaugeMmToBlocks(Integer)} 这一个入口；未声明时回落标准轨距 1435 mm。</li>
 *   <li>模型/贴图/复现间隔/翻转/颜色/模型偏移取自 {@link NteRail}（MTR4 资源 schema 字段），
 *       轨道缺失时全部用硬编码缺省，保证 {@link #resolve(String)} 永不返回 null、永不抛异常。</li>
 * </ol>
 */
public record TurnoutRailParams(
        String railId,
        String modelResource,
        String textureResource,
        double repeatInterval,
        boolean flipV,
        int color,
        double modelYOffset,
        @Nullable Integer gaugeMm,
        double gaugeBlocks,
        double grooveOuter,
        double grooveInner,
        double grooveDepth,
        double railTopY,
        // 单侧横向总宽（内板 + 轮缘槽 + 钢板）= grooveOuter - grooveInner，单位格（槽轨语义，工字钢不用它）
        double railWidth,
        // 轨道半宽 = gaugeBlocks / 2 + grooveOuter，与平交路口采样用的 halfWidth 同义
        double halfWidth,
        // 工字钢断面（实测值）：轨底/轨腰/轨头三段
        BeamProfile beam,
        // 垂直微调（格）：正数把钢轨整体抬高，供实机对齐
        double railTopOffset,
        // 横向微调（格）：正数让两根钢轨整体外移
        double lateralBias) {

    /** 找不到任何轨道时回落的 id（与 {@code fangsu:rail_gauge.json} 的键一致）。 */
    public static final String DEFAULT_RAIL_ID = "mtr:default";
    /** 标准轨距（格）：1435 mm。 */
    public static final double DEFAULT_GAUGE_BLOCKS = LevelCrossingGaugeTable.DEFAULT_GAUGE_MM / 1000.0D;
    /** MTR 默认轨道颜色 0xRRGGBB（MTR4 {@code new RailResource(id, name, ...)} 用 "777777"）。 */
    private static final int DEFAULT_COLOR = 0x777777;

    private static final double DEFAULT_REPEAT_INTERVAL = 0.5D;
    private static final double DEFAULT_MODEL_Y_OFFSET = 0.0D;
    /** 以下断面缺省值抄自 {@code rail_gauge.json} 的 defaults，表读不到时保持同一套几何。 */
    private static final double DEFAULT_GROOVE_OUTER = 0.785883D;
    private static final double DEFAULT_GROOVE_INNER = 0.6675D;
    private static final double DEFAULT_GROOVE_DEPTH = 0.09375D;
    private static final double DEFAULT_RAIL_TOP_Y = 0.26428D;

    /**
     * 工字钢断面（单位：格，y 是<b>模型空间</b>高度：0 = 轨道模型原点平面 / 方块底面）。
     *
     * <pre>
     *          headTopHalf
     *        ┌───────┐  topY            （轨头顶面）
     *        │  head │
     *    ────┘       └──── headHalf     （轨头下沿，webTopY 处向外张开）
     *        │  web  │  webHalf         （轨腰）
     *    ────┘       └──── footTopY     （轨底顶面）
     *   ┌─────────────────┐  footHalf   （轨底）
     *   └─────────────────┘  baseY
     * </pre>
     *
     * @param baseY       轨底底面高度（= 枕木顶面）
     * @param footHalf    轨底半宽（最宽处，几何上决定「两根钢轨会不会压到一起」）
     * @param footTopY    轨底顶面高度（轨腰起点）
     * @param webHalf     轨腰半宽
     * @param webTopY     轨腰顶面高度（轨头起点）
     * @param headHalf    轨头半宽（轨头下沿最宽处）
     * @param headTopHalf 轨头顶面半宽（轨头顶面收口）
     * @param topY        轨顶高度（= 模型空间里的 railTopY）
     * @param headCenterX 该模型的轨头中心到轨道中心线的横向距离（实测；用于与既有轨道对齐）
     */
    public record BeamProfile(double baseY, double footHalf, double footTopY, double webHalf,
                              double webTopY, double headHalf, double headTopHalf,
                              double topY, double headCenterX) {

        /**
         * fsrail（{@code main_tp1.obj} 的 {@code rail} 对象）实测值。
         * <p>分箱方法见 {@code TurnoutGeometry} 注释：把 rail 对象每个 z 环的右侧 16 个顶点按 |x| 排序，
         * 得到 轨底 [0.6875, 0.8159]@y=0.1000、轨腰 [0.7425, 0.7609]@y=0.1411..0.2350、
         * 轨头 [0.7175, 0.7859]@y=0.2450、轨顶 [0.7226, 0.7808]@y=0.2643。</p>
         */
        public static final BeamProfile FSRAIL = new BeamProfile(
                0.1000D,          // baseY      轨底底面（枕木顶 zhenmu_type1 y=0.1000）
                0.0642D,          // footHalf   (0.8159-0.6875)/2
                0.1411D,          // footTopY   轨腰起点
                0.0092D,          // webHalf    (0.7609-0.7425)/2
                0.2350D,          // webTopY    轨腰顶 / 轨头下沿
                0.0342D,          // headHalf   (0.7859-0.7175)/2
                0.0291D,          // headTopHalf (0.7808-0.7226)/2 在轨顶面
                0.26428D,         // topY       轨顶（= rail.obj 的 y 最大值）
                0.7517D);         // headCenterX (0.7175+0.7859)/2

        /**
         * {@code mtr:default} 折算值（来源：{@code rail_gauge.json} + 平交路口实测文档：
         * railTopY 0.2643、最宽层 0.213173、肩部 0.133、枕木顶 0.097、轨头带 [0.6675, 0.785883]）。
         * <p>轨腰宽度没有直接实测值，按轨头宽的 50% 取（工字钢腰厚 ≈ 头宽的 40%~60%）。</p>
         */
        public static final BeamProfile MTR = new BeamProfile(
                0.0970D,
                0.1066D,          // 0.213173 / 2
                0.1330D,
                0.0296D,          // 头宽 0.1184 的一半 → 腰取 0.0592/2
                0.2350D,
                0.0592D,          // (0.785883-0.6675)/2
                0.0500D,
                0.26428D,
                0.7267D);         // (0.6675+0.785883)/2
    }

    /** 连 mtr:default 都不在轨道列表里时的兜底实例。 */
    private static final TurnoutRailParams FALLBACK = new TurnoutRailParams(
            DEFAULT_RAIL_ID, "fangsu:turnout/builtin/mtr_default.obj", "", DEFAULT_REPEAT_INTERVAL, false, DEFAULT_COLOR, DEFAULT_MODEL_Y_OFFSET,
            null, DEFAULT_GAUGE_BLOCKS, DEFAULT_GROOVE_OUTER, DEFAULT_GROOVE_INNER,
            DEFAULT_GROOVE_DEPTH, DEFAULT_RAIL_TOP_Y,
            DEFAULT_GROOVE_OUTER - DEFAULT_GROOVE_INNER,
            DEFAULT_GAUGE_BLOCKS * 0.5D + DEFAULT_GROOVE_OUTER,
            BeamProfile.MTR, 0.0D, 0.0D);

    /**
     * 按轨道 id 解析几何参数。
     *
     * @param railId 轨道 id（可为 null / 空 / 不存在）
     * @return 永远非 null：id 无效时依次尝试 {@value #DEFAULT_RAIL_ID}，再退回硬编码缺省
     */
    public static TurnoutRailParams resolve(@Nullable String railId) {
        NteRailManager manager = NteRailManager.getInstance();
        NteRail rail = manager.getRail(railId);
        if (rail == null) {
            rail = manager.getRail(DEFAULT_RAIL_ID);
        }
        if (rail == null) {
            return FALLBACK;
        }

        GaugeInfo info = LevelCrossingGaugeTable.getInstance().lookupOrNull(rail.id());
        double grooveOuter = info != null ? info.grooveOuter() : DEFAULT_GROOVE_OUTER;
        double grooveInner = info != null ? info.grooveInner() : DEFAULT_GROOVE_INNER;
        double grooveDepth = info != null ? info.grooveDepth() : DEFAULT_GROOVE_DEPTH;
        double railTopY = info != null ? info.railTopY() : DEFAULT_RAIL_TOP_Y;

        // 轨距的唯一换算入口：毫米轨距优先，未声明则用标准轨距
        double gaugeBlocks = rail.gaugeMm() != null
                ? LevelCrossingGaugeTable.gaugeMmToBlocks(rail.gaugeMm())
                : DEFAULT_GAUGE_BLOCKS;

        // 断面来源：fsrail 系列按 main_tp1.obj 的实测值，其余（mtr:default 等）用 rail_gauge 折算值
        BeamProfile beam = isFsrail(rail) ? BeamProfile.FSRAIL : BeamProfile.MTR;

        return new TurnoutRailParams(
                rail.id(),
                rail.model(),
                rail.textureResource(),
                rail.repeatInterval(),
                rail.flipV(),
                rail.color() != null ? rail.color() : DEFAULT_COLOR,
                rail.modelYOffset() != null ? rail.modelYOffset() : DEFAULT_MODEL_Y_OFFSET,
                rail.gaugeMm(),
                gaugeBlocks,
                grooveOuter,
                grooveInner,
                grooveDepth,
                railTopY,
                grooveOuter - grooveInner,
                gaugeBlocks * 0.5D + grooveOuter,
                beam,
                0.0D,
                // 横向基准仍是 gaugeBlocks/2，这个 bias 只承载「模型实测轨头中心 − gaugeBlocks/2」的差值，
                // 让几何与 MTR 自己渲染的轨道对齐；置 0 即严格按 gaugeBlocks/2 取中。
                beam.headCenterX() - gaugeBlocks * 0.5D);
    }

    /** 该轨道是不是 fsrail 系列（模型路径或 id 里带 fsrail / main_tp）。 */
    private static boolean isFsrail(NteRail rail) {
        String model = rail.model() == null ? "" : rail.model().toLowerCase(java.util.Locale.ROOT);
        String id = rail.id() == null ? "" : rail.id().toLowerCase(java.util.Locale.ROOT);
        return model.contains("fsrail") || id.contains("fsrail") || model.contains("main_tp") || id.contains("main_tp");
    }

    /** 该轨道是不是 fsrail 工字钢（用于诊断日志）。 */
    public boolean isFsrailProfile() {
        return beam == BeamProfile.FSRAIL;
    }

    /**
     * 钢轨中心到轨道中心线的横向距离（格）。
     * <p>基准按需求仍是 {@code gaugeBlocks / 2}，加上 {@link #lateralBias}（默认值 = 模型实测轨头中心 − gauge/2，
     * 让几何与 MTR 渲染出来的轨道严丝合缝）。</p>
     */
    public double railCenterOffset() {
        return gaugeBlocks * 0.5D + lateralBias;
    }

    /** 钢轨的横向总宽（取最宽的轨底）。 */
    public double beamWidth() {
        return beam.footHalf() * 2.0D;
    }
}
