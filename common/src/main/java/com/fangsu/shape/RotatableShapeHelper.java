package com.fangsu.shape;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.minecraft.world.phys.shapes.BooleanOp;
import java.util.*;
import java.util.concurrent.*;

// 弧度制
public class RotatableShapeHelper {
    private static final RotatableShapeHelper instance = new RotatableShapeHelper();
    private static final double DEFAULT_STEP_SIZE = 0.25d;
    private static final int MAX_SUB_BOXES = 4096;

    // 异步计算线程池：单线程守护线程，不会阻止 JVM 退出
    private static final ExecutorService COMPUTATION_THREAD = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RotatedShape-Compute");
        t.setDaemon(true);
        return t;
    });

    public static RotatableShapeHelper getInstance() {
        return instance;
    }

    // ======== 缓存 ========

    private final Map<BlockPos, ShapeCacheEntry> cache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, ShapeCacheEntry> eldest) {
            return size() > 256;
        }
    };

    private static class ShapeCacheEntry {
        final PosInfo posInfo;
        final ShapeCollection shapeCollection;
        final CompletableFuture<VoxelShape> futureShape; // 异步旋转计算结果
        final VoxelShape originalShape;                  // 未旋转的原始碰撞箱，异步未完成时返回

        ShapeCacheEntry(PosInfo posInfo, ShapeCollection shapeCollection,
                        CompletableFuture<VoxelShape> futureShape, VoxelShape originalShape) {
            this.posInfo = posInfo;
            this.shapeCollection = shapeCollection;
            this.futureShape = futureShape;
            this.originalShape = originalShape;
        }
    }

    private final Object cacheLock = new Object();

    /**
     * 初始化指定方块位置的碰撞箱（异步计算）。
     * 立即存入未旋转原始形状作为降级，旋转计算在后台线程执行，绝不阻塞调用线程。
     */
    public void initForBlock(BlockPos pos, float tx, float ty, float tz, float rx, float ry, float rz, ShapeCollection shape) {
        VoxelShape original = shape.asVoxelShape();
        CompletableFuture<VoxelShape> future = CompletableFuture.supplyAsync(
                () -> buildRotatedShape(shape, rx, ry, rz),
                COMPUTATION_THREAD
        );
        synchronized (cacheLock) {
            cache.put(pos, new ShapeCacheEntry(
                    new PosInfo(tx, ty, tz, rx, ry, rz),
                    shape,
                    future,
                    original
            ));
        }
    }


    /**
     * 获取指定方块位置缓存的碰撞箱。
     * 若异步旋转计算尚未完成，则返回未旋转的原始碰撞箱，防止阻塞主线程。
     */
    public VoxelShape getShapeForBlock(BlockPos pos, float tx, float ty, float tz, float rx, float ry, float rz) {
        ShapeCacheEntry entry;
        synchronized (cacheLock) {
            entry = cache.get(pos);
        }
        if (entry == null) return null;

        boolean changed = entry.posInfo.checkAndUpdate(tx, ty, tz, rx, ry, rz);
        if (changed) {
            // 参数有变化：先返回旧碰撞箱（旧异步已完成则返回旧旋转结果，否则返回旧原始形状）
            VoxelShape previousShape;
            if (entry.futureShape.isDone()) {
                try {
                    previousShape = entry.futureShape.get();
                } catch (InterruptedException | ExecutionException e) {
                    previousShape = entry.originalShape;
                }
            } else {
                previousShape = entry.originalShape;
            }

            // 提交新的异步任务
            VoxelShape original = entry.shapeCollection.asVoxelShape();
            CompletableFuture<VoxelShape> future = CompletableFuture.supplyAsync(
                    () -> buildRotatedShape(entry.shapeCollection, rx, ry, rz),
                    COMPUTATION_THREAD
            );
            synchronized (cacheLock) {
                cache.put(pos, new ShapeCacheEntry(
                        entry.posInfo,
                        entry.shapeCollection,
                        future,
                        original
                ));
            }
            return previousShape;
        }

        // 参数未变化：检查异步计算是否已完成
        if (entry.futureShape.isDone()) {
            try {
                return entry.futureShape.get(); // 立即返回，不会阻塞
            } catch (InterruptedException | ExecutionException e) {
                // 计算失败，降级返回原始形状
                return entry.originalShape;
            }
        }

        // 异步计算尚未完成，返回原始未旋转形状作为降级
        return entry.originalShape;
    }

    /**
     * 清除指定方块位置的缓存。
     */
    public void removeCache(BlockPos pos) {
        synchronized (cacheLock) {
            cache.remove(pos);
        }
    }

    // ======== 旋转算法（从 CollisionBoxUtil 移植并简化） ========

    /**
     * 对 ShapeCollection 中的所有 RawShape 执行旋转，合并为一个 VoxelShape。
     * 每个 RawShape 的坐标是像素值（0~16），旋转时以方块中心 (8, 0, 8) 为枢轴。
     */
    private static VoxelShape buildRotatedShape(ShapeCollection shapeCollection, float rx, float ry, float rz) {
        if (shapeCollection == null || shapeCollection.isEmpty()) return Shapes.empty();

        VoxelShape result = Shapes.empty();
        for (RawShape raw : shapeCollection.getShapes()) {
            AABB box = raw.asAABB();
            // RawShape 使用世界坐标 (0~1)，直接使用
            AABB normalizedBox = new AABB(
                    box.minX, box.minY, box.minZ,
                    box.maxX, box.maxY, box.maxZ
            );
            VoxelShape part = rotatedShape(normalizedBox, rx, ry, rz, DEFAULT_STEP_SIZE);
            result = Shapes.joinUnoptimized(result, part, BooleanOp.OR);
        }
        return result.optimize();
    }

    /**
     * 对单个 AABB（世界坐标）应用旋转，返回旋转后的 VoxelShape。
     * 枢轴为方块中心 (0.5, 0, 0.5)。
     */
    private static VoxelShape rotatedShape(AABB localBox, float rx, float ry, float rz, double stepSize) {
        if (localBox == null) return Shapes.empty();

        // 枢轴：方块中心 (0.5, 0, 0.5)
        Vec3 pivotWorld = new Vec3(0.5, 0.0, 0.5);

        // 将局部盒变换到以枢轴为原点的局部坐标系
        AABB workingLocal = new AABB(
                localBox.minX - 0.5, localBox.minY, localBox.minZ - 0.5,
                localBox.maxX - 0.5, localBox.maxY, localBox.maxZ - 0.5
        );

        // 无旋转特判
        if (rx == 0f && ry == 0f && rz == 0f) {
            return Shapes.create(workingLocal.move(pivotWorld)).optimize();
        }

        boolean xRot = rx != 0f;
        boolean yRot = ry != 0f;
        boolean zRot = rz != 0f;
        int axes = (xRot ? 1 : 0) + (yRot ? 1 : 0) + (zRot ? 1 : 0);

        // 收集所有旋转后的 AABB，避免在循环中反复调用昂贵的 Shapes.or()
        List<AABB> worldBoxes = new ArrayList<>();
        if (axes == 1) {
            for (LocalBox part : split1D(workingLocal, stepSize, xRot, yRot, zRot)) {
                worldBoxes.add(transformBox(part, pivotWorld, rx, ry, rz));
                if (worldBoxes.size() >= MAX_SUB_BOXES) break;
            }
        } else {
            for (LocalBox part : split3D(workingLocal, stepSize)) {
                worldBoxes.add(transformBox(part, pivotWorld, rx, ry, rz));
                if (worldBoxes.size() >= MAX_SUB_BOXES) break;
            }
        }

        // 用 joinUnoptimized 批量合并，最后只 optimize 一次
        VoxelShape shape = Shapes.empty();
        for (AABB world : worldBoxes) {
            shape = Shapes.joinUnoptimized(shape, Shapes.create(world), BooleanOp.OR);
        }
        return shape.optimize();
    }

    // ======== 内部结构 ========

    private record LocalBox(Vec3 min, Vec3 max) {
    }

    // ======== 切分策略 ========

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

    // ======== 变换 ========

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
        Vec3 r = rotateX(v, rx);
        r = rotateY(r, ry);
        r = rotateZ(r, rz);
        return r.add(pivotWorld);
    }

    // ======== 旋转工具 ========

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

    // ======== 内部数据结构 ========

    private static class PosInfo {
        private static final float EPSILON = 1e-6f;

        private float tx, ty, tz;
        private float rx, ry, rz;

        public PosInfo(float tx, float ty, float tz, float rx, float ry, float rz) {
            this.tx = tx;
            this.ty = ty;
            this.tz = tz;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
        }

        public boolean checkAndUpdate(float tx, float ty, float tz, float rx, float ry, float rz) {
            boolean changed = !floatEquals(this.tx, tx) || !floatEquals(this.ty, ty)
                    || !floatEquals(this.tz, tz) || !floatEquals(this.rx, rx)
                    || !floatEquals(this.ry, ry) || !floatEquals(this.rz, rz);

            this.tx = tx;
            this.ty = ty;
            this.tz = tz;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            return changed;
        }

        private static boolean floatEquals(float a, float b) {
            return Math.abs(a - b) < EPSILON;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tx, ty, tz, rx, ry, rz);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            PosInfo o = (PosInfo) obj;
            return floatEquals(tx, o.tx) && floatEquals(ty, o.ty) && floatEquals(tz, o.tz)
                    && floatEquals(rx, o.rx) && floatEquals(ry, o.ry) && floatEquals(rz, o.rz);
        }
    }

}
