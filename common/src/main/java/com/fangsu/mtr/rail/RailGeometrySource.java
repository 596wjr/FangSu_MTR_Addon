package com.fangsu.mtr.rail;

import com.fangsu.Main;
import com.fangsu.mappings.rail.RailGeometryCore;
import com.fangsu.mappings.rail.RailPoseExtra;
import com.fangsu.mappings.rail.RailRollProfile;

/**
 * MTR3 的「轨道几何来源」适配层：把一个 {@code mtr.data.Rail} 的附加姿态翻译成版本无关内核
 * {@link RailGeometryCore}，并按姿态惰性构建、失效重建。
 * <p>
 * <b>P5-1 里唯一被消费的量是 {@link #getY}（中心线高度）。</b>这不是偷懒，而是 MTR3 的结构决定的：
 * <ul>
 *   <li>MTR3 把几何算在 {@code Rail} 的 {@code private final} 字段里且<b>不可回写</b>，适配层只能
 *       「不改原式，只改读数」；</li>
 *   <li>MTR3 的 {@code Rail.getPosition(double)} 与 {@code Rail.renderSegment(...)} 取 y
 *       <b>只</b>经过私有的 {@code getPositionY(double)}（{@code javap -p} 已核实：整个
 *       {@code Rail} 类里只有它是 y 的来源）。于是改写它的返回值，就同时覆盖
 *       {@code getPosition(double)}、{@code render(…)}（轨道带 / 信号 / 单行道箭头共用）与
 *       {@code Rail$RenderRail} 回调的 y1/y2；</li>
 *   <li>MTR3 的 {@code getPositionXZ} 是<b>纯 x/z</b>（y 分量恒为 0），而 P5-1 的附加姿态只表达
 *       平移 / 俯仰 / 滚转抬升，<b>不旋转横断面</b>（滚转只把中心线抬高
 *       {@code 半轨距·|sin(滚转角)|}，见内核 {@code rollLift}），因此同一参数处的 x/z
 *       在有姿态与无姿态时本来就应完全相同 —— 不动 x/z 既满足「无姿态逐位一致」，
 *       也把「改动横断面角点」的全部风险挡在本步骤之外。</li>
 * </ul>
 * <b>横断面滚转属于 P5-3</b>：那时才重写 {@code renderSegment} 的四个角点（需要把参数反解回滚转
 * 剖面，内核已提供 {@code parameterAt}）。本步骤<b>刻意不做</b>，因为 {@code renderSegment} 与
 * 信号共用：{@code RenderTrains.renderSignalsStandard}（源码 :495-519）会用
 * {@code rail.render(…, u1-1, u2-1)} 走同一条回调，一旦改写角点，信号与单行道箭头会一起被倾斜。
 * <p>
 * <b>无姿态时的行为</b>：{@link #isActive} 返回 {@code false}，钩子直接返回 MTR 自己的值，内核
 * <b>根本不参与</b>（不是「参与后算出一样的值」），因此「无姿态逐位一致」是结构性保证。探针另外
 * 证明了「有姿态但滚转/俯仰为 0 时也不漂移」：CURVE 轨的内核输出与 MTR 逐位相同，CABLE 轨则按
 * {@link #getY} 里说明的叠加方式处理。
 */
public final class RailGeometrySource {

    /**
     * 在所有可核实的 MTR3 版本里 {@code Rail.getPositionY} 都是
     * {@code private double getPositionY(double)}。保留成常量是为了让诊断信息可以逐字引用它。
     */
    public static final String TARGET_METHOD = "getPositionY(D)D";

    /**
     * 姿态生效到几何上的最小轨道长度（格），低于它就整条回退到 MTR 原式。
     * <p>
     * 必要性：MTR3 的 {@code getPositionY} 在 {@code length == 0} 时会算出 {@code 0/0 = NaN}
     * （退化轨道，例如被垂直节点封死的角落 —— 构造器把 h/k/r/t 全置 0），内核也明确拒绝长度为 0 的
     * 纵坡。把这一步挡在适配层，可以保证「本特性绝不引入 NaN 高度」，这是
     * {@code NodeConnector.isGeometryValid} 之类调用方未必能容忍的。
     */
    private static final double MIN_LENGTH = 1.0E-9D;

    // ==================== 一次性诊断 ====================

    private static boolean firstKernelUseLogged = false;
    private static boolean hookDeadWarned = false;

