package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 简单确认对话框：消息文本 + [确认] [取消] 两个按钮。
 * onConfirm（确认）/ onCancel（取消按钮）回调自行负责后续流程（继续导入导出或返回）；
 * 右上角 X（onClose）视为完全取消，直接返回 parent 屏。
 */
public class HybridConfirmScreen extends BasicConfigScreen {

    private final Screen parent;
    private final Component message;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public HybridConfirmScreen(Screen parent, Component message, Runnable onConfirm, Runnable onCancel) {
        super(ComponentHelper.translatable("ui.fangsu.hybrid_creator.confirm.title"));
        this.parent = parent;
        this.message = message;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        layout.y += 8;
        addEntry(createTextLabel(layout.areaLeft, layout.y, message, TextLabel.Align.LEFT, 0xFFFFFF, false), layout.y);
        layout.y += 26;
        // [确认] [取消]（各 1/4 宽，右侧并排）
        addEntry(addButton(layout.areaLeft + layout.contentWidth / 2 + 4, layout.y, layout.contentWidth / 4 - 4, 20,
                ComponentHelper.translatable("ui.fangsu.block.confirm"), button -> onConfirm.run()), layout.y);
        addEntry(addButton(layout.areaLeft + layout.contentWidth * 3 / 4 + 4, layout.y, layout.contentWidth / 4 - 4, 20,
                ComponentHelper.translatable("ui.fangsu.block.cancel"), button -> onCancel.run()), layout.y);
    }

    @Override
    public void onClose() {
        // X = 完全取消，返回 parent（不走 onCancel——「否」语义是继续流程）
        minecraft.setScreen(parent);
    }
}
