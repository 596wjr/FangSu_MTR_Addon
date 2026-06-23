package com.fangsu.ui;

import com.fangsu.drawing.sign.*;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.utils.GraphicContext;
import com.fangsu.utils.ScreenUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.fangsu.drawing.sign.SignItemFactory.EDITOR_ITEMS;

public class SignConfigUI extends Screen {

    private static final int ROW_COUNT = 6;
    private static final int G2D_SCALE = 4;

    private List<Map<String, List<SignItem>>> dispItems;
    private final Consumer<List<Map<String, List<SignItem>>>> setter;

    private int modeFlag = 0;
    private LaneRef inEditingRow = null;
    private LayoutEditRef layoutEditRef = null;
    private int sideEditing = -1; // -2 = head insert
    private float[] rowScroll = new float[ROW_COUNT];
    private float paletteScroll = 0;
    private float editingPreviewScroll = 0;
    private int faces;

    private GraphicsTexture g2dLayer;
    private MouseClickInfo mouseClickInfo;

    public SignConfigUI(int faces, List<Map<String, List<SignItem>>> items, Consumer<List<Map<String, List<SignItem>>>> setter) {
        super(ComponentHelper.translatable("ui.fangsu.sign.title"));
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
        recreateG2dLayer();
    }

    //#if MC_VERSION >= 12000
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GraphicContext g = GraphicContext.of(graphics);
        renderBackground(graphics);
        //#else
        //$$ @Override
        //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     GraphicContext g = GraphicContext.of(poseStack);
        //$$     renderBackground(poseStack);
        //#endif
        g.fill(0, 0, width, height, 0xFF101010);

        g2dLayer.graphics.setComposite(AlphaComposite.Clear);
        g2dLayer.graphics.fillRect(0, 0, g2dLayer.width, g2dLayer.height);
        g2dLayer.graphics.setComposite(AlphaComposite.SrcOver);

        if (modeFlag == 0) {
            drawSelectionScreen(g, mouseX, mouseY);
        } else if (modeFlag == 1) {
            drawEditingScreen(g, mouseX, mouseY);
        } else if (modeFlag == 2) {
            drawLayoutEditingScreen(g, mouseX, mouseY);
        }

        g.drawString(font, this.title, 10, 2, 0xFFFFFF, false);
        g2dLayer.upload();
        //#if MC_VERSION >= 12000
        g.blit(g2dLayer.identifier, 0, 0, 0, 0, width, height, width, height);
        //#else
        //$$ com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, g2dLayer.identifier);
        //$$ net.minecraft.client.gui.Gui.blit(poseStack, 0, 0, 0, 0, width, height, width, height);
        //#endif

