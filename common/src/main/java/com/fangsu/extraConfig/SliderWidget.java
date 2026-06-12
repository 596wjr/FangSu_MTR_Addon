package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
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
    /* =================== 瀵瑰 API ========================= */
    /* ====================================================== */

    /**
     * 锟?NumberConfig / 杈撳叆妗嗚皟锟?
     */
    public void setExternal(float v) {
        v = snap(v);
        this.value = v;
        slider.setFromExternal(v);
    }

    public float getValue() {
        return value;
    }

    //#if MC_VERSION >= 11903
    @Override
    public void setY(int y) {
        super.setY(y);
        slider.setY(y);
    }
    //#else
    //$$public void setY(int y) {
    //$$    this.y = y;
    //$$    slider.y = y;
    //$$}
    //#endif


    /* ====================================================== */

    //#if MC_VERSION >= 12000
    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        slider.render(g, mouseX, mouseY, partial);
    }
    //#elseif MC_VERSION >= 11904
    //$$@Override
    //$$public void renderWidget(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partial) {
    //$$    slider.render(poseStack, mouseX, mouseY, partial);
    //$$}
    //#elseif MC_VERSION >= 11903
    //$$@Override
    //$$public void renderButton(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partial) {
    //$$    slider.render(poseStack, mouseX, mouseY, partial);
    //$$}
    //#else
    //$$@Override
    //$$public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partial) {
    //$$    slider.render(poseStack, mouseX, mouseY, partial);
    //$$}
    //#endif

    @Override
    public boolean mouseClicked(double x, double y, int btn) {
        return slider.mouseClicked(x, y, btn);
    }

    @Override
    public boolean mouseDragged(double x, double y, int btn, double dx, double dy) {
        return slider.mouseDragged(x, y, btn, dx, dy);
    }

    //#if MC_VERSION >= 11903
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
    }
    //#else
    //$$@Override
    //$$public void updateNarration(NarrationElementOutput narration) {
    //$$}
    //#endif

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
                    ComponentHelper.translatable(
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
         * 瑕嗙洊 onClick锛屼娇鐐瑰嚮婊戝潡鏃朵篃鍚搁檮锟?step 鐨勬暣鏁板€嶄綅缃拷?
         */
        @Override
        public void onClick(double mouseX, double mouseY) {
            this.setValueFromMouse(mouseX);
        }

        /**
         * 瑕嗙洊 setValueFromMouse锛屼娇榧犳爣鐐瑰嚮璁＄畻鍊兼椂鐩存帴浣跨敤鍚搁檮鍚庣殑鍊硷拷?
         */
        private void setValueFromMouse(double mouseX) {
            //#if MC_VERSION >= 11903
            double raw = (mouseX - (double) (this.getX() + 4)) / (double) (this.width - 8);
            //#else
            //$$ double raw = (mouseX - (double) (this.x + 4)) / (double) (this.width - 8);
            //#endif
            raw = Mth.clamp(raw, 0.0D, 1.0D);
            float snapped = snap(denormalize(raw));
            this.value = normalize(snapped);
            this.applyValue();
        }

        /**
         * 瑕嗙洊榧犳爣鎷栧姩锛氬綋鍏夋爣鍦ㄦ粦鍧楀尯鍩熷唴涓旈紶鏍囨寜涓嬫椂锛岀洿鎺ユ牴鎹紶鏍囦綅缃惛闄勫苟鏇存柊鍊硷拷?
         * 涓嶄緷锟?isFocused() 鍒ゆ柇锛屽洜涓哄锟?SliderWidget 鎵嶆槸 Screen 涓殑鐒︾偣缁勪欢锟?
         */
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (!this.visible || button != 0) {
                return false;
            }
            //#if MC_VERSION >= 11903
            if (mouseX < this.getX() || mouseX > this.getX() + this.width
                    || mouseY < this.getY() || mouseY > this.getY() + this.height) {
                return false;
            }
            //#else
            //$$ if (mouseX < this.x || mouseX > this.x + this.width
            //$$         || mouseY < this.y || mouseY > this.y + this.height) {
            //$$     return false;
            //$$ }
            //#endif
            setValueFromMouse(mouseX);
            return true;
        }

        /**
         * 鍞竴鍏佽锟?AbstractSliderButton.value 鐨勫湴锟?
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
