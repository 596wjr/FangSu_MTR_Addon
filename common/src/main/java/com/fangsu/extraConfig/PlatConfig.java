package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mappings.LocalComponent;
import com.fangsu.ui.PlatformSelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 站台选择配置项：按键固定为打开站台选择界面。
 * 存储选中站台的 id（Long）。
 */
public class PlatConfig extends ConfigEntry<Long> {

    private Component buttonText;

    public PlatConfig(
            Component title,
            ConfigSpec spec,
            Supplier<Long> getter,
            Consumer<Long> setter
    ) {
        super(title, spec, getter, setter);
        this.buttonText = title;
    }

    public static PlatConfig fromLocal(
            LocalComponent title,
            ConfigSpec spec,
            Supplier<Long> getter,
            Consumer<Long> setter
    ) {
        return new PlatConfig(title.getRaw(), spec, getter, setter);
    }

    public PlatConfig setButtonText(Component text) {
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
            mc.setScreen(new PlatformSelectionScreen(
                    ComponentHelper.translatable("ui.fangsu.common.selectPlat"),
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
