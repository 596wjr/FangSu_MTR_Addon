package com.fangsu.extraConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 多行文本编辑框包装类，兼�?1.18.2（无 MultiLineEditBox）�?
 * 1.20+ 直接使用 Minecraft 原版�?MultiLineEditBox�?.18.2 使用简易的单行输入框替代�?
 */
public abstract class AbstractMultiLineEditBox extends AbstractWidget {

    protected final Font font;
    protected String value;
    protected java.util.function.Consumer<String> valueListener;

    public AbstractMultiLineEditBox(Font font, int x, int y, int w, int h, Component title) {
        super(x, y, w, h, title);
        this.font = font;
        this.value = "";
    }

    public void setValue(String text) {
        this.value = text != null ? text : "";
    }

    public String getValue() {
        return value;
    }

    public void setValueListener(java.util.function.Consumer<String> listener) {
        this.valueListener = listener;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!this.active || !this.visible) return false;
        if (this.font.width(value) >= this.width - 8) return false;
        value += codePoint;
        if (valueListener != null) valueListener.accept(value);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.active || !this.visible) return false;
        if (keyCode == 259 && !value.isEmpty()) { // Backspace
            value = value.substring(0, value.length() - 1);
            if (valueListener != null) valueListener.accept(value);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    //#if MC_VERSION >= 12000
    @Override
    public void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 绘制背景�?
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000);
        // 绘制文本
        graphics.drawString(font, value, getX() + 4, getY() + (height - 8) / 2, 0xFFFFFF, false);
    }
    //#else
    @Override
    public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        net.minecraft.client.gui.Gui.fill(poseStack, x, y, x + width, y + height, 0xFF000000);
        font.draw(poseStack, value, x + 4, y + (height - 8) / 2, 0xFFFFFF);
    }
    //#endif

    //#if MC_VERSION >= 12000
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
    }
    //#else
    @Override
    public void updateNarration(NarrationElementOutput narration) {
    }
    //#endif
}
