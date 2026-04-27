package com.fangsu.train.lcds;

import com.fangsu.train.LcdBase;
import com.fangsu.train.TrainStatus;

import java.awt.*;

public class MtrLcd extends LcdBase {

    @Override
    public void draw(Graphics2D g, TrainStatus status, int side, int x, int y, int w, int h, Runnable callback) {
        //TODO 测试代码!!!
        g.setColor(Color.BLUE);
        g.fillRect(x, y, w, h);

//        callback.run();
    }
}
