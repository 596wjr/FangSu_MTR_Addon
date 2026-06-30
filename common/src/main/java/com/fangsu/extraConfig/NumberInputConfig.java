package com.fangsu.extraConfig;

import com.fangsu.mappings.LocalComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 用单行输入框输入数字的配置项（支持 float / int，保存只在 save() 时写回 BE）
 * 新增 isHex 开关：当 isInt 且 isHex 为 true 时，使用十六进制解析与格式化
 */
public class NumberInputConfig extends ConfigEntry<Float> {

    private final float min;
    private final float max;
    private final boolean isInt;
    private final boolean isHex;   // 是否使用十六进制（仅当 isInt = true 时有效）

    public NumberInputConfig(
            Component title,
            ConfigSpec spec,
            Supplier<Float> getter,
            Consumer<Float> setter
    ) {
        super(title, spec, getter, setter);
        this.min = spec.getFloat("min", Float.NEGATIVE_INFINITY);
        this.max = spec.getFloat("max", Float.POSITIVE_INFINITY);
        this.isInt = spec.getBool("isInt", false);
        this.isHex = spec.getBool("isHex", false);   // 从 spec 读取开关，默认为 false
    }

    public NumberInputConfig(
            LocalComponent title,
            ConfigSpec spec,
            Supplier<Float> getter,
            Consumer<Float> setter
    ) {
        this(title.getRaw(), spec, getter, setter);
    }

    @Override
    public ConfigWidget createWidget(int x, int y, int labelW, int fieldW) {
        int height = 20;
        int fieldX = x + labelW;

        EditBox inputBox = new EditBox(
                Minecraft.getInstance().font,
                fieldX,
                y,
                fieldW,
                height,
                title
        );

        // 根据当前 value 设置初始显示文本（支持十六进制格式）
        inputBox.setValue(formatValue(value));

        inputBox.setResponder(text -> {
            if (text == null || text.isEmpty()) {
                return;
            }
            try {
                if (isInt) {
                    int iv;
                    if (isHex) {
                        // 去除十六进制常见前缀（0x、0X、#）
                        String trimmed = text.trim();
                        if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
                            trimmed = trimmed.substring(2);
                        } else if (trimmed.startsWith("#")) {
                            trimmed = trimmed.substring(1);
                        }
                        iv = Integer.parseInt(trimmed, 16);   // 十六进制解析
                    } else {
                        iv = Integer.parseInt(text.trim());    // 十进制解析
                    }
                    float fv = clamp(iv);
                    value = (float) Math.round(fv);
                    notifyValueChanged();
                } else {
                    // 浮点数模式忽略 isHex
                    float fv = Float.parseFloat(text.trim());
                    value = clamp(fv);
                    notifyValueChanged();
                }
            } catch (NumberFormatException ignored) {
                // 非法输入不更新 value
            }
        });

        return new ConfigWidget(x, y, labelW + fieldW, height, labelW, title, inputBox);
    }

    @Override
    public void load(Object be) {
        try {
            Float v = getter.get();
            if (v == null) {
                float def = spec.getFloat("default", Float.NaN);
                if (!Float.isNaN(def)) {
                    value = def;
                }
            } else {
                value = clamp(v);
            }
        } catch (Throwable t) {
            float def = spec.getFloat("default", Float.NaN);
            if (!Float.isNaN(def)) value = def;
        }
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

    /**
     * 根据 isInt / isHex 格式化当前值显示在输入框中
     */
    private String formatValue(Float v) {
        if (v == null) return "";
        if (isInt) {
            int intVal = Math.round(v);
            if (isHex) {
                // 十六进制格式：0x + 大写十六进制（负数会显示完整的无符号表示）
                return "0x" + Integer.toHexString(intVal).toUpperCase();
            } else {
                return String.valueOf(intVal);
            }
        } else {
            return String.valueOf(v);
        }
    }
}