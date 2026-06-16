package com.fangsu.ui;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ConfigScreen extends BasicConfigScreen {
    private final List<ConfigEntry<?>> configs;
    private static final boolean REALTIME = false;
    private final Screen parent;

    public ConfigScreen(Component title, List<ConfigEntry<?>> configs, Screen parent) {
        super(title);
        this.configs = configs;
        this.parent = parent;
    }

    public ConfigScreen(Component title, List<ConfigEntry<?>> configs) {
        this(title, configs, null);
    }

    @Override
    protected void buildFixedWidgets() {
        //#if MC_VERSION >= 11903
        closeButton = addFixedWidget(Button.builder(ComponentHelper.translatable("ui.fangsu.block.close_and_save"), btn -> { if (!REALTIME) saveAll(); onClose(); }).bounds(this.width / 2 - 50, this.height - 40, 100, 20).build());
        //#else
        //$$ closeButton = addFixedWidget(new Button(this.width / 2 - 50, this.height - 40, 100, 20, ComponentHelper.translatable("ui.fangsu.block.close_and_save"), btn -> { if (!REALTIME) saveAll(); onClose(); }));
        //#endif
    }

    @Override
    protected void buildScrollableContent(ContentLayout layout) {
        int y = layout.y;
        if (configs != null && !configs.isEmpty()) {
            y += 12;
            for (ConfigEntry<?> c : configs) {
                c.setChangeListener(entry -> {
                    if (entry.isSaveOnChange()) {
                        entry.save(null);
                        requestRebuild();
                    }
                });
                if (!c.isVisible()) {
                    continue;
                }
                ConfigWidget w = c.createWidget(layout.areaLeft, y, layout.labelWidth, layout.fieldWidth);
                addRenderableWidget(w);
                addEntry(w, y);
                y += w.getHeight() + 4;
            }
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void saveAll() {
        if (configs != null && !configs.isEmpty()) {
            for (ConfigEntry<?> c : configs) {
                c.save(null);
            }
        }
    }
}

