package com.fangsu.mtr.rail;

/**
 * 「两半径接缝（middle control point）在轨道参数空间里的归一化位置」的提供者
 * （由 {@code com.fangsu.mixin.RailGeometryMixin} 实现在 {@code mtr.data.Rail} 上）。
 * <p>
 * <b>为什么需要它</b>：逐轨道超高的中间控制点必须落在<b>两段圆弧的接缝</b>上（那里才是
 * 超高换坡的物理位置），而不是几何中点。MTR4 的这个量来自 {@code RailMath.getLength1/getLength2}；
 * MTR3 没有 {@code RailMath}，几何直接存在 {@code Rail} 的
 * {@code private final tStart1/tEnd1/tStart2/tEnd2} 里，
 * 而接缝位置就是 {@code |tEnd1 - tStart1|}（= {@code Rail.getPosition(double)} 里分段用的
 * {@code count1}）。这四个字段包私有，所以只能由挂在同一个目标类上的 mixin 读出来。
 * <p>
 * <b>参考帧</b>：MTR3 的 {@code Rail} 构造器<b>不</b>交换端点（见 {@code NodeConnector#parameterSign}
 * 的推导），内核参数 0 端恒为自己构造时的 {@code posStart}，也就是
 * {@code rails[position1][position2]} 里的 {@code position1}。因此这里返回的
 * {@code count1 / length} <b>天然就是</b>「position1 → position2 从 0 到 1」的位置，
 * 不需要 MTR4 {@code FangSuRailMath#middleBreakpointFraction} 那种「参数空间 → position1」
 * 的镜像换算。
 * <p>
 * <b>不实现本接口时</b>（理论上不可达：接口由 {@code RailGeometryMixin} 实现，而调用方只从
 * {@code Rail} 的强转走）调用方一律退回默认中点
 * {@link com.fangsu.mappings.rail.RailPoseExtra#DEFAULT_RAIL_TILT_MIDDLE_FRACTION}，
 * 绝不抛出，也绝不返回 NaN。
 */
public interface RailSeamSource {

    /**
     * 两半径接缝的归一化位置（{@code position1 → position2}，0 到 1）。
     * <p>
     * 几何退化（直线轨、退化轨、长度非有限）时返回
     * {@link com.fangsu.mappings.rail.RailPoseExtra#DEFAULT_RAIL_TILT_MIDDLE_FRACTION}（= 0.5）。
     */
    double getFangSuMiddleBreakpointFraction();
}
