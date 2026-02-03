package com.fangsu.extraConfig;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class BoolConfig extends ConfigEntry<Boolean> {

    public BoolConfig(
            Component title,
            ConfigSpec spec,
            Function<Object, Boolean> getter,
            BiConsumer<Object, Boolean> setter
    ) {
        super(title, spec, getter, setter);
    }

    @Override
    public ConfigWidget createWidget(int x, int y, int labelW, int fieldW) {

        Button btn = Button.builder(
                label(),
                b -> {
                    value = !value;
                    b.setMessage(label());
                }
        ).bounds(x + labelW, y, fieldW, 20).build();

        return new ConfigWidget(x, y, labelW + fieldW, 20, title, btn);
    }

    private Component label() {
        return Component.literal(value ? "ON" : "OFF");
    }
}
