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

    /** 三点式剖面中点的最小归一化位置（避开 0，保证位置严格升序）。 */
    public static final double MIN_MIDDLE_FRACTION = 1.0E-6D;

    /** 三点式剖面中点的最大归一化位置（避开 1，保证位置严格升序）。 */
    public static final double MAX_MIDDLE_FRACTION = 1.0D - 1.0E-6D;

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

    /**
     * 「逐轨道超高」三点式剖面：{@code start} 在位置 0、{@code end} 在位置 1、
     * {@code middle} 在 {@code middleFraction}。
     * <p>
     * 这只是既有 N 点构造器的一个工厂，<b>不改变插值语义</b>：三点之间仍是分段线性插值，
     * {@code [0, middleFraction]} 与 {@code [middleFraction, 1]} 两段各由两个控制点决定，
     * 区间外仍是常量外推（端点值即 {@code start} / {@code end}）。
     * <p>
     * <b>语义与规范化（与 {@link #twoPointDegrees} 保持一致的风格：本类不抛异常、
     * 对所有输入都给出确定结果）</b>：
     * <ul>
     *   <li>{@code start / middle / end}：非有限值（NaN / ±Infinity）一律按 <b>0 度</b> 处理
     *       —— 剖面是「显示量」，坏数据最安全的降级是不倾斜。数值本身<b>不再夹取</b>：
     *       ±45 的夹取属于协议与编辑层的职责（见 {@code RailTiltPackets}），内核不重复设限，
     *       以免上游放宽范围时还要改内核。</li>
     *   <li>{@code middleFraction}：非有限值按 <b>0.5</b>，随后夹进开区间
     *       {@code (0, 1)}（即 [{@link #MIN_MIDDLE_FRACTION}, {@link #MAX_MIDDLE_FRACTION}]）。
     *       必须夹取：N 点构造器要求位置<b>严格升序</b>，取 0 或 1 会与端点位置重合而抛
     *       {@code IllegalArgumentException}；夹到开区间保证任何输入都能构造出合法剖面。</li>
     *   <li>{@code isZero()}：三点全为 0（或全部非有限而按 0 处理后）时为 {@code true}，
     *       含义与两点式完全一致 —— 处处 0 滚转，几何内核会跳过全部滚转计算。</li>
     * </ul>
     *
     * @param startDegrees   起点（位置 0）滚转角，度
     * @param middleDegrees  中点（位置 {@code middleFraction}）滚转角，度
     * @param endDegrees     终点（位置 1）滚转角，度
     * @param middleFraction 中点的归一化位置，非有限值按 0.5，最终夹进 (0,1)
     */
    public static RailRollProfile threePointDegrees(double startDegrees, double middleDegrees, double endDegrees, double middleFraction) {
        return new RailRollProfile(
                new double[]{0.0D, normalizeMiddleFraction(middleFraction), 1.0D},
                new double[]{
                        Math.toRadians(finiteOrZero(startDegrees)),
                        Math.toRadians(finiteOrZero(middleDegrees)),
                        Math.toRadians(finiteOrZero(endDegrees))
                }
        );
    }

    /** 非有限值（NaN / ±Infinity）归零，其余原样返回。 */
    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }

    /** 中点位置规范化为 {@code [MIN_MIDDLE_FRACTION, MAX_MIDDLE_FRACTION]}，非有限值取 0.5。 */
    private static double normalizeMiddleFraction(double fraction) {
        final double value = Double.isFinite(fraction) ? fraction : 0.5D;
        if (value < MIN_MIDDLE_FRACTION) {
            return MIN_MIDDLE_FRACTION;
        }
        if (value > MAX_MIDDLE_FRACTION) {
            return MAX_MIDDLE_FRACTION;
        }
        return value;
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
