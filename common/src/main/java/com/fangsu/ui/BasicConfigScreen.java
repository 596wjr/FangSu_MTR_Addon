package com.fangsu.ui;

import com.fangsu.extraConfig.SliderWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class BasicConfigScreen extends Screen {

    protected int scrollOffset = 0;
    private boolean pendingRebuild = false;

    protected Button closeButton;
    protected final List<AbstractWidget> fixedWidgets = new ArrayList<>();
    protected final List<ScrollEntry> entries = new ArrayList<>();

    protected BasicConfigScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        entries.clear();
        fixedWidgets.clear();
        buildFixedWidgets();
        buildScrollableContent(new ContentLayout(this));
    }

    protected abstract void buildScrollableContent(ContentLayout layout);

    protected void buildFixedWidgets() {
        closeButton = addFixedWidget(Button.builder(Component.translatable("ui.fangsu.block.close_and_save"), btn -> onClose())
                .bounds(this.width / 2 - 50, this.height - 40, 100, 20).build());
    }

    protected void requestRebuild() {
        pendingRebuild = true;
    }

    protected int addAxisControls(
            int centerX,
            int spacing,
            int baseY,
            Component labelX,
            Component labelY,
            Component labelZ,
            float valueX,
            float valueY,
            float valueZ,
            float min,
            float max,
            float step,
            Consumer<Float> setX,
            Consumer<Float> setY,
            Consumer<Float> setZ,
            Runnable onValueChanged,
            boolean useSliderInput
    ) {
        if (useSliderInput) {
            addEntry(createSlider(centerX - spacing, baseY, valueX, v -> {
                setX.accept(v);
                onValueChanged.run();
            }, min, max, step), baseY);
            addEntry(createSlider(centerX, baseY, valueY, v -> {
                setY.accept(v);
                onValueChanged.run();
            }, min, max, step), baseY);
            addEntry(createSlider(centerX + spacing, baseY, valueZ, v -> {
                setZ.accept(v);
                onValueChanged.run();
            }, min, max, step), baseY);
            return baseY;
        }

        baseY += 12;
        addEntry(createAxisInput(centerX - spacing, baseY, labelX, valueX, min, max, step, setX, onValueChanged), baseY);
        addEntry(createAxisInput(centerX, baseY, labelY, valueY, min, max, step, setY, onValueChanged), baseY);
        addEntry(createAxisInput(centerX + spacing, baseY, labelZ, valueZ, min, max, step, setZ, onValueChanged), baseY);
        return baseY;
    }

    protected AbstractWidget createAxisInput(
            int x,
            int baseY,
            Component label,
            float initialValue,
            float min,
            float max,
            float step,
            Consumer<Float> setter,
            Runnable onValueChanged
    ) {
        int width = 60;
        int height = 20;
        int labelY = baseY - 10;
        addEntry(createTextLabel(x, labelY, label, TextLabel.Align.CENTER, 0xFFFFFF, false), labelY);
        EditBox box = new EditBox(this.font, x - width / 2, baseY, width, height, Component.empty());
        box.setValue(formatValue(initialValue));
        box.setResponder(text -> {
            Float value = parseFloat(text);
            if (value == null) {
                return;
            }
            float snapped = snap(value, min, max, step);
            setter.accept(snapped);
            onValueChanged.run();
        });
        this.addRenderableWidget(box);
        return box;
    }

    protected SliderWidget createSlider(int cx, int baseY, float initialValue, Consumer<Float> setter, float min, float max, float step) {
        SliderWidget slider = new SliderWidget(cx - 30, baseY, 60, 20, Component.empty(), initialValue, min, max, step, setter);
        this.addRenderableWidget(slider);
        return slider;
    }

    protected TextLabel createTextLabel(int x, int y, Component text, TextLabel.Align align, int color, boolean bold) {
        TextLabel label = new TextLabel(x, y, text, align, color, bold);
        this.addRenderableWidget(label);
        return label;
    }

    protected void addEntry(AbstractWidget widget, int baseY) {
        entries.add(new ScrollEntry(widget, baseY));
    }

    protected Button addButton(int x, int y, int width, int height, Component label, Button.OnPress onPress) {
        Button button = Button.builder(label, onPress).bounds(x, y, width, height).build();
        addRenderableWidget(button);
        return button;
    }

    protected Button addFixedWidget(Button button) {
        addRenderableWidget(button);
        fixedWidgets.add(button);
        return button;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (pendingRebuild) {
            pendingRebuild = false;
            clearWidgets();
            init();
        }
        renderBackground(graphics);

        int areaLeft = getPanelLeft();
        int areaTop = getPanelTop();
        int areaRight = getPanelRight();
        int areaBottom = getPanelBottom();
        graphics.fill(areaLeft, areaTop, areaRight, areaBottom, 0xCCFFFFFF);

        int titleX = this.width / 2 - this.font.width(this.title.getString()) / 2;
        int titleY = areaTop - 18;
        graphics.drawString(this.font, this.title, titleX, titleY, 0x101010, false);

        for (ScrollEntry e : entries) {
            e.applyScroll(scrollOffset);
        }

        for (AbstractWidget fixedWidget : fixedWidgets) {
            fixedWidget.render(graphics, mouseX, mouseY, partialTick);
        }

        graphics.enableScissor(getContentLeft(), getContentTop(), getContentRight(), getContentBottom());
        for (ScrollEntry e : entries) {
            e.widget.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int visibleTop = getContentTop();
        int visibleBottom = getContentBottom();
        int visibleHeight = visibleBottom - visibleTop;

        int contentBottom = getActualContentBottom();
        int contentTop = entries.isEmpty() ? 0 : entries.get(0).baseY;
        int contentHeight = contentBottom - contentTop;

        if (contentHeight <= visibleHeight) {
            scrollOffset = 0;
            return false;
        }

        scrollOffset += (int) (delta * 12);

        int minOffset = visibleHeight - contentHeight;
        scrollOffset = Mth.clamp(scrollOffset, minOffset, 0);
        return true;
    }

    protected int getPanelLeft() {
        return 40;
    }

    protected int getPanelRight() {
        return this.width - 40;
    }

    protected int getPanelTop() {
        return 30;
    }

    protected int getPanelBottom() {
        return this.height - 60;
    }

    protected int getContentPadding() {
        return 12;
    }

    protected int getContentLeft() {
        return getPanelLeft() + getContentPadding();
    }

    protected int getContentRight() {
        return getPanelRight() - getContentPadding();
    }

    protected int getContentTop() {
        return getPanelTop() + getContentPadding() + 12;
    }

    protected int getContentBottom() {
        return getPanelBottom() - getContentPadding();
    }

    protected int getActualContentBottom() {
        int bottom = 0;
        for (ScrollEntry e : entries) {
            bottom = Math.max(bottom, e.baseY + e.widget.getHeight());
        }
        return bottom;
    }

    protected Float parseFloat(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    protected float snap(float value, float min, float max, float step) {
        float clamped = Mth.clamp(value, min, max);
        if (step <= 0) {
            return clamped;
        }
        return Math.round(clamped / step) * step;
    }

    protected String formatValue(float value) {
        return String.format("%.3f", value);
    }

    protected static class ContentLayout {
        private final BasicConfigScreen screen;
        public final int centerX;
        public final int areaLeft;
        public final int areaRight;
        public final int contentWidth;
        public final int labelWidth;
        public final int fieldWidth;
        public int y;

        private ContentLayout(BasicConfigScreen screen) {
            this.screen = screen;
            this.centerX = screen.width / 2;
            this.y = screen.getContentTop();
            this.areaLeft = (int) (screen.getPanelLeft() + screen.width * 0.1);
            this.areaRight = (int) (screen.getPanelRight() - screen.width * 0.1);
            this.contentWidth = areaRight - areaLeft;
            this.labelWidth = (int) (contentWidth * 0.4f);
            this.fieldWidth = contentWidth - labelWidth;
        }

        public BasicConfigScreen screen() {
            return screen;
        }
    }

    protected static class TextLabel extends AbstractWidget {
        public enum Align {LEFT, CENTER, RIGHT}

        private final Component text;
        private final int color;
        private final boolean bold;
        private final Align align;

        public TextLabel(int x, int y, Component text, Align align, int color, boolean bold) {
            super(x, y, 0, 0, Component.empty());
            this.text = text;
            this.align = align;
            this.color = color;
            this.bold = bold;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            var font = Minecraft.getInstance().font;
            int drawX = this.getX();
            int textWidth = font.width(text.getString());
            switch (align) {
                case CENTER -> drawX = this.getX() - textWidth / 2;
                case RIGHT -> drawX = this.getX() - textWidth;
                case LEFT -> drawX = this.getX();
            }

            if (bold) {
                graphics.drawString(font, text.copy().withStyle(style -> style.withBold(true)), drawX, this.getY(), color, false);
            } else {
                graphics.drawString(font, text, drawX, this.getY(), color, false);
            }
        }

        @Override
        protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        }
    }

    protected static class ScrollEntry {
        final AbstractWidget widget;
        final int baseY;

        ScrollEntry(AbstractWidget widget, int baseY) {
            this.widget = widget;
            this.baseY = baseY;
        }

        void applyScroll(int offset) {
            widget.setY(baseY + offset);
        }
    }
}
