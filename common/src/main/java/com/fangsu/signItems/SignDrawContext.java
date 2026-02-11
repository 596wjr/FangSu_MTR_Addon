package com.fangsu.signItems;

import net.minecraft.client.gui.GuiGraphics;

public record SignDrawContext(
        GuiGraphics graphics,
        float x,
        float y,
        float unit,
        int align,          // 0 左 1 中 2 右
        Object content      // 具体 item 的 content（Map / POJO / Json）
) {
}
