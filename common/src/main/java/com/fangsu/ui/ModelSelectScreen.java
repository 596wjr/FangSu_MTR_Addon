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
        listButtons.clear();
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

        int y = getContentTop();
        for (ModelSelectInfo info : options) {
            int baseY = y;
            //#if MC_VERSION >= 11903
            Button button = Button.builder(ComponentHelper.translatable(info.getText()), btn -> setSelected(info)).bounds(getListLeft(), baseY, LIST_WIDTH, LIST_ITEM_HEIGHT).build();
            //#else
            //$$ Button button = new Button(getListLeft(), baseY, LIST_WIDTH, LIST_ITEM_HEIGHT, ComponentHelper.translatable(info.getText()), btn -> setSelected(info));
            //#endif
            addRenderableWidget(button);
            listButtons.add(button);
            listEntries.add(new ScrollEntry(button, baseY));
            y += LIST_ITEM_HEIGHT + 2;
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
            if (selected != null && Objects.equals(selected.getContent(), info.getContent())) {
                //#if MC_VERSION >= 12000
                button.setMessage(Component.literal(">" + ComponentHelper.translatable(info.getText()).getString() + "<"));
                //#else
                //$$ button.setMessage(ComponentHelper.literal(">" + ComponentHelper.translatable(info.getText()).getString() + "<"));
                //#endif
            } else {
                button.setMessage(ComponentHelper.translatable(info.getText()));
            }
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
        renderBackground(graphics);
        //#else
        //$$ renderBackground(poseStack);
        //#endif

        g.fill(getPanelLeft(), getPanelTop(), getPanelRight(), getPanelBottom(), 0xCCFFFFFF);

        g.drawString(
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

        g.enableScissor(getListLeft(), getContentTop(), getListRight(), getContentBottom());
        for (Button button : listButtons) {
            button.render(g.asMinecraft(), mouseX, mouseY, partialTick);
        }
        g.disableScissor();

        renderContentPanel(g);
        confirmButton.render(g.asMinecraft(), mouseX, mouseY, partialTick);
    }

    private void renderContentPanel(GraphicContext g) {
        g.fill(
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

        g.enableScissor(
                getContentAreaLeft(),
                getContentTop(),
                getContentAreaRight(),
                getContentBottom()
        );

        int y = getContentTop() + 6 + contentScrollOffset;
        for (Component line : lines) {
            g.drawString(this.font, line, textLeft, y, 0x202020, false);
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
            //#if MC_VERSION >= 11903
            widget.setY(baseY + offset);
            //#else
            //$$ widget.y = baseY + offset;
            //#endif
        }
    }
}

