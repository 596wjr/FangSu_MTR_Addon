package com.fangsu.mappings;

public class ResourceLocation {
    private final net.minecraft.resources.ResourceLocation raw;

    public ResourceLocation(net.minecraft.resources.ResourceLocation raw) {
        this.raw = raw;
    }

    public ResourceLocation(String path) {
        this.raw = new net.minecraft.resources.ResourceLocation(path);
    }

    public ResourceLocation(String str1, String str2) {
        this.raw = new net.minecraft.resources.ResourceLocation(str1, str2);
    }

    public net.minecraft.resources.ResourceLocation getRaw() {
        return raw;
    }

    public String getPath() {
        return raw.getPath();
    }

    public String getNamespace() {
        return raw.getNamespace();
    }

    public String toString() {
        return raw.toString();
    }

    public int hashCode() {
        return raw.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof ResourceLocation) {
            return raw.equals(((ResourceLocation) obj).raw);
        }
        return raw.equals(obj);
    }
}
