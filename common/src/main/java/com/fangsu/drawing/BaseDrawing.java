package com.fangsu.drawing;

import java.awt.*;

public abstract class BaseDrawing {
    private boolean isAvailable = false;

    public BaseDrawing() {
    }

    public final void requestDraw(Graphics2D g, int x, int y, int w, int h) {
        if (isAvailable) {
            draw(g, x, y, w, h);
        } else
            throw new IllegalStateException("Drawing not initialized");
    }

    public abstract void draw(Graphics2D g, int x, int y, int w, int h);

    public final void init(Object... args) {
        isAvailable = true;
        initialize(args);
    }

    public abstract void initialize(Object... args);

    public final void close() {
        isAvailable = false;
    }
}
