package com.fangsu.mtr.rail;

import com.fangsu.Main;
import com.fangsu.mappings.rail.RailGeometryCore;
import com.fangsu.mappings.rail.RailPoseExtra;
import com.fangsu.mappings.rail.RailRollProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.data.Rail;
import net.minecraft.core.Direction;

import java.util.function.BiConsumer;

import static mtr.data.IGui.SMALL_OFFSET;

/**
 * MTR3 外轨超高（P5-3）的<b>横断面滚转</b>实现：把 MTR 画出来的那条轨面带
 * （{@code RenderTrains.renderRailStandard} 里的 16-float {@code IDrawing.drawTexture}）的四个角点，
 * 绕<b>该截面中心线点</b>沿<b>轨道前进轴</b>旋转内核给出的滚转角。
 * <p>
 * <b>数据流（MTR3 3.2.2 的真实调用链，已对真实 jar 的字节码核实）</b>
 * <pre>
 *   RenderTrains.render(EntitySeat,float,PoseStack,MultiBufferSource)                     【每帧】
 *     → lambda$render$7 / lambda$render$8 → renderRailStandard(Level,Rail,F,Z,F)          【同步】
 *       → renderRailStandard(…, String, F,F,F,F)                                        【同步】★ 捕获 Rail
 *         → Rail.render(RenderRail, -railWidth, +railWidth)                             【同步】★ 捕获两段弧长
 *           → Rail.renderSegment(...) → callback.renderRail(x1,z1,…,x4,z4,y1,y2)         【每个截面一次】★ 累计弧长
 *             → lambda$renderRailStandard$15（单行道箭头）/ $16（轨面）
 *               → RenderTrains.scheduleRender(ResourceLocation,Z,QueuedRenderLayer,BiConsumer)  【同步排队】★ 包装消费者
 *                 → …本帧稍后（渲染线程）执行 lambda$renderRailStandard$15 / $16…
 *                   → IDrawing.drawTexture(PoseStack,VertexConsumer,F×16,Direction,II)   【真正的绘制】★ 旋转四角
 * </pre>
 * <b>为什么必须「捕获当前轨道 + 包装延迟消费者」而不是直接改 {@code renderSegment}</b>：
 * {@code renderSegment} 的四个角点与 {@code y1/y2} 被 <b>信号</b>
 * （{@code RenderTrains.renderSignalsStandard}，源码 :495-519，走同一条 16-float 绘制调用）
 * 与<b>单行道箭头</b>共用；且在 {@code renderRailStandard} 内部还会被调用<b>两次</b>
 * （{@code offsetRadius1/2 = ∓railWidth} 与 {@code ±railWidth}，几何相同、方向相反）。
 * 一旦无差别改写 {@code renderSegment}，信号会跟着倾斜、轨面会画两遍。
 * 因此本类只作用于「10 参数 {@code renderRailStandard} 同步窗口内排队的那两个消费者」，
 * 与 MTR4 的 {@code RenderRailsMixin} 同构（那边也是「捕获 Rail + 包装排队消费者 + 只转轨面」）。
 * <p>
 * <b>四角点重排的由来（不是随手写的）</b>：{@code lambda$renderRailStandard$16} 的字节码对
 * <b>同一组</b> 16 个 float 调用<b>两次</b> {@code drawTexture}，第二次是同一工艺面、绕序相反的背面。
 * 但第二次的 y 偏移位置与第一次<b>不一致</b>（同一空间点承担的 {@code +SMALL_OFFSET} 在两次之间交换）：
 * <pre>
 *   第 1 次： x1,y1    x2,y1+ε  x3,y2    x4,y2+ε     （+ε 落在「当前截面（t2）」一侧）
 *   第 2 次： x2,y1+ε  x1,y1    x4,y2+ε  x3,y2       （+ε 落回「上一截面（t1）」一侧）
 * </pre>
 * （逐字节核实自 {@code lambda$renderRailStandard$16} 的 {@code javap -p -c} 输出：两次调用的
 * 16 个 float 在栈上完全一致，只是入栈顺序不同；第 2 次的槽位是第 1 次角点的置换
 * {@code {2,1,4,3}}。）
 * 若照抄角点顺序各自旋转，两次旋转的「横向偏移」归类会不同 → 两个面不再重合 → 背面与正面
 * 互相穿插（z-fighting / 法线反向）。所以这里用<b>第 1 次的角点身份</b>作为唯一标准：第 2 次的
 * 4 个槽位先用置换映回第 1 次的角点身份，旋转统一在标准角点上算完，再按各自槽位的角点身份取回。
 * 这样<b>刚体旋转保持绕序</b>、两次绘制保持重合（唯一残留是那对 {@code ±SMALL_OFFSET}，
 * 量级 3.1e-3 格，本来就用于防 z-fighting，远小于任何可见穿帮）。
 * <p>
 * <b>旋转数学</b>：设该截面的角点 1 与 4 在 {@code offsetRadius1} 侧、2 与 3 在 {@code offsetRadius2} 侧
 * （MTR3 {@code Rail.renderSegment} 源码 :429-432 的顺序），令
 * <pre>
 *   C  = 四角点中心（y 取内核已经抬升过的中心线高度，x/z 与 MTR 自己画的完全一致）
 *   T  = 归一化(meanP2 - meanP1)，meanPj = 第 j 段截面的四角点中心（水平）
 *   N  = 归一化((角点2 + 角点3) - (角点1 + 角点4))，方向 = offsetRadius 增大方向
 *   P  = (C 的水平位置, C.y)  ← 该处中心线上、抬升后的那个点
 *   θ  = -roll（见 {@link #railFrameAngle}）
 * </pre>
 * 每个角点绕「过 P、方向 T」的轴右手旋转 θ（Rodrigues 公式）。横向偏移 ρ 的角点因此获得
 * {@code Δy = -ρ·sin θ = +ρ·sin roll}：{@code -N} 侧（offsetRadius 小的一侧）<b>抬升</b>
 * {@code ρ·|sin roll|}、{@code +N} 侧<b>下降</b>同样多。<b>这是唯一与内核自洽的取法</b>：
 * 内核的 {@code rollLift} 把中心线抬 {@code ρ·|sin roll|}，只有「一侧留在原标高、另一侧抬
 * {@code 2ρ·|sin roll|}」的刚体旋转才能让中心线正好等于 {@code 原标高 + ρ·|sin roll|}
 * （{@code θ = +roll} 会把两侧都抬到原标高之上，中心线变成 {@code 原标高 + 2ρ·sin roll}，
 * 与内核的中心线抬升矛盾，并让轨面穿出地面）。
 * <p>
 * <b>枢轴取「抬升后的中心线」而不是「未抬升的中心线」—— 与 ANTE（MTR3 侧已经发布的实现）
 * 的算术逐项对照如下</b>（ANTE 源码在 {@code mtr3/mtr-ante-alpha/}，可读）：
 * <ol>
 *   <li>ANTE 的 {@code RailMixin.getPositionY} 在 MTR 自己的 {@code _getPositionY} 之上
 *       <b>加了</b> {@code rollingYOffset(value)}，而后者是
 *       {@code |Vec2(rollingOffset, 0).rotateRad(roll).z| = rollingOffset·|sin(roll)|}
 *       （{@code rollingOffset} 默认 0.7175，正是半轨距）—— <b>与内核的 {@code rollLift} 同构</b>。
 *       因此 ANTE 的 {@code rail.getPosition(v)} 返回的 y 已经是<b>抬升过</b>的中心线；</li>
 *   <li>ANTE 画横断面的 {@code BakedRail.getLookAtMat(pos, last, next, roll, …)} 第一步是
 *       {@code translate(pos.x, pos.y, pos.z)}，其中 {@code pos} 就是
 *       {@code rail.getPosition(...)} 的中点（{@code MeshBuildingRailChunk.getLookAtMat} / 构造器里的
 *       {@code mid}），<b>最后</b>才 {@code rotateZ(±roll)}。也就是说：<b>围绕抬升后的中心线滚转</b>；</li>
 *   <li>于是 ANTE 的「内轨不抬、外轨抬 {@code 2ρ|sin roll|}、中心线抬 {@code ρ|sin roll|}」
 *       与内核的 {@code rollLift} 严格自洽，本类采用的枢轴与此<b>完全一致</b>。</li>
 * </ol>
 * <b>符号差异（必须在游戏内目视确认）</b>：ANTE 用 {@code rotateZ(reverse ? -roll : roll)}，
 * MTR4 的 {@code railFrameAngle} 用 {@code -roll}。两者差一个符号，但<b>只要与各自的
 * 「哪一侧是外轨」约定配套就都自洽</b>；本类采用 MTR4 的 {@code -roll}，并且这个符号是
 * <b>唯一能让「一侧留在原标高」成立</b>的取值（{@code +roll} 会把两侧都抬到原标高之上、
 * 让内轨穿地）。若在游戏内看到倾斜方向与作者意图相反，那就是「节点帧 pSign ↔ 轨道帧」的
 * 符号约定与本式不匹配，属于需要一次目视确认的一比特问题，而不是几何错误。
 * <p>
 * <b>逐位无操作保证</b>：{@link #buildTiltedQuad} 在下列任一情况返回 {@code null}，
 * 调用方随即把<b>原始 16 个 float 原样转发</b>（一个浮点运算都不做）：
 * <ul>
 *   <li>不在轨面同步窗口内（普通 MTR 轨道预览、信号、单行道箭头、任何别的 4 参数排队）；</li>
 *   <li>轨道没有附加姿态，或姿态没有滚转（{@code RailPoseExtra.hasRoll()} 为假）；</li>
 *   <li>内核拒绝构建（长度退化等）、该参数处滚转为 0、或几何退化到无法确定轴 / 枢轴。</li>
 * </ul>
 * x/z 除刚体旋转外不被单独改动，u/v、light、color、{@code Direction} 一律原样透传。
 * <p>
 * <b>NTE 存在时本路径完全不生效（预期行为，不是缺陷）</b>：NTE（{@code mtrsteamloco}）会
 * {@code @Inject} 到 MTR 的 {@code renderRailStandard} 上并 {@code CallbackInfo.cancel()}，
 * 用它自己的 {@code RailRenderDispatcher} 画轨道；MTR 的轨面根本不会被绘制，本类的
 * {@code @Redirect} 因此一次也不会被调用。<b>这正是 MTR3 需要两条渲染路径的原因</b>
 * （NTE 侧是 P5-4 / 路径 B 的范围）。本类只做路径 A。
 */
