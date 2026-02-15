package com.fangsu.scripting;

import com.fangsu.Main;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;

public class G2dTextHelper {
    public static int getMultiLinesWidth(Graphics2D g, Font font, float h, String... lines) {
        int width = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int fontSize = (int) (h * 0.9 / (lines.length + 2) * (i == 0 ? 3 : 1));
            g.setFont(font.deriveFont(Font.PLAIN, fontSize));
            width = Math.max(width, g.getFontMetrics().stringWidth(line));
        }
        return width;
    }

    public static int drawStrMultiLines(Graphics2D g, Font font, int x, int y, int h, int align, String... lines) {
        int width = getMultiLinesWidth(g, font, h, lines);
        int currentY = (int) (y - h * 0.095);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int fontSize = (int) (h * 0.9 / (lines.length + 2) * (i == 0 ? 3 : 1));
            currentY += fontSize;
            g.setFont(font.deriveFont(Font.PLAIN, fontSize));
            int lineWidth = g.getFontMetrics().stringWidth(line);
            int baseX = align == 0 ? x :
                    align == 1 ? x + width / 2 - lineWidth / 2 :
                            x + width - lineWidth;
            g.drawString(line, baseX, currentY);
        }
        return width;
    }

    /**
     * 绘制单行文字（统一字体，不分语言）
     *
     * @param g     Java AWT绘图上下文对象
     * @param font  统一使用的字体对象
     * @param str   要绘制的原始字符串
     * @param x     基准点X坐标（根据对齐方式计算实际绘制起点）
     * @param y     基准点Y坐标（字符串底部基线位置）
     * @param h     字体高度（直接作为字体大小）
     * @param align 水平对齐方式：
     *              - 0: 左对齐（基准点为左侧）
     *              - 1: 居中对齐（基准点为水平中心）
     *              - 2: 右对齐（基准点为右侧）
     * @return 实际绘制的总宽度（像素）
     */
    public static int drawStrUnified(Graphics2D g, Font font, String str, int x, int y, float h, int align) {
        Font drawFont = font.deriveFont(Font.PLAIN, h);
        g.setFont(drawFont);

        // 获取字体度量
        FontRenderContext frc = g.getFontRenderContext();
        Rectangle2D bounds = drawFont.getStringBounds(str, frc);
        int strWidth = (int) Math.ceil(bounds.getWidth());

        int drawX = x;
        switch (align) {
            case 0: // 左对齐
                // 无需调整
                break;
            case 1: // 居中对齐
                drawX = (int) (x - strWidth / 2.0);
                break;
            case 2: // 右对齐
                drawX = x - strWidth;
                break;
            default:
                Main.LOGGER.error("无效的对齐参数 align={}", align);
                return 0;
        }

        // 绘制文字
        g.drawString(str, drawX, y);

        return strWidth;
    }

    /**
     * 获取字符串的宽度
     *
     * @param g    Java AWT绘图上下文对象
     * @param font 字体对象
     * @param str  要测量的字符串
     * @param h    字体高度（直接作为字体大小）
     * @return 字符串的像素宽度
     */
    public static int getUnifiedStringWidth(Graphics2D g, Font font, String str, float h) {
        Font drawFont = font.deriveFont(Font.PLAIN, h);

        // 获取字体度量
        FontRenderContext frc = g.getFontRenderContext();
        Rectangle2D bounds = drawFont.getStringBounds(str, frc);

        return (int) Math.ceil(bounds.getWidth());
    }
}
