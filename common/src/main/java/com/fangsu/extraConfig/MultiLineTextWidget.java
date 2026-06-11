package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 浠呬綔涓?UI 鎺т欢锛屼笉璐熻矗淇濆瓨
 */
//#if MC_VERSION >= 11903
public class MultiLineTextWidget extends net.minecraft.client.gui.components.MultiLineEditBox {
//#else
//$$public class MultiLineTextWidget extends AbstractMultiLineEditBox {
//#endif

    public MultiLineTextWidget(
            int x, int y, int w, int h,
            String initial,
            java.util.function.Consumer<String> onChanged
    ) {
        //#if MC_VERSION >= 11903
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
     * UI 鍏抽棴鏃剁敱 ConfigEntry 涓诲姩璇诲彇
     */
    public String getText() {
        return this.getValue();
    }
}

