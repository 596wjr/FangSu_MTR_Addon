package com.fangsu.extraConfig;

import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class StringConfig extends ConfigEntry<String> {

    private MultiLineTextWidget widget;

    public StringConfig(
            Component title,
            ConfigSpec spec,
            Function<Object, String> getter,
            BiConsumer<Object, String> setter
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
                value
        );

        return new ConfigWidget(
                x, y,
                labelW + fieldW,
                height,
                title,
                widget
        );
    }

    @Override
    public void save(Object be) {
        value = widget.getText();
        super.save(be);
    }
}
