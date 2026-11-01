package com.fangsu.shape;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.concurrent.CancellationException;

/** 有界旋转算法：先确定完整覆盖的细分预算，再生成子盒，绝不截断形状。 */
public final class ShapeRotation {
    public static final int MAX_SUB_BOXES = 512;
    // 将细分结果向外吸附到整体包围盒的 64 格坐标网格，限制 VoxelShape 布尔运算的坐标数量。
    private static final int GRID = 64;
    private record LocalBox(Vec3 min, Vec3 max) { }
    private ShapeRotation() { }

    public static VoxelShape coarse(List<AABB> boxes, Vec3 origin, float rx, float ry, float rz) {
        if (!validTransform(origin, rx, ry, rz)) return Shapes.block();
        AABB bounds = bounds(boxes);
        if (bounds == null) return Shapes.empty();
        return Shapes.create(transform(bounds, origin, rx, ry, rz));
    }

    public static VoxelShape build(List<AABB> boxes, Vec3 origin, float rx, float ry, float rz,
                                    boolean quality, double requestedStep) {
        checkCancelled();
        if (!validTransform(origin, rx, ry, rz)) return Shapes.block();
        AABB bounds = bounds(boxes);
        if (bounds == null) return Shapes.empty();
        AABB rotatedBounds = transform(bounds, origin, rx, ry, rz);
        if (boxes.size() > MAX_SUB_BOXES) return Shapes.create(rotatedBounds);
        boolean subdivide = quality && !(rightAngle(rx) && rightAngle(ry) && rightAngle(rz));
        double step = Double.isFinite(requestedStep) ? Math.max(0.01, requestedStep) : 0.1;
        // 步长只决定目标精度；超预算时加粗步长，保留整个几何的覆盖范围。
        while (subdivide && count(boxes, step, rx, ry, rz) > MAX_SUB_BOXES) {
            checkCancelled();
            step *= 2;
        }
        VoxelShape result = Shapes.empty();
        for (AABB box : boxes) {
            checkCancelled();
            int[] n = subdivide ? divisions(box, step, rx, ry, rz) : new int[]{1, 1, 1};
            for (int x = 0; x < n[0]; x++) for (int y = 0; y < n[1]; y++) for (int z = 0; z < n[2]; z++) {
                checkCancelled();
                AABB part = new AABB(
                        at(box.minX, box.maxX, x, n[0]), at(box.minY, box.maxY, y, n[1]), at(box.minZ, box.maxZ, z, n[2]),
                        at(box.minX, box.maxX, x + 1, n[0]), at(box.minY, box.maxY, y + 1, n[1]), at(box.minZ, box.maxZ, z + 1, n[2]));
                AABB rotated = transform(part, origin, rx, ry, rz);
                // 正交旋转保留精确形状；任意角旋转统一网格，避免细分盒坐标组合导致内存暴涨。
                if (boxes.size() > 32 || !(rightAngle(rx) && rightAngle(ry) && rightAngle(rz))) rotated = snapOutward(rotated, rotatedBounds);
                result = Shapes.joinUnoptimized(result, Shapes.create(rotated), BooleanOp.OR);
            }
        }
        checkCancelled();
        return result.optimize();
    }

    private static double at(double min, double max, int index, int count) {
        return index == count ? max : min + (max - min) * index / count;
    }

    private static long count(List<AABB> boxes, double step, float rx, float ry, float rz) {
        long total = 0;
        for (AABB box : boxes) {
            int[] n = divisions(box, step, rx, ry, rz);
            total += (long) n[0] * n[1] * n[2];
            if (total > MAX_SUB_BOXES) return total;
        }
        return total;
    }

    private static int[] divisions(AABB box, double step, float rx, float ry, float rz) {
        int axes = (rx != 0 ? 1 : 0) + (ry != 0 ? 1 : 0) + (rz != 0 ? 1 : 0);
        return new int[]{axes == 1 && rx != 0 ? 1 : cells(box.maxX - box.minX, step),
                axes == 1 && ry != 0 ? 1 : cells(box.maxY - box.minY, step),
                axes == 1 && rz != 0 ? 1 : cells(box.maxZ - box.minZ, step)};
    }

    private static int cells(double length, double step) {
        return (int) Math.max(1, Math.min(MAX_SUB_BOXES + 1, Math.ceil(length / step)));
    }

