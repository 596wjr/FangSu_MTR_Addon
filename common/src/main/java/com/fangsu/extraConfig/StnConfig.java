package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mappings.LocalComponent;
import com.fangsu.ui.StationSelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 车站选择配置项：按键固定为打开车站选择界面。
 * 存储选中车站的 id（Long）。
 */
public class StnConfig extends ConfigEntry<Long> {

    private Component buttonText;

    public StnConfig(
            Component title,
            ConfigSpec spec,
            Supplier<Long> getter,
            Consumer<Long> setter
    ) {
        super(title, spec, getter, setter);
        this.buttonText = title;
    }

    public static StnConfig fromLocal(
            LocalComponent title,
            ConfigSpec spec,
            Supplier<Long> getter,
            Consumer<Long> setter
    ) {
        return new StnConfig(title.getRaw(), spec, getter, setter);
    }

    public StnConfig setButtonText(Component text) {
        this.buttonText = text;
        return this;
    }

    @Override
    public ConfigWidget createWidget(
            int x, int y, int labelWidth, int fieldWidth
    ) {
        int height = 20;
        int totalWidth = labelWidth + fieldWidth;

        //#if MC_VERSION >= 11903
        Button button = Button.builder(buttonText, btn -> openSelection()).bounds(x + labelWidth, y, fieldWidth, height).build();
        //#else
        //$$ Button button = new Button(x + labelWidth, y, fieldWidth, height, buttonText, btn -> openSelection());
        //#endif

        return new ConfigWidget(
                x,
                y,
                totalWidth,
                height,
                labelWidth,
                title,
                button
        );
    }

    private void openSelection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new StationSelectionScreen(
                    ComponentHelper.translatable("ui.fangsu.common.selectStn"),
                    value != null ? List.of(value) : List.of(),
                    v -> {
                        if (v != null && !v.isEmpty()) {
                            value = v.get(0);
                            notifyValueChanged();
                        }
                    },
                    mc.player.getOnPos(), 1, mc.screen));
        }
    }
}