public final class RailTiltRenderHelper {

    /**
     * 轨面排队 Lambda 的注入点（MTR3 3.2.2：单行道箭头 = {@code $15}、轨面 = {@code $16}）。
     * <p>
     * 对 1.18.2 与 1.20.1 的已发布 jar 都用 {@code javap -p} 核实过命名与参数表（逐字节相同）。
     * 1.19.x 的 jar 本地没有缓存，无法核实；因此 {@code @Redirect} 全部带 {@code require = 0}，
     * 失效只降级为「不倾斜」，由本类的一次性诊断报出来（绝不崩游戏）。
     */
    public static final String RAIL_ARROW_SCHEDULE_TARGET = "lambda$renderRailStandard$15";
    /** 轨面（本特性真正的目标）。见 {@link #RAIL_ARROW_SCHEDULE_TARGET}。 */
    public static final String RAIL_QUAD_SCHEDULE_TARGET = "lambda$renderRailStandard$16";

    /**
     * 轨面绘制的注入点（MTR3 3.2.2：16-float {@code IDrawing.drawTexture}）。
     * <p>
     * 箭头的 16-float 调用与轨面在字节码里是<b>同一个</b> {@code invokestatic}（同一个常量池项
     * {@code #968}），两者只差所在 lambda，而这个重定向已按方法名锁定在 {@code $15}/{@code $16} 上，
     * 所以两个 lambda 各挂一个同名重定向即可；信号（不走 lambda、也不进同步窗口）天然被排除。
     */
    public static final String RAIL_QUAD_DRAW_TARGET = "lambda$renderRailStandard$16";

