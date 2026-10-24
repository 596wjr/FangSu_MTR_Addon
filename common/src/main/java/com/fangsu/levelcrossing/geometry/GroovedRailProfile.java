package com.fangsu.levelcrossing.geometry;

import com.fangsu.Main;
import com.fangsu.levelcrossing.CellSnapshot;
import com.fangsu.levelcrossing.TrackSlice;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 槽型钢的横断面 + 沿中心线扫掠。
 * <p>
 * 断面在 (v, y) 平面上定义：v = 相对<b>轨道中心线</b>的横向距离（恒为正，实际左右由 Band.side 镜像），
 * y = 相对<b>轨顶</b>的偏移，向下为负。
 * <p>
 * 断面形状（占满整条挖空带，由内向外）：
 * <pre>
 *   v = grooveInner .. grooveInner + 0.40*bandWidth  → 槽（靠轨道中心线一侧），深度 = grooveDepth
 *   v = grooveInner + 0.40*bandWidth .. grooveOuter  → 轨头顶面，y = 0（= railTopY，与沥青齐平）
 * </pre>
 * <b>「外侧和沥青连着、内侧是槽」</b>：铺面被挖掉的整条带 = 轨头占位，带的外沿（grooveOuter）
 * 正好是铺面边界，所以轨头外沿与沥青严丝合缝；槽在整个钢带靠中心线的那 40% 里。
 * 比例常量集中在本类顶部（视觉取舍：视觉上钢轨要「看得见」，所以槽不按真实尺寸 70mm 而按带宽的 40%）。
 */
public final class GroovedRailProfile {

    /**
     * 槽宽（格，绝对尺寸）：紧贴轨头内沿、占轨头宽的一小截。
     * <p>
     * 用户口径「按类似现实值做」：现实槽型钢的轮缘槽约 40~50mm，MTR4 轨头带宽 0.14，
     * 取 0.045（≈0.32×轨头宽）。历史上这里是 0.40×带宽（0.056），且轨头只占其余部分，
     * 导致「一条缝里几乎没有钢」。
     */
    public static final double SLOT_WIDTH = 0.045;
    /** 钢轨实体（web/foot）沉进铺面以下的深度：可见部分只有轨面 + 槽。 */
    public static final double RAIL_BODY_DEPTH = 0.06;
    /** 槽深默认值：1.5 像素 = 1.5/16 格 = 0.09375（用户口径，相对沥青路面下凹）。 */
    public static final double DEFAULT_SLOT_DEPTH = 1.5D / 16.0D;
    /**
     * 历史常量：早期「窄条钢」顶面高出沥青路面的量（0.005）。
     * <p>
     * 第 51 轮起内板顶面改成与钢板<b>同高</b>（railTop 平面），断面不再用它；
     * 常量保留是因为碰撞盒/旧断言仍按「钢面比路面高一点」来解释，删掉容易造成误读。
     */
    public static final double LIP_TOP_RISE = 0.005D;
    /**
     * 钢轨横向带的切分（第 51 轮用户口径：<b>内板 + 轮缘槽 + 钢板</b>）：
     * <pre>
     *   [inner, inner+lipInner]              内板（厚 0.005，顶面与钢板同高 = railTop 平面 + railTopBias）
     *   [inner+lipInner, +SLOT_WIDTH]        轮缘槽（宽 0.045，槽底 = deckTopY - grooveDepth）
     *   [...+SLOT_WIDTH, outer]              钢板（== 原版轨头顶面宽，抬高 railTopBias 盖住原版轨头）
     * </pre>
     * 三段无缝、无重叠、顺序固定。{@code lipInner + SLOT_WIDTH + lipOuter == outer - inner}。
     * <p>
     * <b>镜像不会再乘一次负号</b>：断面 v 恒为「正数 + 靠近中心线的一侧就是 inner」，
     * 实际世界横坐标由 {@code section()} 统一走 {@code mapV(side * v)} 镜像，
     * 所以负侧钢轨的内板自动落在<b>靠中心线</b>那一侧。别在下面再乘 side。
     */
    public static final double LIP_THICKNESS = 0.010D;


    /**
     * 钢轨厚度系数：轨顶以下到轨底的总厚度按此缩放（用户口径 0.75）。
     * 轨头顶面高度、槽宽、槽深都不受影响，只缩短"埋在铺面里的身子"。
     */
    public static final double THICKNESS_SCALE = 0.75D;
    /** 非主轨整体抬高的量：十字交叉处两条钢轨共面会闪烁。 */
    public static final double NON_PRIMARY_LIFT = 0.002;
    /**
     * <b>交叉处的极小抬升</b>（0.4mm，渲染层面的偏置，不是几何误差）。
     * <p>
     * 「槽把对侧钢轨切断」之后钢本身是连续的，两条轨在「A带 ∩ B带 − 槽」那块重叠区里互相穿过；
     * 两侧轨面同高时两组朝上面会<b>共面</b>（z-fighting 闪烁）。所以非主轨在参与交叉时抬
     * 0.4mm：肉眼不可见（比 {@link #NON_PRIMARY_LIFT} 小 5 倍），但足以把两组面在深度上分开。
     * 主轨仍保持 0，保证主轨槽口与沥青路面齐平。
     * <p>
     * 历史记录：曾用「断面绕横向中心外旋 2°」替代本抬升（CROSSING_ROLL_DEGREES），
     * 用户实机比对后选择回退到抬升 —— 不要为了消除共面再去改弯轨断面。
     */
    public static final double CROSSING_LIFT = 0.0004;

    /** 交叉判定的最小交集面积（格²）：小于它的接触不算交叉。 */
    public static final double CROSSING_AREA_EPS = 1e-6;

    /** 切口/边界判定容差（格）。 */
    private static final double CUT_EPS = 1e-7;

    /**
     * 扫掠时顶点标架的斜接上限（与 {@code TrackStrips} 的 miter-limit 同值 2.0）：
     * {@code 1/cos(φ/2)} 超过它就夹住，避免急弯外侧甩出长尖刺。
     */
    private static final double JOINT_MITER_LIMIT = 2.0;

    // ==================== 发射期审计（诊断，不影响几何） ====================
    // 为什么要审计：用户实机「有面但整片不显示」，最可能是顶点坐标出现 NaN/Inf
    // 让整条顶点缓冲作废（体积/包围盒变 NaN → GPU 什么都画不出来）。
    // 这些计数在 build 之前 reset、之后读取，直接覆盖 100% 进缓冲的三角形。

    /** 已发射三角形数。 */
    private static final java.util.concurrent.atomic.AtomicLong AUDIT_EMITTED = new java.util.concurrent.atomic.AtomicLong();
    /** 因非有限值被丢弃的三角形数。 */
    private static final java.util.concurrent.atomic.AtomicLong AUDIT_DROPPED = new java.util.concurrent.atomic.AtomicLong();
    /** 丢弃的三角形里含 NaN 分量的个数。 */
    private static final java.util.concurrent.atomic.AtomicLong AUDIT_NAN = new java.util.concurrent.atomic.AtomicLong();
    /** 丢弃的三角形里含 ±Inf 分量的个数。 */
    private static final java.util.concurrent.atomic.AtomicLong AUDIT_INF = new java.util.concurrent.atomic.AtomicLong();
    /** 已发射但 3D 面积 ≈ 0 的三角形数（退化面）。 */
    private static final java.util.concurrent.atomic.AtomicLong AUDIT_ZERO_AREA = new java.util.concurrent.atomic.AtomicLong();
    /** 第一个坏顶点的坐标文本（只记一次，供现场定位）。 */
    private static volatile String AUDIT_FIRST_BAD = "";

    // ==================== 碎片瘦身（性能，只在预算吃紧时开） ====================
    // 铣切把一块断面面按补集切成多块，交叉区的面能切出几十片，其中大量是 1cm 级的碎渣：
    // 它们在画面上看不出来（1e-4 格² = 1cm×1cm 已经小于一个贴图像素），却每个都要 4 个顶点。
    // 默认关（正常格保持逐位精确，T26-1 的面积判据仍按 1e-9 验收）；顶点预算吃紧时由
    // CrossingMeshBuilder 的降级阶梯打开。

    /** 碎片最小面积（格²）：小于它的补集碎片直接丢掉。 */
    public static final double SLIM_PIECE_MIN_AREA = 1e-4;

    /** 是否丢弃碎渣（降级阶梯第二级打开）。 */
    private static volatile boolean slimPieces = false;

    // ==================== 性能计数（定位 17 秒级任务的热点） ====================

    /** `slotCuts` 累计耗时（纳秒）与调用次数。 */
    private static final java.util.concurrent.atomic.AtomicLong AUDIT_SLOT_CUT_NANOS =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong AUDIT_SLOT_CUT_CALLS =
            new java.util.concurrent.atomic.AtomicLong();

    /** `subtractConvexPlan` 调用次数（补集裁剪的工作量，T47-3 用它对比改前/改后）。 */
    private static final java.util.concurrent.atomic.AtomicLong AUDIT_SUBTRACT_CALLS =
            new java.util.concurrent.atomic.AtomicLong();

    public static long auditSlotCutNanos() {
        return AUDIT_SLOT_CUT_NANOS.get();
    }

    public static long auditSlotCutCalls() {
        return AUDIT_SLOT_CUT_CALLS.get();
    }

    public static long auditSubtractCalls() {
        return AUDIT_SUBTRACT_CALLS.get();
    }

    /** 被瘦身丢掉的碎片数 / 参与判定的碎片数。 */
    private static final java.util.concurrent.atomic.AtomicLong AUDIT_SLIM_DROPPED =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong AUDIT_SLIM_TOTAL =
            new java.util.concurrent.atomic.AtomicLong();

    public static void setSlimPieces(boolean slim) {
        slimPieces = slim;
    }

    public static boolean slimPieces() {
        return slimPieces;
    }

    // ==================== 顶面/侧面分桶 + 随机裁切窗口（材质用） ====================
    // 钢轨要拆成两张材质：顶面（rail_top，128×16 条带）与侧面（rail_side，128×128）。
    // 面按<b>着色法线</b>的 |ny| 分：>= FLAT_NY 归顶面（轨面/窄条顶/槽底/封口里的水平片），
    // 其余（槽壁、竖直外墙、斜面）归侧面。分桶放在发射点 tri() 上做，几何/裁剪/joint frames 完全不动。

    /** 侧面目标 builder；null = 不拆桶。 */
    private static volatile RawMeshBuilder sideSink = null;

    /** 设置侧面目标（build 里 try/finally 复位；工作线程是单线程，静态字段安全）。 */
    public static void setSideSink(RawMeshBuilder sink) {
        sideSink = sink;
    }

    public static RawMeshBuilder sideSink() {
        return sideSink;
    }

    /** 顶面判定阈值（历史：按传入法线判；第 58 轮起分桶改用几何判据 {@link #geometricallyFlat}）。 */
    public static final double FLAT_NY = 0.99;

    /** 几何水平判据的容差（仅供自检/诊断使用，分桶不再用它）。 */
    public static final double FLAT_Y_EPS = 1e-6;

