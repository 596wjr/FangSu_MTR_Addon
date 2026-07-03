package com.fangsu.extraConfig;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mappings.GsonHelper;
import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ConfigTypes {

    private static final Map<String, Factory<?>> REGISTRY = new HashMap<>();

    /* ================= 娉ㄥ唽 ================= */

    static {
        register("bool", ConfigTypes::boolConfig);
        register("number", ConfigTypes::numberConfig);
        register("number_input", ConfigTypes::numberInputConfig);
        register("string", ConfigTypes::stringConfig);
        register("str", ConfigTypes::stringConfig);   // 鍖椾含鍖呬娇锟?"str" 鑰岄潪 "string"
        register("list", ConfigTypes::listConfig);        register("resource", ConfigTypes::resourceConfig);    }

    private static <T> void register(String type, Factory<T> factory) {
        REGISTRY.put(type, factory);
    }

    /* ================= 瀵瑰鍏ュ彛 ================= */

    @SuppressWarnings("unchecked")
    public static <T> ConfigEntry<T> create(
            Component title,
            ConfigSpec spec,
            Supplier<T> getter,
            Consumer<T> setter
    ) {
        Factory<T> factory = (Factory<T>) REGISTRY.get(spec.type);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown config type: " + spec.type);
        }
        return factory.create(title, spec, getter, setter);
    }

    /* ================= 鍚勭被鍨嬪伐锟?================= */

    private static ConfigEntry<Boolean> boolConfig(
            Component title,
            ConfigSpec spec,
            Supplier<Boolean> getter,
            Consumer<Boolean> setter
    ) {
        return new BoolConfig(title, spec, getter, setter);
    }

    private static ConfigEntry<Float> numberConfig(
            Component title,
            ConfigSpec spec,
            Supplier<Float> getter,
            Consumer<Float> setter
    ) {
        return new NumberConfig(title, spec, getter, setter);
    }

    private static ConfigEntry<Float> numberInputConfig(
            Component title,
            ConfigSpec spec,
            Supplier<Float> getter,
            Consumer<Float> setter
    ) {
        return new NumberInputConfig(title, spec, getter, setter);
    }

    private static ConfigEntry<String> stringConfig(
            Component title,
            ConfigSpec spec,
            Supplier<String> getter,
            Consumer<String> setter
    ) {
        return new StringConfig(title, spec, getter, setter);
    }

    private static ConfigEntry<Integer> listConfig(
            Component title,
            ConfigSpec spec,
            Supplier<Integer> getter,
            Consumer<Integer> setter
    ) {
        /*
         * list 锟?values 閫氬父鏉ヨ嚜 JSON锟?
         * 鏍煎紡1锛堟爣鍑嗭級锟?
         * {
         *   type: "list",
         *   values: ["a","b","c"]
         * }
         * 鏍煎紡2锛堝寳浜寘锛夛細
         * {
         *   type: "list",
         *   param: {
         *     listItems: [
         *       { "text": "cfg.img.png", "val": "png" },
         *       ...
         *     ]
         *   }
         * }
         */
        List<MutableComponent> values;

        // 浼樺厛璇诲彇椤跺眰 "values"
        JsonElement valuesEl = spec.params.get("values");
        if (valuesEl != null && valuesEl.isJsonArray()) {
            values = GsonHelper.asList(valuesEl.getAsJsonArray()).stream()
                    .map(e -> ComponentHelper.translatable(e.getAsString()))
                    .toList();
        } else {
            // 鍥為€€锟?param.listItems锛堝寳浜寘鏍煎紡锟?
            JsonElement paramEl = spec.params.get("param");
            if (paramEl != null && paramEl.isJsonObject()) {
                JsonElement listItemsEl = paramEl.getAsJsonObject().get("listItems");
                if (listItemsEl != null && listItemsEl.isJsonArray()) {
                    values = GsonHelper.asList(listItemsEl.getAsJsonArray()).stream()
                            .map(e -> {
                                if (e.isJsonObject() && e.getAsJsonObject().has("text")) {
                                    return ComponentHelper.translatable(e.getAsJsonObject().get("text").getAsString());
                                }
                                return ComponentHelper.translatable(e.getAsString());
                            })
                            .toList();
                } else {
                    values = List.of();
                }
            } else {
                values = List.of();
            }
        }

        return new EnumConfig(title, spec, values, getter, setter);
    }
    private static ConfigEntry<String> resourceConfig(
            Component title,
            ConfigSpec spec,
            Supplier<String> getter,
            Consumer<String> setter
    ) {
        // 支持 "suffixes" 数组和单 "suffix" 字符串两种格式
        List<String> suffixes;
        JsonElement suffixesEl = spec.params.get("suffixes");
        if (suffixesEl != null && suffixesEl.isJsonArray()) {
            List<String> list = new ArrayList<>();
            for (JsonElement e : suffixesEl.getAsJsonArray()) {
                list.add(e.getAsString());
            }
            suffixes = list;
        } else {
            String single = spec.getString("suffix", null);
            suffixes = single != null ? List.of(single) : Collections.emptyList();
        }
        return new ResourceConfig(title, spec, getter, setter, suffixes);
    }
    /* ================= 鍐呴儴鎺ュ彛 ================= */

    @FunctionalInterface
    private interface Factory<T> {
        ConfigEntry<T> create(
                Component title,
                ConfigSpec spec,
                Supplier<T> getter,
                Consumer<T> setter
        );
    }

    private ConfigTypes() {
    }
}
