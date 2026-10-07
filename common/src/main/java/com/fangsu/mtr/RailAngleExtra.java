package com.fangsu.mtr;

import mtr.data.RailAngle;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 任意角度轨道方向（万向节点）支持接口。
 * <p>
 * 自 NTE mtrsteamloco 的 {@code RailAngleExtra} 移植（MIT License, Copyright (c) 2022-present Zbx1425）。
 * <p>
 * MTR3 的 {@link RailAngle} 是 16 个 22.5° 枚举常量的枚举类，无法表达任意角度；
 * 本接口配合 {@link com.fangsu.mixin.RailAngleMixin} 通过 {@code @Invoker("<init>")}
 * 直接调用枚举私有构造器，伪造出 {@code ordinal == -1} 的“幻影常量”，
 * 从而让 {@code RailAngle} 也能承载任意角度（sin/cos/tan/halfTan 均为精确值）。
 * <p>
 * <b>幻影实例缓存</b>（照 MTR4 版 FangSu {@code AngleMixin} 的做法，理由在此复述一遍）：
 * MTR3 的 {@code mtr.path.PathFinder} 用<b>引用比较</b>判断角度，例如
 * {@code PathFinder.java:184} 的 {@code tempAngle == expectedAngle}（民航弧线寻路靠它判停），
 * 以及 {@code PathFinder.java:161} 用 {@code rail.facingStart != newDirection.getOpposite()}
 * 排除“原路返回”的那条反向轨。如果每次 {@code fromDegrees/fromRadians/getOpposite}
 * 都新建对象，这些比较会失效（民航寻路会走满 16 步而非及时中断；反向轨排除失效）。
 * 因此：同角度（键 = 归一化到 [0,360) 的 float 度数）复用同一实例，
 * 且角度若<b>恰好</b>落在 22.5° 栅格上则直接返回对应枚举常量（对枚举输入彻底保持引用语义）。
 * <p>
 * 缓存放在接口里（而不是 mixin 的静态字段）是为了不依赖 mixin 静态字段的合并行为。
 */
public interface RailAngleExtra {

    /** 幻影角度实例缓存（键 = 归一化到 [0,360) 的 float 度数）。 */
    ConcurrentHashMap<Float, RailAngle> PHANTOM_CACHE = new ConcurrentHashMap<>();

    RailAngle _fromDegrees(double degrees);

    RailAngle _fromRadians(double radians);

    void setRadians(double radians);

    /**
     * 以度为单位构造任意角度 RailAngle（幻影常量）。
     */
    static RailAngle fromDegrees(double degrees) {
        return ((RailAngleExtra) (Object) RailAngle.S)._fromDegrees(degrees);
    }

    /**
     * 以弧度为单位构造任意角度 RailAngle（幻影常量）。
     */
    static RailAngle fromRadians(double radians) {
        return ((RailAngleExtra) (Object) RailAngle.S)._fromRadians(radians);
    }

    /**
     * 归一化角度到 [-180, 180)（与 MTR {@code RailAngle} 构造函数内部 normalizeAngle 一致）。
     */
    static double normalizeDegrees(double degrees) {
        double result = degrees % 360D;
        if (result >= 180D) result -= 360D;
        if (result < -180D) result += 360D;
        return result;
    }

    /**
     * 归一化角度到 [0, 180)（万向节点角度存储约定：0=东, 90=南, 180 视作 0）。
     */
    static double normalizeNodeDegrees(double degrees) {
        double result = degrees % 180D;
        if (result < 0D) result += 180D;
        return result;
    }

    /**
     * 是否为伪造的幻影角度（非 16 个枚举常量之一）。
     */
    static boolean isFabricated(RailAngle angle) {
        return angle != null && angle.ordinal() < 0;
    }

    /**
     * 取反方向的角度。
     * <p>
     * {@code RailAngle.getOpposite()} 已被 {@link com.fangsu.mixin.RailAngleMixin} 重写为
     * 对幻影角度按弧度取反，此处仅是语义清晰的静态入口，供连接器与界面代码使用。
     */
    static RailAngle opposite(RailAngle angle) {
        return angle == null ? null : angle.getOpposite();
    }

    /**
     * 两个角度是否平行（同轴，容差内），对幻影角度同样有效。
     */
    static boolean isParallel(RailAngle a, RailAngle b) {
        return a != null && b != null && a.isParallel(b);
    }
}
