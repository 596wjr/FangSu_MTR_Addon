package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 图形化程序配置项：值为 JSON 字符串（{@code ModularDocument} 序列化）。
 * <p>
 * 在配置界面中渲染为一行：标题 + “编辑”按钮。点“编辑”打开
 * {@link com.fangsu.ui.ModularEditingScreen}。屏幕保存时写回本配置项的 getter/setter。
 * <p>
 * 积木白名单由调用方经构造参数（{@code allowedTypes}）传入，以便编辑器只显示一组特定积木
 * （如交通灯程序固定白名单）；null/空集合 = 显示全部。本类不内置任何业务白名单。
 */
public class ProgramConfig extends ConfigEntry<String> {

    private final String editButtonKey;

    /** 编辑器积木白名单（钩子，由调用方传入）；null = 展示全部。 */
    private final Set<String> allowedTypes;

    private boolean opened;

    public ProgramConfig(
            Component title,
            ConfigSpec spec,
            Supplier<String> getter,
            Consumer<String> setter
    ) {
        this(title, spec, getter, setter, null);
    }

    public ProgramConfig(
            Component title,
            ConfigSpec spec,
            Supplier<String> getter,
            Consumer<String> setter,
            Set<String> allowedTypes
    ) {
        super(title, spec, getter, setter);
        this.editButtonKey = "modular.fangsu.edit_program";
        this.allowedTypes = allowedTypes;
        // 编辑器保存时立即写回并同步到服务端（通过 changeListener → save(be) + sendUpdateC2S）
        this.setSaveOnChange(true);
    }

    public static ProgramConfig fromLocal(
            com.fangsu.mappings.LocalComponent title,
            ConfigSpec spec,
            Supplier<String> getter,
            Consumer<String> setter
    ) {
        return new ProgramConfig(title.getRaw(), spec, getter, setter);
    }

    public static ProgramConfig fromLocal(
            com.fangsu.mappings.LocalComponent title,
            ConfigSpec spec,
            Supplier<String> getter,
            Consumer<String> setter,
            Set<String> allowedTypes
    ) {
        return new ProgramConfig(title.getRaw(), spec, getter, setter, allowedTypes);
    }

    @Override
    public ConfigWidget createWidget(int x, int y, int labelWidth, int fieldWidth) {
        int height = 20;
        int totalWidth = labelWidth + fieldWidth;

        //#if MC_VERSION >= 11903
        Button edit = Button.builder(ComponentHelper.translatable(editButtonKey), btn -> openEditor())
                .bounds(x + labelWidth, y, fieldWidth, height).build();
        //#else
        //$$ Button edit = new Button(x + labelWidth, y, fieldWidth, height, ComponentHelper.translatable(editButtonKey), btn -> openEditor());
        //#endif

        return new ConfigWidget(
                x, y,
                totalWidth,
                height,
                labelWidth,
                title,
                edit
        );
    }

    /** 打开程序编辑器；保存时经 setter 写回。白名单用构造传入的钩子。 */
    private void openEditor() {
        if (opened) return;
        opened = true;
        Minecraft.getInstance().setScreen(new com.fangsu.ui.ModularEditingScreen(
                () -> value,
                v -> {
                    value = v;
                    notifyValueChanged();
                },
                allowedTypes
        ));
    }

    @Override
    public void save(Object be) {
        super.save(be);
    }
}
