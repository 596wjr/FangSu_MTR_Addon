package com.fangsu.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public class GraphicContext {

    //#if MC_VERSION >= 12000
    private final GuiGraphics impl;

    public GraphicContext(GuiGraphics impl) {
        this.impl = impl;
    }

    public static GraphicContext of(GuiGraphics g) {
        return new GraphicContext(g);
    }

    public GuiGraphics asMinecraft() {
        return impl;
    }

    //#else
    //$$ private final PoseStack impl;
    //$$ private final Font font;
    //$$
    //$$ public GraphicContext(PoseStack impl) {
    //$$     this.impl = impl;
    //$$     this.font = Minecraft.getInstance().font;
    //$$ }
    //$$
    //$$ public static GraphicContext of(PoseStack p) {
    //$$     return new GraphicContext(p);
    //$$ }
    //$$
    //$$ public PoseStack asMinecraft() {
    //$$     return impl;
    //$$ }
    //#endif

    /* ==================== drawString ==================== */

    public int drawString(Font font, String str, int x, int y, int color, boolean shadow) {
        //#if MC_VERSION >= 12000
        return impl.drawString(font, str, x, y, color, shadow);
        //#else
        //$$ return font.draw(impl, str, (float) x, (float) y, color);
        //#endif
    }

    public int drawString(Font font, Component component, int x, int y, int color, boolean shadow) {
        //#if MC_VERSION >= 12000
        return impl.drawString(font, component, x, y, color, shadow);
        //#else
        //$$ return font.draw(impl, component, (float) x, (float) y, color);
        //#endif
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        //#if MC_VERSION >= 12000
        return impl.drawString(font, text, x, y, color, shadow);
        //#else
        //$$ return font.draw(impl, text, (float) x, (float) y, color);
        //#endif
    }

    /* ==================== drawCenteredString ==================== */

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        //#if MC_VERSION >= 12000
        impl.drawCenteredString(font, text, x, y, color);
        //#else
        //$$ drawString(font, text.getString(), x - font.width(text) / 2, y, color, false);
        //#endif
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        //#if MC_VERSION >= 12000
        impl.drawCenteredString(font, text, x, y, color);
        //#else
        //$$ drawString(font, text, x - font.width(text) / 2, y, color, false);
        //#endif
    }

    /* ==================== fill ==================== */

    public void fill(int minX, int minY, int maxX, int maxY, int color) {
        //#if MC_VERSION >= 12000
        impl.fill(minX, minY, maxX, maxY, color);
        //#else
        //$$ net.minecraft.client.gui.Gui.fill(impl, minX, minY, maxX, maxY, color);
        //#endif
    }

    /* ==================== blit ==================== */

    public void blit(ResourceLocation texture, int x, int y, int uOffset, int vOffset, int uWidth, int vHeight, int textureWidth, int textureHeight) {
        //#if MC_VERSION >= 12000
        impl.blit(texture, x, y, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
        //#else
        //$$ com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture);
        //$$ net.minecraft.client.gui.Gui.blit(impl, x, y, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
        //#endif
    }

    public void blit(ResourceLocation texture, int x, int y, int width, int height, int uOffset, int vOffset, int uWidth, int vHeight, int textureWidth, int textureHeight) {
        //#if MC_VERSION >= 12000
        impl.blit(texture, x, y, width, height, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
        //#else
        //$$ com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture);
        //$$ net.minecraft.client.gui.Gui.blit(impl, x, y, width, height, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight);
        //#endif
    }

    /* ==================== scissor ==================== */

    public void enableScissor(int minX, int minY, int maxX, int maxY) {
        //#if MC_VERSION >= 12000
        impl.enableScissor(minX, minY, maxX, maxY);
        //#else
        //$$ com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
        //$$ double scale = window.getGuiScale();
        //$$ com.mojang.blaze3d.systems.RenderSystem.enableScissor(
        //$$         (int) (minX * scale),
        //$$         (int) (window.getHeight() - maxY * scale),
        //$$         (int) ((maxX - minX) * scale),
        //$$         (int) ((maxY - minY) * scale)
        //$$ );
        //#endif
    }

    public void disableScissor() {
        //#if MC_VERSION >= 12000
        impl.disableScissor();
        //#else
        //$$ com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        //#endif
    }

    /* ==================== pose ==================== */

    public PoseStack pose() {
        //#if MC_VERSION >= 12000
        return impl.pose();
        //#else
        //$$ return impl;
        //#endif
    }

    /* ==================== renderTooltip ==================== */

    public void renderTooltip(Font font, java.util.List<Component> components, int x, int y) {
        //#if MC_VERSION >= 12000
        impl.renderTooltip(font, components.stream().map(Component::getVisualOrderText).toList(), x, y);
        //#else
        //$$ // renderTooltip not directly available in 1.18.2 utility context
        //#endif
    }

    /* ==================== fillGradient ==================== */

    public void fillGradient(int minX, int minY, int maxX, int maxY, int colorFrom, int colorTo) {
        //#if MC_VERSION >= 12000
        impl.fillGradient(minX, minY, maxX, maxY, colorFrom, colorTo);
        //#else
        //$$ // fillGradient not available as static in 1.18.2
        //#endif
    }
}

