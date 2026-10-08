package com.fangsu.mtr.rail;

import com.fangsu.mappings.rail.RailPoseExtra;
import mtr.data.Rail;

/**
 * 轨道「附加姿态」存取接口（MTR3 适配层）。
 * <p>
 * 与 {@code com.fangsu.mtr.RailAngleExtra} 同一套路：MTR3 的 {@link Rail} 所有几何字段都是
 * {@code private final}，且完全由构造器写入，外部无法承载 FangSu 的附加数据；因此附加姿态由
 * {@code RailPoseExtraMixin} 以 {@code @Unique} 字段挂在 {@code mtr.data.Rail} 上，
 * 调用方通过本接口强转取值。
 * <p>
 * <b>与 MTR4 版的分工差异（重要）</b>：MTR4 上数据住在 {@code RailSchema}、几何住在
 * {@code Rail.railMath}，所以那边是两个 mixin 合起来满足本接口。MTR3 <b>没有</b>
 * {@code RailSchema}，{@code mtr.data.Rail} 自己既是数据也是几何，因此这里只有
 * <b>一个</b> mixin（{@code RailPoseExtraMixin}），接口的方法全部由它实现。
 * <p>
 * 本类之所以独立于 mixin 存在于普通包中，是为了让持久化逻辑可以在不启动游戏、不跑 Mixin 的
 * 情况下用真实 MTR3 类做往返验证（见 {@link RailPoseExtraIO}）。
 */
public interface RailPoseExtraHolder {

    /** 当前附加姿态；从未设置过时返回 {@link RailPoseExtra#DEFAULT}（绝不为 null）。 */
    RailPoseExtra getFangSuPose();

    /**
     * 仅写入姿态数据，<b>不</b>重建几何。
     * <p>
     * P5-0 只做持久化，几何重建（{@code Rail}: redirect {@code getPositionY} /
     * {@code renderSegment} 到 {@code RailGeometryCore}）属于 P5-1，届时由同一个 mixin 实现本方法。
     */
    void setFangSuPose(RailPoseExtra pose);

    /**
     * 依据当前姿态重建轨道几何。
     * <p>
     * <b>P5-0 阶段为空实现（保留位）</b>：MTR3 的几何是 {@code Rail} 构造器算完写进
     * {@code private final} 字段的，无法在构造之后"重算字段"；P5-1 会用内核直接接管
     * {@code getPositionY}/{@code getPosition}/{@code renderSegment} 的读数，而不是回写字段。
     * 之所以现在就把它放进接口，是为了让 {@link #apply(Rail, RailPoseExtra)} 的语义
     * 从 P5-0 起就与 MTR4 版一致（写姿态 + 重建几何），后续子阶段不必改调用方。
     */
    default void rebuildRailGeometry() {
    }

    /** 便捷方法：写入姿态并立即重建几何。 */
    static void apply(Rail rail, RailPoseExtra pose) {
        if (rail == null) {
            return;
        }
        final RailPoseExtraHolder holder = (RailPoseExtraHolder) (Object) rail;
        holder.setFangSuPose(pose);
        holder.rebuildRailGeometry();
    }

    /** 读取轨道的附加姿态（{@code null} 轨道返回默认姿态）。 */
    static RailPoseExtra peek(Rail rail) {
        if (rail == null) {
            return RailPoseExtra.DEFAULT;
        }
        return ((RailPoseExtraHolder) (Object) rail).getFangSuPose();
    }
}
