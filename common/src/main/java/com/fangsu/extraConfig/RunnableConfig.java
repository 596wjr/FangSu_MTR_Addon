package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class RunnableConfig extends ConfigEntry<Void> {

    private final Runnable action;
    private Component buttonText;

    public RunnableConfig(
            Component title,
            ConfigSpec spec,
            Runnable action
    ) {
        super(
                title,
                spec,
                () -> null,
                v -> {
                }
        );
        this.action = action;
        this.buttonText = title;
    }

    public RunnableConfig setButtonText(Component text) {
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
        Button button = Button.builder(buttonText, btn -> { action.run(); notifyValueChanged(); }).bounds(x + labelWidth, y, fieldWidth, height).build();
        //#else
        //$$ Button button = new Button(x + labelWidth, y, fieldWidth, height, buttonText, btn -> { action.run(); notifyValueChanged(); });
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

    @Override
    public void load(Object be) {
    }

    @Override
    public void save(Object be) {
    }
}

