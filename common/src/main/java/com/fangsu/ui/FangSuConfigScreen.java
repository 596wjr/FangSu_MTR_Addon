package com.fangsu.ui;

import com.fangsu.config.FangSuConfig;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 模组级配置草稿：应用后立即生效，返回/Esc 放弃尚未应用的修改。 */
public final class FangSuConfigScreen extends BasicConfigScreen {
    private final Screen parent;
    private boolean quality = FangSuConfig.highQualityShape();
    private boolean optimizeTranslucent = FangSuConfig.optimizeTranslucentFaces();
    private boolean tilt = FangSuConfig.cameraTiltEnabled();
    private String step = Double.toString(FangSuConfig.shapeStep());
    private String strength = Double.toString(FangSuConfig.cameraTiltStrength());
    private String status = "ui.fangsu.config.scope";
    private EditBox stepInput;

    public FangSuConfigScreen(Screen parent) {
        super(ComponentHelper.translatable("ui.fangsu.config.title"));
        this.parent = parent;
    }

    @Override
    protected void buildFixedWidgets() {
        int left = getPanelLeft(), half = (getPanelRight() - left - 4) / 2;
        addFixedWidget(ComponentHelper.button(left, height - 40, half, 20,
                ComponentHelper.translatable("ui.fangsu.config.apply"), button -> apply()));
        closeButton = addFixedWidget(ComponentHelper.button(left + half + 4, height - 40, half, 20,
                ComponentHelper.translatable("gui.back"), button -> onClose()));
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        int x = layout.areaLeft, width = layout.areaRight - x, y = layout.y;
        y = addWrappedText(x, y, width, ComponentHelper.translatable(status),
                status.endsWith("error") ? 0xAA2222 : 0x555555) + 10;
        addEntry(createTextLabel(x, y, ComponentHelper.translatable("ui.fangsu.config.collision"),
                TextLabel.Align.LEFT, 0x303030, true), y);
        y += 20;
        addEntry(addButton(x, y, width, 20, toggleLabel("ui.fangsu.config.quality", quality), button -> {
            quality = !quality;
            button.setMessage(toggleLabel("ui.fangsu.config.quality", quality));
        }), y);
        y += 26;
        stepInput = numberField(x, y, width, "ui.fangsu.config.step", step, value -> step = value);
        y += 26;
        String[] names = {"coarse", "standard", "fine"};
        double[] steps = {0.25, 0.1, 0.05};
        int third = (width - 8) / 3;
        for (int i = 0; i < names.length; i++) {
            final double value = steps[i];
            addEntry(addButton(x + i * (third + 4), y, third, 20,
                    ComponentHelper.translatable("ui.fangsu.config." + names[i]), button -> {
                        quality = true;
                        stepInput.setValue(Double.toString(value));
                        requestRebuild();
                    }), y);
        }
        y += 28;
        y = addWrappedText(x, y, width, ComponentHelper.translatable("ui.fangsu.config.step_hint"), 0x555555) + 14;
        addEntry(addButton(x, y, width, 20, toggleLabel("ui.fangsu.config.tilt", tilt), button -> {
            tilt = !tilt;
            button.setMessage(toggleLabel("ui.fangsu.config.tilt", tilt));
        }), y);
        y += 26;
        numberField(x, y, width, "ui.fangsu.config.strength", strength, value -> strength = value);
        y += 30;
        addEntry(addButton(x, y, width, 20, toggleLabel("ui.fangsu.config.optimize_translucent", optimizeTranslucent), button -> {
            optimizeTranslucent = !optimizeTranslucent;
            button.setMessage(toggleLabel("ui.fangsu.config.optimize_translucent", optimizeTranslucent));
        }), y);
        y += 26;
        addEntry(addButton(x, y, width, 20, ComponentHelper.translatable("ui.fangsu.config.defaults"), button -> {
            optimizeTranslucent = false;
            quality = FangSuConfig.DEFAULT_HIGH_QUALITY_SHAPE;
            step = Double.toString(FangSuConfig.DEFAULT_SHAPE_STEP);
            tilt = FangSuConfig.DEFAULT_CAMERA_TILT_ENABLED;
            strength = Double.toString(FangSuConfig.DEFAULT_CAMERA_TILT_STRENGTH);
            requestRebuild();
        }), y);
    }

    private EditBox numberField(int x, int y, int width, String key, String value,
                                java.util.function.Consumer<String> change) {
        addEntry(createTextLabel(x, y + 6, ComponentHelper.translatable(key),
                TextLabel.Align.LEFT, 0x303030, false), y + 6);
        EditBox box = new EditBox(font, x + width - 80, y, 80, 20, ComponentHelper.translatable(key));
        box.setMaxLength(16);
        box.setValue(value);
        box.setResponder(change);
        addRenderableWidget(box);
        addEntry(box, y);
        return box;
    }

    private Component toggleLabel(String key, boolean enabled) {
        return ComponentHelper.translatable(key).append(": ")
                .append(ComponentHelper.translatable(enabled ? "options.on" : "options.off"));
    }

    private void apply() {
        try {
            double parsedStep = Double.parseDouble(step.trim());
            double parsedStrength = Double.parseDouble(strength.trim());
            if (!Double.isFinite(parsedStep) || parsedStep < 0.01 || parsedStep > 1
                    || !Double.isFinite(parsedStrength) || parsedStrength < 0 || parsedStrength > 2) {
                status = "ui.fangsu.config.value_error";
            } else {
                status = FangSuConfig.saveAndApply(quality, parsedStep, tilt, parsedStrength, optimizeTranslucent)
                        ? "ui.fangsu.config.applied" : "ui.fangsu.config.save_error";
            }
        } catch (NumberFormatException e) {
            status = "ui.fangsu.config.value_error";
        }
        scrollOffset = 0;
        requestRebuild();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
