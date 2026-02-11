package com.fangsu.ui;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.customItem.CustomItems;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ObjBlockConfigScreen extends BasicConfigScreen {

    private static final boolean REALTIME = false;

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
            this.rotateX = be.rotateX;
            this.rotateY = be.rotateY;
            this.rotateZ = be.rotateZ;
            this.configs = be.getConfigs();
        } else {
            this.translateX = this.translateY = this.translateZ = 0f;
            this.rotateX = this.rotateY = this.rotateZ = 0f;
            this.configs = List.of();
        }
    }

    @Override
    protected void buildFixedWidgets() {
        Button toggleInputButton = Button.builder(getInputToggleLabel(), btn -> {
            useSliderInput = !useSliderInput;
            requestRebuild();
        }).bounds(this.width - 170, 34, 130, 20).build();
        addFixedWidget(toggleInputButton);

        closeButton = addFixedWidget(Button.builder(Component.translatable("ui.fangsu.block.close_and_save"), btn -> {
            if (!REALTIME) sendToServer();
            onClose();
        }).bounds(this.width / 2 - 50, this.height - 40, 100, 20).build());
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        int spacing = 70;
        int y = layout.y;
        if (be == null) {
            addEntry(createTextLabel(layout.centerX, y, Component.literal("No block entity"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
            return;
        }

        addEntry(createTextLabel(layout.centerX, y, Component.translatable("ui.fangsu.block.modelSelect"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        y += 12;
        addEntry(addButton(layout.areaLeft, y, layout.areaRight - layout.areaLeft, 24, Component.translatable("ui.fangsu.block.mainModelSelect"),
                (b) -> Minecraft.getInstance().setScreen(new ModelSelectScreen(
                        Component.translatable("ui.fangsu.block.mainModelSelect"),
                        this.be,
                        CustomItems.items.get(this.be.getMainModelKey()),
                        (target) -> target.mainModel,
                        (target, v) -> target.mainModel = v, this
                ))
        ), y);
        y += 28;

        if (be.getSubModelInfos() != null) {
            List<SubModelDispInfo> infos = be.getSubModelInfos();
            for (int i = 0; i < infos.size(); i++) {
                SubModelDispInfo info = infos.get(i);
                if (i + 1 == infos.size() && i % 2 == 0) {
                    addEntry(addButton(layout.areaLeft, y, layout.areaRight - layout.areaLeft, 24, info.name(),
                            (b) -> Minecraft.getInstance().setScreen(new ModelSelectScreen(
                                    info.name(), this.be, info.infos(), info.initialGetter(), info.setter(), this
                            ))
                    ), y);
                    y += 28;
                } else if (i % 2 == 0) {
                    addEntry(addButton(layout.areaLeft, y, (layout.areaRight - layout.areaLeft) / 2 - 2, 24, info.name(),
                            (b) -> Minecraft.getInstance().setScreen(new ModelSelectScreen(
                                    info.name(), this.be, info.infos(), info.initialGetter(), info.setter(), this
                            ))
                    ), y);
                } else {
                    addEntry(addButton(layout.areaLeft + ((layout.areaRight - layout.areaLeft) / 2) + 2, y,
                            (layout.areaRight - layout.areaLeft) / 2 - 2, 24, info.name(),
                            (b) -> Minecraft.getInstance().setScreen(new ModelSelectScreen(
                                    info.name(), this.be, info.infos(), info.initialGetter(), info.setter(), this
                            ))
                    ), y);
                    y += 28;
                }
            }
        }

        addEntry(createTextLabel(layout.centerX, y, Component.translatable("ui.fangsu.block.translate"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
        y += 12;
        y = addAxisControls(
                layout.centerX, spacing, y,
                Component.translatable("ui.fangsu.block.transX"),
                Component.translatable("ui.fangsu.block.transY"),
                Component.translatable("ui.fangsu.block.transZ"),
                translateX, translateY, translateZ,
                -1, 1, 0.0625f,
                v -> translateX = v,
                v -> translateY = v,
                v -> translateZ = v,
                () -> {
                    if (REALTIME) sendToServer();
                },
                useSliderInput
        );
        y += 28;

        y = addAxisControls(
                layout.centerX, spacing, y,
                Component.translatable("ui.fangsu.block.rotX"),
                Component.translatable("ui.fangsu.block.rotY"),
                Component.translatable("ui.fangsu.block.rotZ"),
                rotateX, rotateY, rotateZ,
                -180, 180, 5,
                v -> rotateX = v,
                v -> rotateY = v,
                v -> rotateZ = v,
                () -> {
                    if (REALTIME) sendToServer();
                },
                useSliderInput
        );
        y += 28;

        if (configs != null && !configs.isEmpty()) {
            addEntry(createTextLabel(layout.centerX, y, Component.translatable("ui.fangsu.block.extras"), TextLabel.Align.CENTER, 0xFFFFFF, false), y);
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
                ConfigWidget w = c.createWidget(layout.areaLeft, y, layout.labelWidth, layout.fieldWidth);
                addRenderableWidget(w);
                addEntry(w, y);
                y += w.getHeight() + 4;
            }
        }
    }

    private Component getInputToggleLabel() {
        return Component.translatable(useSliderInput
                ? "ui.fangsu.block.toggle_input"
                : "ui.fangsu.block.toggle_slider");
    }

    private void sendToServer() {
        if (be == null) return;
        be.translateX = translateX;
        be.translateY = translateY;
        be.translateZ = translateZ;
        be.rotateX = rotateX;
        be.rotateY = rotateY;
        be.rotateZ = rotateZ;
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
