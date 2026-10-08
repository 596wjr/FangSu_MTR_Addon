package com.fangsu.mtr.rail;

import com.fangsu.mappings.rail.RailPoseExtra;
import mtr.data.Rail;

/**
 * 逐轨道超高的 MTR3 适配工具：把「轨道实例」翻译成界面 / 服务端需要的那几个不依赖 MTR4 类的量。
 * <p>
 * 存在的意义：MTR4 的 {@code FangSuRailMath} 在 MTR3 上不存在（MTR3 没有 {@code RailMath}），
 * 而逐轨道超高界面与 C2S 通道都需要「中间控制点落在哪里」这一个量。把它放在这里，
 * 界面（客户端）与服务端校验路径就共用同一份实现，不会出现「界面显示 0.5、服务端存 0.3」。
 * <p>
 * 只做<b>纯读取</b>：不改轨道、不发包、不碰方块实体，客户端与服务端都可以安全调用。
 */
public final class RailTiltSupport {

    /**
     * 两段圆弧接缝的判定阈值（格）：短于它的那一段视为退化（直线轨 / 圆弧轨 / 构造失败），
     * 此时没有接缝可谈，退回中点。取值与 MTR4 的
     * {@code FangSuRailMath.DEGENERATE_SEGMENT_LENGTH} 一致。
     */
    private static final double DEGENERATE_SEGMENT_LENGTH = 1.0E-6D;

    private RailTiltSupport() {
    }

    /**
     * 中间控制点应当落在的归一化位置（{@code position1 → position2}）。
     * <p>
     * 曲线轨取两段圆弧的接缝 {@code count1 / length}（与 MTR3 {@code Rail.getPosition(double)}
     * 里 {@code value <= count1} 的分段判据同源），退化时取
     * {@link RailPoseExtra#DEFAULT_RAIL_TILT_MIDDLE_FRACTION}（0.5）。
     * <p>
     * <b>不要照抄 0.5</b>：那只是直线轨的近似，曲线轨上中间控制点会落在错误的位置，
     * 结果是超高在接缝两侧的坡度对不上、渲染出一个扭结。
     *
     * @param rail 目标轨道；{@code null} 时返回默认中点
     * @return 归一化位置（position1 → position2）；几何退化到无法给出接缝位置时为 0.5
     */
    public static double middleBreakpointFraction(Rail rail) {
        if (rail == null) {
            return RailPoseExtra.DEFAULT_RAIL_TILT_MIDDLE_FRACTION;
        }
        if (!(rail instanceof RailSeamSource source)) {
            // 理论上不可达：RailSeamSource 由 RailGeometryMixin 实现在 mtr.data.Rail 上。
            // 防线放在这里，是为了「宁可退回中点，也绝不抛异常 / 返回 NaN」。
            return RailPoseExtra.DEFAULT_RAIL_TILT_MIDDLE_FRACTION;
        }
        final double fraction = source.getFangSuMiddleBreakpointFraction();
        if (!(fraction > 0.0D) || fraction >= 1.0D || !Double.isFinite(fraction)) {
            return RailPoseExtra.DEFAULT_RAIL_TILT_MIDDLE_FRACTION;
        }
        return fraction;
    }

    /**
     * 由两段圆弧的弧长算出接缝的归一化位置。供 {@code RailGeometryMixin} 使用（把 mixin 里的
     * 算术全部集中到普通类里，便于用真实 MTR3 数值做探针）。
     *
     * @param count1 第一段（构造器 {@code posStart} 端那一段）的弧长，必须非负
     * @param count2 第二段的弧长，必须非负
     * @return 归一化位置；任一段退化或总长非有限时为 0.5
     */
    public static double middleBreakpointFraction(double count1, double count2) {
        final double length = count1 + count2;
        if (!(length > 0.0D) || !Double.isFinite(length) || !Double.isFinite(count1) || !Double.isFinite(count2)) {
            return RailPoseExtra.DEFAULT_RAIL_TILT_MIDDLE_FRACTION;
        }
        if (count1 <= DEGENERATE_SEGMENT_LENGTH || count2 <= DEGENERATE_SEGMENT_LENGTH) {
            // 单段直线轨 / 单段圆弧轨：没有接缝，取中点
            return RailPoseExtra.DEFAULT_RAIL_TILT_MIDDLE_FRACTION;
        }
        final double fraction = count1 / length;
        if (!(fraction > 0.0D) || fraction >= 1.0D) {
            return RailPoseExtra.DEFAULT_RAIL_TILT_MIDDLE_FRACTION;
        }
        return fraction;
    }
}