    /** 10 参数 {@code renderRailStandard}（捕获「当前正在渲染的轨道」的注入点）。 */
    public static final String RENDER_RAIL_STANDARD_10_DESCRIPTOR = "renderRailStandard";

    /**
     * 两条截面之间的最小水平距离（格）：短于它就认为四角点退化（极短轨道的末端采样），
     * 此时退回内核的中心线切线，或干脆不旋转。与 MTR4 的 {@code DEGENERATE} 同义。
     */
    private static final double DEGENERATE = 1.0E-6D;

    /** 求切线时沿参数方向取的前后采样步长（格）。与 MTR4 的 {@code TANGENT_SAMPLE} 一致。 */
    private static final double TANGENT_SAMPLE = 0.5D;

    /**
     * 渲染线程上下文：当前正在绘制的、带滚转的轨道截面快照。
     * <p>
     * 为什么用 {@link ThreadLocal}：排队（同步窗口，写）与真正的 {@code drawTexture}
     * （本帧稍后，渲染线程，读）不在同一次调用里，而 MTR3 的排队消费者签名是
     * {@code BiConsumer<PoseStack, VertexConsumer>} —— 没有槽位可以带 {@code Rail} 进去，
     * 因此快照只能走线程上下文，与 MTR4 的 {@code ACTIVE_FRAME} 完全同构。
     */
    private static final ThreadLocal<RollFrame> ACTIVE_FRAME = new ThreadLocal<>();

    /**
     * 当前同步窗口内正在渲染的轨道；{@code null} = 窗口外 / 该轨道无滚转。
     * <p>
     * {@code volatile}：{@code renderRailStandard} 在客户端主线程（排队窗口）写，MTR 的
     * {@code scheduleRender} 在同一线程立刻读；用 volatile 是为了不依赖「一定同线程」这一假设。
     * 它是单槽覆盖而不是栈，永远不会累积残值。
     */
    private static volatile Rail pendingRail = null;

    /**
     * 每渲染一条轨道的截面累计状态：
     * {@code {本段起始弧长, 本段 increment, 本段已出截面数, 当前截面起始弧长}}。
     * <p>
     * 为什么必须自己数截面：MTR3 给回调的 10 个 double 里<b>没有</b>参数值，而滚转剖面
     * （尤其逐轨道超高的三点剖面）以<b>归一化位置</b>为键；相位错了，超高就会落在错误的位置。
     * {@code renderSegment} 的循环是 {@code i = 0, increment, 2·increment, …}，
     * 且 {@code increment = count / round(count)}（源码 :424），因此「第 n 个截面」的弧长参数
     * 就是 {@code n · increment}（{@code n} 为该段内的截面序号），再加上该段的起始偏移。
     */
    private static final ThreadLocal<double[]> QUAD_PROGRESS = ThreadLocal.withInitial(() -> new double[4]);

    /** {@link #QUAD_PROGRESS} 里「当前截面起始弧长」的下标。 */
    private static final int PROGRESS_CURRENT_BASE = 3;

    // ==================== 一次性诊断 ====================
    //
    // 与 MTR4 的教训直接相关：那边有一版 @ModifyVariable 写错槽位，require = 0 又把失败藏成静默，
    // 结果轨道「整体被抬高但完全不倾斜」，排查了好几轮。因此每个可能静默失效的钩子都必须能被日志区分。

    private static volatile boolean captureHookAlive = false;
    private static volatile boolean segmentHookAlive = false;
    private static volatile boolean scheduleHookAlive = false;
    private static volatile boolean drawHookAlive = false;

    /** 渲染入口看到过多少次轨道截面（同步窗口计数：不依赖任何 require = 0 的钩子，可当看门狗时钟）。 */
    private static volatile int sectionSamples = 0;
    /** {@link #buildTiltedQuad} 真正旋转过多少次（>0 即「倾斜已生效」）。 */
    private static volatile int tiltedQuadSamples = 0;

    private static boolean firstTiltLogged = false;
    /** 「钩子活性」自检只做一次（每一条 warn 也各自只打一次）。 */
    private static boolean hookLivenessChecked = false;
    /** 「排队包装没挂上」的 warn 只打一次。 */
    private static boolean scheduleMissingWarned = false;

    private RailTiltRenderHelper() {
    }

    // ==================== 钩子 1：捕获「当前正在渲染的轨道」 ====================

    /**
     * {@code @ModifyVariable(argsOnly = true, index = 1)} 的处理器：捕获 10 参数
     * {@code renderRailStandard} 的第 2 个参数（{@code mtr.data.Rail}）。
     * <p>
     * <b>为什么是 index = 1 且必须是 {@code @ModifyVariable}</b>：该方法第 4 个参数是
     * {@code String}（可命名），第 2 个参数是唯一能拿到 {@code Rail} 的地方。MTR4 的原始实现
     * 在这里曾写成 {@code index = 0}（那是 {@code ClientWorld}），处理器类型不匹配，
     * 注入点<b>一次都不会触发</b>且 {@code require = 0} 把它藏成静默 —— 见类注释。
     * 本方法的参数表已用 {@code javap -p -c} 核实：
     * {@code (Level, Rail, float, boolean, float, String, F,F,F,F)}，{@code Rail} 在槽位 1。
     * <p>
     * <b>严格只读</b>：把原值原样返回，绝不修改入参。
     * 同步窗口不在这里清除 —— 消费者可能在本方法返回后才执行，清理由
     * {@link #endRailSection()}（同一个 10 参数方法上的 {@code @At("RETURN")}）负责。
     */
    public static Rail captureSectionRail(Rail rail) {
        captureHookAlive = true;
        sectionSamples++;
        pendingRail = frameFor(rail) == null ? null : rail;
        QUAD_PROGRESS.get()[2] = 0.0D;
        if (pendingRail != null) {
            // 只有「真的需要倾斜」的截面才触发活性自检（见 verifyTiltHooksAlive）
            verifyTiltHooksAlive();
        }
        return rail;
    }

