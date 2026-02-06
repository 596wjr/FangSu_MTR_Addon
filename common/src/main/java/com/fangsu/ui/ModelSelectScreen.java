package com.fangsu.ui;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.customItem.ModelSelectInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ModelSelectScreen extends Screen {
    private static final int PADDING = 12;
    private static final int LIST_ITEM_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 20;

    private final BaseObjBlockEntity be;
    private final List<ModelSelectInfo> options;
    private final Function<BaseObjBlockEntity, ModelSelectInfo> initialGetter;
    private final BiConsumer<BaseObjBlockEntity, String> setter;

    private final List<ScrollEntry> listEntries = new ArrayList<>();
    private final List<Button> listButtons = new ArrayList<>();

    private ModelSelectInfo selected;
    private Button confirmButton;

    private int listScrollOffset = 0;
    private int contentScrollOffset = 0;

    public ModelSelectScreen(
            Component title,
            BaseObjBlockEntity be,
            List<ModelSelectInfo> options,
            Function<BaseObjBlockEntity, ModelSelectInfo> initialGetter,
            BiConsumer<BaseObjBlockEntity, String> setter
    ) {
        super(title);
        this.be = be;
        this.options = options == null ? List.of() : List.copyOf(options);
        this.initialGetter = initialGetter;
        this.setter = setter;
    }

    @Override
    protected void init() {
        super.init();
        listEntries.clear();
        listButtons.clear();
        listScrollOffset = 0;
        contentScrollOffset = 0;

        selected = null;
        if (be != null && initialGetter != null) {
            ModelSelectInfo initial = initialGetter.apply(be);
            if (initial != null) {
                selected = options.stream()
                        .filter(info -> Objects.equals(info.content(), initial.content()))
                        .findFirst()
                        .orElse(initial);
            }
        }

        int listLeft = getListLeft();
        int listWidth = getListWidth();
        int listTop = getContentTop();
        int y = listTop;
        for (ModelSelectInfo info : options) {
            int baseY = y;
            Button button = Button.builder(Component.literal(info.text()), btn -> setSelected(info))
                    .bounds(listLeft, baseY, listWidth, LIST_ITEM_HEIGHT)
                    .build();
            addRenderableWidget(button);
            listButtons.add(button);
            listEntries.add(new ScrollEntry(button, baseY));
            y += LIST_ITEM_HEIGHT + 2;
        }

        confirmButton = addRenderableWidget(Button.builder(Component.literal("确认"), btn -> {
            if (selected != null && be != null && setter != null) {
                setter.accept(be, selected.content());
            }
            onClose();
        }).bounds(getContentLeft(), getPanelBottom() + 10, 100, BUTTON_HEIGHT).build());
        updateConfirmState();
        updateButtonStyles();
    }

    private void setSelected(ModelSelectInfo info) {
        selected = info;
        contentScrollOffset = 0;
        updateConfirmState();
        updateButtonStyles();
    }

    private void updateConfirmState() {
        if (confirmButton != null) {
            confirmButton.active = selected != null;
        }
    }

    private void updateButtonStyles() {
        for (int i = 0; i < listButtons.size(); i++) {
            Button button = listButtons.get(i);
            ModelSelectInfo info = options.get(i);
            if (selected != null && Objects.equals(selected.content(), info.content())) {
                button.setMessage(Component.literal("▶ " + info.text()));
            } else {
                button.setMessage(Component.literal(info.text()));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int panelLeft = getPanelLeft();
        int panelTop = getPanelTop();
        int panelRight = getPanelRight();
        int panelBottom = getPanelBottom();
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xCCFFFFFF);

        int titleX = this.width / 2 - this.font.width(this.title) / 2;
        graphics.drawString(this.font, this.title, titleX, panelTop - 18, 0x101010, false);

        for (ScrollEntry entry : listEntries) {
            entry.applyScroll(listScrollOffset);
        }

        graphics.enableScissor(getListLeft(), getContentTop(), getListRight(), getContentBottom());
        for (Button button : listButtons) {
            button.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();

        renderContentPanel(graphics);

        confirmButton.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderContentPanel(GuiGraphics graphics) {
        int contentLeft = getContentAreaLeft();
        int contentRight = getContentAreaRight();
        int contentTop = getContentTop();
        int contentBottom = getContentBottom();
        int textLeft = contentLeft + 6;
        int textWidth = contentRight - textLeft - 6;

        graphics.fill(contentLeft, contentTop, contentRight, contentBottom, 0x55FFFFFF);

        List<Component> lines = getSelectedContentLines(textWidth);
        int lineHeight = Minecraft.getInstance().font.lineHeight;
        int totalHeight = lines.size() * lineHeight;

        contentScrollOffset = Mth.clamp(contentScrollOffset, Math.min(0, contentBottom - contentTop - totalHeight), 0);

        graphics.enableScissor(contentLeft, contentTop, contentRight, contentBottom);
        int y = contentTop + 6 + contentScrollOffset;
        for (Component line : lines) {
            graphics.drawString(this.font, line, textLeft, y, 0x202020, false);
            y += lineHeight;
        }
        graphics.disableScissor();
    }

    private List<Component> getSelectedContentLines(int width) {
        String text = selected == null ? "" : selected.contentText();

        return this.font
                .split(Component.literal(text), width)
                .stream()
                .map(this::sequenceToComponent)
                .toList();
    }

    private Component sequenceToComponent(FormattedCharSequence sequence) {
        StringBuilder builder = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return Component.literal(builder.toString());
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
        int visibleHeight = getContentBottom() - getContentTop();
        int contentHeight = listEntries.isEmpty() ? 0 : listEntries.get(listEntries.size() - 1).baseY - getContentTop() + LIST_ITEM_HEIGHT;
        if (contentHeight <= visibleHeight) {
            listScrollOffset = 0;
            return false;
        }
        listScrollOffset += delta * 12;
        int minOffset = visibleHeight - contentHeight;
        listScrollOffset = Mth.clamp(listScrollOffset, minOffset, 0);
        return true;
    }

    private boolean scrollContent(double delta) {
        int contentLeft = getContentAreaLeft();
        int contentRight = getContentAreaRight();
        int textWidth = contentRight - contentLeft - 12;
        List<Component> lines = getSelectedContentLines(textWidth);
        int totalHeight = lines.size() * Minecraft.getInstance().font.lineHeight;
        int visibleHeight = getContentBottom() - getContentTop() - 12;
        if (totalHeight <= visibleHeight) {
            contentScrollOffset = 0;
            return false;
        }
        contentScrollOffset += delta * 12;
        int minOffset = visibleHeight - totalHeight;
        contentScrollOffset = Mth.clamp(contentScrollOffset, minOffset, 0);
        return true;
    }

    private boolean isPointInside(double mouseX, double mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
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
        return this.height - 60;
    }

    private int getContentLeft() {
        return getPanelLeft() + PADDING;
    }

    private int getContentTop() {
        return getPanelTop() + PADDING + 12;
    }

    private int getContentBottom() {
        return getPanelBottom() - PADDING;
    }

    private int getListLeft() {
        return getContentLeft();
    }

    private int getListRight() {
        return getContentLeft() + getListWidth();
    }

    private int getListWidth() {
        return (getPanelRight() - getPanelLeft()) / 3;
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

        private ScrollEntry(AbstractWidget widget, int baseY) {
            this.widget = widget;
            this.baseY = baseY;
        }

        private void applyScroll(int offset) {
            widget.setY(baseY + offset);
        }
    }

}
