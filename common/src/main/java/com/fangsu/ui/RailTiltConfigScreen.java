package com.fangsu.ui;

import com.fangsu.Main;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mappings.rail.RailPoseExtra;
import com.fangsu.mtr.rail.RailPoseExtraHolder;
import com.fangsu.mtr.rail.RailTiltSupport;
import com.fangsu.network.RailTiltPackets;
import com.fangsu.utils.GraphicContext;
import mtr.data.Rail;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 逐轨道超高（外轨超高 / 逐轨道倾斜）编辑界面：<b>一条轨道</b>的三个控制点 + 半轨距。
 * <p>
 * 从 {@link MultiDirectionNodeConfigScreen} 的「逐轨道超高编辑…」按钮打开，编辑的是<b>那个面板
 * 当前持有的那条轨道</b>（与轨道编辑区其余按钮完全同一个来源，见该面板的 {@code rail} 字段与
 * {@code resolveRail}），因此那几个按钮的可点状态永远一致。
 * 整个界面<b>只在客户端存在</b>：它引用 {@code ClientData} 与 MC 的 {@link Screen}，
 * 不被任何服务端代码引用，因此专用服务端不会加载到它（{@code com.fangsu.ui} 里其余界面也是同一约定）。
 * <p>
 * <b>三个控制点</b>（{@code start / middle / end}，单位度，硬边界 ±45°）：
 * <ul>
 *   <li><b>起点</b> = 轨道表的 {@code position1}（= 构造器的 {@code posStart}），
 *       <b>终点</b> = {@code position2}。这与 {@code RailPoseExtra} 的三点剖面约定一致
 *       （{@code start} 属于 position1、{@code end} 属于 position2，见
 *       {@code RailPoseExtra#toRollProfile()}）。MTR3 的 {@code Rail} <b>不保存</b>自己的方块坐标，
 *       所以两个端点由打开本界面的调用方（节点面板，它从 {@code ClientData.RAILS} 的表键拿到）
 *       显式传入，而不是从 {@link Rail} 上读；</li>
 *   <li><b>中点</b> = <b>两段圆弧的接缝</b>（曲线轨）或中点（直线 / 退化轨）。它的位置由几何决定、
 *       不随三个角度变化，界面把它显示成「从起点算起的百分比 + 弧长」，让「中点不是几何中点」这件事
 *       可见（位置来自服务端存下的 {@code railTiltMiddleFraction}；未编辑过时用
 *       {@link RailTiltSupport#middleBreakpointFraction} 按当前几何估计）；</li>
 *   <li><b>终点</b> = {@code position2}。</li>
 * </ul>
 * 正值 = 沿「起点 → 终点」方向前进时右手侧抬高（与节点级翻滚角同一个约定）。
 * 节点级 roll 只对「没有逐轨道超高的轨道」生效：某条轨道一旦编辑过逐轨道超高，
 * 几何端就<b>完全</b>用它自己的三点剖面（见 {@code RailGeometrySource#rollProfileFor}）。
 * <p>
 * <b>本界面不写任何本地数据、也不每敲一下就发包</b>：三个角度与半轨距先是界面里的待提交值，
 * 只有点「应用并返回」才通过 {@link RailTiltPackets#sendRailTiltC2S} 把一次编辑请求发给服务端。
 * 服务端校验（权限 / 距离 / 有限性 / 范围）通过后写入轨道姿态并<b>广播给所有玩家</b>，
 * 客户端随之收到 MTR 自己的轨道更新、由 {@code RailPoseExtraMixin} 读回姿态、
 * {@code RailGeometryMixin} 按新姿态重建几何 —— 也就是说<b>生效</b>发生在服务端广播回来之后，
 * 本界面不做乐观更新（本地轨道对象归 MTR 的客户端数据所有，不改它）。
 * 因此「下发成功但服务端拒绝」（例如没有 OP 权限）在本界面上<b>看不到报错</b>，
 * 表现为轨道没有任何变化、服务端日志里有一行 {@code [RailTilt] 忽略轨道超高编辑请求…}。
 * <p>
 * <b>输入方式</b>：三个控制点与半轨距四行全部走基类 {@link NumericInputConfigScreen} 的
 * 「滑块 ⇄ 输入框」总开关（顶部按钮一刀切、两种方式互斥），与万向节点面板<b>同一套实现</b>，
 * 不存在第二种输入样式。滑块区间就是硬边界（倾斜 ±45°、半轨距 [0.25, 2.0]），
 * 所以这里的滑块和输入框能表达的取值集合完全一致。
 */
public class RailTiltConfigScreen extends NumericInputConfigScreen {

    /** 面板左边距（与万向节点面板一致）。 */
    private static final int GAP = 4;
    /** 面板宽度放大系数（与万向节点面板一致，视觉上像同一个系列的界面）。 */
    private static final float PANEL_WIDTH_FACTOR = 1.5f;
    /** 极窄窗口下的面板宽度保底：再窄标签与数值框就放不下了。 */
    private static final int MIN_PANEL_WIDTH = 150;

    /**
     * 三个控制点的硬边界（度）：<b>直接引用服务端校验用的那一个常量</b>，避免客户端与服务端各写一份。
     */
    private static final double MAX_TILT_DEGREES = RailTiltPackets.MAX_TILT_DEGREES;
    private static final float TILT_SLIDER_MIN = (float) -MAX_TILT_DEGREES;
    private static final float TILT_SLIDER_MAX = (float) MAX_TILT_DEGREES;
    /** 倾斜步进 0.5°：与节点面板的俯仰 / 翻滚一致；整数当然也能键入。 */
    private static final float TILT_STEP = 0.5f;

    /** 半轨距区间与步进：同样直接引用 {@link RailTiltPackets} 的服务端校验边界。 */
    private static final float HALF_GAUGE_SLIDER_MIN = (float) RailTiltPackets.MIN_HALF_GAUGE;
    private static final float HALF_GAUGE_SLIDER_MAX = (float) RailTiltPackets.MAX_HALF_GAUGE;
    private static final float HALF_GAUGE_STEP = 0.005f;

    /**
     * 编辑目标：MTR3 的 {@link Rail} 不保存端点，所以端点由调用方显式给出。
     * <p>
     * 顺序就是轨道表的键顺序（{@code ClientData.RAILS.get(position1).get(position2)}），
     * 也就是 {@code Rail} 构造器的 {@code posStart} → {@code posEnd}。
     * <b>不要</b>改成「两端里坐标较小的那一端」：三点剖面的 start/end 是绑在
     * position1 / position2 上的，用错一端会让「起点 / 终点」两行的数值指向相反的物理端。
     */
    private final BlockPos position1;
    private final BlockPos position2;
    /** 轨道短 id（仅用于显示，让用户确认编辑的是哪一条轨道）。 */
    private final String shortId;
    /**
     * 中点控制点的归一化位置（只读，界面展示用），方向约定 = <b>从 {@code position1} 到 {@code position2}</b>。
     * <p>
     * 已编辑过时直接取服务端存下来的 {@code railTiltMiddleFraction}；未编辑过时取客户端按当前几何
     * 算出的接缝位置（{@link RailTiltSupport#middleBreakpointFraction}——MTR3 上这个值是<b>精确</b>的，
     * 因为它直接读 {@code Rail} 自己的两段圆弧弧长，不是估计值）。
     */
    private final double middleFraction;
    /** 轨道全长（格）；几何异常时为 NaN，界面据此决定要不要显示弧长。 */
    private final double arcLength;
    /** 打开界面时该轨道是否已经有逐轨道超高（决定中点读数是「服务端已存的」还是「按几何算的」）。 */
    private final boolean tiltAuthored;
    /** 父界面（万向节点面板）；返回时回到它，而不是直接关掉所有界面。 */
    @Nullable
    private final Screen parent;
    /** 端点 / 轨道是否读取成功；不成功时禁用「应用 / 清除」并显示红字（正常路径不会走到这里）。 */
    private final boolean ready;

    // ---- 待提交值（只在点「应用并返回」时下发，界面自己不写本地、不发包） ----
    private double startDegrees;
    private double middleDegrees;
    private double endDegrees;
    private double halfGaugeM;

    /**
     * @param rail      要编辑的轨道（客户端数据里的实例）
     * @param position1 轨道 {@code position1}（= 表的外层键）
     * @param position2 轨道 {@code position2}（= 表的内层键）
     * @param parent    父界面（万向节点面板），可为 {@code null}
     */
    public RailTiltConfigScreen(@Nullable Rail rail, @Nullable BlockPos position1, @Nullable BlockPos position2,
                               @Nullable Screen parent) {
        super(ComponentHelper.translatable("ui.fangsu.rail_tilt.title"));
        this.parent = parent;
        this.ready = rail != null && position1 != null && position2 != null && !position1.equals(position2);
        this.position1 = position1 == null ? BlockPos.ZERO : position1;
        this.position2 = position2 == null ? BlockPos.ZERO : position2;
        this.shortId = shortId(this.position1, this.position2);

        final RailPoseExtra pose = RailPoseExtraHolder.peek(rail);
        this.tiltAuthored = pose.hasRailTilt();
        // 未编辑过时三个控制点按 0 显示（0 = 不倾斜，与「未编辑」在数值上一致，但语义由 tiltAuthored 区分）
        this.startDegrees = tiltAuthored ? pose.railTiltStartDegrees : 0.0D;
        this.middleDegrees = tiltAuthored ? pose.railTiltMiddleDegrees : 0.0D;
        this.endDegrees = tiltAuthored ? pose.railTiltEndDegrees : 0.0D;
        this.halfGaugeM = pose.halfGauge;
        // 已编辑过 → 用服务端当时算好并存下来的接缝位置（权威值）；
        // 未编辑过 → 按客户端这条轨道自己的几何算（MTR3 上可精确算出，见字段注释）。
        this.middleFraction = tiltAuthored
                ? pose.railTiltMiddleFraction
                : RailTiltSupport.middleBreakpointFraction(rail);
        this.arcLength = rail == null ? Double.NaN : rail.getLength();
    }

    // ==================== 固定控件 ====================

    @Override
    protected void buildFixedWidgets() {
        final int left = getPanelLeft();
        final int width = getPanelWidth();

        // 与万向节点面板同一个总开关：整块面板在「只有滑块」与「只有输入框」之间互斥切换
        addInputModeToggle(left, 34, width, 20);

        // 底部两个固定按钮横跨面板：主操作「应用并返回」 + 「返回（不应用）」
        final int buttonWidth = Math.max(40, (width - GAP) / 2);
        closeButton = addFixedWidget(ComponentHelper.button(left, this.height - 30, buttonWidth, 20,
                ComponentHelper.translatable("ui.fangsu.rail_tilt.apply"), btn -> applyAndClose()));
        closeButton.active = ready;
        addFixedWidget(ComponentHelper.button(left + buttonWidth + GAP, this.height - 30, buttonWidth, 20,
                ComponentHelper.translatable("ui.fangsu.rail_tilt.back"), btn -> onClose()));
    }

    // ==================== 可滚动内容 ====================

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        resetNumericRows();

        final int leftX = getPanelLeft();
        final int fullWidth = getPanelWidth();
        int y = layout.y;

        // ---- 编辑对象：轨道短 id + 两端坐标，让用户确认改的是哪一条轨道、哪一端是「起点」 ----
        addEntry(createTextLabel(leftX, y, ComponentHelper.translatable("ui.fangsu.rail_tilt.railId", shortId),
                TextLabel.Align.LEFT, 0xFFFFFF, false), y);
        y += 12;
        addEntry(createTextLabel(leftX, y, ComponentHelper.translatable("ui.fangsu.rail_tilt.endpoints",
                formatPos(position1), formatPos(position2)), TextLabel.Align.LEFT, 0xAAAAAA, false), y);
        y += 12;

        // ---- 中点控制点的位置读数（只读，说明「中点」是两段圆弧的接缝而不是几何中点） ----
        addEntry(createTextLabel(leftX, y, buildMiddlePositionLabel(), TextLabel.Align.LEFT, 0xAAD4FF, false), y);
        y += 12;
        addEntry(createTextLabel(leftX, y, ComponentHelper.translatable("ui.fangsu.rail_tilt.middleHint"),
                TextLabel.Align.LEFT, 0x888888, false), y);
        y += 12;
        if (!tiltAuthored) {
            addEntry(createTextLabel(leftX, y,
                    ComponentHelper.translatable("ui.fangsu.rail_tilt.middlePositionEstimated"),
                    TextLabel.Align.LEFT, 0x888888, false), y);
            y += 12;
        }
        if (!ready) {
            addEntry(createTextLabel(leftX, y,
                    ComponentHelper.translatable("ui.fangsu.rail_tilt.notReady"),
                    TextLabel.Align.LEFT, 0xFF5555, false), y);
            y += 12;
        }
        y += 4;

        // ---- 三个控制点 + 半轨距（四行，都走基类的 addAxisRow：滑块模式只有滑块，输入模式只有输入框） ----
        y = addAxisRow(leftX, y, fullWidth, ComponentHelper.translatable("ui.fangsu.rail_tilt.start"),
                (float) startDegrees, TILT_SLIDER_MIN, TILT_SLIDER_MAX, TILT_STEP,
                v -> startDegrees = clampTilt(v), () -> (float) startDegrees, this::onPendingValueChanged);
        y = addAxisRow(leftX, y, fullWidth, ComponentHelper.translatable("ui.fangsu.rail_tilt.middle"),
                (float) middleDegrees, TILT_SLIDER_MIN, TILT_SLIDER_MAX, TILT_STEP,
                v -> middleDegrees = clampTilt(v), () -> (float) middleDegrees, this::onPendingValueChanged);
        y = addAxisRow(leftX, y, fullWidth, ComponentHelper.translatable("ui.fangsu.rail_tilt.end"),
                (float) endDegrees, TILT_SLIDER_MIN, TILT_SLIDER_MAX, TILT_STEP,
                v -> endDegrees = clampTilt(v), () -> (float) endDegrees, this::onPendingValueChanged);
        y = addAxisRow(leftX, y, fullWidth, ComponentHelper.translatable("ui.fangsu.rail_tilt.halfGauge"),
                (float) halfGaugeM, HALF_GAUGE_SLIDER_MIN, HALF_GAUGE_SLIDER_MAX, HALF_GAUGE_STEP,
                v -> halfGaugeM = clampHalfGauge(v), () -> (float) halfGaugeM, this::onPendingValueChanged);
        y += 4;

        // ---- 符号约定说明（正 = 前进方向右手侧抬高，与节点面板的翻滚角一致） ----
        addEntry(createTextLabel(leftX, y, ComponentHelper.translatable("ui.fangsu.rail_tilt.signHint"),
                TextLabel.Align.LEFT, 0x888888, false), y);
        y += 12;

        // ---- 清除（回到节点派生滚转）：半轨距保持不变（下发 Double.NaN = 沿用服务端当前值） ----
        addEntry(createTextLabel(leftX, y, ComponentHelper.translatable("ui.fangsu.rail_tilt.clearHint"),
                TextLabel.Align.LEFT, 0x888888, false), y);
        y += 12;
        final Button buttonClear = addButton(leftX, y, fullWidth, 20,
                ComponentHelper.translatable("ui.fangsu.rail_tilt.clear"), b -> clearAndClose());
        // 没编辑过的轨道上「清除」是空操作（服务端也只会把它写回同样的默认剖面），因此灰显
        buttonClear.active = ready && tiltAuthored;
        addEntry(buttonClear, y);
    }

    /**
     * 中点控制点位置的读数：从起点算起的百分比 + 弧长（全长与「距起点」都是格）。
     * <p>
     * 位置本身不随三个角度变化（它由轨道几何决定），所以本行是构建时算好的静态文本；
     * 「live」体现在它读的是<b>这条轨道当前的真实几何</b>，而不是任何硬编码的 0.5。
     */
    private Component buildMiddlePositionLabel() {
        final String percent = String.format("%.1f%%", middleFraction * 100.0D);
        final String arc;
        if (Double.isFinite(arcLength) && arcLength > 0.0D) {
            arc = String.format("%.1f / %.1f M", middleFraction * arcLength, arcLength);
        } else {
            arc = "-";
        }
        return ComponentHelper.translatable("ui.fangsu.rail_tilt.middlePosition", percent, arc);
    }

    /** 控件上的待提交值变化：本界面不写本地轨道、也不发包，故这里不做任何事（见类注释的写入路径）。 */
    private void onPendingValueChanged() {
        // 刻意留空：只有「应用并返回」会下发，滑块拖动与键入都只改界面里的待提交值。
    }

    // ==================== 写入路径（只走 RailTiltPackets） ====================

    /**
     * 应用并返回：先把输入框里未回车的文本提交到界面字段，再把一次编辑请求下发给服务端。
     * <p>
     * 下发后<b>不</b>改本地轨道对象：服务端校验通过后会广播，客户端收到 MTR 自己的轨道更新时
     * 由 mixin 读回姿态、重建几何，本界面此时已经退回万向节点面板。
     */
    private void applyAndClose() {
        commitAllNumericFields();
        if (ready) {
            RailTiltPackets.sendRailTiltC2S(position1, position2,
                    startDegrees, middleDegrees, endDegrees, halfGaugeM);
            Main.debug("[RailTilt] 已请求逐轨道超高：{} -> {} 起点={} 中点={} 终点={} 半轨距={}",
                    position1, position2, startDegrees, middleDegrees, endDegrees, halfGaugeM);
        }
        onClose();
    }

    /**
     * 清除逐轨道超高（回到节点派生滚转）并返回。
     * <p>
     * 半轨距传 {@code Double.NaN}：按 {@link RailTiltPackets#sendClearRailTiltC2S} 的约定
     * 「沿用服务端当前值」，因此「清除」只清三个控制点，不会顺手改掉轨距。
     */
    private void clearAndClose() {
        if (ready) {
            RailTiltPackets.sendClearRailTiltC2S(position1, position2, Double.NaN);
            Main.debug("[RailTilt] 已请求清除逐轨道超高：{} -> {}", position1, position2);
        }
        onClose();
    }

    /**
     * 关闭：回到打开本界面的万向节点面板（而不是把界面全关掉）。
     * <p>
     * {@code Esc} 与「返回（不应用）」都走这里：<b>不</b>下发任何包，界面上改过的待提交值直接丢弃
     * （与「应用并返回」相反）。父界面为 {@code null} 时退化成关闭界面。
     */
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    // ==================== 取值与格式化 ====================

    /** 倾斜角钳制到 ±{@link #MAX_TILT_DEGREES}（与服务端同一个边界；非有限值归 0）。 */
    private static double clampTilt(double degrees) {
        if (!Double.isFinite(degrees)) {
            return 0.0D;
        }
        return Math.max(-MAX_TILT_DEGREES, Math.min(MAX_TILT_DEGREES, degrees));
    }

    /** 半轨距钳制到 [{@link #HALF_GAUGE_SLIDER_MIN}, {@link #HALF_GAUGE_SLIDER_MAX}]（与服务端同一个边界）。 */
    private static double clampHalfGauge(double halfGauge) {
        if (!Double.isFinite(halfGauge)) {
            return RailPoseExtra.DEFAULT_HALF_GAUGE;
        }
        return Math.max(HALF_GAUGE_SLIDER_MIN, Math.min(HALF_GAUGE_SLIDER_MAX, halfGauge));
    }

    /**
     * 展示用坐标：{@code (x,y,z)}。逗号后不留空格是为了在只有一列的窄面板里少占几个像素
     * （内容区按面板宽度裁剪，过长的文本会被截断）。
     */
    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    /**
     * 轨道短 id：两端点混合后的 8 位十六进制。
     * <p>
     * <b>这是显示用的标签，不是身份</b>：MTR3 没有 MTR4 的 {@code TwoPositionsBase.getHexId}，
     * 轨道身份就是 {@code (position1, position2)} 这一对端点本身，而服务端在 C2S 路径上
     * <b>自己</b>用收到的端点去查表（见 {@code NodeConnector#applyRailTilt}），
     * 客户端上报的任何「id」都不参与查找。所以本方法只需满足两点：
     * ① 同一对端点（无论方向）得到同一个字符串；② 不同端点对几乎不会撞。
     * XOR 混合天然与顺序无关，正好满足 ①。
     */
    private static String shortId(BlockPos position1, BlockPos position2) {
        final long mixed = (position1.asLong() * 0x9E3779B97F4A7C15L)
                ^ (position2.asLong() * 0xC2B2AE3D27D4EB4FL)
                ^ (position1.asLong() ^ position2.asLong());
        return String.format("%08X", mixed & 0xFFFFFFFFL);
    }

    // ==================== 面板布局（单列，与万向节点面板同宽同边距） ====================

    @Override
    protected int getPanelLeft() {
        return GAP;
    }

    /**
     * 面板右边界：与万向节点面板同一套算法（原单列宽的 {@value #PANEL_WIDTH_FACTOR} 倍），
     * 只是本界面只有一列，所以内容区就是整个面板宽度。
     */
    @Override
    protected int getPanelRight() {
        final int originalWidth = this.width / 5 - GAP * 2;
        final int scaled = Math.round(originalWidth * PANEL_WIDTH_FACTOR);
        return getPanelLeft() + Math.max(scaled, MIN_PANEL_WIDTH);
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
        return getPanelLeft();
    }

    @Override
    protected int getContentRight() {
        return getPanelRight();
    }

    /** 面板总宽度（本界面只有一列，数值行就用它当行宽）。 */
    private int getPanelWidth() {
        return getPanelRight() - getPanelLeft();
    }

    @Override
    protected void renderPanelBackground(GraphicContext g) {
        // 背景延伸到面板右边界再留一个 GAP（与万向节点面板的视觉边距一致）
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
        // 标题画在面板顶部（BasicConfigScreen 不画标题，万向节点面板也是自己画）
        final int titleX = (getPanelLeft() + getPanelRight()) / 2 - this.font.width(this.title.getString()) / 2;
        g.drawString(this.font, this.title, titleX, 2, 0xFFFFFF, false);

        super.render(g.asMinecraft(), mouseX, mouseY, partialTick);

        // 端点 / 轨道读不到时在「应用」按钮上方补一行红字（按钮同时已灰显），避免用户以为点了没反应
        if (!ready) {
            final Component warning = ComponentHelper.translatable("ui.fangsu.rail_tilt.notReady");
            final int warningX = (getPanelLeft() + getPanelRight()) / 2 - this.font.width(warning.getString()) / 2;
            g.drawString(this.font, warning, warningX, this.height - 42, 0xFF5555, false);
        }
    }
}
