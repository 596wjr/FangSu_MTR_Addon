package com.fangsu.extraConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 用单行输入框输入数字的配置项（支持 float / int，保存只在 save() 时写回 BE）
 */
public class NumberInputConfig extends ConfigEntry<Float> {

    private final float min;
    private final float max;
    private final boolean isInt;

    // 在 createWidget 中创建并保存引用，以便 save() 时读取最新文本
    private EditBox inputBox;

    public NumberInputConfig(
            Component title,
            ConfigSpec spec,
            Function<Object, Float> getter,
            BiConsumer<Object, Float> setter
    ) {
        super(title, spec, getter, setter);
        this.min = spec.getFloat("min", Float.NEGATIVE_INFINITY);
        this.max = spec.getFloat("max", Float.POSITIVE_INFINITY);
        this.isInt = spec.getBool("isInt", false);
    }

    @Override
    public ConfigWidget createWidget(int x, int y, int labelW, int fieldW) {
        int height = 20;
        int fieldX = x + labelW;

        // 创建 EditBox，初始值使用当前 value（load() 后 value 已经被设置）
        inputBox = new EditBox(
                Minecraft.getInstance().font,
                fieldX,
                y,
                fieldW,
                height,
                title
        );

        // 显示格式：整数显示无小数，浮点用默认格式
        inputBox.setValue(formatValue(value));

        // 编辑时立即更新 entry 的 value（但不写回 BE）
        inputBox.setResponder(text -> {
            if (text == null || text.isEmpty()) {
                // 保持当前 value（空输入不立即覆盖）
                return;
            }
            try {
                if (isInt) {
                    int iv = Integer.parseInt(text.trim());
                    float fv = clamp(iv);
                    value = (float) Math.round(fv); // 保证整数语义
                } else {
                    float fv = Float.parseFloat(text.trim());
                    value = clamp(fv);
                }
            } catch (NumberFormatException ignored) {
                // 不合法输入时，不改变 value（等用户修正）
            }
        });

        // 初始可见 / 可用性由 ConfigEntry.isVisible() 决定
        return new ConfigWidget(x, y, labelW + fieldW, height, title, inputBox);
    }

    @Override
    public void load(Object be) {
        // 从 BE 安全地读取（getter 可能返回 null）
        try {
            Float v = getter.apply(be);
            if (v == null) {
                // 使用 spec 中的 default（如果有），否则保持原来的 value
                float def = spec.getFloat("default", Float.NaN);
                if (!Float.isNaN(def)) {
                    value = def;
                }
            } else {
                value = clamp(v);
            }
        } catch (Throwable t) {
            // 出错时尽量使用 spec 的 default，或保留现有 value
            float def = spec.getFloat("default", Float.NaN);
            if (!Float.isNaN(def)) value = def;
        }
    }

    @Override
    public void save(Object be) {
        // 当 UI 关闭时由外层统一调用此方法 → 把 inputBox 的文本解析并写回 BE
        if (inputBox != null) {
            String txt = inputBox.getValue();
            if (txt != null && !txt.isEmpty()) {
                try {
                    if (isInt) {
                        int iv = Integer.parseInt(txt.trim());
                        value = clamp(iv);
                    } else {
                        float fv = Float.parseFloat(txt.trim());
                        value = clamp(fv);
                    }
                } catch (NumberFormatException ignored) {
                    // 解析失败则不改变 value（或可选择设置为 default）
                }
            }
        }
        // 最终写回 BE（由你传入的 setter 处理具体写法）
        setter.accept(be, value);
    }

    /* ============ 辅助 ============ */

    private float clamp(float v) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private float clamp(int iv) {
        float v = (float) iv;
        return clamp(v);
    }

    private String formatValue(Float v) {
        if (v == null) return "";
        if (isInt) {
            return String.valueOf(Math.round(v));
        } else {
            return String.valueOf(v);
        }
    }
}
