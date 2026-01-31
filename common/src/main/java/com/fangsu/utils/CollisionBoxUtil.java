package com.fangsu.utils;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public class CollisionBoxUtil {
    public static class CollisionBox {
        private final List<AABB> boxes = new ArrayList<>();

        public CollisionBox(int... pos) {
            if (pos == null || pos.length < 6) return;
            boxes.add(new AABB(pos[0] / 16d, pos[1] / 16d, pos[2] / 16d, pos[3] / 16d, pos[4] / 16d, pos[5] / 16d));
        }

        public CollisionBox(double... pos) {
            if (pos == null || pos.length < 6) return;
            boxes.add(new AABB(pos[0] / 16d, pos[1] / 16d, pos[2] / 16d, pos[3] / 16d, pos[4] / 16d, pos[5] / 16d));
        }

        public CollisionBox(List<?> pos) {
            if (pos == null || pos.isEmpty()) return;
            if (pos.get(0) instanceof Number) {
                if (pos.size() < 6) return;
                boxes.add(new AABB(((Number) pos.get(0)).doubleValue() / 16d,
                        ((Number) pos.get(1)).doubleValue() / 16d,
                        ((Number) pos.get(2)).doubleValue() / 16d,
                        ((Number) pos.get(3)).doubleValue() / 16d,
                        ((Number) pos.get(4)).doubleValue() / 16d,
                        ((Number) pos.get(5)).doubleValue() / 16d));
            } else if (pos.get(0) instanceof List<?>) {
                A:
                for (Object o : pos) {
                    if (o instanceof List<?> oo) {
                        List<Double> thisList = new ArrayList<>();
                        for (Object o2 : oo) {
                            if (o2 instanceof Number) {
                                thisList.add(((Number) o2).doubleValue());
                            } else continue A;
                        }
                        if (thisList.size() < 6) continue;
                        boxes.add(new AABB(thisList.get(0) / 16d, thisList.get(1) / 16d, thisList.get(2) / 16d,
                                thisList.get(3) / 16d, thisList.get(4) / 16d, thisList.get(5) / 16d));
                    }
                }
            }
        }

        public VoxelShape asRotatedShape(Vec3 origin,
                                         float rx,
                                         float ry,
                                         float rz,
                                         double stepSize) {
            VoxelShape shape = Shapes.empty();
            for (AABB box : boxes) {
                shape = Shapes.or(shape, rotatedShape(box, origin, rx, ry, rz, stepSize));
            }
            return shape.optimize();
        }

        public VoxelShape asVoxelShape() {
            if (boxes.size() == 0) return null;
            if (boxes.size() == 1) return Shapes.create(boxes.get(0));
            return boxes.stream()
                    .map(Shapes::create)
                    .reduce(Shapes.empty(), Shapes::or);
        }
    }

    /**
     * 将局部 AABB 通过切分 + 旋转生成近似旋转的 VoxelShape
     *
     * @param localBox 局部坐标系 AABB（通常以格子角为坐标，例如 0..1，多格高度可 >1）
     * @param origin   旋转基点的世界坐标或中心坐标：
     *                 - 如果 origin 的 x/y/z 接近整数（例如方块格的坐标），方法会自动把枢轴设为 origin+(0.5,0,0.5)
     *                 - 否则 origin 被视为已经是“中心世界坐标”，直接使用
     * @param rx       X 轴旋转（弧度）
     * @param ry       Y 轴旋转（弧度）
     * @param rz       Z 轴旋转（弧度）
     * @param stepSize 每个切分块在局部坐标系中的边长（例如 0.0625 = 1/16）
     */
    public static VoxelShape rotatedShape(
            AABB localBox,
            Vec3 origin,
            float rx,
            float ry,
            float rz,
            double stepSize
    ) {
        // 特判：无旋转 -> 视 origin 是否为方块角或中心
        if (rx == 0f && ry == 0f && rz == 0f) {
            // 若 origin 看起来像方块角（整数），把结果移动到以格子角为基准的世界坐标
            if (isIntegralVec(origin)) {
                Vec3 pivot = origin.add(0.5, 0.0, 0.5);
                // localBox 是以格子角为原点的 -> 先平移为以中心为原点，再加上 pivot
                AABB centered = new AABB(localBox.minX - 0.5, localBox.minY, localBox.minZ - 0.5,
                        localBox.maxX - 0.5, localBox.maxY, localBox.maxZ - 0.5);
                return Shapes.create(centered.move(pivot));
            } else {
                // origin 已经是中心世界坐标
                return Shapes.create(localBox.move(origin));
            }
        }

        // 计算 pivot 与是否需要把 localBox 转为以中心为局部原点
        Vec3 pivotWorld;
        AABB workingLocal; // 局部 box，确保其坐标系以 pivot 相匹配（这里 pivot 采用中心）
        if (isIntegralVec(origin)) {
            // origin 是格子角 -> 我们把枢轴设置为格子中心
            pivotWorld = origin.add(0.5, 0.0, 0.5);
            // 把 localBox (以格子角为原点) 平移到以中心为原点（X/Z 减 0.5）
            workingLocal = new AABB(localBox.minX - 0.5, localBox.minY, localBox.minZ - 0.5,
                    localBox.maxX - 0.5, localBox.maxY, localBox.maxZ - 0.5);
        } else {
            // origin 已经是中心世界坐标，pivot 使用 origin，本地 box 不变
            pivotWorld = origin;
            workingLocal = localBox;
        }

        boolean xRot = rx != 0f;
        boolean yRot = ry != 0f;
        boolean zRot = rz != 0f;
        int axes = (xRot ? 1 : 0) + (yRot ? 1 : 0) + (zRot ? 1 : 0);

        VoxelShape shape = Shapes.empty();

        if (axes == 1) {
            for (LocalBox part : split1D(workingLocal, stepSize, xRot, yRot, zRot)) {
                AABB world = transformBox(part, pivotWorld, rx, ry, rz);
                shape = Shapes.or(shape, Shapes.create(world));
            }
        } else {
            for (LocalBox part : split3D(workingLocal, stepSize)) {
                AABB world = transformBox(part, pivotWorld, rx, ry, rz);
                shape = Shapes.or(shape, Shapes.create(world));
            }
        }

        return shape.optimize();
    }

    /* =============================
     * 内部结构
     * ============================= */

    private static boolean isIntegralVec(Vec3 v) {
        // 允许一点浮点误差
        double eps = 1e-6;
        return Math.abs(Math.round(v.x) - v.x) < eps
                && Math.abs(Math.round(v.y) - v.y) < eps
                && Math.abs(Math.round(v.z) - v.z) < eps;
    }

    private record LocalBox(Vec3 min, Vec3 max) {
    }

    /* =============================
     * 切分策略（以 stepSize 为单位）
     * ============================= */

    private static List<LocalBox> split3D(AABB box, double stepSize) {
        List<LocalBox> list = new ArrayList<>();

        double rangeX = box.maxX - box.minX;
        double rangeY = box.maxY - box.minY;
        double rangeZ = box.maxZ - box.minZ;

        int nx = Math.max(1, (int) Math.ceil(rangeX / stepSize));
        int ny = Math.max(1, (int) Math.ceil(rangeY / stepSize));
        int nz = Math.max(1, (int) Math.ceil(rangeZ / stepSize));

        double dx = rangeX / nx;
        double dy = rangeY / ny;
        double dz = rangeZ / nz;

        for (int ix = 0; ix < nx; ix++) {
            for (int iy = 0; iy < ny; iy++) {
                for (int iz = 0; iz < nz; iz++) {
                    Vec3 min = new Vec3(
                            box.minX + dx * ix,
                            box.minY + dy * iy,
                            box.minZ + dz * iz
                    );
                    list.add(new LocalBox(min, min.add(dx, dy, dz)));
                }
            }
        }

        return list;
    }

    private static List<LocalBox> split1D(AABB box, double stepSize, boolean rx, boolean ry, boolean rz) {
        List<LocalBox> list = new ArrayList<>();

        if (ry) {
            // 切 XZ，整条 Y
            double rangeX = box.maxX - box.minX;
            double rangeZ = box.maxZ - box.minZ;
            int nx = Math.max(1, (int) Math.ceil(rangeX / stepSize));
            int nz = Math.max(1, (int) Math.ceil(rangeZ / stepSize));
            double dx = rangeX / nx;
            double dz = rangeZ / nz;

            for (int ix = 0; ix < nx; ix++) {
                for (int iz = 0; iz < nz; iz++) {
                    Vec3 min = new Vec3(
                            box.minX + dx * ix,
                            box.minY,
                            box.minZ + dz * iz
                    );
                    list.add(new LocalBox(min, new Vec3(min.x + dx, box.maxY, min.z + dz)));
                }
            }
            return list;
        }

        if (rx) {
            // 切 YZ，整条 X
            double rangeY = box.maxY - box.minY;
            double rangeZ = box.maxZ - box.minZ;
            int ny = Math.max(1, (int) Math.ceil(rangeY / stepSize));
            int nz = Math.max(1, (int) Math.ceil(rangeZ / stepSize));
            double dy = rangeY / ny;
            double dz = rangeZ / nz;

            for (int iy = 0; iy < ny; iy++) {
                for (int iz = 0; iz < nz; iz++) {
                    Vec3 min = new Vec3(
                            box.minX,
                            box.minY + dy * iy,
                            box.minZ + dz * iz
                    );
                    list.add(new LocalBox(min, new Vec3(box.maxX, min.y + dy, min.z + dz)));
                }
            }
            return list;
        }

        // rz == true
        double rangeX = box.maxX - box.minX;
        double rangeY = box.maxY - box.minY;
        int nx = Math.max(1, (int) Math.ceil(rangeX / stepSize));
        int ny = Math.max(1, (int) Math.ceil(rangeY / stepSize));
        double dx = rangeX / nx;
        double dy = rangeY / ny;

        for (int ix = 0; ix < nx; ix++) {
            for (int iy = 0; iy < ny; iy++) {
                Vec3 min = new Vec3(
                        box.minX + dx * ix,
                        box.minY + dy * iy,
                        box.minZ
                );
                list.add(new LocalBox(min, new Vec3(min.x + dx, min.y + dy, box.maxZ)));
            }
        }

        return list;
    }

    /* =============================
     * 变换
     * ============================= */

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
            // 现在 c 是相对于 pivot 的局部坐标（如果上面平移了的话）
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
        // 先旋转局部向量，再平移到世界枢轴位置
        Vec3 r = rotateX(v, rx);
        r = rotateY(r, ry);
        r = rotateZ(r, rz);
        return r.add(pivotWorld);
    }

    /* =============================
     * 旋转工具
     * ============================= */

    private static Vec3 rotateX(Vec3 v, float r) {
        float c = (float) Math.cos(r), s = (float) Math.sin(r);
        return new Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c);
    }

    private static Vec3 rotateY(Vec3 v, float r) {
        float c = (float) Math.cos(r), s = (float) Math.sin(r);
        return new Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
    }

    private static Vec3 rotateZ(Vec3 v, float r) {
        float c = (float) Math.cos(r), s = (float) Math.sin(r);
        return new Vec3(v.x * c - v.y * s, v.x * s + v.y * c, v.z);
    }
}
