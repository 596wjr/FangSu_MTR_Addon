package com.fangsu.mixin;

import com.fangsu.mappings.rail.RailGeometryCore;
import com.fangsu.mappings.rail.RailPoseExtra;
import com.fangsu.mtr.rail.RailGeometryProvider;
import com.fangsu.mtr.rail.RailGeometrySource;
import com.fangsu.mtr.rail.RailPoseExtraHolder;
import com.fangsu.mtr.rail.RailSeamSource;
import com.fangsu.mtr.rail.RailTiltSupport;
import mtr.data.Rail;
import mtr.data.RailAngle;
import mtr.data.RailType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 MTR3 的轨道中心线几何在有附加姿态时改由版本无关内核
 * {@link RailGeometryCore} 提供（P5-1）。
 * <p>
 * <b>唯一钩子：{@code Rail.getPositionY(double)} 的返回值。</b>它是 {@code Rail} 里 y 的
 * <b>唯一</b>来源（已用 {@code javap -p} 对 5 个真实 3.2.2 jar 核实：1.18.2 Fabric/Forge
 * hotfix-1、1.20.1 Fabric hotfix-1、1.20.1 Forge hotfix-1/hotfix-2），因此
 * <ul>
 *   <li>{@code Rail.getPosition(double)}（:353）自动跟着变；</li>
 *   <li>{@code Rail.render(...)} → {@code renderSegment(...)}（:434/:435）自动跟着变，
 *       于是轨道带、<b>信号</b>（{@code RenderTrains.renderSignalsStandard}）与<b>单行道箭头</b>
 *       共享的那条 16-float 绘制调用拿到的 y1/y2 与轨道一致；</li>
 *   <li>x/z 一个字节都不动（{@code getPositionXZ} 仍是 MTR 自己的实现）。</li>
 * </ul>
 * <b>为什么不去改 {@code renderSegment} 的四个角点</b>：那条路径与信号/箭头共用，见
 * {@link RailGeometrySource} 的类注释；横断面滚转是 P5-3 的范围。
 * <p>
 * <b>{@code @Inject(at = RETURN)} + {@code CallbackInfoReturnable} 而不是 {@code @ModifyReturnValue}</b>：
 * 只改返回值不需要 {@code ModifyReturnValue}（它也不在可核实的编译类路径上），而
 * {@code @Inject} 是本仓库在同一条 {@code Rail} 目标上已经跑通的写法。处理器在「无姿态」时
 * <b>不调</b> {@code setReturnValue}，即真正的原样放行。
 * <p>
 * <b>注入目标是私有方法</b>，{@code mtr.data.Rail} 是硬依赖类（始终存在），所以用
 * {@code @Mixin(value = Rail.class, remap = false)}；但方法本身<b>没有</b>用默认的
 * {@code require = 1}：将来若某个 MTR3 补丁把 {@code getPositionY} 改名或内联，本步骤应当
 * 「静默降级为不倾斜」而不是让游戏崩在启动期。代价是失效不可见，因此补了一次性诊断 —— 见
 * {@link #fangsu$warnIfHookNeverFired}（由 {@code rebuildRailGeometry()} 触发；那是 P5-2 作者侧
 * 写下姿态的唯一入口，必然早于任何渲染，所以在那里检查「钩子从未触发」是可靠的）。
 * <p>
 * <b>本类与 {@code RailPoseExtraMixin} 的关系</b>：姿态数据（{@code @Unique} 字段）与几何缓存都挂在
 * 同一个 {@code Rail} 上，但分属两个 mixin 类：写入 / 读写序列化在 {@code RailPoseExtraMixin}，
 * 几何在 {@code RailGeometryMixin}。两者都 <b>必须</b>成功注入，否则 {@code checkcast} 会抛
 * {@code AbstractMethodError}（构造期即炸，不会静默）——这一点已记入 {@link RailGeometrySource}
 * 的诊断注释。
 * <p>
 * <b>P5-2 起</b>：构建内核时把姿态两端的<b>竖向</b>平移（{@code offsetY1/offsetY2}）加进锚点 y
 * （= 方块坐标 + 节点平移，与 MTR4 的 {@code FangSuRailMath} 一致）。水平平移仍不进入内核，
 * 理由见 {@link RailGeometrySource#stubAnchors}。姿态本身由
 * {@code NodeConnector.readRailPose} 从万向节点方块实体派生（含俯仰角投影与翻滚角的
 * 节点帧 → 轨道帧换算）。
 */
@Mixin(value = Rail.class, remap = false)
public abstract class RailGeometryMixin implements RailGeometryProvider, RailPoseExtraHolder, RailSeamSource {

    // ==================== 目标类私有几何字段（@Shadow 只读） ====================
    //
    // 全部已在 5 个真实 3.2.2 jar 上用 javap -p 核实同名同类型（见类注释）。
    // 只读不改：几何字段是 private final，构造器写完就固定。
    // 只 shadow 真正会被读到的字段（h/k/r/reverse/isStraight 不参与 P5-1 的 y 计算，
    // 需要时再补 —— @Shadow 一个不存在的字段是致命的）。

    @Shadow
    private int yStart;
    @Shadow
    private int yEnd;
    @Shadow
    private RailAngle facingStart;
    @Shadow
    private RailAngle facingEnd;
    @Shadow
    private RailType railType;
    /**
     * 两段圆弧的参数区间端点（P5-5 逐轨道超高才用到）。
     * <p>
     * {@code |tEnd1 - tStart1|} 就是 {@code Rail.getPosition(double)} 里分段用的 {@code count1}，
     * 也就是「两段圆弧的接缝」在弧长参数上的位置 —— 逐轨道超高的中间控制点必须落在那里。
     * 这四个字段是 {@code private final}，除了 shadow 之外没有别的读法。
     */
    @Shadow
    private double tStart1;
    @Shadow
    private double tEnd1;
    @Shadow
    private double tStart2;
    @Shadow
    private double tEnd2;

    @Shadow
    public abstract double getLength();

    /**
     * 按当前姿态缓存的内核；{@code null} = 无姿态 / 尚未构建 / 已失效。
     * <p>
     * {@code volatile}：{@code Rail} 会在服务端主线程与网络线程里被构造，几何又会被渲染线程读取，
     * 缓存必须安全发布（内核自身不可变，所以只读发布即可，不需要加锁）。
     * <p>
     * 刻意<b>不给初始化器</b>（与 {@code RailPoseExtraMixin.fangsu$pose} 同一理由：
     * {@code Rail} 有 4 个构造器，{@code @Unique} 字段初始化器的搬运行为与构造器形态有关），
     * 用 {@code null} 表示「未构建」。
     */
    @Unique
    private volatile RailGeometryCore fangsu$geometryCore;

    /**
     * {@link #fangsu$geometryCore} 是按<b>哪一个</b>姿态实例构建的。
     * <p>
     * 有了它，「姿态变了但没调 {@code rebuildRailGeometry()}」这种漏调不会造成脏缓存：读取时
     * 比对姿态实例即可发现并重建。这正是 MTR4 用「重新 {@code new FangSuRailMath(...)}」达成的效果，
     * 只不过 MTR3 的几何字段不可回写，只能以「换缓存」表达。
     */
    @Unique
    private volatile RailPoseExtra fangsu$geometryCorePose;

    // ==================== 几何钩子 ====================

    /**
     * {@code private double getPositionY(double)} 是 {@code Rail} 里 y 的唯一来源。
     * <p>
     * {@code @Inject(at = RETURN, cancellable)} 而<b>不是</b> {@code @ModifyReturnValue}：本仓库可核实的
     * 编译类路径上（{@code dev.architectury:mixin-patched:0.8.5.12}）没有
     * {@code ModifyReturnValue} 注解（{@code jar tf} 已核实），而 {@code @Inject} +
     * {@code CallbackInfoReturnable} 是 {@code RailPoseExtraMixin} 已经在同一条目标上跑通的写法。
     * <p>
     * <b>无姿态时不调用 {@code setReturnValue}</b>：那才是真正的「原样放行」，与 MTR 的返回值逐位相同
     * （不是「先用内核算一遍、发现一样再返回」）。
     * <p>
     * {@code require = 0}：见类注释。钩子没注入时这里根本不会被调用，于是
     * {@link RailGeometrySource#wasKernelUsed()} 恒为 false，由
     * {@link #fangsu$warnIfHookNeverFired} 报出来。
     */
    @Inject(method = "getPositionY(D)D", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void fangsu$poseAwarePositionY(double value, CallbackInfoReturnable<Double> callbackInfo) {
        final RailPoseExtra pose = ((RailPoseExtraHolder) (Object) this).getFangSuPose();
        if (!RailGeometrySource.isActive(pose)) {
            // 无姿态 / 默认姿态：原样返回（内核完全不参与，逐位一致的结构性保证）
            return;
        }
        callbackInfo.setReturnValue(RailGeometrySource.getY(
                this, pose, value, callbackInfo.getReturnValueD(),
                railType.railSlopeStyle == RailType.RailSlopeStyle.CABLE
                        ? RailGeometryCore.SHAPE_CABLE
                        : RailGeometryCore.SHAPE_QUADRATIC));
    }

    // ==================== RailGeometryProvider ====================

    /**
     * 按需构建 / 复用内核。
     * <p>
     * <b>内核只负责竖直剖面与滚转抬升</b>，具体怎么组合由 {@link RailGeometrySource#getY} 决定：
     * CURVE 轨整体采用内核的 y（内核的抛物线分支与 MTR 逐位相同），CABLE 轨则叠加内核的
     * {@code rollLift}（内核无法表达两端等高的缆车台阶，理由见 {@code getY} 的说明）。
     * <p>
     * <b>水平几何是占位的</b>，见 {@link RailGeometrySource#stubAnchors}：本步骤只消费内核的
     * {@code getPositionY} / {@code rollLift}，两者都不读水平量，唯一进入它们的是<b>总长度</b>
     * （滚转剖面的相位）。{@link RailGeometrySource#build} 内部保留一道<b>逐位长度校验</b>作为护栏：
     * 一旦内核算出的长度与 MTR 存下来的长度不一致就整条回退（宁可不倾斜，也不能把超高放错位置）。
     */
    @Override
    public RailGeometryCore getFangSuRailGeometryCore() {
        final RailPoseExtra pose = ((RailPoseExtraHolder) (Object) this).getFangSuPose();
        if (!RailGeometrySource.isActive(pose)) {
            return null;
        }
        // 缓存比对<b>姿态实例</b>：姿态是不可变对象，换一次就是换实例，所以引用相等即「同一姿态」。
        // 这让「漏调 rebuildRailGeometry()」不再可能留下脏缓存。
        final RailGeometryCore cached = fangsu$geometryCore;
        if (cached != null && fangsu$geometryCorePose == pose) {
            return cached;
        }
        invalidateFangSuRailGeometry();
        final double length = getLength();
        final RailGeometryCore built = RailGeometrySource.build(
                length, yStart, yEnd,
                // 端点竖向平移必须进入内核锚点 y（= 方块坐标 + 节点平移），
                // 与 MTR4 的 FangSuRailMath 一致；水平平移不进入（P5-2 的水平锚点仍是占位）。
                pose.offsetY1, pose.offsetY2,
                railType.railSlopeStyle == RailType.RailSlopeStyle.CABLE
                        ? RailGeometryCore.SHAPE_CABLE
                        : RailGeometryCore.SHAPE_QUADRATIC,
                pose
        );
        if (built == null) {
            return null;
        }
        fangsu$geometryCorePose = pose;
        fangsu$geometryCore = built;
        // 正向诊断：内核第一次真的建成即打一条 INFO。P5-1 视觉上不可见，没有这条日志就无法从游戏
        // 日志里区分「几何已接管」与「钩子没生效」。
        RailGeometrySource.markFirstKernelUse();
        return built;
    }

    @Override
    public void invalidateFangSuRailGeometry() {
        fangsu$geometryCore = null;
        fangsu$geometryCorePose = null;
    }

    // ==================== RailSeamSource ====================

    /**
     * 两段圆弧接缝的归一化位置（{@code position1 → position2}）——逐轨道超高的中间控制点位置。
     * <p>
     * 直接读 MTR3 自己的几何字段：{@code |tEnd1 - tStart1|} 就是 {@code Rail.getPosition(double)}
     * 分段用的 {@code count1}，除以 {@code getLength()} 即得归一化位置。算术全部委托给
     * {@link RailTiltSupport#middleBreakpointFraction(double, double)}（普通类，便于探针直接验证），
     * 这里只负责把 {@code private final} 字段取出来。
     * <p>
     * <b>与 P5-1 内核无关</b>：内核的水平锚点是占位的（见 {@code RailGeometrySource#stubAnchors}），
     * 它的 {@code getLength1()/getLength2()} 在直线占位上恒退化，<b>不能</b>用来算接缝。
     * 真正的接缝只有 MTR3 自己的 {@code tStart/tEnd} 里有。
     */
    @Override
    public double getFangSuMiddleBreakpointFraction() {
        return RailTiltSupport.middleBreakpointFraction(Math.abs(tEnd1 - tStart1), Math.abs(tEnd2 - tStart2));
    }

    // ==================== RailPoseExtraHolder ====================

    /**
     * 姿态写入后<b>必须</b>让几何缓存失效，否则「改了超高但渲染没变」。
     * <p>
     * 这是 P5-0 预留的 {@code rebuildRailGeometry()} 钩子的落地实现：MTR3 的几何字段是
     * {@code private final}，无法回写，所以「重建」的正确语义就是<b>丢弃缓存 + 下次读取时按新姿态重建</b>
     * （对应 MTR4 上 {@code RailMixin} 重新 {@code new FangSuRailMath(...)}）。语义对调用方
     * （{@code RailPoseExtraHolder.apply}）完全一致。
     * <p>
     * 这里<b>刻意不立刻重建</b>：调用方可能连写多次姿态，立即重建会白算若干次；
     * 而 {@link #getFangSuRailGeometryCore()} 保证任何一次读取拿到的都与当前姿态一致。
     */
    @Override
    public void rebuildRailGeometry() {
        invalidateFangSuRailGeometry();
        fangsu$warnIfHookNeverFired();
    }

    /**
     * 「几何钩子从未触发」的一次性诊断。
     * <p>
     * 由 {@link #rebuildRailGeometry()} 触发（作者刚写下第一个非默认姿态），调用点必然在
     * 服务端主线程且早于任何渲染，因此若几何钩子活着，同一帧内内核就会先被调用，
     * {@link RailGeometrySource#warnIfHookNotAlive()} 便不会打印。
     */
    @Unique
    private void fangsu$warnIfHookNeverFired() {
        RailGeometrySource.warnIfHookNotAlive();
    }
}
