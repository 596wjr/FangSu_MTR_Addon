package com.fangsu.shape;

import com.fangsu.render.sowcerext.model.RawModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

    /**
     * 将 content JSON 中的像素坐标碰撞盒转换为 {@link ShapeCollection}（世界单位 0~1）。
     * <p>
     * 支持两种写法：
     * <ul>
     *     <li>单个盒：{@code [x1, y1, z1, x2, y2, z2]}</li>
     *     <li>盒列表：{@code [[...], [...]]}</li>
     * </ul>
     * 无法识别或长度不足的条目会被跳过（例如玻璃的 {@code "shape": []}）。
     *
     * @param data 解析后的 JSON 数组（List 形式）
     * @return 形状集合；无有效盒时返回空集合
     */
    public static ShapeCollection fromPixelBoxes(@Nullable Object data) {
        ShapeCollection collection = new ShapeCollection();
        if (!(data instanceof List<?> outer) || outer.isEmpty()) return collection;

        if (outer.get(0) instanceof Number) {
            addPixelBox(collection, outer);
            return collection;
        }

        for (Object element : outer) {
            if (element instanceof List<?> box) {
                addPixelBox(collection, box);
            }
        }
        return collection;
    }

    private static void addPixelBox(ShapeCollection collection, List<?> box) {
        if (box.size() < 6) return;
        double[] values = new double[6];
        for (int i = 0; i < 6; i++) {
            if (!(box.get(i) instanceof Number number)) return;
            values[i] = number.doubleValue() / 16.0;
        }
        collection.add(new RawShape(values));
    }
}
