package com.fangsu.render.sowcerext.model.integration;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.util.LinkedHashMap;
import java.util.Map;

public class BufferSourceProxy {

    private final MultiBufferSource bufferSource;
    private final Map<FaceListKey, FaceList> builders = new LinkedHashMap<>();

    public BufferSourceProxy(MultiBufferSource bufferSource) {
        this.bufferSource = bufferSource;
    }

    public FaceList getBuffer(RenderType renderType, boolean needSorting) {
        FaceListKey key = new FaceListKey(renderType, needSorting);
        return builders.computeIfAbsent(key, ignored -> new FaceList(renderType, needSorting));
    }

    public void commit() {
        for (FaceList builder : builders.values()) {
            builder.commit(bufferSource);
        }
        builders.clear();
    }

    private record FaceListKey(RenderType renderType, boolean needSorting) {
    }
}