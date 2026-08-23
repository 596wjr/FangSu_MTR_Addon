package com.fangsu.ui;

import com.fangsu.Main;
import com.fangsu.drawing.sign.*;
import com.fangsu.extraConfig.ConfigEntry;
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
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.fangsu.drawing.sign.SignItemFactory.EDITOR_ITEMS;

public class SignConfigUI extends Screen {

    private static final int ROW_COUNT = 6; // 可视行数（2 个面 × 3 列），多于 2 个面时纵向滚动
    private static final int G2D_SCALE = 4;

    private final List<SignFaceData> facesData;
    private final Consumer<List<SignFaceData>> setter;

    private int modeFlag = 0;
    private LaneRef inEditingRow = null;
    private LayoutEditRef layoutEditRef = null;
    private final Deque<LayoutEditRef> layoutStack = new ArrayDeque<>();
    private int sideEditing = -1; // -2 = head insert
    private float[] rowScroll = new float[0];
    private float paletteScroll = 0;
    private float editingPreviewScroll = 0;
    private float selectionScroll = 0;

    private GraphicsTexture g2dLayer;
    private MouseClickInfo mouseClickInfo;

    /* ===================== 滚动条拖动状态 ===================== */

    private boolean draggingPaletteScroll = false;
    private int paletteDragOffset = 0;
    private boolean draggingRowScroll = false;
    private int draggingRowIndex = -1;
    private boolean draggingSelectionScroll = false;
    private int selectionDragOffset = 0;

    public SignConfigUI(List<SignFaceData> facesData, Consumer<List<SignFaceData>> setter) {
        super(ComponentHelper.translatable("ui.fangsu.sign.title"));
        this.facesData = facesData;
        this.setter = setter;
    }

