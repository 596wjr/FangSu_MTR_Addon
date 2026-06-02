package com.fangsu.utils;

import com.fangsu.shape.RawShape;
import com.fangsu.shape.ShapeCollection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @deprecated 请使用 {@link com.fangsu.shape.RotatableShapeHelper} 替代。
 * RotatableShapeHelper 提供了更简洁的碰撞箱旋转和缓存管理 API，
 * 直接基于 ShapeCollection / RawShape 工作，无需手动构建 CollisionBox。
 */
@Deprecated
public class CollisionBoxUtil {

    /* ========= CollisionBox (使用者侧 API) ========= */
    public static class CollisionBox {
        private final List<AABB> boxes = new ArrayList<>();
        private Vec3 offset = Vec3.ZERO; // 累积平移

        public CollisionBox(int... pos) {
            if (pos == null || pos.length < 6) return;
            boxes.add(new AABB(
                    pos[0] / 16d, pos[1] / 16d, pos[2] / 16d,
                    pos[3] / 16d, pos[4] / 16d, pos[5] / 16d
            ));
        }

        public CollisionBox(double... pos) {
            if (pos == null || pos.length < 6) return;
            boxes.add(new AABB(
                    pos[0] / 16d, pos[1] / 16d, pos[2] / 16d,
                    pos[3] / 16d, pos[4] / 16d, pos[5] / 16d
            ));
        }

        public CollisionBox(double[][] pos) {
            if (pos == null) return;
            for (double[] a : pos) {
                if (a == null || a.length < 6) continue;
                boxes.add(new AABB(a[0], a[1], a[2], a[3], a[4], a[5]));
            }
        }

        public CollisionBox(float[][] pos) {
            if (pos == null) return;
            for (float[] a : pos) {
                if (a == null || a.length < 6) continue;
                boxes.add(new AABB(a[0], a[1], a[2], a[3], a[4], a[5]));
            }
        }

        public CollisionBox(RawShape rawShape) {
            boxes.add(rawShape.asAABB());
        }

        public CollisionBox(ShapeCollection shapeCollection) {
            shapeCollection.getShapes().forEach(shape -> boxes.add(shape.asAABB()));
        }

        public CollisionBox(List<?> pos) {
            if (pos == null || pos.isEmpty()) return;

            if (pos.get(0) instanceof Number) {
                if (pos.size() < 6) return;
                boxes.add(new AABB(
                        ((Number) pos.get(0)).doubleValue() / 16d,
                        ((Number) pos.get(1)).doubleValue() / 16d,
                        ((Number) pos.get(2)).doubleValue() / 16d,
                        ((Number) pos.get(3)).doubleValue() / 16d,
                        ((Number) pos.get(4)).doubleValue() / 16d,
                        ((Number) pos.get(5)).doubleValue() / 16d
                ));
            } else if (pos.get(0) instanceof List<?>) {
                A:
                for (Object o : pos) {
                    if (o instanceof List<?> oo) {
                        List<Double> thisList = new ArrayList<>();
                        for (Object o2 : oo) {
                            if (o2 instanceof Number n) {
                                thisList.add(n.doubleValue());
                            } else {
                                continue A;
                            }
                        }
                        if (thisList.size() < 6) continue;
                        boxes.add(new AABB(
                                thisList.get(0) / 16d,
                                thisList.get(1) / 16d,
                                thisList.get(2) / 16d,
                                thisList.get(3) / 16d,
                                thisList.get(4) / 16d,
                                thisList.get(5) / 16d
                        ));
                    }
                }
            }
        }

        public void translate(double dx, double dy, double dz) {
            offset = new Vec3(dx, dy, dz);
        }

        public void translate(Vec3 delta) {
            if (delta != null) offset = delta;
        }

        public void addBox(AABB box) {
            if (box != null) boxes.add(box);
        }

        public List<AABB> getBoxes() {
            List<AABB> moved = new ArrayList<>(boxes.size());
            for (AABB box : boxes) {
                moved.add(box.move(offset));
            }
            return moved;
        }

        public void clear() {
            boxes.clear();
            offset = Vec3.ZERO;
        }

        public VoxelShape asVoxelShape() {
            if (boxes.isEmpty()) return Shapes.empty();
            VoxelShape shape = Shapes.empty();
            for (AABB box : boxes) {
                shape = Shapes.or(shape, Shapes.create(box.move(offset)));
            }
            return shape.optimize();
        }

        public VoxelShape asRotatedShape(Vec3 origin, float rx, float ry, float rz, double stepSize) {
            if (boxes.isEmpty()) return Shapes.empty();
            Vec3 worldOrigin = origin.add(offset);
            VoxelShape shape = Shapes.empty();
            for (AABB box : boxes) {
                shape = Shapes.or(
                        shape,
                        CollisionBoxUtil.rotatedShape(box, worldOrigin, rx, ry, rz, stepSize)
                );
            }
            return shape.optimize();
        }
    }

    /* =============================
     * 缓存机制（LRU + 泛型 Key）
     * ============================= */

    private static final int DEFAULT_CACHE_CAPACITY = 256;
    private static volatile Map<ShapeCacheKey, VoxelShape> SHAPE_CACHE =
            createLRUCache(DEFAULT_CACHE_CAPACITY);

