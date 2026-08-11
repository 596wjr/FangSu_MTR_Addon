package com.fangsu.ui;

import com.fangsu.data.hybrid.HybridSliceTask;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * 切片任务参数配置（起点距离/间隔/厚度/切片大小），基于 BasicConfigScreen。
 * <p>
 * <ul>
 *   <li>start：第一个切片的轨道距离</li>
 *   <li>间隔 interval：每组切片之间的空隙格数；留空或 0 = 无缝循环铺满（123123…）</li>
 *   <li>厚度 thickness：每组由 N 个连续切片组成，每片有独立横截面矩阵（画布可切换编辑）；
 *       方向（·向外 / ×向里）决定厚度片从主片向轨道哪一侧延伸</li>
 *   <li>切片大小 width/height：矩阵宽高（仅奇数，中心对齐搬运方块，所有厚度片一起搬运）</li>
 * </ul>
 * 关闭时写回任务并同步 NBT（服务端构建读取最新值）。
 */
public class HybridSliceConfigScreen extends BasicConfigScreen {

    private final HybridSliceTask task;
    private final String key;
    private final HybridSliceTaskScreen parent;

    private double start;
    private Double interval;
    private int width;
    private int height;
    private int thickness;
    private boolean thickDirection;
    /** 方向按钮引用：厚度 1 时置灰（方向无效果），厚度输入变化时联动 */
    private DirToggle dirOut;
    private DirToggle dirIn;

