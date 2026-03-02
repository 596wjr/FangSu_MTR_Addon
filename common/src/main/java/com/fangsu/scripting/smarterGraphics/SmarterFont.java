package com.fangsu.scripting.smarterGraphics;

import java.awt.*;
import java.awt.font.FontRenderContext;

public class SmarterFont extends Font {
    private final Font raw;

    public SmarterFont(Font font) {
        super(font);
        this.raw = font;
    }

    public Font getRaw() {
        return raw;
    }

    /**
     * 包装 deriveFont 方法，接受 double 参数
     */
    public SmarterFont deriveFont(double size) {
        return new SmarterFont(raw.deriveFont((float) size));
    }

    /**
     * 包装 deriveFont 方法，接受 Number 参数
     */
    public SmarterFont deriveFont(Number size) {
        return new SmarterFont(raw.deriveFont(size.floatValue()));
    }

    /**
     * 包装 deriveFont 方法，接受 int 样式和 double 大小
     */
    public SmarterFont deriveFont(int style, double size) {
        return new SmarterFont(raw.deriveFont(style, (float) size));
    }

    /**
     * 包装 deriveFont 方法，接受 int 样式和 Number 大小
     */
    public SmarterFont deriveFont(int style, Number size) {
        return new SmarterFont(raw.deriveFont(style, size.floatValue()));
    }

    // ===== 常用的度量方法 =====

    /**
     * 获取字符串宽度（常用）
     */
    public int stringWidth(String str, FontRenderContext frc) {
        return (int) raw.getStringBounds(str, frc).getWidth();
    }

    /**
     * 获取字符串宽度（使用 Graphics2D）
     */
    public int stringWidth(String str, Graphics2D g) {
        return g.getFontMetrics(raw).stringWidth(str);
    }

    /**
     * 获取字体高度
     */
    public int getHeight(Graphics2D g) {
        return g.getFontMetrics(raw).getHeight();
    }

    /**
     * 获取字体 ascent
     */
    public int getAscent(Graphics2D g) {
        return g.getFontMetrics(raw).getAscent();
    }

    /**
     * 获取字体 descent
     */
    public int getDescent(Graphics2D g) {
        return g.getFontMetrics(raw).getDescent();
    }

    // ===== 常用的属性获取方法 =====

    public String getName() {
        return raw.getName();
    }

    public int getSize() {
        return raw.getSize();
    }

    public float getSize2D() {
        return raw.getSize2D();
    }

    public int getStyle() {
        return raw.getStyle();
    }

    public boolean isBold() {
        return raw.isBold();
    }

    public boolean isItalic() {
        return raw.isItalic();
    }

    public boolean isPlain() {
        return raw.isPlain();
    }

    public String getFamily() {
        return raw.getFamily();
    }

    public String getPSName() {
        return raw.getPSName();
    }

    // ===== 其他常用方法按需添加 =====

    public boolean canDisplay(char c) {
        return raw.canDisplay(c);
    }

    public int canDisplayUpTo(String str) {
        return raw.canDisplayUpTo(str);
    }

    // 获取原始 Font 对象（用于需要原始 Font 的地方）
    public Font getRawFont() {
        return raw;
    }

    @Override
    public String toString() {
        return raw.toString();
    }
}