    /**
     * 钩子活性自检：<b>由带滚转轨道的第一次同步窗口</b>触发一次，之后永不再跑。
     * <p>
     * 时机选的不是「第 N 个截面」，而是「第一次真的需要倾斜」—— 这样每一条 warn 都直接对应
     * 「这个特性本该工作了」。若在 120 个截面之后才检查，玩家把视角对着无超高轨道时就会误报。
     * <p>
     * 三条判据：
     * <ol>
     *   <li>{@code Rail.render}/{@code renderSegment} 注入没生效 → 相位可能落错位置；</li>
     *   <li>排队包装没生效 → 轨面完全不会倾斜（最致命的一条）；</li>
     *   <li>绘制重定向没生效 → 同样完全不会倾斜，但排队计数会走。</li>
     * </ol>
     * 本方法由 {@code captureSectionRail}（{@code @ModifyVariable} 钩子）调用，所以「能跑到这里」
     * 本身就证明捕获钩子是活的 —— 这正是 MTR4 那条「用必定会被调用的注入点当参照」的纪律：
     * 否则一旦所有 {@code require = 0} 的钩子同时失效，计数恒为 0，日志会一片空白。
     */
    public static void verifyTiltHooksAlive() {
        if (hookLivenessChecked) {
            return;
        }
        hookLivenessChecked = true;
        Main.LOGGER.info("[P5-3] 外轨超高的轨道截面窗口已建立（捕获钩子 {} 生效，已处理 {} 个截面）",
                RENDER_RAIL_STANDARD_10_DESCRIPTOR, sectionSamples);
        if (!segmentHookAlive) {
            Main.LOGGER.warn("[P5-3] 轨道截面弧长钩子未生效（Rail.render / renderSegment 注入失败）。"
                    + "轨面仍会倾斜，但逐轨道超高（三点剖面）的相位可能落错位置。");
        }
    }

    /**
     * 10 参数 {@code renderRailStandard} 的 {@code @At("RETURN")}：结束同步窗口。
     * <p>
     * 只清同步窗口状态（避免旧实现那种「长期持有 Rail」的泄漏）；{@link #ACTIVE_FRAME}
     * 是渲染线程上下文，由消费者自己设置并还原。
     */
    public static void endRailSection() {
        pendingRail = null;
        final double[] progress = QUAD_PROGRESS.get();
        progress[0] = 0.0D;
        progress[1] = 0.0D;
        progress[2] = 0.0D;
    }

    // ==================== 钩子 2：捕获两段弧长（滚转剖面的相位） ====================

    /**
     * {@code Rail.render(RenderRail,F,F)} 的 {@code @Inject(at = HEAD)}：
     * 记录本轨道的两段弧长 {@code count1 = |tEnd1 - tStart1|}、{@code count2 = |tEnd2 - tStart2|}。
     * <p>
     * 这两个量与 {@code Rail.getPosition(double)} 的分段判据、{@code renderSegment} 的两个区间
     * 长度完全相同（源码 :350-351 / :423）。<b>由 mixin 侧读字段</b>：它们是 {@code Rail} 的
     * {@code private final}，只有持有目标类的 mixin 能 {@code @Shadow}；本类只做纯粹算术，
     * 因此探针可以直接用真实数值驱动。
     */
    public static void prepareRailRender(double count1, double count2) {
        segmentHookAlive = true;
        final double[] lengths = SEGMENT_LENGTHS.get();
        lengths[0] = count1;
        lengths[1] = count2;
        final double[] progress = QUAD_PROGRESS.get();
        progress[0] = 0.0D;
        progress[1] = 0.0D;
        progress[2] = 0.0D;
    }

    /** 两段圆弧的弧长（{@link #prepareRailRender} 写入）。 */
    private static final ThreadLocal<double[]> SEGMENT_LENGTHS = ThreadLocal.withInitial(() -> new double[2]);

    /**
     * 开始一段 {@code renderSegment}：{@code rawValueOffset} 是该段在整条轨道弧长上的起始偏移。
     * <p>
     * {@code javap -p -c} 核实 {@code Rail.render} 的两个调用点：第 1 个传 {@code 0}、
     * 第 2 个传 {@code |tEnd1 - tStart1|}（= 第 1 段的弧长），因此 {@code rawValueOffset}
     * 本身既是「本段起始弧长」，也是「本段是不是第 2 段」的判据。
     * <p>
     * <b>increment 用一个与 {@code renderSegment} 完全相同的公式算</b>：
     * {@code increment = count / Math.round(count)}（源码 :424）。段长来自
     * {@link #prepareRailRender}。第 1 段的 {@code count1} 为 0 时（退化轨道）两段的 offset 都是 0，
     * 此时 increment 取 {@code count1} 的步长（退化轨道本来就不会倾斜，差异不可观测）。
     * <p>
     * 本方法是<b>每个截面</b>都会走的（两个 {@code renderSegment} 调用点各一次），所以
     * 「当前截面的起始弧长」（{@code progress[3]}）在这里<b>取一次就推进一次</b>：
     * 回调 → 本方法 → 排队 → 固定 base，四步在同一个同步窗口内严格同序。
     */
    public static void beginRailSegment(double rawValueOffset) {
        segmentHookAlive = true;
        final double[] lengths = SEGMENT_LENGTHS.get();
        final double count = rawValueOffset != 0.0D ? lengths[1] : lengths[0];
        final double[] progress = QUAD_PROGRESS.get();
        progress[0] = rawValueOffset;
        progress[1] = count > 0.0D ? count / Math.round(count) : 0.0D;
        // 第 4 格：当前截面的起始弧长参数（= 本段起始 + 已出截面数 × 步长）
        progress[PROGRESS_CURRENT_BASE] = progress[0] + progress[2] * progress[1];
        progress[2] += 1.0D;
    }

