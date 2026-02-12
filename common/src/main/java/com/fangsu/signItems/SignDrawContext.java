package com.fangsu.signItems;

import java.awt.*;
import java.util.Map;

public record SignDrawContext(
        Graphics2D graphics,
        float x,
        float y,
        float unit,
        int align          // 0 左 1 中 2 右
) {
}
