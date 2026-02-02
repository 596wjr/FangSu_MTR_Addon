package com.fangsu.ui;

import com.fangsu.blockEntities.BaseObjBlockEntity;
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

public class TransformScreen extends Screen {

    // 切换：true -> 实时每次滑动调用 sendUpdateC2S()
    //      false -> 只在 onClose() 时调用一次 sendUpdateC2S()
    private static final boolean REALTIME = false;

    private final BaseObjBlockEntity be;

    // 保存 BE 的数值本地副本（避免直接频繁访问 be）
    private float translateX, translateY, translateZ;
    private float rotateX, rotateY, rotateZ;

    // 可滚动区域偏移（向上为正）
    private int scrollOffset = 0;
    private int contentHeight = 300; // 内容总高度，后面需要扩展时调整

    // 存放所有动态创建的控件，便于在滚动时调整位置
    private Button closeButton;
    private List<ScrollEntry> entries = new ArrayList<>();


    public TransformScreen(BaseObjBlockEntity be) {
        super(Component.literal("方块配置"));
        this.be = be;

        // 从 BE 读取初始值（防空）
        if (be != null) {
            this.translateX = be.translateX;
            this.translateY = be.translateY;
            this.translateZ = be.translateZ;
            this.rotateX = be.rotateX;
            this.rotateY = be.rotateY;
            this.rotateZ = be.rotateZ;
        } else {
            // 默认值（若 BE 为 null）
            this.translateX = this.translateY = this.translateZ = 0f;
            this.rotateX = this.rotateY = this.rotateZ = 0f;
        }
    }

    @Override
    protected void init() {
        super.init();
        entries.clear();

        // 计算布局基准
        int cx = this.width / 2;
        int startY = this.height / 2 - 80; // 首行基线
        int spacing = 70;
        int y = startY;

        entries.add(new ScrollEntry(createTextLabel(cx, y, Component.translatable("ui.fangsu.block.translate").getString(), TextLabel.Align.CENTER, 0xFFFFFF, false), y));
        y += 8;
        entries.add(new ScrollEntry(createSlider(cx - spacing, y, "X", translateX, v -> {
            translateX = v;
            if (REALTIME) sendToServer();
        }, -1, 1, 0.0625), y));
        entries.add(new ScrollEntry(createSlider(cx, y, "Y", translateY, v -> {
            translateY = v;
            if (REALTIME) sendToServer();
        }, -1, 1, 0.0625), y));
        entries.add(new ScrollEntry(createSlider(cx + spacing, y, "Z", translateZ, v -> {
            translateZ = v;
            if (REALTIME) sendToServer();
        }, -1, 1, 0.0625), y));
        y += 28;

        entries.add(new ScrollEntry(createSlider(cx - spacing, y, "RX", rotateX, v -> {
            rotateX = v;
            if (REALTIME) sendToServer();
        }, -180, 180, 5), y));
        entries.add(new ScrollEntry(createSlider(cx, y, "RY", rotateY, v -> {
            rotateY = v;
            if (REALTIME) sendToServer();
        }, -180, 180, 5), y));
        entries.add(new ScrollEntry(createSlider(cx + spacing, y, "RZ", rotateZ, v -> {
            rotateZ = v;
            if (REALTIME) sendToServer();
        }, -180, 180, 5), y));
        y += 28;

        if (be.getConfigs() != null) {
            entries.add(new ScrollEntry(createTextLabel(cx, y, Component.translatable("ui.fangsu.block.extras").getString(), TextLabel.Align.CENTER, 0xFFFFFF, false), y));
            y += 8;
        }

        closeButton = this.addRenderableWidget(Button.builder(Component.literal("关闭并保存"),
                btn -> {
                    if (!REALTIME) sendToServer();
                    onClose();
                }).bounds(this.width / 2 - 50, this.height - 40, 100, 20).build());

        contentHeight = Math.max(400, startY + 200);
    }

    // 创建自定义滑条（返回实例，不直接 addRenderableWidget，以便 later update positions）
    private BetterSlider createSlider(int cx, int baseY, String label, float initialValue, Consumer<Float> setter) {
        BetterSlider slider = new BetterSlider(cx - 30, baseY, 60, 20, Component.empty(), initialValue, label, setter);
        this.addRenderableWidget(slider);
        return slider;
    }

    private BetterSlider createSlider(int cx, int baseY, String label, float initialValue, Consumer<Float> setter, double min, double max, double step) {
        BetterSlider slider = new BetterSlider(cx - 30, baseY, 60, 20, Component.empty(), initialValue, label, setter, min, max, step);
        this.addRenderableWidget(slider);
        return slider;
    }

    private Button createWideButton(
            int centerX,
            int baseY,
            int width,
            String text,
            Runnable onClick
    ) {
        Button btn = Button.builder(Component.literal(text), b -> onClick.run())
                .bounds(centerX - width / 2, baseY, width, 20)
                .build();
        this.addRenderableWidget(btn);
        return btn;
    }

