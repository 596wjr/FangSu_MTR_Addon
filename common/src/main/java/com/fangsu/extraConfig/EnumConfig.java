package com.fangsu.extraConfig;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class EnumConfig extends ConfigEntry<Integer> {

    private final List<? extends Component> entries;

    public EnumConfig(
            Component title,
            ConfigSpec spec,
            List<? extends Component> entries,
            Supplier<Integer> getter,
            Consumer<Integer> setter
    ) {
        super(title, spec, getter, setter);
        this.entries = entries;
    }

    @Override
    public ConfigWidget createWidget(int x, int y, int labelW, int fieldW) {

        Button btn = Button.builder(
                entries.get(value),
                b -> {
                    value = (value + 1) % entries.size();
                    b.setMessage(entries.get(value));
                    notifyValueChanged();
                }
        ).bounds(x + labelW, y, fieldW, 20).build();

        return new ConfigWidget(
                x, y,
                labelW + fieldW,
                20,
                labelW,
                title,
                btn
        );
    }
}
