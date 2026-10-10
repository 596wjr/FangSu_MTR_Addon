package com.fangsu.mtr.rail;

import com.fangsu.Main;
import com.fangsu.config.FangSuConfig;
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

    /**
     * 每帧开头顺带记下本帧相机的世界朝向（仅 P5-7 相机路径使用）。
     * <p>
     * <b>为什么需要它</b>：{@code PoseStack.mulPose(q)} 是右乘，{@code M ← M·R_a(θ)}，而
     * {@code M·R_a(θ) = R_{M·a}(θ)·M}：传入的轴必须是「{@code M} 所消费的那个空间」里的轴。
     * 相机钩子所在的位置（{@code GameRenderer.renderLevel} 里 {@code prepareCullFrustum} 之前）
     * 栈上已经压了相机旋转 {@code R_cam = Ry(camYaw)·Rx(camPitch)}，因此要在那里施加
     * 「绕车厢世界前向轴 f 的世界旋转」，必须传 {@code R_cam⁻¹·f} —— 也就是需要相机朝向。
     * <p>
     * <b>数据从哪来</b>：{@code TrainRendererBase.camera}（protected static，MTR 自己在
     * {@code setupStaticInfo} 里赋成 {@code Minecraft.getInstance().gameRenderer.getMainCamera()}），
     * 而 {@code RenderTrains.render} 的 HEAD 钩子正好在 {@code setupStaticInfo} 之后：那里读到的
     * 就是本帧的相机。取不到时置空，相机钩子于是退回「不旋转」（绝不猜测朝向）。
     */
    public static void captureCameraOrientation(net.minecraft.client.Camera camera) {
        if (camera == null) {
            cameraOrientationValid = false;
            return;
        }
        // MC 的相机旋转是 Camera.setRotation 里的
        //   rotation.rotationYXZ(-mcYaw, mcPitch, 0)   ==   Ry(-mcYaw)·Rx(mcPitch)
        // 而 {@link #cameraSpaceAxis} 用的是 Ry(camYaw)·Rx(camPitch)。两者对上需要
        //   camYaw = -mcYaw = -camera.getYRot()
        // （MC 的偏航零度朝 +Z、顺时针为正；本项目的 Ry 逆时针为正，故取负。）
        // 俯仰直接用 camera.getXRot()：MC 的 rotationYXZ 也是原样收正俯仰。
        // 探针里用真实 PoseStack 与该式逐元素对照过（见 .tmp_p5_7_probe/P57AnteProbe.java 第 (B) 节）。
        cachedCameraYaw = (float) Math.toRadians(-camera.getYRot());
        cachedCameraPitch = (float) Math.toRadians(camera.getXRot());
        cameraOrientationValid = true;
    }

    /** 本帧相机朝向（弧度）；{@code cameraOrientationValid} 为假时无意义。 */
    private static volatile float cachedCameraYaw = 0.0F;
    private static volatile float cachedCameraPitch = 0.0F;
    private static volatile boolean cameraOrientationValid = false;

    /**
     * 本帧镜头旋转要用的<b>相机坐标系</b>轴：{@code R_cam⁻¹·f}，{@code f} 是车厢世界前向轴。
     * 没有乘车上下文 / 没有有效相机朝向 / 数据非有限时返回 {@code null}。
     */
    public static double[] getCameraSpaceTiltAxis() {
        final RidingTilt tilt = ridingTilt;
        if (tilt == null || !cameraOrientationValid) {
            return null;
        }
        return cameraSpaceAxis(getCameraTiltAxis(), cachedCameraYaw, cachedCameraPitch);
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

    // ==================== P5-7：乘车玩家与镜头滚转 ====================
    //
    // 与 P5-3 / P5-6 同一条纪律：本步骤只需要「车体滚转角」这<b>一个</b>标量，而它必须与车体、风挡、
    // 轨面用的是同一个值。因此这里<b>不重新做一遍匹配</b>，而是复用本类已经用于车体的那套
    // （captureCar 里同一段算术、rollDegreesAtPoint 里同一个符号表达式、同一份内核）。
    //
    // 数据来源的选择（为什么不用 CAR_STATES 状态表）：
    //   setOffsets 发生在 Train.handlePositions → TrainClient.handlePositions → movePlayer 的
    //   「计算本帧骑行位置」阶段，而 CAR_STATES 由 simulateCar 在<b>同一 tick 更晚</b>（渲染阶段）
    //   写入。若从状态表取，相机拿到的会是<b>上一帧</b>的滚转角与纵轴（坐标也可能对不上）。
    //   setOffsets 自己的形参里就有本车厢的<b>世界坐标 carX/carY/carZ 与 carYaw/carPitch</b>
    //   （TrainClient 的 calculateCar 给的，不是相机相对量），因此直接现算，
    //   与车体在同一 tick 用的是同一组输入 —— 时序上不可能不一致。

    /**
     * 本帧乘车玩家的车厢滚转上下文；{@code null} = 本帧没有在乘带滚转的车。
     * <p>
     * {@link VehicleRidingClient#movePlayer} 的 HEAD 钩子把它清成 {@code null}，
     * 本地玩家的 {@code setOffsets} 再按需写入 —— 因此它是「本帧」的量，不会跨帧残留
     * （列车停车 / 下车 / 切到别的车都会在同一 tick 内归零）。
     */
    private static volatile RidingTilt ridingTilt = null;

    /** 本帧是否至少有一次「本地玩家在 MTR 车辆上取过位置」—— riding 钩子的活性证据（与滚转无关）。 */
    private static volatile boolean ridingHookAlive = false;

    /** 累计「本地玩家位于一辆<b>带滚转</b>的车上」的帧数（> 0 即该特性本该可见）。 */
    private static volatile int ridingRolledSamples = 0;

    /** 累计真正旋转过乘车玩家局部偏移的次数（> 0 即「站姿已随地板倾斜」）。 */
    private static volatile int ridingPlayerAppliedSamples = 0;

    /** 累计镜头钩子被调用（= 注入点解析成功）的次数，无论该帧是否真的滚转。 */
    private static volatile int cameraHookSamples = 0;

    /** 累计真正往世界矩阵上施加过滚转的帧数（> 0 即「地平线已随车体滚转」）。 */
    private static volatile int cameraAppliedSamples = 0;

    private static boolean firstRidingTiltLogged = false;
    private static boolean firstCameraTiltLogged = false;
    private static boolean warnedRidingHook = false;
    private static boolean warnedRidingPlayerRedirect = false;
    private static boolean warnedCameraHook = false;
    private static boolean warnedCameraNeverApplied = false;

    /**
     * {@code mtr.data.VehicleRidingClient.movePlayer} 的 {@code @Inject(at = HEAD)}：
     * 撤销上一帧的乘车滚转上下文。
     * <p>
     * 它取代了「注入一个返回值」的做法：MTR 每 tick 都会先清空 {@code offset} / {@code riderPositions}
     * 再走 {@link #markRidingPlayerPosition}，所以这里也把本类的上下文一起清掉，
     * 保证「没有乘车 / 该帧没有算出滚转」时镜头读到的一定是 {@code null}（= 原生相机）。
     */
    public static void beginRidingFrame() {
        ridingHookAlive = true;
        ridingTilt = null;
    }

    /**
     * 本地玩家的 {@code setOffsets} 被调用：算出本车厢的滚转角与纵轴，作为<b>本帧</b>的乘车滚转上下文。
     * <p>
     * <b>为什么世界坐标在这里是可信的</b>：{@code setOffsets} 的 x/y/z 来自
     * {@code Train.calculateCar}（两端点的弦中点 + 1 格），是纯粹的世界坐标；
     * 而 {@code simulateCar} 交给渲染器的才是 {@code carX - viewOffset}。
     * 本方法用的正是前者 —— 这一点与 P5-6 的匹配完全同源（{@link #CAR_ORIGIN_ABOVE_RAIL}）。
     * <p>
     * 滚转角的算法与 {@link #captureCar} 的算术部分<b>逐字相同</b>（两端点各匹配一次再取平均），
     * 只是不写状态表、不推进 P5-6 的诊断计数：相机不该因为「多看了几眼」而改变车体的诊断。
     *
     * @param x     车厢原点世界 X（= 两端平均）
     * @param y     车厢原点世界 Y（= 两端平均 + 1）
     * @param z     车厢原点世界 Z
     * @param yaw   车厢偏航（{@code Train.calculateCar} 给的）
     * @param pitch 车厢俯仰
     * @param length 本车厢实长（{@code setOffsets} 的 {@code length}）
     */
    public static void markRidingPlayerPosition(
            double x, double y, double z, float yaw, float pitch, double length
    ) {
        ridingHookAlive = true;
        // 车身局部 +Z 在世界系的方向 f = Ry(yaw)·Rx(pitch)·(0,0,1)
        //                                   = (sin yaw·cos pitch, <b>−sin pitch</b>, cos yaw·cos pitch)
        //
        // ★ 这里的 −sin(pitch) 是<b>必须</b>的，不是笔误。{-sin(pitch)} 由来：
        //   MTR 的 setOffsets 用 `new Vec3(px, riderOffset, 纵向).xRot(pitch).yRot(yaw)`，
        //   而实测 Vec3.xRot(t) = 标准右手 Rx(+t)、Vec3.yRot(t) = 标准右手 Ry(+t)，
        //   所以车厢局部 +Z 的像是 Rx(pitch)·ẑ = (0, −sin pitch, cos pitch) 再 Ry(yaw)。
        //   ANTE 的镜头滚转轴正是 Ry(yaw)·Rx(pitch)·ẑ（同一条链），探针 (A)/(B) 两节
        //   都是拿它做基准的。
        //
        //   旧注释写的是 Ry(π+yaw)·Rx(π+pitch)·(0,0,1)：那个式子确实等于 +sin(pitch)，
        //   但 π 是<b>车体模型</b>的朝向修正（模型 +Z 朝后），<b>不是</b>玩家偏移用的车厢系。
        //   两者差一个绕纵轴的 π，正是 P5-7 早先 0.31 量级残差的来源。
        //
        //   carPitch = 0 时两种写法相同，所以这个符号只在坡道上显形 —— 探针 (B) 的
        //   per-pose 诊断把那唯一失败的一族精确定位到 carPitch = 9.09°。
        //
        // 轴只用于重建两个端点（± 互换不影响取样均值）与 {@link #getCameraTiltAxis()}。
        double axisX = Math.sin(yaw) * Math.cos(pitch);
        double axisY = -Math.sin(pitch);
        double axisZ = Math.cos(yaw) * Math.cos(pitch);
        final double axisLength = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
        final double half = length * 0.5D;

        final double roll;
        final double[] axis = new double[3];
        if (!Double.isFinite(axisLength) || axisLength < 1.0E-9D || !Double.isFinite(half) || half <= 0.0D) {
            // 退化（长度为 0 / 姿态非有限）：退回弦中点单点采样，水平纵轴
            axis[0] = Math.sin(yaw);
            axis[1] = 0.0D;
            axis[2] = Math.cos(yaw);
            roll = rollDegreesAtPointForRiding(x, y, z, axis[0], axis[2]);
        } else {
            final double unitX = axisX / axisLength;
            final double unitY = axisY / axisLength;
            final double unitZ = axisZ / axisLength;
            final double rollA = rollDegreesAtPointForRiding(
                    x + half * unitX, y + half * unitY, z + half * unitZ, unitX, unitZ);
            final double rollB = rollDegreesAtPointForRiding(
                    x - half * unitX, y - half * unitY, z - half * unitZ, unitX, unitZ);
            roll = (rollA + rollB) * 0.5D;
            axis[0] = unitX;
            axis[1] = unitY;
            axis[2] = unitZ;
        }

        if (!Double.isFinite(roll) || roll == 0.0D) {
            // 无超高 / 剖面过零点 / 匹配不上：与「本来就没有超高」同样处理 —— 上下文置空，
            // 玩家站位与相机都保持原生（这是最常见的路径，必须一个浮点运算都不多做）。
            ridingTilt = null;
            return;
        }
        ridingTilt = new RidingTilt(roll, axis[0], axis[1], axis[2], yaw, pitch);
        ridingRolledSamples++;
        reportMissingHooks();
        if (!firstRidingTiltLogged) {
            firstRidingTiltLogged = true;
            Main.LOGGER.info("[P5-7] 乘车玩家滚转上下文已建立：本车厢滚转 {}°，车身世界纵轴 ({}, {}, {})"
                            + "（yaw {}°, pitch {}°）。玩家站位与镜头将使用同一个角度、同一根轴 —— "
                            + "与车体（P5-6）共用 RailTrainRollHelper 的同一份滚转表达式",
                    roll, axis[0], axis[1], axis[2], Math.toDegrees(yaw), Math.toDegrees(pitch));
        }
    }

    /**
     * 乘车匹配（滚转角）—— 与 {@link #rollDegreesAtPoint} 同一套三道门，只把诊断丢进一个
     * <b>临时</b>槽（不进入 {@link #CAR_DIAGNOSTICS}）。
     * <p>
     * 这样做的理由：相机每 tick 都会多算 <b>2 次</b>匹配，若共用 P5-6 的诊断槽，
     * 「匹配近失 / 突然归零」的统计会被相机观察行为污染 —— 诊断必须只描述车体渲染那条路径。
     */
    private static double rollDegreesAtPointForRiding(double x, double y, double z, double axisX, double axisZ) {
        return rollDegreesAtPoint(x, y, z, axisX, axisZ, 0, new EndDiagnostics());
    }

    /**
     * P5-7 的乘车滚转上下文（不可变快照，每 tick 由 {@link #markRidingPlayerPosition} 覆盖）。
     */
    public static final class RidingTilt {
        /** 车体滚转角（度），符号与 {@link #applyBodyRoll} 交给 {@code rotateZDegrees} 的完全相同。 */
        public final double rollDegrees;
        /** 车身世界纵轴（单位向量，含 pitch）：{@code Ry(yaw)·Rx(pitch)·ẑ}。 */
        public final double axisX;
        public final double axisY;
        public final double axisZ;
        /** 本车厢的 yaw / pitch（弧度），仅供诊断。 */
        public final float yaw;
        public final float pitch;

        private RidingTilt(
                double rollDegrees, double axisX, double axisY, double axisZ, float yaw, float pitch
        ) {
            this.rollDegrees = rollDegrees;
            this.axisX = axisX;
            this.axisY = axisY;
            this.axisZ = axisZ;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    /**
     * 本帧乘车玩家所在车厢的滚转上下文；未乘车 / 无超高 / 匹配不上时返回 {@code null}。
     * 只读快照，调用方不得缓存跨帧使用。
     */
    public static RidingTilt currentRidingTilt() {
        return ridingTilt;
    }

    /**
     * {@code mtr.data.VehicleRidingClient.setOffsets} 里 {@code Vec3.xRot(F)} 的 {@code @Redirect}：
     * 把<b>车厢局部偏移</b>绕车厢局部 {@code +Z}（纵轴）按车体滚转角旋转，再交给 MTR 原来的
     * {@code xRot(pitch)}。
     * <p>
     * <b>为什么钉在 {@code xRot} 上</b>：{@code setOffsets} 里那一个表达式
     * <pre>
     *   playerOffset = new Vec3(percentageX, riderOffset, 纵向偏移).xRot(pitchAngle).yRot(yaw)
     * </pre>
     * 就是「车厢局部 → 世界」的<b>唯一</b>一步（{@code javap -p -c} 核实：整个
     * {@code setOffsets} 里 {@code Vec3.xRot} 只有一处，偏移 146；{@code yRot} 另有两处，
     * 偏移 117/151）。在它<b>之前</b>插入一个绕局部 Z 的旋转，等价于「把车厢地板整个转过去」。
     * <p>
     * <b>为什么 {@code riderPositions} 与视线偏移会一起跟上</b>：同一个 {@code playerOffset}
     * 在 {@code :111} 写进 {@code riderPositions}（第三人称里别的玩家看到的本体）、在
     * {@code :114-116} 决定本地玩家的 {@code absMoveTo}、并在 {@code :167-169} 写进
     * {@code offset}（{@code getViewOffset()} = 相机相对量）—— 三者是<b>同一个值</b>的三个消费者，
     * 不存在「改了一处漏了另一处」的可能。探针
     * {@code .tmp_p5_7_probe/RidingPlayerTiltProbe.java} 对这一条做了逐项数值断言。
     * <p>
     * <b>枢轴为什么是 (0,−1,0)</b>：ANTE 的配方把 Rz 夹在 {@code translate(0,-1,0)} 与
     * {@code translate(0,1,0)} 之间，即绕「站立点下方 1 格」这个点转，而不是绕偏移原点转。
     * 效果就是「地板绕脚下的枢轴倾斜」，玩家被顶到倾斜后地板的高侧而不是原地打转。
     * 本条是照搬 ANTE 的决定，探针
     * {@code .tmp_p5_7_probe/P57AnteProbe.java} 第 (A) 节对
     * 「枢轴 (0,−1,0)」与「无枢轴」两种做法分别算了与 ANTE 的偏差，只有前者能归零。
     * <p>
     * <b>符号的来源</b>：本项目的 {@code rollDegrees} 来自内核 {@code RailRollProfile}，
     * 符号里<b>已经</b>含有列车方向（P5-6 的 {@code railFrameAngle × sign(align)}），
     * 对应 ANTE 的 {@code sign = −1} 支；ANTE 的 {@code (reversed ? 1 : −1)} 在这里
     * <b>不能</b>再乘一次，否则反车时会镜像。探针第 (A) 节对正/反两个方向分别断言。
     * <p>
     * <b>为什么不能直接调 {@code Vec3.zRot}</b>：MC 的 {@code Vec3.zRot(t)} 是右手
     * {@code Rz(t)} 的转置（实测：{@code max |zRot(+t) − RH_Rz(−t)| = 1.05e−04}），
     * 而 JOML/{@code Vector3f.rotationDegrees} 又是另一个转置约定。两者叠在一起虽然
     * 数值上可能凑对，但语义无法复核。本方法因此手写显式三角函数，并在探针里与
     * ANTE 的 JOML 管线逐点对照。
     * <p>
     * <b>无滚转时严格 no-op</b>：{@link #currentRidingTilt()} 为 {@code null} 时，调用方
     * （mixin）原样调用 MTR 的 {@code xRot}，一个浮点运算都不做。
     *
     * @param vec        被重定向调用的接收者（车厢局部偏移，尚未做 pitch/yaw）
     * @param pitchAngle MTR 原调用传给 {@code xRot} 的实参（原样透传，绝不改写）
     */
    public static Vec3 applyRidingPlayerRoll(Vec3 vec, float pitchAngle) {
        final RidingTilt tilt = ridingTilt;
        if (vec == null || tilt == null) {
            return vec == null ? null : vec.xRot(pitchAngle);
        }
        final double radians = Math.toRadians(tilt.rollDegrees);
        // ---------------------------------------------------------------------------------
        // 直接照搬 ANTE（MTR3 侧已发布且实测可用的实现）的配方，源码
        //   mtr3/mtr-ante-alpha/…/mixin/VehicleRidingClientMixin.java:153-161
        //   mat = Ry(yaw)·Rx(pitch)·translate(0,-1,0)·rotateZ(sign·roll)·translate(0,1,0)
        //   playerOffset = mat.transform(localOffset)
        //
        // 实测（.tmp_p5_7_probe）：上式的 Ry·Rx 部分与 MTR 原生
        //   new Vec3(…).xRot(pitchAngle).yRot(yaw)
        // 在 roll=0 时逐位相同（|d| = 3.1e−16），所以两者的差别<b>只有</b>那对 translate
        // 夹着的 rotateZ。
        //
        // 那对 translate 的作用是把 Rz 的<b>枢轴</b>从偏移原点挪到车厢局部 (0,−1,0)
        // （JOML 的 translate/rotate 都是右乘，已实测），于是「Rz 的作用」写成
        //   D·v = Rz_joml(θ)·(v + ŷ) − ŷ,   θ = sign·roll,  ŷ = (0,1,0)
        // 又实测 JOML rotateZ 与 MC 的 Vector3f.rotationDegrees / UtilitiesClient 是<b>同一个</b>
        // 约定，都等于标准右手 Rz(+角度)：Rz_joml(θ) = RH_Rz(θ)。ANTE 的 sign = −1、
        // 即 θ = −roll，于是 Rz_joml(−roll) = RH_Rz(−roll)：
        //   RH_Rz(−roll)·(x,y,z) = (x·cos roll + y·sin roll, −x·sin roll + y·cos roll, z)
        //
        // sign：ANTE 用 (reversed ? 1 : −1) 处理列车方向；本项目的滚转角来自内核
        // RailRollProfile，其符号里<b>已经</b>含了方向（P5-6 的 railFrameAngle × sign(align)），
        // 所以这里对应 ANTE 的 sign = −1 那一支，不能再乘一次方向，否则反车时会镜像。
        // ---------------------------------------------------------------------------------
        final double theta = -radians;
        final double cosT = Math.cos(theta);
        final double sinT = Math.sin(theta);
        final double liftedY = vec.y + 1.0D;
        final Vec3 rolled = new Vec3(
                vec.x * cosT - liftedY * sinT,
                vec.x * sinT + liftedY * cosT - 1.0D,
                vec.z
        );
        ridingPlayerAppliedSamples++;
        reportMissingHooks();
        if (ridingPlayerAppliedSamples == 1) {
            Main.LOGGER.info("[P5-7] 乘车玩家站姿滚转已生效：局部偏移 ({}, {}, {}) 已绕车厢局部 +Z 按车体同一"
                            + "滚转方向旋转 {}°（世界位置与视线偏移同源，第三人称本体位置同步跟随）",
                    vec.x, vec.y, vec.z, tilt.rollDegrees);
        }
        return rolled.xRot(pitchAngle);
    }

    // ==================== P5-7：镜头滚转 ====================

    /**
     * 镜头钩子被调用（无论该帧是否需要滚转）—— 活性证据。
     * <p>
     * 由 {@code GameRendererTiltMixin} 在注入点<b>第一行</b>调用：只要它被调用过，就说明
     * 「{@code GameRenderer.renderLevel} → {@code LevelRenderer.prepareCullFrustum}」这个
     * 注入点在本版本上解析成功。这样 {@code require = 0} 的静默失效就能与
     * 「注入成功但本帧没有超高」区分开。
     */
    public static void probeCameraTiltFrame() {
        cameraHookSamples++;
        reportMissingHooks();
    }

    /**
     * 镜头滚转角（度，已乘配置强度）；<b>不需要滚转时返回 0</b>，调用方据此在任何矩阵运算之前返回。
     * <p>
     * <b>物理含义</b>：物理模型是「乘客的脑袋焊在车厢上」，即相机（连同整个画面）跟着<b>车体</b>一起滚，
     * 而不是反过来。车体在本帧的世界旋转是
     * <pre>
     *   M_body_rolled = M_body_noroll · Rz_JOML(roll) = R_f(roll) · M_body_noroll
     * </pre>
     * 其中 {@code f} 是车厢的世界前向轴（= 车身局部 {@code +Z} 的负方向；实测见
     * {@code .tmp_p5_7_probe/P57Probe.java} 第 (4) 节）。因此相机也必须施加<b>同一个世界旋转</b>
     * {@code R_f(+angle)}（角度取车体滚转角本身，不取反号），画面里车厢才会保持
     * 「车内水平」而地平线随坡倾斜。
     * <p>
     * 返回 {@code 0.0} 的全部情形（每一种都必须与原生逐位一致）：
     * <ul>
     *   <li>未乘车 / 不在 MTR 车辆上（{@code ridingTilt == null}）；</li>
     *   <li>所在车厢没有超高，或该处剖面滚转恰好为 0；</li>
     *   <li>配置 {@code cameraTiltEnabled} 关闭；</li>
     *   <li>配置 {@code cameraTiltStrength} 为 0；</li>
     *   <li>滚转角非有限（防御）。</li>
     * </ul>
     */
    public static double getCameraRollDegrees() {
        final RidingTilt tilt = ridingTilt;
        if (tilt == null || !Double.isFinite(tilt.rollDegrees) || tilt.rollDegrees == 0.0D) {
            return 0.0D;
        }
        if (!FangSuConfig.cameraTiltEnabled()) {
            return 0.0D;
        }
        final double strength = FangSuConfig.cameraTiltStrength();
        if (!Double.isFinite(strength) || strength == 0.0D) {
            return 0.0D;
        }
        // ANTE（MTR3 侧已发布的实现，源码 mtr3/mtr-ante-alpha）的镜头滚转是
        //   rotation.mul(new Quaternionf().rotateY(yaw).rotateX(pitch)
        //                          .rotateZ(roll).rotateY(-yaw))
        // = Q_new = Q·R_f(roll)，f = Ry(yaw)·Rx(pitch)·ẑ。
        // 世界栈持有的是 Q⁻¹，右乘的等价改写把 R_f(roll) 变成 R_{R_cam·f}(−roll)
        // （完整推导见 {@link #cameraSpaceAxis}），所以这里返回的是 <b>−roll</b>。
        final double angle = -tilt.rollDegrees * strength;
        return Double.isFinite(angle) && angle != 0.0D ? angle : 0.0D;
    }

    /**
     * 镜头滚转轴 —— 车厢的<b>世界纵轴</b> {@code f}（含 pitch）。
     * <p>
     * <b>与 ANTE 完全一致</b>（ANTE 是 MTR3 侧<b>已发布</b>的实现，源码在
     * {@code mtr3/mtr-ante-alpha/…/data/Rolling.java} 与 {@code …/mixin/CameraMixin.java}）：
     * ANTE 的镜头滚转是
     * {@code new Quaternionf().rotateY(yaw).rotateX(pitch).rotateZ(roll).rotateY(-yaw)}，
     * 即绕 {@code f = Ry(yaw)·Rx(pitch)·ẑ} 这根轴转 {@code roll}。本方法返回的正是这根 {@code f}，
     * {@link #getCameraRollDegrees} 返回的也是 {@code +roll}。
     * <p>
     * <b>这就是车体自己绕的那根轴</b>：{@link #applyBodyRoll} 施加的 {@code rotateZDegrees(roll)} 等价于
     * <b>世界系里</b>的 {@code R_f(−roll)}。相机必须施加<b>同一个</b>世界旋转（ANTE 的做法），
     * 因此 {@link #getCameraRollDegrees} 返回 {@code +roll}，由 {@code GameRendererTiltMixin}
     * 在<b>相机坐标系</b>里施加（{@code mulPose} 是右乘，故轴必须先换算到相机系）。
     * <p>
     * <b>轴的方向约定（调用方必须知道）</b>：本方法返回的是车厢的<b>世界前向</b>
     * {@code f = (sin yaw·cos pitch, sin pitch, cos yaw·cos pitch)}：它指向车头前进方向，
     * 与 {@code mtr.data.Rail} 参数增大方向一致，也是 {@code Train.calculateCar} 的
     * {@code atan2(末端−首端)} 给出的方向。车体的世界旋转是 {@code R_f(−roll)}。
     * <p>
     * <b>角度符号由调用方决定，且必须是 {@code −roll·strength}</b>（见 {@code GameRendererTiltMixin}）：
     * {@code mulPose} 是「在栈顶矩阵所消费的坐标系里右乘」，所以要先把 {@code f} 变换到那个
     * 坐标系（相机坐标系）里再旋转，而不是直接拿世界系的 {@code f} 去右乘。
     *
     * @return 单位纵轴 {@code {x,y,z}}；没有有效车厢 / 数据非有限时返回 {@code null}
     *         （调用方<b>不旋转</b>，宁可退回原生画面，也绝不用零长度轴去建四元数）
     */
    public static double[] getCameraTiltAxis() {
        final RidingTilt tilt = ridingTilt;
        if (tilt == null) {
            return null;
        }
        final double length = Math.sqrt(
                tilt.axisX * tilt.axisX + tilt.axisY * tilt.axisY + tilt.axisZ * tilt.axisZ);
        if (!Double.isFinite(length) || length < 1.0E-9D) {
            return null;
        }
        return new double[]{tilt.axisX / length, tilt.axisY / length, tilt.axisZ / length};
    }

    /**
     * 把<b>世界系</b>的轴 {@code axis} 变换到<b>相机坐标系</b>里，即
     * {@code R_cam⁻¹ · axis}，其中 {@code R_cam = Ry(camYaw)·Rx(camPitch)} 是
     * {@code GameRenderer.renderLevel} 在注入点处已经压在世界栈上的那个相机旋转。
     * <p>
     * <b>为什么必须做这一步</b>：{@code PoseStack.mulPose(q)} 是右乘，即
     * {@code M ← M·R_a(θ)}，而 {@code M·R_a(θ) = R_{M·a}(θ)·M}。所以传入的四元数轴是
     * 「{@code M} 所消费的那个空间」里的轴。在注入点处那个空间是<b>相机坐标系</b>，
     * 因此要施加「绕世界系 {@code f} 的旋转」，必须在相机坐标系里传
     * {@code R_cam⁻¹·f}（同一个角度、同一根直线，只是换了表达空间）：
     * {@code M·R_{R_cam⁻¹f}(θ) = R_f(θ)·M}。
     * <p>
     * <b>实测证据</b>（{@code .tmp_p5_7_probe/P57Probe.java} 第 (4) 节，覆盖
     * 2 个车头朝向 × 5 个坡度 × 24 个相机偏航 × 8 个视线方向 = 1920 个样本）：
     * <ul>
     *   <li>本换算 + {@code θ = +roll} 与「物理理想」逐元素最大差 1.4e−08（机器精度）；</li>
     *   <li>直接拿<b>世界系</b>的 {@code f} 去右乘（旧写法）在相机偏离车头 90°/180° 时误差
     *       达到 0.156 / 0.313（= 2·|roll| 量级）；</li>
     *   <li>拿<b>视线轴</b>去右乘（MTR4 的旧实现）误差恒为屏幕空间滚转，正侧向看时车内
     *       完全歪掉。</li>
     * </ul>
     * 本方法只做一次 3×3 乘法，纯 {@code double}，不引用任何客户端类型。
     *
     * @param axis   世界系单位轴
     * @param camYaw 相机世界偏航（弧度，从 +Z 起算，与 {@code Ry} 一致）
     * @param camPitch 相机俯仰（弧度）
     * @return 相机坐标系里的同一根轴；输入无效时返回 {@code null}
     */
    public static double[] cameraSpaceAxis(double[] axis, float camYaw, float camPitch) {
        if (axis == null || axis.length != 3) {
            return null;
        }
        final double cy = Math.cos(camYaw);
        final double sy = Math.sin(camYaw);
        final double cp = Math.cos(camPitch);
        final double sp = Math.sin(camPitch);
        // 探针里有一条自检：{@code cameraRotation(camYaw,camPitch)} 与
        // {@code new Matrix4f().rotateY(camYaw).rotateX(camPitch)} <b>逐位相同</b>
        // （max 0.000e+00），而世界栈持有的 {@code m0 = cameraRotation(...)⁻¹}。所以
        // {@code R_cam = m0⁻¹ = Ry(camYaw)·Rx(camPitch)}，本方法必须用它的<b>行</b>点乘：
        //   R_cam = [  cy,  sy·sp,  sy·cp ]
        //           [   0,     cp,    -sp ]
        //           [ -sy,  cy·sp,  cy·cp ]
        //
        // 有了 R_cam，{@code m0·R_{R_cam·f}(θ) = R_f(θ)·m0} 是恒等式，与 f 无关。
        //
        // ⚠ <b>未解决</b>：当车厢<b>有坡度</b>（carPitch ≠ 0）时，本式与 ANTE 的
        // {@code rotation.mul(q)} 仍有残差，且残差随坡度线性增长 ——
        // 坡度 9.09° 时 |production − ANTE| = 1.580e−01 ≈ sin(9.09°)；把轴的 pitch 分量
        // 反向则翻倍到 3.129e−01。坡度恰好为 0 时残差为 1.2e−07（24 个相机偏航 × 8 个视线
        // 俯仰全部通过）。补正旋转 {@code D = mAnte·mProd⁻¹} 的轴<b>随相机视线方向变化</b>
        // （camRelLook=165° 时轴 (−0.20,−0.18,0.96)、180° 时 (−0.21,−0.23,0.95)），
        // 说明两者在有坡度时不是同一个运算，而不是差一个常数符号。
        // 详见 .tmp_p5_7_probe 的 (B) 节输出与报告。
        final double x = cy * axis[0] + sy * sp * axis[1] + sy * cp * axis[2];
        final double y = cp * axis[1] - sp * axis[2];
        final double z = -sy * axis[0] + cy * sp * axis[1] + cy * cp * axis[2];
        final double length = Math.sqrt(x * x + y * y + z * z);
        if (!Double.isFinite(length) || length < 1.0E-9D) {
            return null;
        }
        return new double[]{x / length, y / length, z / length};
    }

    /**
     * 镜头滚转真正被施加到世界矩阵上（一次性正向 INFO + 计数）。
     * <p>
     * 与 {@link #probeCameraTiltFrame} 分开：前者证明<b>注入点解析成功</b>，
     * 本方法证明<b>画面真的转了</b>。两者都能静默失败，因此必须能分别观测。
     */
    public static void markCameraTiltApplied() {
        cameraAppliedSamples++;
        if (!firstCameraTiltLogged) {
            firstCameraTiltLogged = true;
            Main.LOGGER.info("[P5-7] 镜头随车体滚转已生效：世界 PoseStack 已绕车厢世界纵轴按车体同一方向滚转"
                    + "（配置 cameraTiltEnabled={}，cameraTiltStrength={}）",
                    FangSuConfig.cameraTiltEnabled(), FangSuConfig.cameraTiltStrength());
        }
    }

    /**
     * P5-7 的一次性诊断：每一个 {@code require = 0} 的钩子失效时都只会「静默不生效」，
     * 而那与「本来就没有超高」在游戏里完全同形。这里在「确实存在带滚转的车厢」这个
     * 证据前提下把失效点报出来，每个会话最多各一条。
     * <p>
     * 由 {@link #beginRidingFrame()} / {@link #markRidingPlayerPosition} /
     * {@link #applyRidingPlayerRoll} / {@link #probeCameraTiltFrame()} 共同调用，
     * 互相兜底：只要有一个钩子还活着，其余钩子的静默失效就能被报出来。
     */
    private static void reportRidingHooks() {
        // 证据前提：本会话确实出现过「玩家坐在带滚转的车厢里」。
        // 没有这个前提时玩家可能根本没接触过该特性，任何告警都是噪音。
        if (ridingRolledSamples == 0) {
            return;
        }
        // 与 P5-6 同一条纪律：给足观察窗口再报警，避免刚上车那一帧就误报。
        if (cameraHookSamples < HOOK_PROBE_SAMPLES && ridingPlayerAppliedSamples < 1) {
            return;
        }
        if (!ridingHookAlive && !warnedRidingHook) {
            warnedRidingHook = true;
            Main.LOGGER.warn("[P5-7] VehicleRidingClient.movePlayer 的钩子从未触发：乘车滚转上下文永远不会被清除。"
                    + "请用 javap 复核 movePlayer(Ljava/util/function/Consumer;)V 是否仍然存在");
        }
        if (ridingHookAlive && ridingPlayerAppliedSamples == 0 && !warnedRidingPlayerRedirect) {
            warnedRidingPlayerRedirect = true;
            Main.LOGGER.warn("[P5-7] 已经检测到 " + ridingRolledSamples + " 次「本地玩家在带滚转车厢上」，"
                    + "但 setOffsets 里 Vec3.xRot 的重定向一次都没触发：玩家站位<b>不会</b>随地板倾斜。"
                    + "请用 javap 复核 setOffsets 的 17 参数描述符与其中唯一的 xRot 调用点是否仍然存在");
        }
        if (cameraHookSamples == 0 && !warnedCameraHook) {
            warnedCameraHook = true;
            Main.LOGGER.warn("[P5-7] GameRenderer.renderLevel → LevelRenderer.prepareCullFrustum 的镜头钩子从未触发："
                    + "地平线不会随车体滚转。请用 javap 复核 renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V 与 "
                    + "prepareCullFrustum(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;…Matrix4f;)V 是否仍然存在");
        }
        if (cameraHookSamples > 0 && cameraAppliedSamples == 0 && !warnedCameraNeverApplied) {
            warnedCameraNeverApplied = true;
            Main.LOGGER.warn("[P5-7] 镜头钩子已触发 " + cameraHookSamples + " 帧，但一次都没有真正施加过滚转；"
                    + "若此时你正坐在一辆明显倾斜的车厢里，请检查 cameraTiltEnabled / cameraTiltStrength 配置"
                    + "（当前 enabled=" + FangSuConfig.cameraTiltEnabled()
                    + ", strength=" + FangSuConfig.cameraTiltStrength() + "）");
        }
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
        // P5-7 的乘车 / 镜头钩子各自有各自的一次性告警（证据前提与 P5-6 不同：那边看的是「轨道」，
        // 这边看的是「玩家确实坐在带滚转的车厢里」），因此在同一处一并驱动，保证两边只要有一个
        // 钩子还活着，另一侧的静默失效就能被报出来。
        reportRidingHooks();
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

    /** 探针 / 诊断读取（P5-7）：累计「本地玩家位于带滚转车厢上」的 tick 数。 */
    public static int ridingRolledSampleCount() {
        return ridingRolledSamples;
    }

    /** 探针 / 诊断读取（P5-7）：乘车玩家站位重定向真正生效的次数。 */
    public static int ridingPlayerAppliedSampleCount() {
        return ridingPlayerAppliedSamples;
    }

    /** 探针 / 诊断读取（P5-7）：镜头钩子被调用的次数（证明注入点解析成功）。 */
    public static int cameraHookSampleCount() {
        return cameraHookSamples;
    }

    /** 探针 / 诊断读取（P5-7）：镜头滚转真正施加到世界矩阵上的次数。 */
    public static int cameraAppliedSampleCount() {
        return cameraAppliedSamples;
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
        // P5-7
        ridingTilt = null;
        ridingHookAlive = false;
        ridingRolledSamples = 0;
        ridingPlayerAppliedSamples = 0;
        cameraHookSamples = 0;
        cameraAppliedSamples = 0;
        firstRidingTiltLogged = false;
        firstCameraTiltLogged = false;
        warnedRidingHook = false;
        warnedRidingPlayerRedirect = false;
        warnedCameraHook = false;
        warnedCameraNeverApplied = false;
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
