package com.fangsu.ui;

import com.fangsu.Main;
import com.fangsu.blockEntities.BlockEntityMultiDirectionNode;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mtr.RailAngleExtra;
import com.fangsu.network.RailTiltPackets;
import com.fangsu.util.NodeConnector;
import com.fangsu.utils.GraphicContext;
import mtr.data.Rail;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 万向节点配置界面（扳手右键打开），<b>取代旧的全屏 {@code NodeAngleScreen}</b>（该类已删除）。
 * <p>
 * 界面基于仓库通用的可滚动配置框架 {@link BasicConfigScreen}，采用<b>双列布局</b>：
 * <ul>
 *   <li>左列 = 平移（X / Y / Z 偏移）</li>
 *   <li>右列 = 旋转（俯仰角 / 方向 / 翻滚角）与「旋转绑定」开关</li>
 *   <li>两列下方 = 轨道编辑（占满两列宽度）：逐轨道超高编辑入口 + 清除 + 重建 + 重新读取</li>
 *   <li>顶部输入模式切换与底部「保存并退出」按钮横跨两列</li>
 * </ul>
 * 双列是为了避免单列纵向堆叠导致的频繁滚动。
 * <p>
 * <b>为什么是「取代」而不是「扩展」旧界面</b>（P5-5 的显式决策）：
 * <ol>
 *   <li>旧界面是 205 行的手写 {@code Screen}（每个控件各自一个 {@code EditBox} + 一个步进按钮），
 *       P5-2 只在它上面加了三个输入框；要拿到 MTR4 的「两列 + 滑块/输入框互斥 + 就地输入」
 *       就必须换掉整套输入机制，而这套机制在 MTR4 里已经是基类
 *       {@link NumericInputConfigScreen}，与逐轨道超高界面<b>共用同一份实现</b>。
 *       把旧界面「扩展」成同样的东西，等于把基类重写一遍 —— 那就不是扩展了；</li>
 *   <li>MTR4 工程里已经没有 {@code NodeAngleScreen}，两个工程的界面集合因此保持一致
 *       （本工程的既有约定是两个版本互相对应）；</li>
 *   <li>P5-2 的行为全部保留并走同一条写入路径：俯仰 / 翻滚两个编辑入口
 *       （{@link #applyAngles()} → {@code setAnglesAndSync} → {@link #tryRefreshRails()}）、
 *       BE 的钳制函数（{@code clampPitch/clampRoll/clampHalfGauge/clampOffset}）、
 *       {@code NODE_REFRESH_RAIL} 重建路径、以及「外轨超高开关 / 半轨距由 BE 持有、
 *       本面板原样透传」的既有分工，一个字都没有改。</li>
 * </ol>
 * <p>
 * <b>角度语义</b>：俯仰角（硬边界 ±60°）正 = 沿节点方向前进时上坡，会写进 {@code RailPoseExtra}
 * 的纵坡端点、驱动几何内核的三次 Hermite 竖向剖面。
 * 翻滚角（硬边界 ±45°）正 = 前进方向右手侧抬高（外轨超高约定），驱动
 * {@code 半轨距·|sin(roll)|} 的中心线抬升；逐轨道超高（{@link RailTiltConfigScreen}）优先于它。
 * <p>
 * <b>两套区间</b>：滑块区间（拖动便利）与硬边界（{@code BlockEntityMultiDirectionNode} 单点定义、
 * 服务端强制）是两回事。逐轨道超高的两套区间见 {@link RailTiltConfigScreen}。
 * <p>
 * <b>顶部总开关一刀切</b>：面板顶部的按钮切换<b>整块面板</b>的数字输入方式，两种方式<b>互斥</b>
 * （滑块模式每行只有滑块；输入模式每行只有输入框）。机制本身在基类
 * {@link NumericInputConfigScreen} 里，与 {@link RailTiltConfigScreen} 共用同一份实现。
 * <p>
 * <b>写入顺序</b>：{@code BE_SYNC} → {@code NODE_REFRESH_RAIL}。姿态（平移 / 俯仰 / 翻滚 / 开关 / 半轨距）
 * 也随刷新请求一起发送，所以顺序不再影响正确性（见 {@code ModNetwork.handleNodeRefreshRail} 的说明），
 * 这里保持固定顺序只为数据流统一。
 * <p>
 * <b>几何预检</b>：每次改动姿态都会做一次纯客户端、不发包的几何预检
 * （{@link NodeConnector#isGeometryValid(BlockPos, double, BlockPos, double, double)}，锚点感知版本）；
 * 预检不通过时显示红字警告并<b>跳过</b>重建请求，避免触发一次注定失败的重建。
 * 预检只看<b>水平</b>姿态（平移 + 方向），俯仰 / 翻滚 / 半轨距<b>不</b>参与，
 * 所以它们再极端也不会触发红字或阻断重建。
 */
public class MultiDirectionNodeConfigScreen extends NumericInputConfigScreen {

    private static final int GAP = 4;

    /**
     * 面板宽度放大系数。两列布局比原单列宽，但不用 2 倍：1.5 倍即可容纳两列控件，
     * 同时避免面板占掉太多屏幕、把世界遮住。
     */
    private static final float PANEL_WIDTH_FACTOR = 1.5f;
    /** 两列之间的间隙（像素）。 */
    private static final int COLUMN_GAP = 6;
    /** 列宽下限：再窄滑块/按钮就点不准了。 */
    private static final int MIN_COLUMN_WIDTH = 60;

    // ==================== 两套区间：滑块区间 vs 硬边界 ====================
    //
    // 本界面刻意区分两个概念，混用它们是「数值框里的值看起来没生效」的根因：
    //
    //   * 硬边界 —— 真正的安全上限，由方块实体
    //     {@link BlockEntityMultiDirectionNode} 单点定义并在服务端强制（setter / readC2S / load
    //     三条入口都会过一遍），客户端这里只是**引用**同一批常量与静态方法来镜像钳制。
    //     超出硬边界的输入会被钳制（方向则是回绕），这是唯一会被真正丢掉信息的边界。
    //   * 滑块区间（*_SLIDER_*）—— 只是「拖动手感」的便利区间，比硬边界窄。
    //     超出滑块区间但未超硬边界的值**照原样保留**：滑块钉在最近的端点，
    //     真值仍然完整地显示在数值框里（见 {@link NumericInputConfigScreen}）。

    /** 平移步进：1/16 格，与 {@code ObjBlockConfigScreen} 的物件平移一致。 */
    private static final float TRANSLATE_STEP = 0.0625f;
    /** 平移滑块区间（格）。硬边界是 {@link BlockEntityMultiDirectionNode#MAX_OFFSET}（更宽）。 */
    private static final float TRANSLATE_SLIDER_MIN = -1f;
    private static final float TRANSLATE_SLIDER_MAX = 1f;

    /**
     * 方向（Y 旋转）滑块区间（度）。
     * <p>
     * 万向节点支持任意角度，MTR 原版 22.5° 的步进限制在这里不适用，因此步进为 0（连续）。
     * 滑块只用 [0,180] 表示：直线轨道上 180° 与 0° 是同一条线。
     * <p>
     * <b>方向没有硬边界</b>：它是周期量，输入框里键入的值由
     * {@link RailAngleExtra#normalizeNodeDegrees(double)} <b>回绕</b>到 [0, 360)，而不是钳制。
     */
    private static final float DIRECTION_SLIDER_MIN = 0f;
    private static final float DIRECTION_SLIDER_MAX = 180f;
    private static final float DIRECTION_STEP = 0f;

    /**
     * 俯仰角（纵坡）滑块区间（度），步进 0.5°。正值 = 沿节点方向前进时上坡。
     * <p>
     * 滑块保持 ±15°（手感不变）；硬边界是
     * {@link BlockEntityMultiDirectionNode#MAX_PITCH_DEG}（±60°）。
     */
    private static final float PITCH_SLIDER_MIN = -15f;
    private static final float PITCH_SLIDER_MAX = 15f;
    private static final float PITCH_STEP = 0.5f;

    /**
     * 翻滚角（外轨超高）滑块区间（度），步进 0.5°。正值 = 前进方向右手侧抬高。
     * <p>
     * 滑块保持 ±20°（手感不变）；硬边界是
     * {@link BlockEntityMultiDirectionNode#MAX_ROLL_DEG}（±45°，与逐轨道超高的
     * {@code RailPoseExtra#MAX_RAIL_TILT_DEGREES} 一致）。
     * 超出滑块区间的值只能从输入框键入（切到输入模式），滑块钉在端点。
     * <p>
     * 本面板的翻滚角是相连轨道的<b>默认值</b>：已按轨道授权过逐轨道超高的轨道以授权值为准。
     */
    private static final float ROLL_SLIDER_MIN = -20f;
    private static final float ROLL_SLIDER_MAX = 20f;
    private static final float ROLL_STEP = 0.5f;

    /**
     * 轨道解析的射线拾取参数：视线与「眼睛 → 轨道采样点」的夹角正切上限。
     * <p>
     * 0.25 ≈ 14°，比准星的可视范围松一点（轨道带很窄，玩家在十几格外很难精确对准），
     * 但比「随便挑一条」严得多。超过这个角度就不算「正在看着的轨道」，回退到确定性第一条。
     */
    private static final double LOOK_ANGLE_TANGENT = 0.25D;
    /** 射线拾取的最远距离（格）：再远的轨道即使正好在准星上也不认为「在看」。 */
    private static final double LOOK_MAX_DISTANCE = 24.0D;
    /** 每条轨道的采样点数：接缝两侧都要采到，8 个点足够区分「看着哪一条」。 */
    private static final int LOOK_SAMPLES = 8;

    private final BlockEntityMultiDirectionNode node;

    /**
     * 本面板当前编辑的那条轨道，也是轨道编辑区<b>全部</b>按钮（逐轨道超高编辑 / 清除逐轨道超高 /
     * 重建轨道几何 / 重新读取轨道）<b>唯一</b>的轨道来源；为 {@code null} 时它们一起灰显
     * （节点未连接、且没看向任何相连轨道）。
     * <p>
     * <b>不是 final</b>：服务端每次应用编辑都会重播轨道，客户端数据里的实例会被换成新的，
     * 因此重建界面时（以及点「逐轨道超高编辑…」时）用 {@link #syncRailSnapshot()} 把这里的快照
     * 刷新到新实例 —— 刷新只按<b>已经确定的那一对端点</b>重查，不会换来源、也不会自己跳目标。
     */
    @Nullable
    private Rail rail;
    /**
     * 当前编辑轨道的另一端（= 轨道的 {@code position2}；本节点侧是 {@code position1}）。
     * <p>
     * 必须与 {@link #rail} 一起维护：MTR3 的 {@code mtr.data.Rail} <b>不保存</b>端点，
     * 而逐轨道超高的 C2S 通道与编辑界面都需要两个端点（服务端用它们重算轨道身份）。
     * 为 {@code null} 表示「没有解析到轨道」。
     */
    @Nullable
    private BlockPos railOtherPos;

    private double offsetX;
    private double offsetY;
    private double offsetZ;
    private double direction;
    /**
     * 俯仰角（度，纵坡）。正 = 沿方向前进时上坡。硬边界 ±{@code MAX_PITCH_DEG}（±60°），
     * 滑块区间仍是 ±15°。改动后走 {@link #applyAngles()} → {@link #tryRefreshRails()} 触发轨道重建。
     */
    private double pitchDeg;
    /** 翻滚角（度，外轨超高）。正 = 前进方向右手侧抬高。硬边界 ±{@code MAX_ROLL_DEG}（±45°）。 */
    private double rollDeg;
    /**
     * 外轨超高开关（镜像 BE 的 {@code superelevation}，默认 true）。
     * <p>
     * <b>本面板没有它的编辑入口</b>（逐轨道超高界面里按轨道编辑半轨距；外轨超高由逐轨道超高的
     * 授权值或节点翻滚角表达）。它在 BE 里保留、也随 BE_SYNC 全量载荷同步，
     * 本面板只做「读入 → 原样写回」的透传，因此不会把用户没编辑过的东西改掉。
     */
    private boolean superelevation;
    /** 半轨距（米，镜像 BE 的 {@code rollOffsetM}）。与 {@link #superelevation} 一样，本面板只透传。 */
    private double halfGaugeM;
    /**
     * 旋转绑定开关（仅右列，节点已连接时锁定为 true）。
     * <p>
     * 是 → 写方向时调 {@code setDirectionAndBind}（绑定）；否 → 调 {@code setDirectionUnbound}（只写值、不绑定）。
     * 初值取节点当前的 {@code directionBonded}；节点已连接时强制为「是」并锁定（服务端重建轨道依赖绑定方向）。
     */
    private boolean rotationBonded;

    /** 当前编辑的「平移 + 方向」是否无法产出合法轨道（由 {@link #refreshPoseValidity()} 维护）。 */
    private boolean poseInvalid;

    public MultiDirectionNodeConfigScreen(BlockEntityMultiDirectionNode node) {
        super(ComponentHelper.translatable("ui.fangsu.multi_direction_node.title"));
        this.node = node;
        this.offsetX = node.getOffsetX();
        this.offsetY = node.getOffsetY();
        this.offsetZ = node.getOffsetZ();
        this.direction = node.getDirectionDegrees();
        this.pitchDeg = node.getPitchDegrees();
        this.rollDeg = node.getRollDegrees();
        this.superelevation = node.isSuperelevationEnabled();
        this.halfGaugeM = node.getRollOffsetM();
        // 已连接的节点方向必须保持绑定：开关强制为「是」且不可点击
        this.rotationBonded = node.isDirectionBonded() || node.isConnected();
        final NodeConnector.ConnectedRail resolved = resolveRail(node);
        this.rail = resolved == null ? null : resolved.rail();
        this.railOtherPos = resolved == null ? null : resolved.position2();
    }

    // ==================== 轨道解析（MTR3 版） ====================

    /**
     * 解析「本面板要编辑的那条轨道」。
     * <p>
     * <b>MTR3 上这个动作必须重新设计</b>：MTR4 的
     * {@code MinecraftClientData.getFacingRailAndBlockPos(false)} 在 MTR3 的整个 jar 里
     * <b>0 命中</b>（连名字都不存在），所以「原版那种准星射线」没有现成的 API 可调。
     * 本工程可用的原料只有两样：
     * <ul>
     *   <li>{@link NodeConnector#connectedRails(BlockPos)} —— 从 {@code ClientData.RAILS} 取
     *       「连到本节点的（两端点 + 轨道）」列表，顺序确定（按另一端点字典序）；</li>
     *   <li>轨道自己的世界坐标：MTR3 的 {@code Rail} 把绝对坐标算进几何，所以
     *       {@code rail.getPosition(t)}（{@code t ∈ [0, length]}）给的是<b>世界空间</b>的采样点，
     *       可以拿来和玩家的视线做几何比较。</li>
     * </ul>
     * 于是这里的解析是「在<b>本节点相连的</b>轨道里，挑视线夹角最小的那一条」，
     * 失败（没有一条在视线锥内）再回退到确定性第一条：
     * <ol>
     *   <li>相连轨道为空 → {@code null}（四件套一起灰显 + 提示）；</li>
     *   <li>视线拾取命中 → 那一条；</li>
     *   <li>否则 → 列表第一条（确定性，见 {@link NodeConnector#connectedRails}）。</li>
     * </ol>
     * <b>与 MTR4 的语义差异（如实记录）</b>：MTR4 会在<b>全世界的轨道</b>里做准星射线，
     * 因此可以编辑「节点没连着、但正看着的」轨道；MTR3 的解析被限制在<b>本节点的相连轨道</b>内
     * —— 因为服务端校验只认「本节点那条轨道」的端点对（{@code refreshNodeRail} 的语义），
     * 放开到全世界会让「编辑一条不属于本节点的轨道」变成一条没有合法写入路径的操作。
     * 这个限制对用户可见的表现是：<b>要编辑某条轨道，必须先站在连着它的节点前打开本面板</b>，
     * 而不是随便对着一条轨道点扳手。
     * <p>
     * <b>四个按钮永远一致</b>：解析只在构造期与「重新读取轨道」/重建时发生，结果<b>只</b>落进
     * {@link #rail} + {@link #railOtherPos} 这两个字段，轨道编辑区的每个控件都只读这两个字段，
     * 因此不存在「某一处走另一条解析路径」而导致彼此灰显不一致的可能
     * （MTR4 上曾经出现的「下面三个能点、最上面一个灰着」就是两处解析不同源造成的）。
     */
    @Nullable
    private static NodeConnector.ConnectedRail resolveRail(BlockEntityMultiDirectionNode node) {
        if (node == null) {
            return null;
        }
        final List<NodeConnector.ConnectedRail> candidates;
        try {
            candidates = NodeConnector.connectedRails(node.getBlockPos());
        } catch (Exception e) {
            Main.debug("[MultiDirectionNode] connected rail lookup failed: {}", e.getMessage());
            return null;
        }
        if (candidates.isEmpty()) {
            return null;
        }
        final NodeConnector.ConnectedRail looked = pickLookedAtRail(candidates);
        return looked != null ? looked : candidates.get(0);
    }

    /**
     * 在候选轨道里挑「玩家正在看着的那条」：视线夹角最小者，且夹角必须在
     * {@link #LOOK_ANGLE_TANGENT} 之内、距离在 {@link #LOOK_MAX_DISTANCE} 之内。
     * <p>
     * 用「夹角的正切」而不是「点到直线的垂距」比较，是因为前者与距离无关：远处的轨道带在屏幕上
     * 只占几个像素，用绝对垂距会把近处擦边的轨道误判成目标。没有玩家（专用服务端 / 未进入世界）时
     * 直接返回 {@code null}，由调用方回退到确定性第一条。
     */
    @Nullable
    private static NodeConnector.ConnectedRail pickLookedAtRail(List<NodeConnector.ConnectedRail> candidates) {
        final Player player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        final Vec3 eye = player.getEyePosition(1.0F);
        final Vec3 look = player.getLookAngle();
        if (look.lengthSqr() < 1.0E-8D) {
            return null;
        }
        NodeConnector.ConnectedRail best = null;
        double bestTangent = LOOK_ANGLE_TANGENT;
        for (final NodeConnector.ConnectedRail candidate : candidates) {
            final Rail rail = candidate.rail();
            final double length = rail.getLength();
            if (!(length > 0.0D) || !Double.isFinite(length)) {
                continue;
            }
            for (int i = 0; i <= LOOK_SAMPLES; i++) {
                final double parameter = length * i / LOOK_SAMPLES;
                final Vec3 sample = rail.getPosition(parameter);
                final Vec3 delta = sample.subtract(eye);
                final double along = delta.dot(look);
                // 采样点必须在<b>前方</b>（along > 0）且在拾取距离内
                if (along <= 0.05D || along > LOOK_MAX_DISTANCE) {
                    continue;
                }
                final double tangent = delta.subtract(look.scale(along)).length() / along;
                if (tangent < bestTangent) {
                    bestTangent = tangent;
                    best = candidate;
                }
            }
        }
        return best;
    }

    /**
     * 重建界面时把轨道快照刷新到客户端数据里的<b>当前实例</b>。
     * <p>
     * 服务端每次应用编辑（逐轨道超高 / 节点姿态）都会重播轨道，客户端数据里的 {@code Rail}
     * 会被换成<b>新实例</b>；本界面若继续持有旧实例，逐轨道超高界面就会把过期姿态当作起点
     * （例如刚编辑掉的逐轨道超高会被当成「未编辑」）。因此这里按<b>已经确定的那一对端点</b>
     * 重新查一次：
     * <ul>
     *   <li>查到了同一个实例 → 直接返回（客户端数据没变）；</li>
     *   <li>查到了新实例 → 换进来；</li>
     *   <li>查不到（轨道被删 / 客户端数据尚未同步）→ 解析结果整体失效（{@code rail = null}），
     *       四个按钮一起灰显 —— 而不是抱着一个已经不存在的实例继续显示它的超高位；</li>
     *   <li>本来就没解析到轨道（{@link #railOtherPos} = {@code null}）→ 这里<b>不</b>重新做视线拾取，
     *       否则玩家一移动视线、轨道编辑区就会自己亮起来，破坏「四个按钮一起灰显」的可预期性。
     *       要重新拾取请点「重新读取轨道」。</li>
     * </ul>
     */
    private void syncRailSnapshot() {
        if (railOtherPos == null) {
            return;
        }
        final Rail resolved = findRailByOtherEnd(railOtherPos);
        if (resolved == null) {
            rail = null;
            railOtherPos = null;
            Main.debug("[MultiDirectionNode] rail snapshot dropped at {}", node.getBlockPos());
            return;
        }
        if (resolved == rail) {
            return;
        }
        rail = resolved;
        Main.debug("[MultiDirectionNode] rail snapshot refreshed at {}", node.getBlockPos());
    }

    /** 按「另一端」重新查客户端数据里的轨道（本节点恒为 position1）。 */
    @Nullable
    private Rail findRailByOtherEnd(BlockPos otherPos) {
        for (final NodeConnector.ConnectedRail candidate : NodeConnector.connectedRails(node.getBlockPos())) {
            if (candidate.position2().equals(otherPos)) {
                return candidate.rail();
            }
        }
        return null;
    }

    /**
     * 重新做一次完整的轨道解析（「重新读取轨道」按钮）。
     * <p>
     * 与构造期走<b>同一个</b> {@link #resolveRail(BlockEntityMultiDirectionNode)}，
     * 因此不会换来源；用途是：节点刚连上轨道、客户端数据后到、或服务端重播换了实例之后，
     * 让面板重新认一次目标。解析不到时四件套一起灰显（与构造期一致）。
     */
    private void reResolveRail() {
        final NodeConnector.ConnectedRail resolved = resolveRail(node);
        this.rail = resolved == null ? null : resolved.rail();
        this.railOtherPos = resolved == null ? null : resolved.position2();
        Main.debug("[MultiDirectionNode] rail re-resolved at {} -> {}", node.getBlockPos(), railOtherPos);
    }

    // ==================== 固定控件（不随滚动） ====================

    @Override
    protected void buildFixedWidgets() {
        final int left = getLeftColumnX();
        // 顶部切换与底部保存都横跨两列（宽度 = 两列宽 + 列间距）
        final int width = getColumnsWidth();

        // 整块面板的总开关：两列的所有数值行一起切到另一种输入方式（互斥，机制与标签都在基类里）
        addInputModeToggle(left, 34, width, 20);

        closeButton = addFixedWidget(ComponentHelper.button(left, this.height - 30, width, 20,
                ComponentHelper.translatable("ui.fangsu.block.close_and_save"), btn -> {
                    save();
                    onClose();
                }));
    }

    // ==================== 可滚动内容 ====================

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        // 重建会销毁全部数值行的控件引用（含可能正在编辑的那一个）：把编辑态收掉、再丢掉上一批行的引用。
        // 这两件事都在基类的 resetNumericRows() 里（见 NumericInputConfigScreen）。
        resetNumericRows();
        // 客户端数据里的轨道可能已被服务端替换成新实例（例如刚从逐轨道超高界面返回），
        // 先同步一次快照，避免逐轨道超高界面拿到过期姿态
        syncRailSnapshot();
        // 构造期没解析到轨道时，在每次重建时再试一次：客户端 MTR 数据可能只是来得晚。
        // 注意这里只处理「从来没有轨道」的情形；已经有轨道时不重做视线拾取（见 syncRailSnapshot）。
        if (railOtherPos == null) {
            reResolveRail();
        }
        // 重建界面时同步刷新几何预检结果（输入模式切换、开关翻转、打开界面都会走到这里）
        refreshPoseValidity();

        final int leftX = getLeftColumnX();
        final int rightX = getRightColumnX();
        final int columnWidth = getColumnWidth();
        final int fullWidth = getColumnsWidth();
        final int yTop = layout.y;

        // ---- 两列标题：左列「节点平移」/ 右列「节点旋转」 ----
        addEntry(createTextLabel(leftX + columnWidth / 2, yTop, ComponentHelper.translatable("ui.fangsu.multi_direction_node.translate"), TextLabel.Align.CENTER, 0xFFFFFF, false), yTop);
        addEntry(createTextLabel(rightX + columnWidth / 2, yTop, ComponentHelper.translatable("ui.fangsu.multi_direction_node.rotate"), TextLabel.Align.CENTER, 0xFFFFFF, false), yTop);

        int yLeft = yTop + 12;
        int yRight = yTop + 12;

        // ---- 左列：节点平移（X / Y / Z） ----
        yLeft = addAxisRow(leftX, yLeft, columnWidth, ComponentHelper.translatable("ui.fangsu.multi_direction_node.offset_x"),
                (float) offsetX, TRANSLATE_SLIDER_MIN, TRANSLATE_SLIDER_MAX, TRANSLATE_STEP,
                v -> setOffset(0, v), () -> (float) offsetX, this::applyOffsets);
        yLeft = addAxisRow(leftX, yLeft, columnWidth, ComponentHelper.translatable("ui.fangsu.multi_direction_node.offset_y"),
                (float) offsetY, TRANSLATE_SLIDER_MIN, TRANSLATE_SLIDER_MAX, TRANSLATE_STEP,
                v -> setOffset(1, v), () -> (float) offsetY, this::applyOffsets);
        yLeft = addAxisRow(leftX, yLeft, columnWidth, ComponentHelper.translatable("ui.fangsu.multi_direction_node.offset_z"),
                (float) offsetZ, TRANSLATE_SLIDER_MIN, TRANSLATE_SLIDER_MAX, TRANSLATE_STEP,
                v -> setOffset(2, v), () -> (float) offsetZ, this::applyOffsets);

        // ---- 右列：节点旋转（俯仰 + 方向 + 翻滚）+ 旋转绑定开关 ----
        // 标签按概念命名（俯仰角 / 翻滚角）而不是轴字母：这两个量是铁路语义（纵坡 / 外轨超高），
        // 不是「绕方块 X 轴转」，用轴字母会误导用户与后续维护者。
        // 翻滚角一行在本面板保留：它是相连轨道的<b>默认值</b>，并经 NodeConnector.readRailPose
        // 写进轨道的 roll1Degrees/roll2Degrees（所以走 this::applyAngles 重建轨道）；
        // 已按轨道授权过逐轨道超高的轨道，几何端优先用授权三点剖面。
        yRight = addAxisRow(rightX, yRight, columnWidth, ComponentHelper.translatable("ui.fangsu.multi_direction_node.pitch"),
                (float) pitchDeg, PITCH_SLIDER_MIN, PITCH_SLIDER_MAX, PITCH_STEP,
                v -> setPitch(v), () -> (float) pitchDeg, this::applyAngles);
        yRight = addAxisRow(rightX, yRight, columnWidth, ComponentHelper.translatable("ui.fangsu.multi_direction_node.rotY"),
                (float) direction, DIRECTION_SLIDER_MIN, DIRECTION_SLIDER_MAX, DIRECTION_STEP,
                v -> setDirection(v), () -> (float) direction, this::applyDirection);
        yRight = addAxisRow(rightX, yRight, columnWidth, ComponentHelper.translatable("ui.fangsu.multi_direction_node.roll"),
                (float) rollDeg, ROLL_SLIDER_MIN, ROLL_SLIDER_MAX, ROLL_STEP,
                v -> setRoll(v), () -> (float) rollDeg, this::applyAngles);
        yRight = addRotationBindRow(rightX, yRight, columnWidth);

        // ---- 轨道编辑：两列下方，占满两列宽度 ----
        int y = Math.max(yLeft, yRight) + 4;
        addEntry(createTextLabel(leftX + fullWidth / 2, y, ComponentHelper.translatable("ui.fangsu.multi_direction_node.railSection"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        y += 12;

        final boolean hasRail = rail != null && railOtherPos != null;
        if (!hasRail) {
            // 「没有轨道」时唯一的提示行：四个按钮会一起灰显，这一行说明为什么
            addEntry(createTextLabel(leftX, y, ComponentHelper.translatable("ui.fangsu.multi_direction_node.noRail"), TextLabel.Align.LEFT, 0xFFAA55, false), y);
            y += 12;
        }

        // ---- 轨道编辑区：四个按钮，全部只读同一份解析结果（rail + railOtherPos） ----
        //
        // 【为什么是这四个】MTR4 的那四个是「逐轨道超高 / 轨道形状 / 轨道样式 / 反转样式」。
        // MTR3 的 mtr.data.Rail <b>没有</b> Rail.Shape、没有样式列表、没有分端限速
        // （见 NodeRefreshRailPayload 的类注释与旧 NodeAngleScreen 的记录），
        // 形状 / 样式 / 反转这三件事在 MTR3 上没有可写入的数据模型，
        // 照抄只会得到三个永远点不动的按钮。因此换成 MTR3 上确实存在、且都属于「这条轨道」的三个操作，
        // 加上逐轨道超高编辑，凑满四个：
        //   1) 逐轨道超高编辑…  —— 打开 RailTiltConfigScreen（C2S 通道）
        //   2) 清除逐轨道超高    —— 直接下发清除（回到节点派生滚转）
        //   3) 重建本节点轨道几何 —— NODE_REFRESH_RAIL：按服务端权威姿态原地重建相连轨道
        //   4) 重新读取轨道      —— 重新做一次轨道解析（客户端数据后到 / 服务端换了实例时用）
        // <b>四个按钮的可点条件完全同源</b>：都只看 hasRail，因此要么一起可点、要么一起灰显。
        final boolean enabled = hasRail;

        final Button buttonRailTilt = addButton(leftX, y, fullWidth, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.editRailTilt"), b -> openRailTiltEditor());
        buttonRailTilt.active = enabled;
        addEntry(buttonRailTilt, y);
        y += 24;

        final Button buttonClearTilt = addButton(leftX, y, fullWidth, 20,
                ComponentHelper.translatable("ui.fangsu.rail_tilt.clear"), b -> clearRailTilt());
        buttonClearTilt.active = enabled;
        addEntry(buttonClearTilt, y);
        y += 24;

        final Button buttonRebuild = addButton(leftX, y, fullWidth, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.rebuildRail"), b -> rebuildRails());
        buttonRebuild.active = enabled;
        addEntry(buttonRebuild, y);
        y += 24;

        final Button buttonReload = addButton(leftX, y, fullWidth, 20,
                ComponentHelper.translatable("ui.fangsu.multi_direction_node.reloadRail"), b -> {
                    reResolveRail();
                    requestRebuild();
                });
        buttonReload.active = enabled;
        addEntry(buttonReload, y);
        y += 24;

        // 说明「节点翻滚角 = 默认值」这层关系。刻意拆成两行短句：内容区按面板宽度裁剪
        // （getContentRight），极窄面板下整行宽度只够十来个汉字，长句会被静默截断。
        addEntry(createTextLabel(leftX, y, ComponentHelper.translatable("ui.fangsu.multi_direction_node.rollDefaultHint"),
                TextLabel.Align.LEFT, 0x888888, false), y);
        y += 10;
        addEntry(createTextLabel(leftX, y, ComponentHelper.translatable("ui.fangsu.multi_direction_node.rollAuthoredHint"),
                TextLabel.Align.LEFT, 0x888888, false), y);
        y += 12;

        // 外轨超高开关与半轨距两行<b>不</b>在本面板恢复：半轨距在「逐轨道超高」界面里按轨道编辑。
        // 节点级的开关 / 半轨距仍在 BE 里保留并原样同步，供「没有逐轨道超高的轨道」回退使用；
        // 这里刻意不写 BE 的这几个字段：本面板打开时读到的就是当前值，写回是恒等变换，
        // 真正落盘由 applyAngles() / save() 负责。
    }

    // ==================== 轨道编辑区的四个动作 ====================

    /**
     * 打开「逐轨道超高」编辑界面，编辑的是<b>本面板那条轨道</b>（{@link #rail}）——
     * 与另外三个按钮完全同一个来源，四者的可点状态因此永远一致。
     * <p>
     * 点击时先调用一次 {@link #syncRailSnapshot()}：它只按已经确定的那一对端点重查
     * （不会换来源），把服务端重播过的新实例换进来，避免抱着过期实例打开界面。
     * 解析不到（此时按钮本身已灰显）时不开界面，只记日志。
     * <p>
     * 打开的是同一个客户端上的新界面（{@code setScreen}），并把本界面作为父界面传下去，
     * 这样新界面的「返回 / 应用」都能回到本面板。
     */
    private void openRailTiltEditor() {
        if (minecraft == null) {
            return;
        }
        syncRailSnapshot();
        if (rail == null || railOtherPos == null) {
            Main.debug("[MultiDirectionNode] rail tilt editor skipped: no rail at {}", node == null ? null : node.getBlockPos());
            return;
        }
        minecraft.setScreen(new RailTiltConfigScreen(rail, node.getBlockPos(), railOtherPos, this));
    }

    /**
     * 清除本轨道的逐轨道超高（回到节点派生滚转），直接下发 C2S，不打开界面。
     * <p>
     * 与逐轨道超高界面里的「清除」走<b>同一个</b> {@link RailTiltPackets#sendClearRailTiltC2S}，
     * 半轨距传 {@code NaN} = 沿用服务端当前值（只清三个控制点）。
     * 这条轨道从未被授权过时，服务端会把同样的默认剖面写回去（等价于空操作，不会有副作用）。
     */
    private void clearRailTilt() {
        if (rail == null || railOtherPos == null || node == null) {
            return;
        }
        RailTiltPackets.sendClearRailTiltC2S(node.getBlockPos(), railOtherPos, Double.NaN);
        Main.debug("[MultiDirectionNode] clear per-rail tilt request: {} -> {}", node.getBlockPos(), railOtherPos);
    }

    /**
     * 重建本节点的相连轨道几何：走 {@code NODE_REFRESH_RAIL}，服务端按自己权威的节点姿态原地重建。
     * <p>
     * 这是「客户端与服务端几何不同步」时的手动修复入口（正常路径下每次改姿态都会自动触发），
     * 与连接器建轨 / 世界重载走的是同一条重建路径。
     */
    private void rebuildRails() {
        if (node == null) {
            return;
        }
        tryRefreshRails();
        Main.debug("[MultiDirectionNode] manual rail rebuild requested at {}", node.getBlockPos());
    }

    // ==================== 旋转绑定开关 ====================

    /**
     * 旋转绑定开关（右列）：显示「旋转绑定：是 / 否」。
     * <ul>
     *   <li>是 → 编辑方向会调用 {@code setDirectionAndBind}，方向被绑定（模型固定、相连轨道按该方向重建）；</li>
     *   <li>否 → 只调用 {@code setDirectionUnbound} 写下方向值，{@code directionBonded} 保持 false（模型继续旋转）；</li>
     *   <li>节点<b>已连接</b>时强制为「是」并锁定：服务端重建相连轨道依赖节点绑定方向，此时解绑没有意义。</li>
     * </ul>
     */
    private int addRotationBindRow(int areaLeft, int y, int rowWidth) {
        final Component label = ComponentHelper.translatable(
                "ui.fangsu.multi_direction_node.rotationBind",
                ComponentHelper.translatable(rotationBonded
                        ? "ui.fangsu.multi_direction_node.yes"
                        : "ui.fangsu.multi_direction_node.no"));
        final Button button = addButton(areaLeft, y, rowWidth, 20, label, b -> toggleRotationBind());
        // 已连接 → 锁定为「是」且不可点击（工具提示由标签本身表达）
        button.active = node == null || !node.isConnected();
        addEntry(button, y);
        return y + 24;
    }

    // ==================== 节点写入（平移 / 方向 / 角度） ====================

    /**
     * 平移：钳制到硬边界 {@link BlockEntityMultiDirectionNode#MAX_OFFSET}（±2 格）。
     * <p>
     * 滑块区间仍是 ±1 格，所以键入 1.5 会被<b>原样保留</b>，键入 5 才钳到 2。
     * 钳制只此一处，且与 BE 的 {@code clampOffset} 是同一套数字（客户端镜像）。
     */
    private void setOffset(int axis, float value) {
        final double clamped = BlockEntityMultiDirectionNode.clampOffset(value);
        switch (axis) {
            case 0 -> offsetX = clamped;
            case 1 -> offsetY = clamped;
            default -> offsetZ = clamped;
        }
    }

    /**
     * 方向：<b>回绕</b>而不是钳制。
     * <p>
     * 滑块仍只用 [0,180] 表示，而滑块拖到端点时若取模会立刻跳回另一端、与滑块自身位置不一致，
     * 所以滑块给出的值本来就在区间内，回绕对它是恒等变换（既有手感不变）；
     * 而输入框键入 270 / -90 / 450 这类值时，钳制会把它们错压成 180 / 0 / 180（完全是别的方向），
     * 只有回绕才保真。回绕用的是 BE 存储侧同一个方法
     * {@link RailAngleExtra#normalizeNodeDegrees(double)}，因此客户端显示值与服务端存储值一致。
     */
    private void setDirection(float value) {
        direction = RailAngleExtra.normalizeNodeDegrees(value);
    }

    /** 俯仰角：钳制到硬边界 {@link BlockEntityMultiDirectionNode#MAX_PITCH_DEG}（±60°）。 */
    private void setPitch(float value) {
        pitchDeg = BlockEntityMultiDirectionNode.clampPitch(value);
    }

    /**
     * 翻滚角：钳制到硬边界 {@link BlockEntityMultiDirectionNode#MAX_ROLL_DEG}（±45°，与逐轨道超高的
     * {@code RailPoseExtra#MAX_RAIL_TILT_DEGREES} 一致）。
     * <p>
     * 它是相连轨道的<b>默认值</b>：改动后由 {@link #applyAngles()} 触发一次轨道重建，
     * 未按轨道授权过逐轨道超高的轨道才会拿到新值。
     */
    private void setRoll(float value) {
        rollDeg = BlockEntityMultiDirectionNode.clampRoll(value);
    }

    /**
     * 俯仰 / 翻滚实时写入：先把姿态落库并发一次 BE_SYNC，再（预检通过后）请求重建轨道。
     * <p>
     * 与平移 / 方向一致：即时保存（不点「保存并退出」也生效）。
     * 外轨超高开关 / 半轨距<b>不</b>在这里写：它们在本面板没有编辑入口，值是打开界面时从 BE 读到的，
     * 写回是恒等变换；{@code writeC2S} 本身是全量载荷，因此 BE_SYNC 里的这两个字段仍然是当前值。
     */
    private void applyAngles() {
        if (node == null) {
            return;
        }
        node.setAnglesAndSync(pitchDeg, rollDeg);
        tryRefreshRails();
    }

    /**
     * 平移实时写入。顺序：写平移（本地）→ 写方向并发 BE_SYNC → 预检通过才请求重建轨道。
     * <p>
     * {@code writeC2S} 的载荷包含方向 + 绑定标志 + 三个平移分量，所以这里只需要一次 BE_SYNC
     * （{@code setNodeOffset} 只写本地数据，不发包）。
     * <p>
     * <b>平移绝不隐式绑定旋转</b>：开关为「否」时用 {@code setDirectionUnbound} 只写值；
     * 只有开关为「是」才用 {@code setDirectionAndBind}。这就是「拖一下节点就把方向绑死」的修复点。
     * 与 {@code ObjBlockConfigScreen} 一样是即时保存（不点按钮也生效），因此拖动滑块会逐步触发重建；
     * 万向节点相连轨道通常只有 1~2 条，该开销可接受。
     */
    private void applyOffsets() {
        if (node == null) {
            return;
        }
        node.setNodeOffset(offsetX, offsetY, offsetZ);
        writeDirection();
        tryRefreshRails();
    }

    /**
     * 方向实时写入，顺序与旧 {@code NodeAngleScreen#saveAndClose} 一致：
     * 先写 BE 并发 BE_SYNC，再请求重建相连轨道（服务端从刷新包里读方向与姿态）。
     */
    private void applyDirection() {
        if (node == null) {
            return;
        }
        writeDirection();
        tryRefreshRails();
    }

    /**
     * 按「旋转绑定」开关写方向并发一次 BE_SYNC：是 → 绑定；否 → 只写值、保持未绑定。
     * <p>
     * {@code setDirectionAndBind} 只做本地 setChanged（不发包），所以「是」分支补一次 BE_SYNC；
     * {@code setDirectionUnbound} 内部已含 setChanged + 发包（见 BE 的 markChangedAndSync）。
     */
    private void writeDirection() {
        if (node == null) {
            return;
        }
        if (rotationBonded) {
            node.setDirectionAndBind(direction);
            node.sendUpdateC2S();
        } else {
            // 未绑定：只写方向值，directionBonded 保持不变（C2S 载荷布局不变）
            node.setDirectionUnbound(direction);
        }
    }

    /** 翻转「旋转绑定」开关：切到「否」立即解绑（保留方向值）；已连接的节点锁定为「是」。 */
    private void toggleRotationBind() {
        if (node == null) {
            return;
        }
        if (node.isConnected()) {
            // 已连接 → 锁定为「是」，点击不生效
            rotationBonded = true;
            requestRebuild();
            return;
        }
        rotationBonded = !rotationBonded;
        if (rotationBonded) {
            node.setDirectionAndBind(direction);
            node.sendUpdateC2S();
        } else {
            // 解绑但保留当前方向值：只动方向绑定标志（C2S 载荷布局不变）
            node.setRotationBonded(false);
        }
        requestRebuild();
    }

    /**
     * 几何预检 + 条件重建：姿态合法才发送 {@code NODE_REFRESH_RAIL}。
     * <p>
     * 非法姿态下服务端建不出轨道，旧实现会把旧轨道先删掉再失败（轨道消失）；现在服务端本身也是
     * 「先校验后删除」，客户端这一层再挡一次，连删除尝试都不会发生。
     * 预检每帧重算很贵，所以只在姿态变化 / 界面重建时调用 {@link #refreshPoseValidity()}。
     */
    private void tryRefreshRails() {
        if (node == null) {
            return;
        }
        refreshPoseValidity();
        if (poseInvalid) {
            Main.debug("[MultiDirectionNode] refresh skipped: edited pose cannot build a valid rail at {}", node.getBlockPos());
            return;
        }
        node.refreshConnectedRailsIfNeeded();
    }

    /**
     * 关闭前兜底：把方向、平移与轨道姿态整体再写一次，避免切换输入模式等重建过程丢失最后一次输入。
     * <p>
     * 顺序仍是 BE_SYNC → NODE_REFRESH_RAIL；姿态非法时同样跳过重建（旧轨道保持不动）。
     * 外轨超高开关 / 半轨距在本面板没有编辑入口，这里写回的是打开界面时从 BE 读到的同一个值
     * （透传，保证 BE_SYNC 全量载荷完整），不会把用户没编辑过的东西改掉。
     */
    private void save() {
        if (node == null) {
            return;
        }
        // 输入框里可能还有未提交的文本（用户没按回车就点了保存）：先统一提交，再整体落盘
        commitAllNumericFields();
        node.setNodeOffset(offsetX, offsetY, offsetZ);
        node.setNodeAngles(pitchDeg, rollDeg);
        node.setSuperelevation(superelevation);
        node.setRollOffsetM(halfGaugeM);
        writeDirection();
        tryRefreshRails();
        Main.debug("[MultiDirectionNode] node config saved at {}", node.getBlockPos());
    }

    // ==================== 几何预检（客户端、不发包） ====================

    /**
     * 重新计算「当前编辑的姿态（平移 + 方向）能否产出合法轨道」，结果供红字警告与重建门控使用。
     * <p>
     * 逐个相连端点调用
     * {@link NodeConnector#isGeometryValid(BlockPos, double, BlockPos, double, double)}
     * 的<b>锚点感知</b>版本（朝向比较用的是锚点连线方位角，与服务端重建用的完全同一条算术）；
     * 只要有一条端点能建成就认为有效。没有相连轨道、客户端 MTR 数据未同步、
     * 或节点本身为空时按「有效」处理——那些情况下本来也不会发出重建请求，不该误报红色。
     * <p>
     * 只做纯计算：构造临时 {@code Rail} 判长度，不发包、不改世界。
     */
    private void refreshPoseValidity() {
        poseInvalid = false;
        if (node == null) {
            return;
        }
        final Level level = node.getLevel();
        if (level == null) {
            return;
        }
        final BlockPos self = node.getBlockPos();
        final List<BlockPos> others;
        try {
            others = NodeConnector.findConnectedEndpoints(self);
        } catch (Exception e) {
            Main.debug("[MultiDirectionNode] pose validation skipped: {}", e.getMessage());
            return;
        }
        if (others.isEmpty()) {
            return;
        }
        for (final BlockPos other : others) {
            try {
                final double[] otherOffset = NodeConnector.readNodeOffset(level, other);
                final double anchorDx = (other.getX() + otherOffset[0]) - (self.getX() + offsetX);
                final double anchorDz = (other.getZ() + otherOffset[2]) - (self.getZ() + offsetZ);
                if (anchorDx == 0.0D && anchorDz == 0.0D) {
                    // 两个锚点水平重合：没有弦向，建不出轨道
                    continue;
                }
                final double anchorDegrees = Math.toDegrees(Math.atan2(anchorDz, anchorDx));
                if (NodeConnector.isGeometryValid(self, direction, other,
                        NodeConnector.getDirectionDegrees(level, other), anchorDegrees)) {
                    return;
                }
            } catch (Exception e) {
                // 单个端点计算异常（数据半同步）时按有效处理，避免误报
                Main.debug("[MultiDirectionNode] pose validation failed for {}: {}", other, e.getMessage());
                return;
            }
        }
        poseInvalid = true;
        Main.debug("[MultiDirectionNode] edited pose is invalid for every connected endpoint at {}", self);
    }

    // ==================== 面板布局（双列 + 1.5 倍宽） ====================

    @Override
    protected int getPanelLeft() {
        return GAP;
    }

    /**
     * 面板右边界。<b>总宽度 = 原单列宽度的 1.5 倍</b>。
     * <p>
     * “原单列宽度”取的是本模组既有配置界面的实际尺寸，即
     * {@code ObjBlockConfigScreen#getPanelRight()} 的 {@code width / 5 - GAP}（左边界同为 {@code GAP}），
     * 因此原宽 = {@code width / 5 - 2 * GAP}。乘 {@value #PANEL_WIDTH_FACTOR} 后从
     * {@link #getPanelLeft()} 起算 —— 用 1.5 倍而不是 2 倍，是为了两列各占约 0.72 倍原宽
     * （控件“稍微小一点”），同时不把屏幕挡得太满。
     * <p>
     * 极窄窗口（如 GUI 缩放下只有 320 像素宽）时 1.5 倍仍不足两列使用，此时保底
     * {@code 2 * MIN_COLUMN_WIDTH + COLUMN_GAP}，否则右列会被内容裁剪区切掉。
     */
    @Override
    protected int getPanelRight() {
        final int originalWidth = this.width / 5 - GAP * 2;
        final int scaled = Math.round(originalWidth * PANEL_WIDTH_FACTOR);
        final int minPanelWidth = MIN_COLUMN_WIDTH * 2 + COLUMN_GAP;
        return getPanelLeft() + Math.max(scaled, minPanelWidth);
    }

    @Override
    protected int getPanelTop() {
        return 30;
    }

    @Override
    protected int getPanelBottom() {
        return this.height - 30;
    }

    @Override
    protected int getContentTop() {
        return 58;
    }

    @Override
    protected int getContentBottom() {
        return this.height - 42;
    }

    @Override
    protected int getContentLeft() {
        return getLeftColumnX();
    }

    @Override
    protected int getContentRight() {
        return getPanelRight();
    }

    /** 面板总宽度（两列 + 列间距）。 */
    private int getPanelWidth() {
        return getPanelRight() - getPanelLeft();
    }

    /** 单列宽度：总宽减去列间距后对半分，控件都比原单列布局窄一些以适应两列。 */
    private int getColumnWidth() {
        return Math.max(1, (getPanelWidth() - COLUMN_GAP) / 2);
    }

    /** 左列（平移）起点。 */
    private int getLeftColumnX() {
        return getPanelLeft();
    }

    /** 右列（旋转）起点。 */
    private int getRightColumnX() {
        return getLeftColumnX() + getColumnWidth() + COLUMN_GAP;
    }

    /** 两列合起来的宽度：顶部切换、保存按钮与轨道编辑区都用它铺满。 */
    private int getColumnsWidth() {
        return getColumnWidth() * 2 + COLUMN_GAP;
    }

    @Override
    protected void renderPanelBackground(GraphicContext g) {
        // 背景延伸到面板右边界再留一个 GAP，与原单列实现（fill 到 width/4）保持同样的视觉边距
        g.fill(0, 0, getPanelRight() + GAP, this.height, 0xFF000000);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 渲染 ====================

    //#if MC_VERSION >= 12000
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GraphicContext g = GraphicContext.of(graphics);
        //#else
        //$$ @Override
        //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     GraphicContext g = GraphicContext.of(poseStack);
        //#endif
        // 标题画在面板顶部（BasicConfigScreen 不画标题，ObjBlockConfigScreen 也是自己画）
        final int titleX = (getPanelLeft() + getPanelRight()) / 2 - this.font.width(this.title.getString()) / 2;
        g.drawString(this.font, this.title, titleX, 2, 0xFFFFFF, false);

        super.render(g.asMinecraft(), mouseX, mouseY, partialTick);

        // 警告画在滚动裁剪区之外（否则会被 getContentRight() 截断），堆叠在保存按钮上方
        if (poseInvalid) {
            final Component warning = ComponentHelper.translatable("ui.fangsu.multi_direction_node.invalidGeometry");
            final int warningX = (getPanelLeft() + getPanelRight()) / 2 - this.font.width(warning.getString()) / 2;
            g.drawString(this.font, warning, warningX, this.height - 42, 0xFF5555, false);
        }
    }
}