        mouseClickInfo = null;
    }

    @Override
    public void resize(Minecraft client, int width, int height) {
        super.resize(client, width, height);
        recreateG2dLayer();
    }

    private void recreateG2dLayer() {
        if (g2dLayer != null) g2dLayer.close();
        int texW = Math.max(1, width);
        int texH = Math.max(1, height);
        g2dLayer = new GraphicsTexture(texW * G2D_SCALE, texH * G2D_SCALE);
        g2dLayer.graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private void drawSelectionScreen(GraphicContext ctx, int mouseX, int mouseY) {
        int rowHeight = (height - 12) / ROW_COUNT;
        int i = 0;
        float u = Math.min(30f, rowHeight * 0.65f);
        Graphics2D g2d = g2dLayer.graphics;

        for (int side = 0; side < faces; side++) {
            Map<String, List<SignItem>> faceLanes = dispItems.get(side);
            for (int part = 0; part < 3; part++) {
                int rowY = 12 + i * rowHeight;
                int rowBottom = rowY + rowHeight;
                int stripeColor = (i % 2 == 0) ? 0x22ffffff : 0x00ffffff;
                if (mouseY >= rowY && mouseY <= rowBottom) stripeColor = 0x33ffffff;
                ctx.fill(0, rowY, width, rowY + rowHeight, stripeColor);

                ScreenUtil.drawString(ctx.asMinecraft(),
                        ComponentHelper.translatable("ui.fangsu.sign." + faceName(side)).getString() + " - " + ComponentHelper.translatable("ui.fangsu.sign." + partName(part)).getString(),
                        16, rowY + rowHeight / 8, 0xffffffff, rowHeight / 8, false);

                List<SignItem> lane = faceLanes.computeIfAbsent(partName(part), k -> new ArrayList<>());
                float laneStartX = part == 2 ? width + rowScroll[i] : rowScroll[i];
                drawLane(g2d, lane, laneStartX, rowY + rowHeight * 0.3f, part, u, false);

                if (mouseClickInfo != null && mouseClickInfo.button == 0 && mouseClickInfo.mouseY >= rowY && mouseClickInfo.mouseY <= rowBottom) {
                    modeFlag = 1;
                    inEditingRow = new LaneRef(side, part, lane);
                    paletteScroll = 0;
                    editingPreviewScroll = 0;
                    sideEditing = lane.isEmpty() ? -2 : -1;
                }
                i++;
            }
        }
    }

    private void drawEditingScreen(GraphicContext ctx, int mouseX, int mouseY) {
        if (inEditingRow == null) {
            modeFlag = 0;
            return;
        }
        LaneRef laneRef = inEditingRow;
        List<SignItem> lane = laneRef.lane;
        if (lane == null) lane = new ArrayList<>();
        else {
            lane = laneRef.lane;
        }

        ctx.fill(12, 24, width - 12, 78, 0x441E1E1E);
        ctx.drawString(font,
                ComponentHelper.translatable("ui.fangsu.sign.tooltip1", ComponentHelper.translatable("ui.fangsu.sign." + faceName(laneRef.face)).getString() + " - " + ComponentHelper.translatable("ui.fangsu.sign." + partName(laneRef.part)).getString()),
                16, 32, 0xFFFFFF, false);
        ctx.drawString(font, ComponentHelper.translatable("ui.fangsu.sign.tooltip2"), width - 80, 32, 0xCCCCCC, false);

        float u = 24;
        float y = 50;

        float totalWidth = 0;
        for (SignItem token : lane) totalWidth += getTokenWidth(g2dLayer.graphics, token, u) + u * 0.35f;

        float baseX = switch (laneRef.part()) {
            case 2 -> width - 24 - totalWidth;
            case 1 -> (width - totalWidth) / 2f;
            default -> 24;
        };
        float minPreviewScroll = Math.min(0, width - 24 - (baseX + totalWidth));
        float maxPreviewScroll = Math.max(0, 24 - baseX);
        editingPreviewScroll = Math.max(minPreviewScroll, Math.min(maxPreviewScroll, editingPreviewScroll));
        float x = baseX + editingPreviewScroll;

        boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
        float headIndicatorX = x - u * 0.25f;
        if (lane.isEmpty() || sideEditing == -2) {
            if (blink) drawAddIndicator(ctx, headIndicatorX, y, u);
        }
        if (mouseClickInfo != null && mouseClickInfo.button == 0 && mouseClickInfo.mouseX >= headIndicatorX && mouseClickInfo.mouseX <= headIndicatorX + u * 0.5f && mouseClickInfo.mouseY >= y && mouseClickInfo.mouseY <= y + u) {
            sideEditing = -2;
        }

        Graphics2D g = g2dLayer.graphics;
        for (int idx = 0; idx < lane.size(); idx++) {
            SignItem token = lane.get(idx);
            float tokenW = getTokenWidth(g, token, u);

            drawTokenG2D(g, token, x, y, u, laneRef.part(), false);

            boolean addHover = mouseX >= x + tokenW && mouseX <= x + tokenW + u * 0.5f && mouseY >= y && mouseY <= y + u;
            if (addHover || (sideEditing == idx && blink)) drawAddIndicator(ctx, x + tokenW, y, u);
            if (mouseClickInfo != null && mouseClickInfo.button == 0 && addHover) sideEditing = idx;

            boolean hover = mouseX >= x && mouseX <= x + tokenW && mouseY >= y && mouseY <= y + u;
            if (hover) {
                ctx.fill((int) x, (int) y, (int) (x + tokenW), (int) (y + u), 0x33FFFFFF);
                ctx.drawString(font, ComponentHelper.translatable("ui.fangsu.sign.edit_hint"), (int) x, (int) (y - 10), 0xE0E0E0, false);
            }

            if (mouseClickInfo != null && hover) {
                if (mouseClickInfo.button == 1) {
                    lane.remove(idx);
                    sideEditing = -1;
                    break;
                } else if (mouseClickInfo.button == 0 && token instanceof LayoutItem layoutItem) {
                    modeFlag = 2;
                    layoutEditRef = new LayoutEditRef(lane, idx, layoutItem, layoutItem.getLane("top").isEmpty() ? "top" : "bottom");
                    paletteScroll = 0;
                    break;
                } else if (token.getConfigs() != null && !token.getConfigs().isEmpty()) {
                    mouseClickInfo = null;
                    sideEditing = -1;
                    Minecraft.getInstance().setScreen(new ConfigScreen(ComponentHelper.translatable("ui.fangsu.common.config"), token.getConfigs(), this));
                    break;
                }
            }
            x += tokenW + u * 0.35f;
        }

        List<SignItem> finalLane = lane;
        drawPalette(ctx, mouseX, mouseY, lane, item -> {
            int insertIndex = sideEditing == -2 ? 0 : (sideEditing >= 0 && sideEditing < finalLane.size() ? sideEditing + 1 : finalLane.size());
            finalLane.add(insertIndex, item);
        });
    }

    private void drawLayoutEditingScreen(GraphicContext ctx, int mouseX, int mouseY) {
        if (layoutEditRef == null) {
            modeFlag = 1;
            return;
        }

        LayoutItem layoutItem = layoutEditRef.layoutItem;
        ctx.fill(12, 24, width - 12, 140, 0x441E1E1E);
        ctx.drawString(font, ComponentHelper.translatable("ui.fangsu.sign.layout_title"), 16, 32, 0xFFFFFF, false);
        ctx.drawString(font, ComponentHelper.translatable("ui.fangsu.sign.layout_hint"), 16, 45, 0xCCCCCC, false);

        int boxX = 20;
        int boxW = width - 40;
        int topY = 62;
        int rowH = 28;

        drawSubLaneEditor(ctx, layoutItem, "top", boxX, topY, boxW, rowH, mouseX, mouseY);
        drawSubLaneEditor(ctx, layoutItem, "bottom", boxX, topY + rowH + 8, boxW, rowH, mouseX, mouseY);

        drawPalette(ctx, mouseX, mouseY, layoutItem.getLane(layoutEditRef.selectedLaneKey), item ->
                layoutItem.getLane(layoutEditRef.selectedLaneKey).add(item)
        );
    }

    private void drawSubLaneEditor(GraphicContext ctx, LayoutItem layoutItem, String laneKey, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean selectedLane = layoutEditRef != null && laneKey.equals(layoutEditRef.selectedLaneKey);
        ctx.fill(x, y, x + w, y + h, selectedLane ? 0x33336699 : 0x22000000);

        List<SignItem> lane = layoutItem.getLane(laneKey);
        Graphics2D g = g2dLayer.graphics;
        float unit = h;
        float drawX = x + 8;
        for (int i = 0; i < lane.size(); i++) {
            SignItem token = lane.get(i);
            float tokenW = token.getWidth(g, unit);
            drawTokenG2D(g, token, drawX, y, unit, 0, false);

            boolean hover = mouseX >= drawX && mouseX <= drawX + tokenW && mouseY >= y && mouseY <= y + h;
            if (hover && mouseClickInfo != null) {
                if (mouseClickInfo.button == 1) {
                    lane.remove(i);
                    break;
                } else if (mouseClickInfo.button == 0 && token.getConfigs() != null && !token.getConfigs().isEmpty()) {
                    mouseClickInfo = null;
                    Minecraft.getInstance().setScreen(new ConfigScreen(ComponentHelper.translatable("ui.fangsu.common.config"), token.getConfigs(), this));
                    return;
                }
            }
            drawX += tokenW + unit * 0.2f;
        }

        if (lane.isEmpty() && selectedLane) {
            drawDashedRect(g, x + 8, y + 3, w - 16, h - 6, new Color(255, 255, 255, 200));
        }

        if (mouseClickInfo != null && mouseClickInfo.button == 0 && mouseClickInfo.mouseX >= x && mouseClickInfo.mouseX <= x + w && mouseClickInfo.mouseY >= y && mouseClickInfo.mouseY <= y + h) {
            layoutEditRef = new LayoutEditRef(layoutEditRef.parentLane, layoutEditRef.itemIndex, layoutEditRef.layoutItem, laneKey);
        }
    }

    private void drawPalette(GraphicContext ctx, int mouseX, int mouseY, List<SignItem> targetLane, Consumer<SignItem> inserter) {
        int top = height / 2;
        int cell = 26;
        int gap = 6;
        int usableWidth = width - 32;
        int lineItems = Math.max(1, usableWidth / (cell + gap));
        int contentHeight = ((EDITOR_ITEMS.size() + lineItems - 1) / lineItems) * (cell + gap);
        ctx.enableScissor(12, top, width - 12, height - 12);
        for (int idx = 0; idx < EDITOR_ITEMS.size(); idx++) {
            int row = idx / lineItems;
            int col = idx % lineItems;
            int x = 16 + col * (cell + gap);
            int y = top + (int) paletteScroll + row * (cell + gap);
            if (y > height || y + cell < top) continue;
            boolean hover = mouseX >= x && mouseX <= x + cell && mouseY >= y && mouseY <= y + cell;
            ctx.fill(x, y, x + cell, y + cell, hover ? 0x33FFFFFF : 0x22000000);
            int border = hover ? 0x88FFFFFF : 0x44000000;
            ctx.fill(x, y, x + cell, y + 1, border);
            ctx.fill(x, y + cell - 1, x + cell, y + cell, border);
            ctx.fill(x, y, x + 1, y + cell, border);
            ctx.fill(x + cell - 1, y, x + cell, y + cell, border);
            SignItem token = EDITOR_ITEMS.get(idx);
            var location = token.getIconLocation() == null ? new ResourceLocation("mtrsteamloco:imgnnotfound.png") : token.getIconLocation();
            ctx.blit(location, x + 3, y + 3, 0, 0, cell - 6, cell - 6, cell - 6, cell - 6);
            if (hover) ctx.drawString(font, "+", x + cell / 2 - 3, y + cell / 2 - 4, 0xFFFFFF, false);
            if (hover && token.withText) {
                ctx.drawString(font, ComponentHelper.translatable("ui.fangsu.sign.tooltip3"), width - 80, 32, 0xCCCCCC, false);
            }
            if (hover && mouseClickInfo != null && (mouseClickInfo.button == 0 || mouseClickInfo.button == 1 || mouseClickInfo.button == 2)) {
                SignItem newItem = copySignItem(token);
                if (newItem == null) continue;
                if (token.withText && token.text != null && !token.text.isEmpty() && targetLane != null && modeFlag == 1) {
                    insertWithText(targetLane, inserter, newItem, token, mouseClickInfo.button);
                } else {
                    inserter.accept(newItem);
                }
                sideEditing = -1;
            }
        }
        ctx.disableScissor();
        float minScroll = -Math.max(0, contentHeight - (height - top - 12));
        paletteScroll = Math.max(minScroll, Math.min(0, paletteScroll));
    }

    private void insertWithText(List<SignItem> lane, Consumer<SignItem> inserter, SignItem newItem, SignItem token, int button) {
        TextItem textItem = createTextItem(token.text);
        int beforeSize = lane.size();
        if (button == 0) {
            inserter.accept(textItem.setAlign(0));
            inserter.accept(newItem);
        } else if (button == 1) {
            inserter.accept(newItem);
            inserter.accept(textItem.setAlign(2));
        } else {
            inserter.accept(newItem);
        }
        if (beforeSize == lane.size()) inserter.accept(newItem);
    }

    private TextItem createTextItem(String text) {
        JsonObject json = new JsonObject();
        json.addProperty("text", text);
        return new TextItem(json);
    }

    private SignItem copySignItem(SignItem item) {
        try {
            JsonObject json = item.toJson();
            String type = json.get("type").getAsString();
            json.remove("type");
            return SignItemFactory.get(type).apply(deepCopy(json));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void drawLane(Graphics2D g, List<SignItem> lane, float startX, float y, int align, float u, boolean selected) {
        if (lane == null || lane.isEmpty()) return;
//        Shape oriClip = g.getClip();
        float x = startX;
        if (align == 2) {
            float totalWidth = 0;
            for (SignItem token : lane) totalWidth += getTokenWidth(g, token, u) + u * 0.1f;
            x = startX - totalWidth;
        } else if (align == 1) {
            float totalWidth = 0;
            for (SignItem token : lane) totalWidth += getTokenWidth(g, token, u) + u * 0.1f;
            x = startX + (this.width - totalWidth) / 2f;
        }
        for (SignItem token : lane) {
            float tokenWidth = getTokenWidth(g, token, u);
//            g.setClip(new Rectangle((int) x, (int) y, (int) tokenWidth, (int) u));
            drawTokenG2D(g, token, x, y, u, align, selected);
            x += tokenWidth + u * 0.1f;
//            g.setClip(oriClip);
        }
    }

    private float getTokenWidth(Graphics2D graphics, SignItem token, float unit) {
        return token.getWidth(graphics, unit);
    }

    private void drawTokenG2D(Graphics2D g, SignItem token, float x, float y, float unit, int align, boolean selected) {
        SignDrawContext ctx = new SignDrawContext(g, getG2dX(x), getG2dY(y), getG2dU(unit), align, selected);
        token.draw(ctx);
    }

    private void drawDashedRect(Graphics2D g, int x, int y, int w, int h, Color color) {
        Stroke original = g.getStroke();
        g.setColor(color);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4f, 4f}, 0));
        g.drawRect(getG2dX(x), getG2dY(y), getG2dU(w), getG2dU(h));
        g.setStroke(original);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseClickInfo = new MouseClickInfo(mouseX, mouseY, button);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (modeFlag == 0) {
            int rowHeight = (height - 12) / ROW_COUNT;
            for (int i = 0; i < ROW_COUNT; i++) {
                int rowY = 12 + i * rowHeight;
                if (mouseY >= rowY && mouseY <= rowY + rowHeight) {
                    rowScroll[i] += (float) (delta * 8f);
                    return true;
                }
            }
        } else {
            if (modeFlag == 1 && mouseY >= 24 && mouseY <= 78) {
                editingPreviewScroll += (float) (delta * 10f);
                return true;
            }
            if (mouseY >= 170) {
                paletteScroll += (float) (delta * 10f);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (modeFlag == 2) {
                modeFlag = 1;
                layoutEditRef = null;
                return true;
            }
            modeFlag--;
            if (modeFlag < 0) onClose();
            else if (modeFlag == 0) {
                inEditingRow = null;
                editingPreviewScroll = 0;
                sideEditing = -1;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.modeFlag--;
        if (modeFlag == 1) {
            layoutEditRef = null;
        } else if (modeFlag == 0) {
            inEditingRow = null;
            editingPreviewScroll = 0;
            sideEditing = -1;
        } else if (this.modeFlag < 0) {
            setter.accept(dispItems);
            g2dLayer.close();
            super.onClose();
        }
    }

    private String partName(int part) {
        return switch (part) {
            case 0 -> "left";
            case 1 -> "center";
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

    private record LayoutEditRef(List<SignItem> parentLane, int itemIndex, LayoutItem layoutItem,
                                 String selectedLaneKey) {
    }

    private record MouseClickInfo(double mouseX, double mouseY, int button) {
    }

    private void drawAddIndicator(GraphicContext ctx, float x, float y, float u) {
        int color = 0xFFFFFFFF;
        int w = Math.round(u * 0.5f);
        int h = Math.round(u);
        int px = Math.round(x);
        int py = Math.round(y);
        int shortEdge = Math.max(1, Math.round(u * 0.075f));
        int longEdge = Math.round(u * 0.15f);
        int plusLongEdge = Math.round(u * 0.25f);

        ctx.fill(px, py, px + shortEdge, py + longEdge, color);
        ctx.fill(px, py, px + longEdge, py + shortEdge, color);
        ctx.fill(px + w - shortEdge, py, px + w, py + longEdge, color);
        ctx.fill(px + w - longEdge, py, px + w, py + shortEdge, color);
        ctx.fill(px, py + h - longEdge, px + shortEdge, py + h, color);
        ctx.fill(px, py + h - shortEdge, px + longEdge, py + h, color);
        ctx.fill(px + w - shortEdge, py + h - longEdge, px + w, py + h, color);
        ctx.fill(px + w - longEdge, py + h - shortEdge, px + w, py + h, color);

        int centerX = px + w / 2;
        int centerY = py + h / 2;
        ctx.fill(centerX - plusLongEdge / 2, centerY - shortEdge / 2, centerX + plusLongEdge / 2, centerY + shortEdge / 2, color);
        ctx.fill(centerX - shortEdge / 2, centerY - plusLongEdge / 2, centerX + shortEdge / 2, centerY + plusLongEdge / 2, color);
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
}
