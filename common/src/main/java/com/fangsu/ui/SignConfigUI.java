package com.fangsu.ui;

import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.signItems.SignDrawContext;
import com.fangsu.signItems.SignItem;
import com.fangsu.utils.ResourceUtil;
import com.fangsu.utils.ScreenUtil;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

import static com.fangsu.signItems.SignItemFactory.EDITOR_ITEMS;

public class SignConfigUI extends Screen {

    private static final int ROW_COUNT = 6;

    private
    List<                       //第几面
            Map<String,         //左中右
                    List<SignItem>>> dispItems;
    private final List<SignItem> signItems = new ArrayList<>();
    private final Consumer<List<Map<String, List<SignItem>>>> setter;

    private int modeFlag = 0;
    private LaneRef inEditingRow = null;
    private int sideEditing = -1; // -2 = head insert
    private float[] rowScroll = new float[ROW_COUNT];
    private float paletteScroll = 0;
    private float editScroll = 0;
    private int faces = 2;

    private GraphicsTexture g2dLayer;
    private MouseClickInfo mouseClickInfo;

    public SignConfigUI(int faces, List<Map<String, List<SignItem>>> items, Consumer<List<Map<String, List<SignItem>>>> setter) {
        super(Component.translatable("ui.fangsu.sign.title"));
        this.faces = faces;
        this.dispItems = items;
        this.setter = setter;
    }

    @Override
    protected void init() {
        super.init();
        if (rowScroll.length != ROW_COUNT) {
            rowScroll = new float[ROW_COUNT];
        }
        g2dLayer = new GraphicsTexture(width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xFF101010);
        g2dLayer.graphics.setComposite(AlphaComposite.Clear);      // 设置为清除模式
        g2dLayer.graphics.fillRect(0, 0, width, height);              // 填充整个画布
        g2dLayer.graphics.setComposite(AlphaComposite.SrcOver);    // 恢复默认绘制模式

        if (modeFlag == 0) {
            drawSelectionScreen(graphics, mouseX, mouseY);
        } else if (modeFlag == 1) {
            drawEditingScreen(graphics, mouseX, mouseY);
        }

        graphics.drawString(font, this.title, 10, 2, 0xFFFFFF, false);
        g2dLayer.upload();
        graphics.blit(g2dLayer.identifier, 0, 0, width, height, width, height);

        mouseClickInfo = null;
    }

    private void drawSelectionScreen(GuiGraphics graphics, int mouseX, int mouseY) {
        int rowHeight = (height - 12) / ROW_COUNT;
        int i = 0;
        float u = (rowHeight / 7f) * 6f; // token 单位大小
        Graphics2D g2d = g2dLayer.graphics;

        for (int side = 0; side < faces; side++) {
            Map<String, List<SignItem>> faceLanes = dispItems.get(side);
            for (int part = 0; part < 3; part++) {
                int rowY = 12 + i * rowHeight;
                int rowBottom = rowY + rowHeight;

                // 背景条纹
                int stripeColor = (i % 2 == 0) ? 0x22ffffff : 0x00ffffff;
                if (mouseY >= rowY && mouseY <= rowBottom) {
                    stripeColor = 0x33ffffff;
                }
                graphics.fill(0, rowY, width, rowY + rowHeight, stripeColor);

                // 文本
                ScreenUtil.drawString(graphics,
                        Component.translatable("ui.fangsu.sign." + faceName(side)).getString() + " - " + Component.translatable("ui.fangsu.sign." + partName(part)).getString(),
                        16,
                        rowY + rowHeight / 8,
                        0xffffffff,
                        rowHeight / 8, false);

                // lane token
                List<SignItem> lane = faceLanes.get(partName(part));
                float scrollX = rowScroll[i];
                float startX = scrollX;

                drawLane(g2d, lane, startX, rowY / 7f, part, u);

                if (mouseClickInfo != null) {
                    if (mouseClickInfo.mouseY >= rowY && mouseClickInfo.mouseY <= rowBottom) {
                        modeFlag = 1;
                        inEditingRow = new LaneRef(side, part, lane);
                    }
                }

                i++;
            }
        }
    }


