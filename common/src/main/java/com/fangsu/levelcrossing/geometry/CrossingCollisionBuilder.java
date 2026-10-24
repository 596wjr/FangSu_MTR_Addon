package com.fangsu.levelcrossing.geometry;

import com.fangsu.Main;
import com.fangsu.levelcrossing.CellSnapshot;
import com.fangsu.levelcrossing.CrossingSettings;
import com.fangsu.levelcrossing.TrackSlice;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 平交路口格的碰撞形状：沿主轨方向切成 stepCount 条阶梯盒，逐条取 AABB 后取并集。
 * <p>
 * 碰撞体必须覆盖到轨顶高度（玩家是踩在钢轨上的），高度随 pitch/roll 变化，
 * 所以用阶梯近似而不是一个整格方块。
 */
public final class CrossingCollisionBuilder {

    /** 单条带在格面上至少占这么多宽，避免退化盒被 VoxelShape 丢掉。 */
    private static final double MIN_THICKNESS = 1e-4;

    private CrossingCollisionBuilder() {
    }

    public static VoxelShape build(CellSnapshot snap, CrossingSettings cfg) {
        if (snap == null || cfg == null) return Shapes.block();
        int steps = Math.max(1, Math.min(32, cfg.stepCount));
        TrackSlice primary = DeckSurface.primary(snap);
        double dx = DeckSurface.dirX(snap);
        double dz = DeckSurface.dirZ(snap);

        double uMin = Double.MAX_VALUE;
        double uMax = -Double.MAX_VALUE;
        double vMin = Double.MAX_VALUE;
        double vMax = -Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            double x = (i & 1) == 0 ? 0 : 1;
            double z = (i & 2) == 0 ? 0 : 1;
            double u = (x - 0.5) * dx + (z - 0.5) * dz;
            double v = -(x - 0.5) * dz + (z - 0.5) * dx;
            uMin = Math.min(uMin, u);
            uMax = Math.max(uMax, u);
            vMin = Math.min(vMin, v);
            vMax = Math.max(vMax, v);
        }

        VoxelShape shape = Shapes.empty();
        for (int i = 0; i < steps; i++) {
            double u0 = uMin + (uMax - uMin) * i / steps;
            double u1 = uMin + (uMax - uMin) * (i + 1) / steps;

            double minX = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE;
            double maxZ = -Double.MAX_VALUE;
            double top = -Double.MAX_VALUE;
            for (int c = 0; c < 4; c++) {
                double u = (c & 1) == 0 ? u0 : u1;
                double v = (c & 2) == 0 ? vMin : vMax;
                // u/v 平面的逆变换
                double x = 0.5 + u * dx - v * dz;
                double z = 0.5 + u * dz + v * dx;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
                double y = primary == null
                        ? DeckSurface.groundY(snap, x, z)
                        : DeckSurface.railTopY(primary, snap, x, z);
                top = Math.max(top, y);
            }
            if (!isFinite(minX) || !isFinite(maxX) || !isFinite(minZ)
                    || !isFinite(maxZ) || !isFinite(top)) {
                // 快照里有脏数据：整格兜底成实心方块，别让玩家掉进地里
                Main.LOGGER.warn("[levelcrossing] 碰撞体坐标出现非有限值，退化为整格方块");
                return Shapes.block();
            }
            minX = clamp01(minX);
            maxX = clamp01(maxX);
            minZ = clamp01(minZ);
            maxZ = clamp01(maxZ);
            if (maxX - minX < MIN_THICKNESS) {
                maxX = Math.min(1, minX + MIN_THICKNESS);
                minX = maxX - MIN_THICKNESS;
            }
            if (maxZ - minZ < MIN_THICKNESS) {
                maxZ = Math.min(1, minZ + MIN_THICKNESS);
                minZ = maxZ - MIN_THICKNESS;
            }
            if (top < MIN_THICKNESS) top = MIN_THICKNESS;
            shape = Shapes.or(shape, Shapes.box(minX, 0, minZ, maxX, top, maxZ));
        }
        return shape.isEmpty() ? Shapes.block() : shape;
    }

    private static boolean isFinite(double value) {
        return Double.isFinite(value);
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