    /**
     * 当前截面的起始弧长参数（由 {@link #beginRailSegment} 计算）。
     * <p>
     * <b>无副作用</b>：{@link #wrapQuadConsumer} 在排队那一刻把它的值固定进闭包，
     * 绘制时不再读这个状态（排队与绘制不在同一时刻）。
     */
    public static double quadParameterRange() {
        return QUAD_PROGRESS.get()[PROGRESS_CURRENT_BASE];
    }

    // ==================== 钩子 3：包装延迟绘制的消费者 ====================

    /**
     * {@code @Redirect} 在 {@code RenderTrains.scheduleRender(ResourceLocation,Z,QueuedRenderLayer,BiConsumer)}
     * 上的处理器（注入点锁定在 {@link #RAIL_ARROW_SCHEDULE_TARGET} / {@link #RAIL_QUAD_SCHEDULE_TARGET}）。
     * <p>
     * <b>安全不变式</b>：不在同步窗口内（= 不是 {@code renderRailStandard} 排的队）、轨道没有滚转、
     * 或内核拒绝构建时，<b>原样返回同一个消费者实例</b> —— 纹理、光照、排队顺序与 MTR 原生逐位一致。
     * <p>
     * 包装后的消费者执行时把快照挂到 {@link #ACTIVE_FRAME} 上，因此「真正该转哪条轨道」由排队
     * 那一刻的快照决定，而不是由绘制时刻的全局状态决定；执行完（含异常路径）一定还原。
     * <p>
     * <b>截面相位在排队这一刻就固定下来</b>（{@code base}）：排队与真正的绘制发生在不同时刻，
     * 而 {@link #quadParameterRange} 依赖只在本同步窗口内有意义的计数状态，因此不能在绘制时再读。
     */
    public static BiConsumer<PoseStack, VertexConsumer> wrapQuadConsumer(
            BiConsumer<PoseStack, VertexConsumer> original
    ) {
        scheduleHookAlive = true;
        final Rail rail = pendingRail;
        if (rail == null) {
            return original;
        }
        final RollFrame frame = frameFor(rail);
        if (frame == null) {
            return original;
        }
        final double base = quadParameterRange();
        return (matrices, vertexConsumer) -> {
            final RollFrame previous = ACTIVE_FRAME.get();
            ACTIVE_FRAME.set(frame);
            try {
                ACTIVE_FRAME_BASE.set(base);
                original.accept(matrices, vertexConsumer);
            } finally {
                ACTIVE_FRAME_BASE.remove();
                if (previous == null) {
                    ACTIVE_FRAME.remove();
                } else {
                    ACTIVE_FRAME.set(previous);
                }
            }
        };
    }

    /** 当前正在绘制的截面的起始弧长参数（由 {@link #wrapQuadConsumer} 在排队时固定）。 */
    private static final ThreadLocal<Double> ACTIVE_FRAME_BASE = new ThreadLocal<>();

    // ==================== 钩子 4：真正绘制时旋转四角点 ====================

    /**
     * {@code @Redirect} 在 16-float {@code IDrawing.drawTexture} 上的处理器。
     * <p>
     * 调用方（mixin）必须先用 {@link #normalizeQuad}/{@link #normalizeArrowQuad} 把该次调用的
     * 12 个坐标按角点身份归一，再把 16 个 float 数组交给本方法；这样「第 2 次绘制的槽位置换」
     * 只在一处处理，两个 lambda 共用同一段逻辑。
     * <p>
     * 截面的起始弧长参数取自 {@link #ACTIVE_FRAME_BASE}（{@link #wrapQuadConsumer} 在排队那一刻
     * 固定），因此本方法不需要额外入参，也就不存在「绘制侧参数传错」这一失效模式。
     *
     * @param quad 归一化后的 16 个 float：x1,y1,z1, x2,y2,z2, x3,y3,z3, x4,y4,z4, u1,v1,u2,v2
     */
    public static void drawTiltedQuad(
            PoseStack matrices, VertexConsumer vertexConsumer,
            float[] quad,
            Direction facing, int color, int light
    ) {
        drawHookAlive = true;
        verifyScheduleHookAlive();
        final float[] tilted = buildTiltedQuad(quad);
        if (tilted == null) {
            // 无滚转（含所有原版轨道、信号、箭头、预览）→ 16 个 float 原封不动转发
            mtr.client.IDrawing.drawTexture(
                    matrices, vertexConsumer,
                    quad[0], quad[1], quad[2], quad[3], quad[4], quad[5], quad[6], quad[7],
                    quad[8], quad[9], quad[10], quad[11], quad[12], quad[13], quad[14], quad[15],
                    facing, color, light
            );
            return;
        }
        mtr.client.IDrawing.drawTexture(
                matrices, vertexConsumer,
                tilted[0], tilted[1], tilted[2], tilted[3], tilted[4], tilted[5], tilted[6], tilted[7],
                tilted[8], tilted[9], tilted[10], tilted[11], tilted[12], tilted[13], tilted[14], tilted[15],
                facing, color, light
        );
    }

