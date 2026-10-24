package com.fangsu.levelcrossing.geometry;

/**
 * 不可变 2D 点（方块局部 XZ 平面）。
 * <p>
 * 平交路口的所有平面运算都在 XZ 平面上做，Z 轴朝南；本项目不引入任何 MC 类型，
 * 这样几何算法可以脱离游戏单独编译、单独自检。
 */
public record Vec2(double x, double z) {

    public Vec2 add(Vec2 other) {
        return new Vec2(x + other.x, z + other.z);
    }

    public Vec2 sub(Vec2 other) {
        return new Vec2(x - other.x, z - other.z);
    }

    public Vec2 mul(double k) {
        return new Vec2(x * k, z * k);
    }

    public double dot(Vec2 other) {
        return x * other.x + z * other.z;
    }

    /** 2D 叉积（z 分量）。正值表示 other 在本点逆时针一侧。 */
    public double cross(Vec2 other) {
        return x * other.z - z * other.x;
    }

    public double length() {
        return Math.hypot(x, z);
    }

    public Vec2 lerp(Vec2 other, double t) {
        return new Vec2(x + (other.x - x) * t, z + (other.z - z) * t);
    }

    /** 归一化；长度为 0 时返回自身，调用方不必再判空。 */
    public Vec2 normalized() {
        double len = length();
        return len < 1e-12 ? this : new Vec2(x / len, z / len);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(z);
    }
}
