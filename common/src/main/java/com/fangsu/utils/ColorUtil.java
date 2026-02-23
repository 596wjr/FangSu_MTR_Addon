package com.fangsu.utils;

import com.fangsu.Main;

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

    /**
     * 判断颜色是否为亮色
     * 基于亮度计算公式：0.299*R + 0.587*G + 0.114*B
     * 亮度值 > 128 认为是亮色
     *
     * @param color Java AWT Color对象
     * @return 如果是亮色返回true，如果color为null返回true（默认安全值）
     */
    public static boolean isLightColor(Color color) {
        // 处理null值（对应JavaScript的undefined）
        if (color == null) {
            return true; // 默认返回true，与JavaScript保持一致
        }

        // 计算亮度
        double luminance = 0.299 * color.getRed()
                + 0.587 * color.getGreen()
                + 0.114 * color.getBlue();

        // 亮度 > 128 认为是亮色
        return luminance > 128;
    }

    /**
     * 判断颜色是否为亮色（重载方法，使用RGB分量）
     *
     * @param r 红色分量 (0-255)
     * @param g 绿色分量 (0-255)
     * @param b 蓝色分量 (0-255)
     * @return 如果是亮色返回true
     */
    public static boolean isLightColor(int r, int g, int b) {
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        return luminance > 128;
    }

    /**
     * 判断颜色是否为亮色（重载方法，使用十六进制颜色值）
     *
     * @param hexColor 十六进制颜色值，如 "#FF0000" 或 "0xFF0000"
     * @return 如果是亮色返回true，如果输入无效返回false
     */
    public static boolean isLightColor(String hexColor) {
        if (hexColor == null || hexColor.isEmpty()) {
            return false;
        }

        try {
            Color color = Color.decode(hexColor);
            return isLightColor(color);
        } catch (NumberFormatException e) {
            Main.LOGGER.error("[ERROR] 无效的颜色格式: " + hexColor);
            return false;
        }
    }

    public static int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >>> 24) & 0xFF;
        int r1 = (c1 >>> 16) & 0xFF;
        int g1 = (c1 >>> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int a2 = (c2 >>> 24) & 0xFF;
        int r2 = (c2 >>> 16) & 0xFF;
        int g2 = (c2 >>> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
