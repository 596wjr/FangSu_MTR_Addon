package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class BoolConfig extends ConfigEntry<Boolean> {

    public BoolConfig(
            Component title,
            ConfigSpec spec,
            Supplier<Boolean> getter,
            Consumer<Boolean> setter
    ) {
        super(title, spec, getter, setter);
    }

    @Override
    public ConfigWidget createWidget(int x, int y, int labelW, int fieldW) {

        //#if MC_VERSION >= 11903
        Button btn = Button.builder(
                label(), b -> { value = !value; b.setMessage(label()); notifyValueChanged(); }
        ).bounds(x + labelW, y, fieldW, 20).build();
        //#else
        //$$ Button btn = new Button(x + labelW, y, fieldW, 20, label(), b -> { value = !value; b.setMessage(label()); notifyValueChanged(); });
        //#endif

        return new ConfigWidget(x, y, labelW + fieldW, 20, labelW, title, btn);
    }

    private Component label() {
        return ComponentHelper.translatable(value
                ? "ui.fangsu.common.on"
                : "ui.fangsu.common.off");
    }
}

