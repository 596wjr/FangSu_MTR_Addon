package com.fangsu.ui;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.customItem.ModelSelectInfo;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
    private final List<Button> listButtons = new ArrayList<>();

    private ModelSelectInfo selected;
    private Button confirmButton;

    private int listScrollOffset = 0;
    private int contentScrollOffset = 0;

    public ModelSelectScreen(
            Component title,
            BaseObjBlockEntity be,
            List<ModelSelectInfo> options,
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
            List<ModelSelectInfo> options,
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
            List<ModelSelectInfo> options,
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
        listButtons.clear();
        listScrollOffset = 0;
        contentScrollOffset = 0;

        selected = null;
        if (be != null && initialGetter != null) {
            String initial = initialGetter.apply(be);
            if (initial != null) {
                selected = options.stream()
                        .filter(info -> Objects.equals(info.content(), initial))
                        .findFirst()
                        .orElse(null);
            }
        }

        int y = getContentTop();
        for (ModelSelectInfo info : options) {
            int baseY = y;
            Button button = Button.builder(Component.translatable(info.text()), btn -> setSelected(info))
                    .bounds(getListLeft(), baseY, LIST_WIDTH, LIST_ITEM_HEIGHT)
                    .build();
            addRenderableWidget(button);
            listButtons.add(button);
            listEntries.add(new ScrollEntry(button, baseY));
            y += LIST_ITEM_HEIGHT + 2;
        }

        confirmButton = addRenderableWidget(
                Button.builder(Component.translatable("ui.fangsu.block.confirm"), btn -> {
                    if (selected != null && be != null && setter != null) {
                        setter.accept(be, selected.content());
                        if (selected.defaultItem() != null) {
                            for (Map.Entry<String, JsonElement> entry : selected.defaultItem().entrySet()) {
                                String key = entry.getKey();
                                String value = entry.getValue().getAsString();
                                be.subModels.put(key, value);
                            }
                        }
                        if (afterSave != null) {
                            afterSave.run();
                        }
//                        be.sendUpdateC2S();
                    }
                    onClose();
                }).bounds(
                        getContentAreaLeft(),
                        getPanelBottom() - BUTTON_HEIGHT - PADDING,
                        getContentAreaRight() - getContentAreaLeft(),
                        BUTTON_HEIGHT
                ).build()
        );

        updateConfirmState();
        updateButtonStyles();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
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
                button.setMessage(Component.literal(">" + Component.translatable(info.text()).getString() + "<"));
            } else {
                button.setMessage(Component.translatable(info.text()));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(getPanelLeft(), getPanelTop(), getPanelRight(), getPanelBottom(), 0xCCFFFFFF);

        graphics.drawString(
                this.font,
                this.title,
                this.width / 2 - this.font.width(this.title) / 2,
                getPanelTop() - 18,
                0x101010,
                false
        );

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
        graphics.fill(
                getContentAreaLeft(),
                getContentTop(),
                getContentAreaRight(),
                getContentBottom(),
                0x55FFFFFF
        );

        int textLeft = getContentAreaLeft() + 6;
        int textWidth = getContentAreaRight() - textLeft - 6;

        List<Component> lines = getSelectedContentLines(textWidth);
        int lineHeight = Minecraft.getInstance().font.lineHeight;
        int totalHeight = lines.size() * lineHeight;

        contentScrollOffset = Mth.clamp(
                contentScrollOffset,
                Math.min(0, getContentBottom() - getContentTop() - totalHeight),
                0
        );

        graphics.enableScissor(
                getContentAreaLeft(),
                getContentTop(),
                getContentAreaRight(),
                getContentBottom()
        );

        int y = getContentTop() + 6 + contentScrollOffset;
        for (Component line : lines) {
            graphics.drawString(this.font, line, textLeft, y, 0x202020, false);
            y += lineHeight;
        }

        graphics.disableScissor();
    }

    private List<Component> getSelectedContentLines(int width) {
        String text = selected == null ? "" : selected.contentText();
        return this.font.split(Component.translatable(text), width).stream()
                .map(this::sequenceToComponent)
                .toList();
    }

    private Component sequenceToComponent(FormattedCharSequence sequence) {
        StringBuilder builder = new StringBuilder();
        sequence.accept((i, s, c) -> {
            builder.appendCodePoint(c);
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
        int visible = getContentBottom() - getContentTop();
        int total = listEntries.isEmpty() ? 0 :
                listEntries.get(listEntries.size() - 1).baseY - getContentTop() + LIST_ITEM_HEIGHT;
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

        private ScrollEntry(AbstractWidget widget, int baseY) {
            this.widget = widget;
            this.baseY = baseY;
        }

        private void applyScroll(int offset) {
            widget.setY(baseY + offset);
        }
    }
}