    /**
     * 内核第一次真的建成时调用：打一条 INFO 并置位，之后调用无副作用。
     * <p>
     * 这是「本步骤确实接上了」的<b>正向</b>证据：P5-1 在没有作者姿态时视觉上完全不可见，
     * 没有这条日志就无法从游戏日志里区分「几何已接管」与「钩子没生效」。
     * 只可能重复打印一次，并发无害。
     */
    public static void markFirstKernelUse() {
        if (firstKernelUseLogged) {
            return;
        }
        firstKernelUseLogged = true;
        Main.LOGGER.info("[P5-1] 轨道附加姿态几何内核已生效：中心线高度改由 RailGeometryCore 提供"
                + "（注入目标 {}，轨道带/信号/寻路共用同一参数化）", TARGET_METHOD);
    }

    /** 供探针/诊断读取：内核是否已经真的被建成过（即 {@code getPositionY} 钩子是否活着）。 */
    public static boolean wasKernelUsed() {
        return firstKernelUseLogged;
    }

    /**
     * 钩子「注射失败」的一次性诊断。
     * <p>
     * 为什么需要它：P5-1 的钩子是「无声」的 —— 没有姿态时视觉与行为完全不变，一旦 Mixin 没能注入
     * （例如将来某个 MTR3 补丁把 {@code getPositionY} 改名/内联），本特性会<b>静默降级成「不倾斜」</b>，
     * 而这与「一切正常」在游戏内无法区分。本方法在那条路径上补一条 WARN。
     * <p>
     * 调用时机：① {@code RailGeometryMixin.rebuildRailGeometry()}（作者刚写下第一个非默认姿态，
     * 必然在服务端主线程、早于任何渲染）会调用一次 —— 若几何钩子活着，同一帧内内核就会先被建成，
     * 于是这里不打印；② 探针可主动调用以断言诊断机制本身有效。
     *
     * @return 是否真的打出了 WARN（探针据此断言诊断机制有效）
     */
    public static boolean warnIfHookNotAlive() {
        if (firstKernelUseLogged || hookDeadWarned) {
            return false;
        }
        hookDeadWarned = true;
        Main.LOGGER.warn("[P5-1] 轨道中心线几何钩子从未触发：{} 未被注入或未被调用。"
                + "本模组的轨道附加姿态（超高 / 纵坡 / 平移）在本版本上不会生效，普通轨道不受影响。"
                + "请检查 Rail 的几何方法是否被改名或内联。", TARGET_METHOD);
        return true;
    }

    private RailGeometrySource() {
    }

    /**
     * 姿态是否会改变几何。
     * <p>
     * 判据就是 {@link RailPoseExtra#affectsGeometry()}（等价于 {@code !isDefault()}）：平移 / 俯仰 /
     * 滚转 / 逐轨道超高任一非默认即为真。<b>只有半轨距不同时为假</b> —— 半轨距只通过
     * {@code halfGauge·|sin(roll)|} 参与几何，滚转为 0 时它不影响任何输出。
     */
    public static boolean isActive(RailPoseExtra pose) {
        return pose != null && pose.affectsGeometry();
    }

    /**
     * 取中心线高度。
     * <p>
     * <b>两种模式，取决于竖直剖面能否用内核整体表达</b>：
     * <ul>
     *   <li><b>CURVE（{@code SHAPE_QUADRATIC}）</b>：内核的竖直剖面与 MTR 的抛物线分支<b>逐位相同</b>
     *       （见 {@link #build}），所以整体采用内核算出的 y。俯仰（Hermite 纵坡）也只有这条路径能表达。</li>
     *   <li><b>CABLE（{@code SHAPE_CABLE}）</b>：<b>不能</b>整体采用内核的 y。MTR3 的 CABLE 轨
     *       <b>无条件</b>先走缆车分支，而内核的 {@code basePositionY} 第一条规则是
     *       「{@code yStart == yEnd} 直接返回 {@code yStart}」（先于 {@code SHAPE_CABLE} 分支），
     *       因此两端等高的缆车轨上内核会丢掉那 0.5 格的台阶与下垂（实测 20 格平缆车轨差 0.0024 格）。
     *       内核是逐字节同步文件、本阶段不可改，所以 CABLE 轨改为<b>叠加</b>：
     *       {@code MTR 的 y + 内核的 rollLift} —— 缆车台阶仍由 MTR 提供，滚转抬升由内核提供，
     *       两者物理上互不干涉（台阶是纵向剖面，抬升是横向超高）。</li>
     * </ul>
     * 无姿态 / 内核拒绝构建时一律原样返回 {@code originalY}（MTR3 自己的值）。
     * <p>
     * 这是 mixin 注解处理器的<b>全部逻辑</b>，放在普通类里是为了能用真实 MTR3 的
     * {@code mtr.data.Rail} 在不启动游戏的前提下做数值探针（见
     * {@code .tmp_p5_1_probe/RailGeometryProbe.java}）。
     *
     * @param rail      目标轨道（只需能强转成 {@link RailGeometryProvider}）
     * @param pose      该轨道当前的附加姿态（{@code null} 视作默认，直接回退）
     * @param value     沿轨道的参数（0 .. {@code rail.getLength()}），与 MTR 的
     *                  {@code getPositionY(double)} 同一参数化
     * @param originalY MTR3 自己算出的高度（交给注解处理器拿到的返回值）
     * @param coreMode  {@link RailGeometryCore#SHAPE_QUADRATIC} 或 {@link RailGeometryCore#SHAPE_CABLE}
     */
    public static double getY(Object rail, RailPoseExtra pose, double value, double originalY, int coreMode) {
        if (!isActive(pose)) {
            return originalY;
        }
        if (!(rail instanceof RailGeometryProvider)) {
            // 理论上不可达：RailGeometryProvider 由 RailGeometryMixin 实现，而本方法只可能从该 mixin
            // 的处理器里被调用。留作防御，且绝不改变结果。
            return originalY;
        }
        // 每次调用都经 provider 取，由它负责「按当前姿态重建 / 复用缓存」；
        // 这里不要自己缓存内核 —— 姿态可能在两次调用之间被改写。
        final RailGeometryCore core = ((RailGeometryProvider) rail).getFangSuRailGeometryCore();
        if (core == null) {
            // 几何退化 / 参数化无法对齐：保持 MTR 原样，绝不引入 NaN
            return originalY;
        }
        if (coreMode == RailGeometryCore.SHAPE_CABLE) {
            return originalY + core.rollLift(value);
        }
        return core.getPositionY(value);
    }