    /** 三个顶点是否几何水平（诊断用）。 */
    public static boolean geometricallyFlat(double[] a, double[] b, double[] c) {
        return Math.abs(a[1] - b[1]) <= FLAT_Y_EPS && Math.abs(a[1] - c[1]) <= FLAT_Y_EPS;
    }

    /**
     * 当前正在发射的面是否来自<b>钢板顶面</b>那一条断面边（第 58 轮用户最终口径）。
     * <p>
     * 分桶判据是<b>断面上的哪一段</b>，不是朝向、也不是几何水平：
     * <ul>
     *   <li>{@code true} → railTop 桶（材质 rail_top，v 完整 0→1）：只有横向 [0.7175, 0.785883] 的那条顶面；</li>
     *   <li>{@code false} → railSide 桶（材质 rail_side，随机窗口）：槽底、内板顶面（窄板）、所有竖直面、端面、切口面。</li>
     * </ul>
     * 这个标志在 sweepPolyline 里按段设置，cap()/emitSlotWall() 进来先置 false。
     */
    private static volatile boolean plateTopFace = false;

    public static boolean plateTopFace() {
        return plateTopFace;
    }

    // ==================== 邻格是与否（抑制不必要的端面封口） ====================
    // 四条格界各一个布尔：true = 邻格也是平交路口 → <b>不封口</b>（否则接缝中立起一块竖直板，
    // 用户截图红圈里的「多出来的面」）。判定来自 CrossingSettings.neighbourCrossing（采样期缓存的
    // 方块状态），这里只在工作线程读缓存，绝不调 getBlockState。
    private static volatile boolean[] neighbourCap = null;

    /** 设置四条格界的邻格信息（null = 未知，按「邻居存在」保守处理即不封口）。 */
    public static void setNeighbourCrossing(boolean[] neighbour) {
        neighbourCap = neighbour;
    }

    /** 端点落在哪条格界上（0..3 = {@link DeckSurface#SIDE_NEG_X} 等）；不在格界返回 -1。 */
    public static int boundarySideOf(Vec2 p) {
        double eps = 1e-6;
        if (Math.abs(p.x()) < eps) return DeckSurface.SIDE_NEG_X;
        if (Math.abs(p.x() - 1) < eps) return DeckSurface.SIDE_POS_X;
        if (Math.abs(p.z()) < eps) return DeckSurface.SIDE_NEG_Z;
        if (Math.abs(p.z() - 1) < eps) return DeckSurface.SIDE_POS_Z;
        return -1;
    }

    /**
     * 该格界的邻格是不是平交路口。
     * <p>
     * {@code neighbourCap == null} 只出现在<b>不经过 build 的直接 sweep</b>（自检/工具）：
     * 这时按历史行为<b>照常封口</b>（返回 false），保证「逐 band 面数之和 == 整格面数」这类核对仍然成立。
     * 真正的构建路径 {@code CrossingMeshBuilder.build} 一定会先 {@link #setNeighbourCrossing} 传入四条格界的缓存值。
     */
    public static boolean neighbourIsCrossing(Vec2 p) {
        int side = boundarySideOf(p);
        if (side < 0) return false;
        boolean[] n = neighbourCap;
        if (n == null) return false;
        return n[side];
    }

    // 随机裁切窗口：全部由方块 pos 的 seed 确定性推导（同一块每次重建/各客户端一致，绝不 Math.random()）
    private static volatile double uvTopPhase = 0;
    private static volatile double uvSideUOff = 0;
    private static volatile double uvSideVOff = 0;
    /** 侧面窗口尺寸：128×128 里裁 64×32。 */
    public static final double SIDE_U_SIZE = 0.5;
    public static final double SIDE_V_SIZE = 0.25;

    /**
     * 用方块坐标（{@code BlockPos.asLong()}）做<b>确定性随机</b>裁切窗口：
     * 同一 pos 永远得到同一组偏移 → 重建不闪、各客户端一致。{@code seed == 0} 表示不偏移。
     */
    public static void setUvWindow(long seed) {
        if (seed == 0L) {
            uvTopPhase = 0;
            uvSideUOff = 0;
            uvSideVOff = 0;
            return;
        }
        java.util.Random rnd = new java.util.Random(seed);
        uvTopPhase = rnd.nextDouble();
        uvSideUOff = rnd.nextDouble() * (1.0 - SIDE_U_SIZE);
        uvSideVOff = rnd.nextDouble() * (1.0 - SIDE_V_SIZE);
    }

    public static double uvTopPhase() {
        return uvTopPhase;
    }

    public static double uvSideUOff() {
        return uvSideUOff;
    }

    public static double uvSideVOff() {
        return uvSideVOff;
    }

    /** 顶面 UV 的 u：沿轨参数（1 格 = 1 张纹宽，循环）+ 每块随机相位。 */
    private static double topU(double along) {
        double u = along + uvTopPhase;
        return u - Math.floor(u);
    }

    /**
     * 竖直面 UV 的 u：材质是 <b>rail_side</b>（128×128），所以落进每块随机窗口 [uOff, uOff + uSize]。
     * <p>
     * 用户口径（第 57 轮）：只有 rail_top 是「完整 0→1」，rail_side 依旧是随机偏移/裁切。
     */
    private static double sideU(double along) {
        return uvSideUOff + (along - Math.floor(along)) * SIDE_U_SIZE;
    }

    /**
     * 竖直面 UV 的 v：高度（轨底 0 → 轨顶 1）落进 rail_side 的随机窗口 [vOff, vOff + vSize]。
     * <p>
     * 竖直面才用随机窗口；水平轨面必须是完整条带（见 sweepPolyline 的水平面分支）。
     */
    private static double sideV(double heightFrac) {
        // rail_side 整张随机裁一块（uSize × vSize）：v 落进窗口 [vOff, vOff + vSize]
        return uvSideVOff + shrink(heightFrac) * SIDE_V_SIZE;
    }

    /**
     * 轨面 UV 的 v：<b>每个水平面各取 0..1</b>（第 56 轮用户口径：轨面必须看到完整的「深-浅-深」条带）。
     * <p>
     * 注意不要改成「按整条 band 归一化」—— 钢板只占 band 的 58%，那样取样只有半张贴图；
     * 也不要加随机 vOff / 乘 vSize，否则条带会被压缩或平移。随机相位只作用于 u（拼缝观感）。
     */
    static double faceVFull() {
        return 0.0;
    }

