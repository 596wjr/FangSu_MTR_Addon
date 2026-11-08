package com.fangsu.train;

import java.awt.*;
import java.util.Map;

public abstract class LcdBase {
    /** 无线路时仍需绘制的面板可覆盖此项，例如保留印刷底板并清除 LED。 */
    public boolean canDrawWithoutRoute() {
        return false;
    }

    public abstract void draw(Graphics2D g, TrainStatus status, LcdInfo info, Map<String, Object> state, String side, int x, int y, int w, int h, Runnable completeCallback);
}
