package com.fangsu.train;

import java.awt.*;

public abstract class LcdBase {
    public abstract void draw(Graphics2D g, TrainStatus status, int side, int x, int y, int w, int h, Runnable completeCallback);
}