    @Override
    protected void init() {
        super.init();
        if (rowScroll.length != facesData.size() * 3) {
            rowScroll = new float[facesData.size() * 3];
        }
        recreateG2dLayer();
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
        g.fill(0, 0, width, height, 0xFF101010);

        g2dLayer.graphics.setComposite(AlphaComposite.Clear);
        g2dLayer.graphics.fillRect(0, 0, g2dLayer.width, g2dLayer.height);
        g2dLayer.graphics.setComposite(AlphaComposite.SrcOver);

        if (modeFlag == 0) {
            drawSelectionScreen(g, mouseX, mouseY);
        } else if (modeFlag == 1) {
            drawEditingScreen(g, mouseX, mouseY);
        } else if (modeFlag == 2) {
            drawLayoutRowSelectScreen(g, mouseX, mouseY);
        } else if (modeFlag == 3) {
            drawLayoutRowEditScreen(g, mouseX, mouseY);
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
    public void resize(@NotNull Minecraft client, int width, int height) {
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
        int viewportH = height - 12;
        float u = Math.min(30f, rowHeight * 0.65f);
        Graphics2D g2d = g2dLayer.graphics;

        // 4 面分两列避免滚动；其余单列（>4 面才纵向滚动）
        int maxScroll = selMaxScroll();
        selectionScroll = Math.max(-maxScroll, Math.min(0, selectionScroll));
        int colorBarW = 14;

        for (int side = 0; side < facesData.size(); side++) {
            int colX = selColX(side);
            int colW = selColW();
            int colorBarX = colX + colW - 18;
            boolean overColorBar = mouseX >= colorBarX && mouseX <= colorBarX + colorBarW;
            SignFaceData face = facesData.get(side);
            Map<String, List<SignItem>> faceLanes = face.getLanes();
            for (int part = 0; part < 3; part++) {
                int i = side * 3 + part;
                int rowY = selRowY(side, part);
                int rowBottom = rowY + rowHeight;
                // 跳过屏幕外的行
                if (rowBottom < 12 || rowY > height) continue;
                int stripeColor = (i % 2 == 0) ? 0x22ffffff : 0x00ffffff;
                // 鼠标指向最右侧色条时不高亮该行
                if (!overColorBar && mouseY >= rowY && mouseY <= rowBottom) stripeColor = 0x33ffffff;
                ctx.fill(colX, rowY, colX + colW, rowY + rowHeight, stripeColor);

                ScreenUtil.drawString(ctx.asMinecraft(),
                        faceLabel(side) + " - " + ComponentHelper.translatable("ui.fangsu.sign." + partName(part)).getString(),
                        colX + 16, rowY + rowHeight / 8, 0xffffffff, rowHeight / 8, false);

                List<SignItem> lane = faceLanes.computeIfAbsent(partName(part), k -> new ArrayList<>());
                float laneStartX = part == 2 ? (colX + colW) + rowScroll[i] : colX + rowScroll[i];
                drawLane(g2d, lane, laneStartX, rowY + rowHeight * 0.3f, part, u, false);

                // 水平滚动条（宽度为该列宽）
                if (lane != null && !lane.isEmpty()) {
                    float totalLaneWidth = 0;
                    for (SignItem token : lane) totalLaneWidth += getTokenWidth(g2d, token, u) + u * 0.1f;
                    int scrollbarY = rowBottom - 4;
                    int scrollbarW = colW;
                    if (totalLaneWidth > scrollbarW) {
                        ctx.fill(colX, scrollbarY, colX + scrollbarW, scrollbarY + 4, 0x30FFFFFF);
                        float ratio = -rowScroll[i] / Math.max(1, totalLaneWidth - scrollbarW);
                        int thumbW = Math.max(10, (int) (scrollbarW * (float) scrollbarW / totalLaneWidth));
                        int thumbX = colX + (int) (ratio * (scrollbarW - thumbW));
                        ctx.fill(thumbX, scrollbarY, thumbX + thumbW, scrollbarY + 4, 0x99FFFFFF);
                    }
                }

                if (mouseClickInfo != null && mouseClickInfo.button == 0
                        && mouseClickInfo.mouseX >= colX && mouseClickInfo.mouseX <= colX + colW
                        && mouseClickInfo.mouseY >= rowY && mouseClickInfo.mouseY <= rowBottom) {
                    modeFlag = 1;
                    inEditingRow = new LaneRef(side, part, lane);
                    paletteScroll = 0;
                    editingPreviewScroll = 0;
                    sideEditing = lane.isEmpty() ? -2 : -1;
                }
            }

            // 每个面最右侧的色条，点击进入颜色选择 UI
            int barTop = 12 + selFaceInCol(side) * 3 * rowHeight + (int) selectionScroll;
            int barBottom = barTop + 3 * rowHeight;
            if (barBottom >= 12 && barTop <= height) {
                boolean barHover = overColorBar && mouseY >= barTop && mouseY <= barBottom;
                int border = barHover ? 0xFFFFFFFF : 0xFF888888;
                int barFill = face.hasBgColor() ? face.getBgColor() : 0x00000000;
                ctx.fill(colorBarX, barTop, colorBarX + colorBarW, barBottom, barFill);
                ctx.fill(colorBarX, barTop, colorBarX + colorBarW, barTop + 1, border);
                ctx.fill(colorBarX, barBottom - 1, colorBarX + colorBarW, barBottom, border);
                ctx.fill(colorBarX, barTop, colorBarX + 1, barBottom, border);
                ctx.fill(colorBarX + colorBarW - 1, barTop, colorBarX + colorBarW, barBottom, border);
                // 透明背景画斜线提示
                if (!face.hasBgColor()) {
                    for (int k = 0; k < 3 * rowHeight; k += 4) {
                        int lx = colorBarX + 1 + (k % (colorBarW - 2));
                        int ly = barTop + k;
                        if (ly < barBottom && lx < colorBarX + colorBarW - 1) ctx.fill(lx, ly, lx + 1, ly + 1, 0xFFFFFFFF);
                    }
                }
            }
        }

        // 纵向滚动条（仅单列且超出视口时显示）
        if (maxScroll > 0) {
            int sbX = width - 4, sbW = 4;
            ctx.fill(sbX, 12, sbX + sbW, 12 + viewportH, 0x30FFFFFF);
            int thumbH = Math.max(10, (int) (viewportH * (float) viewportH / (facesData.size() * 3 * rowHeight)));
            int thumbY = 12 + (int) ((float) (-selectionScroll) / maxScroll * (viewportH - thumbH));
            ctx.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0x99FFFFFF);
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
                ComponentHelper.translatable("ui.fangsu.sign.tooltip1", faceLabel(laneRef.face) + " - " + ComponentHelper.translatable("ui.fangsu.sign." + partName(laneRef.part)).getString()),
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

        drawEditableLane(ctx, mouseX, mouseY, lane, laneRef.part(), u, y, x);

        List<SignItem> finalLane = lane;
        drawPalette(ctx, mouseX, mouseY, lane, item -> {
            int insertIndex = sideEditing == -2 ? 0 : (sideEditing >= 0 && sideEditing < finalLane.size() ? sideEditing + 1 : finalLane.size());
            finalLane.add(insertIndex, item);
        });
    }

    /**
     * 布局容器行选择界面（同选择正面-左侧那个 UI）：列出该布局容器的每一行（子行），
     * 点击某一行进入该行的编辑页。右上角有容器配置按钮。
     */
    private void drawLayoutRowSelectScreen(GraphicContext ctx, int mouseX, int mouseY) {
        if (layoutEditRef == null) {
            modeFlag = 1;
            return;
        }
        LayoutItem item = layoutEditRef.layoutItem;
        List<String> laneKeys = item.getLaneKeys();

        int boxTop = 24;
        int rowH = 46;
        int boxBottom = boxTop + laneKeys.size() * (rowH + 6) + 8;
        ctx.fill(12, boxTop, width - 12, boxBottom, 0x441E1E1E);
        ctx.drawString(font, ComponentHelper.translatable("ui.fangsu.sign.layout_title"), 16, 32, 0xFFFFFF, false);
        drawLayoutConfigButton(ctx, mouseX, mouseY);

        Graphics2D g = g2dLayer.graphics;
        int rowX = 20;
        int rowW = width - 40;
        for (int i = 0; i < laneKeys.size(); i++) {
            String key = laneKeys.get(i);
            int rowY = boxTop + 12 + i * (rowH + 6);
            List<SignItem> lane = item.getLane(key);
            boolean hover = mouseX >= rowX && mouseX <= rowX + rowW && mouseY >= rowY && mouseY <= rowY + rowH;
            ctx.fill(rowX, rowY, rowX + rowW, rowY + rowH, hover ? 0x33336699 : 0x22000000);
            ctx.drawString(font, ComponentHelper.translatable("ui.fangsu.sign.layout_row", i + 1), rowX + 8, rowY + 8, 0xFFFFFF, false);

            // 行内容预览
            float unit = rowH * 0.5f;
            float drawX = rowX + 8 + 70;
            float ty = rowY + (rowH - unit) / 2f;
            for (SignItem token : lane) {
                float tw = token.getWidth(g, unit);
                drawTokenG2D(g, token, drawX, ty, unit, 0, false);
                drawX += tw + unit * 0.2f;
            }

            if (mouseClickInfo != null && mouseClickInfo.button == 0 && hover) {
                mouseClickInfo = null;
                replaceTopLayout(new LayoutEditRef(layoutEditRef.parentLane, layoutEditRef.itemIndex, layoutEditRef.layoutItem, key, true));
                modeFlag = 3;
                sideEditing = -1;
                editingPreviewScroll = 0;
                return;
            }
        }
    }

    /**
     * 布局容器单行编辑界面（与编辑指示牌内容相同），额外提供 上一行/下一行/容器配置 按钮。
     */
    private void drawLayoutRowEditScreen(GraphicContext ctx, int mouseX, int mouseY) {
        if (layoutEditRef == null) {
            modeFlag = 2;
            return;
        }
        LayoutItem item = layoutEditRef.layoutItem;
        List<String> laneKeys = item.getLaneKeys();
        String laneKey = layoutEditRef.selectedLaneKey;
        int laneIndex = Math.max(0, laneKeys.indexOf(laneKey));
        List<SignItem> lane = item.getLane(laneKey);

        ctx.fill(12, 24, width - 12, 78, 0x441E1E1E);
        ctx.drawString(font, ComponentHelper.translatable("ui.fangsu.sign.layout_edit_title",
                ComponentHelper.translatable("ui.fangsu.sign.layout_row", laneIndex + 1).getString()), 16, 32, 0xFFFFFF, false);
        drawLayoutRowNav(ctx, mouseX, mouseY, laneIndex, laneKeys);

        float u = 24;
        float y = 50;

        float totalWidth = 0;
        for (SignItem token : lane) totalWidth += getTokenWidth(g2dLayer.graphics, token, u) + u * 0.35f;

        float baseX = 24;
        float minPreviewScroll = Math.min(0, width - 24 - (baseX + totalWidth));
        float maxPreviewScroll = Math.max(0, 24 - baseX);
        editingPreviewScroll = Math.max(minPreviewScroll, Math.min(maxPreviewScroll, editingPreviewScroll));
        float x = baseX + editingPreviewScroll;

        drawEditableLane(ctx, mouseX, mouseY, lane, 0, u, y, x);

        List<SignItem> finalLane = lane;
        drawPalette(ctx, mouseX, mouseY, lane, item2 -> finalLane.add(item2));
    }

    private void drawLayoutRowNav(GraphicContext ctx, int mouseX, int mouseY, int laneIndex, List<String> laneKeys) {
        int btnH = 18;
        int y = 32;
        int cfgW = 70;
        int cfgX = width - 90;
        int navW = 60;
        int nextX = cfgX - navW - 6;
        int prevX = nextX - navW - 6;

        drawNavButton(ctx, mouseX, mouseY, cfgX, y, cfgW, btnH, "ui.fangsu.sign.layout_config", () -> {
            List<ConfigEntry<?>> configs = layoutEditRef.layoutItem.getConfigs();
            if (configs != null && !configs.isEmpty()) {
                mouseClickInfo = null;
                Minecraft.getInstance().setScreen(new ConfigScreen(ComponentHelper.translatable("ui.fangsu.common.config"), configs, this));
            }
        });
        drawNavButton(ctx, mouseX, mouseY, nextX, y, navW, btnH, "ui.fangsu.sign.layout_next", () -> {
            if (laneIndex < laneKeys.size() - 1) {
                mouseClickInfo = null;
                switchLane(laneKeys.get(laneIndex + 1));
            }
        });
        drawNavButton(ctx, mouseX, mouseY, prevX, y, navW, btnH, "ui.fangsu.sign.layout_prev", () -> {
            if (laneIndex > 0) {
                mouseClickInfo = null;
                switchLane(laneKeys.get(laneIndex - 1));
            }
        });
    }

    private void drawNavButton(GraphicContext ctx, int mouseX, int mouseY, int x, int y, int w, int h, String langKey, Runnable onClick) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        ctx.fill(x, y, x + w, y + h, hover ? 0x66FFFFFF : 0x33FFFFFF);
        ctx.drawString(font, ComponentHelper.translatable(langKey), x + 4, y + 5, 0xFFFFFF, false);
        if (mouseClickInfo != null && mouseClickInfo.button == 0 && hover) {
            onClick.run();
        }
    }

    private void drawLayoutConfigButton(GraphicContext ctx, int mouseX, int mouseY) {
        if (layoutEditRef == null) return;
        List<ConfigEntry<?>> configs = layoutEditRef.layoutItem.getConfigs();
        if (configs == null || configs.isEmpty()) return;
        int btnX = width - 90, btnY = 32, btnW = 70, btnH = 18;
        boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHover ? 0x66FFFFFF : 0x33FFFFFF);
        ctx.drawString(font, ComponentHelper.translatable("ui.fangsu.sign.layout_config"), btnX + 4, btnY + 5, 0xFFFFFF, false);
        if (mouseClickInfo != null && mouseClickInfo.button == 0 && btnHover) {
            mouseClickInfo = null;
            Minecraft.getInstance().setScreen(new ConfigScreen(ComponentHelper.translatable("ui.fangsu.common.config"), configs, this));
        }
    }