    /**
     * {@code lambda$renderRailStandard$16}（轨面）的 12 个坐标 → <b>标准角点顺序</b>。
     * <p>
     * 第 1 次调用就是标准顺序；第 2 次调用的槽位用置换 {@code {2,1,4,3}} 映回标准角点身份。
     * u/v 在该 lambda 的两次调用里完全相同，因此不参与置换（由 {@link #drawTiltedQuad} 原样透传）。
     *
     * @param secondPass 是否是同一次绘制里的第 2 次调用（背面）
     */
    public static float[] normalizeQuad(
            boolean secondPass,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float u1, float v1, float u2, float v2
    ) {
        if (secondPass) {
            return new float[]{x2, y2, z2, x1, y1, z1, x4, y4, z4, x3, y3, z3, u1, v1, u2, v2};
        }
        return new float[]{x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, u1, v1, u2, v2};
    }

    /** 单行道箭头（{@code $15}）的 12 个坐标 → 标准角点顺序。角点模式与轨面<b>完全相同</b>。 */
    public static float[] normalizeArrowQuad(
            boolean secondPass,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float u1, float v1, float u2, float v2
    ) {
        return normalizeQuad(secondPass, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, u1, v1, u2, v2);
    }

    /**
     * 把标准顺序的 16 个 float 绕该截面中心线旋转滚转角。
     *
     * @return 旋转后的 16 个 float；<b>不需要旋转</b>时返回 {@code null}，调用方原样转发。
     *         <p>
     *         <b>退化与非有限输入一律按「不旋转」处理</b>：零长度轨道的量会变成 {@code NaN} 甚至
     *         {@code Infinity}，而 {@code Math.max/min} 会传播 {@code NaN}
     *         （{@code NaN} 的一切比较都是 false，会穿透 {@code <} 形式的退化守卫）。
     *         因此每个可能非有限的量都先过 {@link Double#isFinite}；有限但过小的轴长才走
     *         「退回内核切线」。这与 MTR4 的 D-B 修复同一条纪律。
     */
    public static float[] buildTiltedQuad(float[] quad) {
        final RollFrame frame = ACTIVE_FRAME.get();
        if (frame == null) {
            return null;
        }
        // 四角点中心（y 由下面的 pivotY 修正回真正的中心线高度）
        final double centerX = (quad[0] + quad[3] + quad[6] + quad[9]) * 0.25D;
        final double centerZ = (quad[2] + quad[5] + quad[8] + quad[11]) * 0.25D;
        // MTR 给其中一个角点加了 +SMALL_OFFSET 防 z-fighting（见类注释的两次绘制对照表）。
        // 该偏移在<b>本次绘制</b>里平均分给四个角点各 SMALL_OFFSET/4，于是四角点 y 的算术平均
        // 比真正的中心线高 SMALL_OFFSET/2；若不减回来，枢轴就会偏 ε/2，
        // 横向偏移 ρ 的角点会获得 (ρ·sinθ − ε/2) 的竖直位移 —— 探针 (d) 抓到过这个偏差。
        final double pivotY = (quad[1] + quad[4] + quad[7] + quad[10]) * 0.25D - SMALL_OFFSET / 2.0D;

        // 滚转剖面相位：优先用 Rail.renderSegment 的弧长计数给出的参数；
        // 拿不到时退回内核闭式反解（内核的锚点在 MTR3 上是占位直线，反解值仅单调、
        // 与真实弧长有偏移，因此绝不作首选 —— 见 RailGeometrySource#stubAnchors）。
        final Double activeBase = ACTIVE_FRAME_BASE.get();
        final double parameter;
        if (activeBase != null && Double.isFinite(activeBase)) {
            parameter = activeBase;
        } else {
            parameter = frame.core.parameterAt(centerX, centerZ);
        }
        if (!Double.isFinite(parameter)) {
            return null;
        }
        // 全类唯一的角度表达式
        final double roll = frame.core.getRollRadians(parameter);
        final double angle = railFrameAngle(roll, frame.parameterIsPosition1ToPosition2);
        if (!Double.isFinite(angle) || angle == 0.0D) {
            return null;
        }

        // 局部坐标架：N = offsetRadius 增大方向（+railWidth 侧），T = 前进轴（与 N 正交化）
        double forwardX = (quad[6] + quad[9]) * 0.5D - (quad[0] + quad[3]) * 0.5D;
        double forwardZ = (quad[8] + quad[11]) * 0.5D - (quad[2] + quad[5]) * 0.5D;
        double sideX = (quad[3] + quad[6]) * 0.5D - (quad[0] + quad[9]) * 0.5D;
        double sideZ = (quad[5] + quad[8]) * 0.5D - (quad[2] + quad[11]) * 0.5D;
        final double sideLength = Math.sqrt(sideX * sideX + sideZ * sideZ);
        if (!Double.isFinite(sideLength) || sideLength < DEGENERATE) {
            // 角点退化（或本身含 NaN）→ 无法确定横向方向，原样转发
            return null;
        }
        sideX /= sideLength;
        sideZ /= sideLength;

        double forwardLength = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        if (!Double.isFinite(forwardLength)) {
            return null;
        }
        if (forwardLength < DEGENERATE) {
            // 截面退化（例如极短轨道的末端采样）→ 退回内核中心线切线
            final double lo = Math.max(0.0D, parameter - TANGENT_SAMPLE);
            final double hi = Math.min(frame.core.getLength(), parameter + TANGENT_SAMPLE);
            if (!Double.isFinite(hi - lo) || hi - lo < DEGENERATE) {
                return null;
            }
            final double[] ahead = frame.core.getPosition(hi, false);
            final double[] behind = frame.core.getPosition(lo, false);
            forwardX = ahead[0] - behind[0];
            forwardZ = ahead[2] - behind[2];
            forwardLength = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        }
        if (!Double.isFinite(forwardLength) || forwardLength < DEGENERATE) {
            return null;
        }
        forwardX /= forwardLength;
        forwardZ /= forwardLength;
        // 与 N 正交化（两段圆弧相接处 T 与 N 严格正交，这里只为数值稳健）
        final double dotTN = forwardX * sideX + forwardZ * sideZ;
        forwardX -= dotTN * sideX;
        forwardZ -= dotTN * sideZ;
        final double orthogonalLength = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        if (!Double.isFinite(orthogonalLength) || orthogonalLength < DEGENERATE) {
            return null;
        }
        forwardX /= orthogonalLength;
        forwardZ /= orthogonalLength;
        // 反转正交化 N，使 (T, N) 成为水平面内的右手正交基
        final double orthoDot = sideX * forwardX + sideZ * forwardZ;
        sideX -= orthoDot * forwardX;
        sideZ -= orthoDot * forwardZ;
        final double sideNorm = Math.sqrt(sideX * sideX + sideZ * sideZ);
        if (!Double.isFinite(sideNorm) || sideNorm < DEGENERATE) {
            return null;
        }
        sideX /= sideNorm;
        sideZ /= sideNorm;

        // 枢轴水平位置 = 四角点中心在该处水平面内的正投影（= 该处中心线点的 x/z）；y 见 pivotY
        final double alongDot = centerX * forwardX + centerZ * forwardZ;
        final double sideDot = centerX * sideX + centerZ * sideZ;
        final double pivotX = forwardX * alongDot + sideX * sideDot;
        final double pivotZ = forwardZ * alongDot + sideZ * sideDot;
        if (!Double.isFinite(pivotX) || !Double.isFinite(pivotY) || !Double.isFinite(pivotZ)) {
            return null;
        }

        final double cos = Math.cos(angle);
        final double sin = Math.sin(angle);
        final float[] out = new float[16];
        for (int corner = 0; corner < 4; corner++) {
            final int index = corner * 3;
            // 角点身份 0/3 在 offsetRadius1 侧、1/2 在 offsetRadius2 侧
            // （MTR3 Rail.renderSegment 源码 :429-432 的顺序）；Rodrigues 只需要轴与枢轴。
            rotateAboutAxis(out, index,
                    quad[index], quad[index + 1], quad[index + 2],
                    pivotX, pivotY, pivotZ, forwardX, forwardZ, cos, sin);
        }
        System.arraycopy(quad, 12, out, 12, 4);
        markTilted();
        return out;
    }

