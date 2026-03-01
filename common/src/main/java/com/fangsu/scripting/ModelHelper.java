package com.fangsu.scripting;

import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;

import java.util.List;

public class ModelHelper {
    public static float[] calculateNormal(List<Double> p1, List<Double> p2, List<Double> p3) {
        float[] v1 = {(float) (p2.get(0) - p1.get(0)), (float) (p2.get(1) - p1.get(1)), (float) (p2.get(2) - p1.get(2))};
        float[] v2 = {(float) (p3.get(0) - p1.get(0)), (float) (p3.get(1) - p1.get(1)), (float) (p3.get(2) - p1.get(2))};

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
}
