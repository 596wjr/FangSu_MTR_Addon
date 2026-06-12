package com.fangsu.extraConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 澶氳鏂囨湰缂栬緫妗嗗寘瑁呯被锛屽吋锟?1.18.2锛堟棤 MultiLineEditBox锛夛拷?
 * 1.20+ 鐩存帴浣跨敤 Minecraft 鍘熺増锟?MultiLineEditBox锟?.18.2 浣跨敤绠€鏄撶殑鍗曡杈撳叆妗嗘浛浠ｏ拷?
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
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000);
        graphics.drawString(font, value, getX() + 4, getY() + (height - 8) / 2, 0xFFFFFF, false);
    }
    //#elseif MC_VERSION >= 11904
    //$$@Override
    //$$public void renderWidget(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
    //$$    net.minecraft.client.gui.Gui.fill(poseStack, getX(), getY(), getX() + width, getY() + height, 0xFF000000);
    //$$    font.draw(poseStack, value, getX() + 4, getY() + (height - 8) / 2, 0xFFFFFF);
    //$$}
    //#elseif MC_VERSION >= 11903
    //$$@Override
    //$$public void renderButton(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
    //$$    net.minecraft.client.gui.Gui.fill(poseStack, getX(), getY(), getX() + width, getY() + height, 0xFF000000);
    //$$    font.draw(poseStack, value, getX() + 4, getY() + (height - 8) / 2, 0xFFFFFF);
    //$$}
    //#else
    //$$@Override
    //$$public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
    //$$    net.minecraft.client.gui.Gui.fill(poseStack, this.x, this.y, this.x + width, this.y + height, 0xFF000000);
    //$$    font.draw(poseStack, value, this.x + 4, this.y + (height - 8) / 2, 0xFFFFFF);
    //$$}
    //#endif

    //#if MC_VERSION >= 11903
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
    }
    //#else
    //$$ @Override
    //$$ public void updateNarration(NarrationElementOutput narration) {
    //$$ }
    //#endif
}