    public HybridSliceConfigScreen(HybridSliceTask task, String key, HybridSliceTaskScreen parent) {
        super(ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.title"));
        this.task = task;
        this.key = key;
        this.parent = parent;
        this.start = task.start;
        this.interval = task.interval;
        this.width = task.width;
        this.height = task.height;
        this.thickness = task.thickness;
        this.thickDirection = task.thickDirection;
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        layout.y += 12;
        addDoubleInput(layout, ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.start_pos"), formatValue(start), v -> start = v, false, false);
        // 间隔：留空 = null = 无缝循环（构建层按 0 处理），由 allowEmpty 在 responder 里置 null；
        // 负数不接受（会死循环）
        addDoubleInput(layout, ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.interval"), interval == null ? "" : formatValue(interval), v -> {
            if (v != null && v >= 0) interval = v;
        }, false, true);
        addOddIntInput(layout, ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.width"), String.valueOf(width), v -> width = v);
        addOddIntInput(layout, ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.height"), String.valueOf(height), v -> height = v);
        // 厚度：正整数（≥1，无需奇数——组内切片无中心对称要求）
        final TextLabel thickLabel = createTextLabel(layout.areaLeft, layout.y, ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.thickness"), TextLabel.Align.LEFT, 0xFFFFFF, false);
        addEntry(thickLabel, layout.y);
        final EditBox thickBox = new EditBox(this.font, layout.areaLeft + layout.labelWidth, layout.y, layout.fieldWidth, 20, ComponentHelper.empty());
        thickBox.setValue(String.valueOf(thickness));
        thickBox.setResponder(text -> {
            final Integer v = parsePositiveInt(text);
            if (v == null) return;
            thickness = v;
            updateDirActive();
        });
        this.addRenderableWidget(thickBox);
        addEntry(thickBox, layout.y);
        layout.y += 26;
        // 方向（·向外 / ×向里）：厚度片从主片向轨道哪一侧延伸
        final TextLabel dirLabel = createTextLabel(layout.areaLeft, layout.y, ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.thick_dir"), TextLabel.Align.LEFT, 0xFFFFFF, false);
        addEntry(dirLabel, layout.y);
        final int dirX = layout.areaLeft + layout.labelWidth;
        dirOut = new DirToggle(dirX, layout.y, 30, 20, "·", false);
        dirIn = new DirToggle(dirX + 34, layout.y, 30, 20, "×", true);
        updateDirActive();
        this.addRenderableWidget(dirOut);
        this.addRenderableWidget(dirIn);
        addEntry(dirOut, layout.y);
        addEntry(dirIn, layout.y);
        layout.y += 26;

        layout.y += 8;
        final TextLabel tip = createTextLabel(layout.areaLeft, layout.y, ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.interval_tip"), TextLabel.Align.LEFT, 0xAAAAAA, false);
        addEntry(tip, layout.y);
        layout.y += 12;
        final TextLabel thickTip = createTextLabel(layout.areaLeft, layout.y, ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.thickness_tip"), TextLabel.Align.LEFT, 0xAAAAAA, false);
        addEntry(thickTip, layout.y);
    }

    /** 厚度 1 时方向无效果：方向按钮置灰 */
    private void updateDirActive() {
        dirOut.active = thickness > 1;
        dirIn.active = thickness > 1;
    }

    private static Integer parsePositiveInt(String text) {
        try {
            final int v = Integer.parseInt(text.trim());
            if (v < 1) return null;
            return v;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 单个数值输入行（Double）。
     *
     * @param positive   true = 只接受 > 0 的值
     * @param allowEmpty true = 输入留空时置 null（interval 的「无缝循环」语义）
     */
    private void addDoubleInput(ContentLayout layout, Component label, String initial, java.util.function.Consumer<Double> setter, boolean positive, boolean allowEmpty) {
        final TextLabel lbl = createTextLabel(layout.areaLeft, layout.y, label, TextLabel.Align.LEFT, 0xFFFFFF, false);
        addEntry(lbl, layout.y);
        final EditBox box = new EditBox(this.font, layout.areaLeft + layout.labelWidth, layout.y, layout.fieldWidth, 20, ComponentHelper.empty());
        box.setValue(initial);
        box.setResponder(text -> {
            if (text.trim().isEmpty()) {
                if (allowEmpty) setter.accept(null);
                return; // 非法输入（或不允许空）不更新
            }
            final Double v = parseDouble(text);
            if (v == null) return;
            if (positive && v <= 0) return;
            setter.accept(v);
        });
        this.addRenderableWidget(box);
        addEntry(box, layout.y);
        layout.y += 26;
    }

    private static Double parseDouble(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String formatValue(double value) {
        return String.format("%.4f", value);
    }

    /**
     * 单个正整数（奇数）输入行：矩阵宽/高仅支持奇数（画布中心对齐）。
     */
    private void addOddIntInput(ContentLayout layout, Component label, String initial, java.util.function.Consumer<Integer> setter) {
        final TextLabel lbl = createTextLabel(layout.areaLeft, layout.y, label, TextLabel.Align.LEFT, 0xFFFFFF, false);
        addEntry(lbl, layout.y);
        final EditBox box = new EditBox(this.font, layout.areaLeft + layout.labelWidth, layout.y, layout.fieldWidth, 20, ComponentHelper.empty());
        box.setValue(initial);
        box.setResponder(text -> {
            final Integer v = parseOddInt(text);
            if (v == null) return;
            setter.accept(v);
        });
        this.addRenderableWidget(box);
        addEntry(box, layout.y);
        layout.y += 26;
    }

    private static Integer parseOddInt(String text) {
        try {
            final int v = Integer.parseInt(text.trim());
            if (v < 1 || v % 2 == 0) return null;
            return v;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public void onClose() {
        task.start = start;
        task.interval = interval;
        // 尺寸变更中心对齐搬运已有方块（画布屏 ± 按钮同一入口，所有厚度片一起搬运）
        task.setWidthAndHeight(width, height);
        // 厚度变更扩/缩组：既有组的内容保留、扩组补空矩阵
        task.setThickness(thickness);
        task.thickDirection = thickDirection;
        HybridCreatorScreen.updateTag(tag -> {
            if (tag.contains(HybridCreatorScreen.TAG_TASKS)) {
                tag.getCompound(HybridCreatorScreen.TAG_TASKS).put(key, task.toCompoundTag());
            }
        });
        minecraft.setScreen(parent);
    }

    /**
     * 方向选择按钮（·向外 / ×向里，厚度片延伸方向）：AbstractWidget 子类——1.19.3+
     * Button 构造器私有无法继承，照 BasicConfigScreen.TextLabel 的渲染阶梯模板。
     * 选中（thickDirection == value）画金色边框；厚度 1 时置灰（active=false + 灰色字形）。
     */
    private class DirToggle extends AbstractWidget {

        private final String glyph;
        private final boolean value;

        DirToggle(int x, int y, int width, int height, String glyph, boolean value) {
            //#if MC_VERSION >= 12000
            super(x, y, width, height, Component.empty());
            //#else
            //$$ super(x, y, width, height, ComponentHelper.empty());
            //#endif
            this.glyph = glyph;
            this.value = value;
        }

        //#if MC_VERSION >= 12000
        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderToggle(GraphicContext.of(graphics));
        }
        //#elseif MC_VERSION >= 11904
        //$$ @Override
        //$$ public void renderWidget(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     renderToggle(GraphicContext.of(poseStack));
        //$$ }
        //#elseif MC_VERSION >= 11903
        //$$ @Override
        //$$ public void renderButton(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     renderToggle(GraphicContext.of(poseStack));
        //$$ }
        //#else
        //$$ @Override
        //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     renderToggle(GraphicContext.of(poseStack));
        //$$ }
        //#endif

        private void renderToggle(GraphicContext g) {
            //#if MC_VERSION >= 11903
            final int x = this.getX();
            final int y = this.getY();
            //#else
            //$$ final int x = this.x;
            //$$ final int y = this.y;
            //#endif
            final int w = this.getWidth();
            final int h = this.getHeight();
            final boolean selected = active && thickDirection == value;
            g.fill(x, y, x + w, y + h, selected ? 0xff4a4a4a : 0xff2e2e2e);
            if (selected) {
                // 选中金边
                g.fill(x, y, x + w, y + 1, 0xFFFFD700);
                g.fill(x, y + h - 1, x + w, y + h, 0xFFFFD700);
                g.fill(x, y, x + 1, y + h, 0xFFFFD700);
                g.fill(x + w - 1, y, x + w, y + h, 0xFFFFD700);
            }
            g.drawCenteredString(font, glyph, x + w / 2, y + (h - 8) / 2, active ? 0xFFFFFFFF : 0xFF888888);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!active || button != 0) return false;
            //#if MC_VERSION >= 11903
            final int x = this.getX();
            final int y = this.getY();
            //#else
            //$$ final int x = this.x;
            //$$ final int y = this.y;
            //#endif
            if (mouseX >= x && mouseX <= x + this.getWidth() && mouseY >= y && mouseY <= y + this.getHeight()) {
                thickDirection = value;
                return true;
            }
            return false;
        }

        //#if MC_VERSION >= 11903
        @Override
        protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        }
        //#else
        //$$ @Override
        //$$ public void updateNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        //$$ }
        //#endif
    }
}
