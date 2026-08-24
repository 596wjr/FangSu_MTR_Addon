package com.fangsu.drawing.sign;

import com.fangsu.utils.ResourceUtil;
import com.fangsu.mappings.ResourceLocation;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 复刻 MTR 原版铁路指示牌的贴图渲染：直接加载 MTR 的 circle / arrow / exit_letter_blank 贴图
 * 并用其 alpha 通道作为蒙版着色（对齐 MTR RouteMapGenerator.drawResource 的着色逻辑），
 * 文字使用 MTR 自带的字体（noto-sans / noto-serif-cjk）。
 */
public final class MtrSignRenderer {

    private static final String MTR_CIRCLE = "mtr:textures/block/sign/circle.png";
    private static final String MTR_ARROW = "mtr:textures/block/sign/arrow.png";
    private static final String MTR_EXIT_BLANK = "mtr:textures/block/sign/exit_letter_blank.png";
    private static final String MTR_FONT_LATIN = "mtr:font/noto-sans-semibold.ttf";
    private static final String MTR_FONT_CJK = "mtr:font/noto-serif-cjk-tc-semibold.ttf";

    private static BufferedImage circle;
    private static BufferedImage arrow;
    private static BufferedImage exitBlank;
    private static Font fontLatin;
    private static Font fontCjk;

    private MtrSignRenderer() {
    }

    private static BufferedImage load(String path) {
        try {
            return ResourceUtil.loadImage(new ResourceLocation(path).getRaw());
        } catch (Exception e) {
            return null;
        }
    }

    private static Font loadFont(String path) {
        return ResourceUtil.loadFont(new ResourceLocation(path).getRaw());
    }

    private static BufferedImage circleImage() {
        if (circle == null) circle = load(MTR_CIRCLE);
        return circle;
    }

    private static BufferedImage arrowImage() {
        if (arrow == null) arrow = load(MTR_ARROW);
        return arrow;
    }

    private static BufferedImage exitBlankImage() {
        if (exitBlank == null) exitBlank = load(MTR_EXIT_BLANK);
        return exitBlank;
    }

    /**
     * 拉丁文字体（站台号/数字/非 CJK）。
     */
    public static Font latinFont() {
        if (fontLatin == null) fontLatin = loadFont(MTR_FONT_LATIN);
        return fontLatin;
    }

    /**
     * CJK 字体（中文终点/站名）。
     */
    public static Font cjkFont() {
        if (fontCjk == null) fontCjk = loadFont(MTR_FONT_CJK);
        return fontCjk;
    }

    /**
     * 按目标颜色对一张白底蒙版贴图着色：结果 RGB = targetColor 的 RGB，alpha 沿用贴图 alpha。
     * 复刻 MTR drawResource 的非 useActualColor 分支。
     */
    private static BufferedImage tint(BufferedImage src, int targetRgb) {
        if (src == null) return null;
        final int w = src.getWidth();
        final int h = src.getHeight();
        final BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final int r = (targetRgb >> 16) & 0xFF;
        final int g = (targetRgb >> 8) & 0xFF;
        final int b = targetRgb & 0xFF;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final int argb = src.getRGB(x, y);
                final int a = (argb >>> 24) & 0xFF;
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    /**
     * 绘制一个着色后的圆形指示（用于站台号圆标）。
     */
    public static void drawCircle(Graphics2D g, int color, int x, int y, int size) {
        final BufferedImage tinted = tint(circleImage(), color);
        if (tinted != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(tinted, x, y, size, size, null);
        }
    }

    /**
     * 绘制一个方向箭头（白色箭头贴图）。flip 为 true 时水平翻转（向右）。
     */
    public static void drawArrow(Graphics2D g, boolean flip, int x, int y, int size) {
        final BufferedImage tinted = tint(arrowImage(), 0xFFFFFF);
        if (tinted != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (flip) {
                g.drawImage(tinted, x + size, y, -size, size, null);
            } else {
                g.drawImage(tinted, x, y, size, size, null);
            }
        }
    }

    /**
     * 绘制一个出口字母牌底（exit_letter_blank，使用实际贴图颜色）。
     */
    public static void drawExitBlank(Graphics2D g, int x, int y, int size) {
        final BufferedImage img = exitBlankImage();
        if (img != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, x, y, size, size, null);
        }
    }
}