    /**
     * 绘制一条可编辑的内容行（横向 token + 头部插入指示 + 逐个 token 的悬停/删除/配置/嵌套进入）。
     * 供指示牌内容编辑（mode 1）与布局容器单行编辑（mode 3）共用。
     */
    private void drawEditableLane(GraphicContext ctx, int mouseX, int mouseY, List<SignItem> lane, int align, float u, float y, float x) {
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

            drawTokenG2D(g, token, x, y, u, align, false);

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
                    mouseClickInfo = null;
                    enterLayout(layoutItem, lane, idx);
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
    }

    private void enterLayout(LayoutItem layoutItem, List<SignItem> parentLane, int itemIndex) {
        boolean multi = countNonEmptyLanes(layoutItem) > 1;
        pushLayout(new LayoutEditRef(parentLane, itemIndex, layoutItem, defaultLaneKey(layoutItem), multi));
        modeFlag = multi ? 2 : 3;
    }

    private void pushLayout(LayoutEditRef ref) {
        layoutStack.push(ref);
        layoutEditRef = ref;
        paletteScroll = 0;
        sideEditing = -1;
        editingPreviewScroll = 0;
    }

    private void replaceTopLayout(LayoutEditRef ref) {
        layoutStack.pop();
        layoutStack.push(ref);
        layoutEditRef = ref;
    }