    /** 「第一次真的倾斜」的一次性 INFO：这是路径 A 已接上的正向证据。 */
    private static void markTilted() {
        tiltedQuadSamples++;
        if (!firstTiltLogged) {
            firstTiltLogged = true;
            Main.LOGGER.info("[P5-3] 轨道横断面滚转已生效：轨面四个角点已绕该处中心线按内核滚转角旋转"
                    + "（注入点 {}，路径 A；安装了 NTE 时本路径不生效，属预期）", RAIL_QUAD_DRAW_TARGET);
        }
    }

    /**
     * 绕经过 {@code (cx, cy, cz)}、方向为世界水平<b>单位</b>向量 {@code (ax, 0, az)} 的轴做右手旋转
     * （Rodrigues 公式），结果写入 {@code out[index..index+2]}。
     * <p>
     * 与 MTR4 的 {@code rotateAboutAxis} 逐字相同（含「轴只有水平分量」这一简化）。
     * {@code crossY = az·vx - ax·vz} 正是「横向偏移 ρ → 竖直位移 {@code ±ρ·sin θ}」的来源。
     */
    private static void rotateAboutAxis(
            float[] out, int index,
            double px, double py, double pz,
            double cx, double cy, double cz,
            double ax, double az, double cos, double sin
    ) {
        final double vx = px - cx;
        final double vy = py - cy;
        final double vz = pz - cz;
        // a = (ax, 0, az)
        final double crossX = -az * vy;
        final double crossY = az * vx - ax * vz;
        final double crossZ = ax * vy;
        final double factor = (ax * vx + az * vz) * (1.0D - cos);
        out[index] = (float) (cx + vx * cos + crossX * sin + ax * factor);
        out[index + 1] = (float) (cy + vy * cos + crossY * sin);
        out[index + 2] = (float) (cz + vz * cos + crossZ * sin + az * factor);
    }

    /**
     * 轨参数系下的滚转角（弧度）—— <b>全类唯一</b>的滚转符号表达式：
     * <pre>
     * angle = -roll * (参数方向 == position1→position2 ? +1 : -1)
     * </pre>
     * 与 MTR4 的 {@code RailRollRenderHelper.railFrameAngle} 逐字相同。MTR3 的
     * {@code Rail} 构造器不做 MTR4 那种端点交换（{@code posStart} 恒为 position1、
     * {@code posEnd} 恒为 position2，已用 {@code javap} 核实只有一个几何构造器），
     * 且 {@code renderSegment} 的参数从 {@code posStart} 端起算，因此这里的
     * {@code parameterIsPosition1ToPosition2} 恒为 {@code true}，角点旋转角就是 {@code -roll}。
     * <p>
     * <b>为什么是 {@code -roll}</b>：见类注释的推导 —— 只有这个符号能让
     * 「offsetRadius 小的一侧留在原标高、另一侧抬到 {@code 2ρ·|sin roll|}」，
     * 从而与内核 {@code rollLift = |halfGauge·sin(roll)|} 的中心线抬升严格自洽。
     * 若把符号改反，轨面会整体沉到原标高之下（内轨穿地），这是肉眼可见的错误。
     */
    public static double railFrameAngle(double rollRadians, boolean parameterIsPosition1ToPosition2) {
        return -rollRadians * (parameterIsPosition1ToPosition2 ? 1.0D : -1.0D);
    }

    // ==================== 快照 ====================

