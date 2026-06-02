package com.fangsu.shape;

import com.fangsu.render.sowcerext.model.RawModel;
import org.jetbrains.annotations.NotNull;

public class ShapeUtil {
    public static ShapeCollection buildSpiltShape(
            @NotNull ShapeCollection leftTop, @NotNull ShapeCollection top, @NotNull ShapeCollection rightTop,
            @NotNull ShapeCollection left, @NotNull ShapeCollection center, @NotNull ShapeCollection right,
            @NotNull ShapeCollection leftBottom, @NotNull ShapeCollection bottom, @NotNull ShapeCollection rightBottom,
            int w, int h, double widthStep, double heightStep
    ) {
        ShapeCollection shapeCollection = new ShapeCollection();

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                ShapeCollection thisShape;
                if (x == 0) {
                    if (y == 0) {
                        thisShape = leftBottom;
                    } else if (y == h - 1) {
                        thisShape = leftTop;
                    } else {
                        thisShape = left;
                    }
                } else if (x == w - 1) {
                    if (y == 0) {
                        thisShape = rightBottom;
                    } else if (y == h - 1) {
                        thisShape = rightTop;
                    } else {
                        thisShape = right;
                    }
                } else {
                    if (y == 0) {
                        thisShape = bottom;
                    } else if (y == h - 1) {
                        thisShape = top;
                    } else {
                        thisShape = center;
                    }
                }

                ShapeCollection copy = thisShape.copy();
                copy.moveAll((float) (x * widthStep), (float) (y * heightStep), 0);
                shapeCollection.addAll(copy);

            }
        }
        shapeCollection.moveAll((float) (w * widthStep / -2f + widthStep / 2f), 0, 0);

        return shapeCollection;
    }
}
