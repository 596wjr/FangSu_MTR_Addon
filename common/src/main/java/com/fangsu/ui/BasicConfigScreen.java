package com.fangsu.ui;

import com.fangsu.extraConfig.ConfigRow;
import com.fangsu.extraConfig.ConfigWidget;
import com.fangsu.extraConfig.SliderWidget;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.utils.GraphicContext;
import net.minecraft.client.Minecraft;
//#if MC_VERSION >= 12000
import net.minecraft.client.gui.GuiGraphics;
//#endif
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
        //#if MC_VERSION >= 11903
        closeButton = addFixedWidget(Button.builder(ComponentHelper.translatable("ui.fangsu.block.close_and_save"), btn -> onClose()).bounds(this.width / 2 - 50, this.height - 40, 100, 20).build());
        //#else
        //$$ closeButton = addFixedWidget(new Button(this.width / 2 - 50, this.height - 40, 100, 20, ComponentHelper.translatable("ui.fangsu.block.close_and_save"), btn -> onClose()));
        //#endif
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
        //#if MC_VERSION >= 12000
        EditBox box = new EditBox(this.font, x - width / 2, baseY, width, height, Component.empty());
        //#else
        //$$ EditBox box = new EditBox(this.font, x - width / 2, baseY, width, height, ComponentHelper.empty());
        //#endif
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
        //#if MC_VERSION >= 12000
        SliderWidget slider = new SliderWidget(cx - 30, baseY, 60, 20, Component.empty(), initialValue, min, max, step, setter);
        //#else
        //$$ SliderWidget slider = new SliderWidget(cx - 30, baseY, 60, 20, ComponentHelper.empty(), initialValue, min, max, step, setter);
        //#endif
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
        //#if MC_VERSION >= 11903
        Button button = Button.builder(label, onPress).bounds(x, y, width, height).build();
        //#else
        //$$ Button button = new Button(x, y, width, height, label, onPress);
        //#endif
        addRenderableWidget(button);
        return button;
    }

    protected Button addFixedWidget(Button button) {
        addRenderableWidget(button);
        fixedWidgets.add(button);
        return button;
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
        if (pendingRebuild) {
            pendingRebuild = false;
            clearWidgets();
            init();
        }
        //#if MC_VERSION >= 12000
        renderBackground(graphics);
        //#else
        //$$ renderBackground(poseStack);
        //#endif

        renderPanelBackground(g);

        for (ScrollEntry e : entries) {
            e.applyScroll(scrollOffset);
        }

        for (AbstractWidget fixedWidget : fixedWidgets) {
            fixedWidget.render(g.asMinecraft(), mouseX, mouseY, partialTick);
        }

        g.enableScissor(getContentLeft(), getContentTop(), getContentRight(), getContentBottom());
        for (ScrollEntry e : entries) {
            e.widget.render(g.asMinecraft(), mouseX, mouseY, partialTick);
        }
        g.disableScissor();
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

    /**
     * 鐎涙劗琚崣顖炲櫢閸愭瑦顒濋弬瑙勭《缂佹ê鍩楅棃銏℃緲閼冲本娅?
     */
    protected void renderPanelBackground(GraphicContext g) {
        int areaLeft = getPanelLeft();
        int areaTop = getPanelTop();
        int areaRight = getPanelRight();
        int areaBottom = getPanelBottom();
        g.fill(areaLeft, areaTop, areaRight, areaBottom, 0xCCFFFFFF);
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
        return String.format("%.4f", value);
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
            //#if MC_VERSION >= 12000
            super(x, y, 0, 0, Component.empty());
            //#else
            //$$ super(x, y, 0, 0, ComponentHelper.empty());
            //#endif
            this.text = text;
            this.align = align;
            this.color = color;
            this.bold = bold;
        }

        //#if MC_VERSION >= 12000
        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderLabel(GraphicContext.of(graphics));
        }
        //#elseif MC_VERSION >= 11904
        //$$ @Override
        //$$ public void renderWidget(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     renderLabel(GraphicContext.of(poseStack));
        //$$ }
        //#elseif MC_VERSION >= 11903
        //$$ @Override
        //$$ public void renderButton(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     renderLabel(GraphicContext.of(poseStack));
        //$$ }
        //#else
        //$$ @Override
        //$$ public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        //$$     renderLabel(GraphicContext.of(poseStack));
        //$$ }
        //#endif

        private void renderLabel(GraphicContext g) {
            var font = Minecraft.getInstance().font;
            //#if MC_VERSION >= 11903
            int x = this.getX();
            int y = this.getY();
            //#else
            //$$ int x = this.x;
            //$$ int y = this.y;
            //#endif
            int drawX = x;
            int textWidth = font.width(text.getString());
            switch (align) {
                case CENTER -> drawX = x - textWidth / 2;
                case RIGHT -> drawX = x - textWidth;
                case LEFT -> drawX = x;
            }
            if (bold) {
                g.drawString(font, text.copy().withStyle(style -> style.withBold(true)), drawX, y, color, false);
            } else {
                g.drawString(font, text, drawX, y, color, false);
            }
        }

        //#if MC_VERSION >= 11903
        @Override
        protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        }
        //#else
        //$$ @Override
        //$$ public void updateNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        //$$ }
        //#endif
    }

    protected static class ScrollEntry {
        final AbstractWidget widget;
        final int baseY;

        ScrollEntry(AbstractWidget widget, int baseY) {
            this.widget = widget;
            this.baseY = baseY;
        }

        void applyScroll(int offset) {
            //#if MC_VERSION >= 11903
            widget.setY(baseY + offset);
            //#else
            //$$ if (widget instanceof ConfigWidget) { ((ConfigWidget) widget).setY(baseY + offset); }
            //$$ else if (widget instanceof ConfigRow) { ((ConfigRow) widget).setY(baseY + offset); }
            //$$ else if (widget instanceof SliderWidget) { ((SliderWidget) widget).setY(baseY + offset); }
            //$$ else { widget.y = baseY + offset; }
            //#endif
        }
    }
}

