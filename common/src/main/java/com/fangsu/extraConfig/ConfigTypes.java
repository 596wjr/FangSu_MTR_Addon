package com.fangsu.extraConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ConfigTypes {

    private static final Map<String, Factory<?>> REGISTRY = new HashMap<>();

    /* ================= 注册 ================= */

    static {
        register("bool", ConfigTypes::boolConfig);
        register("number", ConfigTypes::numberConfig);
        register("number_input", ConfigTypes::numberInputConfig);
        register("string", ConfigTypes::stringConfig);
        register("list", ConfigTypes::listConfig);
    }

    private static <T> void register(String type, Factory<T> factory) {
        REGISTRY.put(type, factory);
    }

    /* ================= 对外入口 ================= */

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

    /* ================= 各类型工厂 ================= */

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
         * list 的 values 通常来自 JSON：
         * {
         *   type: "list",
         *   values: ["a","b","c"]
         * }
         */
        var arr = spec.params.get("values").getAsJsonArray();
        List<MutableComponent> values = arr.asList().stream()
                .map(e -> Component.translatable(e.getAsString()))
                .toList();

        return new EnumConfig(title, spec, values, getter, setter);
    }

    /* ================= 内部接口 ================= */

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
