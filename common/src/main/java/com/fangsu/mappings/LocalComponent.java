package com.fangsu.mappings;

import net.minecraft.network.chat.MutableComponent;

public class LocalComponent {
    private final MutableComponent raw;

    public LocalComponent(MutableComponent raw) {
        this.raw = raw;
    }

    public static LocalComponent translatable(String str) {
        return new LocalComponent(ComponentHelper.translatable(str));
    }

    public static LocalComponent translatable(String key, Object... args) {
        return new LocalComponent(ComponentHelper.translatable(key, args));
    }

    public static LocalComponent literal(String str) {
        return new LocalComponent(ComponentHelper.literal(str));
    }

    public static LocalComponent empty() {
        return new LocalComponent(ComponentHelper.empty());
    }

    public MutableComponent getRaw() {
        return raw;
    }
}
