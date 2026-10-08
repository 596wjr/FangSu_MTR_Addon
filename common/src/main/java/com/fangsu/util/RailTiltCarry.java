package com.fangsu.util;

import com.fangsu.mappings.rail.RailPoseExtra;

/**
 * 一条轨道的「作者授权逐轨道超高」随行数据：节点驱动的轨道重建
 * （{@code NODE_REFRESH_RAIL} → {@link NodeConnector#refreshNodeRail}）必须把它从客户端带过去，
 * 否则重建会用「只含节点派生数据」的新姿态整份覆盖旧姿态，把作者编辑过的三点剖面和半轨距一起抹掉
 * （{@code RailPoseExtraHolder.apply} 是<b>整体写入</b>，不合并）。
 * <p>
 * 与 MTR4 版 {@code com.fangsu.util.RailTiltCarry} <b>逐字对应</b>：唯一 import 是
 * {@link RailPoseExtra}，不含任何 MTR / MC 类型，因此两个工程可以共用同一份语义。
 * <p>
 * <b>为什么要显式的 {@link #hasRailTilt()} 布尔</b>：{@code RailPoseExtra} 用
 * {@code Double null} 表示「从未授权」，而 0/0/0 是「作者显式授权成水平」——两者在数值上无法区分，
 * 但在渲染上必须区分（未授权要回退到节点派生滚转，授权成 0 要保持不倾斜）。这个布尔就是那个
 * 「{@code null} 哨兵」在网络包里的等价物：{@code false} 时服务端<b>不调用</b>
 * {@link RailPoseExtra#withRailTilt}，保持当时节点派生的滚转与半轨距。
 * <p>
 * <b>参考帧</b>：三点剖面的 start/end 属于 {@code Rail.position1} / {@code Rail.position2}
 * （见 {@link RailPoseExtra#toRollProfile()}），而重建出来的新轨道恒以「本节点 → 另一端」为
 * position1 → position2（{@link NodeConnector#refreshNodeRail} 里 {@code posStart = nodePos}）。
 * 因此从旧轨道取值时必须按<b>旧轨道的 position1 是哪一端</b>换算参考帧，见 {@link #fromPose}。
 */
public record RailTiltCarry(
        /** true = 该轨道确有作者授权的三点剖面（可能是全 0），false = 未授权，回退节点派生值。 */
        boolean hasRailTilt,
        /** 起点控制点（度），参考帧 = 本节点 → 另一端。 */
        double startDegrees,
        /** 中间控制点（度）。 */
        double middleDegrees,
        /** 终点控制点（度）。 */
        double endDegrees,
        /** 中间控制点的归一化位置（参考帧 = 本节点 → 另一端）。 */
        double middleFraction,
        /** 半轨距（米）。 */
        double halfGauge
) {

    /**
     * 「该轨道没有授权逐轨道超高」。其余字段只是把记录填满（网络包按定长写出），
     * 服务端只看 {@link #hasRailTilt()}，不会读到它们。
     */
    public static final RailTiltCarry NONE = new RailTiltCarry(
            false,
            0.0D, 0.0D, 0.0D,
            RailPoseExtra.DEFAULT_RAIL_TILT_MIDDLE_FRACTION,
            RailPoseExtra.DEFAULT_HALF_GAUGE
    );

    /**
     * 从轨道<b>当前</b>姿态取出随行数据。
     *
     * @param pose     轨道当前附加姿态（{@code RailPoseExtraHolder.peek(rail)}）
     * @param reversed 参考帧是否需要反转：{@code true} = 旧轨道的 {@code position1} <b>不是</b>本节点
     *                 （即旧轨道以另一端为 position1），此时 start/end 整体对调，
     *                 中间控制点的归一化位置镜像成 {@code 1 - f}（控制点在物理上没有移动）
     * @return 未授权时返回 {@link #NONE}
     */
    public static RailTiltCarry fromPose(RailPoseExtra pose, boolean reversed) {
        if (pose == null || !pose.hasRailTilt()) {
            return NONE;
        }
        // hasRailTilt() 为 true 时三个 Double 必非 null，可以安全拆箱
        if (!reversed) {
            return new RailTiltCarry(true,
                    pose.railTiltStartDegrees, pose.railTiltMiddleDegrees, pose.railTiltEndDegrees,
                    pose.railTiltMiddleFraction, pose.halfGauge);
        }
        return new RailTiltCarry(true,
                pose.railTiltEndDegrees, pose.railTiltMiddleDegrees, pose.railTiltStartDegrees,
                1.0D - pose.railTiltMiddleFraction, pose.halfGauge);
    }

    /**
     * 把随行的授权值合并进服务端<b>刚派生</b>的节点姿态。
     * <p>
     * 未授权时原样返回 {@code nodePose}：此刻服务端必须完全按当前节点值走
     * （滚转角、半轨距都是节点派生的），这也是「清除」后应有的行为。
     * 已授权时只覆盖三点剖面与半轨距，两端的平移 / 俯仰 / 节点派生滚转原样保留
     * （{@link RailPoseExtra#withRailTilt} 的既有语义，本方法不改变它）。
     *
     * @param nodePose 服务端按当前节点数据重新派生的姿态
     * @return 最终要写进候选轨道的姿态
     */
    public RailPoseExtra mergeInto(RailPoseExtra nodePose) {
        if (!hasRailTilt) {
            return nodePose;
        }
        return nodePose.withRailTilt(startDegrees, middleDegrees, endDegrees, middleFraction, halfGauge);
    }
}
