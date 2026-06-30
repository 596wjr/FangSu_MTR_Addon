package com.fangsu.scripting;

import com.fangsu.Main;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

public class G2dTextHelper {

    public static final double ACTUAL_DRAW_HEIGHT = 0.9;

    public static int getMultiLinesWidth(Graphics2D g, Font cjkFont, Font nonCjkFont, float h, String... lines) {
        if (lines == null || lines.length == 0) return 0;
        int width = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int fontSize = (int) (h * ACTUAL_DRAW_HEIGHT / (lines.length + 2) * (i == 0 ? 3 : 1));
            g.setFont((TextUtil.isCjk(line) ? cjkFont : nonCjkFont).deriveFont(Font.PLAIN, fontSize));
            width = Math.max(width, g.getFontMetrics().stringWidth(line));
        }
        return width;
    }

    public static int getMultiLinesWidth(Graphics2D g, Font font, float h, String... lines) {
        return getMultiLinesWidth(g, font, font, h, lines);
    }

    public static int drawStrMultiLines(Graphics2D g, Font cjkFont, Font nonCjkFont, int x, int y, int h, int align, String... lines) {
        if (lines.length == 0) return 0;
        int width = getMultiLinesWidth(g, cjkFont, nonCjkFont, h, lines);
        int currentY = (int) (y + h - h * (0.095));
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int fontSize = (int) (h * ACTUAL_DRAW_HEIGHT / (lines.length + 2) * (i == 0 ? 3 : 1));
            int lineGap = lines.length == 1 ? 0 : (int) (h * 0.1 / (lines.length - 1));
            currentY += fontSize + lineGap;
            if (TextUtil.isCjk(line))
                g.setFont(cjkFont.deriveFont(Font.PLAIN, fontSize));
            else
                g.setFont(nonCjkFont.deriveFont(Font.PLAIN, fontSize));
            int lineWidth = g.getFontMetrics().stringWidth(line);
            int baseX = align == 0 ? x :
                    align == 1 ? x + width / 2 - lineWidth / 2 :
                            x + width - lineWidth;
            g.drawString(line, baseX, currentY);
        }
        return width;
    }

    public static int drawStrMultiLines(Graphics2D g, Font cjkFont, Font nonCjkFont, int x, int y, int h, int allAlign, int align, String... lines) {
        int width = G2dTextHelper.getMultiLinesWidth(g, cjkFont, nonCjkFont, (float) h, lines);
        return switch (allAlign) {
            case 1 -> drawStrMultiLines(g, cjkFont, nonCjkFont, x - width / 2, y - h * 2, h, align, lines);
            case 2 -> drawStrMultiLines(g, cjkFont, nonCjkFont, x - width, y - h * 2, h, align, lines);
            default -> drawStrMultiLines(g, cjkFont, nonCjkFont, x, y - h * 2, h, align, lines);
        };
    }

    public static int drawStrMultiLines(Graphics2D g, Font font, int x, int y, int h, int align, String... lines) {
        return drawStrMultiLines(g, font, font, x, y, h, align, lines);
    }

    /**
     * 绘制单行文字(JS移植)
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
        if (str == null || str.isEmpty()) return 0;
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

    public static int drawStrUnified(Graphics2D g, Font font, String str, double x, double y, double h, int align) {
        return drawStrUnified(g, font, str, (int) x, (int) y, (float) h, align);
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
        if (str == null || str.isEmpty()) return 0;
        Font drawFont = font.deriveFont(Font.PLAIN, h);

        // 获取字体度量
        FontRenderContext frc = g.getFontRenderContext();
        Rectangle2D bounds = drawFont.getStringBounds(str, frc);

        return (int) Math.ceil(bounds.getWidth());
    }

    /**
     * 绘制单行文字，当宽度超过最大宽度时进行水平拉伸（缩放）以适应最大宽度
     *
     * @param g        Java AWT绘图上下文对象
     * @param font     字体对象
     * @param str      要绘制的原始字符串
     * @param x        基准点X坐标（根据对齐方式计算实际绘制起点）
     * @param y        基准点Y坐标（字符串底部基线位置）
     * @param h        字体高度（直接作为字体大小）
     * @param maxWidth 最大宽度，当文字超过此宽度时进行水平缩放
     * @param align    水平对齐方式：0-左对齐，1-居中对齐，2-右对齐
     * @return 实际绘制的总宽度（像素），经过缩放后的宽度
     */
    public static int drawStrUnifiedWithStretch(Graphics2D g, Font font, String str, int x, int y, float h, int maxWidth, int align) {
        if (str == null || str.isEmpty()) return 0;
        Font drawFont = font.deriveFont(Font.PLAIN, h);

        // 获取字符串实际宽度
        FontRenderContext frc = g.getFontRenderContext();
        Rectangle2D bounds = drawFont.getStringBounds(str, frc);
        int strWidth = (int) Math.ceil(bounds.getWidth());

        if (strWidth <= maxWidth) {
            // 不超过最大宽度，普通绘制
            return drawStrUnified(g, font, str, x, y, h, align);
        }

        // 计算水平缩放比例，使绘制后的宽度等于 maxWidth
        double scaleX = (double) maxWidth / strWidth;

        // 保存原始变换
        AffineTransform oldTransform = g.getTransform();

        g.setFont(drawFont);

        int drawX = x;
        switch (align) {
            case 0: // 左对齐
                break;
            case 1: // 居中对齐
                drawX = (int) (x - maxWidth / 2.0);
                break;
            case 2: // 右对齐
                drawX = x - maxWidth;
                break;
            default:
                Main.LOGGER.error("无效的对齐参数 align={}", align);
                return 0;
        }

        // 应用水平缩放变换（以drawX为原点缩放）
        AffineTransform tx = new AffineTransform();
        tx.translate(drawX, y);
        tx.scale(scaleX, 1.0);
        tx.translate(-drawX, -y);
        g.transform(tx);

        g.drawString(str, drawX, y);

        // 恢复原始变换
        g.setTransform(oldTransform);

        return maxWidth;
    }

    public static int drawStrUnifiedWithStretch(Graphics2D g, Font font, String str, double x, double y, double h, int maxWidth, int align) {
        return drawStrUnifiedWithStretch(g, font, str, (int) x, (int) y, (float) h, maxWidth, align);
    }

    /**
     * 绘制多行文字，每行独立计算，当某行宽度超过最大宽度时进行水平拉伸以适应最大宽度
     *
     * @param g        Java AWT绘图上下文对象
     * @param cjkFont  中文字体
     * @param nonCjkFont 非中文字体
     * @param x        基准点X坐标
     * @param y        基准点Y坐标
     * @param h        总高度
     * @param maxWidth 最大宽度，每行超过此宽度时进行水平缩放
     * @param align    水平对齐方式：0-左对齐，1-居中对齐，2-右对齐
     * @param lines    要绘制的多行文字
     * @return 实际绘制的总宽度（像素）
     */
    public static int drawStrMultiLinesWithStretch(Graphics2D g, Font cjkFont, Font nonCjkFont, int x, int y, int h, int maxWidth, int align, String... lines) {
        if (lines.length == 0) return 0;
        int width = Math.min(getMultiLinesWidth(g, cjkFont, nonCjkFont, h, lines), maxWidth);
        int currentY = (int) (y + h - h * (0.095));
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int fontSize = (int) (h * ACTUAL_DRAW_HEIGHT / (lines.length + 2) * (i == 0 ? 3 : 1));
            int lineGap = lines.length == 1 ? 0 : (int) (h * 0.1 / (lines.length - 1));
            currentY += fontSize + lineGap;
            Font lineFont = (TextUtil.isCjk(line) ? cjkFont : nonCjkFont).deriveFont(Font.PLAIN, fontSize);
            g.setFont(lineFont);

            // 获取该行实际宽度
            FontRenderContext frc = g.getFontRenderContext();
            Rectangle2D bounds = lineFont.getStringBounds(line, frc);
            int lineWidth = (int) Math.ceil(bounds.getWidth());

            // 判断是否需要水平缩放
            boolean needStretch = maxWidth > 0 && lineWidth > maxWidth;
            int effectiveWidth = needStretch ? maxWidth : lineWidth;

            int baseX = align == 0 ? x :
                    align == 1 ? x + width / 2 - effectiveWidth / 2 :
                            x + width - effectiveWidth;

            if (needStretch) {
                double scaleX = (double) maxWidth / lineWidth;
                AffineTransform oldTransform = g.getTransform();
                AffineTransform tx = new AffineTransform();
                tx.translate(baseX, currentY);
                tx.scale(scaleX, 1.0);
                tx.translate(-baseX, -currentY);
                g.transform(tx);
                g.drawString(line, baseX, currentY);
                g.setTransform(oldTransform);
            } else {
                g.drawString(line, baseX, currentY);
            }
        }
        return width;
    }

    public static int drawStrMultiLinesWithStretch(Graphics2D g, Font font, int x, int y, int h, int maxWidth, int align, String... lines) {
        return drawStrMultiLinesWithStretch(g, font, font, x, y, h, maxWidth, align, lines);
    }

    public static int drawStrMultiLinesWithStretch(Graphics2D g, Font cjkFont, Font nonCjkFont, int x, int y, int h, int maxWidth, int allAlign, int align, String... lines) {
        int width = Math.min(G2dTextHelper.getMultiLinesWidth(g, cjkFont, nonCjkFont, (float) h, lines), maxWidth);
        return switch (allAlign) {
            case 1 -> drawStrMultiLinesWithStretch(g, cjkFont, nonCjkFont, x - width / 2, y - h * 2, h, maxWidth, align, lines);
            case 2 -> drawStrMultiLinesWithStretch(g, cjkFont, nonCjkFont, x - width, y - h * 2, h, maxWidth, align, lines);
            default -> drawStrMultiLinesWithStretch(g, cjkFont, nonCjkFont, x, y - h * 2, h, maxWidth, align, lines);
        };
    }

    public static int drawStrMultiLinesWithStretch(Graphics2D g, Font font, int x, int y, int h, int maxWidth, int allAlign, int align, String... lines) {
        return drawStrMultiLinesWithStretch(g, font, font, x, y, h, maxWidth, allAlign, align, lines);
    }

    /**
     * 绘制多行文字，超出最大宽度的行水平压缩，不到最大宽度的行水平分散对齐绘制
     *
     * @param g        Java AWT绘图上下文对象
     * @param font     字体对象
     * @param x        基准点X坐标（左对齐起始X）
     * @param y        基准点Y坐标（第一行文字底部基线位置）
     * @param maxWidth 最大宽度，超出时水平压缩，不足时分散对齐
     * @param h        行高（每行之间的垂直间距）
     * @param lines    要绘制的多行文字
     * @return 最大宽度
     */
    public static int drawStrDistributedAlign(Graphics2D g, Font font, int x, int y, int maxWidth, int h, String... lines) {
        if (lines == null || lines.length == 0) return 0;

        g.setFont(font);
        FontRenderContext frc = g.getFontRenderContext();

        int currentY = y;
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                currentY += h;
                continue;
            }

            // 获取每个字符的宽度
            int len = line.length();
            int[] charWidths = new int[len];
            int totalWidth = 0;
            for (int i = 0; i < len; i++) {
                String ch = String.valueOf(line.charAt(i));
                Rectangle2D bounds = font.getStringBounds(ch, frc);
                charWidths[i] = (int) Math.ceil(bounds.getWidth());
                totalWidth += charWidths[i];
            }

            if (totalWidth <= maxWidth && len > 1) {
                // 不到最大宽度且多于1个字符，分散对齐
                int totalGaps = len - 1;
                int gapWidth = (maxWidth - totalWidth) / totalGaps;
                int extraRemainder = (maxWidth - totalWidth) - gapWidth * totalGaps;

                int drawX = x;
                for (int i = 0; i < len; i++) {
                    String ch = String.valueOf(line.charAt(i));
                    g.drawString(ch, drawX, currentY);
                    drawX += charWidths[i] + gapWidth + (i < extraRemainder ? 1 : 0);
                }
            } else if (totalWidth > maxWidth) {
                // 超过最大宽度，水平压缩
                double scaleX = (double) maxWidth / totalWidth;
                AffineTransform oldTransform = g.getTransform();
                AffineTransform tx = new AffineTransform();
                tx.translate(x, currentY);
                tx.scale(scaleX, 1.0);
                tx.translate(-x, -currentY);
                g.transform(tx);
                g.drawString(line, x, currentY);
                g.setTransform(oldTransform);
            } else {
                // 只有1个字符且不超过宽度，居中绘制
                int drawX = x + (maxWidth - totalWidth) / 2;
                g.drawString(line, drawX, currentY);
            }

            currentY += h;
        }

        return maxWidth;
    }
}