    /**
     * 由轨道构造滚转快照；没有附加姿态 / 没有滚转 / 内核拒绝构建时返回 {@code null}。
     * <p>
     * <b>内核来源与 P5-1 完全同一个</b>：{@link RailGeometryProvider#getFangSuRailGeometryCore()}
     * 按当前姿态惰性构建并缓存，因此轨面、中心线、信号与几何<b>不可能不一致</b>
     * （P5-1 的 {@code getPositionY} 钩子用的就是同一个实例，本类绝不另建内核）。
     */
    public static RollFrame frameFor(Rail rail) {
        if (rail == null) {
            return null;
        }
        if (!(rail instanceof RailPoseExtraHolder holder) || !(rail instanceof RailGeometryProvider provider)) {
            // 理论上不可达：两个接口都由 RailPoseExtraMixin / RailGeometryMixin 实现在 mtr.data.Rail 上。
            // 防线放在这里，是为了「宁可退回 MTR 原生轨面，也绝不抛异常」。
            return null;
        }
        final RailPoseExtra pose = holder.getFangSuPose();
        if (pose == null || !pose.affectsGeometry() || !pose.hasRoll()) {
            return null;
        }
        final RailGeometryCore core = provider.getFangSuRailGeometryCore();
        if (core == null) {
            return null;
        }
        final RailRollProfile profile = core.getRollProfile();
        if (profile == null || profile.isZero()) {
            return null;
        }
        return new RollFrame(core, true);
    }

    /** 一次延迟绘制期间的滚转快照（不可变，可跨线程传递）。 */
    public static final class RollFrame {
        /** 该轨道当前姿态下的内核（与 P5-1 的 {@code getPositionY} 是同一个实例）。 */
        public final RailGeometryCore core;
        /** 参数方向是否就是 {@code position1 → position2}（MTR3 恒为 {@code true}）。 */
        public final boolean parameterIsPosition1ToPosition2;

        RollFrame(RailGeometryCore core, boolean parameterIsPosition1ToPosition2) {
            this.core = core;
            this.parameterIsPosition1ToPosition2 = parameterIsPosition1ToPosition2;
        }
    }

    // ==================== 供探针 / 诊断使用的入口 ====================

    /** 探针专用：把一个快照压进当前线程的渲染上下文并返回还原用的句柄。 */
    public static RollFrame activateFrameForProbe(RollFrame frame) {
        final RollFrame previous = ACTIVE_FRAME.get();
        ACTIVE_FRAME.set(frame);
        return previous;
    }

    /** 探针专用：还原 {@link #activateFrameForProbe} 的上下文。 */
    public static void restoreFrameForProbe(RollFrame previous) {
        if (previous == null) {
            ACTIVE_FRAME.remove();
        } else {
            ACTIVE_FRAME.set(previous);
        }
    }

    /** 探针专用：由内核直接构造一个快照（等价于 {@link #frameFor} 在真实轨道上得到的东西）。 */
    public static RollFrame frameFromCoreForProbe(RailGeometryCore core, boolean parameterIsPosition1ToPosition2) {
        return new RollFrame(core, parameterIsPosition1ToPosition2);
    }

    /** 探针专用：直接以给定的快照调用旋转（等价于渲染线程上的实际路径）。 */
    public static float[] buildTiltedQuadForProbe(RollFrame frame, float[] quad, double base) {
        final RollFrame previous = activateFrameForProbe(frame);
        final Double previousBase = ACTIVE_FRAME_BASE.get();
        ACTIVE_FRAME_BASE.set(base);
        try {
            return buildTiltedQuad(quad);
        } finally {
            if (previousBase == null) {
                ACTIVE_FRAME_BASE.remove();
            } else {
                ACTIVE_FRAME_BASE.set(previousBase);
            }
            restoreFrameForProbe(previous);
        }
    }

    /** 供诊断读取：绘制重定向是否触发过。 */
    public static boolean wasDrawHookAlive() {
        return drawHookAlive;
    }

    /** 供诊断读取：捕获钩子是否触发过。 */
    public static boolean wasCaptureHookAlive() {
        return captureHookAlive;
    }

    /** 供诊断读取：截面弧长钩子是否触发过。 */
    public static boolean wasSegmentHookAlive() {
        return segmentHookAlive;
    }

    /** 供诊断读取：排队包装钩子是否触发过。 */
    public static boolean wasScheduleHookAlive() {
        return scheduleHookAlive;
    }

    /** 供诊断读取：真正旋转过多少次（探针用来断言「倾斜已生效」）。 */
    public static int tiltedQuadSampleCount() {
        return tiltedQuadSamples;
    }

    /** 供诊断读取：渲染入口看到过多少个轨道截面。 */
    public static int sectionSampleCount() {
        return sectionSamples;
    }

    /** 探针专用：清零统计量，便于多次断言。 */
    public static void resetDiagnosticsForProbe() {
        captureHookAlive = false;
        segmentHookAlive = false;
        scheduleHookAlive = false;
        drawHookAlive = false;
        sectionSamples = 0;
        tiltedQuadSamples = 0;
        firstTiltLogged = false;
        hookLivenessChecked = false;
        scheduleMissingWarned = false;
    }

    // ==================== 一次性诊断：绘制重定向 ====================

    /**
     * 由 {@link #drawTiltedQuad} 在<b>第一次真的走到绘制</b>时调用一次：确认整条链
     * 「排队的消费者确实被包装过」。
     * <p>
     * 判据：能走到绘制说明 {@code scheduleRender} 的重定向要么生效、要么 MTR 原生路径被走到。
     * 若 {@link #scheduleHookAlive} 为假，说明排队包装没挂上 —— 此时轨面<b>完全不会倾斜</b>，
     * 而现象与「本来就没有滚转」在游戏内无法区分（MTR4 上正是这个现象排查了好几轮）。
     */
    public static void verifyScheduleHookAlive() {
        if (scheduleHookAlive || scheduleMissingWarned) {
            return;
        }
        scheduleMissingWarned = true;
        Main.LOGGER.warn("[P5-3] 轨面排队包装未生效：{} / {} 上的 scheduleRender @Redirect 未注入。"
                        + "轨道中心线仍会按 P5-1 抬高，但轨面完全不会倾斜。",
                RAIL_ARROW_SCHEDULE_TARGET, RAIL_QUAD_SCHEDULE_TARGET);
    }
}
