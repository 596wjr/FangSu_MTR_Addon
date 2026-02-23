package com.fangsu.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ScreenUtil {
    public static int drawStringScale(GuiGraphics graphics,
                                      String str,
                                      int x, int y,
                                      int color,
                                      float scale,
                                      boolean shadow) {

        Font font = Minecraft.getInstance().font;
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        poseStack.translate(x, y, 0);
        poseStack.scale(scale, scale, 1.0f);

        int rawEndX = graphics.drawString(font, str, 0, 0, color, shadow);

        poseStack.popPose();

        return x + Math.round(rawEndX * scale);
    }

    public static int drawStringScale(GuiGraphics graphics, Component component, int x, int y, int color, float scale, boolean shadow) {
        Font font = Minecraft.getInstance().font;
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        poseStack.translate(x, y, 0);
        poseStack.scale(scale, scale, 1.0f);

        int rawEndX = graphics.drawString(font, component, 0, 0, color, shadow);

        poseStack.popPose();
        return x + Math.round(rawEndX * scale);
    }

    public static int drawCenteredStringScale(GuiGraphics graphics,
                                              String str,
                                              int centerX,
                                              int y,
                                              int color,
                                              float scale,
                                              boolean shadow) {

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
    }

    public static int drawCenteredStringScale(GuiGraphics graphics,
                                              Component component,
                                              int centerX,
                                              int y,
                                              int color,
                                              float scale,
                                              boolean shadow) {

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
    }

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
        g.drawString(font, text,
                x + 3 - (int) offset,
                drawY,
                color,
                hovered);
        g.disableScissor();
    }

    /**
     * 使用九宫格（9-slice）方式绘制可拉伸的按钮/面板背景。
     * <p>
     * 四角不缩放，边框单向拉伸，中心区域双向拉伸，
     * 用于在宽高变化时保持圆角和边框不变形。
     * <p>
     * 仅负责背景绘制，不包含文字或交互逻辑。
     * <p>
     * 使用默认大小 (30x30)
     *
     * @param g       当前 {@link GuiGraphics}，用于实际绘制
     * @param texture 九宫格按钮贴图资源
     * @param x       绘制起始 X（GUI 坐标）
     * @param y       绘制起始 Y（GUI 坐标）
     * @param width   目标绘制宽度
     * @param height  目标绘制高度
     */
    public static void drawNineSlice(
            GuiGraphics g,
            ResourceLocation texture,
            int x, int y,
            int width, int height
    ) {
        drawNineSlice(g, texture, x, y, width, height, 4, 30, 30);
    }

    /**
     * 使用九宫格（9-slice）方式绘制可拉伸的按钮/面板背景。
     * <p>
     * 四角不缩放，边框单向拉伸，中心区域双向拉伸，
     * 用于在宽高变化时保持圆角和边框不变形。
     * <p>
     * 仅负责背景绘制，不包含文字或交互逻辑。
     *
     * @param g       当前 {@link GuiGraphics}，用于实际绘制
     * @param texture 九宫格按钮贴图资源
     * @param x       绘制起始 X（GUI 坐标）
     * @param y       绘制起始 Y（GUI 坐标）
     * @param width   目标绘制宽度
     * @param height  目标绘制高度
     * @param corner  九宫格角尺寸（像素），即不可拉伸的边角大小
     * @param texW    原始贴图宽度（像素）
     * @param texH    原始贴图高度（像素）
     */
    public static void drawNineSlice(
            GuiGraphics g,
            ResourceLocation texture,
            int x, int y,
            int width, int height,
            int corner,
            int texW, int texH
    ) {
        // 四角
        g.blit(texture, x, y,
                0, 0,
                corner, corner,
                texW, texH); // TL

        g.blit(texture, x + width - corner, y,
                texW - corner, 0,
                corner, corner,
                texW, texH); // TR

        g.blit(texture, x, y + height - corner,
                0, texH - corner,
                corner, corner,
                texW, texH); // BL

        g.blit(texture, x + width - corner, y + height - corner,
                texW - corner, texH - corner,
                corner, corner,
                texW, texH); // BR

        // 上边（水平拉伸，源宽度 = texW - 2*corner，目标宽度 = width - 2*corner）
        g.blit(texture, x + corner, y, width - 2 * corner, corner,
                corner, 0, texW - 2 * corner, corner, texW, texH);
        // 下边
        g.blit(texture, x + corner, y + height - corner, width - 2 * corner, corner,
                corner, texH - corner, texW - 2 * corner, corner, texW, texH);

        // 左边（垂直拉伸，源高度 = texH - 2*corner，目标高度 = height - 2*corner）
        g.blit(texture, x, y + corner, corner, height - 2 * corner,
                0, corner, corner, texH - 2 * corner, texW, texH);
        // 右边
        g.blit(texture, x + width - corner, y + corner, corner, height - 2 * corner,
                texW - corner, corner, corner, texH - 2 * corner, texW, texH);

        // 中心（双向拉伸，源尺寸 = (texW-2*corner) x (texH-2*corner)）
        g.blit(texture, x + corner, y + corner, width - 2 * corner, height - 2 * corner,
                corner, corner, texW - 2 * corner, texH - 2 * corner, texW, texH);
    }

    /**
     * 绘制右对齐缩放文本
     *
     * @param graphics GuiGraphics 渲染对象
     * @param str      文本
     * @param rightX   右边界 X 坐标
     * @param y        Y 坐标
     * @param color    文本颜色
     * @param scale    缩放倍数
     * @param shadow   是否绘制阴影
     * @return 最终绘制的右边界 X（与 rightX 相同）
     */
    public static int drawRightAlignedStringScale(GuiGraphics graphics,
                                                  String str,
                                                  int rightX,
                                                  int y,
                                                  int color,
                                                  float scale,
                                                  boolean shadow) {
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
    }

    public static int drawRightAlignedStringScale(GuiGraphics graphics,
                                                  Component component,
                                                  int rightX,
                                                  int y,
                                                  int color,
                                                  float scale,
                                                  boolean shadow) {
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
    }
    
    public static void drawString(GuiGraphics graphics, String str, int x, int y, int color, int height, boolean shadow) {
        Font font = Minecraft.getInstance().font;
        int fontHeight = font.lineHeight;
        float scale = (float) height / fontHeight;
        drawStringScale(graphics, str, x, y, color, scale, shadow);
    }
}
