package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 仅作为 UI 控件，不负责保存
 */
//#if MC_VERSION >= 12000
public class MultiLineTextWidget extends net.minecraft.client.gui.components.MultiLineEditBox {
//#else
//$$public class MultiLineTextWidget extends AbstractMultiLineEditBox {
//#endif

    public MultiLineTextWidget(
            int x, int y, int w, int h,
            String initial,
            java.util.function.Consumer<String> onChanged
    ) {
        //#if MC_VERSION >= 12000
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
        //#else
        //$$ super(Minecraft.getInstance().font, x, y, w, h, ComponentHelper.empty());
        //$$ this.setValue(initial);
        //$$ if (onChanged != null) {
        //$$     this.setValueListener(onChanged);
        //$$ }
        //#endif
    }

    /**
     * UI 关闭时由 ConfigEntry 主动读取
     */
    public String getText() {
        return this.getValue();
    }
}
