package com.fangsu.mtr.rail;

import com.fangsu.Main;
import com.fangsu.mappings.rail.RailGeometryCore;
import com.mojang.blaze3d.vertex.PoseStack;
import mtr.client.ClientData;
import mtr.data.Rail;
import mtr.mappings.UtilitiesClient;
import mtr.render.TrainRendererBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * MTR3 外轨超高（P5-6）的<b>车体 / 风挡滚转</b>实现。
 * <p>
 * <b>目标</b>：轨道（P5-1 中心线抬升 + P5-3 轨面滚转）已经会倾斜，但 MTR3 自己的列车
 * 仍是水平刚体，开上超高段时看起来是「轨道斜了、车没斜」。本类让<b>车体</b>与<b>风挡 / 挡板</b>
 * 绕各自的车身纵轴滚转同一角度。
 * <p>
 * <b>为什么不能给 {@code renderCar}/{@code renderConnection}/{@code renderBarrier} 加参数</b>：
 * 这三个方法是 {@link TrainRendererBase} 的 <b>public abstract</b> API，由第三方附加模组
 * （Metropolis / Joban / …）实现。改签名 = 破坏二进制兼容。因此滚转角必须<b>脱离签名</b>传递：
 * <ul>
 *   <li>{@code mtr.data.TrainClient.simulateCar} 是每节车厢的<b>唯一</b>入口，它拿到的是
 *       <b>世界坐标</b>的 {@code carX/carY/carZ/carYaw/carPitch}、两端的 {@code prevCarX..prevCarPitch}
 *       以及本车厢实长 {@code realSpacing}。
 *       本类在它的 HEAD 把该车厢（以及上一节车厢）的滚转角与纵轴算好并按
 *       {@code (渲染器实例, 车厢序号)} 存起来；</li>
 *   <li>车体：{@code mtr.render.JonModelTrainRenderer.renderCar} HEAD 按车厢序号取出角度放进
 *       {@link #BODY_ROLL}，再由同一个方法里 {@code UtilitiesClient.rotateX} 的
 *       {@code @Redirect} 在模型链<b>之后</b>追加 {@code rotateZDegrees}；</li>
 *   <li>风挡 / 挡板：{@code simulateCar} 里那两个 {@code renderConnection}/{@code renderBarrier}
 *       调用被 {@code @Redirect}，处理器把 8 个角点绕<b>各自那一端</b>的纵轴旋转。</li>
 * </ul>
 * <p>
 * <b>为什么可以在这里拿到世界坐标</b>：{@code simulateCar} 传给渲染器的是
 * {@code carX - viewOffset}（乘车时是相机相对量），所以 {@code renderCar} 里的 x/y/z
 * <b>不能</b>直接用来做轨道匹配（这正是 MTR4 的 BUG 1）。{@code simulateCar} 收到的则是纯粹的
 * 世界坐标，因此匹配一律在本类里、用 {@code simulateCar} 的量完成。
 * <p>
 * <b>车厢在轨道上的定位（不依赖任何 MTR 私有状态）</b>：MTR3 的 {@code Train.calculateCar} 把一节车
 * 的两端取为 {@code positions[i]}/{@code positions[i+1]}（两者都取自
 * {@code Rail.getPosition(arcLength)}），车厢原点取它们的<b>弦中点</b>并上抬 1 格
 * （{@code y = 平均 + 1}），偏航/俯仰由两端连线得到。因此
 * <pre>
 *   车身局部 +Z 在世界系的方向 a = Ry(π+yaw)·Rx(π+pitch)·(0,0,1)
 *                                 = (sin yaw·cos pitch, sin pitch, cos yaw·cos pitch)
 *   车厢两端 = 车厢原点 ± (realSpacing/2)·a      ← 数学上严格等于 positions[i] / positions[i+1]
 * </pre>
 * 这两个端点<b>正好落在轨道中心线上</b>，于是匹配可以做得很紧（见 {@link #MATCH_HORIZONTAL}），
 * 并且天然复刻了 MTR4「按两个转向架处滚转取平均」的平滑效果：车头一端进入超高段时车体就开始倾斜，
 * 而不是等弦中点进入。若只匹配一端（另一端无超高），另一端贡献 0，取平均后与 MTR4 的行为一致。
 * <p>
 * <b>P5-6 修正：端点「落在中心线上」不等于「最近采样顶点就在中心线上」。</b>匹配用的是 MTR 自己的
 * {@code Rail.getPosition} 采样折线，旧实现只在<b>采样顶点</b>里取最近者，于是残差不是浮点舍入而是
 * <b>采样网格的半格</b>（步长 0.5 → 上界 0.25 格，实测最坏 0.2500 格，占掉 71% 的 0.35 水平容差），
 * 而且参数被量化到采样点、滚转角呈阶梯状；更糟的是「先按横向距离选最优、再单独过竖向门」，
 * 一旦那个横向最近者竖向对不上就直接返回 0，哪怕旁边还有一条三道门全过的候选。
 * 现在改为<b>点-线段投影 + 按投影比例插值参数与 y</b>，并在<b>三道门全部通过的候选里取横向最近者</b>，
 * 残差降到折线弓高（实测最坏 2.1e-3 格）。证据见 {@code .tmp_p5_3_probe/RailTrainRollDropoutProbe.java}。
 * <p>
 * <b>抖动的可观测性</b>：匹配失败与「本来就没有超高」在游戏里完全同形，因此
 * {@link #reportNearMiss} 在「上一帧还在倾斜、本帧突然归零、且确实有一端近失」时打一条点名
 * 端点 / 门 / 残差的 WARN（整个会话一次）。这是本类对「这一类问题」的定点诊断。
 * <p>
 * <b>内核来源与 P5-1/P5-3 完全同一个</b>：候选轨道一律经
 * {@link RailTiltRenderHelper#frameFor(Rail)} 取快照，它内部走
 * {@link RailGeometryProvider#getFangSuRailGeometryCore()}（与 {@code getPositionY} 钩子同一个实例）。
 * 滚转角表达式只用 {@link RailTiltRenderHelper#railFrameAngle(double, boolean)} —— <b>与轨面共用同一行代码</b>，
 * 因此「轨面往哪边斜、车体就往哪边斜」是构造性保证，而不是两处各写一遍。
 * <p>
 * <b>符号推导（MTR3 真实变换链）</b>：{@code JonModelTrainRenderer.renderCar} 的链是
 * {@code translate(x,y,z) → rotateY(π+yaw) → rotateX(π+hasPitch?pitch:0)}，
 * 因此 {@code mulPose} 一个绕局部 {@code +Z} 的旋转就是「绕车身纵轴滚转」。
 * 设轨面在参数处的旋转角为 {@code θ_r = railFrameAngle(roll, 参数方向==position1→position2)}
 * （P5-3 用它绕<b>轨道参数方向</b> {@code t} 旋转横断面），并设
 * {@code align = (sin yaw, cos yaw)·(t.x, t.z)}：
 * <ul>
 *   <li>{@code align > 0}：车身 {@code +Z} 与 {@code t} 同向 ⟹ 绕局部 {@code +Z} 转 {@code θ_r}
 *       等价于绕 {@code t} 转 {@code θ_r} ⟹ {@code d = θ_r}；</li>
 *   <li>{@code align < 0}：两者反向 ⟹ 需要 {@code d = -θ_r}。</li>
 * </ul>
 * 合并即 {@code d = θ_r · sign(align)}，与 MTR4 的
 * {@code -deg(roll)·(forwardIsPosition1ToPosition2 ? 1 : -1)} 在
 * {@code θ_r = -roll}、{@code 参数方向 == position1→position2}（MTR3 恒成立）时<b>逐字等价</b>。
 * 数值一致性（车体抬升的一侧与轨面抬升的一侧相同）由
 * {@code .tmp_p5_3_probe/RailTrainRollProbe.java} 对真实的
 * {@code Rail.render} 角点与真实的 {@code Rail.getPosition} 折线双向断言。
 * <p>
 * <b>逐位无操作保证</b>：下列任一情况本类<b>不产生任何变换</b>，调用方按原参数转发：
 * 客户端没有任何「带滚转」的轨道、该点匹配不上（水平 / 竖向 / 平行度三道门）、内核给出的滚转为 0、
 * 两个端点都算得 0。此时车体少一次 {@code mulPose}、风挡沿用<b>原来那 8 个 Vec3 实例</b>，
 * 与 MTR 原生逐位相同。
 * <p>
 * <b>NTE 的关系</b>：NTE（{@code mtrsteamloco}）只接管<b>轨道带</b>（它 cancel 掉
 * {@code RenderTrains.renderRailStandard}），列车车体仍是 MTR 自己的 {@code JonModelTrainRenderer}，
 * {@code ClientData.RAILS} 也照常同步，所以<b>装了 NTE 时本步骤照样生效</b>（与 P5-3 的路径 A 相反）。
 */
public final class RailTrainRollHelper {

    /**
     * 车厢原点比轨道中心线高的格数：{@code Train.calculateCar} 里 {@code y = 平均 + 1}。
     * <p>
     * 竖向匹配时要从车厢端点的 y 里减掉它（以及 {@code transportMode.railOffset}），
     * 才能和轨道中心线的高度比较。
     */
    private static final double CAR_ORIGIN_ABOVE_RAIL = 1.0D;

    /**
     * 水平匹配容差（格）。
     * <p>
     * <b>残差到底有多大（P5-6 修正在此，实测数据见 {@code RailTrainRollDropoutProbe}）</b>：
     * 车厢两端确实<b>精确</b>落在轨道中心线上（重建是 {@code calculateCar} 的代数逆），但<b>旧实现</b>
     * 只在折线的<b>采样顶点</b>里取最近者、并且把该顶点的参数当成端点的参数，于是横向残差不是
     * 浮点舍入，而是<b>「端点落在两个采样点之间」的弦长</b> —— 上界恰好是
     * {@code SAMPLE_STEP / 2 = 0.25} 格（实测最坏 0.2500，曲线 R=100…15 全部 0.233–0.250，
     * 即 71% 的容差被采样网格吃掉，只剩 0.10 格余量）。
     * <p>
     * 现在改为<b>端点往折线段上做点-线段投影</b>，并<b>按投影比例插值</b>取该处的 y 与参数，
     * 残差降到采样折线对真实圆弧的弓高（实测最坏 2.1e-3 格 @ R=15，直线 2.2e-7），
     * 于是同一道门有 <b>0.35 / 2.1e-3 ≈ 170 倍</b>余量，且参数不再随采样网格跳变
     * （旧实现的滚转角是阶梯状量化到 0.5 格弧长的，视觉上本身就是一种抖动）。
     * <p>
     * <b>为什么仍然是 0.35、不放大</b>：MTR 的平行股道中心线间距最小也是 1 格（1.0 m）量级，
     * 而候选<b>只有带滚转的轨道</b>（作者显式写过超高），再加上竖向门与平行度门，0.35 与邻线之间
     * 仍有 0.65 格余量。残差降到 1e-3 量级之后，<b>没有任何理由再动这个数字</b>。
     */
    private static final double MATCH_HORIZONTAL = 0.35D;

    /**
     * 「近失（near-miss）」诊断的搜索半径（格）：端点周围这个半径内存在带滚转的轨道、却三道门全没过时，
     * 才认为是一次<b>可疑</b>的匹配失败。它<b>只用于诊断，不参与匹配</b>。
     * <p>
     * 取 {@code 2 × MATCH_HORIZONTAL}：既要能看见「被水平门刚好挡掉」的情形，
     * 又要小到不会把几百米外的轨道算成「附近有超高」（那样诊断会永远在响）。
     */
    private static final double DIAGNOSTIC_NEAR_RADIUS = 2.0D * MATCH_HORIZONTAL;

    /**
     * 竖向匹配容差（格）。
     * <p>
     * 采样点 y 与车厢端点 y 都取自同一条轨道中心线（车厢端点的 y 减去
     * {@link #CAR_ORIGIN_ABOVE_RAIL} 与 {@code transportMode.railOffset} 之后），
     * 因此残差≈0。0.5 只用来容纳 {@code Mth.clamp} 端点夹取与跨轨道的极小差异，
     * 同时又足以排除「上跨 / 下穿的另一条轨道」。
     */
    private static final double MATCH_VERTICAL = 0.5D;

    /**
     * 车体纵轴与轨道切向的夹角的 |cos| 下限。
     * <p>
     * 排除横穿 / 斜穿的轨道，同时保证 {@code sign(align)} 有意义（接近垂直时符号没有物理含义）。
     */
    private static final double MATCH_PARALLEL = 0.9D;

    /** 轨道采样折线的目标步长（格）。折线用 MTR 自己的 {@code Rail.getPosition} 采样，只建一次。 */
    private static final double SAMPLE_STEP = 0.5D;

    /** 单条轨道的采样点数上限（超长轨道按比例放大步长，避免建表过大）。 */
    private static final int MAX_SAMPLES = 1024;

    /**
     * 候选表的最长存活时间（纳秒）。
     * <p>
     * 正常情况下每帧由 {@link #beginRenderFrame()} 置脏后重建（≤ 1 帧延迟）；这个 TTL 是
     * <b>帧头钩子失效时的兜底</b>：即使那个 {@code require = 0} 的钩子没挂上，候选表也不会永远陈旧。
     */
    private static final long CANDIDATE_TTL_NANOS = 100_000_000L;

    /** 一次性诊断的观察窗口（帧数 / 车厢数取大者），到点后仍未触发的钩子会被报出来。 */
    private static final int HOOK_PROBE_SAMPLES = 200;

    // ==================== 状态 ====================

    /** 本帧参与匹配的「带滚转」轨道（快照 + 采样折线）。 */
    private static final List<Candidate> FRAME_CANDIDATES = new ArrayList<>();
    private static boolean candidatesDirty = true;
    private static long lastCandidateBuildNanos = 0L;
    private static boolean probeCandidates = false;

    /** 轨道 → 采样折线。{@code Rail} 没有覆写 {@code equals/hashCode}，所以这里就是按实例缓存。 */
    private static final Map<Rail, Polyline> POLYLINES = new WeakHashMap<>();

    /**
     * {@code 渲染器实例 → 每节车厢的 [滚转角(度), 纵轴x, 纵轴y, 纵轴z, yaw, pitch]}。
     * <p>
     * 键用渲染器实例而不是 {@code TrainClient}：{@code renderCar} 的处理器只能拿到渲染器，
     * 而 MTR 自己的 {@code JonModelTrainRenderer.createTrainInstance} 为<b>每列车</b>造一个新实例。
     * 若某个第三方渲染器在多列车之间共享实例，{@link #beginBodyRender} 的 yaw 一致性校验会把它判为
     * 「不可信」并退回 0（宁可不倾斜，也绝不把别的车的角度用上来）。
     * <p>
     * 用 {@code WeakHashMap}：列车被删除后渲染器实例可回收。只在渲染线程访问，不加锁。
     */
    private static final Map<TrainRendererBase, double[][]> CAR_STATES = new WeakHashMap<>();

    /**
     * {@code 渲染器实例 → 每节车厢的近失诊断}。与 {@link #CAR_STATES} 分开存放：
     * 状态表每帧被整体覆盖写，而诊断必须<b>跨帧</b>记住「上一帧的滚转角」，才能判定
     * 「本来在倾斜、这一帧突然归零」这种车体姿态抖动。
     */
    private static final Map<TrainRendererBase, CarDiagnostics[]> CAR_DIAGNOSTICS = new WeakHashMap<>();

    /** 当前正在 {@code simulateCar} 的车厢（供风挡 / 挡板重定向取用）；{@code simulateCar} RETURN 时清除。 */
    private static final ThreadLocal<CurrentCar> CURRENT_CAR = new ThreadLocal<>();

    /** {@code renderCar} HEAD 放进来的本车厢滚转角（度），由同一次调用的 {@code rotateX} 重定向消费。 */
    private static final ThreadLocal<Double> BODY_ROLL = new ThreadLocal<>();

    // ==================== 匹配结果的拒绝原因 ====================

    /** 水平门拒绝（端点周围存在带滚转的轨道，但横向距离超过 {@link #MATCH_HORIZONTAL}）。 */
    private static final int GATE_HORIZONTAL = 0;
    /** 竖向门拒绝（横向贴上了，但高度差超过 {@link #MATCH_VERTICAL}）。 */
    private static final int GATE_VERTICAL = 1;
    /** 车身纵轴退化（长度为零 / 非有限）—— 数学上无法定义滚转轴。 */
    private static final int GATE_AXIS = 2;

    /** 近失诊断里「哪道门拒绝的」的中文名，下标就是上面的 GATE_*。 */
    private static final String[] GATE_NAMES = {"水平", "竖向", "纵轴退化"};

    // ==================== 一次性诊断 ====================
    //
    // 与 P5-3 同一条纪律：本步骤每一个钩子都是 require = 0，失效只会「静默不倾斜」，
    // 而那与「本来就没有超高」在游戏里无法区分。所以每个钩子都要有一次性日志。

    private static volatile boolean frameHookAlive = false;
    private static volatile boolean captureHookAlive = false;
    private static volatile boolean bodyBeginHookAlive = false;
    private static volatile boolean bodyRotateHookAlive = false;
    private static volatile boolean connectionHookAlive = false;

    /** 帧数（{@code RenderTrains.render} 的 HEAD 钩子推进，一次性诊断的慢时钟）。 */
    private static volatile int frameSamples = 0;
    /** 车厢数（{@code simulateCar} 的 HEAD 钩子推进）。 */
    private static volatile int captureSamples = 0;
    /** 风挡 / 挡板重定向被调用的次数。 */
    private static volatile int connectionSamples = 0;
    /** 匹配到「带滚转轨道」的次数（>= 0；滚转角在该点可能恰为 0）。 */
    private static volatile int railHitSamples = 0;
    /** 解析出<b>非零</b>车体滚转角的次数。 */
    private static volatile int matchedSamples = 0;
    /** 真正往矩阵上追加了滚转的次数（> 0 即「已生效」）。 */
    private static volatile int tiltAppliedSamples = 0;
    /** 累计的「近失」端点次数（附近有带滚转的轨道、但三道门全没过）。 */
    private static volatile int nearMissSamples = 0;
    /** 累计的「本来在倾斜、这一帧突然归零」次数 —— 也就是用户报告的抖动。 */
    private static volatile int rollDropoutSamples = 0;

    /** 本会话是否见过「客户端确实存在带滚转的轨道」——这是所有告警的证据前提。 */
    private static volatile boolean rolledRailEvidence = false;

    private static boolean firstTiltLogged = false;
    private static boolean warnedFrame = false;
    private static boolean warnedCapture = false;
    private static boolean warnedBodyBegin = false;
    private static boolean warnedBodyRotate = false;
    private static boolean warnedConnection = false;
    private static boolean warnedNeverApplied = false;
    private static boolean warnedNoMatch = false;
    private static boolean warnedRollDropout = false;

    private RailTrainRollHelper() {
    }

    // ==================== 钩子 1：每帧边界 ====================

    /**
     * {@code mtr.render.RenderTrains.render(EntitySeat,float,PoseStack,MultiBufferSource)} 的
     * {@code @Inject(at = HEAD)}：开启新的渲染帧。
     * <p>
     * 只做两件事：把候选表置脏（下一帧重建，保证轨道被编辑后最多一帧就同步）并推进诊断时钟。
     * 车体 / 风挡的逐车厢状态<b>不</b>在这里清空：它们在每节车的 {@code simulateCar} 里整体覆盖写，
     * 而同一帧更晚执行的半透明重绘（{@code TrainClient.renderTranslucent}）需要沿用本帧的值。
     */
    public static void beginRenderFrame() {
        frameHookAlive = true;
        frameSamples++;
        candidatesDirty = true;
        reportMissingHooks();
    }

    // ==================== 钩子 2：每节车厢 ====================

    /**
     * {@code mtr.data.TrainClient.simulateCar(...)} 的 {@code @Inject(at = HEAD)}：
     * 用<b>世界坐标</b>算出这节车厢（以及它前面那节）的滚转角与车身纵轴。
     * <p>
     * 参数顺序与 {@code simulateCar} 完全一致（描述符
     * {@code (Lnet/minecraft/world/level/Level;IFDDDFFDDDDFFZZD)V}），由 mixin 处理器挑选并传入。
     *
     * @param renderer   本列车的渲染器（{@code trainRenderer}），作为状态表的键
     * @param carIndex   车厢序号（{@code ridingCar}）
     * @param carX       车厢原点的世界 X（= 两端平均）
     * @param carY       车厢原点的世界 Y（= 两端平均 + 1）
     * @param carZ       车厢原点的世界 Z
     * @param carYaw     车厢偏航（{@code atan2(末端-首端)}）
     * @param carPitch   车厢俯仰（两端高差 / 实长）
     * @param realSpacing 本车厢的<b>实长</b>（两端点距离），用于重建两个端点
     * @param railOffset {@code transportMode.railOffset}（缆车为 -6，其余为 0），竖向匹配时扣除
     */
    public static void captureCar(
            TrainRendererBase renderer, int carIndex,
            double carX, double carY, double carZ, float carYaw, float carPitch,
            double realSpacing, int railOffset
    ) {
        captureHookAlive = true;
        captureSamples++;
        if (renderer == null || carIndex < 0) {
            reportMissingHooks();
            return;
        }

        // 车身局部 +Z 在世界系的方向（= Ry(π+yaw)·Rx(π+pitch)·(0,0,1)），见类注释。
        double axisX = Math.sin(carYaw) * Math.cos(carPitch);
        double axisY = Math.sin(carPitch);
        double axisZ = Math.cos(carYaw) * Math.cos(carPitch);
        final double axisLength = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
        final double half = realSpacing * 0.5D;

        // 逐车厢诊断（近失 / 突然归零）。与状态表分开存放：状态表每帧被整体覆盖写，
        // 诊断需要跨帧记住「上一帧的滚转角」，两者的生命周期不同。
        final CarDiagnostics diagnostics = carDiagnostics(renderer, carIndex);
        final EndDiagnostics endA = diagnostics.end0;
        final EndDiagnostics endB = diagnostics.end1;
        endA.reset();
        endB.reset();

        final double[] state = carState(renderer, carIndex);
        final double previousRoll = state[0];
        final double roll;
        if (!Double.isFinite(axisLength) || axisLength < 1.0E-9D || !Double.isFinite(half) || half <= 0.0D) {
            // 退化（车厢长度为 0 / 姿态非有限）：退回「弦中点单点采样」，
            // 局部 +Z 仍按偏航给出水平方向，绝不产生 NaN。
            axisX = Math.sin(carYaw);
            axisY = 0.0D;
            axisZ = Math.cos(carYaw);
            roll = rollDegreesAtPoint(carX, carY, carZ, axisX, axisZ, railOffset, endA);
        } else {
            axisX /= axisLength;
            axisY /= axisLength;
            axisZ /= axisLength;
            // 两端点数学上严格等于 positions[i] / positions[i+1]（= 落在中心线上的那两个点）
            final double rollA = rollDegreesAtPoint(
                    carX + half * axisX, carY + half * axisY, carZ + half * axisZ, axisX, axisZ, railOffset, endA);
            final double rollB = rollDegreesAtPoint(
                    carX - half * axisX, carY - half * axisY, carZ - half * axisZ, axisX, axisZ, railOffset, endB);
            roll = (rollA + rollB) * 0.5D;
        }
        diagnostics.previousRoll = Double.isFinite(previousRoll) ? previousRoll : 0.0D;
        diagnostics.roll = Double.isFinite(roll) ? roll : 0.0D;

        state[0] = diagnostics.roll;
        state[1] = axisX;
        state[2] = axisY;
        state[3] = axisZ;
        state[4] = carYaw;
        state[5] = carPitch;
        if (state[0] != 0.0D) {
            matchedSamples++;
        }
        reportNearMiss(diagnostics, renderer, carIndex);

        CURRENT_CAR.set(new CurrentCar(renderer, carIndex));
        reportMissingHooks();
    }

    /**
     * {@code simulateCar} 的 {@code @Inject(at = RETURN)}：结束本车厢的同步窗口。
     * <p>
     * 它失效本身<b>无害</b>（{@link #currentCarState()} 每次 {@code captureCar} 都会被整体覆盖写），
     * 但会让 {@link #CURRENT_CAR} 在两次调用之间保留残值；由于风挡 / 挡板的两处重定向都在
     * {@code simulateCar} 内部、晚于 HEAD，所以实际行为不受影响。因此这里不为它单独发告警，
     * 只在探针里断言它清得干净。
     */
    public static void endCar() {
        CURRENT_CAR.remove();
        BODY_ROLL.remove();
    }

    // ==================== 钩子 3：车体 ====================

    /**
     * {@code mtr.render.JonModelTrainRenderer.renderCar(IDDDFFZZ)V} 的 {@code @Inject(at = HEAD)}：
     * 按车厢序号取出本车厢的滚转角，放进 {@link #BODY_ROLL} 等同一个方法里的重定向消费。
     * <p>
     * <b>为什么不在 {@code renderCar} 里现算</b>：{@code renderCar} 收到的 x/y/z 是
     * {@code carX - viewOffset}（乘车时是相机相对量），拿去做轨道匹配必然失败（MTR4 的 BUG 1）。
     * 世界坐标只有 {@code simulateCar} 有。
     * <p>
     * <b>为什么要按序号查表而不是用「当前车厢」单槽</b>：{@code TrainClient.simulateCar} 会把
     * 每一节车体的绘制再排一次队（{@code trainTranslucentRenders}），那批重绘在本帧所有列车
     * {@code simulateTrain} 跑完之后才执行 —— 单槽那时只剩最后一节车的数据，半透明部件会被
     * 套上别的车的角度。按 {@code (渲染器实例, 车厢序号)} 查表则两趟都取到各自的车厢。
     * <p>
     * {@code yaw} 用于一致性校验：同一格状态里的 yaw 与本次 {@code renderCar} 收到的 yaw 不同，
     * 说明这个渲染器实例被多列车共享，此时退回 0（安全失败）。
     * <p>
     * <b>为什么这个方法是 {@code public} 而不是包私有</b>：装饰器渲染器
     * {@code com.fangsu.train.FunctionalTrainRenderer}（本模组的「功能车」渲染器，包了一层
     * {@code TrainRendererBase}）需要在自己的 {@code renderCar} HEAD 里调用它 ——
     * {@code TrainClient.trainRenderer} 是<b>装饰器</b>实例，而 {@link #captureCar} 是按这个实例登记状态的，
     * 真正画车体的却是被包住的那一个 {@code JonModelTrainRenderer}。不补这一处调用，
     * 功能车的查找键就对不上，车体<b>完全不会倾斜</b>。见
     * {@code mixin/FunctionalTrainRendererRollMixin.java}。
     */
    public static void beginBodyRender(TrainRendererBase renderer, int carIndex, float yaw) {
        bodyBeginHookAlive = true;
        double roll = 0.0D;
        final double[] state = stateOf(renderer, carIndex);
        if (state != null && state[0] != 0.0D && Math.abs((float) state[4] - yaw) < 1.0E-3F) {
            roll = state[0];
        }
        BODY_ROLL.set(roll);
        reportMissingHooks();
    }

    /**
     * 丢弃当前线程尚未被消费的车体滚转角。
     * <p>
     * 正常路径上 {@link #applyBodyRoll} 会把它消费掉（取走 + 清除），因此本方法多数时候是空操作。
     * 它存在是为了让<b>自绘车体的渲染器</b>（{@code com.fangsu.train.FunctionalTrainRenderer}）
     * 在 {@code finally} 里兜底：万一 {@code rotateX} 的重定向没生效（例如本模组的 mixin 被别的模组
     * 挤掉），{@link #beginBodyRender} 写下的值就会留在 ThreadLocal 里污染同线程后续的渲染。
     * 清理在任何情况下都无害，且不产生任何矩阵运算。
     */
    public static void clearBodyRoll() {
        BODY_ROLL.remove();
    }

    /**
     * {@code UtilitiesClient.rotateX(PoseStack,float)} 在 {@code renderCar} 里的 {@code @Redirect}
     * （{@code ordinal = 0}，即模型链的 {@code rotateX(π + pitch)}）：
     * 先原样执行原调用，再绕<b>局部 +Z</b>（车身纵轴）追加滚转。
     * <p>
     * <b>为什么钉在这个调用点上</b>：{@code renderCar} 里 {@code rotateX} 恰好两处
     * （{@code javap -p -c}：偏移 210 = 模型链、833 = 缆车抓手分支），{@code ordinal = 0}
     * 精确落在模型链上，于是整个车体（模型 + 转向架）都在同一个已滚转的局部系里绘制。
     * <p>
     * <b>无滚转时一个矩阵运算都不做</b>：{@link #BODY_ROLL} 为 0 / null 时直接返回，
     * 与 MTR 原生逐位一致。
     */
    public static void applyBodyRoll(PoseStack matrices) {
        bodyRotateHookAlive = true;
        final Double roll = BODY_ROLL.get();
        BODY_ROLL.remove();
        if (matrices == null || roll == null || !Double.isFinite(roll) || roll == 0.0D) {
            return;
        }
        UtilitiesClient.rotateZDegrees(matrices, roll.floatValue());
        tiltAppliedSamples++;
        if (!firstTiltLogged) {
            firstTiltLogged = true;
            Main.LOGGER.info("[P5-6] 列车车体滚转已生效：车体局部 +Z（车身纵轴）已按内核滚转角旋转 {}°。"
                    + "滚转来源与 P5-3 的轨面共用同一个内核实例与同一个符号表达式"
                    + "（装了 NTE 时轨面走 NTE 自己的路径，但车体仍是本路径）", roll);
        }
    }

    // ==================== 钩子 4：风挡 / 挡板 ====================

    /**
     * {@code simulateCar} 里 {@code renderConnection} / {@code renderBarrier} 调用的 {@code @Redirect}：
     * 把两端的 8 个角点绕<b>各自那一端</b>的车身纵轴旋转。
     * <p>
     * <b>角点为什么必须逐端旋转</b>：MTR3 的这两个方法<b>没有</b> MTR4 那种
     * {@code oscillationAmount}（摆动量）参数 —— 风挡 / 挡板的几何完全由 8 个世界坐标角点决定
     * （{@code simulateCar} 里用 {@code new Vec3(局部).xRot(pitch).yRot(yaw).add(车厢原点)} 拼出来）。
     * 所以 MTR4「把滚转角加到摆动量上」在 MTR3 的等价物就是<b>把角点绕车体纵轴转过去</b>。
     * <p>
     * <b>轴心为什么取四角点质心</b>：那一端的四个角点是
     * {@code (±xStart, {SMALL_OFFSET, CONNECTION_HEIGHT}, zStart)}（{@code simulateCar} 源码），
     * 横向成对、纵向同一个 z，因此质心恰好落在<b>车厢纵轴</b>上；绕「过质心、平行于纵轴」的轴
     * 旋转就等价于绕车厢纵轴旋转，不需要任何额外平移。
     * <p>
     * <b>两端的角点各自用各自车厢的轴与角</b>：前后两节车的超高可能不同（过渡段），
     * 逐端旋转才能让风挡既贴住前一节车、又贴住后一节车，不出现撕裂。
     *
     * @return 8 个新角点（前 4 个 = 前一节车端，后 4 个 = 本节车端）；<b>不需要旋转时返回 {@code null}</b>，
     *         调用方按<b>原来的 8 个实例</b>转发，与 MTR 原生逐位一致。
     */
    public static Vec3[] rolledConnectionPoints(
            Vec3 prevPos1, Vec3 prevPos2, Vec3 prevPos3, Vec3 prevPos4,
            Vec3 thisPos1, Vec3 thisPos2, Vec3 thisPos3, Vec3 thisPos4
    ) {
        connectionHookAlive = true;
        connectionSamples++;
        final CurrentCar current = CURRENT_CAR.get();
        if (current == null) {
            return null;
        }
        final double[] thisState = stateOf(current.renderer, current.carIndex);
        final double[] prevState = current.carIndex > 0
                ? stateOf(current.renderer, current.carIndex - 1) : null;
        final double rollThis = thisState == null ? 0.0D : thisState[0];
        final double rollPrev = prevState == null ? 0.0D : prevState[0];
        if (rollThis == 0.0D && rollPrev == 0.0D) {
            return null;
        }

        final Vec3[] out = new Vec3[8];
        if (rollPrev != 0.0D && prevState != null) {
            rotateQuad(out, 0, prevPos1, prevPos2, prevPos3, prevPos4, prevState);
        } else {
            out[0] = prevPos1;
            out[1] = prevPos2;
            out[2] = prevPos3;
            out[3] = prevPos4;
        }
        if (rollThis != 0.0D && thisState != null) {
            rotateQuad(out, 4, thisPos1, thisPos2, thisPos3, thisPos4, thisState);
        } else {
            out[4] = thisPos1;
            out[5] = thisPos2;
            out[6] = thisPos3;
            out[7] = thisPos4;
        }
        reportMissingHooks();
        return out;
    }

    /** 把一端（4 个角点）绕该端车厢纵轴滚转，结果写入 {@code out[offset..offset+3]}。 */
    private static void rotateQuad(
            Vec3[] out, int offset,
            Vec3 pos1, Vec3 pos2, Vec3 pos3, Vec3 pos4,
            double[] state
    ) {
        final double pivotX = (pos1.x + pos2.x + pos3.x + pos4.x) * 0.25D;
        final double pivotY = (pos1.y + pos2.y + pos3.y + pos4.y) * 0.25D;
        final double pivotZ = (pos1.z + pos2.z + pos3.z + pos4.z) * 0.25D;
        final double degrees = state[0];
        final double angle = Math.toRadians(degrees);
        final double cos = Math.cos(angle);
        final double sin = Math.sin(angle);
        final double axisX = state[1];
        final double axisY = state[2];
        final double axisZ = state[3];
        out[offset] = rotateAboutAxis(pos1, pivotX, pivotY, pivotZ, axisX, axisY, axisZ, cos, sin);
        out[offset + 1] = rotateAboutAxis(pos2, pivotX, pivotY, pivotZ, axisX, axisY, axisZ, cos, sin);
        out[offset + 2] = rotateAboutAxis(pos3, pivotX, pivotY, pivotZ, axisX, axisY, axisZ, cos, sin);
        out[offset + 3] = rotateAboutAxis(pos4, pivotX, pivotY, pivotZ, axisX, axisY, axisZ, cos, sin);
    }

    /**
     * 绕「过 {@code (pivot)}、方向为<b>单位</b>向量 {@code a}」的轴做右手旋转（Rodrigues 公式）。
     * <p>
     * 与 {@code RailTiltRenderHelper.rotateAboutAxis}（P5-3 的轨面版本）是同一个公式，
     * 区别只在于这里的轴有 y 分量（车身有俯仰）。轴必须是单位向量 —— 它由
     * {@link #captureCar} 归一化后写入状态表。
     */
    private static Vec3 rotateAboutAxis(
            Vec3 point,
            double pivotX, double pivotY, double pivotZ,
            double axisX, double axisY, double axisZ,
            double cos, double sin
    ) {
        final double vx = point.x - pivotX;
        final double vy = point.y - pivotY;
        final double vz = point.z - pivotZ;
        final double dot = axisX * vx + axisY * vy + axisZ * vz;
        final double crossX = axisY * vz - axisZ * vy;
        final double crossY = axisZ * vx - axisX * vz;
        final double crossZ = axisX * vy - axisY * vx;
        return new Vec3(
                pivotX + vx * cos + crossX * sin + axisX * dot * (1.0D - cos),
                pivotY + vy * cos + crossY * sin + axisY * dot * (1.0D - cos),
                pivotZ + vz * cos + crossZ * sin + axisZ * dot * (1.0D - cos)
        );
    }

    // ==================== 匹配 ====================

    /**
     * 某个世界点处应当施加的车体滚转角（度）；匹配不到返回 {@code 0}。
     * <p>
     * 正值 = 绕车身局部 {@code +Z} 的右手旋转角（度），即直接交给
     * {@code UtilitiesClient.rotateZDegrees} 的值。
     * <p>
     * <b>P5-6 修正（车体姿态抖动）</b>：旧实现有两个会「突然不倾斜」的结构性缺陷，本方法现在都修掉了：
     * <ol>
     *   <li><b>只在采样顶点里找最近者</b>：端点落在两个采样点之间时，横向残差 = 到折线的距离，
     *       上界是步长的一半（0.25 格），也就是 71% 的水平容差被采样网格吃掉；而且<b>参数被量化</b>
     *       到最近的采样点（最多 0.25 格的弧长误差），滚转角因此是阶梯状的。现在改为
     *       <b>点-线段投影</b>并<b>按投影比例插值</b> y 与参数，残差降到折线的弓高（实测 ≈1e-3 格），
     *       参数也是连续的。</li>
     *   <li><b>先按横向距离选「最优」、再单独过竖向门</b>：一旦那个横向最近者（可能是节点处的另一条
     *       轨道、或相邻股道）竖向对不上就直接返回 0 —— 即使旁边还有一条<b>三道门全过</b>的候选。
     *       现在改成「<b>三道门全部通过的候选里取横向最近者</b>」，这类失败从结构上消失。</li>
     * </ol>
     * 三道门：
     * <ol>
     *   <li><b>水平</b>：到采样折线的水平距离 &lt; {@link #MATCH_HORIZONTAL}；</li>
     *   <li><b>竖向</b>：该处插值出的轨道高度与「车厢端点 y − {@link #CAR_ORIGIN_ABOVE_RAIL}
     *       − {@code railOffset}」之差 &lt; {@link #MATCH_VERTICAL}（上跨 / 下穿的轨道一律出局）；</li>
     *   <li><b>平行</b>：车身纵轴的水平投影与该处轨道切向（= 参数增大方向）的 |cos| ≥
     *       {@link #MATCH_PARALLEL}。</li>
     * </ol>
     * 非有限量一律按「匹配不上」处理（MTR4 的 D-B 教训：NaN 的每次比较都是 false，
     * 会穿透 &lt; 形式的守卫）。
     *
     * @param axisX 车身局部 +Z 的<b>水平</b> x 分量（不必归一，方法内归一）
     * @param axisZ 车身局部 +Z 的水平 z 分量
     * @param railOffset {@code transportMode.railOffset}（缆车为 -6，其余为 0）
     * @param diagnostics 本次端点的近失诊断出口（不可为 {@code null}；只在失败帧写）
     */
    private static double rollDegreesAtPoint(
            double x, double y, double z, double axisX, double axisZ, int railOffset,
            EndDiagnostics diagnostics
    ) {
        final double axisLength = Math.sqrt(axisX * axisX + axisZ * axisZ);
        if (!Double.isFinite(axisLength) || axisLength < 1.0E-9D
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            diagnostics.railNearby = true;
            diagnostics.failedGate = GATE_AXIS;
            return 0.0D;
        }
        axisX /= axisLength;
        axisZ /= axisLength;

        final List<Candidate> candidates = frameCandidates();
        if (candidates.isEmpty()) {
            return 0.0D;
        }

        final double targetY = y - CAR_ORIGIN_ABOVE_RAIL - railOffset;
        Candidate best = null;
        double bestDistance = Double.MAX_VALUE;
        double bestParameter = 0.0D;
        double bestAlignment = 0.0D;
        boolean rejectedVertical = false;

        for (final Candidate candidate : candidates) {
            final Polyline polyline = candidate.polyline;
            // 包围盒先剪枝：绝大多数候选连一次扫折线都不需要。
            // 用「诊断半径」而不是「匹配容差」做剪枝，好让近失诊断还能看见被水平门挡掉的情形。
            if (x < polyline.minX - DIAGNOSTIC_NEAR_RADIUS || x > polyline.maxX + DIAGNOSTIC_NEAR_RADIUS
                    || z < polyline.minZ - DIAGNOSTIC_NEAR_RADIUS || z > polyline.maxZ + DIAGNOSTIC_NEAR_RADIUS) {
                continue;
            }
            final int segments = polyline.parameters.length - 1;
            for (int i = 0; i < segments; i++) {
                final double segmentX = polyline.xs[i + 1] - polyline.xs[i];
                final double segmentZ = polyline.zs[i + 1] - polyline.zs[i];
                final double segmentLengthSquared = segmentX * segmentX + segmentZ * segmentZ;
                double ratio = 0.0D;
                if (segmentLengthSquared > 1.0E-18D) {
                    ratio = ((x - polyline.xs[i]) * segmentX + (z - polyline.zs[i]) * segmentZ)
                            / segmentLengthSquared;
                    ratio = ratio < 0.0D ? 0.0D : (ratio > 1.0D ? 1.0D : ratio);
                }
                final double projectedX = polyline.xs[i] + ratio * segmentX;
                final double projectedZ = polyline.zs[i] + ratio * segmentZ;
                final double dx = projectedX - x;
                final double dz = projectedZ - z;
                final double distance = Math.sqrt(dx * dx + dz * dz);

                // 近失诊断的「附近确实有带滚转的轨道」判据（只在后面真的失败时才会被读到）
                if (distance <= DIAGNOSTIC_NEAR_RADIUS) {
                    diagnostics.railNearby = true;
                    if (distance < diagnostics.bestHorizontal) {
                        diagnostics.bestHorizontal = distance;
                    }
                }
                if (distance > MATCH_HORIZONTAL) {
                    continue;
                }
                // 该处的轨道高度：两个采样点的 y 按同一个投影比例插值（不是取最近顶点）
                final double railY = polyline.ys[i] + ratio * (polyline.ys[i + 1] - polyline.ys[i]);
                if (!Double.isFinite(railY) || Math.abs(railY - targetY) > MATCH_VERTICAL) {
                    rejectedVertical = true;
                    continue;
                }
                // 切向 = 参数增大方向（折线的采样顺序就是 MTR 的弧长顺序）
                final int previous = Math.max(0, i - 1);
                final int next = Math.min(segments, i + 2);
                double tangentX = polyline.xs[next] - polyline.xs[previous];
                double tangentZ = polyline.zs[next] - polyline.zs[previous];
                final double tangentLength = Math.sqrt(tangentX * tangentX + tangentZ * tangentZ);
                if (!Double.isFinite(tangentLength) || tangentLength < 1.0E-9D) {
                    continue;
                }
                tangentX /= tangentLength;
                tangentZ /= tangentLength;
                final double alignment = axisX * tangentX + axisZ * tangentZ;
                if (!Double.isFinite(alignment) || Math.abs(alignment) < MATCH_PARALLEL) {
                    continue;
                }
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                    bestParameter = polyline.parameters[i]
                            + ratio * (polyline.parameters[i + 1] - polyline.parameters[i]);
                    bestAlignment = alignment;
                }
            }
        }
        if (best == null) {
            // 全部候选都被挡住：记录是哪道门挡的，供一次性近失告警使用。
            // 竖向门优先报：它意味着「横向确实贴上了、只是高度不对」，信息量最大。
            diagnostics.failedGate = rejectedVertical ? GATE_VERTICAL : GATE_HORIZONTAL;
            return 0.0D;
        }

        final double roll = best.frame.core.getRollRadians(bestParameter);
        if (!Double.isFinite(roll) || roll == 0.0D) {
            // 匹配上了、但该处滚转恰好为 0（剖面端点 / 过零点）——这是<b>正常</b>的，不是抖动。
            // 旧实现也在这里返回 0，区别是它连 railHitSamples 都不加，于是无法与「匹配失败」区分。
            diagnostics.rollIsZero = true;
            return 0.0D;
        }
        railHitSamples++;
        // 与 P5-3 的轨面共用同一个符号表达式，再按「车身局部 +Z 是否与轨道参数同向」翻转（见类注释）。
        // ★ 单位：RailTiltRenderHelper.railFrameAngle 返回的是<b>弧度</b>（P5-3 直接喂 cos/sin），
        //   而 UtilitiesClient.rotateZDegrees 与笛卡尔 Rodrigues 要的是<b>度</b>，所以这里必须换算。
        //   MTR4 的对应表达式同样是 -Math.toDegrees(roll)·(…)。
        // ★ 符号用的是<b>选中的那一段</b>的 alignment（选候选时算出来的同一个值），
        //   不再事后按最近顶点重算一遍切向 —— 否则「选中」与「符号」可能出自两个不同的采样点。
        final double ribbonAngleDegrees = Math.toDegrees(
                RailTiltRenderHelper.railFrameAngle(roll, best.frame.parameterIsPosition1ToPosition2));
        return ribbonAngleDegrees * (bestAlignment >= 0.0D ? 1.0D : -1.0D);
    }

    /**
     * 本帧的候选轨道（带滚转的那些）。
     * <p>
     * <b>只收集带滚转的轨道</b>：{@link RailTiltRenderHelper#frameFor(Rail)} 已经做了
     * 「有姿态 + 有滚转 + 内核非退化 + 滚转剖面非零」的全部判断，因此<b>没有作者超高的世界里
     * 候选表恒空</b>，本步骤连一次距离计算都不会做。
     * <p>
     * 重建时机：{@link #beginRenderFrame()} 每帧置脏（轨道被编辑后最多一帧同步），
     * 外加 {@link #CANDIDATE_TTL_NANOS} 兜底。
     */
    private static List<Candidate> frameCandidates() {
        if (probeCandidates) {
            return FRAME_CANDIDATES;
        }
        final long now = System.nanoTime();
        if (!candidatesDirty && now - lastCandidateBuildNanos < CANDIDATE_TTL_NANOS) {
            return FRAME_CANDIDATES;
        }
        candidatesDirty = false;
        lastCandidateBuildNanos = now;
        FRAME_CANDIDATES.clear();
        for (final Map<BlockPos, Rail> inner : ClientData.RAILS.values()) {
            if (inner == null) {
                continue;
            }
            for (final Rail rail : inner.values()) {
                if (rail == null) {
                    continue;
                }
                final RailTiltRenderHelper.RollFrame frame = RailTiltRenderHelper.frameFor(rail);
                if (frame == null) {
                    continue;
                }
                final Polyline polyline = polylineFor(rail);
                if (polyline == null) {
                    continue;
                }
                FRAME_CANDIDATES.add(new Candidate(frame, polyline));
            }
        }
        if (!FRAME_CANDIDATES.isEmpty()) {
            rolledRailEvidence = true;
        }
        return FRAME_CANDIDATES;
    }

    /**
     * 轨道采样折线（<b>只建一次</b>，按 {@code Rail} 实例缓存）。
     * <p>
     * 采样用的是 MTR 自己的 {@link Rail#getPosition(double)}：它既是列车真正踩的那条曲线，
     * 又已经经过 P5-1 的 {@code getPositionY} 钩子（中心线抬升），所以竖向门比较的正是同一个量。
     * <b>不存在「折线与列车走的轨道不是同一条」的可能</b>。
     * <p>
     * 每一步记录 {@code (x, y, z, 弧长参数)}：参数直接喂给内核
     * {@link RailGeometryCore#getRollRadians(double)}，与 P5-3 的轨面用的是同一个参数化
     * （内核构建时做过逐位长度校验，长度必然等于 {@code Rail.getLength()}）。
     */
    private static Polyline polylineFor(Rail rail) {
        final Polyline cached = POLYLINES.get(rail);
        if (cached != null) {
            return cached;
        }
        final double length = rail.getLength();
        if (!Double.isFinite(length) || length <= 0.0D) {
            return null;
        }
        final double step = Math.max(SAMPLE_STEP, length / MAX_SAMPLES);
        final int count = Math.max(2, (int) Math.ceil(length / step) + 1);
        final double[] xs = new double[count];
        final double[] ys = new double[count];
        final double[] zs = new double[count];
        final double[] parameters = new double[count];
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            final double parameter = Math.min(length, i * step);
            final Vec3 position = rail.getPosition(parameter);
            if (position == null
                    || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
                return null;
            }
            xs[i] = position.x;
            ys[i] = position.y;
            zs[i] = position.z;
            parameters[i] = parameter;
            minX = Math.min(minX, position.x);
            maxX = Math.max(maxX, position.x);
            minZ = Math.min(minZ, position.z);
            maxZ = Math.max(maxZ, position.z);
        }
        final Polyline built = new Polyline(xs, ys, zs, parameters, minX, maxX, minZ, maxZ);
        POLYLINES.put(rail, built);
        return built;
    }

    // ==================== 状态表 ====================

    /** 查（不改动状态表）；不存在返回 {@code null}。 */
    private static double[] stateOf(TrainRendererBase renderer, int carIndex) {
        if (renderer == null || carIndex < 0) {
            return null;
        }
        final double[][] perCar = CAR_STATES.get(renderer);
        if (perCar == null || carIndex >= perCar.length) {
            return null;
        }
        return perCar[carIndex];
    }

    /** 查或建。车厢数变化时按需扩容；槽位一旦创建就复用（每帧整体覆盖写）。 */
    private static double[] carState(TrainRendererBase renderer, int carIndex) {
        double[][] perCar = CAR_STATES.get(renderer);
        if (perCar == null || carIndex >= perCar.length) {
            final int size = Math.max(8, carIndex + 1);
            final double[][] grown = new double[size][];
            if (perCar != null) {
                System.arraycopy(perCar, 0, grown, 0, perCar.length);
            }
            perCar = grown;
            CAR_STATES.put(renderer, perCar);
        }
        if (perCar[carIndex] == null) {
            perCar[carIndex] = new double[6];
        }
        return perCar[carIndex];
    }

    /** 查或建每节车厢的近失诊断槽（与状态表同构的扩容逻辑）。 */
    private static CarDiagnostics carDiagnostics(TrainRendererBase renderer, int carIndex) {
        CarDiagnostics[] perCar = CAR_DIAGNOSTICS.get(renderer);
        if (perCar == null || carIndex >= perCar.length) {
            final int size = Math.max(8, carIndex + 1);
            final CarDiagnostics[] grown = new CarDiagnostics[size];
            if (perCar != null) {
                System.arraycopy(perCar, 0, grown, 0, perCar.length);
            }
            perCar = grown;
            CAR_DIAGNOSTICS.put(renderer, perCar);
        }
        if (perCar[carIndex] == null) {
            perCar[carIndex] = new CarDiagnostics();
        }
        return perCar[carIndex];
    }

    // ==================== 一次性诊断：匹配近失 / 车体姿态突然归零 ====================

    /**
     * <b>P5-6 新增的一次性诊断（本项目「一-shot」约定）</b>：车体抖动（突然不倾斜、马上又回来）
     * 与「这里本来就没有超高」在游戏里无法区分，所以必须由代码把它指出来。
     * <p>
     * 判定条件（两个都要满足，宁可漏报也不误报）：
     * <ol>
     *   <li><b>本帧滚转突然归零</b>：上一个 {@code captureCar} 给这节车厢算出的滚转角非 0，
     *       而本帧是 0 —— 也就是用户看到的那一帧「突然不倾斜」；</li>
     *   <li><b>确实有一端是「近失」</b>：那一端的端点周围 {@link #DIAGNOSTIC_NEAR_RADIUS} 格内
     *       存在带滚转的轨道，但三道门全没过。</li>
     * </ol>
     * 告警里点名<b>哪一端、哪道门、残差多少、门限多少</b>，这是下一个人定位这一类问题的入口。
     * <p>
     * <b>为什么条件 2 不能只看「上一帧非零」</b>：滚转剖面本来就会过零（三点剖面的中点、
     * 两点剖面的起始端），那种归零是正常的；只有「附近有带滚转的轨道却匹配不上」才可疑。
     * <p>
     * <b>为什么不会刷屏</b>：{@link #warnedRollDropout} 保证整个会话只打一条。
     * 匹配成功、或者根本没有超高的世界里，这个方法一次日志都不会产生。
     */
    private static void reportNearMiss(CarDiagnostics diagnostics, TrainRendererBase renderer, int carIndex) {
        final boolean endAFailed = diagnostics.end0.railNearby && !diagnostics.end0.rollIsZero;
        final boolean endBFailed = diagnostics.end1.railNearby && !diagnostics.end1.rollIsZero;
        if (endAFailed) {
            nearMissSamples++;
        }
        if (endBFailed) {
            nearMissSamples++;
        }
        // 只统计「本来在倾斜 → 本帧为 0 → 且确实有一端近失」的情形
        if (diagnostics.roll != 0.0D || diagnostics.previousRoll == 0.0D || (!endAFailed && !endBFailed)) {
            return;
        }
        rollDropoutSamples++;
        if (warnedRollDropout) {
            return;
        }
        warnedRollDropout = true;
        final EndDiagnostics worst = worstEnd(diagnostics);
        Main.LOGGER.warn("[P5-6] 车体滚转在相邻两帧之间突然归零（用户看到的「姿态抖动」）："
                + "车厢 " + carIndex + " 上一帧滚转 " + diagnostics.previousRoll + "°，本帧因匹配失败退回 0°。"
                + "失败的一端=" + (worst == diagnostics.end0 ? "前端" : "后端")
                + "，被「" + GATE_NAMES[worst.failedGate] + "」门挡下："
                + "横向最近距离 " + worst.bestHorizontal + " 格（水平门限 " + MATCH_HORIZONTAL + "）"
                + "，竖向门限 " + MATCH_VERTICAL + "，平行度门限 " + MATCH_PARALLEL
                + "。若这条日志出现在正常行车中，请把上面的残差与门限一起报告 —— "
                + "匹配用的是「点-线段投影 + 按比例插值参数」，正常残差应在 1e-3 格量级。"
                + "累计近失 " + nearMissSamples + " 次 / 归零 " + rollDropoutSamples + " 次");
    }

    /** 取两端里「信息量最大」的那一端：优先竖向门（横向贴上了、只是高度不对），其次近失距离大的。 */
    private static EndDiagnostics worstEnd(CarDiagnostics diagnostics) {
        final EndDiagnostics endA = diagnostics.end0;
        final EndDiagnostics endB = diagnostics.end1;
        final boolean aFailed = endA.railNearby && !endA.rollIsZero;
        final boolean bFailed = endB.railNearby && !endB.rollIsZero;
        if (aFailed && !bFailed) {
            return endA;
        }
        if (bFailed && !aFailed) {
            return endB;
        }
        if (endA.failedGate == GATE_VERTICAL && endB.failedGate != GATE_VERTICAL) {
            return endA;
        }
        if (endB.failedGate == GATE_VERTICAL && endA.failedGate != GATE_VERTICAL) {
            return endB;
        }
        return endA.bestHorizontal >= endB.bestHorizontal ? endA : endB;
    }

    // ==================== 一次性诊断 ====================

    /**
     * 由 {@link #beginRenderFrame()} 与 {@link #captureCar} 调用（两边互相兜底：
     * 只要有一侧还活着，另一侧的静默失效就能被报出来，与 MTR4 的做法一致）。
     * <p>
     * 证据前提：只有「客户端确实存在带滚转的轨道」或「确实算过车厢滚转」时才值得报警，
     * 否则玩家可能根本没接触过这个特性。
     */
    private static void reportMissingHooks() {
        final int clock = Math.max(frameSamples, captureSamples);
        if (clock < HOOK_PROBE_SAMPLES) {
            return;
        }
        if (!rolledRailEvidence && captureSamples == 0) {
            return;
        }
        if (!frameHookAlive && !warnedFrame) {
            warnedFrame = true;
            Main.LOGGER.warn("[P5-6] RenderTrains.render 的帧头钩子未触发；列车车体滚转的候选轨道表不会每帧刷新"
                    + "（仍有 TTL 兜底，最多 100 ms 陈旧）。若同时车体也不倾斜，请用 javap 复核 "
                    + "RenderTrains.render(Lmtr/entity/EntitySeat;FLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;)V 这个描述符是否仍然存在");
        }
        if (frameHookAlive && !captureHookAlive && !warnedCapture) {
            warnedCapture = true;
            Main.LOGGER.warn("[P5-6] 已渲染 " + frameSamples + " 帧，但 TrainClient.simulateCar 的钩子从未触发；"
                    + "车厢的滚转角根本不会被算出来，车体与风挡 / 挡板都不会倾斜。请用 javap 复核 "
                    + "simulateCar(Lnet/minecraft/world/level/Level;IFDDDFFDDDDFFZZD)V 这个描述符");
        }
        if (captureHookAlive && !bodyBeginHookAlive && !warnedBodyBegin) {
            warnedBodyBegin = true;
            Main.LOGGER.warn("[P5-6] simulateCar 的钩子已触发 " + captureSamples + " 次，但 "
                    + "JonModelTrainRenderer.renderCar 的钩子从未触发；车体不会倾斜（请用 javap 复核 "
                    + "renderCar(IDDDFFZZ)V，并确认该渲染器仍是 MTR 自己的 JonModelTrainRenderer）");
        }
        if (bodyBeginHookAlive && !bodyRotateHookAlive && !warnedBodyRotate) {
            warnedBodyRotate = true;
            Main.LOGGER.warn("[P5-6] renderCar 的钩子已触发，但其中 UtilitiesClient.rotateX 的 @Redirect"
                    + "（ordinal = 0）从未触发；车体滚转角算出来了却一个矩阵都没改。请用 javap 复核 "
                    + "JonModelTrainRenderer.renderCar 里 rotateX(PoseStack,F)V 仍有两处（模型链在前、缆车抓手在后）");
        }
        if (captureHookAlive && !connectionHookAlive && !warnedConnection) {
            warnedConnection = true;
            Main.LOGGER.warn("[P5-6] simulateCar 的钩子已触发 " + captureSamples + " 次，但 renderConnection /"
                    + " renderBarrier 的 @Redirect 从未触发；风挡与挡板不会随车体倾斜（请用 javap 复核 "
                    + "TrainRendererBase.renderConnection / renderBarrier 的 13 参数描述符）");
        }
        if (matchedSamples > 0 && tiltAppliedSamples == 0 && !warnedNeverApplied) {
            warnedNeverApplied = true;
            Main.LOGGER.warn("[P5-6] 已解析出 " + matchedSamples + " 次非零车体滚转角，但一次都没有真正应用到矩阵上；"
                    + "说明 renderCar 里的 rotateX 重定向没生效（见上一条告警）");
        }
        // 只有在「客户端确实存在带滚转轨道」且观察窗口足够长时才提示匹配问题，避免列车正好停在别处时误报。
        if (rolledRailEvidence && railHitSamples == 0 && captureSamples >= HOOK_PROBE_SAMPLES * 300 && !warnedNoMatch) {
            warnedNoMatch = true;
            Main.LOGGER.warn("[P5-6] 客户端存在带滚转的轨道，但累计 " + captureSamples + " 节车厢都没有匹配上；"
                    + "若此时确实有列车停在超高段上，请复核 RailTrainRollHelper 的匹配容差"
                    + "（水平 " + MATCH_HORIZONTAL + " 格 / 竖向 " + MATCH_VERTICAL + " 格 / 平行度 "
                    + MATCH_PARALLEL + "）与车厢端点的重建公式");
        }
    }

    // ==================== 供探针使用 ====================

    /**
     * 探针专用：直接注入本帧候选轨道。
     * <p>
     * 需要它是因为探针不启动 Mixin，{@code mtr.data.Rail} 上没有 {@code RailPoseExtraHolder}，
     * {@link RailTiltRenderHelper#frameFor(Rail)} 必然返回 {@code null} —— 于是候选来源改由探针给出，
     * 但<b>匹配、滚转角表达式、折线采样仍然走真实代码</b>。
     */
    public static void addCandidateForProbe(Rail rail, RailGeometryCore core, boolean parameterIsPosition1ToPosition2) {
        if (rail == null || core == null) {
            return;
        }
        final Polyline polyline = polylineFor(rail);
        if (polyline == null) {
            return;
        }
        probeCandidates = true;
        FRAME_CANDIDATES.add(new Candidate(
                RailTiltRenderHelper.frameFromCoreForProbe(core, parameterIsPosition1ToPosition2), polyline));
    }

    /** 探针专用：清空候选表并取消探针模式。 */
    public static void resetProbeCandidates() {
        FRAME_CANDIDATES.clear();
        probeCandidates = false;
        candidatesDirty = true;
    }

    /**
     * 探针专用：直接算出某节车厢的滚转角，不写任何状态（等价于 {@link #captureCar} 的算术部分）。
     * <p>
     * 诊断出口是一个<b>临时</b> {@link EndDiagnostics}（不进入 {@link #CAR_DIAGNOSTICS}），
     * 因此探针调用它不会污染游戏内的一次性诊断计数。
     */
    public static double computeBodyRollForProbe(
            double carX, double carY, double carZ, float carYaw, float carPitch,
            double realSpacing, int railOffset
    ) {
        final EndDiagnostics scratch1 = new EndDiagnostics();
        final EndDiagnostics scratch2 = new EndDiagnostics();
        final double axisX = Math.sin(carYaw) * Math.cos(carPitch);
        final double axisY = Math.sin(carPitch);
        final double axisZ = Math.cos(carYaw) * Math.cos(carPitch);
        final double half = realSpacing * 0.5D;
        if (!(half > 0.0D) || !Double.isFinite(half)) {
            return rollDegreesAtPoint(carX, carY, carZ, axisX, axisZ, railOffset, scratch1);
        }
        final double rollA = rollDegreesAtPoint(
                carX + half * axisX, carY + half * axisY, carZ + half * axisZ, axisX, axisZ, railOffset, scratch1);
        final double rollB = rollDegreesAtPoint(
                carX - half * axisX, carY - half * axisY, carZ - half * axisZ, axisX, axisZ, railOffset, scratch2);
        return (rollA + rollB) * 0.5D;
    }

    /** 探针专用：读回某节车厢算好的纵轴（单位向量），用于独立验证旋转轴。 */
    public static double[] axisForProbe(TrainRendererBase renderer, int carIndex) {
        final double[] state = stateOf(renderer, carIndex);
        if (state == null) {
            return null;
        }
        return new double[]{state[0], state[1], state[2], state[3]};
    }

    /** 供诊断读取：渲染帧计数。 */
    public static int frameSampleCount() {
        return frameSamples;
    }

    /** 供诊断读取：车厢采样计数。 */
    public static int captureSampleCount() {
        return captureSamples;
    }

    /** 供诊断读取：真正应用过滚转的次数。 */
    public static int tiltAppliedSampleCount() {
        return tiltAppliedSamples;
    }

    /** 供诊断读取：累计「近失」端点数（附近有带滚转轨道、但三道门全没过）。 */
    public static int nearMissSampleCount() {
        return nearMissSamples;
    }

    /** 供诊断读取：累计「上一帧还在倾斜、本帧突然归零」的次数。 */
    public static int rollDropoutSampleCount() {
        return rollDropoutSamples;
    }

    /** 探针专用：清零统计量。 */
    public static void resetDiagnosticsForProbe() {
        frameHookAlive = false;
        captureHookAlive = false;
        bodyBeginHookAlive = false;
        bodyRotateHookAlive = false;
        connectionHookAlive = false;
        frameSamples = 0;
        captureSamples = 0;
        connectionSamples = 0;
        railHitSamples = 0;
        matchedSamples = 0;
        tiltAppliedSamples = 0;
        nearMissSamples = 0;
        rollDropoutSamples = 0;
        rolledRailEvidence = false;
        firstTiltLogged = false;
        warnedFrame = false;
        warnedCapture = false;
        warnedBodyBegin = false;
        warnedBodyRotate = false;
        warnedConnection = false;
        warnedNeverApplied = false;
        warnedNoMatch = false;
        warnedRollDropout = false;
    }

    // ==================== 内部数据结构 ====================

    /** 一条候选轨道：内核快照 + 采样折线。 */
    private static final class Candidate {
        private final RailTiltRenderHelper.RollFrame frame;
        private final Polyline polyline;

        private Candidate(RailTiltRenderHelper.RollFrame frame, Polyline polyline) {
            this.frame = frame;
            this.polyline = polyline;
        }
    }

    /** 轨道采样折线：每个采样点的世界坐标与弧长参数，外加水平包围盒。 */
    private static final class Polyline {
        private final double[] xs;
        private final double[] ys;
        private final double[] zs;
        private final double[] parameters;
        private final double minX;
        private final double maxX;
        private final double minZ;
        private final double maxZ;

        private Polyline(
                double[] xs, double[] ys, double[] zs, double[] parameters,
                double minX, double maxX, double minZ, double maxZ
        ) {
            this.xs = xs;
            this.ys = ys;
            this.zs = zs;
            this.parameters = parameters;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }

    /** 当前正在 {@code simulateCar} 的车厢（风挡 / 挡板重定向据此取两端状态）。 */
    private static final class CurrentCar {
        private final TrainRendererBase renderer;
        private final int carIndex;

        private CurrentCar(TrainRendererBase renderer, int carIndex) {
            this.renderer = renderer;
            this.carIndex = carIndex;
        }
    }

    /**
     * 一节车厢的近失诊断（跨帧存活，见 {@link #CAR_DIAGNOSTICS}）。
     * <p>
     * {@link #previousRoll} 是<b>上一帧</b>算出的滚转角，用来判定「本来在倾斜、这一帧突然归零」；
     * 它每次 {@link #captureCar} 都被 {@link #roll} 覆盖，所以不会累积残值。
     * 两个端点槽 {@link #end0} / {@link #end1} 在每次 {@code captureCar} 开头被 {@code reset()}。
     */
    private static final class CarDiagnostics {
        private final EndDiagnostics end0 = new EndDiagnostics();
        private final EndDiagnostics end1 = new EndDiagnostics();
        private double previousRoll = 0.0D;
        private double roll = 0.0D;
    }

    /**
     * 一个车厢端点的匹配诊断：只在<b>没匹配上</b>时才有意义。
     * <p>
     * 语义刻意分成三档，因为「归零」的原因完全不同、处理方式也完全不同：
     * <ul>
     *   <li>{@link #rollIsZero}：匹配<b>成功</b>了，只是该处剖面滚转恰好为 0（正常的过零点）；</li>
     *   <li>{@link #railNearby} 且 {@code !rollIsZero}：附近确实有带滚转的轨道但三道门全没过
     *       —— 这才是「近失」，也就是用户看到的抖动的候选原因；</li>
     *   <li>两者都假：附近<b>根本没有</b>带滚转的轨道（列车不在超高段附近），归零完全正常。</li>
     * </ul>
     */
    private static final class EndDiagnostics {
        /** 该端点 {@link #DIAGNOSTIC_NEAR_RADIUS} 格内存在带滚转的轨道。 */
        private boolean railNearby = false;
        /** 匹配成功、但该处滚转为 0（剖面端点 / 过零点）——正常，不算近失。 */
        private boolean rollIsZero = false;
        /** 失败时哪道门拒绝的（{@link #GATE_HORIZONTAL} / {@link #GATE_VERTICAL} / {@link #GATE_AXIS}）。 */
        private int failedGate = GATE_HORIZONTAL;
        /** 观测到的最小横向距离（格），用于告警里给出「差多少」。 */
        private double bestHorizontal = Double.MAX_VALUE;

        private void reset() {
            railNearby = false;
            rollIsZero = false;
            failedGate = GATE_HORIZONTAL;
            bestHorizontal = Double.MAX_VALUE;
        }
    }
}