    private void drawEditingScreen(GuiGraphics graphics, int mouseX, int mouseY) {
        if (inEditingRow == null) modeFlag = 0;
        LaneRef laneRef = inEditingRow;
        List<SignItem> lane = laneRef.lane;

        graphics.fill(12, 24, width - 12, 78, 0x441E1E1E);
        graphics.drawString(
                font,
                Component.translatable("ui.fangsu.sign.tooltip1", Component.translatable("ui.fangsu.sign." + faceName(laneRef.face)).getString() + " - " + Component.translatable("ui.fangsu.sign." + partName(laneRef.part)).getString()),
                16,
                32,
                0xFFFFFF,
                false
        );

        graphics.drawString(
                font,
                Component.translatable("ui.fangsu.sign.tooltip2"),
                width - 80,
                32,
                0xCCCCCC,
                false
        );


        float u = 24;
        float y = 50 + editScroll;

        float totalWidth = 0;
        if (lane != null && !lane.isEmpty())
            for (SignItem token : lane) {
                totalWidth += getTokenWidth(g2dLayer.graphics, token, u, laneRef.part()) + u * 0.5f;
            }

        float x;
        switch (laneRef.part()) {
            case 2 -> x = width - 24 - totalWidth;
            case 1 -> x = (width - totalWidth) / 2f;
            default -> x = 24;
        }

        boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;

        // 头部加号
        if (lane != null && (lane.isEmpty() || sideEditing == -2)) {
            if (blink) {
                drawAddIndicator(graphics, x - u * 0.25f, y, u);
            }
        }

        Graphics2D g = g2dLayer.graphics;
        if (lane != null) {
            for (int idx = 0; idx < lane.size(); idx++) {
                SignItem token = lane.get(idx);
                float tokenW = getTokenWidth(g, token, u, laneRef.part());

                drawTokenG2D(g, token, x, y, u, laneRef.part());

                boolean addHover =
                        mouseX >= x + tokenW &&
                                mouseX <= x + tokenW + u * 0.5f &&
                                mouseY >= y &&
                                mouseY <= y + u;

                if (addHover || (sideEditing == idx && blink)) {
                    drawAddIndicator(graphics, x + tokenW, y, u);
                }

                boolean hover =
                        mouseX >= x &&
                                mouseX <= x + tokenW &&
                                mouseY >= y &&
                                mouseY <= y + u;

                if (hover) {
                    graphics.fill(
                            (int) x,
                            (int) y,
                            (int) (x + tokenW),
                            (int) (y + u),
                            0x33FFFFFF
                    );

                    graphics.drawString(
                            font,
                            "L:edit  R:del",
                            (int) x,
                            (int) (y - 10),
                            0xE0E0E0,
                            false
                    );
                }

                if (mouseClickInfo != null &&
                        mouseClickInfo.mouseY >= y && mouseClickInfo.mouseY <= y + u &&
                        mouseClickInfo.mouseX >= x && mouseClickInfo.mouseX <= x + tokenW) {
                    if (token.getConfigs() != null && !token.getConfigs().isEmpty()) {
                        mouseClickInfo = null;
                        Screen configScreen = new ConfigScreen(Component.translatable("ui.fangsu.common.config"), token.getConfigs(), this);
                        Minecraft.getInstance().setScreen(configScreen);
                    }
                }

                x += tokenW + u * 0.35f;
            }
        }

        drawPalette(graphics, mouseX, mouseY, laneRef, lane);
    }

    private void drawPalette(GuiGraphics graphics, int mouseX, int mouseY, LaneRef laneRef, List<SignItem> lane) {
        int top = 110;
        int lineItems = Math.max(1, (width - 24) / 30);
        int cell = 24;
        int gap = 4;

        graphics.enableScissor(12, top, width - 12, height - 12);
        for (int idx = 0; idx < EDITOR_ITEMS.size(); idx++) {
            int row = idx / lineItems;
            int col = idx % lineItems;
            int x = 16 + col * (cell + gap);
            int y = top + (int) paletteScroll + row * (cell + gap);
            boolean hover = mouseX >= x && mouseX <= x + cell && mouseY >= y && mouseY <= y + cell;

            graphics.fill(x, y, x + cell, y + cell, hover ? 0x33FFFFFF : 0x22000000);
            SignItem token = EDITOR_ITEMS.get(idx);
            try {
                drawTokenIconG2D(g2dLayer.graphics, token, x + 2, y + 2, cell - 4, laneRef.part());
            } catch (IOException e) {

            }
            if (hover) {
                graphics.drawString(font, "+", x + 9, y + 8, 0xFFFFFF, false);
            }
        }
        graphics.disableScissor();
    }

    private void drawLane(Graphics2D g, List<SignItem> lane, float startX, float y, int align, float u) {
        if (lane == null || lane.isEmpty()) return;
        Shape oriClip = g.getClip();
        float x = startX;
        if (align == 2) { // 右对齐
            float totalWidth = 0;
            for (SignItem token : lane) totalWidth += getTokenWidth(g2dLayer.graphics, token, u, align) + u * 0.1f;
            x = startX - totalWidth;
        } else if (align == 1) { // 居中
            float totalWidth = 0;
            for (SignItem token : lane) totalWidth += getTokenWidth(g2dLayer.graphics, token, u, align) + u * 0.1f;
            x = startX + (this.width - totalWidth) / 2f;
        }
        for (SignItem token : lane) {
            float tokenWidth = getTokenWidth(g, token, u, align);
            g.setClip(new Rectangle((int) (x + (align == 2 ? -1 : 1) * (tokenWidth)), (int) y, (int) tokenWidth, (int) u));
            drawTokenG2D(g, token, x, y, u, align);
            x += (align == 2 ? -1 : 1) * (tokenWidth + u * 0.1f);
            g.setClip(oriClip);
        }
    }

