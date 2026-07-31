package com.fangsu.ui;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
import com.fangsu.customItem.ModelSelectInfo;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ModelSelectScreen extends Screen {

    /* ===================== 滚动条拖动状态 ===================== */

    private boolean draggingListScrollbar = false;
    private int listDragOffset = 0;
    private boolean draggingContentScrollbar = false;
    private int contentDragOffset = 0;
    private static final int PADDING = 12;
    private static final int LIST_ITEM_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int LIST_WIDTH = 90;

    private final BaseObjBlockEntity be;
    private final List<ModelSelectInfo> options;
    private final Function<BaseObjBlockEntity, String> initialGetter;
    private final BiConsumer<BaseObjBlockEntity, String> setter;
    private final Runnable afterSave;

    private Screen parent = null;

    private final List<ScrollEntry> listEntries = new ArrayList<>();

    private ModelSelectInfo selected;
    private Button confirmButton;

    private int listScrollOffset = 0;
    private int contentScrollOffset = 0;

    public ModelSelectScreen(
            Component title,
            BaseObjBlockEntity be,
            List<? extends ModelSelectInfo> options,
            Function<BaseObjBlockEntity, String> initialGetter,
            BiConsumer<BaseObjBlockEntity, String> setter
    ) {
        super(title);
        this.be = be;
        this.options = options == null ? List.of() : List.copyOf(options);
        this.initialGetter = initialGetter;
        this.setter = setter;
        this.afterSave = null;
    }

    public ModelSelectScreen(
            Component title,
            BaseObjBlockEntity be,
            List<? extends ModelSelectInfo> options,
            Function<BaseObjBlockEntity, String> initialGetter,
            BiConsumer<BaseObjBlockEntity, String> setter,
            Screen parent
    ) {
        super(title);
        this.be = be;
        this.options = options == null ? List.of() : List.copyOf(options);
        this.initialGetter = initialGetter;
        this.setter = setter;
        this.parent = parent;
        this.afterSave = null;
    }

    public ModelSelectScreen(
            Component title,
            BaseObjBlockEntity be,
            List<? extends ModelSelectInfo> options,
            Function<BaseObjBlockEntity, String> initialGetter,
            BiConsumer<BaseObjBlockEntity, String> setter,
            Screen parent,
            Runnable afterSave
    ) {
        super(title);
        this.be = be;
        this.options = options == null ? List.of() : List.copyOf(options);
        this.initialGetter = initialGetter;
        this.setter = setter;
        this.parent = parent;
        this.afterSave = afterSave;
    }

    @Override
    protected void init() {
        super.init();
        listEntries.clear();
        listScrollOffset = 0;
        contentScrollOffset = 0;

        selected = null;
        if (be != null && initialGetter != null) {
            String initial = initialGetter.apply(be);
            if (initial != null) {
                selected = options.stream()
                        .filter(info -> Objects.equals(info.getContent(), initial))
                        .findFirst()
                        .orElse(null);
            }
        }

        int itemHeight = getItemHeight();
        int y = getContentTop();
        for (ModelSelectInfo info : options) {
            int baseY = y;
            listEntries.add(new ScrollEntry(null, baseY, info));
            y += itemHeight + 2;
        }

        //#if MC_VERSION >= 11903
        confirmButton = addRenderableWidget(Button.builder(ComponentHelper.translatable("ui.fangsu.block.confirm"), btn -> {
            if (selected != null && setter != null) {
                setter.accept(be, selected.getContent());
                if (be != null && selected.getDefault() != null) {
                    for (Map.Entry<String, JsonElement> entry : selected.getDefault().entrySet()) {
                        be.subModels.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                if (afterSave != null) afterSave.run();
            }
            onClose();
        }).bounds(getContentAreaLeft(), getPanelBottom() - BUTTON_HEIGHT - PADDING, getContentAreaRight() - getContentAreaLeft(), BUTTON_HEIGHT).build());
        //#else
        //$$ confirmButton = addRenderableWidget(new Button(getContentAreaLeft(), getPanelBottom() - BUTTON_HEIGHT - PADDING, getContentAreaRight() - getContentAreaLeft(), BUTTON_HEIGHT, ComponentHelper.translatable("ui.fangsu.block.confirm"), btn -> { if (selected != null && setter != null) { setter.accept(be, selected.getContent()); if (be != null && selected.getDefault() != null) { for (Map.Entry<String, JsonElement> entry : selected.getDefault().entrySet()) { be.subModels.put(entry.getKey(), entry.getValue().getAsString()); } } if (afterSave != null) afterSave.run(); } onClose(); }));
        //#endif

        updateConfirmState();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private void setSelected(ModelSelectInfo info) {
        selected = info;
        contentScrollOffset = 0;
        updateConfirmState();
    }

    private void updateConfirmState() {
        if (confirmButton != null) {
            confirmButton.active = selected != null;
        }
    }

    //#if MC_VERSION >= 12000
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GraphicContext g = GraphicContext.of(graphics);
        //#else
        //$$ @Override
        //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     GraphicContext g = GraphicContext.of(poseStack);
        //#endif
        //#if MC_VERSION >= 12000
        renderDirtBackground(g.asMinecraft());
        //#else
        //$$ renderBackground(poseStack);
        //#endif

        // 标题（白色，左上角）
        g.drawString(font, title, 10, 2, 0xFFFFFF, false);

        // 左侧列列表背景
        g.fill(getListLeft(), getContentTop(), getListRight(), getContentBottom(), 0xaa000000);

        // 内容面板背景
        g.fill(getContentAreaLeft(), getContentTop(), getContentAreaRight(), getContentBottom(), 0xaa000000);

        // 列标题
        g.drawCenteredString(font, ComponentHelper.translatable("ui.fangsu.block.modelSelect"), (getListLeft() + getListRight()) / 2, getContentTop() - font.lineHeight - 4, 0xFFFFFF);
        g.drawCenteredString(font, ComponentHelper.literal("Detail"), (getContentAreaLeft() + getContentAreaRight()) / 2, getContentTop() - font.lineHeight - 4, 0xFFFFFF);

        for (ScrollEntry entry : listEntries) {
            entry.applyScroll(listScrollOffset);
        }

        // 列表项（手动绘制）
        g.enableScissor(getListLeft(), getContentTop(), getListRight(), getContentBottom());
        renderListItems(g, mouseX, mouseY);
        g.disableScissor();

        renderListScrollbar(g);

        // 内容面板
        renderContentPanel(g, mouseX, mouseY);
        renderContentScrollbar(g);

        confirmButton.render(g.asMinecraft(), mouseX, mouseY, partialTick);
    }

    private void renderListItems(GraphicContext g, int mouseX, int mouseY) {
        int itemLeft = getListLeft() + 2;
        int itemWidth = getListRight() - getListLeft() - 4;
        for (ScrollEntry entry : listEntries) {
            int y = entry.currentY;
            if (y + LIST_ITEM_HEIGHT < getContentTop() || y > getContentBottom()) continue;

            boolean isSelected = selected != null && entry.info != null && Objects.equals(selected.getContent(), entry.info.getContent());
            boolean hovered = mouseX >= itemLeft && mouseX <= itemLeft + itemWidth && mouseY >= y && mouseY <= y + LIST_ITEM_HEIGHT;

            int bgColor = isSelected ? 0x55ffffff : hovered ? 0x44ffffff : 0x33ffffff;
            g.fill(itemLeft, y, itemLeft + itemWidth, y + LIST_ITEM_HEIGHT, bgColor);

            int textColor = isSelected ? 0xFFFFFF00 : 0xffffffff;

            // 自动换行
            String text = ComponentHelper.translatable(entry.info.getText()).getString();
            int textMaxWidth = itemWidth - 4;
            List<FormattedCharSequence> wrappedLines = font.split(ComponentHelper.literal(text), textMaxWidth);
            int textY = y + (LIST_ITEM_HEIGHT - wrappedLines.size() * font.lineHeight) / 2;
            for (FormattedCharSequence line : wrappedLines) {
                g.drawString(font, line, itemLeft + 2, textY, textColor, false);
                textY += font.lineHeight;
            }

            // 点击检测
            if (hovered && mouseClickInfo != null && mouseClickInfo.button == 0) {
                setSelected(entry.info);
                mouseClickInfo = null;
            }
        }
    }

    private int getItemHeight() {
        return LIST_ITEM_HEIGHT;
    }

    private void renderContentPanel(GraphicContext g, int mouseX, int mouseY) {
        int textLeft = getContentAreaLeft() + 6;
        int textWidth = getContentAreaRight() - textLeft - 6;

        List<Component> lines = getSelectedContentLines(textWidth);
        int lineHeight = Minecraft.getInstance().font.lineHeight;
        int totalHeight = lines.size() * lineHeight;

        int visibleHeight = getContentBottom() - getContentTop();
        int maxScroll = Math.max(0, totalHeight + 12 - visibleHeight);
        contentScrollOffset = Mth.clamp(contentScrollOffset, -maxScroll, 0);

        g.enableScissor(
                getContentAreaLeft(),
                getContentTop(),
                getContentAreaRight(),
                getContentBottom()
        );

        int y = getContentTop() + 6 + contentScrollOffset;
        for (Component line : lines) {
            g.drawString(this.font, line, textLeft, y, 0xffffffff, false);
            y += lineHeight;
        }

        g.disableScissor();
    }

    private List<Component> getSelectedContentLines(int width) {
        String text = selected == null ? "" : selected.getContentText();
        return this.font.split(ComponentHelper.translatable(text), width).stream()
                .map(this::sequenceToComponent)
                .toList();
    }

    private Component sequenceToComponent(FormattedCharSequence sequence) {
        StringBuilder builder = new StringBuilder();
        sequence.accept((i, s, c) -> {
            builder.appendCodePoint(c);
            return true;
        });
        //#if MC_VERSION >= 12000
        return Component.literal(builder.toString());
        //#else
        //$$ return ComponentHelper.literal(builder.toString());
        //#endif
    }

    /* ===================== 滚动条 ===================== */

    private int calcListContentHeight() {
        if (listEntries.isEmpty()) return 0;
        return listEntries.get(listEntries.size() - 1).baseY - getContentTop() + getItemHeight();
    }

    private int calcContentContentHeight() {
        int textWidth = getContentAreaRight() - getContentAreaLeft() - 12;
        return getSelectedContentLines(textWidth).size() * Minecraft.getInstance().font.lineHeight + 12;
    }

    private int getScrollbarVisibleHeight() {
        return getContentBottom() - getContentTop();
    }

    private void renderListScrollbar(GraphicContext g) {
        int visible = getScrollbarVisibleHeight();
        int total = calcListContentHeight();
        if (total <= visible) return;

        int sbX = getListRight() - 4;
        int sbW = 4;
        int sbTop = getContentTop();
        int sbBottom = getContentBottom();
        g.fill(sbX, sbTop, sbX + sbW, sbBottom, 0x30FFFFFF);

        float ratio = (float) -listScrollOffset / Math.max(1, total - visible);
        int thumbH = Math.max(10, (int) (visible * (float) visible / total));
        int thumbY = sbTop + (int) (ratio * (visible - thumbH));
        g.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0x99FFFFFF);

        listSbInfo[0] = sbX;
        listSbInfo[1] = sbX + sbW;
        listSbInfo[2] = sbTop;
        listSbInfo[3] = sbBottom;
        listSbInfo[4] = thumbY;
        listSbInfo[5] = thumbY + thumbH;
    }

    private void renderContentScrollbar(GraphicContext g) {
        int visible = getScrollbarVisibleHeight();
        int total = calcContentContentHeight();
        if (total <= visible) return;

        int sbX = getContentAreaRight() - 4;
        int sbW = 4;
        int sbTop = getContentTop();
        int sbBottom = getContentBottom();
        g.fill(sbX, sbTop, sbX + sbW, sbBottom, 0x30FFFFFF);

        float ratio = (float) -contentScrollOffset / Math.max(1, total - visible);
        int thumbH = Math.max(10, (int) (visible * (float) visible / total));
        int thumbY = sbTop + (int) (ratio * (visible - thumbH));
        g.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0x99FFFFFF);

        contentSbInfo[0] = sbX;
        contentSbInfo[1] = sbX + sbW;
        contentSbInfo[2] = sbTop;
        contentSbInfo[3] = sbBottom;
        contentSbInfo[4] = thumbY;
        contentSbInfo[5] = thumbY + thumbH;
    }

    private final int[] listSbInfo = new int[6];
    private final int[] contentSbInfo = new int[6];

    private MouseClickInfo mouseClickInfo;

    private record MouseClickInfo(double mouseX, double mouseY, int button) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 列表滚动条
            if (trySbClick(mouseX, mouseY, listSbInfo, listScrollOffset, calcListContentHeight(), true)) return true;
            // 内容滚动条
            if (trySbClick(mouseX, mouseY, contentSbInfo, contentScrollOffset, calcContentContentHeight(), false)) return true;
        }
        mouseClickInfo = new MouseClickInfo(mouseX, mouseY, button);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean trySbClick(double mx, double my, int[] info, int scrollOffset, int totalHeight, boolean isListScroll) {
        if (info[0] == 0 && info[1] == 0) return false; // 未初始化
        int visible = getScrollbarVisibleHeight();
        if (totalHeight <= visible) return false;
        if (mx < info[0] || mx > info[1] || my < info[2] || my > info[3]) return false;

        int thumbTop = info[4];
        int thumbBottom = info[5];
        if (my >= thumbTop && my <= thumbBottom) {
            if (isListScroll) {
                draggingListScrollbar = true;
                listDragOffset = (int) (my - thumbTop);
            } else {
                draggingContentScrollbar = true;
                contentDragOffset = (int) (my - thumbTop);
            }
            return true;
        } else {
            int page = visible / 2;
            int target;
            if (my < thumbTop) {
                target = scrollOffset + page;
            } else {
                target = scrollOffset - page;
            }
            int maxScroll = Math.max(0, totalHeight - visible);
            target = Math.max(-maxScroll, Math.min(target, 0));
            if (isListScroll) {
                listScrollOffset = target;
            } else {
                contentScrollOffset = target;
            }
            return true;
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingListScrollbar = false;
        draggingContentScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            if (draggingListScrollbar) {
                return dragScrollbar(mouseY, listSbInfo, listScrollOffset, calcListContentHeight(), true);
            }
            if (draggingContentScrollbar) {
                return dragScrollbar(mouseY, contentSbInfo, contentScrollOffset, calcContentContentHeight(), false);
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean dragScrollbar(double mouseY, int[] info, int scrollOffset, int totalHeight, boolean isList) {
        int visible = getScrollbarVisibleHeight();
        int maxScroll = Math.max(0, totalHeight - visible);
        if (maxScroll <= 0) return false;
        int thumbH = Math.max(10, (int) (visible * (float) visible / totalHeight));
        int offset = isList ? listDragOffset : contentDragOffset;
        float ratio = (float) (mouseY - info[2] - offset) / (visible - thumbH);
        ratio = Math.max(0, Math.min(1, ratio));
        int target = (int) (-ratio * maxScroll);
        if (isList) {
            listScrollOffset = target;
        } else {
            contentScrollOffset = target;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isPointInside(mouseX, mouseY, getListLeft(), getContentTop(), getListRight(), getContentBottom())) {
            return scrollList(delta);
        }
        if (isPointInside(mouseX, mouseY, getContentAreaLeft(), getContentTop(), getContentAreaRight(), getContentBottom())) {
            return scrollContent(delta);
        }
        return false;
    }

    private boolean scrollList(double delta) {
        int visible = getContentBottom() - getContentTop();
        int total = listEntries.isEmpty() ? 0 :
                listEntries.get(listEntries.size() - 1).baseY - getContentTop() + getItemHeight();
        if (total <= visible) {
            listScrollOffset = 0;
            return false;
        }
        listScrollOffset = Mth.clamp(listScrollOffset + (int) (delta * 12), visible - total, 0);
        return true;
    }

    private boolean scrollContent(double delta) {
        int textWidth = getContentAreaRight() - getContentAreaLeft() - 12;
        int total = getSelectedContentLines(textWidth).size() * Minecraft.getInstance().font.lineHeight;
        int visible = getContentBottom() - getContentTop() - 12;
        if (total <= visible) {
            contentScrollOffset = 0;
            return false;
        }
        contentScrollOffset = Mth.clamp(contentScrollOffset + (int) (delta * 12), visible - total, 0);
        return true;
    }

    private boolean isPointInside(double x, double y, int l, int t, int r, int b) {
        return x >= l && x <= r && y >= t && y <= b;
    }

    private int getPanelLeft() {
        return 30;
    }

    private int getPanelRight() {
        return this.width - 30;
    }

    private int getPanelTop() {
        return 30;
    }

    private int getPanelBottom() {
        return this.height - 30;
    }

    private int getContentTop() {
        return getPanelTop() + PADDING + 12;
    }

    private int getContentBottom() {
        return getPanelBottom() - BUTTON_HEIGHT - PADDING * 2;
    }

    private int getListLeft() {
        return getPanelLeft() + PADDING;
    }

    private int getListRight() {
        return getListLeft() + LIST_WIDTH;
    }

    private int getContentAreaLeft() {
        return getListRight() + PADDING;
    }

    private int getContentAreaRight() {
        return getPanelRight() - PADDING;
    }

    private static class ScrollEntry {
        private final AbstractWidget widget;
        private final int baseY;
        private final ModelSelectInfo info;
        private int currentY;

        private ScrollEntry(AbstractWidget widget, int baseY) {
            this.widget = widget;
            this.baseY = baseY;
            this.info = null;
            this.currentY = baseY;
        }

        private ScrollEntry(AbstractWidget widget, int baseY, ModelSelectInfo info) {
            this.widget = widget;
            this.baseY = baseY;
            this.info = info;
            this.currentY = baseY;
        }

        private void applyScroll(int offset) {
            currentY = baseY + offset;
            if (widget != null) {
                //#if MC_VERSION >= 11903
                widget.setY(currentY);
                //#else
                //$$ widget.y = currentY;
                //#endif
            }
        }
    }
}