    // ==================== 内核构建 ====================

    /**
     * 由轨道几何字段 + 附加姿态构建内核。
     * <p>
     * <b>水平几何在 P5-1 里只是占位</b>，理由见 {@link #stubAnchors}：本步骤只消费
     * {@code getPositionY}，而它只依赖<b>总长度</b>、{@code yStart/yEnd}、俯仰角与滚转剖面。
     * <p>
     * <b>竖直剖面</b>：MTR3 的 {@code RailType.RailSlopeStyle} 只有 {@code CURVE} 与 {@code CABLE}，
     * 而内核的 {@code SHAPE_QUADRATIC / SHAPE_TWO_RADII} 在 {@code verticalRadius == 0} 时走
     * <b>不同</b>分支（{@code SHAPE_TWO_RADII} 退化为线性），所以 MTR3 的曲线轨必须映射成
     * {@code SHAPE_QUADRATIC} 才能与 {@code Rail.getPositionY} 的抛物线分支逐位一致。MTR3 不使用
     * 竖曲线半径，{@code verticalRadius} 恒为 0。CABLE 轨映射成 {@code SHAPE_CABLE}，其常量
     * （{@code CABLE_CURVATURE_SCALE = 1000}、{@code MAX_CABLE_DIP = 8}）与 MTR3 完全相同。
     * <p>
     * <b>端序</b>：MTR3 的 {@code Rail} 构造器不做 MTR4 那种 {@code reversePositions} 端点交换
     * （{@code javap} 已核实：只有一个几何构造器，参数顺序就是
     * {@code posStart, facingStart, posEnd, facingEnd}），因此姿态的 {@code *1} 恒对应
     * {@code facingStart} 端、{@code *2} 恒对应 {@code facingEnd} 端，<b>不需要</b> MTR4
     * {@code FangSuRailMath} 里的 {@code firstIsPosition1} 端点映射与剖面镜像。
     *
     * @param length   轨道长度（{@code Rail.getLength()}）；同时是内核位置公式里 {@code +0.5} 之外的
     *                 全部水平信息
     * @param yStart   起点高度（{@code Rail.yStart}，整数格）
     * @param yEnd     终点高度（{@code Rail.yEnd}）
     * @param coreMode {@link RailGeometryCore#SHAPE_QUADRATIC} 或
     *                 {@link RailGeometryCore#SHAPE_CABLE}（由 {@code RailType.railSlopeStyle} 决定）
     * @return 内核；长度退化（&lt; {@link #MIN_LENGTH}）或姿态为默认时返回 {@code null}
     */
    public static RailGeometryCore build(double length, double yStart, double yEnd, int coreMode, RailPoseExtra pose) {
        if (pose == null || !isActive(pose)) {
            return null;
        }
        if (!(length > MIN_LENGTH)) {
            // 退化轨道：MTR 自己的公式会得 NaN（0/0），内核同样无意义，整条回退
            return null;
        }
        final double[][] anchors = stubAnchors(length, yStart, yEnd);
        final RailRollProfile rollProfile = rollProfileFor(pose);

        // 竖直剖面：CURVE 用内核的 QUADRATIC 分支（与 MTR 的抛物线分支逐位相同），CABLE 用
        // SHAPE_CABLE 分支。竖曲线半径恒为 0 —— MTR3 不使用它，而且 CABLE 轨最后并不会采用内核的
        // 剖面（见 getY 的说明：内核的 basePositionY 在 yStart == yEnd 时会先返回 yStart，
        // 早于 SHAPE_CABLE 分支，无法表达两端等高的缆车台阶；那一步由 MTR 自己提供）。
        final RailGeometryCore core = new RailGeometryCore(
                anchors[0], anchors[1],
                0.0D, 0.0D, coreMode, 0.0D,
                Math.toRadians(pose.pitch1Degrees), Math.toRadians(pose.pitch2Degrees),
                rollProfile, pose.halfGauge
        );
        if (Double.doubleToRawLongBits(core.getLength()) != Double.doubleToRawLongBits(length)) {
            return null;
        }
        return core;
    }

