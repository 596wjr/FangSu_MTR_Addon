package com.fangsu.scripting;

import com.fangsu.Main;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class ModelHelper {
    public static float[] calculateNormal(List<Double> p1, List<Double> p2, List<Double> p3) {
        float[] v1 = {(float) (p2.get(0) - p1.get(0)), (float) (p2.get(1) - p1.get(1)), (float) (p2.get(2) - p1.get(2))};
        float[] v2 = {(float) (p3.get(0) - p1.get(0)), (float) (p3.get(1) - p1.get(1)), (float) (p3.get(2) - p1.get(2))};

        return calcNormal(v1, v2);
    }

    public static float[] calculateNormal(double[] p1, double[] p2, double[] p3) {
        if (p1 == null || p2 == null || p3 == null
                || p1.length < 3 || p2.length < 3 || p3.length < 3) {
            return new float[]{0, 0, 0};
        }

        float[] v1 = {
                (float) (p2[0] - p1[0]),
                (float) (p2[1] - p1[1]),
                (float) (p2[2] - p1[2])
        };
        float[] v2 = {
                (float) (p3[0] - p1[0]),
                (float) (p3[1] - p1[1]),
                (float) (p3[2] - p1[2])
        };

        return calcNormal(v1, v2);
    }

    private static float[] calcNormal(float[] v1, float[] v2) {
        float[] normal = {
                v1[1] * v2[2] - v1[2] * v2[1],
                v1[2] * v2[0] - v1[0] * v2[2],
                v1[0] * v2[1] - v1[1] * v2[0]
        };

        float length = (float) Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
        if (length == 0) return new float[]{0, 0, 0};

        normal[0] /= length;
        normal[1] /= length;
        normal[2] /= length;
        return normal;
    }

    public static void addQuad(RawMeshBuilder builder,
                               List<List<Double>> quad,
                               boolean reverse) {

        // ===== 强契约校验 =====
        if (quad == null || quad.size() != 4) {
            return;
        }
        for (List<Double> v : quad) {
            if (v == null || v.size() != 3) {
                return;
            }
        }

        // ===== 法线 =====
        float[] normal = ModelHelper.calculateNormal(
                quad.get(0),
                quad.get(1),
                quad.get(2)
        );

        if (reverse) {
            normal[0] = -normal[0];
            normal[1] = -normal[1];
            normal[2] = -normal[2];
        }

        // ===== 固定 UV（与顶点顺序绑定）=====
        builder.vertex(quad.get(0).get(0), quad.get(0).get(1), quad.get(0).get(2))
                .normal(normal[0], normal[1], normal[2]).uv(0f, 0f).endVertex();

        builder.vertex(quad.get(1).get(0), quad.get(1).get(1), quad.get(1).get(2))
                .normal(normal[0], normal[1], normal[2]).uv(0f, 1f).endVertex();

        builder.vertex(quad.get(2).get(0), quad.get(2).get(1), quad.get(2).get(2))
                .normal(normal[0], normal[1], normal[2]).uv(1f, 1f).endVertex();

        builder.vertex(quad.get(3).get(0), quad.get(3).get(1), quad.get(3).get(2))
                .normal(normal[0], normal[1], normal[2]).uv(1f, 0f).endVertex();
    }

    public static void addQuad(RawMeshBuilder builder, double[][] quad, boolean reverse) {
        // 契约校验
        if (quad == null || (quad.length < 4)) {
            Main.LOGGER.error("Failed to add quad: {}", quad != null ? "quad.length < 4" + quad.length + Arrays.deepToString(quad) : "quad == null");
            return;
        }
        for (int i = 0; i < 4; i++) {
            double[] v = quad[i];
            if (v == null || v.length < 3) {
                Main.LOGGER.error("Failed to add quad: vertex is null or length < 3");
                return;
            }
        }
        boolean hasUV = (quad.length == 5);
        double[] uvArr = null;
        if (hasUV) {
            uvArr = quad[4];
            if (uvArr == null || uvArr.length < 4) {
                Main.LOGGER.error("Failed to add quad: uvArr is {}", uvArr == null ? "null" : "length < 4");
                return;
            }
        }

        // 计算法线
        float[] normal = calculateNormal(quad[0], quad[1], quad[2]);

        if (reverse) {
            normal[0] = -normal[0];
            normal[1] = -normal[1];
            normal[2] = -normal[2];
        }

        // 确定UV
        float u1, v1, u2, v2;
        if (hasUV) {
            u1 = (float) uvArr[0];
            v1 = (float) uvArr[1];
            u2 = (float) uvArr[2];
            v2 = (float) uvArr[3];
        } else {
            u1 = 0f;
            v1 = 0f;
            u2 = 1f;
            v2 = 1f;
        }

        builder.vertex(quad[0][0], quad[0][1], quad[0][2])
                .normal(normal[0], normal[1], normal[2]).uv(u1, v1).endVertex();

        builder.vertex(quad[1][0], quad[1][1], quad[1][2])
                .normal(normal[0], normal[1], normal[2]).uv(u1, v2).endVertex();

        builder.vertex(quad[2][0], quad[2][1], quad[2][2])
                .normal(normal[0], normal[1], normal[2]).uv(u2, v2).endVertex();

        builder.vertex(quad[3][0], quad[3][1], quad[3][2])
                .normal(normal[0], normal[1], normal[2]).uv(u2, v1).endVertex();
    }

    public static void addQuad(RawMeshBuilder builder, float[][] quad, boolean reverse) {
        double[][] quadDouble = Arrays.stream(quad)
                .map(row -> IntStream.range(0, row.length)
                        .mapToDouble(i -> row[i])
                        .toArray())
                .toArray(double[][]::new);
        addQuad(builder, quadDouble, reverse);
    }

    public static RawModel buildSpiltModel(
            @NotNull RawModel leftTop, @NotNull RawModel top, @NotNull RawModel rightTop,
            @NotNull RawModel left, @NotNull RawModel center, @NotNull RawModel right,
            @NotNull RawModel leftBottom, @NotNull RawModel bottom, @NotNull RawModel rightBottom,
            int w, int h, double widthStep, double heightStep
    ) {
        RawModel spiltModel = new RawModel();

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                RawModel thisModel;
                if (x == 0) {
                    if (y == 0) {
                        thisModel = leftBottom;
                    } else if (y == h - 1) {
                        thisModel = leftTop;
                    } else {
                        thisModel = left;
                    }
                } else if (x == w - 1) {
                    if (y == 0) {
                        thisModel = rightBottom;
                    } else if (y == h - 1) {
                        thisModel = rightTop;
                    } else {
                        thisModel = right;
                    }
                } else {
                    if (y == 0) {
                        thisModel = bottom;
                    } else if (y == h - 1) {
                        thisModel = top;
                    } else {
                        thisModel = center;
                    }
                }
                RawModel copy = thisModel.copy();
                copy.applyTranslation((float) (x * widthStep), (float) (y * heightStep), 0);
                spiltModel.append(copy);

            }
        }
        spiltModel.applyTranslation((float) (w * widthStep / -2f + widthStep / 2f), 0, 0);

        return spiltModel;
    }
}
