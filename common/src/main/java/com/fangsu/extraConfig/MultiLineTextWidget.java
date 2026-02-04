package com.fangsu.extraConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

/**
 * 仅作为 UI 控件，不负责保存
 */
public class MultiLineTextWidget extends MultiLineEditBox {

    public MultiLineTextWidget(
            int x, int y, int w, int h,
            String initial,
            java.util.function.Consumer<String> onChanged
    ) {
        super(
                Minecraft.getInstance().font,
                x, y, w, h,
                Component.empty(),
                Component.empty()
        );
        this.setValue(initial);
        if (onChanged != null) {
            this.setValueListener(onChanged);
        }
    }

    /** UI 关闭时由 ConfigEntry 主动读取 */
    public String getText() {
        return this.getValue();
    }
}