    private static boolean rightAngle(float rad) {
        double quarters = rad / (Math.PI / 2);
        return Math.abs(quarters - Math.round(quarters)) < 1e-4;
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    public static boolean validBox(AABB box) {
        return Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
    }

    private static boolean validTransform(Vec3 origin, float rx, float ry, float rz) {
        return origin != null && Double.isFinite(origin.x) && Double.isFinite(origin.y) && Double.isFinite(origin.z)
                && Float.isFinite(rx) && Float.isFinite(ry) && Float.isFinite(rz);
    }

    private static AABB bounds(List<AABB> boxes) {
        AABB bounds = null;
        for (AABB box : boxes) {
            if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                    || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)) continue;
            bounds = bounds == null ? box : bounds.minmax(box);
        }
        return bounds;
    }

    private static AABB transform(AABB box, Vec3 origin, float rx, float ry, float rz) {
        boolean integral = Math.abs(origin.x - Math.rint(origin.x)) < 1e-6
                && Math.abs(origin.y - Math.rint(origin.y)) < 1e-6 && Math.abs(origin.z - Math.rint(origin.z)) < 1e-6;
        Vec3 pivot = integral ? origin.add(0.5, 0, 0.5) : origin;
        double offset = integral ? 0.5 : 0;
        return transformBox(new LocalBox(new Vec3(box.minX - offset, box.minY, box.minZ - offset),
                new Vec3(box.maxX - offset, box.maxY, box.maxZ - offset)), pivot, rx, ry, rz);
    }

    private static AABB snapOutward(AABB box, AABB bounds) {
        return new AABB(snap(box.minX, bounds.minX, bounds.maxX, false), snap(box.minY, bounds.minY, bounds.maxY, false),
                snap(box.minZ, bounds.minZ, bounds.maxZ, false), snap(box.maxX, bounds.minX, bounds.maxX, true),
                snap(box.maxY, bounds.minY, bounds.maxY, true), snap(box.maxZ, bounds.minZ, bounds.maxZ, true));
    }

    private static double snap(double value, double min, double max, boolean up) {
        if (max - min < 1e-9) return value;
        double index = (value - min) / (max - min) * GRID;
        index = up ? Math.ceil(index) : Math.floor(index);
        return at(min, max, (int) Math.max(0, Math.min(GRID, index)), GRID);
    }

    private static AABB transformBox(LocalBox box, Vec3 pivotWorld, float rx, float ry, float rz) {
        Vec3[] corners = new Vec3[]{
                new Vec3(box.min.x, box.min.y, box.min.z),
                new Vec3(box.min.x, box.min.y, box.max.z),
                new Vec3(box.min.x, box.max.y, box.min.z),
                new Vec3(box.min.x, box.max.y, box.max.z),
                new Vec3(box.max.x, box.min.y, box.min.z),
                new Vec3(box.max.x, box.min.y, box.max.z),
                new Vec3(box.max.x, box.max.y, box.min.z),
                new Vec3(box.max.x, box.max.y, box.max.z)
        };

        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;

        for (Vec3 c : corners) {
            Vec3 w = applyTransform(c, pivotWorld, rx, ry, rz);
            minX = Math.min(minX, w.x);
            minY = Math.min(minY, w.y);
            minZ = Math.min(minZ, w.z);
            maxX = Math.max(maxX, w.x);
            maxY = Math.max(maxY, w.y);
            maxZ = Math.max(maxZ, w.z);
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Vec3 applyTransform(Vec3 v, Vec3 pivotWorld, float rx, float ry, float rz) {
        Vec3 r = rotateX(v, -rx);
        r = rotateY(r, ry);
        r = rotateZ(r, rz);
        return r.add(pivotWorld);
    }

    private static float snapTrig(double value) {
        double nearest = Math.rint(value);
        return (float) (Math.abs(value - nearest) < 1e-6 ? nearest : value);
    }

    // ======== 旋转工具 ========

    private static Vec3 rotateX(Vec3 v, float r) {
        float c = snapTrig(Math.cos(r)), s = snapTrig(Math.sin(r));
        return new Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c);
    }

    private static Vec3 rotateY(Vec3 v, float r) {
        float c = snapTrig(Math.cos(r)), s = snapTrig(Math.sin(r));
        return new Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
    }

    private static Vec3 rotateZ(Vec3 v, float r) {
        float c = snapTrig(Math.cos(r)), s = snapTrig(Math.sin(r));
        return new Vec3(v.x * c - v.y * s, v.x * s + v.y * c, v.z);
    }

}
