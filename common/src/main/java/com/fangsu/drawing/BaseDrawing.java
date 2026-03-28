package com.fangsu.drawing;

import java.awt.*;

public abstract class BaseDrawing {
    public BaseDrawing() {
    }

    public abstract void draw(Graphics2D g, int x, int y, int w, int h);
}
