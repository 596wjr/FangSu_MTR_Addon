package com.fangsu.drawing.sign;

import java.awt.*;

public record SignDrawContext(
        Graphics2D graphics,
        float x,
        float y,
        float unit,
        int align,         // 0 左 1 中 2 右
        boolean selected
) {
}
