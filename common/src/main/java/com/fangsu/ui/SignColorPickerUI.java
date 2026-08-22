package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.utils.GraphicContext;
import net.minecraft.client.Minecraft;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.function.Consumer;

/**
 * Swing 风格指示牌面背景色选择 UI（新）。
 * 含 2D 拾色板（SV）+ 色相条 + 预览 + R/G/B/A 与 Hex 输入框 + 应用/取消。
 * 尺寸自适应窗口高度，保证 16:9 下可完整显示。
 */
public class SignColorPickerUI extends Screen {

    private static final int G2D_SCALE = 4;

    private final Screen parent;
    private final Consumer<Integer> onApply;

    // 颜色状态
    private int[] rgba = new int[4];   // r,g,b,a 0~255
    private float hue = 0f;            // 0~360
    private float sat = 0f;            // 0~1
    private float val = 1f;            // 0~1

    // 输入控件
    private EditBox rBox, gBox, bBox, aBox, hexBox;
    private Button applyBtn, cancelBtn;
    private GraphicsTexture g2dLayer;

    // 布局
    private int svX, svY, svW, svH;
    private int hueX, hueY, hueW, hueH;
    private int rightX;
    private int previewX, previewY, previewW, previewH;
    private int[] boxY = new int[4];
    private int hexX, hexY, hexW;
    private int btnY;

    // 拖动状态
    private boolean draggingPalette = false;
    private boolean draggingHue = false;

    public SignColorPickerUI(Screen parent, int currentColor, Consumer<Integer> onApply) {
        super(ComponentHelper.translatable("ui.fangsu.sign.color_title"));
        this.parent = parent;
        this.onApply = onApply;
        setColor(currentColor);
    }

    private void setColor(int argb) {
        rgba[0] = (argb >> 16) & 0xFF;
        rgba[1] = (argb >> 8) & 0xFF;
        rgba[2] = argb & 0xFF;
        rgba[3] = (argb >>> 24) & 0xFF;
        recomputeHsv();
    }

    private void recomputeHsv() {
        float[] hsv = rgbToHsv(rgba[0], rgba[1], rgba[2]);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
    }

    private void applyRgbFromHsv() {
        int[] rgb = hsvToRgb(hue, sat, val);
        rgba[0] = rgb[0];
        rgba[1] = rgb[1];
        rgba[2] = rgb[2];
    }

    private int currentArgb() {
        return (rgba[3] << 24) | (rgba[0] << 16) | (rgba[1] << 8) | rgba[2];
    }

    @Override
    protected void init() {
        super.init();
        int panelTop = 26;
        int margin = 40;
        svW = svH = Math.max(100, Math.min(160, height - 130));
        svX = margin;
        svY = panelTop;
        hueX = svX + svW + 8;
        hueY = svY;
        hueW = 14;
        hueH = svH;
        rightX = hueX + hueW + 24;
        previewX = rightX;
        previewY = svY;
        previewW = Math.min(140, Math.max(90, width - previewX - 10));
        previewH = 28;
        int labelW = 24, boxW = 52, boxH = 18;
        int firstBoxY = svY + 46;
        int rowGap = 22;
        for (int i = 0; i < 4; i++) boxY[i] = firstBoxY + i * rowGap;
        hexX = rightX + labelW;
        hexY = firstBoxY + 4 * rowGap + 4;
        hexW = 72;
        btnY = Math.max(svY + svH + 18, hexY + 18 + 10);

        rBox = makeBox(rightX + labelW, boxY[0], boxW);
        gBox = makeBox(rightX + labelW, boxY[1], boxW);
        bBox = makeBox(rightX + labelW, boxY[2], boxW);
        aBox = makeBox(rightX + labelW, boxY[3], boxW);
        hexBox = makeBox(hexX, hexY, hexW);
        rBox.setMaxLength(3);
        gBox.setMaxLength(3);
        bBox.setMaxLength(3);
        aBox.setMaxLength(3);
        hexBox.setMaxLength(9);

        rBox.setResponder(t -> onByteEdit(0, t));
        gBox.setResponder(t -> onByteEdit(1, t));
        bBox.setResponder(t -> onByteEdit(2, t));
        aBox.setResponder(t -> onByteEdit(3, t));
        hexBox.setResponder(this::onHexEdit);

        addRenderableWidget(rBox);
        addRenderableWidget(gBox);
        addRenderableWidget(bBox);
        addRenderableWidget(aBox);
        addRenderableWidget(hexBox);

        int btnW = 100, btnH = 20, gap = 12;
        int applyX = (width - (btnW * 2 + gap)) / 2;
        applyBtn = ComponentHelper.button(applyX, btnY, btnW, btnH,
                ComponentHelper.translatable("ui.fangsu.sign.color_confirm"),
                b -> {
                    onApply.accept(currentArgb());
                    closeScreen();
                });
        cancelBtn = ComponentHelper.button(applyX + btnW + gap, btnY, btnW, btnH,
                ComponentHelper.translatable("ui.fangsu.block.cancel"),
                b -> closeScreen());
        addRenderableWidget(applyBtn);
        addRenderableWidget(cancelBtn);

        syncBoxes();
        recreateG2dLayer();
    }

