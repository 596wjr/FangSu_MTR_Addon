package com.fangsu.extraConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

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
        v = snap(v);
        this.value = v;
        slider.setFromExternal(v);
    }

    public float getValue() {
        return value;
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        slider.setY(y);
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
                    Component.translatable(
                            "ui.fangsu.common.value",
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
         * 覆盖 onClick，使点击滑块时也吸附到 step 的整数倍位置。
         */
        @Override
        public void onClick(double mouseX, double mouseY) {
            this.setValueFromMouse(mouseX);
        }

        /**
         * 覆盖 setValueFromMouse，使鼠标点击计算值时直接使用吸附后的值。
         */
        private void setValueFromMouse(double mouseX) {
            double raw = (mouseX - (double) (this.getX() + 4)) / (double) (this.width - 8);
            raw = Mth.clamp(raw, 0.0D, 1.0D);
            float snapped = snap(denormalize(raw));
            this.value = normalize(snapped);
            this.applyValue();
        }

        /**
         * 覆盖鼠标拖动：当光标在滑块区域内且鼠标按下时，直接根据鼠标位置吸附并更新值。
         * 不依赖 isFocused() 判断，因为外部 SliderWidget 才是 Screen 中的焦点组件。
         */
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (!this.visible || button != 0) {
                return false;
            }
            // 检查鼠标是否在滑块范围内
            if (mouseX < this.getX() || mouseX > this.getX() + this.width
                    || mouseY < this.getY() || mouseY > this.getY() + this.height) {
                return false;
            }
            setValueFromMouse(mouseX);
            return true;
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
        return String.format("%.4f", v);
    }
}
