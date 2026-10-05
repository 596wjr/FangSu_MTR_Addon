package com.fangsu.mappings.rail;

/**
 * 版本无关的「滚转（外轨超高）剖面」。
 * <p>
 * 关键点是以<b>归一化位置</b>（{@code fraction ∈ [0,1]}，即「沿轨道参数 / 轨道长度」）为键，
 * 而不是以米为键：这样剖面可以在拿到轨道长度<b>之前</b>构造，避免「先有长度才能建剖面、
 * 先有剖面才能建几何」的循环依赖。适配层在调用几何内核时用 {@code value / length} 把参数换算成分数。
 * <p>
 * 插值语义与 MTR 上游 {@code tiltPoints = 2} 一致：分段<b>线性</b>插值、区间外<b>常量外推</b>，
 * 空剖面等价于「处处为 0」。
 * <p>
 * 本类禁止 import 任何 {@code org.mtr.*} / {@code mtr.*} / MC 类型，需在两个工程保持逐字节一致
 * （校验见 {@code devtools/check-synced-files.ps1}）。
 */
public final class RailRollProfile {

    /** 空剖面：处处 0 滚转。 */
    public static final RailRollProfile NONE = new RailRollProfile(new double[]{0.0D, 1.0D}, new double[]{0.0D, 0.0D});

    private final double[] fractions;
    private final double[] radians;

    /**
     * @param fractions 归一化位置，需为非空、等长、升序、取值在 [0,1]
     * @param radians   对应位置的滚转角（弧度）
     */
    public RailRollProfile(double[] fractions, double[] radians) {
        if (fractions == null || radians == null || fractions.length == 0 || fractions.length != radians.length) {
            throw new IllegalArgumentException("RailRollProfile: fractions/radians must be non-empty and same length");
        }
        this.fractions = fractions.clone();
        this.radians = radians.clone();
        for (int i = 1; i < this.fractions.length; i++) {
            if (this.fractions[i] < this.fractions[i - 1]) {
                throw new IllegalArgumentException("RailRollProfile: fractions must be ascending");
            }
        }
    }

    /** 两点式剖面：0 处 roll1、1 处 roll2（节点的「两端翻滚角」就是这种情形）。 */
    public static RailRollProfile twoPoint(double roll1Radians, double roll2Radians) {
        return new RailRollProfile(new double[]{0.0D, 1.0D}, new double[]{roll1Radians, roll2Radians});
    }

    /** 由角度（度）构造两点式剖面。 */
    public static RailRollProfile twoPointDegrees(double roll1Degrees, double roll2Degrees) {
        return twoPoint(Math.toRadians(roll1Degrees), Math.toRadians(roll2Degrees));
    }

    /** 是否处处为 0（用于跳过全部滚转相关计算）。 */
    public boolean isZero() {
        for (final double value : radians) {
            if (value != 0.0D) {
                return false;
            }
        }
        return true;
    }

    /**
     * 取归一化位置处的滚转角（弧度）：分段线性、区间外常量外推。
     *
     * @param fraction 归一化位置，内部会 clamp 到 [0,1]
     */
    public double getRadians(double fraction) {
        final double t = fraction < 0.0D ? 0.0D : (fraction > 1.0D ? 1.0D : fraction);
        final int size = fractions.length;
        if (t <= fractions[0]) {
            return radians[0];
        }
        if (t >= fractions[size - 1]) {
            return radians[size - 1];
        }
        for (int i = 1; i < size; i++) {
            final double previous = fractions[i - 1];
            final double current = fractions[i];
            if (t < current) {
                final double span = current - previous;
                if (span <= 0.0D) {
                    return radians[i];
                }
                final double ratio = (t - previous) / span;
                return radians[i - 1] * (1.0D - ratio) + radians[i] * ratio;
            }
        }
        return radians[size - 1];
    }

    public int size() {
        return fractions.length;
    }

    public double fractionAt(int index) {
        return fractions[index];
    }

    public double radiansAt(int index) {
        return radians[index];
    }
}
