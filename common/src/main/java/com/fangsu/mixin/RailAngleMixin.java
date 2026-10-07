package com.fangsu.mixin;

import com.fangsu.mtr.RailAngleExtra;
import mtr.data.RailAngle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 让 MTR3 的 {@link RailAngle} 枚举支持任意角度（万向节点核心）。
 * <p>
 * 自 NTE mtrsteamloco {@code mixin/RailAngleMixin.java} 移植（MIT License, Copyright (c) 2022-present Zbx1425），
 * 并按 MTR4 版 FangSu {@code AngleMixin}（同一问题的成熟实现）补齐了两处 ANTE 蓝本没有的东西 ——
 * <b>幻影实例缓存</b>与<b>落在 22.5° 栅格时返回枚举常量</b>。原因是 MTR3 的 {@code mtr.path.PathFinder}
 * 用引用比较判断角度：
 * <ul>
 *   <li>{@code PathFinder.java:184} {@code if (tempAngle == expectedAngle)} —— 民航弧线寻路靠它判停。
 *       若 {@code add()} 每次都返回新对象，该条件永不成立，循环会走满 {@code RailAngle.values().length}
 *       （16 步）而不是在目标角度中断，民航路径直接错。</li>
 *   <li>{@code PathFinder.java:161} {@code rail.facingStart != newDirection.getOpposite()} ——
 *       用于排除“原路返回”的反向轨。若 {@code getOpposite()} 每次新建对象，
 *       反向轨排除失效，寻路会多出回溯分支。</li>
 * </ul>
 * 思路：MTR3 的 {@code RailAngle} 是 16 个 22.5° 常量的枚举，无法表达任意角度。
 * 本 mixin 通过 {@code @Invoker("<init>")} 直接调用枚举私有构造器，以 {@code ordinal = -1}
 * 伪造出“幻影常量”，再用 {@link #setRadians(double)} 覆盖 sin/cos/tan/halfTan，
 * 使其承载精确的任意角度。{@code Rail} 的几何计算只消费这些连续三角函数值，故完全可用。
 * <p>
 * FangSu 相对 ANTE 蓝本的适配（均按 3.2.2 实测 API 校正）：
 * <ul>
 *   <li>3.2.2 的方法名是 {@code sub(RailAngle)}（ANTE 蓝本写的 {@code subtract} 在 3.2.2 中并不存在），
 *       而 {@code Rail.java:146} 的构造器正是调用 {@code newFacingEnd.sub(newFacingStart)}；
 *       若不重写，任意角度会被 {@code fromAngle} 量化到 22.5° 栅格、几何退化，故必须重写</li>
 *   <li>3.2.2 的 {@code RailAngle} 含 {@code halfTan} 字段（{@code Rail.java:153/185} 使用），
 *       一并按弧度精确重算</li>
 *   <li>{@link #sub(RailAngle)} 的结果<b>不进缓存</b>：它必须把弧度归一化到 (-180,180]，
 *       而 {@code Rail} 用 {@code angleDifference.angleRadians} 的<b>符号</b>挑选几何分支
 *       （见 {@code Rail.java:169} 的 {@code Math.signum} 比较）。同键实例可能由
 *       {@code fromDegrees(315°)} 创建（弧度 = +315°），与 {@code sub} 所需的 -45° 语义冲突，
 *       复用会翻转符号、走进错误的几何分支</li>
 *   <li>{@code isParallel} 对“双方都是真实枚举常量”的情形保持 MTR 原有的枚举表/引用语义，
 *       其余情形按弧度值比较（容差 1e-6 弧度）</li>
 * </ul>
 * 注：这里沿用 ANTE 的“同名方法直接覆盖”写法（不加 {@code @Overwrite}），
 * 以便与同样覆写这些方法的 NTE/ANTE 共存时行为一致。
 */
@Mixin(value = RailAngle.class, remap = false)
public abstract class RailAngleMixin implements RailAngleExtra {

    // 注意：这里必须【不】写 Java 关键字 final —— 无构造器赋值的 final 实例字段会被 javac 拒绝
    // （“变量未在默认构造器中初始化”）；final 语义由 Mixin 的 @Final 注解表达，
    // 需要写入的字段再加 @Mutable 让 Mixin 清掉目标字段的 ACC_FINAL。
    @Shadow(remap = false)
    @Final
    public float angleDegrees;

    @Shadow(remap = false)
    @Final
    @Mutable
    public double angleRadians, sin, cos, tan;

    @Shadow(remap = false)
    @Final
    @Mutable
    public double halfTan;

    /**
     * 直接调用枚举私有构造器 {@code RailAngle(String, int, float)} 伪造实例。
     */
    @Invoker(value = "<init>")
    private static RailAngle create(String name, int ordinal, float angleDegrees) {
        throw new IllegalStateException();
    }

    /** 归一化到 [0,360)，作为缓存键与“是否落在 22.5° 栅格”的判据。 */
    private static float normalizeDegreesTo360(double degrees) {
        final double normalized = degrees % 360D;
        return (float) (normalized < 0D ? normalized + 360D : normalized);
    }

    /**
     * 角度恰好落在 22.5° 栅格上时返回对应枚举常量（否则 null）。
     * <p>
     * 枚举常量的 {@code angleDegrees} 字段是构造器 {@code normalizeAngle} 之后的 [-180,180) 值
     * （如 NEE=337.5° 的字段是 -22.5），因此比较前要把它映射回 [0,360)，
     * 否则 337.5° 匹配不到 NEE 而错误地造出幻影实例，
     * 使“同方向”出现“枚举/幻影”两个不同对象、引用比较再次断裂。
     */
    private static RailAngle snapToEnumIfExact(float degrees) {
        for (final RailAngle candidate : RailAngle.values()) {
            float field = candidate.angleDegrees;
            if (field < 0F) {
                field += 360F;
            }
            if (field == degrees) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public RailAngle _fromDegrees(double degrees) {
        final float key = normalizeDegreesTo360(degrees);
        final RailAngle exact = snapToEnumIfExact(key);
        if (exact != null) {
            return exact;
        }
        final RailAngle cached = RailAngleExtra.PHANTOM_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        final RailAngle result = create("D" + key, -1, (float) RailAngleExtra.normalizeDegrees(degrees));
        // 弧度保留原始输入的 double 精度（供 getOpposite/add/sub 还原精确角度）
        setRadians(result, Math.toRadians(degrees));
        RailAngleExtra.PHANTOM_CACHE.put(key, result);
        return result;
    }

    @Override
    public RailAngle _fromRadians(double radians) {
        // 与 _fromDegrees 共用同一键空间：fromRadians(toRadians(x)) 与 fromDegrees(x) 必须拿到同一实例
        return _fromDegrees(Math.toDegrees(radians));
    }

    @Override
    public void setRadians(double angleRadians) {
        this.angleRadians = angleRadians;
        this.sin = Math.sin(angleRadians);
        this.cos = Math.cos(angleRadians);
        this.tan = Math.tan(angleRadians);
        this.halfTan = Math.tan(angleRadians / 2D);
    }

    private static void setRadians(RailAngle angle, double angleRadians) {
        ((RailAngleMixin) (Object) angle).setRadians(angleRadians);
    }

    /**
     * 反向角度。
     * <p>
     * 用「由弧度还原的 double 度数 + 180」再走 {@link RailAngleExtra#fromDegrees}，
     * 保证取两次反向能回到<b>同一个实例</b>（缓存键按 [0,360) 归一化），
     * 从而让 {@code PathFinder.java:161} 的引用比较成立；落在栅格上时直接返回枚举常量。
     */
    public RailAngle getOpposite() {
        return RailAngleExtra.fromDegrees(Math.toDegrees(angleRadians) + 180D);
    }

    /** 角度相加（走度数域 + 缓存 + 栅格吸附，保证民航寻路的 {@code ==} 判停成立）。 */
    public RailAngle add(RailAngle other) {
        return RailAngleExtra.fromDegrees(Math.toDegrees(angleRadians) + Math.toDegrees(other.angleRadians));
    }

    /**
     * 角度相减（{@code Rail.java:146} 使用）。
     * <p>
     * <b>刻意不进缓存</b>，并把弧度归一化到 [-180,180)：{@code Rail} 依赖该弧度的符号挑选几何分支，
     * 复用缓存实例（其弧度可能是 +315° 这类同角异号表示）会翻转符号、走进错误分支。
     */
    public RailAngle sub(RailAngle other) {
        final double difference360 = normalizeDegreesTo360(Math.toDegrees(angleRadians) - Math.toDegrees(other.angleRadians));
        final RailAngle exact = snapToEnumIfExact((float) difference360);
        if (exact != null) {
            return exact;
        }
        final double difference180 = difference360 >= 180D ? difference360 - 360D : difference360;
        final RailAngle result = create("S" + difference360, -1, (float) RailAngleExtra.normalizeDegrees(difference360));
        setRadians(result, Math.toRadians(difference180));
        return result;
    }

    /**
     * 是否平行（同轴）。
     * <p>
     * 双方都是真实枚举常量时保持 MTR 原始的引用/枚举表语义（{@code other == getOpposite()}）；
     * 只要有一方是幻影角度则按弧度值判断，容差 1e-6 弧度。
     */
    public boolean isParallel(RailAngle other) {
        if (other == null) {
            return false;
        }
        if (other == (Object) this) {
            return true;
        }
        if (other.ordinal() >= 0 && ((RailAngle) (Object) this).ordinal() >= 0) {
            return other == getOpposite();
        }
        final double diff = Math.abs(angleRadians - other.angleRadians) % Math.PI;
        return Math.min(diff, Math.PI - diff) < 1e-6;
    }
}