    private EditBox makeBox(int x, int y, int w) {
        //#if MC_VERSION >= 12000
        EditBox box = new EditBox(this.font, x, y, w, 18, Component.empty());
        //#else
        //$$ EditBox box = new EditBox(this.font, x, y, w, 18, ComponentHelper.empty());
        //#endif
        return box;
    }

    private void onByteEdit(int channel, String text) {
        Integer v = parseByte(text);
        if (v == null) return;
        rgba[channel] = v;
        if (channel < 3) recomputeHsv();
    }

    private void onHexEdit(String text) {
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return;
        try {
            int c = (int) Long.parseLong(s, 16);
            rgba[0] = (c >> 16) & 0xFF;
            rgba[1] = (c >> 8) & 0xFF;
            rgba[2] = c & 0xFF;
            recomputeHsv();
        } catch (Exception ignored) {
        }
    }

    private void syncBoxes() {
        rBox.setValue(String.valueOf(rgba[0]));
        gBox.setValue(String.valueOf(rgba[1]));
        bBox.setValue(String.valueOf(rgba[2]));
        aBox.setValue(String.valueOf(rgba[3]));
        hexBox.setValue(String.format("#%02X%02X%02X", rgba[0], rgba[1], rgba[2]));
    }

    private Integer parseByte(String s) {
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            return Math.max(0, Math.min(255, Integer.parseInt(s)));
        } catch (Exception e) {
            return null;
        }
    }

    private void recreateG2dLayer() {
        if (g2dLayer != null) g2dLayer.close();
        int texW = Math.max(1, width);
        int texH = Math.max(1, height);
        g2dLayer = new GraphicsTexture(texW * G2D_SCALE, texH * G2D_SCALE);
        g2dLayer.graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    //#if MC_VERSION >= 12000
    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GraphicContext g = GraphicContext.of(graphics);
        renderBackground(graphics);
        //#else
        //$$ @Override
        //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     GraphicContext g = GraphicContext.of(poseStack);
        //$$     renderBackground(poseStack);
        //#endif

        drawPaletteG2D();
        g2dLayer.upload();

        // 背景与标题（GuiGraphics）
        g.fill(0, 0, width, height, 0xFF101010);
        g.drawString(font, this.title, 10, 8, 0xFFFFFF, false);

        // 拾色板渐变（仅此使用 G2D 纹理）
        blitPalette(g);
        blitHueBar(g);
        drawRectOutline(g, svX, svY, svW, svH, 0xFF8C8C8C);
        drawRectOutline(g, hueX, hueY, hueW, hueH, 0xFF8C8C8C);
        drawMarker(g, svX + Math.round(sat * svW), svY + Math.round((1f - val) * svH));
        drawMarker(g, hueX + hueW / 2, hueY + Math.round(hue / 360f * hueH));

        // 预览（GuiGraphics）
        g.fill(previewX, previewY, previewX + previewW, previewY + previewH, currentArgb());
        drawRectOutline(g, previewX, previewY, previewW, previewH, 0xFFC8C8C8);

        // 标签（GuiGraphics）
        String[] labels = {"R", "G", "B", "A"};
        for (int i = 0; i < 4; i++) {
            g.drawString(font, labels[i], rightX, boxY[i] + 4, 0xFFFFFF, false);
        }
        g.drawString(font, "Hex", rightX, hexY + 4, 0xFFFFFF, false);

        rBox.render(g.asMinecraft(), mouseX, mouseY, partialTick);
        gBox.render(g.asMinecraft(), mouseX, mouseY, partialTick);
        bBox.render(g.asMinecraft(), mouseX, mouseY, partialTick);
        aBox.render(g.asMinecraft(), mouseX, mouseY, partialTick);
        hexBox.render(g.asMinecraft(), mouseX, mouseY, partialTick);
        applyBtn.render(g.asMinecraft(), mouseX, mouseY, partialTick);
        cancelBtn.render(g.asMinecraft(), mouseX, mouseY, partialTick);
    }

    private void drawPaletteG2D() {
        Graphics2D g2d = g2dLayer.graphics;
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, g2dLayer.width, g2dLayer.height);
        g2d.setComposite(AlphaComposite.SrcOver);

        // SV 拾色板渐变
        int gx = getG2dX(svX), gy = getG2dY(svY), gw = getG2dU(svW), gh = getG2dU(svH);
        int[] top = hsvToRgb(hue, 1f, 1f);
        g2d.setPaint(new GradientPaint(gx, gy, new Color(top[0], top[1], top[2]), gx, gy + gh, Color.BLACK));
        g2d.fillRect(gx, gy, gw, gh);
        g2d.setPaint(new GradientPaint(gx, gy, Color.WHITE, gx + gw, gy, new Color(255, 255, 255, 0)));
        g2d.fillRect(gx, gy, gw, gh);

        // 色相条渐变
        int hx = getG2dX(hueX), hy = getG2dY(hueY), hw = getG2dU(hueW), hh = getG2dU(hueH);
        float[] frac = {0f, 1f / 6f, 2f / 6f, 3f / 6f, 4f / 6f, 5f / 6f, 1f};
        Color[] colors = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
        g2d.setPaint(new LinearGradientPaint(hx, hy, hx, hy + hh, frac, colors));
        g2d.fillRect(hx, hy, hw, hh);
    }

    private void blitPalette(GraphicContext g) {
        g.blit(g2dLayer.identifier, svX, svY, svW, svH,
                getG2dX(svX), getG2dY(svY), getG2dU(svW), getG2dU(svH),
                g2dLayer.width, g2dLayer.height);
    }

    private void blitHueBar(GraphicContext g) {
        g.blit(g2dLayer.identifier, hueX, hueY, hueW, hueH,
                getG2dX(hueX), getG2dY(hueY), getG2dU(hueW), getG2dU(hueH),
                g2dLayer.width, g2dLayer.height);
    }

    private void drawRectOutline(GraphicContext g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void drawMarker(GraphicContext g, int cx, int cy) {
        int r = 3;
        g.fill(cx - r, cy - r, cx + r, cy - r + 1, 0xFFFFFFFF);
        g.fill(cx - r, cy + r - 1, cx + r, cy + r, 0xFFFFFFFF);
        g.fill(cx - r, cy - r, cx - r + 1, cy + r, 0xFFFFFFFF);
        g.fill(cx + r - 1, cy - r, cx + r, cy + r, 0xFFFFFFFF);
    }

    private int getG2dX(float p) {
        return Math.round(p * ((float) g2dLayer.width / Math.max(1, width)));
    }

    private int getG2dY(float p) {
        return Math.round(p * ((float) g2dLayer.height / Math.max(1, height)));
    }

    private int getG2dU(float p) {
        return Math.max(1, Math.round(p * ((float) g2dLayer.height / Math.max(1, height))));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inRect(mouseX, mouseY, svX, svY, svW, svH)) {
                draggingPalette = true;
                updateSv(mouseX, mouseY);
                return true;
            }
            if (inRect(mouseX, mouseY, hueX, hueY, hueW, hueH)) {
                draggingHue = true;
                updateHue(mouseX, mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            if (draggingPalette) {
                updateSv(mouseX, mouseY);
                return true;
            }
            if (draggingHue) {
                updateHue(mouseX, mouseY);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingPalette = false;
        draggingHue = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void updateSv(double mx, double my) {
        sat = (float) Math.max(0, Math.min(1, (mx - svX) / (double) svW));
        val = (float) Math.max(0, Math.min(1, 1 - (my - svY) / (double) svH));
        applyRgbFromHsv();
        syncBoxes();
    }

    private void updateHue(double mx, double my) {
        hue = (float) Math.max(0, Math.min(1, (my - hueY) / (double) hueH)) * 360f;
        applyRgbFromHsv();
        syncBoxes();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            closeScreen();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void closeScreen() {
        if (g2dLayer != null) g2dLayer.close();
        Minecraft.getInstance().setScreen(parent);
    }

    /* ===================== 颜色换算 ===================== */

    private static int[] hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return new int[]{
                Math.round((r + m) * 255f),
                Math.round((g + m) * 255f),
                Math.round((b + m) * 255f)
        };
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf));
        float d = max - min;
        float h = 0f;
        if (d > 0f) {
            if (max == rf) h = 60f * (((gf - bf) / d) % 6f);
            else if (max == gf) h = 60f * ((bf - rf) / d + 2f);
            else h = 60f * ((rf - gf) / d + 4f);
        }
        if (h < 0f) h += 360f;
        float s = max == 0f ? 0f : d / max;
        return new float[]{h, s, max};
    }
}