    /** 夹到 [0,1]（UV 必须落在贴图内，T51-2 逐值检查）。 */
    private static double shrink(double v) {
        if (!Double.isFinite(v)) return 0;
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public static long auditSlimDropped() {
        return AUDIT_SLIM_DROPPED.get();
    }

    public static long auditSlimPieces() {
        return AUDIT_SLIM_TOTAL.get();
    }

    /** 清空审计计数（每次 build 之前调用）。 */
    public static void resetAudit() {
        AUDIT_EMITTED.set(0);
        AUDIT_DROPPED.set(0);
        AUDIT_NAN.set(0);
        AUDIT_INF.set(0);
        AUDIT_ZERO_AREA.set(0);
        AUDIT_FIRST_BAD = "";
        AUDIT_SLIM_DROPPED.set(0);
        AUDIT_SLIM_TOTAL.set(0);
        AUDIT_SLOT_CUT_NANOS.set(0);
        AUDIT_SLOT_CUT_CALLS.set(0);
        AUDIT_SUBTRACT_CALLS.set(0);
    }

    public static long auditEmitted() {
        return AUDIT_EMITTED.get();
    }

    public static long auditDropped() {
        return AUDIT_DROPPED.get();
    }

    public static long auditNan() {
        return AUDIT_NAN.get();
    }

    public static long auditInf() {
        return AUDIT_INF.get();
    }

    public static long auditZeroArea() {
        return AUDIT_ZERO_AREA.get();
    }

    public static String auditFirstBad() {
        return AUDIT_FIRST_BAD;
    }

    private GroovedRailProfile() {
    }

    /**
     * 一处「槽铣切」在一条钢轨上留下的<b>切口边</b>：被挖掉的那片（对方槽条带 ∩ 本 band）
     * 落在对方槽条带边界上的边。水平方向 = 这条边（a→b），竖直方向 = 轨顶到轨底。
     * {@code nx/nz} 是朝<b>被挖掉一侧</b>（槽内）的水平单位法线。
     */
    public record SlotWall(Vec2 a, Vec2 b, double nx, double nz) {
        public double length() {
            return a.sub(b).length();
        }
    }

    /**
     * 一次「槽铣切」：<b>对方的槽条带</b>从本 band 上铣过去，挖掉 {@code pieces}（凸片），
     * 并在 {@code walls} 上留下竖直切口面。
     * <p>
     * 与旧的「整条 band 互相切断」口径的区别（用户口径）：
     * 钢本身是连续的，只有<b>槽</b>像铣刀一样切穿对侧钢轨；所以被挖掉的宽度是槽宽
     * {@link #SLOT_WIDTH}（0.045），不是整条 band 宽（0.14）。宽出 0.14−0.045 的那部分
     * 就是用户看到的「缺口」。
     */
    public record SlotCut(TrackStrips.Band band, TrackStrips.Band other,
                          List<List<Vec2>> pieces, List<SlotWall> walls) {
    }

    /**
     * 某条 band 的<b>槽条带</b>多边形（格内）：中心线按 {@link #lateralLayout} 的
     * {@code layout[1]..layout[2]}（槽的横向区间）向该 band 所在侧偏移得到，与
     * {@link TrackStrips#bands} 生成走廊用的是同一套偏移做法。
     *
     * @return 槽条带（已裁到格内、逆时针无所谓）；该轨无有效槽时返回空表
     */
    public static List<Vec2> slotStrip(TrackStrips.Band band) {
        if (band == null || band.slice() == null || band.centerline() == null) return new ArrayList<>();
        TrackSlice slice = band.slice();
        double[] layout = lateralLayout(slice);
        double inner = layout[1];
        double outer = layout[2];
        if (!(outer > inner + 1e-6)) return new ArrayList<>();
        int side = band.side() >= 0 ? 1 : -1;
        // 与 TrackStrips.bands 用同一套真斜接偏移：槽条带与走廊必须同源，否则交叉判定会错位
        List<Vec2> near = TrackStrips.offsetMiter(band.centerline(), side * mapV(inner, slice));
        List<Vec2> far = TrackStrips.offsetMiter(band.centerline(), side * mapV(outer, slice));
        if (near.size() < 2 || far.size() < 2) return new ArrayList<>();
        List<Vec2> poly = new ArrayList<>(near.size() + far.size());
        poly.addAll(near);
        java.util.Collections.reverse(far);
        poly.addAll(far);
        List<Vec2> clipped = PolyOps.clipToRect(poly, 0, 0, 1, 1);
        return clipped.size() < 3 ? new ArrayList<>() : clipped;
    }

    /**
     * 找出本 band 上所有「被对方槽条带铣掉」的区域。
     * <p>
     * 每处 = {@code 本 band ∩ 对方 band 的槽条带}，再<b>减去本 band 自己的槽条带</b>：
     * 自己的槽是轮缘通道，必须连续贯通，绝不能被这次铣切削掉（否则槽底出现缺口）。
     * 只有「不同 TrackSlice」才算交叉，同一条轨的左右两条带不互相铣。
     */
    public static List<SlotCut> slotCuts(TrackStrips.Band band, List<TrackStrips.Band> bands) {
        long t0 = System.nanoTime();
        try {
            return slotCutsUncached(band, bands);
        } finally {
            AUDIT_SLOT_CUT_NANOS.addAndGet(System.nanoTime() - t0);
            AUDIT_SLOT_CUT_CALLS.incrementAndGet();
        }
    }

    /**
     * 槽条带缓存（同一次 build 内每条 band 只算一次 {@link #slotStrip}）。
     * <p>
     * 一次 build 里 4 条 band 两两配对 → 同一条 band 的槽条带会被重复算 3 次，
     * 每次都要 offsetMiter + 裁剪 + 凸分解，是 17 秒级任务里白烧的一部分。
     */
    private static final java.util.Map<TrackStrips.Band, List<Vec2>> SLOT_STRIP_CACHE =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    /**
     * 合并后的补集裁片缓存（同一条 band + 同一份 bands 表，一次 build 只合并一次）。
     * <p>
     * 键必须同时包含 {@code bands}（Identity 比较）：同一条 band 用「空 bands」和「完整 bands」
     * 扫出来的铣切结果是不同的，只按 band 缓存会把「不铣切」的结果串给后面的调用（自检里当场翻车）。
     */
    private static final java.util.Map<CutKey, List<List<Vec2>>> MERGED_CUT_CACHE =
            java.util.Collections.synchronizedMap(new java.util.HashMap<>());

    /** 按 Identity 比较的缓存键：(band 实例, bands 表实例)。 */
    private static final class CutKey {
        private final TrackStrips.Band band;
        private final List<TrackStrips.Band> bands;

        CutKey(TrackStrips.Band band, List<TrackStrips.Band> bands) {
            this.band = band;
            this.bands = bands;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CutKey other)) return false;
            return band == other.band && bands == other.bands;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(band) * 31 + System.identityHashCode(bands);
        }
    }

    /** 一次 build 开始前清空槽条带 / 补集裁片 / 凸分解缓存。 */
    public static void clearCutCache() {
        SLOT_STRIP_CACHE.clear();
        MERGED_CUT_CACHE.clear();
        PolyOps.clearConvexCache();
    }

    /**
     * 某 band 的补集裁片（已合并成少数凸片）。同一次 build 内只算/合并一次：
     * 预算估算与真正的扫掠共用这份结果，避免把「合并」这件重活做两遍。
     */
    public static List<List<Vec2>> mergedCutPolys(TrackStrips.Band band, List<TrackStrips.Band> bands) {
        if (band == null) return new ArrayList<>();
        CutKey key = new CutKey(band, bands);
        List<List<Vec2>> hit = MERGED_CUT_CACHE.get(key);
        if (hit != null) return hit;
        List<List<Vec2>> raw = new ArrayList<>();
        for (SlotCut cut : slotCuts(band, bands)) raw.addAll(cut.pieces());
        List<List<Vec2>> merged = cutMergeEnabled ? mergeCutPieces(raw) : raw;
        if (MERGED_CUT_CACHE.size() < 256) MERGED_CUT_CACHE.put(key, merged);
        return merged;
    }

    /**
     * 自检/诊断开关：关掉「补集裁片合并 + 包围盒预筛」，用来量出<b>改前</b>的裁剪调用量（T47-3）。
     * 正式路径永远开启。
     */
    private static volatile boolean cutMergeEnabled = true;

    public static void setCutMergeEnabled(boolean on) {
        cutMergeEnabled = on;
        clearCutCache();
    }

    public static boolean cutMergeEnabled() {
        return cutMergeEnabled;
    }

    /** {@link #slotStrip} 的按 band 记忆化版本。 */
    public static List<Vec2> slotStripCached(TrackStrips.Band band) {
        if (band == null) return new ArrayList<>();
        List<Vec2> hit = SLOT_STRIP_CACHE.get(band);
        if (hit != null) return hit;
        List<Vec2> poly = slotStrip(band);
        if (SLOT_STRIP_CACHE.size() < 256) SLOT_STRIP_CACHE.put(band, poly);
        return poly;
    }

    private static List<SlotCut> slotCutsUncached(TrackStrips.Band band, List<TrackStrips.Band> bands) {
        List<SlotCut> out = new ArrayList<>();
        if (band == null || band.poly() == null || bands == null) return out;
        List<Vec2> ownSlot = slotStripCached(band);
        for (TrackStrips.Band other : bands) {
            if (other == null || other == band || other.poly() == null) continue;
            // 同一个 TrackSlice 实例 = 同一条轨道的另一侧，不算交叉
            if (band.slice() == other.slice()) continue;
            List<Vec2> slot = slotStripCached(other);
            if (slot.size() < 3) continue;
            List<List<Vec2>> pieces = new ArrayList<>(2);
            for (List<Vec2> pa : PolyOps.convexPartsCached(band.poly())) {
                for (List<Vec2> ps : PolyOps.convexPartsCached(slot)) {
                    List<Vec2> inter = PolyOps.clipConvex(pa, ps);
                    if (inter.size() < 3 || Math.abs(PolyOps.area(inter)) <= CROSSING_AREA_EPS) continue;
                    pieces.addAll(cutOutOwnSlot(inter, ownSlot));
                }
            }
            if (pieces.isEmpty()) continue;
            out.add(new SlotCut(band, other, pieces, wallsOnBoundary(pieces, slot)));
        }
        return out;
    }

    /** 把凸片里被自己槽条带盖住的部分去掉（自己槽必须保持完整）。 */
    private static List<List<Vec2>> cutOutOwnSlot(List<Vec2> piece, List<Vec2> ownSlot) {
        List<List<Vec2>> out = new ArrayList<>(2);
        if (ownSlot == null || ownSlot.size() < 3) {
            out.add(piece);
            return out;
        }
        for (List<Vec2> keep : PolyOps.subtractConvex(piece, ownSlot)) {
            if (keep.size() >= 3 && Math.abs(PolyOps.area(keep)) > CROSSING_AREA_EPS) out.add(keep);
        }
        return out;
    }

    /**
     * 把补集裁片合并成少数凸片（面级裁剪的 cut 数从十几片降到 2~4 片）。
     * <p>
     * 做法：反复把「并集仍是凸的」两片换成它们的凸包 —— 凸包面积等于两片面积和时，
     * 凸包与并集只差测度为零的边界，合并是<b>精确</b>的（面积、覆盖都不变）。
     * 全部片本身共凸时一次就合成一片。这一条直接决定 {@code quad()} 里补集裁剪的规模：
     * 每多一片就等于给每块面多切一刀，pieces 是乘法增长，所以这是 17 秒任务的主要来源。
     */
    private static List<List<Vec2>> mergeCutPieces(List<List<Vec2>> pieces) {
        if (pieces.size() < 2) return pieces;
        List<List<Vec2>> cur = new ArrayList<>(pieces.size());
        for (List<Vec2> p : pieces) {
            List<Vec2> c = PolyOps.clean(p);
            if (c.size() >= 3 && Math.abs(PolyOps.area(c)) > CROSSING_AREA_EPS) cur.add(c);
        }
        boolean merged = true;
        while (merged && cur.size() > 1) {
            merged = false;
            outer:
            for (int i = 0; i < cur.size(); i++) {
                for (int j = i + 1; j < cur.size(); j++) {
                    List<Vec2> hull = convexHullOf(cur.get(i), cur.get(j));
                    if (hull == null) continue;
                    double want = Math.abs(PolyOps.area(cur.get(i))) + Math.abs(PolyOps.area(cur.get(j)));
                    if (Math.abs(Math.abs(PolyOps.area(hull)) - want) > 1e-9 + want * 1e-9) continue;
                    cur.set(i, hull);
                    cur.remove(j);
                    merged = true;
                    break outer;
                }
            }
        }
        return cur;
    }

    /** 两个小多边形的凸包（Andrew 单调链）；退化时返回 null。 */
    private static List<Vec2> convexHullOf(List<Vec2> a, List<Vec2> b) {
        List<Vec2> pts = new ArrayList<>(a.size() + b.size());
        pts.addAll(a);
        pts.addAll(b);
        pts.sort((p, q) -> p.x() != q.x() ? Double.compare(p.x(), q.x()) : Double.compare(p.z(), q.z()));
        int n = pts.size();
        if (n < 3) return null;
        Vec2[] hull = new Vec2[2 * n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k >= 2 && hull[k - 2].sub(hull[k - 1]).cross(pts.get(i).sub(hull[k - 1])) <= 0) k--;
            hull[k++] = pts.get(i);
        }
        for (int i = n - 2, lower = k + 1; i >= 0; i--) {
            while (k >= lower && hull[k - 2].sub(hull[k - 1]).cross(pts.get(i).sub(hull[k - 1])) <= 0) k--;
            hull[k++] = pts.get(i);
        }
        List<Vec2> out = new ArrayList<>(k - 1);
        for (int i = 0; i < k - 1; i++) out.add(hull[i]);
        return out.size() >= 3 ? PolyOps.clean(out) : null;
    }

    /** 挖掉区域里落在<b>对方槽条带</b>边界上的边（这些边才是铣出来的槽壁，需要竖直切口面）。 */
    private static List<SlotWall> wallsOnBoundary(List<List<Vec2>> pieces, List<Vec2> slotPoly) {
        List<SlotWall> out = new ArrayList<>(2);
        List<Vec2> edge = PolyOps.clean(slotPoly);
        int m = edge.size();
        if (m < 3) return out;
        for (List<Vec2> piece : pieces) {
            List<Vec2> ring = PolyOps.clean(piece);
            int n = ring.size();
            if (n < 3) continue;
            double cx = 0;
            double cz = 0;
            for (Vec2 p : ring) {
                cx += p.x();
                cz += p.z();
            }
            cx /= n;
            cz /= n;
            for (int i = 0; i < n; i++) {
                Vec2 a = ring.get(i);
                Vec2 b = ring.get((i + 1) % n);
                Vec2 d = b.sub(a);
                if (d.length() < CUT_EPS) continue;
                if (!onBoundaryEdge(a, b, edge)) continue;
                d = d.normalized();
                double nx = -d.z();
                double nz = d.x();
                // 法线朝被挖掉的一侧（= 这条片的内侧），这样从槽里看才看得见槽壁
                Vec2 mid = a.add(b).mul(0.5);
                if (nx * (cx - mid.x()) + nz * (cz - mid.z()) < 0) {
                    nx = -nx;
                    nz = -nz;
                }
                out.add(new SlotWall(a, b, nx, nz));
            }
        }
        return out;
    }

    /** 线段 (a,b) 是否整条落在 {@code ring} 的某条边界边上。 */
    private static boolean onBoundaryEdge(Vec2 a, Vec2 b, List<Vec2> ring) {
        int m = ring.size();
        for (int j = 0; j < m; j++) {
            Vec2 u = ring.get(j);
            Vec2 v = ring.get((j + 1) % m);
            if (onSegmentOf(a, u, v) && onSegmentOf(b, u, v)) return true;
        }
        return false;
    }

    /** 点是否落在线段上（含端点，容差 {@link #CUT_EPS}）。 */
    private static boolean onSegmentOf(Vec2 p, Vec2 a, Vec2 b) {
        Vec2 ab = b.sub(a);
        double len = ab.length();
        if (len < CUT_EPS) return p.sub(a).length() < CUT_EPS;
        if (Math.abs(ab.cross(p.sub(a)) / len) > CUT_EPS) return false;
        double t = p.sub(a).dot(ab) / (len * len);
        return t >= -1e-6 && t <= 1 + 1e-6;
    }

    /**
     * 断面段表，坐标是「相对轨道中心线的横向距离 v（恒为正，左右由 Band.side 镜像）」×
     * 「相对<b>轨顶</b>的高度 y（向下为负）」。
     *
     * @param deckBelowRail 沥青路面相对轨顶的高度（负数，{@code deckTopY - railTopY}）。
     *                      槽深是<b>相对路面</b>下凹的，所以必须知道路面在哪，
     *                      否则会出现「槽底比路面还高」（用户反馈的可见缺陷）。
     */
    public static List<double[]> section(TrackSlice slice, double deckBelowRail) {
        double[] range = lateralRange(slice);
        double inner = range[0];
        double outer = range[1];
        // 槽：紧贴轨头内沿的一条窄槽，槽底必须低于沥青路面
        double slotWidth = Math.min(SLOT_WIDTH, Math.max(1e-4, (outer - inner) * 0.8));
        double slotEnd = inner + slotWidth;
        double slotBottom = slotBottom(slice, deckBelowRail);
        // 轨顶以下到轨底的总厚度按 THICKNESS_SCALE 缩放（用户口径 0.75）：轨顶 y=0 不动，只把底部收上来
        double bodyBottom = bodyBottom(slice, deckBelowRail);
        // 布局（第 51 轮）：内板 → 轮缘槽 → 钢板（横向相邻、无缝无重叠）
        // 内板顶面与钢板<b>同高</b>（都在 railTop 平面，+ railTopBias 由 DeckSurface 统一加），视觉上是一条带；
        // 想让内板低一点时把 innerTop 改成 deckBelowRail + 某值即可（例如 -0.002）。
        double innerTop = 0;
        double[] layout = lateralLayout(slice);

        // 断面（自内向外，闭环）：带内沿底面 → 内板外侧墙 → 内板顶面 → 槽内壁 → 槽底
        //                       → 槽外壁 → 钢板顶面 → 外侧墙 → 回到底面。
        // lipInner == 0 时「内板顶面」退化为零长段，下面的去重会整段丢掉（第 49 轮的开口轮缘槽形态）；
        // 第 51 轮 lipInner = 0.005，8 个点全不重合、无零面积面。
        List<double[]> points = new ArrayList<>(8);
        points.add(new double[]{layout[0], bodyBottom});    // 带内沿（底面角）
        points.add(new double[]{layout[0], innerTop});      // 内板外侧墙（升到轨顶平面）
        points.add(new double[]{layout[1], innerTop});      // 内板顶面（厚 0.005，与钢板同高）
        points.add(new double[]{layout[1], slotBottom});    // 槽内壁（竖直向下）
        points.add(new double[]{layout[2], slotBottom});    // 槽底（宽 SLOT_WIDTH）
        points.add(new double[]{layout[2], 0});             // 槽外壁（竖直向上到轨顶）
        points.add(new double[]{layout[3], 0});             // 钢板顶面（一路到带外沿，盖住原版轨头）
        points.add(new double[]{layout[3], bodyBottom});    // 带外沿竖直墙（沉进铺面）

        // 去掉连续重复点（容差 1e-9）：lipInner == 0 时上面前两组点会重合，
        // 留着会生成零长度断面段 → 零面积面（自检有 zero-area 审计）
        List<double[]> dedup = new ArrayList<>(points.size());
        for (double[] p : points) {
            if (dedup.isEmpty()) {
                dedup.add(p);
                continue;
            }
            double[] last = dedup.get(dedup.size() - 1);
            if (Math.abs(last[0] - p[0]) > 1e-9 || Math.abs(last[1] - p[1]) > 1e-9) dedup.add(p);
        }
        // 首尾也可能重复
        while (dedup.size() > 1) {
            double[] first = dedup.get(0);
            double[] last = dedup.get(dedup.size() - 1);
            if (Math.abs(first[0] - last[0]) <= 1e-9 && Math.abs(first[1] - last[1]) <= 1e-9) {
                dedup.remove(dedup.size() - 1);
            } else {
                break;
            }
        }
        points = dedup;

        double signed = 0;
        int n = points.size();
        for (int i = 0; i < n; i++) {
            double[] a = points.get(i);
            double[] b = points.get((i + 1) % n);
            signed += a[0] * b[1] - b[0] * a[1];
        }
        if (signed < 0) {
            // 统一成逆时针，后面才能用 (dy,-dv) 直接当外法线
            List<double[]> reversed = new ArrayList<>(n);
            for (int i = n - 1; i >= 0; i--) reversed.add(points.get(i));
            points = reversed;
        }

        // 所有横坐标在输出前统一过 mapV（y 不变）；映射只作用于最终坐标，
        // slotWidth / lipWidth 等宽度量仍用未映射的带宽算出来（见上）。
        List<double[]> segments = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double[] a = points.get(i);
            double[] b = points.get((i + 1) % n);
            segments.add(new double[]{mapV(a[0], slice), a[1], mapV(b[0], slice), b[1]});
        }
        // 布局 A 下「窄条钢」本身就是有厚度的实体（宽 LIP_THICKNESS、顶面 +0.005），
        // 不再需要零厚度的额外竖壁段 —— 断面就是上面这个 8 点闭合多边形。
        return segments;
    }

    /**
     * 槽底高度（相对轨顶，负数）：先取「路面 - 槽深」，再夹到轨头底之上，
     * 保证槽永远低于路面、又不会切穿轨头。
     */
    public static double slotBottom(TrackSlice slice, double deckBelowRail) {
        // 槽深默认 1.5 像素 = 1.5/16 格 = 0.09375（用户口径「给到往下 1.5 个像素点」）
        double slotDepth = slice == null ? DEFAULT_SLOT_DEPTH : slice.grooveDepth;
        if (!(slotDepth > 1e-4)) slotDepth = DEFAULT_SLOT_DEPTH;
        // 不变量：槽不能切穿轨头（槽底必须高于轨头底 -0.10），且一定低于路面
        return Math.max(deckBelowRail - slotDepth, -0.10 + 1e-3);
    }

    /**
     * 钢轨断面底边高度（相对轨顶，负数）。切口封口面的底边取这里，
     * 与断面表格里的 {@code bodyBottom} 是同一个量（断面与封口面必须严丝合缝）。
     */
    public static double bodyBottom(TrackSlice slice, double deckBelowRail) {
        return (Math.min(slotBottom(slice, deckBelowRail), deckBelowRail) - RAIL_BODY_DEPTH) * THICKNESS_SCALE;
    }

    /**
     * 布局 A 的四条横向分界：{@code {inner, inner+LIP, inner+LIP+SLOT, outer}}。
     * 三段依次是 窄条钢 / 槽 / 宽轨面，并集严格等于 {@code [inner, outer]}（无缝、无重叠）。
     */
    public static double[] lateralLayout(TrackSlice slice) {
        double[] range = lateralRange(slice);
        double inner = range[0];
        double outer = range[1];
        // 钢面 = <b>整条钢轨可见轮廓</b>（rail.obj 最宽层 0.213173 = 0.647147..0.86032），
        // 槽仍<b>居中于轨头</b>：槽中心取 TrackSlice.grooveCenter（轨头中心 0.7516915）；
        // 没给（<=0，旧存档/未配置）就退回「内外沿中点」。
        // 两侧钢条于是按几何自然得到：内侧 0.0820445、外侧 0.0861285（外侧略宽 —— 正是用户说的「偏内侧」），
        // 不需要任何额外的比例参数。
        double span = outer - inner;
        double slack = span - SLOT_WIDTH;
        double slotInner;
        if (slack < 0.002) {
            // 钢面比槽还窄：退回常量薄边，保证旧存档/怪尺寸轨道仍能出几何
            double lip = Math.max(0, Math.min(LIP_THICKNESS, slack * 0.5));
            slotInner = Math.min(outer, inner + lip);
        } else {
            double center = slice == null ? 0 : slice.grooveCenter;
            if (!(center > 0)) center = (inner + outer) * 0.5;
            // 槽必须整条落在钢面内
            center = Math.max(inner + SLOT_WIDTH * 0.5, Math.min(outer - SLOT_WIDTH * 0.5, center));
            slotInner = center - SLOT_WIDTH * 0.5;
        }
        double slotOuter = Math.min(outer, slotInner + SLOT_WIDTH);
        return new double[]{inner, slotInner, slotOuter, outer};
    }

    /** 该轨横断面区间 {@code {grooveInner, grooveOuter}}（已做退化保护）。 */
    private static double[] lateralRange(TrackSlice slice) {
        double inner = slice == null ? 0.62 : slice.grooveInner;
        double outer = slice == null ? 0.72 : slice.grooveOuter;
        if (!(outer > inner + 1e-4)) outer = inner + 0.1;
        return new double[]{inner, outer};
    }

    /** 唯一的横向映射（委托 {@link TrackSlice#mapLateral(double)}，本类不得出现第二处公式）。 */
    private static double mapV(double v, TrackSlice slice) {
        return slice == null ? v : slice.mapLateral(v);
    }

    /** 横向 v 归一化到该轨断面区间：贴图横向 0..1 正好覆盖 grooveInner..grooveOuter。 */
    private static double lateralU(double v, double inner, double outer) {
        double span = outer - inner;
        return span > 1e-6 ? (v - inner) / span : v;
    }

    /**
     * 沿 band 中心线扫掠断面；两端在格界处封口。
     * <p>
     * UV 全部跟随<b>轨道局部标架</b>，不再使用世界坐标：
     * <ul>
     *   <li>水平面（踏面/槽底/轨头底面/轨底底面）：U = (v-grooveInner)/(grooveOuter-grooveInner)（0..1 铺满贴图宽度），
     *       V = 沿中心线累计弧长（单位：格）</li>
     *   <li>竖直面（槽壁/web 侧面/foot 侧面）：U = 累计弧长，V = y</li>
     *   <li>端面（cap）：U 同上归一化，V = y</li>
     * </ul>
     * 这样条纹沿轨向连续，断面纹理在横向铺满（block/level_crossing_rail.png 本就是一条钢轨横断面铺满 16px 宽），
     * 45° 斜轨与转弯处都不会再斜切纹理。
     */
    public static void sweep(RawMeshBuilder out, TrackStrips.Band band, TrackSlice slice, CellSnapshot snap) {
        if (snap == null) return;
        sweep(out, band, slice, snap, TrackStrips.bands(snap));
    }

    /**
     * 扫掠入口（带外部已算好的 band 表，槽铣切要看到所有轨道）。
     * <p>
     * <b>最终控制流（用户口径：槽把对侧钢轨等切断）</b>：
     * <ol>
     *   <li>用 {@link #slotCuts} 找出本 band 上被<b>对方槽条带</b>铣掉的区域（宽度 = 槽宽，不是整条 band）；</li>
     *   <li>参与铣切的 band 整体不抬高（lift = 0），未参与的仍按主轨/非主轨规则；</li>
     *   <li>中心线按钢带外沿裁到格内得到若干段折线，逐段 {@code sweepPolyline}；</li>
     *   <li>每块面在发出去之前按那些凸片做「补集」裁剪——只挖掉槽宽的一条，钢本身保持连续；</li>
     *   <li>每条切口边补一块竖直切口面（{@link #emitSlotWall}），铣出来的槽壁才闭合。</li>
     * </ol>
     */
    public static void sweep(RawMeshBuilder out, TrackStrips.Band band, TrackSlice slice,
                             CellSnapshot snap, List<TrackStrips.Band> bands) {
        if (out == null || band == null || slice == null || snap == null) return;
        // 沥青路面相对轨顶的高度：deckTopY - railTopY 在整格里是常数（pitch/roll 时两者同步平移）
        double deckBelowRail = snap.deckTopY - slice.railTopY;
        List<double[]> section = section(slice, deckBelowRail);
        if (section.isEmpty()) return;
        int side = band.side() >= 0 ? 1 : -1;
        // 参与槽铣切的 band 不再整体抬高（抬 0.002 会让两条钢轨在槽口错层，槽壁接不上）
        List<SlotCut> cuts = slotCuts(band, bands);
        // 参与「槽铣切」的 band：主轨仍 0（槽口与沥青齐平），非主轨只抬 CROSSING_LIFT(0.4mm) ——
        // 只为把重叠区两组共面的朝上面在深度上分开（渲染偏置，不是几何误差），
        // 比未参与交叉时的 NON_PRIMARY_LIFT(2mm) 小 5 倍，肉眼不可见。
        double lift = slice.primary ? 0 : (cuts.isEmpty() ? NON_PRIMARY_LIFT : CROSSING_LIFT);
        double inner = lateralRange(slice)[0];
        double outer = lateralRange(slice)[1];
        // 补集裁片（合并成少数凸片）：面级裁剪的 cut 数直接决定成本（pieces 是乘法增长）。
        // 与预算估算共用同一份缓存，一次 build 只合并一次。
        List<List<Vec2>> cutPolys = mergedCutPolys(band, bands);

        // <b>裁剪基准 = 钢带外沿（始终，不是兜底）</b>：
        // 「格内」不等于「中心线在格内」——钢带是中心线两侧的条带，斜穿时中心线常常
        // 「一段在格内、一段在格外但钢带仍覆盖本格」。只按格矩形（margin=0）裁中心线，
        // 后面那种整段会被丢掉，用户看到的就是「斜线有一部分钢轨没画出来」。
        // 所以一律按 outer 放宽裁中心线；格内的最终边界由面层裁格矩形负责（quad 里已有）。
        // <p>
        // <b>逐段扫掠</b>：中心线可能在裁切矩形里进出多次（多段折线），每段各自扫掠。
        List<List<Vec2>> lines = clipLinesToCell(band.centerline(), outer);
        for (List<Vec2> line : lines) {
            sweepPolyline(out, line, section, side, lift, inner, outer, slice, snap, cutPolys);
        }

        // 端面：中心线按外沿放宽后端点落在格外，'格界自动封口' 不再触发，
        // 这里按「中心线穿出格界的位置」补回端面（与旧行为一致：只在格界封、格内不封）。
        // 段的端点在格内部结束说明轨道还在继续延伸，那里封口会在钢轨旁立一小片薄片，必须不封。
        // 第 52 轮：<b>邻格也是平交路口时不封口</b> —— 钢带在接缝两侧连续，封口会在接缝中立一块竖直板
        // （用户截图红圈里「多出来的面」）。四条格界分别判定，信息来自采样期缓存的 neighbourCrossing。
        for (List<Vec2> line : lines) {
            for (List<Vec2> inside : clipLinesToCell(line, 0)) {
                if (isOnCellBoundary(inside.get(0)) && !neighbourIsCrossing(inside.get(0))) {
                    cap(out, inside, section, side, slice, snap, lift, true, cutPolys);
                }
                if (isOnCellBoundary(inside.get(inside.size() - 1))
                        && !neighbourIsCrossing(inside.get(inside.size() - 1))) {
                    cap(out, inside, section, side, slice, snap, lift, false, cutPolys);
                }
            }
        }

        // 切口面：每个 SlotCut 的每条槽壁边竖一块竖直四边形（A 两侧、B 两侧都由各自的 sweep 负责）
        for (SlotCut cut : cuts) {
            for (SlotWall wall : cut.walls()) {
                emitSlotWall(out, wall, slice, snap, lift);
            }
        }

        // 决定性诊断（实机一次判定「镜像 / 布局」）：本 band 的侧向范围。
        // 判定标准：side=-1 时 vMin/vMax 必须都 ≤ 0；side=+1 时必须都 ≥ 0；
        // 若出现跨侧（vMin 与 vMax 异号），说明这一条 band 被画到了中心线两侧 = 产品 bug。
        // 频率与 recompute 相同（每条 band 每次重建一条），可接受。
        double sMin = Double.POSITIVE_INFINITY;
        double sMax = Double.NEGATIVE_INFINITY;
        for (double[] seg : section) {
            sMin = Math.min(sMin, Math.min(seg[0], seg[2]));
            sMax = Math.max(sMax, Math.max(seg[0], seg[2]));
        }
        // 判据已完成（实机确认 side=±1 侧向同号、镜像无 bug），这条降级为 Main.debug，避免刷日志。
        Main.debug("[levelcrossing] sweep band side={} inner={} outer={} lines={} slotCuts={} vMin={} vMax={}",
                band.side(), inner, outer, lines.size(), cuts.size(), side * sMin, side * sMax);
    }

    /**
     * 扫掠一段连续折线（不含两端封口）。
     * <p>
     * <b>顶点级共享断面（joint frames）</b>：断面的横向位置不再按「每一段各自的切线」算，
     * 而是按<b>折线顶点</b>算一次——内部顶点取前后两段的<b>角平分方向</b>（= miter 方向），
     * 并按 {@code 1/cos(φ/2)} 沿角平分线外推（夹在 MITER_LIMIT×v 以内）。
     * 于是相邻两段在同一个顶点上拿到的是<b>同一组断面顶点</b>：
     * 弯道外侧天然闭合（不再有楔形缺口），内侧也不再重叠。
     * <p>
     * 老做法的病根正是「每段各自用自己的切线」：外侧半径比中心线大，两段各自等距偏移后
     * 在外侧张开一个楔形缝、在内侧互相压进去 —— 这就是用户长期反馈的「弯道外侧缝隙」。
     */
    public static void sweepPolyline(RawMeshBuilder out, List<Vec2> line, List<double[]> section,
                                      int side, double lift, double inner, double outer,
                                      TrackSlice slice, CellSnapshot snap, List<List<Vec2>> cuts) {
        if (line == null || line.size() < 2) return;
        // 断面里的横坐标已经是映射后的值，UV 归一化必须用同一套（映射后的）区间
        inner = mapV(inner, slice);
        outer = mapV(outer, slice);
        if (inner > outer) {
            double tmp = inner;
            inner = outer;
            outer = tmp;
        }
        // 侧面 UV 的高度基准：轨底（断面底边）→ 轨顶，映射到 0..1 再落进裁切窗口
        double bodyBottomHere = bodyBottom(slice, snap.deckTopY - slice.railTopY);
        double heightSpan = -bodyBottomHere > 1e-9 ? -bodyBottomHere : 1.0;
        int n = line.size();
        double[] px = new double[n];
        double[] pz = new double[n];
        double[] py = new double[n];
        double[] arc = new double[n];
        // 每个顶点的横向单位向量 (ux,uz) 与斜接放大系数 sc（横向距离 v 实际沿 u 走 v*sc）
        double[] ux = new double[n];
        double[] uz = new double[n];
        double[] sc = new double[n];
        // 每段的切线（给法线用）
        double[] tx = new double[n];
        double[] tz = new double[n];
        for (int k = 0; k < n; k++) {
            px[k] = line.get(k).x();
            pz[k] = line.get(k).z();
            py[k] = DeckSurface.railTopY(slice, snap, px[k], pz[k]) + lift;
            if (k > 0) {
                arc[k] = arc[k - 1] + line.get(k).sub(line.get(k - 1)).length();
            }
        }
        for (int k = 0; k + 1 < n; k++) {
            Vec2 t = line.get(k + 1).sub(line.get(k));
            if (t.length() < 1e-9) {
                tx[k] = k > 0 ? tx[k - 1] : 1;
                tz[k] = k > 0 ? tz[k - 1] : 0;
            } else {
                t = t.normalized();
                tx[k] = t.x();
                tz[k] = t.z();
            }
        }
        tx[n - 1] = tx[n - 2];
        tz[n - 1] = tz[n - 2];

        // 顶点标架：端点用单侧法线；内部顶点用前后两段法线的角平分方向
        for (int k = 0; k < n; k++) {
            int prevSeg = Math.max(0, k - 1);
            int nextSeg = Math.min(n - 2, k);
            double n0x = -tz[prevSeg];
            double n0z = tx[prevSeg];
            double n1x = -tz[nextSeg];
            double n1z = tx[nextSeg];
            if (k == 0 || k == n - 1) {
                // 端点只有一侧段可用
                ux[k] = k == 0 ? n0x : n1x;
                uz[k] = k == 0 ? n0z : n1z;
                sc[k] = 1.0;
                continue;
            }
            double bx = n0x + n1x;
            double bz = n0z + n1z;
            double blen = Math.hypot(bx, bz);
            if (blen < 1e-9) {
                // 180° 折返：两段的偏移线平行无交点，退化到前一段法线
                ux[k] = n0x;
                uz[k] = n0z;
                sc[k] = 1.0;
                continue;
            }
            ux[k] = bx / blen;
            uz[k] = bz / blen;
            // 1/cos(φ/2) = 1 / (u·n0)（两边对称），夹到 MITER_LIMIT 防尖刺
            double denom = ux[k] * n0x + uz[k] * n0z;
            sc[k] = denom < 1e-9 ? JOINT_MITER_LIMIT : Math.min(1.0 / denom, JOINT_MITER_LIMIT);
        }

        for (int i = 0; i + 1 < n; i++) {
            double s0 = arc[i];
            double s1 = arc[i + 1];
            // 法线仍按该段的切线求（着色法线；几何上相邻段共用顶点，法线差只是平滑问题）
            double lx = -tz[i];
            double lz = tx[i];

            for (double[] seg : section) {
                double v0 = seg[0];
                double sy0 = seg[1];
                double v1 = seg[2];
                double sy1 = seg[3];
                double dv = v1 - v0;
                double dy = sy1 - sy0;
                double nl = Math.hypot(dv, dy);
                if (nl < 1e-12) continue;
                // (v,y) 逆时针时外法线为 (dy,-dv)
                double nv = dy / nl;
                double ny = -dv / nl;
                double nx = side * nv * lx;
                double nz = side * nv * lz;

                // 顶点 i / i+1 的断面点：沿各自顶点的角平分方向、按斜接系数放大 → 相邻段共用同一组点
                // 顶点 i / i+1 的断面点：沿各自顶点的角平分方向、按斜接系数放大 → 相邻段共用同一组点
                double ax = px[i] + ux[i] * side * v0 * sc[i];
                double az = pz[i] + uz[i] * side * v0 * sc[i];
                double bx = px[i + 1] + ux[i + 1] * side * v0 * sc[i + 1];
                double bz = pz[i + 1] + uz[i + 1] * side * v0 * sc[i + 1];
                double cx = px[i + 1] + ux[i + 1] * side * v1 * sc[i + 1];
                double cz = pz[i + 1] + uz[i + 1] * side * v1 * sc[i + 1];
                double dx = px[i] + ux[i] * side * v1 * sc[i];
                double dz = pz[i] + uz[i] * side * v1 * sc[i];
                double ya = py[i] + sy0;
                double yb = py[i + 1] + sy0;
                double yc = py[i + 1] + sy1;
                double yd = py[i] + sy1;

                // 这一段是不是「钢板顶面」：横向正好 [slotOuter, outer]、y 恰在轨顶平面 0。
                // 这是<b>唯一</b>进 railTop 桶的段（材质 rail_top，v 完整 0→1）；
                // 槽底（sy<0 的水平边）、内板顶面（[inner, slotInner] 的水平边）、所有竖直边都归 railSide。
                double[] layHere = lateralLayout(slice);
                boolean plateTop = dy == 0 && Math.abs(sy0) <= 1e-9 && Math.abs(sy1) <= 1e-9
                        && Math.abs(Math.min(v0, v1) - layHere[2]) <= 1e-6
                        && Math.abs(Math.max(v0, v1) - layHere[3]) <= 1e-6;
                double[][] pts;
                if (dy == 0) {
                    if (plateTop) {
                        // 钢板顶面 → rail_top：U 沿轨（含随机相位），V = 本面横向两端取 0 / 1（完整「深-浅-深」）
                        double du0 = topU(s0);
                        double du1 = topU(s1);
                        double vv0 = v1 >= v0 ? 0.0 : 1.0;
                        double vv1 = v1 >= v0 ? 1.0 : 0.0;
                        pts = new double[][]{
                                {ax, ya, az, du0, vv0},
                                {bx, yb, bz, du1, vv0},
                                {cx, yc, cz, du1, vv1},
                                {dx, yd, dz, du0, vv1}};
                    } else {
                        // 槽底 / 内板顶面 / 轨底 → rail_side：与竖直面同一套随机窗口
                        double du0 = sideU(s0);
                        double du1 = sideU(s1);
                        double vv0 = sideV(lateralU(v0, inner, outer));
                        double vv1 = sideV(lateralU(v1, inner, outer));
                        pts = new double[][]{
                                {ax, ya, az, du0, vv0},
                                {bx, yb, bz, du1, vv0},
                                {cx, yc, cz, du1, vv1},
                                {dx, yd, dz, du0, vv1}};
                    }
                } else {
                    // 竖直面（槽壁/竖直外墙）→ 仍用 rail_top，但只取样<b>顶部暗边行</b>：
                    // U = 沿轨参数，V = 高度（轨底 0 → 轨顶 1）映射到 [DARK_V_MIN, DARK_V_MAX]
                    double su0 = sideU(s0);
                    double su1 = sideU(s1);
                    // sy 是「相对轨顶」的负值：换算成「轨底 0 → 轨顶 1」，再映射到暗边带
                    double sv0 = sideV((sy0 - bodyBottomHere) / heightSpan);
                    double sv1 = sideV((sy1 - bodyBottomHere) / heightSpan);
                    pts = new double[][]{
                            {ax, ya, az, su0, sv0},
                            {bx, yb, bz, su1, sv0},
                            {cx, yc, cz, su1, sv1},
                            {dx, yd, dz, su0, sv1}};
                }
                plateTopFace = plateTop;
                quad(out, pts, nx, ny, nz, cuts);
            }
        }
        plateTopFace = false;
    }

    /** 端点是否落在格界上（容差 1e-6）：决定该端要不要封口。 */
    public static boolean isOnCellBoundary(Vec2 p) {
        if (p == null) return false;
        double eps = 1e-6;
        return Math.abs(p.x()) < eps || Math.abs(p.x() - 1) < eps
                || Math.abs(p.z()) < eps || Math.abs(p.z() - 1) < eps;
    }

    /**
     * 折线裁到单位格 [0,1]² 向外扩 {@code margin} 的矩形，返回<b>所有</b>连续段（不是最长的一段）。
     * <p>
     * 斜轨时中心线可能在格内进出多次，每段都是本格里真实存在的一截轨道；
     * 旧实现只留最长段，其余段连钢轨一起消失（用户反馈「一部分轨道没了」）。
     */
    public static List<List<Vec2>> clipLinesToCell(List<Vec2> line, double margin) {
        List<List<Vec2>> out = new ArrayList<>();
        if (line == null || line.size() < 2) return out;
        List<Vec2> current = new ArrayList<>();
        for (int i = 0; i + 1 < line.size(); i++) {
            Vec2[] seg = clipSegment(line.get(i), line.get(i + 1), margin);
            if (seg == null) {
                flush(current, out);
                current = new ArrayList<>();
                continue;
            }
            if (current.isEmpty()) {
                current.add(seg[0]);
            } else if (current.get(current.size() - 1).sub(seg[0]).length() > 1e-9) {
                // 上一条线段的出点接不上本条的入点，说明中间离开过格子，断开重接
                flush(current, out);
                current = new ArrayList<>();
                current.add(seg[0]);
            }
            current.add(seg[1]);
        }
        flush(current, out);
        return out;
    }

    /** 把攒好的一段折线放进结果表（长度不足 2 的丢弃）。 */
    private static void flush(List<Vec2> current, List<List<Vec2>> out) {
        if (current != null && current.size() >= 2) {
            out.add(current);
        }
    }

    /** Liang–Barsky 线段裁剪（矩形 = [-margin, 1+margin]²）。 */
    private static Vec2[] clipSegment(Vec2 a, Vec2 b, double margin) {
        double t0 = 0;
        double t1 = 1;
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double[] p = {-dx, dx, -dz, dz};
        double[] q = {a.x() + margin, 1 + margin - a.x(), a.z() + margin, 1 + margin - a.z()};
        for (int i = 0; i < 4; i++) {
            if (Math.abs(p[i]) < 1e-15) {
                if (q[i] < 0) return null;
                continue;
            }
            double r = q[i] / p[i];
            if (p[i] < 0) {
                if (r > t1) return null;
                if (r > t0) t0 = r;
            } else {
                if (r < t0) return null;
                if (r < t1) t1 = r;
            }
        }
        if (t1 - t0 < 1e-12) return null;
        return new Vec2[]{a.lerp(b, t0), a.lerp(b, t1)};
    }

    /**
     * 端面封口：中心点扇形配对成四边形（断面点数按偶数设计，最后一个四边形跨回首点）。
     * UV 用断面平面 (v, y) 的线性映射：U = 归一化横向位置，V = y。
     */
    private static void cap(RawMeshBuilder out, List<Vec2> line, List<double[]> section, int side,
                            TrackSlice slice, CellSnapshot snap, double lift, boolean atStart,
                            List<List<Vec2>> cuts) {
        plateTopFace = false;   // 端面一律进 railSide 桶（rail_side）
        Vec2 anchor = atStart ? line.get(0) : line.get(line.size() - 1);
        Vec2 other = atStart ? line.get(1) : line.get(line.size() - 2);
        Vec2 tangent = anchor.sub(other);
        if (tangent.length() < 1e-9) return;
        tangent = tangent.normalized();
        double lx = -tangent.z();
        double lz = tangent.x();
        double baseY = DeckSurface.railTopY(slice, snap, anchor.x(), anchor.z()) + lift;
        // 起点端面朝 -切线方向，终点端面朝 +切线方向
        double nx = atStart ? -tangent.x() : tangent.x();
        double nz = atStart ? -tangent.z() : tangent.z();

        double inner = lateralRange(slice)[0];
        double outer = lateralRange(slice)[1];
        // 端面高度基准：轨底（bodyBottom）→ 轨顶，映射 0..1 后落进侧面裁切窗口
        double capBodyBottom = bodyBottom(slice, snap.deckTopY - slice.railTopY);
        double capSpan = -capBodyBottom > 1e-9 ? -capBodyBottom : 1.0;
        int n = section.size();
        // 每点 {x, y, z, v}
        double[][] pts = new double[n][4];
        double cv = 0;
        double cy = 0;
        for (int i = 0; i < n; i++) {
            double[] seg = section.get(i);
            double v = seg[0];
            double y = seg[1];
            pts[i][0] = anchor.x() + lx * side * v;
            pts[i][1] = baseY + y;
            pts[i][2] = anchor.z() + lz * side * v;
            pts[i][3] = v;
            cv += v;
            cy += y;
        }
        cv /= n;
        cy /= n;
        double centerX = anchor.x() + lx * side * cv;
        double centerY = baseY + cy;
        double centerZ = anchor.z() + lz * side * cv;

        for (int i = 0; i < n; i += 2) {
            double[] a = pts[i];
            double[] b = pts[(i + 1) % n];
            double[] c = pts[(i + 2) % n];
            // UV 与 (v, y) 成线性，中心点的 UV 取断面平均值，扇形内部才不变形
            // 端面走<b>侧面材质</b>：U = 横向位置（落进窗口宽度），V = 高度（轨底→轨顶，落进窗口高度）
            double[][] fan = {
                    {centerX, centerY, centerZ,
                            sideU(lateralU(cv, inner, outer)),
                            sideV((centerY - (baseY + capBodyBottom)) / capSpan)},
                    {a[0], a[1], a[2], sideU(lateralU(a[3], inner, outer)),
                            sideV((a[1] - (baseY + capBodyBottom)) / capSpan)},
                    {b[0], b[1], b[2], sideU(lateralU(b[3], inner, outer)),
                            sideV((b[1] - (baseY + capBodyBottom)) / capSpan)},
                    {c[0], c[1], c[2], sideU(lateralU(c[3], inner, outer)),
                            sideV((c[1] - (baseY + capBodyBottom)) / capSpan)}};
            quad(out, fan, nx, 0, nz, cuts);
        }
    }

    /**
     * 槽壁切口面：在一条「被铣掉的槽边界」上竖一个竖直四边形。
     * <p>
     * 这就是铣刀切进钢轨后留下的槽壁在交叉处的延续：水平方向 = 切口边两端点，
     * 竖直方向 = 从断面底边 {@link #bodyBottom} 到轨顶 {@link DeckSurface#railTopY}（含 lift）。
     * 法线水平（|ny| = 0），朝<b>被铣掉的一侧</b>（槽内），从槽里看才看得见。
     * <p>
     * 这里不做补集裁剪：面本来就长在被挖掉的边界上，再按同一片挖一次会整块消失（历史 bug）。
     */
    private static void emitSlotWall(RawMeshBuilder out, SlotWall wall,
                                     TrackSlice slice, CellSnapshot snap, double lift) {
        plateTopFace = false;   // 槽壁切口面一律进 railSide 桶（rail_side）
        Vec2 a = wall.a();
        Vec2 b = wall.b();
        if (a.sub(b).length() < CUT_EPS) return;
        double bottom = bodyBottom(slice, snap.deckTopY - slice.railTopY);
        double topA = DeckSurface.railTopY(slice, snap, a.x(), a.z()) + lift;
        double topB = DeckSurface.railTopY(slice, snap, b.x(), b.z()) + lift;
        double lowA = topA + bottom;
        double lowB = topB + bottom;
        // 侧面材质 UV：U = 沿切口边的累计长度（落进裁切窗口），V = 高度（轨底 0 → 轨顶 1，落进窗口）
        double len = wall.length();
        double span = -bottom > 1e-9 ? -bottom : 1.0;
        double su0 = sideU(0);
        double su1 = sideU(len);
        double sv0 = sideV(0);
        double sv1 = sideV(1);
        double[][] pts = {
                {a.x(), lowA, a.z(), su0, sv0},
                {b.x(), lowB, b.z(), su1, sv0},
                {b.x(), topB, b.z(), su1, sv1},
                {a.x(), topA, a.z(), su0, sv1}};
        quad(out, pts, wall.nx(), 0, wall.nz(), null);
    }

    /**
     * 发射一块断面段：先把它<b>裁到格矩形 [0,1]²</b>，再按「被铣掉的槽条带」做补集裁剪，
     * 最后扇形三角化发出。
     * <p>
     * 格矩形裁剪是硬约束：钢轨任何顶点都不许跑出方块（用户反馈过「钢条伸出铺面很远」）。
     * 上一轮为了不丢「中心线在格外、钢带在格内」的情况放宽了中心线裁剪，
     * 那种放宽只有在每个面都被格矩形二次裁剪之后才是安全的。
     * <p>
     * <b>槽铣切</b>（用户口径）：钢本身连续，只有<b>槽</b>把对侧钢轨切断。所以每块面按每片
     * 凸的「被铣掉区域」做<b>补集</b>裁剪（{@link #subtractConvexPlan}），挖掉的宽度就是槽宽
     * {@link #SLOT_WIDTH}，而不是整条 band 宽 —— 这里少挖的 0.14−0.045 正是用户看到的「缺口」。
     */
    private static void quad(RawMeshBuilder out, double[][] pts, double nx, double ny, double nz,
                             List<List<Vec2>> cuts) {
        List<double[]> poly = new ArrayList<>(pts.length);
        for (double[] p : pts) poly.add(p);
        for (int plane = 0; plane < 4; plane++) {
            poly = clipToCell(poly, plane);
            if (poly.size() < 3) return;
        }
        List<List<double[]>> pieces = new ArrayList<>(2);
        pieces.add(poly);
        if (cuts != null) {
            // 面的平面包围盒：完全不相交的补集片直接跳过（省掉一次多边形裁剪/分解）
            double bx0 = Double.POSITIVE_INFINITY;
            double bx1 = Double.NEGATIVE_INFINITY;
            double bz0 = Double.POSITIVE_INFINITY;
            double bz1 = Double.NEGATIVE_INFINITY;
            for (double[] p : poly) {
                bx0 = Math.min(bx0, p[0]);
                bx1 = Math.max(bx1, p[0]);
                bz0 = Math.min(bz0, p[2]);
                bz1 = Math.max(bz1, p[2]);
            }
            for (List<Vec2> cut : cuts) {
                if (cut == null || cut.size() < 3) continue;
                if (cutMergeEnabled && !bboxOverlapsPlan(cut, bx0, bx1, bz0, bz1)) continue;
                List<List<double[]>> next = new ArrayList<>(pieces.size() + 1);
                for (List<double[]> piece : pieces) {
                    // <b>竖直面（平面投影退化成一条线）要用「严格外侧」</b>：
                    // 钢轨的最内/最外两侧竖直墙正好落在被挖区（A带 ∩ B槽）的边界边上，
                    // 宽松语义（边界=保留）会把它们整片留下 —— 这正是「交叉只删了顶面、侧面照样穿过」。
                    // 严格语义下「贴着边界也算挖掉」，同时 A 自己的槽底（水平面）不受影响。
                    // 判据不只看「平面投影退化」：竖直墙的平面投影本应退化，但只要断面有任何倾斜
                    // （铺面 pitch/roll）投影就不再精确为零，只看 planArea 会让钢轨侧壁重新穿过交叉
                    // （「只删顶面、侧面照样穿过」的老问题）。所以同时用断面法线判定竖直面。
                    boolean strictOutside = Math.abs(ny) < 0.5 || Math.abs(planArea(piece)) < 1e-12;
                    next.addAll(subtractConvexPlan(piece, cut, strictOutside));
                }
                pieces = next;
                if (pieces.isEmpty()) return;
            }
        }
        for (List<double[]> piece : pieces) {
            // 补集裁剪会在切口边界上留下零面积的薄片：白占顶点，先丢掉
            if (degenerate3D(piece)) continue;
            if (slimPieces) {
                AUDIT_SLIM_TOTAL.incrementAndGet();
                double area = pieceArea3D(piece);
                if (area < SLIM_PIECE_MIN_AREA) {
                    // 碎渣：小于一个贴图像素，丢掉不影响观感，省下 4 个顶点/三角
                    AUDIT_SLIM_DROPPED.incrementAndGet();
                    continue;
                }
            }
            // 半平面裁剪保凸，所以从第 0 个顶点扇形三角化就是对的（竖直面在平面投影上退化也不影响）。
            // 裁剪可能留下**重合顶点**，扇形里会因此出现零面积三角形（不可见但白占顶点、
            // 也会污染「面是否落在对方槽内」这类统计）—— 这里直接跳过。
            for (int i = 1; i + 1 < piece.size(); i++) {
                // 阈值 1e-8 格² ≈ 0.1mm × 0.1mm：切口边界上的细长薄片全在这个量级，
                // 留着既不可见又会污染「面是否落在槽内」之类统计（实测 5.8e-09 的薄片）
                if (triangleArea3D(piece.get(0), piece.get(i), piece.get(i + 1)) < 1e-8) continue;
                tri(out, piece.get(0), piece.get(i), piece.get(i + 1), nx, ny, nz);
            }
        }
    }

    /**
     * 面（平面凸多边形，5 属性顶点）减去一个凸多边形：把凸包的每条边当外半平面逐块切，
     * 第 i 块 = face ∩ (前 i 条边的内侧) ∩ (第 i 条边的外侧)，这些块互不重叠且并集正好是
     * {@code face − cut}（与 {@link PolyOps#subtractConvex} 同一套分块，但保留顶点属性 u/v）。
     */
    /** 5 属性顶点多边形的平面投影面积（判断竖直面：投影退化 → 面积 ≈ 0）。 */
    private static double planArea(List<double[]> poly) {
        double sum = 0;
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            double[] a = poly.get(i);
            double[] b = poly.get((i + 1) % n);
            sum += a[0] * b[2] - b[0] * a[2];
        }
        return sum * 0.5;
    }

    /**
     * @param strictOutside true = 「外侧」判定用严格大于（贴着切口边界也算挖掉），用于平面投影退化的竖直面：
     *                      钢轨最内/最外侧竖直墙正好压在挖区边界上，宽松语义会让它们整片留下。
     */
    private static List<List<double[]>> subtractConvexPlan(List<double[]> face, List<Vec2> cut,
                                                          boolean strictOutside) {
        AUDIT_SUBTRACT_CALLS.incrementAndGet();
        List<List<double[]>> out = new ArrayList<>(3);
        List<Vec2> c = PolyOps.clean(cut);
        if (c.size() < 3) {
            out.add(face);
            return out;
        }
        if (PolyOps.area(c) < 0) {
            java.util.Collections.reverse(c);
        }
        int n = c.size();
        for (int i = 0; i < n; i++) {
            List<double[]> piece = face;
            for (int j = 0; j < i; j++) {
                if (piece.size() < 3) break;
                Vec2 nrm = insideNormal(c, j);
                piece = clipPlanHalfPlane(piece, c.get(j), nrm, 1);
            }
            if (piece.size() < 3) continue;
            Vec2 nrm = insideNormal(c, i);
            piece = strictOutside
                    ? clipPlanHalfPlaneStrict(piece, c.get(i), nrm)
                    : clipPlanHalfPlane(piece, c.get(i), nrm, -1);
            if (piece.size() >= 3) out.add(piece);
        }
        return out;
    }

    /** 补集片的平面包围盒是否与 [x0,x1]×[z0,z1] 相交（快速剔除，容差 1e-9）。 */
    private static boolean bboxOverlapsPlan(List<Vec2> poly, double x0, double x1, double z0, double z1) {
        double cx0 = Double.POSITIVE_INFINITY;
        double cx1 = Double.NEGATIVE_INFINITY;
        double cz0 = Double.POSITIVE_INFINITY;
        double cz1 = Double.NEGATIVE_INFINITY;
        for (Vec2 p : poly) {
            if (p.x() < cx0) cx0 = p.x();
            if (p.x() > cx1) cx1 = p.x();
            if (p.z() < cz0) cz0 = p.z();
            if (p.z() > cz1) cz1 = p.z();
        }
        return cx1 > x0 - 1e-9 && cx0 < x1 + 1e-9 && cz1 > z0 - 1e-9 && cz0 < z1 + 1e-9;
    }

    /** 多边形（扇形三角化后）的 3D 面积，用于判断补集碎片是不是碎渣。 */
    private static double pieceArea3D(List<double[]> piece) {
        double sum = 0;
        for (int i = 1; i + 1 < piece.size(); i++) {
            sum += triangleArea3D(piece.get(0), piece.get(i), piece.get(i + 1));
        }
        return sum;
    }

    /** 三角形的 3D 面积（扇形三角化时用来丢掉重合顶点造成的零面积三角形）。 */
    private static double triangleArea3D(double[] a, double[] b, double[] c) {
        double ux = b[0] - a[0];
        double uy = b[1] - a[1];
        double uz = b[2] - a[2];
        double vx = c[0] - a[0];
        double vy = c[1] - a[1];
        double vz = c[2] - a[2];
        double cx = uy * vz - uz * vy;
        double cy = uz * vx - ux * vz;
        double cz = ux * vy - uy * vx;
        return 0.5 * Math.hypot(Math.hypot(cx, cy), cz);
    }

    /** 3D 面积近似为 0 的碎片（切口边界上的零面积薄片）：直接丢掉，省顶点也免得污染几何统计。 */
    private static boolean degenerate3D(List<double[]> piece) {
        if (piece.size() < 3) return true;
        double[] a = piece.get(0);
        double sum = 0;
        for (int i = 1; i + 1 < piece.size(); i++) {
            double[] b = piece.get(i);
            double[] c = piece.get(i + 1);
            double ux = b[0] - a[0];
            double uy = b[1] - a[1];
            double uz = b[2] - a[2];
            double vx = c[0] - a[0];
            double vy = c[1] - a[1];
            double vz = c[2] - a[2];
            double cx = uy * vz - uz * vy;
            double cy = uz * vx - ux * vz;
            double cz = ux * vy - uy * vx;
            sum += 0.5 * Math.hypot(Math.hypot(cx, cy), cz);
        }
        return sum < 1e-12;
    }

    /** 逆时针凸多边形第 i 条边的「内法线」({@code (-d.z, d.x)})：内积 ≥ 0 即在这一边内侧。 */
    private static Vec2 insideNormal(List<Vec2> poly, int i) {
        Vec2 d = poly.get((i + 1) % poly.size()).sub(poly.get(i)).normalized();
        return new Vec2(-d.z(), d.x());
    }

    /**
     * 竖直半平面裁剪（Sutherland–Hodgman）：保留 {@code sign * (p-a)·n >= 0} 的一侧。
     * 顶点是 {@code {x, y, z, u, v}}，属性按线性插值；平面法线只有水平分量，
     * 所以竖直面、水平面、端面都能用同一套裁剪（竖直面在平面投影上退化成线段也成立）。
     */
    /** 严格「外侧」裁剪：只保留 {@code (p-a)·n > +CUT_EPS} 的点（贴着边界的一律丢掉）。 */
    private static List<double[]> clipPlanHalfPlaneStrict(List<double[]> poly, Vec2 a, Vec2 n) {
        List<double[]> out = new ArrayList<>(poly.size() + 2);
        int m = poly.size();
        if (m < 3) return out;
        for (int i = 0; i < m; i++) {
            double[] cur = poly.get(i);
            double[] prev = poly.get((i + m - 1) % m);
            double cv = -((cur[0] - a.x()) * n.x() + (cur[2] - a.z()) * n.z());
            double pv = -((prev[0] - a.x()) * n.x() + (prev[2] - a.z()) * n.z());
            boolean cin = cv > CUT_EPS;
            boolean pin = pv > CUT_EPS;
            if (cin != pin) {
                double t = Math.abs(pv - cv) < 1e-15 ? 0 : (CUT_EPS - pv) / (cv - pv);
                double[] r = new double[5];
                for (int k = 0; k < 5; k++) r[k] = prev[k] + (cur[k] - prev[k]) * t;
                out.add(r);
            }
            if (cin) out.add(cur);
        }
        return out;
    }

    private static List<double[]> clipPlanHalfPlane(List<double[]> poly, Vec2 a, Vec2 n, double sign) {
        List<double[]> out = new ArrayList<>(poly.size() + 2);
        int m = poly.size();
        if (m < 3) return out;
        for (int i = 0; i < m; i++) {
            double[] cur = poly.get(i);
            double[] prev = poly.get((i + m - 1) % m);
            double cv = sign * ((cur[0] - a.x()) * n.x() + (cur[2] - a.z()) * n.z());
            double pv = sign * ((prev[0] - a.x()) * n.x() + (prev[2] - a.z()) * n.z());
            boolean cin = cv >= -CUT_EPS;
            boolean pin = pv >= -CUT_EPS;
            if (cin != pin) {
                double t = Math.abs(pv - cv) < 1e-15 ? 0 : pv / (pv - cv);
                double[] r = new double[5];
                for (int k = 0; k < 5; k++) r[k] = prev[k] + (cur[k] - prev[k]) * t;
                out.add(r);
            }
            if (cin) out.add(cur);
        }
        return out;
    }
    /**
     * 竖直平面裁剪（Sutherland–Hodgman）：0 = x≥0，1 = x≤1，2 = z≥0，3 = z≤1。
     * 顶点是 {@code {x, y, z, u, v}}，属性按线性插值。
     */
    private static List<double[]> clipToCell(List<double[]> poly, int plane) {
        List<double[]> out = new ArrayList<>(poly.size() + 2);
        int n = poly.size();
        double limit = (plane == 1 || plane == 3) ? 1 : 0;
        for (int i = 0; i < n; i++) {
            double[] cur = poly.get(i);
            double[] prev = poly.get((i + n - 1) % n);
            double cv = plane < 2 ? cur[0] : cur[2];
            double pv = plane < 2 ? prev[0] : prev[2];
            boolean cin = insidePlane(cv, limit);
            boolean pin = insidePlane(pv, limit);
            if (cin != pin) {
                out.add(lerp(prev, cur, pv, cv, limit, plane));
            }
            if (cin) out.add(cur);
        }
        return out;
    }

    private static boolean insidePlane(double value, double limit) {
        // limit 为 0 时是「≥ 下界」，为 1 时是「≤ 上界」
        return limit == 0 ? value >= -1e-12 : value <= 1 + 1e-12;
    }

    private static double[] lerp(double[] a, double[] b, double av, double bv, double limit, int plane) {
        double delta = bv - av;
        double t = Math.abs(delta) < 1e-15 ? 0 : (limit - av) / delta;
        t = Math.max(0, Math.min(1, t));
        double[] r = new double[5];
        for (int k = 0; k < 5; k++) {
            r[k] = a[k] + (b[k] - a[k]) * t;
        }
        // 裁剪点严格落在平面上，避免浮点误差把顶点又推出格界。
        // <b>只钉被裁的那一轴</b>：历史上两个轴一起钳，中心线按钢带外沿放宽后，
        // 「斜穿角上 x<0 且 z<0」的四边形会被插值点拽到 z=0，整块面变形丢面积
        // （表现为斜线靠角那一截钢轨缺失）。另一轴必须保持插值原值。
        if (plane < 2) {
            r[0] = limit == 0 ? Math.max(0, r[0]) : Math.min(1, r[0]);
        } else {
            r[2] = limit == 0 ? Math.max(0, r[2]) : Math.min(1, r[2]);
        }
        return r;
    }
    /**
     * 发射一个三角形并校正绕序，使写入法线与期望法线同向。
     * <p>
     * <b>非有限值防线</b>：任何一个坐标/法线是 NaN 或 ±Inf 时，<b>整个三角形都不发</b>
     * （不是只丢一个顶点：RawMeshBuilder 每满 faceSize 个顶点就切一个面，丢单个顶点会把后面
     * 所有面的顶点分组全部错位）。宁可少几个面，也绝不让 NaN 进顶点缓冲 ——
     * 一个 NaN 就会让整条缓冲的包围盒/体积失效，表现正是「有面但整片什么都不显示」。
     */
    private static void tri(RawMeshBuilder out, double[] a, double[] b, double[] c, double nx, double ny, double nz) {
        // 顶面/侧面分桶：按<b>断面上的哪一段</b>（plateTopFace），不看朝向也不看几何水平。
        // 只有「钢板顶面 [0.7175, 0.785883] 那条边」进 railTop 桶（rail_top 材质），
        // 槽底 / 内板顶面 / 所有竖直面 / 端面 / 切口面一律进 railSide 桶（rail_side 材质）。
        RawMeshBuilder sink = sideSink;
        if (sink != null && sink != out && !plateTopFace) {
            out = sink;
        }
        if (!finiteVertex(a, nx, ny, nz) || !finiteVertex(b, nx, ny, nz) || !finiteVertex(c, nx, ny, nz)) {
            AUDIT_DROPPED.incrementAndGet();
            if (hasNaN(a) || hasNaN(b) || hasNaN(c) || Double.isNaN(nx) || Double.isNaN(ny) || Double.isNaN(nz)) {
                AUDIT_NAN.incrementAndGet();
            } else {
                AUDIT_INF.incrementAndGet();
            }
            if (AUDIT_FIRST_BAD.isEmpty()) {
                AUDIT_FIRST_BAD = "a=(" + a[0] + "," + a[1] + "," + a[2] + ") b=(" + b[0] + "," + b[1] + "," + b[2]
                        + ") c=(" + c[0] + "," + c[1] + "," + c[2] + ") n=(" + nx + "," + ny + "," + nz + ")";
            }
            return;
        }
        double ax = b[0] - a[0];
        double ay = b[1] - a[1];
        double az = b[2] - a[2];
        double bx = c[0] - a[0];
        double by = c[1] - a[1];
        double bz = c[2] - a[2];
        double cx = ay * bz - az * by;
        double cy = az * bx - ax * bz;
        double cz = ax * by - ay * bx;
        AUDIT_EMITTED.incrementAndGet();
        if (Math.hypot(Math.hypot(cx, cy), cz) < 1e-12) {
            AUDIT_ZERO_AREA.incrementAndGet();
        }
        // 第 4 个顶点复制第 3 个：本项目的 RawMeshBuilder 按 faceSize=4 收面，
        // 退化三角形面积为零，不影响渲染，但能保持「每面 4 顶点」的约定（下游 UV 统计也按这个约定写）。
        if (cx * nx + cy * ny + cz * nz < 0) {
            vertex(out, a, nx, ny, nz);
            vertex(out, c, nx, ny, nz);
            vertex(out, b, nx, ny, nz);
            vertex(out, b, nx, ny, nz);
        } else {
            vertex(out, a, nx, ny, nz);
            vertex(out, b, nx, ny, nz);
            vertex(out, c, nx, ny, nz);
            vertex(out, c, nx, ny, nz);
        }
    }

    /** 顶点 {x,y,z,u,v} + 法线全部有限才算可发。 */
    private static boolean finiteVertex(double[] p, double nx, double ny, double nz) {
        return p != null && Double.isFinite(p[0]) && Double.isFinite(p[1]) && Double.isFinite(p[2])
                && Double.isFinite(p[3]) && Double.isFinite(p[4])
                && Double.isFinite(nx) && Double.isFinite(ny) && Double.isFinite(nz);
    }

    private static boolean hasNaN(double[] p) {
        return p != null && (Double.isNaN(p[0]) || Double.isNaN(p[1]) || Double.isNaN(p[2])
                || Double.isNaN(p[3]) || Double.isNaN(p[4]));
    }

    private static void vertex(RawMeshBuilder out, double[] p, double nx, double ny, double nz) {
        out.vertex(p[0], p[1], p[2])
                .normal((float) nx, (float) ny, (float) nz)
                .uv((float) p[3], (float) p[4])
                .endVertex();
    }
}
