package com.fangsu.extraConfig;

import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class StringConfig extends ConfigEntry<String> {

    private MultiLineTextWidget widget;

    public StringConfig(
            Component title,
            ConfigSpec spec,
            Supplier<String> getter,
            Consumer<String> setter
    ) {
        super(title, spec, getter, setter);
    }

    @Override
    public ConfigWidget createWidget(int x, int y, int labelW, int fieldW) {

        boolean multiline = spec.getBool("multiline", false);
        int lines = Math.max(1, spec.getInt("lines", 1));
        int height = multiline ? lines * 12 + 4 : 20;

        widget = new MultiLineTextWidget(
                x + labelW,
                y,
                fieldW,
                height,
                value,
                text -> {
                    value = text;
                    notifyValueChanged();
                }
        );

        return new ConfigWidget(
                x, y,
                labelW + fieldW,
                height,
                labelW,
                title,
                widget
        );
    }

    @Override
    public void save(Object be) {
        if (widget != null)
            value = widget.getText();
        super.save(be);
    }
}