    private float getTokenWidth(Graphics2D graphics, SignItem token, float unit, int align) {
        return token.getWidth(graphics, unit);
    }

    private void drawTokenG2D(Graphics2D g, SignItem token, float x, float y, float unit, int align) {
        SignDrawContext ctx = new SignDrawContext(g, x, y, unit, align);
        token.draw(ctx);
    }

    private void drawTokenIconG2D(Graphics2D g, SignItem token, float x, float y, float unit, int align) throws IOException {
        BufferedImage image = ResourceUtil.loadImage(token.getIconLocation());
        g.drawImage(image, (int) x, (int) y, (int) unit, (int) unit, null);

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseClickInfo = new MouseClickInfo(mouseX, mouseY, button);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (modeFlag == 0) {
            int rowHeight = Math.max(40, (int) (height * 0.14f));
            for (int i = 0; i < ROW_COUNT; i++) {
                int rowY = 40 + i * rowHeight;
                if (mouseY >= rowY && mouseY <= rowY + rowHeight - 4) {
                    rowScroll[i] += (float) (delta * 8f);
                    return true;
                }
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        } else if (modeFlag == 1) {
            if (mouseY >= 110) {
                paletteScroll += (float) (delta * 10f);
                float min = -Math.max(0, (float) Math.ceil((double) signItems.size() / Math.max(1, (width - 24) / 30)) * 28f - (height - 122));
                paletteScroll = Math.max(min, Math.min(0, paletteScroll));
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            modeFlag--;
            if (modeFlag < 0) onClose();
            else if (modeFlag == 0) {
                inEditingRow = null;
                sideEditing = -1;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.modeFlag--;
        if (modeFlag == 0) {
            inEditingRow = null;
            sideEditing = -1;
        } else if (this.modeFlag < 0) {
            setter.accept(dispItems);
            super.onClose();
        }
    }

    private String partName(int part) {
        return switch (part) {
            case 0 -> "left";
            case 1 -> "middle";
            case 2 -> "right";
            default -> "unknown";
        };
    }

    private String faceName(int index) {
        return switch (index) {
            case 0 -> "front";
            case 1 -> "back";
            default -> "unknown";
        };
    }

    private JsonObject deepCopy(JsonObject input) {
        return JsonParser.parseString(input.toString()).getAsJsonObject();
    }

    private record LaneRef(int face, int part, List<SignItem> lane) {
    }

    private record MouseClickInfo(double mouseX, double mouseY, int button) {
    }

    private void drawAddIndicator(GuiGraphics g, float x, float y, float u) {

        int hhh = 0xFFFFFFFF;

        int fff = Math.round(u * 0.5f);                     //w
        int ggg = Math.round(u);                            //h

        int aaa = Math.round(x);                            //x
        int bbb = Math.round(y);                            //y

        int ccc = Math.max(1, Math.round(u * 0.075f));       // 框短边 + 加号短边
        int ddd = Math.round(u * 0.15f);                     // 框长边
        int eee = Math.round(u * 0.25f);                     // 加号长边

        // ===== 四角框 =====

        // 左上
        g.fill(aaa, bbb, aaa + ccc, bbb + ddd, hhh);
        g.fill(aaa, bbb, aaa + ddd, bbb + ccc, hhh);

        // 右上
        g.fill(aaa + fff - ccc, bbb, aaa + fff, bbb + ddd, hhh);
        g.fill(aaa + fff - ddd, bbb, aaa + fff, bbb + ccc, hhh);

        // 左下
        g.fill(aaa, bbb + ggg - ddd, aaa + ccc, bbb + ggg, hhh);
        g.fill(aaa, bbb + ggg - ccc, aaa + ddd, bbb + ggg, hhh);

        // 右下
        g.fill(aaa + fff - ccc, bbb + ggg - ddd, aaa + fff, bbb + ggg, hhh);
        g.fill(aaa + fff - ddd, bbb + ggg - ccc, aaa + fff, bbb + ggg, hhh);

        // ===== 中间加号 =====

        int centerX = aaa + fff / 2;
        int centerY = bbb + ggg / 2;

        // 横线
        g.fill(
                centerX - eee / 2,
                centerY - ccc / 2,
                centerX + eee / 2,
                centerY + ccc / 2,
                hhh
        );

        // 竖线（已修正宽度为 shortEdge）
        g.fill(
                centerX - ccc / 2,
                centerY - eee / 2,
                centerX + ccc / 2,
                centerY + eee / 2,
                hhh
        );
    }

}
