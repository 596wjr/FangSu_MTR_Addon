package com.fangsu.utils;

import com.fangsu.mappings.ResourceLocation;
import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * 提供以 {@link ResourceLocation}（com.fangsu.mappings 包）为参数的便捷方法，
 * 内部委托给 {@link ResourceUtil} 中对应的 {@link net.minecraft.resources.ResourceLocation} 版本。
 */
public final class LocalResourceUtil {

    private LocalResourceUtil() {
    }

    public static String[] loadStringAsArray(ResourceLocation location) throws IOException {
        return ResourceUtil.loadStringAsArray(location.getRaw());
    }

    public static byte[] loadResourceBytes(ResourceLocation location) throws IOException {
        return ResourceUtil.loadResourceBytes(location.getRaw());
    }

    public static String loadString(ResourceLocation location) throws IOException {
        return ResourceUtil.loadString(location.getRaw());
    }

    public static BufferedImage loadImage(ResourceLocation location) throws IOException {
        return ResourceUtil.loadImage(location.getRaw());
    }

    public static Font loadFont(ResourceLocation location) {
        return ResourceUtil.loadFont(location.getRaw());
    }

    public static @NotNull JsonElement simpleLoadAsJson(ResourceLocation location) {
        return ResourceUtil.simpleLoadAsJson(location.getRaw());
    }
}
