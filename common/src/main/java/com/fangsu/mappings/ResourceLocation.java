package com.fangsu.mappings;

public class ResourceLocation extends SimpleMappings<net.minecraft.resources.ResourceLocation> {
    private ResourceLocation(net.minecraft.resources.ResourceLocation raw) {
        super(raw);
    }

    public ResourceLocation(String path) {
        super(new net.minecraft.resources.ResourceLocation(path));
    }

    public ResourceLocation(String str1, String str2) {
        super(new net.minecraft.resources.ResourceLocation(str1, str2));
    }

    public static ResourceLocation fromRaw(net.minecraft.resources.ResourceLocation raw) {
        return new ResourceLocation(raw);
    }

    public String getPath() {
        return raw.getPath();
    }

    public String getNamespace() {
        return raw.getNamespace();
    }
}
