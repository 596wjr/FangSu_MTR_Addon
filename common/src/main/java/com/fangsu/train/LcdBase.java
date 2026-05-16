package com.fangsu.train;

import java.awt.*;
import java.util.Map;

public abstract class LcdBase {
    public abstract void draw(Graphics2D g, TrainStatus status, LcdInfo info, Map<String, Object> state, String side, int x, int y, int w, int h, Runnable completeCallback);
}
