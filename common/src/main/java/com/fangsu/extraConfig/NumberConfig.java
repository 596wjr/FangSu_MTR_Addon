package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class NumberConfig extends ConfigEntry<Float> {

    private final float min;
    private final float max;
    private final float step;

    private SliderWidget slider;
    private EditBox input;

    public NumberConfig(
            Component title,
            ConfigSpec spec,
            Supplier<Float> getter,
            Consumer<Float> setter
    ) {
        super(title, spec, getter, setter);
        this.min = spec.getFloat("min", 0f);
        this.max = spec.getFloat("max", 1f);
        this.step = spec.getFloat("step", 0f);
    }

    @Override
    public ConfigWidget createWidget(int x, int y, int labelW, int fieldW) {

        int toggleW = 20;
        int fieldAreaW = fieldW - toggleW - 4;

        /* ---------- Slider ---------- */

        slider = new SliderWidget(
                x + labelW,
                y,
                fieldAreaW,
                20,
                title,
                value,
                min,
                max,
                step,
                v -> {
                    value = v;
                    notifyValueChanged();
                }
        );

        /* ---------- Input ---------- */

        input = new EditBox(
                Minecraft.getInstance().font,
                x + labelW,
                y,
                fieldAreaW,
                20,
                title
        );
        input.setValue(Float.toString(value));
        input.setResponder(str -> {
            try {
                float v = Float.parseFloat(str);
                value = v;
                slider.setExternal(v);
                notifyValueChanged();
            } catch (NumberFormatException ignored) {
            }
        });

        /* ---------- Toggle ---------- */

        //#if MC_VERSION >= 11903
        Button toggle = Button.builder(ComponentHelper.translatable("ui.fangsu.common.toggle_input"), b -> { slider.visible = !slider.visible; input.visible = !input.visible; }).bounds(x + labelW + fieldAreaW + 4, y, toggleW, 20).build();
        //#else
        //$$ Button toggle = new Button(x + labelW + fieldAreaW + 4, y, toggleW, 20, ComponentHelper.translatable("ui.fangsu.common.toggle_input"), b -> { slider.visible = !slider.visible; input.visible = !input.visible; });
        //#endif

        slider.visible = true;
        input.visible = false;

        return new ConfigWidget(
                x,
                y,
                labelW + fieldW,
                20,
                labelW,
                title,
                new CompoundWidget(slider, input, toggle)
        );
    }
}