    /**
     * 水平占位锚点：{@code x1 = -length}、{@code x2 = 0}，两端角度 0°，y 取 MTR 的
     * {@code yStart / yEnd} 加上该端的 {@code offsetY}。
     * <p>
     * <b>为什么水平量可以占位、且平移不进水平锚点</b>：P5-1 只调用内核的 {@code getPositionY}，而
     * {@code RailGeometryCore} 的竖直剖面（{@code basePositionY} / {@code hermitePositionY} /
     * {@code rollLift}）<b>只读长度、yStart/yEnd、俯仰角与滚转剖面</b>，一个水平量都不读
     * （{@code rollLift} 读 yStart 只是为了判断退化，见内核实现）。于是：
     * <ul>
     *   <li>姿态的 {@code offsetX/offsetZ} 在 P5-1 里<b>没有可观测作用</b>（要等到 P5-3 重写横断面角点
     *       才会用到），因此不去扰动水平锚点 —— 一旦扰动，它就会进入内核 h/k/r/t 的浮点公式、
     *       让长度差几个 ULP，从而让「滚转剖面相位」与 MTR 的长度产生无关漂移；</li>
     *   <li>{@code offsetY} 则<b>必须</b>进入锚点 y，它就是 P5-1 要表达的「节点抬高 / 压低」。</li>
     * </ul>
     * <p>
     * <b>为什么不用「真锚点」</b>（已实测，两种反推都失败）：
     * <ul>
     *   <li>MTR3 的 {@code Rail} 不保存方块坐标；</li>
     *   <li>用 {@code getPosition(0)} 反推起点、再沿 {@code facingStart} 走满长度 → 对
     *       <b>两半径 / 直线段优先</b>这类轨道，起点处的切线方向<b>不是</b> {@code facingStart}
     *       （起点在半径 1 的圆弧上，切线是圆弧切线；{@code facingStart} 只保证端点衔接与
     *       {@code deltaSide} 的符号），于是内核算出的长度变成 0，弯轨全部无法倾斜。</li>
     * </ul>
     * 占位锚点的长度<b>恒等</b>于传入的 {@code length}（{@code (h1*x2 - h1*x1)/1 = x2 - x1}），
     * 于是「相位与 MTR 一致」由构造成立，且不依赖任何几何反推。{@link #build} 仍会做一次逐位长度校验，
     * 作为对内核未来改动的护栏。
     */
    public static double[][] stubAnchors(double length, double yStart, double yEnd) {
        return new double[][]{
                {-length, yStart, 0.0D},
                {0.0D, yEnd, 0.0D}
        };
    }

    /**
     * 由附加姿态选出实际生效的滚转剖面。
     * <p>
     * 语义与 MTR4 的 {@code FangSuRailMath.rollProfileFor(pose, firstIsPosition1)} <b>完全一致</b>，
     * 只是 MTR3 没有 {@code reversePositions}（见 {@link #build} 的端序说明），所以端点映射恒为
     * {@code position1 → 参数 0}：
     * <ul>
     *   <li>编辑过逐轨道超高：三点剖面（{@code start} 在 0、{@code end} 在 1、{@code middle} 在
     *       {@code railTiltMiddleFraction}），与 MTR4 {@code firstIsPosition1 == true} 分支逐字相同；</li>
     *   <li>没编辑过：节点派生的两点剖面（{@code roll1} 在 0、{@code roll2} 在 1），
     *       与引入本特性之前逐位相同。</li>
     * </ul>
     */
    public static RailRollProfile rollProfileFor(RailPoseExtra pose) {
        if (pose.hasRailTilt()) {
            return RailRollProfile.threePointDegrees(
                    pose.railTiltStartDegrees, pose.railTiltMiddleDegrees, pose.railTiltEndDegrees,
                    pose.railTiltMiddleFraction
            );
        }
        return RailRollProfile.twoPointDegrees(pose.roll1Degrees, pose.roll2Degrees);
    }
}
