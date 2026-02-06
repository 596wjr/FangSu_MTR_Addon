package com.fangsu.ui;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.extraConfig.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ObjBlockConfigScreen extends Screen {

    // 切换：true -> 实时每次滑动调用 sendUpdateC2S()
    //      false -> 只在 onClose() 时调用一次 sendUpdateC2S()
    private static final boolean REALTIME = false;

    private final BaseObjBlockEntity be;

    // 保存 BE 的数值本地副本（避免直接频繁访问 be）
    private float translateX, translateY, translateZ;
    private float rotateX, rotateY, rotateZ;

    // 可滚动区域偏移（向上为正）
    private int scrollOffset = 0;

    // 存放所有动态创建的控件，便于在滚动时调整位置
    private Button closeButton;
    private final List<ScrollEntry> entries = new ArrayList<>();

    private final List<ConfigEntry<?>> configs;
    private boolean useSliderInput = true;
    private boolean pendingRebuild = false;

    public ObjBlockConfigScreen(BaseObjBlockEntity be) {
        super(Component.translatable("ui.fangsu.block.title"));
        this.be = be;

        // 从 BE 读取初始值（防空）
        if (be != null) {
            this.translateX = be.translateX;
            this.translateY = be.translateY;
            this.translateZ = be.translateZ;
            this.rotateX = be.rotateX;
            this.rotateY = be.rotateY;
            this.rotateZ = be.rotateZ;
            this.configs = be.getConfigs();
        } else {
            // 默认值（若 BE 为 null）
            this.translateX = this.translateY = this.translateZ = 0f;
            this.rotateX = this.rotateY = this.rotateZ = 0f;
            this.configs = List.of();
        }
    }

    @Override
    protected void init() {
        super.init();
        entries.clear();
        scrollOffset = 0;

        // 计算布局基准
        int cx = this.width / 2;
        int startY = 60; // 首行基线
        int spacing = 70;
        int y = startY;

        int areaLeft = 40;
        int areaRight = this.width - 40;
        int contentWidth = areaRight - areaLeft;
        int labelW = (int) (contentWidth * 0.4f);
        int fieldW = contentWidth - labelW;

        int leftX = areaLeft;

        Button toggleInputButton = Button.builder(getInputToggleLabel(), btn -> {
            useSliderInput = !useSliderInput;
            requestRebuild();
        }).bounds(this.width - 170, 34, 130, 20).build();
        addRenderableWidget(toggleInputButton);

        addEntry(createTextLabel(cx, y, Component.translatable("ui.fangsu.block.translate"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        y += 12;
        y = addAxisControls(cx, spacing, y,
                Component.translatable("ui.fangsu.block.transX"),
                Component.translatable("ui.fangsu.block.transY"),
                Component.translatable("ui.fangsu.block.transZ"),
                translateX,
                translateY,
                translateZ,
                -1, 1, 0.0625f,
                v -> translateX = v,
                v -> translateY = v,
                v -> translateZ = v);
        y += 28;

        y = addAxisControls(cx, spacing, y,
                Component.translatable("ui.fangsu.block.rotX"),
                Component.translatable("ui.fangsu.block.rotY"),
                Component.translatable("ui.fangsu.block.rotZ"),
                rotateX,
                rotateY,
                rotateZ,
                -180, 180, 5,
                v -> rotateX = v,
                v -> rotateY = v,
                v -> rotateZ = v);
        y += 28;

        if (!configs.isEmpty()) {
            addEntry(createTextLabel(cx, y, Component.translatable("ui.fangsu.block.extras"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
            y += 12;
            for (ConfigEntry<?> c : configs) {
                c.load(be);
                c.setChangeListener(entry -> {
                    if (entry.isSaveOnChange()) {
                        entry.save(be);
                        be.sendUpdateC2S();
                        requestRebuild();
                    }
                });
                if (!c.isVisible(be)) {
                    continue;
                }
                ConfigWidget w = c.createWidget(leftX, y, labelW, fieldW);
                addRenderableWidget(w);
                addEntry(w, y);
                y += w.getHeight() + 4;
            }
        }

        closeButton = this.addRenderableWidget(Button.builder(Component.translatable("ui.fangsu.block.close_and_save"),
                btn -> {
                    if (!REALTIME) sendToServer();
                    onClose();
                }).bounds(this.width / 2 - 50, this.height - 40, 100, 20).build());

    }

    private Component getInputToggleLabel() {
        return Component.translatable(useSliderInput
                ? "ui.fangsu.block.toggle_input"
                : "ui.fangsu.block.toggle_slider");
    }

    private void requestRebuild() {
        pendingRebuild = true;
    }

    private int addAxisControls(
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
            Consumer<Float> setZ
    ) {
        if (useSliderInput) {
            addEntry(createSlider(centerX - spacing, baseY, valueX, v -> {
                setX.accept(v);
                if (REALTIME) sendToServer();
            }, min, max, step), baseY);
            addEntry(createSlider(centerX, baseY, valueY, v -> {
                setY.accept(v);
                if (REALTIME) sendToServer();
            }, min, max, step), baseY);
            addEntry(createSlider(centerX + spacing, baseY, valueZ, v -> {
                setZ.accept(v);
                if (REALTIME) sendToServer();
            }, min, max, step), baseY);
            return baseY;
        }
        baseY += 12;
        addEntry(createAxisInput(centerX - spacing, baseY, labelX, valueX, min, max, step, setX), baseY);
        addEntry(createAxisInput(centerX, baseY, labelY, valueY, min, max, step, setY), baseY);
        addEntry(createAxisInput(centerX + spacing, baseY, labelZ, valueZ, min, max, step, setZ), baseY);
        return baseY;
    }

    private AbstractWidget createAxisInput(
            int x,
            int baseY,
            Component label,
            float initialValue,
            float min,
            float max,
            float step,
            Consumer<Float> setter
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
            if (REALTIME) sendToServer();
        });
        this.addRenderableWidget(box);
        return box;
    }

    /**
     * 创建带步进的滑块。
     */
    private SliderWidget createSlider(int cx, int baseY, float initialValue, Consumer<Float> setter, float min, float max, float step) {
        SliderWidget slider = new SliderWidget(cx - 30, baseY, 60, 20, Component.empty(), initialValue, min, max, step, setter);
        this.addRenderableWidget(slider);
        return slider;
    }

    private TextLabel createTextLabel(int x, int y, Component text, TextLabel.Align align, int color, boolean bold) {
        TextLabel label = new TextLabel(x, y, text, align, color, bold);
        this.addRenderableWidget(label);
        return label;
    }

    private void addEntry(AbstractWidget widget, int baseY) {
        entries.add(new ScrollEntry(widget, baseY));
    }

    // 将本地副本的数据写回 BE 并调用 sendUpdateC2S()
    private void sendToServer() {
        if (be == null) return;
        be.translateX = translateX;
        be.translateY = translateY;
        be.translateZ = translateZ;
        be.rotateX = rotateX;
        be.rotateY = rotateY;
        be.rotateZ = rotateZ;

        // 你提供的封装方法
        be.sendUpdateC2S();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (pendingRebuild) {
            pendingRebuild = false;
            clearWidgets();
            init();
        }
        renderBackground(graphics);

        int areaLeft = 40;
        int areaTop = 30;
        int areaRight = this.width - 40;
        int areaBottom = this.height - 60;
        graphics.fill(areaLeft, areaTop, areaRight, areaBottom, 0xCCFFFFFF); // 半透明白

        // 标题（居中，使用深色文本以便在浅底上清晰显示）
        String titleText = this.title.getString();
        int titleX = this.width / 2 - this.font.width(titleText) / 2;
        int titleY = areaTop - 18;
        graphics.drawString(this.font, this.title, titleX, titleY, 0x101010, false);

        // 在渲染每一帧之前，给 sliders 应用 scrollOffset —— 修改它们的 y 值
        for (ScrollEntry e : entries) {
            e.applyScroll(scrollOffset);
        }

        // 现在调用父类渲染（会渲染所有 child widgets）
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {

        int visibleTop = 30;
        int visibleBottom = this.height - 60;
        int visibleHeight = visibleBottom - visibleTop;

        int contentBottom = getActualContentBottom();
        int contentTop = entries.isEmpty() ? 0 : entries.get(0).baseY;
        int contentHeight = contentBottom - contentTop;

        // 如果内容根本没超过可视区域，直接禁止滚动
        if (contentHeight <= visibleHeight) {
            scrollOffset = 0;
            return false;
        }

        scrollOffset += delta * 12;

        int minOffset = visibleHeight - contentHeight;
        scrollOffset = Mth.clamp(scrollOffset, minOffset, 0);

        return true;
    }


    private int getActualContentBottom() {
        int bottom = 0;
        for (ScrollEntry e : entries) {
            bottom = Math.max(bottom, e.baseY + e.widget.getHeight());
        }
        return bottom;
    }

    private Float parseFloat(String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private float snap(float value, float min, float max, float step) {
        float clamped = Mth.clamp(value, min, max);
        if (step <= 0) {
            return clamped;
        }
        return Math.round(clamped / step) * step;
    }

    private String formatValue(float value) {
        return String.format("%.3f", value);
    }


    @Override
    public void onClose() {
        if (be != null) {
            for (ConfigEntry<?> c : configs) {
                c.save(be);
            }
            be.sendUpdateC2S();
        }
        super.onClose();
    }


    private static class TextLabel extends AbstractWidget {
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

            // 根据对齐方式调整 x
            String labelText = text.getString();
            int textWidth = font.width(labelText);
            switch (align) {
                case CENTER -> drawX = this.getX() - textWidth / 2;
                case RIGHT -> drawX = this.getX() - textWidth;
                case LEFT -> drawX = this.getX();
            }

            // 绘制文本，可加粗
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


    private static class ScrollEntry {
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