    private void switchLane(String key) {
        LayoutEditRef ref = layoutEditRef;
        replaceTopLayout(new LayoutEditRef(ref.parentLane, ref.itemIndex, ref.layoutItem, key, ref.selectShown()));
        sideEditing = -1;
        editingPreviewScroll = 0;
    }

    private void popLayoutToParent() {
        layoutStack.pop();
        layoutEditRef = layoutStack.peek();
        sideEditing = -1;
        editingPreviewScroll = 0;
        if (layoutEditRef == null) {
            modeFlag = 1;
        } else {
            modeFlag = 3;
        }
    }

    private int countNonEmptyLanes(LayoutItem item) {
        int c = 0;
        for (String key : item.getLaneKeys()) {
            if (!item.getLane(key).isEmpty()) c++;
        }
        return c;
    }

    private String defaultLaneKey(LayoutItem item) {
        for (String key : item.getLaneKeys()) {
            if (!item.getLane(key).isEmpty()) return key;
        }
        return item.getLaneKeys().get(0);
    }

    private void drawPalette(GraphicContext ctx, int mouseX, int mouseY, List<SignItem> targetLane, Consumer<SignItem> inserter) {
        int top = height / 2;
        int cell = 26;
        int gap = 6;
        int usableWidth = width - 32;
        int lineItems = Math.max(1, usableWidth / (cell + gap));
        final var itemsList = new ArrayList<>(EDITOR_ITEMS);
        int contentHeight = ((itemsList.size() + lineItems - 1) / lineItems) * (cell + gap);
        ctx.enableScissor(12, top, width - 12, height - 12);
        for (int idx = 0; idx < itemsList.size(); idx++) {
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
            SignItem token = itemsList.get(idx);
            var location = token.getIconLocation() == null ? new com.fangsu.mappings.ResourceLocation("mtrsteamloco:imgnnotfound.png") : token.getIconLocation();
            ctx.blit(location.getRaw(), x + 3, y + 3, 0, 0, cell - 6, cell - 6, cell - 6, cell - 6);
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

        // 调色板垂直滚动条
        int paletteAreaHeight = height - top - 12;
        if (contentHeight > paletteAreaHeight) {
            int scrollbarX = width - 6;
            int scrollbarW = 4;
            ctx.fill(scrollbarX, top, scrollbarX + scrollbarW, height - 12, 0x30FFFFFF);
            float ratio = -paletteScroll / Math.max(1, contentHeight - paletteAreaHeight);
            int thumbH = Math.max(10, (int) (paletteAreaHeight * (float) paletteAreaHeight / contentHeight));
            int thumbY = top + (int) (ratio * (paletteAreaHeight - thumbH));
            ctx.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0x99FFFFFF);
        }

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
        } catch (Exception e) {
            Main.LOGGER.warn("[FangSu] SignConfigUI.copySignItem failed, type={}, json={}", item == null ? null : item.getType(), item == null ? null : item.toJson(), e);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 检查调色板滚动条点击
            if (modeFlag != 0) {
                int top = height / 2;
                int paletteAreaHeight = height - top - 12;
                int cell = 26;
                int gap = 6;
                int usableWidth = width - 32;
                int lineItems = Math.max(1, usableWidth / (cell + gap));
                int contentHeight = ((EDITOR_ITEMS.size() + lineItems - 1) / lineItems) * (cell + gap);
                if (contentHeight > paletteAreaHeight) {
                    int sbX = width - 5;
                    int sbW = 4;
                    int sbLeft = sbX;
                    int sbRight = sbX + sbW;
                    if (mouseX >= sbLeft && mouseX <= sbRight && mouseY >= top && mouseY <= height - 12) {
                        float ratio = -paletteScroll / Math.max(1, contentHeight - paletteAreaHeight);
                        int thumbH = Math.max(10, (int) (paletteAreaHeight * (float) paletteAreaHeight / contentHeight));
                        int thumbY = top + (int) (ratio * (paletteAreaHeight - thumbH));
                        if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                            draggingPaletteScroll = true;
                            paletteDragOffset = (int) (mouseY - thumbY);
                            return true;
                        } else {
                            // 轨道点击翻页
                            int page = paletteAreaHeight / 2;
                            if (mouseY < thumbY) {
                                paletteScroll = Math.max(-(contentHeight - paletteAreaHeight), paletteScroll + page);
                            } else {
                                paletteScroll = Math.min(0, paletteScroll - page);
                            }
                            return true;
                        }
                    }
                }
            }
            // 检查选择模式下行水平滚动条点击
            if (modeFlag == 0) {
                int totalRows = facesData.size() * 3;
                int rowHeight = (height - 12) / ROW_COUNT;
                int viewportH = height - 12;
                int maxScroll = selMaxScroll();
                float u = Math.min(30f, rowHeight * 0.65f);
                for (int i = 0; i < totalRows; i++) {
                    int side = i / 3;
                    int part = i % 3;
                    if (side >= facesData.size()) break;
                    int colX = selColX(side);
                    int colW = selColW();
                    int rowY = selRowY(side, part);
                    int rowBottom = rowY + rowHeight;
                    if (mouseX >= colX && mouseX <= colX + colW && mouseY >= rowBottom - 6 && mouseY <= rowBottom) {
                        // 计算该行人lane总宽度
                        Map<String, List<SignItem>> faceLanes = facesData.get(side).getLanes();
                        List<SignItem> lane = faceLanes.get(partName(part));
                        if (lane != null && !lane.isEmpty()) {
                            float totalLaneWidth = 0;
                            for (SignItem token : lane) totalLaneWidth += getTokenWidth(g2dLayer.graphics, token, u) + u * 0.1f;
                            if (totalLaneWidth > colW) {
                                float ratio = -rowScroll[i] / Math.max(1, totalLaneWidth - colW);
                                int thumbW = Math.max(10, (int) (colW * (float) colW / totalLaneWidth));
                                int thumbX = colX + (int) (ratio * (colW - thumbW));
                                if (mouseX >= thumbX && mouseX <= thumbX + thumbW) {
                                    draggingRowScroll = true;
                                    draggingRowIndex = i;
                                    return true;
                                }
                            }
                        }
                    }
                }
                // 色条点击 -> 打开颜色选择 UI
                int colorBarW = 14;
                for (int side = 0; side < facesData.size(); side++) {
                    int colX = selColX(side);
                    int colorBarX = colX + selColW() - 18;
                    int barTop = 12 + selFaceInCol(side) * 3 * rowHeight + (int) selectionScroll;
                    int barBottom = barTop + 3 * rowHeight;
                    if (mouseX >= colorBarX && mouseX <= colorBarX + colorBarW && mouseY >= barTop && mouseY <= barBottom) {
                        SignFaceData face = facesData.get(side);
                        mouseClickInfo = null;
                        Minecraft.getInstance().setScreen(new SignColorPickerUI(this, face.getBgColor(), c -> face.setBgColor(c)));
                        return true;
                    }
                }
                // 纵向滚动条
                if (maxScroll > 0 && mouseX >= width - 4 && mouseX <= width) {
                    int thumbH = Math.max(10, (int) (viewportH * (float) viewportH / (totalRows * rowHeight)));
                    int thumbY = 12 + (int) ((float) (-selectionScroll) / maxScroll * (viewportH - thumbH));
                    if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                        draggingSelectionScroll = true;
                        selectionDragOffset = (int) (mouseY - thumbY);
                        return true;
                    }
                }
            }
        }
        mouseClickInfo = new MouseClickInfo(mouseX, mouseY, button);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingPaletteScroll = false;
        draggingRowScroll = false;
        draggingRowIndex = -1;
        draggingSelectionScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingPaletteScroll && button == 0) {
            int top = height / 2;
            int paletteAreaHeight = height - top - 12;
            int cell = 26;
            int gap = 6;
            int usableWidth = width - 32;
            int lineItems = Math.max(1, usableWidth / (cell + gap));
            int contentHeight = ((EDITOR_ITEMS.size() + lineItems - 1) / lineItems) * (cell + gap);
            int maxScroll = Math.max(0, contentHeight - paletteAreaHeight);
            if (maxScroll > 0) {
                float ratio = (float) (mouseY - top - paletteDragOffset) / (paletteAreaHeight - 10);
                ratio = Math.max(0, Math.min(1, ratio));
                paletteScroll = -ratio * maxScroll;
            }
            return true;
        }
        if (draggingSelectionScroll && button == 0) {
            int totalRows = facesData.size() * 3;
            int rowHeight = (height - 12) / ROW_COUNT;
            int viewportH = height - 12;
            int maxScroll = Math.max(0, totalRows * rowHeight - viewportH);
            if (maxScroll > 0) {
                int thumbH = Math.max(10, (int) (viewportH * (float) viewportH / (totalRows * rowHeight)));
                float ratio = (float) (mouseY - 12 - selectionDragOffset) / (viewportH - thumbH);
                ratio = Math.max(0, Math.min(1, ratio));
                selectionScroll = -ratio * maxScroll;
            }
            return true;
        }
        if (draggingRowScroll && draggingRowIndex >= 0 && button == 0) {
            int rowHeight = (height - 12) / ROW_COUNT;
            float u = Math.min(30f, rowHeight * 0.65f);
            int side = draggingRowIndex / 3;
            int part = draggingRowIndex % 3;
            if (side < facesData.size()) {
                Map<String, List<SignItem>> faceLanes = facesData.get(side).getLanes();
                List<SignItem> lane = faceLanes.get(partName(part));
                if (lane != null && !lane.isEmpty()) {
                    int colX = selColX(side);
                    int colW = selColW();
                    float totalLaneWidth = 0;
                    for (SignItem token : lane) totalLaneWidth += getTokenWidth(g2dLayer.graphics, token, u) + u * 0.1f;
                    if (totalLaneWidth > colW) {
                        float ratio = (float) (mouseX - colX) / colW;
                        ratio = Math.max(0, Math.min(1, ratio));
                        rowScroll[draggingRowIndex] = -ratio * (totalLaneWidth - colW);
                    }
                }
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (modeFlag == 0) {
            int rowHeight = (height - 12) / ROW_COUNT;
            int maxScroll = selMaxScroll();
            // 单列且超出视口：优先纵向滚动
            if (maxScroll > 0) {
                selectionScroll += (float) (delta * 16f);
                return true;
            }
            for (int side = 0; side < facesData.size(); side++) {
                int colX = selColX(side);
                int colW = selColW();
                for (int part = 0; part < 3; part++) {
                    int i = side * 3 + part;
                    int rowY = selRowY(side, part);
                    if (mouseX >= colX && mouseX <= colX + colW && mouseY >= rowY && mouseY <= rowY + rowHeight) {
                        rowScroll[i] += (float) (delta * 8f);
                        return true;
                    }
                }
            }
        } else {
            if ((modeFlag == 1 || modeFlag == 3) && mouseY >= 24 && mouseY <= 78) {
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
            if (modeFlag == 3) {
                if (layoutEditRef != null && layoutEditRef.selectShown()) {
                    modeFlag = 2;
                    sideEditing = -1;
                    editingPreviewScroll = 0;
                } else {
                    popLayoutToParent();
                }
                return true;
            }
            if (modeFlag == 2) {
                popLayoutToParent();
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
            layoutStack.clear();
        } else if (modeFlag == 0) {
            inEditingRow = null;
            editingPreviewScroll = 0;
            sideEditing = -1;
        } else if (this.modeFlag < 0) {
            layoutStack.clear();
            layoutEditRef = null;
            setter.accept(facesData);
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
            default -> "face" + (index + 1);
        };
    }

    /** 面的显示名：优先用该面保存的名字（front_upper 等），并按语言键 ui.fangsu.sign.<name> 尝试翻译，找不到则显示原名。 */
    private String faceLabel(int index) {
        if (index < 0 || index >= facesData.size()) return "";
        String name = facesData.get(index).getName();
        if (name == null || name.isEmpty()) {
            return ComponentHelper.translatable("ui.fangsu.sign." + faceName(index)).getString();
        }
        String key = "ui.fangsu.sign." + name;
        String resolved = ComponentHelper.translatable(key).getString();
        return resolved.equals(key) ? name : resolved;
    }

    /* ===================== 选择页布局（4 面分两列，>4 面才纵向滚动） ===================== */

    /** 4 个面时分成左右两列，避免滚动；其余单列。 */
    private int selColumns() {
        return facesData.size() == 4 ? 2 : 1;
    }

    private int selColW() {
        return width / selColumns();
    }

    private int selFacesPerCol() {
        return (facesData.size() + selColumns() - 1) / selColumns();
    }

    private int selCol(int side) {
        return selColumns() == 2 ? (side >= selFacesPerCol() ? 1 : 0) : 0;
    }

    private int selFaceInCol(int side) {
        return selColumns() == 2 ? (side >= selFacesPerCol() ? side - selFacesPerCol() : side) : side;
    }

    private int selColX(int side) {
        return selCol(side) * selColW();
    }

    private int selRowY(int side, int part) {
        int rowHeight = (height - 12) / ROW_COUNT;
        return 12 + (selFaceInCol(side) * 3 + part) * rowHeight + (int) selectionScroll;
    }

    private int selMaxScroll() {
        if (selColumns() == 2) return 0;
        int rowHeight = (height - 12) / ROW_COUNT;
        return Math.max(0, facesData.size() * 3 * rowHeight - (height - 12));
    }

    private JsonObject deepCopy(JsonObject input) {
        return JsonParser.parseString(input.toString()).getAsJsonObject();
    }

    private record LaneRef(int face, int part, List<SignItem> lane) {
    }

    private record LayoutEditRef(List<SignItem> parentLane, int itemIndex, LayoutItem layoutItem,
                                 String selectedLaneKey, boolean selectShown) {
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
