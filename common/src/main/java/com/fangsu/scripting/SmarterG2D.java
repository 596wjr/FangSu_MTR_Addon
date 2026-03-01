package com.fangsu.scripting;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.text.AttributedCharacterIterator;

public class SmarterG2D extends Graphics2D {
    private final Graphics2D g;

    public SmarterG2D(Graphics2D g) {
        this.g = g;
    }

    /* ========================= */
    /* ==== 核心拦截方法 ====== */
    /* ========================= */

    // 字符串绘制相关
    public void drawString(Object text, Object x, Object y) {
        g.drawString(
                text == null ? "" : text.toString(),
                ((Number) x).floatValue(),
                ((Number) y).floatValue()
        );
    }

    @Override
    public void drawString(String str, float x, float y) {
        g.drawString(str, x, y);
    }

    @Override
    public void drawString(String str, int x, int y) {
        g.drawString(str, x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, float x, float y) {
        g.drawString(iterator, x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, int x, int y) {
        g.drawString(iterator, x, y);
    }

    // 矩形绘制相关
    @Override
    public void fillRect(int x, int y, int w, int h) {
        g.fillRect(x, y, w, h);
    }

    public void fillRect(Object x, Object y, Object w, Object h) {
        g.fillRect(
                ((Number) x).intValue(),
                ((Number) y).intValue(),
                ((Number) w).intValue(),
                ((Number) h).intValue()
        );
    }

    // 其他基本图形绘制 - 添加Object版本
    public void drawLine(Object x1, Object y1, Object x2, Object y2) {
        g.drawLine(
                ((Number) x1).intValue(),
                ((Number) y1).intValue(),
                ((Number) x2).intValue(),
                ((Number) y2).intValue()
        );
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        g.drawLine(x1, y1, x2, y2);
    }

    public void drawRect(Object x, Object y, Object w, Object h) {
        g.drawRect(
                ((Number) x).intValue(),
                ((Number) y).intValue(),
                ((Number) w).intValue(),
                ((Number) h).intValue()
        );
    }

    @Override
    public void drawRect(int x, int y, int width, int height) {
        g.drawRect(x, y, width, height);
    }

    public void drawOval(Object x, Object y, Object w, Object h) {
        g.drawOval(
                ((Number) x).intValue(),
                ((Number) y).intValue(),
                ((Number) w).intValue(),
                ((Number) h).intValue()
        );
    }

    @Override
    public void drawOval(int x, int y, int width, int height) {
        g.drawOval(x, y, width, height);
    }

    public void fillOval(Object x, Object y, Object w, Object h) {
        g.fillOval(
                ((Number) x).intValue(),
                ((Number) y).intValue(),
                ((Number) w).intValue(),
                ((Number) h).intValue()
        );
    }

    @Override
    public void fillOval(int x, int y, int width, int height) {
        g.fillOval(x, y, width, height);
    }

    public void drawRoundRect(Object x, Object y, Object w, Object h, Object aw, Object ah) {
        g.drawRoundRect(
                ((Number) x).intValue(),
                ((Number) y).intValue(),
                ((Number) w).intValue(),
                ((Number) h).intValue(),
                ((Number) aw).intValue(),
                ((Number) ah).intValue()
        );
    }

    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        g.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    public void fillRoundRect(Object x, Object y, Object w, Object h, Object aw, Object ah) {
        g.fillRoundRect(
                ((Number) x).intValue(),
                ((Number) y).intValue(),
                ((Number) w).intValue(),
                ((Number) h).intValue(),
                ((Number) aw).intValue(),
                ((Number) ah).intValue()
        );
    }

    @Override
    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        g.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    public void drawArc(Object x, Object y, Object w, Object h, Object startAngle, Object arcAngle) {
        g.drawArc(
                ((Number) x).intValue(),
                ((Number) y).intValue(),
                ((Number) w).intValue(),
                ((Number) h).intValue(),
                ((Number) startAngle).intValue(),
                ((Number) arcAngle).intValue()
        );
    }

    @Override
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        g.drawArc(x, y, width, height, startAngle, arcAngle);
    }

    public void fillArc(Object x, Object y, Object w, Object h, Object startAngle, Object arcAngle) {
        g.fillArc(
                ((Number) x).intValue(),
                ((Number) y).intValue(),
                ((Number) w).intValue(),
                ((Number) h).intValue(),
                ((Number) startAngle).intValue(),
                ((Number) arcAngle).intValue()
        );
    }

    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        g.fillArc(x, y, width, height, startAngle, arcAngle);
    }