    private EditBox createEditBox(
            int x,
            int baseY,
            int width,
            String placeholder,
            String initialValue,
            java.util.function.Consumer<String> onChanged
    ) {
        EditBox box = new EditBox(this.font, x, baseY, width, 20, Component.empty());
        box.setValue(initialValue);
        box.setHint(Component.literal(placeholder));

        box.setResponder(text -> {
            onChanged.accept(text);
            if (REALTIME) sendToServer();
        });

        this.addRenderableWidget(box);
        return box;
    }

    private <T> CycleButton<T> createDropdown(
            int x,
            int baseY,
            int width,
            String label,
            List<T> values,
            T initial,
            java.util.function.Consumer<T> onChanged
    ) {
        CycleButton<T> btn = CycleButton.<T>builder(v -> Component.literal(label + ": " + v))
                .withValues(values)
                .withInitialValue(initial)
                .create(x, baseY, width, 20, Component.empty(),
                        (b, v) -> {
                            onChanged.accept(v);
                            if (REALTIME) sendToServer();
                        });

        this.addRenderableWidget(btn);
        return btn;
    }

    private TextLabel createTextLabel(int x, int y, String text, TextLabel.Align align, int color, boolean bold) {
        TextLabel label = new TextLabel(x, y, text, align, color, bold);
        this.addRenderableWidget(label);
        entries.add(new ScrollEntry(label, y));
        return label;
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
        // 背景（原生暗背景）
        renderBackground(graphics);

        // 绘制一个半透明的浅底用于控件区域，解决“背景太深看不清文字”的问题
        int areaLeft = 40;
        int areaTop = 30;
        int areaRight = this.width - 40;
        int areaBottom = this.height - 60;
        graphics.fill(areaLeft, areaTop, areaRight, areaBottom, 0xCCFFFFFF); // 半透明白

        // 标题（居中，使用深色文本以便在浅底上清晰显示）
        String title = this.title.getString();
        int titleX = this.width / 2 - this.font.width(title) / 2;
        int titleY = areaTop - 18;
        graphics.drawString(this.font, Component.literal(title), titleX, titleY, 0x101010, false);

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
            e.applyScroll(scrollOffset);
        }
        return bottom;
    }


    @Override
    public void onClose() {
        // 如果关闭通过 ESC 或其他方式，确保在非实时模式下保存一次
        if (!REALTIME) {
            sendToServer();
        }
        super.onClose();
    }

    private static class BetterSlider extends AbstractSliderButton {
        // baseY 为初始化时的参考 y（不含滚动偏移）
        public final int baseY;
        private final String label;
        private final java.util.function.Consumer<Float> setter;

        private double minValue = 0.0;
        private double maxValue = 1.0;
        private double step = 0.1;

        public BetterSlider(int x, int y, int width, int height, Component message, double value, String label, java.util.function.Consumer<Float> setter) {
            super(x, y, width, height, message, value);
            this.baseY = y;
            this.label = label;
            this.setter = setter;
            updateMessage();
        }

        public BetterSlider(int x, int y, int width, int height, Component message, double value, String label, java.util.function.Consumer<Float> setter, double minValue, double maxValue, double step) {
            super(x, y, width, height, message, (value - minValue) / (maxValue - minValue));
            this.baseY = y;
            this.label = label;
            this.setter = setter;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.step = step;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double real = toReal();
            this.setMessage(Component.literal(label + ": " + String.format("%.2f", real)));
        }

        @Override
        protected void applyValue() {
            float real = (float) toReal();
            setter.accept((real));
        }

        private double toReal() {
            double raw = this.minValue + (float) value * (this.maxValue - this.minValue);
            return Math.round(raw / this.step) * this.step;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (!this.active) return false;
            if (true) {
                // 计算 normalized value，根据鼠标 x 在滑条内部的位置
                double newValue = (mouseX - (this.getX() + 4)) / (double) (this.width - 8);
                this.value = Mth.clamp(newValue, 0.0, 1.0);
                // update message 每帧保持文本更新
                updateMessage();
                // 立即回调 setter（用于实时 UI / 数据更新）
                float real = (float) toReal();
                setter.accept(real);
                return true;
            }
            return false;
        }
    }

    private static class TextLabel extends AbstractWidget {
        public enum Align {LEFT, CENTER, RIGHT}

        private final String text;
        private final int color;
        private final boolean bold;
        private final Align align;

        public TextLabel(int x, int y, String text, Align align, int color, boolean bold) {
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
            int textWidth = font.width(text);
            switch (align) {
                case CENTER -> drawX = this.getX() - textWidth / 2;
                case RIGHT -> drawX = this.getX() - textWidth;
                case LEFT -> drawX = this.getX();
            }

            // 绘制文本，可加粗
            if (bold) {
                graphics.drawString(font, Component.literal(text).withStyle(style -> style.withBold(true)), drawX, this.getY(), color, false);
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