    private static volatile int cacheCapacity = DEFAULT_CACHE_CAPACITY;

    public static synchronized void setCacheCapacity(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        cacheCapacity = capacity;
        SHAPE_CACHE = createLRUCache(capacity);
    }

    public static synchronized void clearCache() {
        SHAPE_CACHE.clear();
    }

    /**
     * 泛型 LRU Cache 工具方法（关键修改点）
     */
    private static <K> Map<K, VoxelShape> createLRUCache(int capacity) {
        return new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, VoxelShape> eldest) {
                return size() > capacity;
            }
        };
    }

    private static double q(double value) {
        return Math.round(value * 1e4) / 1e4;
    }

    /* =============================
     * Rotated Box Cache
     * ============================= */

    private record RotatedCacheKey(
            long pos,
            double originX, double originY, double originZ,
            float rx, float ry, float rz,
            double step,
            int hash
    ) {
        static RotatedCacheKey of(long pos, Vec3 origin,
                                  float rx, float ry, float rz,
                                  double step, int hash) {
            return new RotatedCacheKey(
                    pos,
                    q(origin.x), q(origin.y), q(origin.z),
                    rx, ry, rz,
                    q(step),
                    hash
            );
        }
    }

    private static final int DEFAULT_BOX_CACHE_CAPACITY = 1024;
    private static final Map<RotatedCacheKey, VoxelShape> BOX_SHAPE_CACHE =
            createLRUCache(DEFAULT_BOX_CACHE_CAPACITY);

    public static synchronized void clearBoxShapeCache() {
        BOX_SHAPE_CACHE.clear();
    }

    public static VoxelShape cachedRotatedShape(
            long posLong,
            CollisionBox box,
            Vec3 origin,
            float rx,
            float ry,
            float rz,
            double stepSize
    ) {
        if (box == null || origin == null) return Shapes.empty();
        int hash = box.getBoxes().hashCode();
        RotatedCacheKey key = RotatedCacheKey.of(posLong, origin, rx, ry, rz, stepSize, hash);
        synchronized (BOX_SHAPE_CACHE) {
            VoxelShape cached = BOX_SHAPE_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        VoxelShape shape = box.asRotatedShape(origin, rx, ry, rz, stepSize);
        synchronized (BOX_SHAPE_CACHE) {
            BOX_SHAPE_CACHE.put(key, shape);
        }
        return shape;
    }

    private record ShapeCacheKey(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double originX, double originY, double originZ,
            float rx, float ry, float rz,
            double step
    ) {
        static ShapeCacheKey of(AABB box, Vec3 origin, float rx, float ry, float rz, double step) {
            return new ShapeCacheKey(
                    q(box.minX), q(box.minY), q(box.minZ),
                    q(box.maxX), q(box.maxY), q(box.maxZ),
                    q(origin.x), q(origin.y), q(origin.z),
                    rx, ry, rz,
                    q(step)
            );
        }
    }

    /* =============================
     * 旋转 + 切分逻辑（保持原逻辑）
     * ============================= */

    public static VoxelShape rotatedShape(AABB localBox, Vec3 origin,
                                          float rx, float ry, float rz, double stepSize) {
        if (localBox == null || origin == null) return Shapes.empty();

        ShapeCacheKey key = ShapeCacheKey.of(localBox, origin, rx, ry, rz, stepSize);
        synchronized (SHAPE_CACHE) {
            VoxelShape cached = SHAPE_CACHE.get(key);
            if (cached != null) return cached;
        }

        VoxelShape shape;

        // 无旋转特判
        if (rx == 0f && ry == 0f && rz == 0f) {
            if (isIntegralVec(origin)) {
                Vec3 pivot = origin.add(0.5, 0.0, 0.5);
                AABB centered = new AABB(localBox.minX - 0.5, localBox.minY, localBox.minZ - 0.5,
                        localBox.maxX - 0.5, localBox.maxY, localBox.maxZ - 0.5);
                shape = Shapes.create(centered.move(pivot));
            } else {
                shape = Shapes.create(localBox.move(origin));
            }
            shape = shape.optimize();
            synchronized (SHAPE_CACHE) {
                SHAPE_CACHE.put(key, shape);
            }
            return shape;
        }

        // 计算 pivot
        Vec3 pivotWorld;
        AABB workingLocal;
        if (isIntegralVec(origin)) {
            pivotWorld = origin.add(0.5, 0.0, 0.5);
            workingLocal = new AABB(localBox.minX - 0.5, localBox.minY, localBox.minZ - 0.5,
                    localBox.maxX - 0.5, localBox.maxY, localBox.maxZ - 0.5);
        } else {
            pivotWorld = origin;
            workingLocal = localBox;
        }

        boolean xRot = rx != 0f;
        boolean yRot = ry != 0f;
        boolean zRot = rz != 0f;
        int axes = (xRot ? 1 : 0) + (yRot ? 1 : 0) + (zRot ? 1 : 0);

        shape = Shapes.empty();
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

        VoxelShape result = shape.optimize();
        synchronized (SHAPE_CACHE) {
            SHAPE_CACHE.put(key, result);
        }
        return result;
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
