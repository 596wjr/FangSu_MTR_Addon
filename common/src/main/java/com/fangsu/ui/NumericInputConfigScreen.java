package com.fangsu.ui;

import com.fangsu.extraConfig.SliderWidget;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 「数值行」输入机制的共享基类：滑块 / 数值框 / 就地输入框三件套 + 双模式总开关。
 * <p>
 * 本类是从 MTR4 版 {@code MultiDirectionNodeConfigScreen} 原样抽出来的，MTR3 版逐字移植
 * （P5-5 逐轨道超高界面要复用同一套输入机制，而不是再造第二种输入样式），因此
 * <b>行为、行高、尺寸、提交语义一字未改</b>：
 * <ul>
 *   <li><b>顶部总开关一刀切</b>：{@link #addInputModeToggle} 挂的按钮切换<b>整块面板</b>的数字输入方式，
 *       两种方式<b>互斥</b>：滑块模式下每行<b>只有滑块</b>，输入模式下每行<b>只有输入框</b>，
 *       绝不会出现「同一行既有滑块又有输入框」；</li>
 *   <li>两种方式的行高都取 {@link #ROW_HEIGHT}，切换时行高不变、其他行不动，也<b>不改任何数值</b>；</li>
 *   <li>输入模式下回车 / 小键盘回车 / 点到别处提交，{@code Esc} 取消，
 *       同一时刻只允许一行处于编辑态（点另一行的输入框会先把上一行提交掉）；</li>
 *   <li>滑块拖不到的值可以切到输入模式键入；超出滑块区间但未超硬边界的值会被原样保留，
 *       切回滑块模式时滑块钉在端点、真值由滑块自身的数值文本显示。</li>
 * </ul>
 * 子类负责面板几何（{@code getPanelLeft/Right/Top/Bottom}、列划分）与各行语义；
 * 在 {@code buildScrollableContent} 开头必须调用 {@link #resetNumericRows()}，否则重建会把
 * 上一批已销毁的控件留在状态机里。
 * <p>
 * <b>条件编译阈值的核对结论（MTR3 专用说明）</b>：本类只用到 {@code MC_VERSION >= 12000}、
 * {@code >= 11904}、{@code >= 11903} 三个阈值，与 MTR3 工程在用的阈值集合
 * （{@code 11900 / 11903 / 11904 / 12000 / 12003}，<b>没有 11902</b>）完全相容，
 * 因此<b>一个字都不需要改</b>。三个阈值的语义也与 MTR3 既有文件一致：
 * <pre>
 *   &gt;= 11903 —— {@code AbstractWidget#getX()/getY()} 访问器出现；
 *                {@code updateWidgetNarration} 成为 protected abstract（替代 updateNarration）
 *   &gt;= 11904 —— {@code renderButton} 改名为 {@code renderWidget}
 *   &gt;= 12000 —— 渲染入口参数由 {@code PoseStack} 换成 {@code GuiGraphics}
 * </pre>
 * 与 {@link BasicConfigScreen.TextLabel}（同工程内的既有实现）用的是同一组阈值与同一套写法。
 */
public abstract class NumericInputConfigScreen extends BasicConfigScreen {

    // ==================== 单行数值行的控件尺寸 ====================
    //
    // 每一行（平移 / 方向 / 俯仰 / 翻滚 / 半轨距 / 逐轨道倾斜控制点…）在<b>两种输入方式下都只占一行</b>，
    // 且行高取同一个常量 {@link #ROW_HEIGHT}，所以切换输入方式时布局不跳、其他行也不动：
    //
    //     [ 标签文字 ]
    //     [ 滑块 ........................................... ]   ← 滑块模式：滑块占满行宽，数值框隐藏
    //     [ 输入框 ......................................... ]   ← 输入模式：不构造滑块，输入框占满列宽
    //
    // 注意：滑块模式下数值框与输入框仍然被构造出来（共用同一段行构造代码），但都不可见，
    // 因此滑块可以放心占满整行 —— 隐藏的控件既不绘制、也点不到（见 isValueBoxVisible）。

    /** 行高（像素）：滑块 20 + 行间距 2。常态与编辑态都用它，保证布局不跳。 */
    protected static final int ROW_HEIGHT = 22;
    /** 滑块高度（像素）。 */
    private static final int SLIDER_HEIGHT = 20;
    /** 数值框 / 输入框高度（像素）。比滑块矮 2 像素，在 20 像素的行里垂直居中。 */
    private static final int VALUE_BOX_HEIGHT = 18;
    /**
     * 数值区（可点击的数值显示 / 就地出现的输入框）占行宽的比例上限与下限。
     * <p>
     * 上限 60 像素是为了给滑块留出可拖动的宽度；下限 40 像素是为了放得下
     * {@code -0.5000} / {@code 270.0000} 这类最长的值。列宽只有 60 像素（极窄面板）时
     * 数值区取 40、滑块剩 20，滑块变窄但仍能拖 —— 两者都不至于完全不可用。
     */
    private static final int VALUE_BOX_MAX_WIDTH = 60;
    private static final int VALUE_BOX_MIN_WIDTH = 40;
    /** 滑块最小宽度（像素）：极窄列下的保底，再窄就拖不准了。 */
    private static final int SLIDER_MIN_WIDTH = 20;
    /** 数值区与滑块之间的间隙（像素）。 */
    private static final int CONTROL_GAP = 2;

    /**
     * 整块面板的数字输入方式（顶部总开关一刀切）：{@code true} = <b>滑块模式</b>（默认，每行只有滑块）；
     * {@code false} = <b>输入模式</b>（每行只有输入框）。
     * <p>
     * 两种方式<b>互斥</b>、行高相同（{@link #ROW_HEIGHT}）：切换只改控件的可见性 / 构造，
     * <b>不改任何数值</b>，也不移动其他行。数值框与就地输入框只属于输入模式
     * （见 {@link #isValueBoxVisible()}），滑块只属于滑块模式。
     */
    protected boolean useSliderInput = true;

    /** 本次构建出来的全部数值输入框，供 {@link #commitAllNumericFields()} 统一提交。 */
    private final List<NumericField> numericFields = new ArrayList<>();

    /**
     * 本次构建出来的全部数值行（键 = 行序号），供 {@link #beginEdit(int)} 按序号找到行、
     * 以及 {@link NumericRow#refreshDisplay()} 在写值后重绘数值框文本
     * （数值框因此永远显示<b>当前真值</b>，包括超出滑块范围的值）。
     */
    private final Map<Integer, NumericRow> valueRows = new LinkedHashMap<>();

    /**
     * 正在编辑的行序号；{@code -1} = 当前没有行处于编辑态。
     * <p>
     * <b>同时只允许一行处于编辑态</b>：点击另一行的数值框时，{@link #beginEdit(int)} 会先
     * {@link #finishEdit()} 把上一行提交掉（写值 + 收起输入框），再切换到新的一行。
     */
    private int editingIndex = -1;

    /** 当前编辑态的输入框；与 {@link #editingIndex} 同步维护，{@code null} = 不在编辑态。 */
    @Nullable
    private NumericField editingField;

    protected NumericInputConfigScreen(Component title) {
        super(title);
    }

    // ==================== 总开关（两种输入方式互斥） ====================

    /**
     * 顶部总开关：把整块面板的所有数值行一起切到另一种输入方式（互斥，见 {@link #useSliderInput}）。
     * 子类在 {@code buildFixedWidgets} 里调用，位置由子类决定（横跨它自己的列宽）。
     */
    protected void addInputModeToggle(int x, int y, int width, int height) {
        addFixedWidget(ComponentHelper.button(x, y, width, height, getInputToggleLabel(), btn -> {
            useSliderInput = !useSliderInput;
            requestRebuild();
        }));
    }

    /**
     * 顶部总开关的标签：显示的是<b>点下去会发生什么</b>，因此滑块模式下写「切换为输入框」、
     * 输入模式下写「切换为滑块」，与当前控件的形态互为印证（状态由整块面板的控件形态直接表达）。
     */
    protected Component getInputToggleLabel() {
        return ComponentHelper.translatable(useSliderInput
                ? "ui.fangsu.block.toggle_input"
                : "ui.fangsu.block.toggle_slider");
    }

    /**
     * 数值框（可点击的 {@code [ 1.5 ]}）与就地输入框是否应当可见：<b>只属于输入模式</b>。
     * <p>
     * 滑块模式下这一行<b>只有滑块</b>：数值框既不绘制（{@code visible = false} 时
     * {@code AbstractWidget.render} 会直接跳过，各 MC 版本一致），也点不到
     * （{@link ValueButton#isHovered} 先判 {@code visible}），
     * 于是「点一下就地变输入框」这条入口在滑块模式下完全关闭；输入模式下反过来不构造滑块。
     * 两种模式的行高都是 {@link #ROW_HEIGHT}，控件 y 也相同，因此切换不跳、其他行不动。
     */
    protected boolean isValueBoxVisible() {
        return !useSliderInput;
    }

    /**
     * 重建内容前收掉编辑态并丢掉上一批行的引用：子类在 {@code buildScrollableContent} 开头调用。
     * <p>
     * 重建会销毁全部数值行的控件引用（含可能正在编辑的那一个），必须先取消编辑，
     * 再丢掉上一批行的引用，避免状态机指向已经不存在的控件。
     */
    protected void resetNumericRows() {
        cancelEdit();
        editingIndex = -1;
        editingField = null;
        numericFields.clear();
        valueRows.clear();
    }

    // ==================== 单行数值行：控件尺寸与文本格式 ====================

    /** 数值框 / 输入框宽度：行宽的 40%，钳在 [{@link #VALUE_BOX_MIN_WIDTH}, {@link #VALUE_BOX_MAX_WIDTH}]。 */
    private int valueBoxWidth(int rowWidth) {
        return Math.max(VALUE_BOX_MIN_WIDTH,
                Math.min(VALUE_BOX_MAX_WIDTH, Math.round(rowWidth * 0.4f)));
    }

    /**
     * 数值区右侧宽度：行宽减去数值区与间隙，保底 {@link #SLIDER_MIN_WIDTH}。
     * <p>
     * 滑块模式下滑块已占满行宽（见 {@link #addSliderInputRow}），本方法<b>不再</b>用于算滑块宽度，
     * 现在唯一的调用点是隐藏数值框 / 数值框按钮的 x —— 它们不可见，位置只求与旧布局一致，
     * 因此保留本方法而不是删掉（删掉会改动那两处不可见控件的坐标计算）。
     */
    private int sliderWidth(int rowWidth, int boxWidth) {
        return Math.max(SLIDER_MIN_WIDTH, rowWidth - boxWidth - CONTROL_GAP);
    }

    /**
     * 一行「标签 + 数值控件」。两种输入方式<b>都</b>只占一行、行高相同，且<b>互斥</b>：
     * <ul>
     *   <li>{@code useSliderInput} = 是（滑块模式）→ <b>只有滑块</b>（数值框隐藏）；</li>
     *   <li>{@code useSliderInput} = 否（输入模式）→ <b>只有输入框</b>（占满列宽，不构造滑块）。</li>
     * </ul>
     * 两者共用同一套提交 / 编辑规则与同一批 setter，因此不会出现「某种输入方式下行为不一样」。
     *
     * @param value     构造该行时的当前值（只用于初始化控件显示文本）
     * @param sliderMin 滑块区间下限（拖动便利，与硬边界无关）
     * @param sliderMax 滑块区间上限（同上）
     * @param step      滑块步进
     * @param setter    与滑块共用的写入函数（内部按硬边界钳制 / 回绕）
     * @param current   读取「已经落库的真值」，提交时用它规范化或回滚输入框文本
     * @param onChanged 与滑块共用的变更回调（写 BE_SYNC + 触发轨道重建）
     */
    protected int addAxisRow(int areaLeft, int y, int rowWidth, Component label,
                             float value, float sliderMin, float sliderMax, float step,
                             Consumer<Float> setter, FloatGetter current, Runnable onChanged) {
        return useSliderInput
                ? addSliderInputRow(areaLeft, y, rowWidth, label, value, sliderMin, sliderMax, step, setter, current, onChanged)
                : addInputOnlyRow(areaLeft, y, rowWidth, label, value, setter, current, onChanged);
    }

    /**
     * 数字转字符串（最多 4 位小数，去掉无意义的尾随 0）：
     * {@code 1.5 → "1.5"}、{@code 270 → "270"}、{@code 0.7175 → "0.7175"}、{@code -0 → "0"}。
     * <p>
     * 数值框只有 40~60 像素宽，{@code "270.0000"} 这种定长写法会顶到框外，
     * 所以数值框与就地输入框都用这个紧凑格式（提交后的规范化也用它，因此「提交后显示什么」是可预期的）。
     */
    protected static String formatCompact(double value) {
        String text = String.format("%.4f", value);
        if (text.indexOf('.') >= 0) {
            text = text.replaceAll("0+$", "");
            if (text.endsWith(".")) {
                text = text.substring(0, text.length() - 1);
            }
        }
        return "-0".equals(text) ? "0" : text;
    }

    /**
     * 一行「标签 + 滑块」，<b>标签一行、控件一行，共 2 行</b>（旧实现的「标签 / 滑块 / 输入框」三行版已删除）。
     * <p>
     * <b>滑块模式在这一行只显示滑块</b>：数值框 {@link ValueButton} 与就地输入框 {@link NumericField}
     * 都<b>不</b>可见（数值框用 {@link #isValueBoxVisible()} 显式置 {@code visible = false}），
     * 因此既不会画出来、也点不到，「点一下变输入框」这条入口在滑块模式下完全关闭。
     * 它们仍然被构造出来，是为了让两种输入方式共用同一段行构造代码与同一套宽度计算，
     * 并且提交 / 取消（{@link #finishEdit()} / {@link #cancelEdit()}）恢复可见性时不必区分模式。
     * <p>
     * <b>行高两种方式相同</b>：{@link #ROW_HEIGHT}，控件 y 也相同，所以切换输入方式时布局<b>不跳</b>，
     * 其他行也不动（{@code entries} 的 {@code baseY} 不随输入方式改变）。
     * <p>
     * <b>越界语义</b>（与旧实现一致，一字未改）：滑块拖不到的值可以切到输入模式键入 ——
     * 键入值在滑块区间内 → 与拖动完全等价（同一个 {@code setter} + 同一个 {@code onChanged}）；
     * 超出滑块区间但未超硬边界 → 真值原样保留，滑块由 {@link SliderWidget#setExternal(float)} 钉在端点，
     * 切回滑块模式时真值由滑块自身的数值文本显示；超出硬边界 → setter 按硬边界钳制（方向回绕）。
     */
    private int addSliderInputRow(int areaLeft, int y, int rowWidth,
                                  Component label, float value,
                                  float sliderMin, float sliderMax, float step,
                                  Consumer<Float> setter, FloatGetter current, Runnable onChanged) {
        addEntry(createTextLabel(areaLeft, y, label, TextLabel.Align.LEFT, 0xFFFFFF, false), y);
        y += 8;

        final int index = numericFields.size();
        final int boxWidth = valueBoxWidth(rowWidth);
        final NumericRow row = new NumericRow(index, current);
        row.rowTop = y;

        // 滑块模式整行只有滑块，所以滑块直接占满行宽（不再给数值区留位置）；
        // 高度与 y 都不变，因此切换输入方式时行高不变、其他行也不动
        final SliderWidget slider = new SliderWidget(areaLeft, y, rowWidth, SLIDER_HEIGHT,
                ComponentHelper.empty(), value, sliderMin, sliderMax, step,
                v -> {
                    setter.accept(v);
                    // 数值框跟随滑块（静默回写，不触发 responder 二次写值）；滑块模式下数值框不可见，
                    // 这里只是保持文本与真值一致，切到输入模式时输入框一开始就是对的
                    row.writeFieldSilently(formatCompact(current.get()));
                    row.refreshDisplay();
                    onChanged.run();
                });
        row.slider = slider;
        addRenderableWidget(slider);
        addEntry(slider, y);

        // 数值框 / 数值框按钮在滑块模式下不可见，这里的 x 沿用旧的「滑块右侧」算法，
        // 只是为了让这两个不可见控件的坐标与旧布局一致（点不到、画不出，不影响滑块）
        final int boxX = areaLeft + sliderWidth(rowWidth, boxWidth) + CONTROL_GAP;
        final NumericField box = new NumericField(boxX, y, boxWidth, index, setter, current, onChanged, row);
        row.box = box;
        box.setInitialValue(value);
        addRenderableWidget(box);
        addEntry(box, y);

        final ValueButton button = new ValueButton(boxX, y, boxWidth, index, row);
        // 滑块模式：数值框不显示（本行只有滑块）。这一行是本次改动的核心 —— 数值框、
        // 高亮、点击编辑入口全部随 visible 一起消失，控件位置 / 行高 / 其他行都不动。
        button.visible = isValueBoxVisible();
        row.button = button;
        addRenderableWidget(button);
        addEntry(button, y);

        row.refreshDisplay();
        registerRow(row);
        return y + ROW_HEIGHT;
    }

    /**
     * 只有输入框的一行（{@code useSliderInput} = 否，输入模式）。除没有滑块外，
     * 一切行为与 {@link #addSliderInputRow} 完全一致（行高同样是 {@link #ROW_HEIGHT}）。
     * <p>
     * 数值框 {@link ValueButton} 仍然在这里构造并<b>可见</b>（{@link #isValueBoxVisible()} = 是）：
     * 输入模式的常态显示的是「点一下就地变输入框」的数值框，点开后才换成 {@link NumericField}，
     * 因此这一行与本次改动无关，保持原样。
     */
    private int addInputOnlyRow(int areaLeft, int y, int rowWidth,
                                Component label, float value,
                                Consumer<Float> setter, FloatGetter current, Runnable onChanged) {
        addEntry(createTextLabel(areaLeft, y, label, TextLabel.Align.LEFT, 0xFFFFFF, false), y);
        y += 8;

        final int index = numericFields.size();
        final int boxWidth = Math.max(VALUE_BOX_MIN_WIDTH, rowWidth);
        final NumericRow row = new NumericRow(index, current);
        row.rowTop = y;

        final NumericField box = new NumericField(areaLeft, y, boxWidth, index, setter, current, onChanged, row);
        row.box = box;
        box.setInitialValue(value);
        addRenderableWidget(box);
        addEntry(box, y);

        final ValueButton button = new ValueButton(areaLeft, y, boxWidth, index, row);
        button.visible = isValueBoxVisible();
        row.button = button;
        addRenderableWidget(button);
        addEntry(button, y);

        row.refreshDisplay();
        registerRow(row);
        return y + ROW_HEIGHT;
    }

    /** 登记一行数值行（并建立「行序号 → 行」的索引，供数值框文本刷新用）。 */
    private void registerRow(NumericRow row) {
        valueRows.put(row.index, row);
        if (row.box != null) {
            numericFields.add(row.box);
        }
    }

    // ==================== 就地编辑状态机（同时只允许一行编辑） ====================

    /**
     * 进入某一行的编辑态：数值框就地换成 {@link NumericField}（同一个位置、同一个高度，布局不跳）。
     * <p>
     * 切换前先 {@link #finishEdit()} 提交上一行 —— 这就是「同时只允许一行处于编辑态」的实现：
     * 点另一个数值框时，上一个输入框的文本立刻落库并收起。
     */
    private void beginEdit(int index) {
        final NumericRow row = valueRows.get(index);
        if (row == null || row.box == null) {
            return;
        }
        // 已经是这一行在编辑：只需把焦点抢回来
        if (editingIndex == index && editingField == row.box) {
            row.box.setFocused(true);
            return;
        }
        finishEdit();
        row.writeFieldSilently(formatCompact(row.current.get()));
        row.button.visible = false;
        row.box.visible = true;
        editingIndex = index;
        editingField = row.box;
        row.box.setFocused(true);
    }

    /**
     * 提交并退出编辑态（回车的落库路径）。
     * <p>
     * {@link NumericField#commit()} 已经把「可解析 → 落库 + 规范化文本 / 不可解析 → 文本回滚」
     * 做完，这里只负责收尾（收起输入框、显示数值框、清掉状态与焦点）。
     * <p>
     * <b>为什么还要在界面层收尾</b>：MC 1.18.2 的 {@code AbstractWidget.setFocused} 是 protected，
     * 且容器的 {@code setFocused} 只改自己的引用字段、<b>不</b>通知旧控件（1.19.4+ 才回调），
     * 所以「点到别处」「点保存」这两个时机不能只依赖 {@code setFocused}，还要由
     * {@link #mouseClicked} 与子类的保存动作显式调用 {@link #commitAllNumericFields()}。
     * 三条路径最终都收敛到同一个 {@link NumericField#commit()}。
     */
    private void finishEdit() {
        if (editingIndex < 0) {
            return;
        }
        final NumericRow row = valueRows.get(editingIndex);
        final NumericField field = editingField;
        // 先清状态再提交：commit() 内部可能再次走到 finishEdit（例如 setFocused(false) 的回调），
        // 状态已清空即可直接返回，不会递归
        editingIndex = -1;
        editingField = null;
        if (field != null) {
            field.commit();
            field.visible = false;
        }
        if (row != null && row.button != null) {
            // 恢复数值框时同样过一遍模式判定：输入模式才显示，滑块模式保持隐藏（见 isValueBoxVisible）
            row.button.visible = isValueBoxVisible();
            row.button.setDisplayText(formatCompact(row.current.get()));
        }
    }

    /**
     * 取消编辑（Esc）：<b>一个字节都不写</b>，把输入框文本恢复成当前真值后收起输入框。
     * <p>
     * 与 {@link #finishEdit()}（提交）的区别只在于「不回写数据」；输入过程中
     * {@link NumericField} 的 responder 已经实时写过合法的中间值，而 Esc 的语义是
     * 「放弃本次键入」，因此恢复文本后不触碰 {@code setter} / {@code onChanged}。
     */
    private void cancelEdit() {
        if (editingIndex < 0) {
            return;
        }
        final NumericRow row = valueRows.get(editingIndex);
        final NumericField field = editingField;
        editingIndex = -1;
        editingField = null;
        if (field != null) {
            field.visible = false;
            // 先恢复文本（静默，不写值），再摘掉焦点，顺序反过来会触发一次无用的提交
            if (row != null) {
                row.writeFieldSilently(formatCompact(row.current.get()));
            }
            field.setFocused(false);
        }
        if (row != null && row.button != null) {
            // 同 finishEdit：恢复可见性也要过模式判定，滑块模式下不能把数值框重新显示出来
            row.button.visible = isValueBoxVisible();
            row.button.setDisplayText(formatCompact(row.current.get()));
        }
    }

    /**
     * 提交全部数值输入框的待处理文本（回车之外的兜底：鼠标点到别处、点「保存并退出」、界面重建）。
     * <p>
     * 见 {@link NumericField} 的说明：跨 MC 版本（1.18.2 ~ 1.20.1）的「失去焦点」回调不可靠，
     * 因此由界面显式调用。正常情况下同时最多只有一个框处于编辑态，这里循环是为了兜底。
     */
    protected void commitAllNumericFields() {
        for (final NumericField field : numericFields) {
            field.commit();
        }
    }

    /** 读取「当前已落库数值」的取值器（输入框提交 / 回滚时用）。 */
    @FunctionalInterface
    protected interface FloatGetter {
        float get();
    }

    /**
     * 解析输入框文本：非数字（{@code "abc"}、空串、{@code "-"}）与<b>非有限值</b>一律返回 {@code null}。
     * <p>
     * 为什么不直接用框架的 {@link BasicConfigScreen#parseFloat(String)}：它只挡
     * {@code NumberFormatException}，而 {@code Float.parseFloat("NaN")} 会成功返回 NaN、
     * {@code "1e999"} 会返回 Infinity。放行的话它们会被 setter 落成 0 或边界值 ——
     * 那正是「非法输入静默写 0」这种要避免的行为。所以这里把「非有限」也归入非法输入。
     */
    @Nullable
    private static Float parseFieldValue(String text) {
        if (text == null) {
            return null;
        }
        final float parsed;
        try {
            parsed = Float.parseFloat(text);
        } catch (NumberFormatException ex) {
            return null;
        }
        if (Float.isNaN(parsed) || Float.isInfinite(parsed)) {
            return null;
        }
        return parsed;
    }

    /**
     * 一行数值控件（滑块 + 可点击数值框 + 就地输入框）的容器。
     * <p>
     * 三个控件互相引用（滑块回调要刷新数值框与文本、点击数值框要开输入框、提交要回写滑块），
     * 构造顺序上必有一方先于另一方存在，因此用这个可变容器在闭包里解引用，而不是依赖构造顺序。
     * 滑块缺席（{@code useSliderInput} = 否）时 {@link #slider} 为 {@code null}，对应操作静默跳过。
     */
    private final class NumericRow {

        /** 行序号：{@code numericFields} 的下标，也是 {@code valueRows} 的键与编辑态的身份。 */
        private final int index;
        /** 读取「已落库真值」的取值器：数值框文本、提交规范化、回滚都用它。 */
        private final FloatGetter current;
        /** 这一行控件的 baseY（构建时的逻辑 y，不含滚动偏移）：只用于「点击是否在本行内」的判定。 */
        private int rowTop;
        private SliderWidget slider;
        private NumericField box;
        private ValueButton button;
        /** 静默回写标志：为 true 时输入框的 responder 只更新文本、不写值。 */
        private boolean silent;

        NumericRow(int index, FloatGetter current) {
            this.index = index;
            this.current = current;
        }

        /** 静默把文本写进输入框：不触发 responder，因此不会二次写值 / 二次重建轨道。 */
        private void writeFieldSilently(String text) {
            if (box == null || text.equals(box.getValue())) {
                return;
            }
            silent = true;
            try {
                box.setValue(text);
            } finally {
                silent = false;
            }
        }

        /** 输入框 → 滑块：把真值交给滑块显示；越界时由 {@link SliderWidget#setExternal(float)} 钉在端点。 */
        private void applyToSlider(float applied) {
            if (slider != null) {
                slider.setExternal(applied);
            }
        }

        /** 重绘数值框文本（当前真值的紧凑格式），使它与滑块、输入框三者始终一致。 */
        private void refreshDisplay() {
            if (button != null) {
                button.setDisplayText(formatCompact(current.get()));
            }
        }
    }

    /**
     * 可点击的数值显示（输入模式下这一行的「数值」外观）：{@code [ 1.5 ]}。
     * <p>
     * <b>只在输入模式可见</b>：滑块模式下一行只有滑块，本控件由 {@link #isValueBoxVisible()}
     * 置 {@code visible = false} —— 不绘制、悬停不高亮、也点不到（{@link #isHovered} 先判 {@code visible}），
     * 因此「点一下就地变输入框」这条入口在滑块模式下完全关闭。代码本身<b>没有</b>删掉，
     * 输入模式仍然完全依赖它。
     * <p>
     * <b>为什么不用普通文本标签</b>：标签没有任何「可以点」的提示，用户不会想到点它能输入。
     * 这里画成带背景块 + 方括号的「值」，悬停时提亮背景与文字，既与界面既有的灰底风格一致，
     * 又把「这是一块可点的控件」表达清楚。
     * <p>
     * <b>两种 MC 版本的渲染分叉</b>：{@code AbstractWidget} 的渲染方法在 1.19.4+ 是
     * {@code renderWidget(GuiGraphics, int, int, float)}，在 1.18.2 是
     * {@code render(PoseStack, int, int, float)}。两者签名不同、无法用条件编译把「同一个方法名」
     * 分叉出来（条件块的两个分支会被同时编译），因此这里声明两个私有方法各自加条件编译，
     * 用匿名 {@link Renderable} 在运行时按 {@code GraphicContext} 的类型挑一个 ——
     * 界面本身的 {@code render} 就是这么分叉的，这里沿用同一套写法。
     */
    private final class ValueButton extends AbstractWidget {

        private final int rowIndex;
        private final NumericRow row;
        private String displayText = "";

        ValueButton(int x, int y, int width, int rowIndex, NumericRow row) {
            super(x, y, width, VALUE_BOX_HEIGHT, ComponentHelper.empty());
            this.rowIndex = rowIndex;
            this.row = row;
        }

        /** 更新显示文本（由 {@link NumericRow#refreshDisplay()} 调用）。 */
        void setDisplayText(String text) {
            this.displayText = text;
        }

        /**
         * 控件左上角与尺寸：{@code getX() / getY()} 是 1.19.3+ 才有的访问器，
         * 1.18.2 里坐标是 {@code public int x / y} 字段（{@code width / height} 则一直是 protected 字段）。
         * 条件编译的两个分支都会被同时编译，所以坐标读取必须这样分叉 ——
         * 同仓库的 {@link BasicConfigScreen.TextLabel#renderLabel} 与
         * {@link SliderWidget} 内层滑块就是这么写的。
         */
        private int[] bounds() {
            //#if MC_VERSION >= 11903
            return new int[]{this.getX(), this.getY(), this.width, this.height};
            //#else
            //$$ return new int[]{this.x, this.y, this.width, this.height};
            //#endif
        }

        /** 不用 hovered 状态，避免依赖 AbstractWidget 的鼠标追踪（不同版本细节不同）。 */
        private boolean isHovered(int mouseX, int mouseY) {
            final int[] b = bounds();
            return this.visible
                    && mouseX >= b[0] && mouseX < b[0] + b[2]
                    && mouseY >= b[1] && mouseY < b[1] + b[3];
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && isHovered((int) mouseX, (int) mouseY)) {
                // 就地进入编辑态：数值框换成输入框（位置与高度都不变，见 beginEdit）
                beginEdit(rowIndex);
                return true;
            }
            return false;
        }

        /**
         * 数值框不参与 Tab 焦点轮转：它的「编辑」语义是点一下就地变成输入框，
         * 真正的焦点属于输入框。置成不可聚焦也避免抢走输入框的焦点。
         */
        @Override
        public void setFocused(boolean focused) {
            // 1.18.2 的 AbstractWidget#setFocused 是 protected，这里放宽为 public（Java 允许），
            // 因此在两个版本上都能作为覆写编过（同 NumericField#setFocused）。
            super.setFocused(false);
        }

        private void draw(GraphicContext g, int mouseX, int mouseY) {
            final boolean hovered = isHovered(mouseX, mouseY);
            final int[] b = bounds();
            final int left = b[0];
            final int top = b[1];
            final int right = left + b[2];
            final int bottom = top + b[3];
            // 方框：常态浅底 + 细边框，悬停时整体提亮（同色系，不引入新配色）
            g.fill(left, top, right, bottom, hovered ? 0x60FFFFFF : 0x30FFFFFF);
            g.fill(left, top, right, top + 1, 0x80FFFFFF);
            g.fill(left, bottom - 1, right, bottom, 0x80FFFFFF);
            g.fill(left, top, left + 1, bottom, 0x80FFFFFF);
            g.fill(right - 1, top, right, bottom, 0x80FFFFFF);
            // 方括号 + 值：明确「这里是一个可以点的数值」
            final String text = "[" + this.displayText + "]";
            final int textX = left + (b[2] - NumericInputConfigScreen.this.font.width(text)) / 2;
            final int textY = top + (b[3] - 8) / 2 + 1;
            g.drawString(NumericInputConfigScreen.this.font, text, textX, textY,
                    hovered ? 0xFFFFA0 : 0xFFFFFF, false);
        }

        //#if MC_VERSION >= 12000
        private void renderAt(java.lang.Object graphics, int mouseX, int mouseY) {
            draw(GraphicContext.of((net.minecraft.client.gui.GuiGraphics) graphics), mouseX, mouseY);
        }
        //#else
        //$$ private void renderAt(java.lang.Object graphics, int mouseX, int mouseY) {
        //$$     draw(GraphicContext.of((com.mojang.blaze3d.vertex.PoseStack) graphics), mouseX, mouseY);
        //$$ }
        //#endif

        //#if MC_VERSION >= 12000
        @Override
        protected void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderAt(graphics, mouseX, mouseY);
        }
        //#elseif MC_VERSION >= 11904
        //$$ @Override
        //$$ protected void renderWidget(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     renderAt(poseStack, mouseX, mouseY);
        //$$ }
        //#elseif MC_VERSION >= 11903
        //$$ @Override
        //$$ protected void renderButton(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     renderAt(poseStack, mouseX, mouseY);
        //$$ }
        //#else
        //$$ @Override
        //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     renderAt(poseStack, mouseX, mouseY);
        //$$ }
        //#endif

        // 旁白：跨版本的方法名不一样，必须分叉 ——
        //   1.19.3+：AbstractWidget#updateWidgetNarration 是 protected abstract，必须实现；
        //   1.18.2：没有 updateWidgetNarration，但 NarratableEntry#updateNarration 是
        //           public abstract，不实现就会「不是抽象类且未实现抽象方法」。
        // 两段都是空实现（本控件不需要旁白），但都要有。
        //#if MC_VERSION >= 11903
        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
        }
        //#else
        //$$ @Override
        //$$ public void updateNarration(NarrationElementOutput narration) {
        //$$ }
        //#endif
    }

    /**
     * 就地数值输入框：<b>回车 / 小键盘回车 / {@code Esc} / 失去焦点</b>是它的退出时机。
     * <p>
     * 行为（本界面选定并在此固定；所有数值行共用这一个实现，因此不存在第二条更新路径）：
     * <ul>
     *   <li>输入过程中只要文本<b>可解析</b>，就立刻走与滑块<b>完全相同</b>的 {@code setter} + {@code onChanged}
     *       （同样的写 BE、同样的 BE_SYNC、同样的轨道重建），并把滑块挪到该值处；</li>
     *   <li>回车 / 失去焦点时提交：可解析 → 按硬边界钳制（方向回绕）后落库，
     *       并把文本重写成<b>真正的落库值</b>（例如键入 999 会显示硬边界值）；
     *       <b>不可解析 → 一个字节都不改数据，只把文本回滚成当前真值</b>。
     *       这就是本界面对非法输入的处理方式：不动数据 + 文本回滚，
     *       <b>不</b>静默写 0 / NaN，也<b>不</b>给输入框染色（染色需要按 MC 版本分叉 {@code setTextColor}，
     *       而回滚在所有目标版本上都是同一份代码）；</li>
     *   <li>{@code Esc} 取消：文本恢复成当前真值并收起输入框，同样不写数据；</li>
     *   <li>输入过程中的非法中间态（例如刚敲下的 {@code "-"}、{@code "1e"}、空串）不会被写值，
     *       因此也不会把 0 / NaN 偷偷写进节点。</li>
     * </ul>
     * 之所以「边输边写 + 提交时规范化」而不是「只在提交时才写」：滑块是实时写值的，
     * 若输入框只在提交时才写，界面上就会长期存在「显示值 ≠ 已生效值」的不一致。
     * <p>
     * <b>跨版本</b>：回车与 Esc 由 {@link #keyPressed} 自己拦下（不依赖 {@code EditBox} 的默认处理，
     * 各版本并不一致）；「失去焦点」同时挂了 {@link #setFocused} 与界面层的
     * {@link #commitAllNumericFields()}（鼠标点击 / 保存），因为 1.18.2 的 {@code setFocused}
     * 不会通知旧控件、1.19.4+ 才会。三条路径都只是调用同一个 {@link #commit()}，语义完全一致。
     */
    private final class NumericField extends EditBox {

        private final int rowIndex;
        private final Consumer<Float> setter;
        private final FloatGetter current;
        private final Runnable onChanged;
        private final NumericRow row;

        NumericField(int x, int y, int width, int rowIndex,
                     Consumer<Float> setter, FloatGetter current, Runnable onChanged, NumericRow row) {
            super(NumericInputConfigScreen.this.font, x, y, width, VALUE_BOX_HEIGHT,
                    ComponentHelper.empty());
            this.rowIndex = rowIndex;
            this.setter = setter;
            this.current = current;
            this.onChanged = onChanged;
            this.row = row;
            this.setResponder(text -> {
                if (row.silent) {
                    // 程序回写（滑块 → 输入框）不写值，否则会二次重建轨道
                    return;
                }
                final Float parsed = parseFieldValue(text);
                if (parsed == null) {
                    // 非法 / 中间态 / 非有限文本：不写值，等 Enter / Esc / 失去焦点时统一处理
                    return;
                }
                setter.accept(parsed);
                row.applyToSlider(parsed);
                // 只刷新数值框（它此刻是隐藏的），不改输入框文本：边输边改文本会把光标顶到行尾
                row.refreshDisplay();
                onChanged.run();
            });
            // 初始隐藏：常态显示的是可点击的数值框，点一下才把它换成输入框
            this.visible = false;
        }

        /**
         * 输入框里的文本用紧凑格式（同 {@link #formatCompact(double)}）：与数值框的显示格式一致，
         * 且 {@code 270} / {@code -0.5} 这类值不会因为定长 4 位小数而超过 40~60 像素的框宽。
         * 提交时的规范化也走这个方法，因此「提交后显示什么」是可预期的。
         * <p>
         * 注意：不能写成 {@code @Override formatValue(float)} —— {@code EditBox} 里同名的方法是
         * {@code private}（各版本都是），无法覆写；这里只是本类自己的格式化入口。
         */
        private String formatFieldValue(float value) {
            return formatCompact(value);
        }

        /** 初始化显示文本（静默：构造期不发 BE_SYNC、不重建轨道）。 */
        void setInitialValue(float value) {
            row.writeFieldSilently(formatFieldValue(value));
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (this.isFocused() && (keyCode == 257 || keyCode == 335)) {
                // 257 / 335 = 回车 / 小键盘回车。在 super 之前拦下：不同 MC 版本里 EditBox 对回车的
                // 默认处理并不一致（有的只把 responder 再叫一次、有的不处理），自己接管最稳。
                commit();
                return true;
            }
            if (this.isFocused() && keyCode == 256) {
                // 256 = Esc。交给界面统一处理「取消本次编辑」，不留给各版本行为不一的默认实现
                cancelEdit();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void setFocused(boolean focused) {
            // 可见性由 protected（1.18.2 的 AbstractWidget）放宽为 public（1.19.4+ 的 GuiEventListener
            // 契约）。Java 允许这种放宽，因此同一份代码在两个版本上都能编过。
            final boolean wasFocused = this.isFocused();
            super.setFocused(focused);
            if (wasFocused && !focused) {
                commit();
            }
        }

        /** 提交：可解析 → 落库并规范化文本；不可解析 → 值不变、文本回滚。提交后收起输入框。 */
        void commit() {
            if (this.visible) {
                commitValue();
                // 提交即退出编辑态（「点别处 / 回车」都走这里），数值框重新显示当前真值
                if (editingField == this) {
                    finishEdit();
                }
            }
        }

        /** 纯数据提交（不碰编辑态）：{@link #finishEdit()} 收尾时调用。 */
        private void commitValue() {
            if (row.silent) {
                return;
            }
            final Float parsed = parseFieldValue(this.getValue());
            if (parsed == null) {
                row.writeFieldSilently(formatFieldValue(current.get()));
                return;
            }
            setter.accept(parsed);
            row.applyToSlider(parsed);
            onChanged.run();
            // 文本规范化为「真正落库的值」：可能被硬边界钳制（方向则是回绕）过，不能停在用户键入的原文
            row.writeFieldSilently(formatFieldValue(current.get()));
        }
    }

    // ==================== 点击：先把正在编辑的那一行提交掉 ====================

    /**
     * 点击任何位置之前，先把「正在编辑的那一行」的待处理文本提交掉。
     * <p>
     * 这就是「输入框失去焦点即提交」在本界面上的实现方式：不依赖 MC 版本各异的焦点回调
     * （见 {@link #finishEdit()} 的说明），而是用「任何一次鼠标点击」这一在 1.18.2~1.20.1 上
     * 行为完全一致的事件作为提交时机。
     * <p>
     * 点在<b>同一行</b>上时跳过提交：滑块与数值区是同一行，点滑块只是改值 / 继续看这一行，
     * 若在这里提交，输入框会立刻收起（用户会觉得「点一下滑块编辑就没了」）。
     * 判定用构建时记录的 {@link NumericRow#rowTop}（逻辑 y，不含滚动偏移），
     * 不依赖焦点也不依赖各版本不一致的坐标访问器，语义在 1.18.2~1.20.1 上一致。
     * <p>
     * <b>事件顺序（容易写错的地方）</b>：{@code super.mouseClicked} 会把点击派发给子控件，
     * 也就是说「点了某个数值框」的 {@link ValueButton#mouseClicked} → {@link #beginEdit(int)}
     * 是在本方法<b>下半段</b>才发生的。因此进入时的 {@link #editingIndex} 只可能属于「上一次
     * 已经开着的那一行」——提交它、并跳过点击落点那一行，正是「点另一个数值框先提交上一行、
     * 再把新的一行切进编辑态」这个语义。写得简单一点（比如对全部框无条件 commit）会把刚点开的
     * 输入框立刻提交掉、编辑态一闪而过，这里刻意按上面两条规则来。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        final int editingBeforeClick = editingIndex;
        if (editingBeforeClick >= 0) {
            final NumericRow row = valueRows.get(editingBeforeClick);
            // 点在编辑行自己身上（滑块或数值区）→ 不提交；点在别处 → 提交并退出编辑态。
            // 用构建时记录的 rowTop（而非 getY()）：getY() 在 1.18.2 上不存在。
            final boolean insideRow = row != null
                    && mouseY >= row.rowTop - 2
                    && mouseY <= row.rowTop + ROW_HEIGHT;
            if (!insideRow) {
                finishEdit();
            }
        }
        final boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // 派发之后：点中的那一行可能刚刚进入编辑态（上面的 beginEdit），
        // 它与「上一行刚被 finishEdit 提交」两种情况都由 editingIndex 表达，跳过即可
        for (final NumericField field : numericFields) {
            if (field.rowIndex == editingIndex) {
                continue;
            }
            field.commit();
        }
        return handled;
    }
}
