package com.fangsu.ui;

import com.fangsu.data.hybrid.HybridSliceTask;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 切片任务参数配置（起点距离/间隔/切片大小），基于 BasicConfigScreen。
 * <p>
 * <ul>
 *   <li>start：第一个切片的轨道距离</li>
 *   <li>间隔 interval：每隔多少格放置一次切片（间距 = 间隔+1 格）；
 *       默认 0 = 每格无缝铺满；留空 = 只建一块</li>
 *   <li>切片大小 width/height：矩阵宽高（仅奇数，中心对齐搬运方块）</li>
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

    public HybridSliceConfigScreen(HybridSliceTask task, String key, HybridSliceTaskScreen parent) {
        super(ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.title"));
        this.task = task;
        this.key = key;
        this.parent = parent;
        this.start = task.start;
        this.interval = task.interval;
        this.width = task.width;
        this.height = task.height;
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        layout.y += 12;
        addDoubleInput(layout, ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.start_pos"), formatValue(start), v -> start = v, false, false);
        // 间隔：留空 = null（只建一块），由 allowEmpty 在 responder 里置 null；负数不接受（会死循环）
        addDoubleInput(layout, ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.interval"), interval == null ? "" : formatValue(interval), v -> {
            if (v != null && v >= 0) interval = v;
        }, false, true);
        addOddIntInput(layout, ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.width"), String.valueOf(width), v -> width = v);
        addOddIntInput(layout, ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.height"), String.valueOf(height), v -> height = v);

        layout.y += 8;
        final TextLabel tip = createTextLabel(layout.areaLeft, layout.y, ComponentHelper.translatable("ui.fangsu.hybrid_creator.config.interval_tip"), TextLabel.Align.LEFT, 0xAAAAAA, false);
        addEntry(tip, layout.y);
    }

    /**
     * 单个数值输入行（Double）。
     *
     * @param positive   true = 只接受 > 0 的值
     * @param allowEmpty true = 输入留空时置 null（interval 的「只建一块」语义）
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
        // 尺寸变更中心对齐搬运已有方块（画布屏 ± 按钮同一入口）
        task.setWidthAndHeight(width, height);
        HybridCreatorScreen.updateTag(tag -> {
            if (tag.contains(HybridCreatorScreen.TAG_TASKS)) {
                tag.getCompound(HybridCreatorScreen.TAG_TASKS).put(key, task.toCompoundTag());
            }
        });
        minecraft.setScreen(parent);
    }
}
