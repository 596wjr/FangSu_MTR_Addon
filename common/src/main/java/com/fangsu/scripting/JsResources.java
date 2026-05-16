package com.fangsu.scripting;

import com.fangsu.utils.ResourceUtil;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class JsResources {
    public static ResourceLocation id(String path) {
        return ResourceLocation.tryParse(path);
    }

    public static ResourceLocation id(String a, String b) {
        return ResourceLocation.tryParse(a + ":" + b);
    }

    public static Font getSystemFont(String name) {
        return new Font(name, Font.PLAIN, 12);
    }

    public static BufferedImage readBufferedImage(ResourceLocation path) throws IOException {
        return ResourceUtil.loadImage(path);
    }

    public static Font readFont(ResourceLocation path) {
        return ResourceUtil.loadFont(path);
    }

    public static String readString(ResourceLocation path) throws IOException {
        return ResourceUtil.loadString(path);
    }
}
