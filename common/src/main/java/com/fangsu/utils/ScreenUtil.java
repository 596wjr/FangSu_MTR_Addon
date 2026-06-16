package com.fangsu.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ScreenUtil {

    //#if MC_VERSION >= 12000
    public static int drawStringScale(GuiGraphics graphics,
                                      //#else
                                      //$$public static int drawStringScale(PoseStack poseStack,
                                      //#endif
                                      String str,
                                      int x, int y,
                                      int color,
                                      float scale,
                                      boolean shadow) {
        //#if MC_VERSION >= 12000
        Font font = Minecraft.getInstance().font;
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(scale, scale, 1.0f);
        int rawEndX = graphics.drawString(font, str, 0, 0, color, shadow);
        poseStack.popPose();
        return x + Math.round(rawEndX * scale);
        //#else
        //$$ Font font = Minecraft.getInstance().font;
        //$$ poseStack.pushPose();
        //$$ poseStack.translate(x, y, 0);
        //$$ poseStack.scale(scale, scale, 1.0f);
        //$$ int rawEndX = font.draw(poseStack, str, 0, 0, color);
        //$$ poseStack.popPose();
        //$$ return x + Math.round(rawEndX * scale);
        //#endif
    }

    //#if MC_VERSION >= 12000
    public static int drawStringScale(GuiGraphics graphics, Component component, int x, int y, int color, float scale, boolean shadow) {
        //#else
        //$$ public static int drawStringScale(PoseStack poseStack, Component component, int x, int y, int color, float scale, boolean shadow) {
        //#endif
        //#if MC_VERSION >= 12000
        Font font = Minecraft.getInstance().font;
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(scale, scale, 1.0f);
        int rawEndX = graphics.drawString(font, component, 0, 0, color, shadow);
        poseStack.popPose();
        return x + Math.round(rawEndX * scale);
        //#else
        //$$ Font font = Minecraft.getInstance().font;
        //$$ poseStack.pushPose();
        //$$ poseStack.translate(x, y, 0);
        //$$ poseStack.scale(scale, scale, 1.0f);
        //$$ int rawEndX = font.draw(poseStack, component, 0, 0, color);
        //$$ poseStack.popPose();
        //$$ return x + Math.round(rawEndX * scale);
        //#endif
    }

    //#if MC_VERSION >= 12000
    public static int drawCenteredStringScale(GuiGraphics graphics,
                                              //#else
                                              //$$ public static int drawCenteredStringScale(PoseStack poseStack,
                                              //#endif
                                              String str,
                                              int centerX,
                                              int y,
                                              int color,
                                              float scale,
                                              boolean shadow) {
        //#if MC_VERSION >= 12000
        Font font = Minecraft.getInstance().font;
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        int rawWidth = font.width(str);
        int startX = centerX - Math.round(rawWidth * scale / 2f);
        poseStack.translate(startX, y, 0);
        poseStack.scale(scale, scale, 1.0f);
        int rawEndX = graphics.drawString(font, str, 0, 0, color, shadow);
        poseStack.popPose();
        return startX + Math.round(rawEndX * scale);
        //#else
        //$$ Font font = Minecraft.getInstance().font;
        //$$ poseStack.pushPose();
        //$$ int rawWidth = font.width(str);
        //$$ int startX = centerX - Math.round(rawWidth * scale / 2f);
        //$$ poseStack.translate(startX, y, 0);
        //$$ poseStack.scale(scale, scale, 1.0f);
        //$$ int rawEndX = font.draw(poseStack, str, 0, 0, color);
        //$$ poseStack.popPose();
        //$$ return startX + Math.round(rawEndX * scale);
        //#endif
    }

    //#if MC_VERSION >= 12000
    public static int drawCenteredStringScale(GuiGraphics graphics,
                                              //#else
                                              //$$ public static int drawCenteredStringScale(PoseStack poseStack,
                                              //#endif
                                              Component component,
                                              int centerX,
                                              int y,
                                              int color,
                                              float scale,
                                              boolean shadow) {
        //#if MC_VERSION >= 12000
        Font font = Minecraft.getInstance().font;
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        int rawWidth = font.width(component);
        int startX = centerX - Math.round(rawWidth * scale / 2f);
        poseStack.translate(startX, y, 0);
        poseStack.scale(scale, scale, 1.0f);
        int rawEndX = graphics.drawString(font, component, 0, 0, color, shadow);
        poseStack.popPose();
        return startX + Math.round(rawEndX * scale);
        //#else
        //$$ Font font = Minecraft.getInstance().font;
        //$$ poseStack.pushPose();
        //$$ int rawWidth = font.width(component);
        //$$ int startX = centerX - Math.round(rawWidth * scale / 2f);
        //$$ poseStack.translate(startX, y, 0);
        //$$ poseStack.scale(scale, scale, 1.0f);
        //$$ int rawEndX = font.draw(poseStack, component, 0, 0, color);
        //$$ poseStack.popPose();
        //$$ return startX + Math.round(rawEndX * scale);
        //#endif
    }

    //#if MC_VERSION >= 12000
    public static void drawScrollingText(
            GuiGraphics g,
            Font font,
            Component text,
            int x, int y,
            int width, int height,
            int color,
            boolean hovered
    ) {
        int textWidth = font.width(text);
        int available = width - 6;
        int drawX = x + width / 2;
        int drawY = y + (height - 8) / 2;
        if (textWidth <= available) {
            g.drawCenteredString(font, text, drawX, drawY, color);
            return;
        }
        float offset = 0;
        if (hovered) {
            float time = Util.getMillis() / 1000f;
            float overflow = textWidth - available;
            offset = (time * 20) % (overflow + 20) - 10;
            if (offset < 0) offset = 0;
        }
        g.enableScissor(x + 3, y, x + width - 3, y + height);
        g.drawString(font, text, x + 3 - (int) offset, drawY, color, hovered);
        g.disableScissor();
    }
    //#else
    //$$public static void drawScrollingText(
    //$$        PoseStack poseStack,
    //$$        Font font,
    //$$        Component text,
    //$$        int x, int y,
    //$$        int width, int height,
    //$$        int color,
    //$$        boolean hovered
    //$$) {
    //$$    int textWidth = font.width(text);
    //$$    int available = width - 6;
    //$$    int drawX = x + width / 2;
    //$$    int drawY = y + (height - 8) / 2;
    //$$    if (textWidth <= available) {
    //$$        font.draw(poseStack, text, drawX - font.width(text) / 2f, drawY, color);
    //$$        return;
    //$$    }
    //$$    float offset = 0;
    //$$    if (hovered) {
    //$$        float time = Util.getMillis() / 1000f;
    //$$        float overflow = textWidth - available;
    //$$        offset = (time * 20) % (overflow + 20) - 10;
    //$$        if (offset < 0) offset = 0;
    //$$    }
    //$$    com.mojang.blaze3d.systems.RenderSystem.enableScissor(
    //$$            (int) (x + 3), (int) y, (int) (width - 6), (int) height);
    //$$    font.draw(poseStack, text, x + 3 - offset, drawY, color);
    //$$    com.mojang.blaze3d.systems.RenderSystem.disableScissor();
    //$$}
    //#endif

    /**
     * 使用九宫格（9-slice）方式绘制可拉伸的按�?面板背景�?
     * 使用默认大小 (30x30)
     */
    //#if MC_VERSION >= 12000
    public static void drawNineSlice(
            GuiGraphics g,
            ResourceLocation texture,
            int x, int y,
            int width, int height
    ) {
        drawNineSliceImpl(g, texture, x, y, width, height, 4, 30, 30);
    }

    public static void drawNineSlice(
            GuiGraphics g,
            ResourceLocation texture,
            int x, int y,
            int width, int height,
            int corner,
            int texW, int texH
    ) {
        drawNineSliceImpl(g, texture, x, y, width, height, corner, texW, texH);
    }

    private static void drawNineSliceImpl(GuiGraphics g, ResourceLocation texture,
                                          int x, int y, int width, int height,
                                          int corner, int texW, int texH) {
        g.blit(texture, x, y, 0, 0, corner, corner, texW, texH);
        g.blit(texture, x + width - corner, y, texW - corner, 0, corner, corner, texW, texH);
        g.blit(texture, x, y + height - corner, 0, texH - corner, corner, corner, texW, texH);
        g.blit(texture, x + width - corner, y + height - corner, texW - corner, texH - corner, corner, corner, texW, texH);
        g.blit(texture, x + corner, y, width - 2 * corner, corner, corner, 0, texW - 2 * corner, corner, texW, texH);
        g.blit(texture, x + corner, y + height - corner, width - 2 * corner, corner, corner, texH - corner, texW - 2 * corner, corner, texW, texH);
        g.blit(texture, x, y + corner, corner, height - 2 * corner, 0, corner, corner, texH - 2 * corner, texW, texH);
        g.blit(texture, x + width - corner, y + corner, corner, height - 2 * corner, texW - corner, corner, corner, texH - 2 * corner, texW, texH);
        g.blit(texture, x + corner, y + corner, width - 2 * corner, height - 2 * corner, corner, corner, texW - 2 * corner, texH - 2 * corner, texW, texH);
    }
//#else
//$$    public static void drawNineSlice(
//$$            PoseStack poseStack,
//$$            ResourceLocation texture,
//$$            int x, int y,
//$$            int width, int height
//$$    ) {
//$$        drawNineSliceImpl(poseStack, texture, x, y, width, height, 4, 30, 30);
//$$    }
//$$
//$$    public static void drawNineSlice(
//$$            PoseStack poseStack,
//$$            ResourceLocation texture,
//$$            int x, int y,
//$$            int width, int height,
//$$            int corner,
//$$            int texW, int texH
//$$    ) {
//$$        drawNineSliceImpl(poseStack, texture, x, y, width, height, corner, texW, texH);
//$$    }
//$$
//$$    private static void drawNineSliceImpl(PoseStack poseStack, ResourceLocation texture,
//$$                                          int x, int y, int width, int height,
//$$                                          int corner, int texW, int texH) {
//$$        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture);
//$$        net.minecraft.client.gui.Gui.blit(poseStack, x, y, 0, 0, corner, corner, texW, texH);
//$$        net.minecraft.client.gui.Gui.blit(poseStack, x + width - corner, y, texW - corner, 0, corner, corner, texW, texH);
//$$        net.minecraft.client.gui.Gui.blit(poseStack, x, y + height - corner, 0, texH - corner, corner, corner, texW, texH);
//$$        net.minecraft.client.gui.Gui.blit(poseStack, x + width - corner, y + height - corner, texW - corner, texH - corner, corner, corner, texW, texH);
//$$        net.minecraft.client.gui.Gui.blit(poseStack, x + corner, y, width - 2 * corner, corner, corner, 0, texW - 2 * corner, corner, texW, texH);
//$$        net.minecraft.client.gui.Gui.blit(poseStack, x + corner, y + height - corner, width - 2 * corner, corner, corner, texH - corner, texW - 2 * corner, corner, texW, texH);
//$$        net.minecraft.client.gui.Gui.blit(poseStack, x, y + corner, corner, height - 2 * corner, 0, corner, corner, texH - 2 * corner, texW, texH);
//$$        net.minecraft.client.gui.Gui.blit(poseStack, x + width - corner, y + corner, corner, height - 2 * corner, texW - corner, corner, corner, texH - 2 * corner, texW, texH);
//$$        net.minecraft.client.gui.Gui.blit(poseStack, x + corner, y + corner, width - 2 * corner, height - 2 * corner, corner, corner, texW - 2 * corner, texH - 2 * corner, texW, texH);
//$$    }
//#endif

    //#if MC_VERSION >= 12000
    public static int drawRightAlignedStringScale(GuiGraphics graphics,
                                                  //#else
                                                  //$$ public static int drawRightAlignedStringScale(PoseStack poseStack,
                                                  //#endif
                                                  String str,
                                                  int rightX,
                                                  int y,
                                                  int color,
                                                  float scale,
                                                  boolean shadow) {
        //#if MC_VERSION >= 12000
        Font font = Minecraft.getInstance().font;
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        int rawWidth = font.width(str);
        int startX = rightX - Math.round(rawWidth * scale);
        poseStack.translate(startX, y, 0);
        poseStack.scale(scale, scale, 1.0f);
        graphics.drawString(font, str, 0, 0, color, shadow);
        poseStack.popPose();
        return rightX;
        //#else
        //$$ Font font = Minecraft.getInstance().font;
        //$$ poseStack.pushPose();
        //$$ int rawWidth = font.width(str);
        //$$ int startX = rightX - Math.round(rawWidth * scale);
        //$$ poseStack.translate(startX, y, 0);
        //$$ poseStack.scale(scale, scale, 1.0f);
        //$$ font.draw(poseStack, str, 0, 0, color);
        //$$ poseStack.popPose();
        //$$ return rightX;
        //#endif
    }

    //#if MC_VERSION >= 12000
    public static int drawRightAlignedStringScale(GuiGraphics graphics,
                                                  //#else
                                                  //$$ public static int drawRightAlignedStringScale(PoseStack poseStack,
                                                  //#endif
                                                  Component component,
                                                  int rightX,
                                                  int y,
                                                  int color,
                                                  float scale,
                                                  boolean shadow) {
        //#if MC_VERSION >= 12000
        Font font = Minecraft.getInstance().font;
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        int rawWidth = font.width(component);
        int startX = rightX - Math.round(rawWidth * scale);
        poseStack.translate(startX, y, 0);
        poseStack.scale(scale, scale, 1.0f);
        graphics.drawString(font, component, 0, 0, color, shadow);
        poseStack.popPose();
        return rightX;
        //#else
        //$$ Font font = Minecraft.getInstance().font;
        //$$ poseStack.pushPose();
        //$$ int rawWidth = font.width(component);
        //$$ int startX = rightX - Math.round(rawWidth * scale);
        //$$ poseStack.translate(startX, y, 0);
        //$$ poseStack.scale(scale, scale, 1.0f);
        //$$ font.draw(poseStack, component, 0, 0, color);
        //$$ poseStack.popPose();
        //$$ return rightX;
        //#endif
    }

    //#if MC_VERSION >= 12000
    public static void drawString(GuiGraphics graphics, String str, int x, int y, int color, int height, boolean shadow) {
        //#else
        //$$ public static void drawString(PoseStack poseStack, String str, int x, int y, int color, int height, boolean shadow) {
        //#endif
        //#if MC_VERSION >= 12000
        Font font = Minecraft.getInstance().font;
        int fontHeight = font.lineHeight;
        float scale = (float) height / fontHeight;
        drawStringScale(graphics, str, x, y, color, scale, shadow);
        //#else
        //$$ Font font = Minecraft.getInstance().font;
        //$$ int fontHeight = font.lineHeight;
        //$$ float scale = (float) height / fontHeight;
        //$$ drawStringScale(poseStack, str, x, y, color, scale, shadow);
        //#endif
    }
}

