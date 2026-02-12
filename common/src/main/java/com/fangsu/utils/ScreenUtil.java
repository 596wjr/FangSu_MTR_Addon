package com.fangsu.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class ScreenUtil {
    public static void drawStringScale(GuiGraphics graphics, String str, int x, int y, int color, float scale, boolean shadow) {
        Font font = Minecraft.getInstance().font;
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        poseStack.translate(x, y, 0);
        poseStack.scale(scale, scale, 1.0f);

        graphics.drawString(font, str, 0, 0, color, shadow);

        poseStack.popPose();
    }

    public static void drawString(GuiGraphics graphics, String str, int x, int y, int color, int height, boolean shadow) {
        Font font = Minecraft.getInstance().font;
        int fontHeight = font.lineHeight;
        float scale = (float) height / fontHeight;
        drawStringScale(graphics, str, x, y, color, scale, shadow);
    }
}
