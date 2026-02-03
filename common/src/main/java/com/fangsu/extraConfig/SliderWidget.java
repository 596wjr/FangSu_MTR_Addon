package com.fangsu.extraConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class SliderWidget extends AbstractWidget {

    private final float min;
    private final float max;
    private final float step;

    private float value;

    private final Consumer<Float> onChanged;

    private final InnerSlider slider;

    public SliderWidget(
            int x, int y, int width, int height,
            Component title,
            float initial,
            float min, float max, float step,
            Consumer<Float> onChanged
    ) {
        super(x, y, width, height, title);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = initial;
        this.onChanged = onChanged;

        slider = new InnerSlider(
                x, y,
                width, height,
                title,
                initial
        );
    }

    /* ====================================================== */
    /* =================== 对外 API ========================= */
    /* ====================================================== */

    /**
     * 供 NumberConfig / 输入框调用
     */
    public void setExternal(float v) {
        v = clamp(v);
        this.value = v;
        slider.setFromExternal(v);
    }

    public float getValue() {
        return value;
    }

    /* ====================================================== */

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        slider.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double x, double y, int btn) {
        return slider.mouseClicked(x, y, btn);
    }

    @Override
    public boolean mouseDragged(double x, double y, int btn, double dx, double dy) {
        return slider.mouseDragged(x, y, btn, dx, dy);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
    }

    /* ====================================================== */
    /* =================== Inner Slider ===================== */
    /* ====================================================== */

    private final class InnerSlider extends AbstractSliderButton {

        InnerSlider(
                int x, int y, int w, int h,
                Component title,
                float initial
        ) {
            super(x, y, w, h, title, normalize(initial));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(
                    Component.literal(
                            format(denormalize(value))
                    )
            );
        }

        @Override
        protected void applyValue() {
            float v = snap(denormalize(value));
            SliderWidget.this.value = v;
            onChanged.accept(v);
            updateMessage();
        }

        /**
         * 唯一允许写 AbstractSliderButton.value 的地方
         */
        void setFromExternal(float v) {
            this.value = normalize(v);
            updateMessage();
        }
    }

    /* ====================================================== */
    /* =================== util ============================= */
    /* ====================================================== */

    private double normalize(float v) {
        return (v - min) / (max - min);
    }

    private float denormalize(double v) {
        return (float) (v * (max - min) + min);
    }

    private float snap(float v) {
        if (step > 0) {
            v = Math.round(v / step) * step;
        }
        return clamp(v);
    }

    private float clamp(float v) {
        return Math.max(min, Math.min(max, v));
    }

    private String format(float v) {
        return String.format("%.3f", v);
    }
}
