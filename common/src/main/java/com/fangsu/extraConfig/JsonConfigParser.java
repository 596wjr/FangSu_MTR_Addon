package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 从 JsonObject 解析配置项的工厂工具类。
 * <p>
 * 示例 JSON：
 * <pre>{@code
 * {
 *   "type": "number_input",
 *   "title": "ui.fangsu.common.length",
 *   "min": 2,
 *   "max": 16,
 *   "isInt": true,
 *   "default": 4
 * }
 * }</pre>
 * <p>
 * 支持的 type 与 {@link ConfigTypes} 一致：bool, number, number_input, string, list, resource
 */
public final class JsonConfigParser {

    private JsonConfigParser() {
    }

    /**
     * 从 JSON 解析一个配置项。
     *
     * @param json      JSON 对象，必须包含 "type" 字段
     * @param getter    值读取器
     * @param setter    值写入器
     * @param <T>       配置值类型
     * @return 配置项实例
     */
    @SuppressWarnings("unchecked")
    public static <T> ConfigEntry<T> parse(
            @NotNull JsonObject json,
            @NotNull Supplier<T> getter,
            @NotNull Consumer<T> setter
    ) {
        String type = json.get("type").getAsString();
        // 优先使用 "title"，兼容北京包使用 "text" 字段
        String titleKey;
        if (json.has("title")) {
            titleKey = json.get("title").getAsString();
        } else if (json.has("text")) {
            titleKey = json.get("text").getAsString();
        } else {
            titleKey = type + "@" + Integer.toHexString(json.hashCode());
        }
        Component title = ComponentHelper.translatable(titleKey);

        ConfigSpec spec = ConfigSpec.fromJson(json);

        return ConfigTypes.create(title, spec, getter, setter);
    }

    /**
     * 从 JSON 解析一个配置项（带默认值的简化版本）。
     *
     * @param json        JSON 对象
     * @param defaultValue 默认值（用于推断类型和构造 getter/setter）
     * @param onChanged    值变更回调
     * @param <T>          配置值类型
     * @return 配置项实例
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> ConfigEntry<T> parseWithDefault(
            @NotNull JsonObject json,
            @NotNull T defaultValue,
            @NotNull Consumer<T> onChanged
    ) {
        Supplier<T> getter = () -> defaultValue;
        Consumer<T> setter = v -> {
            if (onChanged != null) {
                onChanged.accept(v);
            }
        };

        // 尝试从 JSON 的 "default" 字段读取默认值（如果存在）
        if (json.has("default")) {
            T jsonDefault = readDefault(json, (Class<T>) defaultValue.getClass());
            if (jsonDefault != null) {
                getter = () -> jsonDefault;
            }
        }

        return parse(json, getter, setter);
    }

    /* ============ 辅助方法 ============ */

    /**
     * 从 JSON 的 "default" 字段读取对应类型的默认值。
     */
    @SuppressWarnings("unchecked")
    private static <T> T readDefault(JsonObject json, Class<T> clazz) {
        JsonElement defEl = json.get("default");
        if (defEl == null) return null;

        if (clazz == Float.class || clazz == float.class) {
            return (T) Float.valueOf(defEl.getAsFloat());
        } else if (clazz == Integer.class || clazz == int.class) {
            return (T) Integer.valueOf(defEl.getAsInt());
        } else if (clazz == Boolean.class || clazz == boolean.class) {
            return (T) Boolean.valueOf(defEl.getAsBoolean());
        } else if (clazz == String.class) {
            return (T) defEl.getAsString();
        } else if (clazz == Double.class || clazz == double.class) {
            return (T) Double.valueOf(defEl.getAsDouble());
        } else if (clazz == Long.class || clazz == long.class) {
            return (T) Long.valueOf(defEl.getAsLong());
        }
        return null;
    }
}
