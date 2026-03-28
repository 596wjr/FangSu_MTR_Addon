package com.fangsu.drawing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class DrawingManager {
    private static DrawingManager instance = new DrawingManager();
    private Map<String, Map<String, BaseDrawing>> drawings;

    private DrawingManager() {
        drawings = new HashMap<>();
    }

    @Nullable
    public BaseDrawing getDrawing(@NotNull String type, @NotNull String name) {
        if (drawings.containsKey(type)) {
            return drawings.get(type).get(name);
        }
        return null;
    }

    public DrawingManager getInstance() {
        return instance;
    }
}
