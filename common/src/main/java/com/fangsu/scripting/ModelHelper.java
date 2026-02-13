package com.fangsu.scripting;

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
}
