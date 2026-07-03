package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ui.ResourceSelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 资源文件选择配置项。
 * <p>
 * 左侧为文本输入框展示资源路径，右侧为 Browse 按钮，点击后打开 {@link ResourceSelectionScreen} 进行文件浏览选择。
 * <p>
 * JSON 配置示例：
 * <pre>
 * {
 *   "type": "resource",
 *   "title": "ui.fangsu.common.selectFilePath",
 *   "namespace": "fangsu",
 *   "suffixes": [".json"],
 *   "maxSelect": 1
 * }
 * </pre>
 */
public class ResourceConfig extends ConfigEntry<String> {

    private final String namespace;
    private final List<String> suffixes;
    private final int maxSelect;

    private EditBox editBox;

    public ResourceConfig(
            Component title,
            ConfigSpec spec,
            Supplier<String> getter,
            Consumer<String> setter,
            List<String> suffixes
    ) {
        super(title, spec, getter, setter);
        this.namespace = spec.getString("namespace", null);
        this.suffixes = suffixes != null ? suffixes : Collections.emptyList();
        this.maxSelect = spec.getInt("maxSelect", 1);
    }

    @Override
    public ConfigWidget createWidget(int x, int y, int labelW, int fieldW) {
        int btnW = Math.min(60, Math.max(40, fieldW / 4));
        int fieldAreaW = fieldW - btnW - 4;
        int height = 20;

        // ---------- EditBox ----------

        editBox = new EditBox(
                Minecraft.getInstance().font,
                x + labelW,
                y,
                fieldAreaW,
                height,
                title
        );
        editBox.setMaxLength(Integer.MAX_VALUE);
        editBox.setValue(value != null ? value : "");
        editBox.setResponder(text -> {
            value = text;
            notifyValueChanged();
        });

        // ---------- Browse Button ----------

        //#if MC_VERSION >= 11903
        Button button = Button.builder(
                ComponentHelper.translatable("ui.fangsu.common.browse"),
                btn -> openSelectionScreen()
        ).bounds(x + labelW + fieldAreaW + 4, y, btnW, height).build();
        //#else
        //$$ Button button = new Button(x + labelW + fieldAreaW + 4, y, btnW, height, ComponentHelper.translatable("ui.fangsu.common.browse"), btn -> openSelectionScreen());
        //#endif

        return new ConfigWidget(
                x, y,
                labelW + fieldW,
                height,
                labelW,
                title,
                editBox, button
        );
    }

    /** 打开资源选择 UI */
    private void openSelectionScreen() {
        final Screen currentScreen = Minecraft.getInstance().screen;
        if (currentScreen == null) return;

        Minecraft.getInstance().setScreen(new ResourceSelectionScreen(
                ComponentHelper.translatable("ui.fangsu.common.selectFile"),
                namespace,
                suffixes,
                result -> {
                    if (result != null && !result.isEmpty()) {
                        final String path = result.get(0).toString();
                        value = path;
                        if (editBox != null) {
                            editBox.setValue(path);
                        }
                        setter.accept(path);
                        notifyValueChanged();
                    }
                },
                maxSelect,
                currentScreen
        ));
    }

    @Override
    public void save(Object be) {
        if (editBox != null) {
            value = editBox.getValue();
        }
        super.save(be);
    }
}