    // 图像绘制相关
    @Override
    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
        g.drawImage(img, op, x, y);
    }

    @Override
    public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
        return g.drawImage(img, xform, obs);
    }

    public boolean drawImage(Object img, Object x, Object y, Object observer) {
        return g.drawImage(
                (Image) img,
                ((Number) x).intValue(),
                ((Number) y).intValue(),
                (ImageObserver) observer
        );
    }

    @Override
    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        return g.drawImage(img, x, y, observer);
    }

    public boolean drawImage(Object img, Object x, Object y, Object width, Object height, Object observer) {
        return g.drawImage(
                (Image) img,
                ((Number) x).intValue(),
                ((Number) y).intValue(),
                ((Number) width).intValue(),
                ((Number) height).intValue(),
                (ImageObserver) observer
        );
    }

    @Override
    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        return g.drawImage(img, x, y, width, height, observer);
    }

    // 文本相关
    @Override
    public void drawGlyphVector(java.awt.font.GlyphVector gv, float x, float y) {
        g.drawGlyphVector(gv, x, y);
    }

    // 变换相关
    @Override
    public void translate(int x, int y) {
        g.translate(x, y);
    }

    @Override
    public void translate(double tx, double ty) {
        g.translate(tx, ty);
    }

    @Override
    public void rotate(double theta) {
        g.rotate(theta);
    }

    @Override
    public void rotate(double theta, double x, double y) {
        g.rotate(theta, x, y);
    }

    @Override
    public void scale(double sx, double sy) {
        g.scale(sx, sy);
    }

    @Override
    public void shear(double shx, double shy) {
        g.shear(shx, shy);
    }

    // 裁剪相关
    @Override
    public void clip(Shape s) {
        g.clip(s);
    }

    @Override
    public void clipRect(int x, int y, int width, int height) {
        g.clipRect(x, y, width, height);
    }

    public void clipRect(Object x, Object y, Object w, Object h) {
        g.clipRect(
                ((Number) x).intValue(),
                ((Number) y).intValue(),
                ((Number) w).intValue(),
                ((Number) h).intValue()
        );
    }

    @Override
    public Rectangle getClipBounds() {
        return g.getClipBounds();
    }

    @Override
    public void setClip(int x, int y, int width, int height) {
        g.setClip(x, y, width, height);
    }

    // 合成相关
    @Override
    public void setComposite(Composite comp) {
        g.setComposite(comp);
    }

    @Override
    public void setPaint(Paint paint) {
        g.setPaint(paint);
    }

    @Override
    public void setStroke(Stroke s) {
        g.setStroke(s);
    }

    @Override
    public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
        g.setRenderingHint(hintKey, hintValue);
    }

    @Override
    public Object getRenderingHint(RenderingHints.Key hintKey) {
        return g.getRenderingHint(hintKey);
    }

    @Override
    public void setRenderingHints(java.util.Map<?, ?> hints) {
        g.setRenderingHints(hints);
    }

    @Override
    public void addRenderingHints(java.util.Map<?, ?> hints) {
        g.addRenderingHints(hints);
    }

    @Override
    public RenderingHints getRenderingHints() {
        return g.getRenderingHints();
    }

    @Override
    public void setBackground(Color color) {
        g.setBackground(color);
    }

    @Override
    public Color getBackground() {
        return g.getBackground();
    }

    @Override
    public void setPaintMode() {
        g.setPaintMode();
    }

    @Override
    public void setXORMode(Color c1) {
        g.setXORMode(c1);
    }

    /* ========================= */
    /* ==== 图像绘制相关 ====== */
    /* ========================= */

    // RenderedImage 和 RenderableImage 绘制
    @Override
    public void drawRenderedImage(java.awt.image.RenderedImage img, java.awt.geom.AffineTransform xform) {
        g.drawRenderedImage(img, xform);
    }

    @Override
    public void drawRenderableImage(java.awt.image.renderable.RenderableImage img, java.awt.geom.AffineTransform xform) {
        g.drawRenderableImage(img, xform);
    }

    // 获取设备配置
    @Override
    public java.awt.GraphicsConfiguration getDeviceConfiguration() {
        return g.getDeviceConfiguration();
    }

    // 变换相关
    @Override
    public void transform(java.awt.geom.AffineTransform Tx) {
        g.transform(Tx);
    }

    /* ========================= */
    /* ==== 清理相关 ========== */
    /* ========================= */

    @Override
    public void clearRect(int x, int y, int width, int height) {
        g.clearRect(x, y, width, height);
    }

    public void clearRect(Object x, Object y, Object width, Object height) {
        g.clearRect(
                ((Number) x).intValue(),
                ((Number) y).intValue(),
                ((Number) width).intValue(),
                ((Number) height).intValue()
        );
    }

    /* ========================= */
    /* ==== 多边形相关 ======== */
    /* ========================= */

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        g.drawPolyline(xPoints, yPoints, nPoints);
    }

    // 对于数组参数，JavaScript可能传入的是数组但元素可能是各种类型
    // 这里不创建Object版本，因为处理数组类型转换比较复杂
    // 建议在脚本层面确保传入的是正确的int数组

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        g.drawPolygon(xPoints, yPoints, nPoints);
    }

    @Override
    public void drawPolygon(java.awt.Polygon p) {
        g.drawPolygon(p);
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        g.fillPolygon(xPoints, yPoints, nPoints);
    }

    @Override
    public void fillPolygon(java.awt.Polygon p) {
        g.fillPolygon(p);
    }

    /* ========================= */
    /* ==== 图像绘制(带背景色) = */
    /* ========================= */

    @Override
    public boolean drawImage(java.awt.Image img, int x, int y, java.awt.Color bgcolor, java.awt.image.ImageObserver observer) {
        return g.drawImage(img, x, y, bgcolor, observer);
    }

    public boolean drawImage(Object img, Object x, Object y, Object bgcolor, Object observer) {
        return g.drawImage(
                (java.awt.Image) img,
                ((Number) x).intValue(),
                ((Number) y).intValue(),
                (java.awt.Color) bgcolor,
                (java.awt.image.ImageObserver) observer
        );
    }

    @Override
    public boolean drawImage(java.awt.Image img, int x, int y, int width, int height, java.awt.Color bgcolor, java.awt.image.ImageObserver observer) {
        return g.drawImage(img, x, y, width, height, bgcolor, observer);
    }

    public boolean drawImage(Object img, Object x, Object y, Object width, Object height, Object bgcolor, Object observer) {
        return g.drawImage(
                (java.awt.Image) img,
                ((Number) x).intValue(),
                ((Number) y).intValue(),
                ((Number) width).intValue(),
                ((Number) height).intValue(),
                (java.awt.Color) bgcolor,
                (java.awt.image.ImageObserver) observer
        );
    }

    /* ========================= */
    /* ==== 图像绘制(带源目标区域) = */
    /* ========================= */

    @Override
    public boolean drawImage(java.awt.Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, java.awt.image.ImageObserver observer) {
        return g.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
    }

    public boolean drawImage(Object img, Object dx1, Object dy1, Object dx2, Object dy2, Object sx1, Object sy1, Object sx2, Object sy2, Object observer) {
        return g.drawImage(
                (java.awt.Image) img,
                ((Number) dx1).intValue(),
                ((Number) dy1).intValue(),
                ((Number) dx2).intValue(),
                ((Number) dy2).intValue(),
                ((Number) sx1).intValue(),
                ((Number) sy1).intValue(),
                ((Number) sx2).intValue(),
                ((Number) sy2).intValue(),
                (java.awt.image.ImageObserver) observer
        );
    }

    @Override
    public boolean drawImage(java.awt.Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, java.awt.Color bgcolor, java.awt.image.ImageObserver observer) {
        return g.drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgcolor, observer);
    }

    public boolean drawImage(Object img, Object dx1, Object dy1, Object dx2, Object dy2, Object sx1, Object sy1, Object sx2, Object sy2, Object bgcolor, Object observer) {
        return g.drawImage(
                (java.awt.Image) img,
                ((Number) dx1).intValue(),
                ((Number) dy1).intValue(),
                ((Number) dx2).intValue(),
                ((Number) dy2).intValue(),
                ((Number) sx1).intValue(),
                ((Number) sy1).intValue(),
                ((Number) sx2).intValue(),
                ((Number) sy2).intValue(),
                (java.awt.Color) bgcolor,
                (java.awt.image.ImageObserver) observer
        );
    }

    /* ========================= */
    /* ==== 安全直接转发 ====== */
    /* ========================= */

    @Override
    public void setColor(Color c) {
        g.setColor(c);
    }

    @Override
    public Color getColor() {
        return g.getColor();
    }

    @Override
    public void setFont(Font f) {
        g.setFont(f);
    }

    @Override
    public Font getFont() {
        return g.getFont();
    }

    @Override
    public void setClip(Shape clip) {
        g.setClip(clip);
    }

    @Override
    public Shape getClip() {
        return g.getClip();
    }

    @Override
    public void setTransform(AffineTransform Tx) {
        g.setTransform(Tx);
    }

    @Override
    public AffineTransform getTransform() {
        return g.getTransform();
    }

    /* ========================= */
    /* ==== 必须实现的抽象 ===== */
    /* ========================= */

    @Override
    public void draw(Shape s) {
        g.draw(s);
    }

    @Override
    public void fill(Shape s) {
        g.fill(s);
    }

    @Override
    public void copyArea(int x, int y, int width, int height, int dx, int dy) {
        g.copyArea(x, y, width, height, dx, dy);
    }

    @Override
    public Graphics create() {
        return new SmarterG2D((Graphics2D) g.create());
    }

    @Override
    public void dispose() {
        g.dispose();
    }

    @Override
    public FontRenderContext getFontRenderContext() {
        return g.getFontRenderContext();
    }

    @Override
    public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
        return g.hit(rect, s, onStroke);
    }

    @Override
    public Paint getPaint() {
        return g.getPaint();
    }

    @Override
    public Composite getComposite() {
        return g.getComposite();
    }

    @Override
    public Stroke getStroke() {
        return g.getStroke();
    }

    @Override
    public FontMetrics getFontMetrics() {
        return g.getFontMetrics();
    }

    @Override
    public FontMetrics getFontMetrics(Font f) {
        return g.getFontMetrics(f);
    }

    @Override
    public Rectangle getClipRect() {
        return g.getClipRect();
    }

    @Override
    public boolean hitClip(int x, int y, int width, int height) {
        return g.hitClip(x, y, width, height);
    }

    @Override
    public Rectangle getClipBounds(Rectangle r) {
        return g.getClipBounds(r);
    }

    @Override
    public String toString() {
        return "SmarterG2D wrapper for: " + g.toString();
    }

    // 添加一个通用的辅助方法，用于类型转换
    private int toInt(Object obj) {
        return ((Number) obj).intValue();
    }

    private float toFloat(Object obj) {
        return ((Number) obj).floatValue();
    }

    private double toDouble(Object obj) {
        return ((Number) obj).doubleValue();
    }

}
