package com.fangsu.ui;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.customItem.CustomItems;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigWidget;
import com.fangsu.extraConfig.SliderWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public class ObjBlockConfigScreen extends BasicConfigScreen {

    private static final int GAP = 4;

    private final BaseObjBlockEntity be;

    private float translateX, translateY, translateZ;
    private float rotateX, rotateY, rotateZ;

    private final List<ConfigEntry<?>> configs;
    private boolean useSliderInput = true;

    public ObjBlockConfigScreen(BaseObjBlockEntity be) {
        super(Component.translatable("ui.fangsu.block.title"));
        this.be = be;

        if (be != null) {
            this.translateX = be.translateX;
            this.translateY = be.translateY;
            this.translateZ = be.translateZ;
            this.rotateX = (float) Math.toDegrees(be.rotateX);
            this.rotateY = (float) Math.toDegrees(be.rotateY);
            this.rotateZ = (float) Math.toDegrees(be.rotateZ);
            this.configs = be.getConfigs();
        } else {
            this.translateX = this.translateY = this.translateZ = 0f;
            this.rotateX = this.rotateY = this.rotateZ = 0f;
            this.configs = List.of();
        }
    }

    @Override
    protected void buildFixedWidgets() {
        int left = getPanelLeft();
        int btnWidth = getPanelWidth();

        Button toggleInputButton = Button.builder(getInputToggleLabel(), btn -> {
            useSliderInput = !useSliderInput;
            requestRebuild();
        }).bounds(left, 34, btnWidth, 20).build();
        addFixedWidget(toggleInputButton);

        closeButton = addFixedWidget(Button.builder(Component.translatable("ui.fangsu.block.close_and_save"), btn -> {
            sendToServer();
            onClose();
        }).bounds(left, this.height - 30, btnWidth, 20).build());
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        int y = layout.y;
        int panelCenterX = (getPanelLeft() + getPanelRight()) / 2;
        if (be == null) {
            addEntry(createTextLabel(panelCenterX, y, Component.literal("No block entity"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
            return;
        }

        int colLeft = getPanelLeft();
        int colWidth = getPanelWidth();

        // ---- 模型选择 ----
        addEntry(createTextLabel(panelCenterX, y, Component.translatable("ui.fangsu.block.modelSelect"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        y += 12;
        addEntry(addButton(colLeft, y, colWidth, 24, Component.translatable("ui.fangsu.block.mainModelSelect"),
                (b) -> Minecraft.getInstance().setScreen(new ModelSelectScreen(
                        Component.translatable("ui.fangsu.block.mainModelSelect"),
                        this.be,
                        CustomItems.items.get(this.be.getMainModelKey()),
                        (target) -> target.mainModel,
                        (target, v) -> target.mainModel = v, this,
                        () -> {
                            be.setDefaultSubModel();
                            be.afterChangeModel();
                        }
                ))
        ), y);
        y += 28;

        if (be.getSubModelInfos() != null) {
            List<SubModelDispInfo> infos = be.getSubModelInfos();
            for (SubModelDispInfo info : infos) {
                Button.OnPress c;
                if (info instanceof SubModelMethodInfo m) {
                    c = (b) -> m.getAction().run();
                } else
                    c = (b) -> Minecraft.getInstance().setScreen(new ModelSelectScreen(
                            info.name(), this.be, info.infos(), info.initialGetter(), info.setter(), this,
                            be::afterChangeModel
                    ));
                addEntry(addButton(colLeft, y, colWidth, 24, info.name(), c), y);
                y += 28;
            }
        }

        // ---- 平移（紧凑两行布局） ----
        addEntry(createTextLabel(panelCenterX, y, Component.translatable("ui.fangsu.block.translate"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        y += 10;
        if (useSliderInput) {
            y = addCompactTwoRow(colLeft, y, colWidth,
                    Component.translatable("ui.fangsu.block.transX"), translateX, -1, 1, 0.0625f,
                    v -> translateX = v, this::sendToServer, true);
            y = addCompactTwoRow(colLeft, y, colWidth,
                    Component.translatable("ui.fangsu.block.transY"), translateY, -1, 1, 0.0625f,
                    v -> translateY = v, this::sendToServer, true);
            y = addCompactTwoRow(colLeft, y, colWidth,
                    Component.translatable("ui.fangsu.block.transZ"), translateZ, -1, 1, 0.0625f,
                    v -> translateZ = v, this::sendToServer, true);
        } else {
            y = addAxisInputTwoRow(colLeft, y, colWidth,
                    Component.translatable("ui.fangsu.block.transX"), translateX, -1, 1, 0.0625f,
                    v -> translateX = v, this::sendToServer, true);
            y = addAxisInputTwoRow(colLeft, y, colWidth,
                    Component.translatable("ui.fangsu.block.transY"), translateY, -1, 1, 0.0625f,
                    v -> translateY = v, this::sendToServer, true);
            y = addAxisInputTwoRow(colLeft, y, colWidth,
                    Component.translatable("ui.fangsu.block.transZ"), translateZ, -1, 1, 0.0625f,
                    v -> translateZ = v, this::sendToServer, true);
        }

        // ---- 旋转（紧凑两行布局） ----
        addEntry(createTextLabel(panelCenterX, y, Component.translatable("ui.fangsu.block.rotate"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        y += 10;
        if (useSliderInput) {
            y = addCompactTwoRow(colLeft, y, colWidth,
                    Component.translatable("ui.fangsu.block.rotX"), rotateX, -180, 180, 5,
                    v -> rotateX = v, this::sendToServer, true);
            y = addCompactTwoRow(colLeft, y, colWidth,
                    Component.translatable("ui.fangsu.block.rotY"), rotateY, -180, 180, 5,
                    v -> rotateY = v, this::sendToServer, true);
            y = addCompactTwoRow(colLeft, y, colWidth,
                    Component.translatable("ui.fangsu.block.rotZ"), rotateZ, -180, 180, 5,
                    v -> rotateZ = v, this::sendToServer, true);
        } else {
            y = addAxisInputTwoRow(colLeft, y, colWidth,
                    Component.translatable("ui.fangsu.block.rotX"), rotateX, -180, 180, 5,
                    v -> rotateX = v, this::sendToServer, true);
            y = addAxisInputTwoRow(colLeft, y, colWidth,
                    Component.translatable("ui.fangsu.block.rotY"), rotateY, -180, 180, 5,
                    v -> rotateY = v, this::sendToServer, true);
            y = addAxisInputTwoRow(colLeft, y, colWidth,
                    Component.translatable("ui.fangsu.block.rotZ"), rotateZ, -180, 180, 5,
                    v -> rotateZ = v, this::sendToServer, true);
        }

        // ---- 额外配置（标准两行布局，比平移旋转高一点） ----
        if (configs != null && !configs.isEmpty()) {
            addEntry(createTextLabel(panelCenterX, y, Component.translatable("ui.fangsu.block.extras"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
            y += 10;
            for (ConfigEntry<?> c : configs) {
                c.load(be);
                c.setChangeListener(entry -> {
                    if (entry.isSaveOnChange()) {
                        entry.save(be);
                        be.sendUpdateC2S();
                        requestRebuild();
                    }
                });
                if (!c.isVisible()) {
                    continue;
                }
                // 第一行：名称，左对齐，矮高度
                addEntry(createTextLabel(colLeft, y, c.title, TextLabel.Align.LEFT, 0xFFFFFF, false), y);
                y += 10;
                // 第二行：控件，正常高度
                ConfigWidget w = c.createWidget(colLeft, y, (int)(colWidth * 0.35f), (int)(colWidth * 0.65f));
                addRenderableWidget(w);
                addEntry(w, y);
                y += w.getHeight() + 6;
            }
        }
    }

    @Override
    protected int getPanelLeft() {
        return GAP;
    }

    @Override
    protected int getPanelRight() {
        return this.width / 5 - GAP;
    }

    @Override
    protected int getPanelTop() {
        return 30;
    }

    @Override
    protected int getPanelBottom() {
        return this.height - 30;
    }

    @Override
    protected int getContentTop() {
        return 58; // 标题+切换按钮之下
    }

    @Override
    protected int getContentBottom() {
        return this.height - 42; // 保存按钮之上
    }

    @Override
    protected int getContentLeft() {
        return getPanelLeft();
    }

    @Override
    protected int getContentRight() {
        return getPanelRight();
    }

    @Override
    protected void renderPanelBackground(GuiGraphics graphics) {
        // 屏幕左 1/5 填充纯黑背景（从最左侧开始）
        int bgRight = this.width / 5;
        graphics.fill(0, 0, bgRight, this.height, 0xFF000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 标题 - 绘制在黑色背景上
        int titleX = (getPanelLeft() + getPanelRight()) / 2 - this.font.width(this.title.getString()) / 2;
        int titleY = 2;
        graphics.drawString(this.font, this.title, titleX, titleY, 0xFFFFFF, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component getInputToggleLabel() {
        return Component.translatable(useSliderInput
                ? "ui.fangsu.block.toggle_input"
                : "ui.fangsu.block.toggle_slider");
    }

    private int getPanelWidth() {
        return getPanelRight() - getPanelLeft();
    }

    /**
     * 紧凑两行布局：上一行矮标签，下一行滑块
     * @param compact 为 true 时行高更紧凑（平移/旋转用）
     */
    private int addCompactTwoRow(int areaLeft, int y, int rowWidth,
                                 Component label, float value,
                                 float min, float max, float step,
                                 Consumer<Float> setter, Runnable onChanged,
                                 boolean compact) {
        int labelHeight = compact ? 8 : 10;
        // 第一行：名称，左对齐，矮高度
        addEntry(createTextLabel(areaLeft, y, label, TextLabel.Align.LEFT, 0xFFFFFF, false), y);
        y += labelHeight;
        // 第二行：滑块，正常高度
        int sliderWidth = rowWidth;
        if (sliderWidth < 60) sliderWidth = 60;
        SliderWidget slider = new SliderWidget(areaLeft, y, sliderWidth, 20,
                Component.empty(), value, min, max, step,
                v -> {
                    setter.accept(v);
                    onChanged.run();
                });
        this.addRenderableWidget(slider);
        addEntry(slider, y);
        return y + 20 + (compact ? 2 : 4);
    }

    /**
     * 紧凑两行布局：上一行矮标签，下一行输入框
     */
    private int addAxisInputTwoRow(int areaLeft, int y, int rowWidth,
                                   Component label, float value,
                                   float min, float max, float step,
                                   Consumer<Float> setter, Runnable onChanged,
                                   boolean compact) {
        int labelHeight = compact ? 8 : 10;
        // 第一行：名称，左对齐，矮高度
        addEntry(createTextLabel(areaLeft, y, label, TextLabel.Align.LEFT, 0xFFFFFF, false), y);
        y += labelHeight;
        // 第二行：输入框
        int inputWidth = Math.min(rowWidth, 80);
        EditBox box = new EditBox(this.font, areaLeft, y, inputWidth, 20, Component.empty());
        box.setValue(formatValue(value));
        box.setResponder(text -> {
            Float v = parseFloat(text);
            if (v == null) return;
            float snapped = snap(v, min, max, step);
            setter.accept(snapped);
            onChanged.run();
        });
        this.addRenderableWidget(box);
        addEntry(box, y);
        return y + 20 + (compact ? 2 : 4);
    }

    private void sendToServer() {
        if (be == null) return;
        be.translateX = translateX;
        be.translateY = translateY;
        be.translateZ = translateZ;
        be.rotateX = (float) Math.toRadians(rotateX);
        be.rotateY = (float) Math.toRadians(rotateY);
        be.rotateZ = (float) Math.toRadians(rotateZ);
        be.sendUpdateC2S();
    }

    @Override
    public void onClose() {
        if (be != null) {
            if (configs != null) {
                for (ConfigEntry<?> c : configs) {
                    c.save(be);
                }
            }
            be.sendUpdateC2S();
        }
        super.onClose();
    }
}
