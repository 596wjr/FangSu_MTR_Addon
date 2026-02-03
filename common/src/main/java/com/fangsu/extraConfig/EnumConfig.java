package com.fangsu.extraConfig;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class EnumConfig extends ConfigEntry<Integer> {

    private final List<? extends Component> entries;

    public EnumConfig(
            Component title,
            ConfigSpec spec,
            List<? extends Component> entries,
            Function<Object, Integer> getter,
            BiConsumer<Object, Integer> setter
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
                }
        ).bounds(x + labelW, y, fieldW, 20).build();

        return new ConfigWidget(
                x, y,
                labelW + fieldW,
                20,
                title,
                btn
        );
    }
}
