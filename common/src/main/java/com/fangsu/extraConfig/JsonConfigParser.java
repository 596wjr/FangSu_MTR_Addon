package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 锟?JsonObject 瑙ｆ瀽閰嶇疆椤圭殑宸ュ巶宸ュ叿绫伙拷?
 * <p>
 * 绀轰緥 JSON锟?
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
 * 鏀寔锟?type 锟?{@link ConfigTypes} 涓€鑷达細bool, number, number_input, string, list锟?
 */
public final class JsonConfigParser {

    private JsonConfigParser() {
    }

    /**
     * 锟?JSON 瑙ｆ瀽涓€涓厤缃」锟?
     *
     * @param json      JSON 瀵硅薄锛屽繀椤诲寘锟?"type" 瀛楁
     * @param getter    鍊艰鍙栧櫒
     * @param setter    鍊煎啓鍏ュ櫒
     * @param <T>       閰嶇疆鍊肩被锟?
     * @return 閰嶇疆椤瑰疄锟?
     */
    @SuppressWarnings("unchecked")
    public static <T> ConfigEntry<T> parse(
            @NotNull JsonObject json,
            @NotNull Supplier<T> getter,
            @NotNull Consumer<T> setter
    ) {
        String type = json.get("type").getAsString();
        // 浼樺厛浣跨敤 "title"锛屽吋瀹瑰寳浜寘锟?"text" 瀛楁
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
     * 锟?JSON 瑙ｆ瀽涓€涓厤缃」锛堝甫榛樿鍊肩殑绠€鍖栫増鏈級锟?
     *
     * @param json        JSON 瀵硅薄
     * @param defaultValue 榛樿鍊硷紙鐢ㄤ簬鎺ㄦ柇绫诲瀷鍜屾瀯锟?getter/setter锟?
     * @param onChanged    鍊煎彉鏇村洖锟?
     * @param <T>          閰嶇疆鍊肩被锟?
     * @return 閰嶇疆椤瑰疄锟?
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

        // 灏濊瘯锟?JSON 锟?"default" 瀛楁璇诲彇榛樿鍊硷紙濡傛灉瀛樺湪锟?
        if (json.has("default")) {
            T jsonDefault = readDefault(json, (Class<T>) defaultValue.getClass());
            if (jsonDefault != null) {
                getter = () -> jsonDefault;
            }
        }

        return parse(json, getter, setter);
    }

    /* ============ 杈呭姪鏂规硶 ============ */

    /**
     * 锟?JSON 锟?"default" 瀛楁璇诲彇瀵瑰簲绫诲瀷鐨勯粯璁ゅ€硷拷?
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
