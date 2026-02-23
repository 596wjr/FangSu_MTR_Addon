package com.fangsu.render.model;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class RawModel {
    private final List<Object> meshes = new ArrayList<>();
    private ResourceLocation texture;

    public void append(Object mesh) { meshes.add(mesh); }
    public void applyUVMirror(boolean mirrorU, boolean mirrorV) { }
    public void triangulate() { }
    public void generateNormals() { }
    public void replaceAllTexture(ResourceLocation texture) { this.texture = texture; }

    public List<Object> meshes() { return meshes; }
    public ResourceLocation texture() { return texture; }
}
