package com.fangsu.utils;

import java.awt.*;

public class ColorUtil {
    /**
     * 将AWT颜色转换为Minecraft颜色整数
     */
    public static int awtColorToMinecraft(Color color) {
        return (color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    }

    /**
     * 将Minecraft颜色整数转换为AWT颜色
     */
    public static Color minecraftColorToAwt(int color) {
        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return new Color(red, green, blue, alpha);
    }
}
