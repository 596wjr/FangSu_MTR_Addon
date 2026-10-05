package com.fangsu.mappings.rail;

/**
 * 版本无关的轨道几何内核（纯数学）。
 * <p>
 * <b>禁止 import 任何 {@code org.mtr.*} / {@code mtr.*} / MC 类型</b>，需在两个工程保持逐字节一致
 * （校验见 {@code devtools/check-synced-files.ps1}）。适配层（MTR4 的 {@code RailMath} 子类、
 * MTR3 的 {@code Rail} mixin）只做「喂参数、取结果」。
 * <p>
 * 水平代数与竖直剖面<b>逐行对齐 MTR 4.0.5 的 {@code org.mtr.core.data.RailMath}</b>：
 * <ul>
 *   <li>三个分支：两角平行且共线（单段）、两角平行且错位（双圆）、以及「直线段优先 /圆弧优先」两解</li>
 *   <li>退化情形与 4.0.5 一样置零（{@link #isValid()} 随之为 false）</li>
 *   <li>{@code getPositionY} 的 QUADRATIC / TWO_RADII / CABLE 三分支照抄（CABLE 的常量在内核里同样内联）</li>
 *   <li>{@code getMaxVerticalRadius} 不做除零保护，与 4.0.5 行为一致（平轨得到 +Infinity）</li>
 * </ul>
 * 相对 4.0.5 的<b>扩展</b>（仅在附加姿态非默认时生效，附加姿态为默认时输出与原版逐位一致）：
 * <ol>
 *   <li><b>双精度锚点</b>：端点用 {@code 方块坐标 + 节点偏移}（{@code +0.5} 仍留在位置公式里，
 *       因此偏移为 0 时与 4.0.5 完全一致，且代数对平移是等变的 → 偏移即整体平移）</li>
 *   <li><b>纵坡 Hermite</b>：任一俯仰角非零时，竖直剖面改为三次 Hermite，端点高度与端点切线
 *       （{@code tan(俯仰角)}）同时满足，4 个自由度恰好被 2 高度 + 2 斜率唯一确定，不超定</li>
 *   <li><b>滚转抬升（ANTE 语义）</b>：中心线抬升 {@code 半轨距 · |sin(滚转角)|}，使内轨保持标高</li>
 *   <li><b>闭式参数反解</b> {@link #parameterAt(double, double)}：供适配层把绘制出的截面点映射回
 *       滚转剖面（轨面按滚转倾斜时必需）</li>
 * </ol>
 * <b>许可与归属</b>：水平代数与竖直剖面<b>实质性移植</b>自 Transport-Simulation-Core
 * （MIT License，Copyright (c) 2023 Jonathan Ho），对应 MTR 4.0.5 的
 * {@code org.mtr.core.data.RailMath}；滚转语义（半轨距抬升中心线、内轨保持标高）参考
 * ANTE / mtr-ante-alpha（MIT License，Copyright (c) 2022-present Zbx1425）。
 * MIT 许可要求保留版权声明：请勿在删除上述归属说明的前提下再分发。
 */
public final class RailGeometryCore {

    // ==================== 形状模式（由适配层把 MTR 的 Rail.Shape 映射为 int） ====================

    /** MTR {@code Rail.Shape.QUADRATIC}。 */
    public static final int SHAPE_QUADRATIC = 0;
    /** MTR {@code Rail.Shape.TWO_RADII}。 */
    public static final int SHAPE_TWO_RADII = 1;
    /** MTR {@code Rail.Shape.CABLE}。 */
    public static final int SHAPE_CABLE = 2;

    private static final double ACCEPT_THRESHOLD = 1E-4;
    private static final int CABLE_CURVATURE_SCALE = 1000;
    private static final int MAX_CABLE_DIP = 8;

    /**
     * 4.0.5 {@code renderSegment} 循环上界的偏移量：实测 jar 里是<b>字面量 0.1</b>
     * （反编译证据：{@code count + increment - 0.1}，其中 0.1 与构造器传给 {@code render} 的 interval
     * 共用同一个常量池项）。更高的 MTR 源码里该处是 {@code 0.001}。
     * <p>
     * 这个差别不是无关紧要的：它决定采样是否覆盖到轨道末端。当 {@code increment < 0.1} 时末端采样被跳过，
     * 末端 y 不再进入包围盒（实测会差 1 格）。必须照抄 4.0.5。
     */
    private static final double RENDER_SEGMENT_LOOP_MARGIN = 0.1D;

    // ==================== 包围盒（与 4.0.5 同名同义，供空间索引/剔除） ====================

    public final long minX;
    public final long minY;
    public final long minZ;
    public final long maxX;
    public final long maxY;
    public final long maxZ;

    // ==================== 输入（双精度锚点 + 角度） ====================

    private final double xStart;
    private final double zStart;
    private final double xEnd;
    private final double zEnd;
    private final double angle1Radians;
    private final double angle2Radians;
    private final int shape;
    private final double yStart;
    private final double yEnd;

    // ==================== 水平几何（与 4.0.5 同构） ====================

    private final double h1;
    private final double k1;
    private final double r1;
    private final double tStart1;
    private final double tEnd1;
    private final double h2;
    private final double k2;
    private final double r2;
    private final double tStart2;
    private final double tEnd2;
    private final boolean reverseT1;
    private final boolean reverseT2;
    private final boolean isStraight1;
    private final boolean isStraight2;

    private final boolean valid;

    // ==================== 竖直扩展 ====================

    private final double verticalRadius;
    private final double slope1;
    private final double slope2;
    private final boolean hermite;
    private final RailRollProfile rollProfile;
    private final double halfGauge;

    /**
     * 无附加姿态（等价 4.0.5）的构造。
     *
     * @param anchor1     端点 1 的 {@code {x, y, z}}，其中 x/z 为「方块坐标 + 节点偏移」（不含 +0.5）
     * @param anchor2     端点 2 同上
     * @param angle1Radians 端点 1 角度（弧度）
     * @param angle2Radians 端点 2 角度（弧度）
     * @param shape       {@link #SHAPE_QUADRATIC} / {@link #SHAPE_TWO_RADII} / {@link #SHAPE_CABLE}
     * @param verticalRadius 竖曲线半径（0 表示用形状自带的默认剖面）
     */
    public RailGeometryCore(double[] anchor1, double[] anchor2, double angle1Radians, double angle2Radians, int shape, double verticalRadius) {
        this(anchor1, anchor2, angle1Radians, angle2Radians, shape, verticalRadius, 0.0D, 0.0D, RailRollProfile.NONE, RailPoseExtra.DEFAULT_HALF_GAUGE);
    }

    /**
     * 带附加姿态的构造（本内核完整形态）。
     *
     * @param pitch1Radians 端点 1 的纵坡角（弧度），剖面导数 = {@code tan(该值)}
     * @param pitch2Radians 端点 2 同上
     * @param rollProfile   滚转剖面（归一化位置为键），可为 {@link RailRollProfile#NONE}
     * @param halfGauge     半轨距（米），用于滚转抬升
     */
    public RailGeometryCore(
            double[] anchor1, double[] anchor2, double angle1Radians, double angle2Radians, int shape, double verticalRadius,
            double pitch1Radians, double pitch2Radians, RailRollProfile rollProfile, double halfGauge
    ) {
        this.xStart = anchor1[0];
        this.zStart = anchor1[2];
        this.xEnd = anchor2[0];
        this.zEnd = anchor2[2];
        this.yStart = anchor1[1];
        this.yEnd = anchor2[1];
        this.angle1Radians = angle1Radians;
        this.angle2Radians = angle2Radians;
        this.shape = shape;
        this.slope1 = Math.tan(pitch1Radians);
        this.slope2 = Math.tan(pitch2Radians);
        this.hermite = slope1 != 0.0D || slope2 != 0.0D;
        this.rollProfile = rollProfile == null ? RailRollProfile.NONE : rollProfile;
        this.halfGauge = halfGauge > 0.0D ? halfGauge : RailPoseExtra.DEFAULT_HALF_GAUGE;

        // ---- 角度：忠实还原 MTR Angle 的「两套数值」----
        // 枚举 Angle 同时持有 float 角度（angleDegrees，归一化到 [-180,180)）与 double 弧度（angleRadians）：
        // sin/cos/tan/halfTan 用弧度；add/sub/isParallel/getOpposite 走 float 角度域。两者不可混用，
        // 例如 angle1.add(angle1).cos 是「float 角度相加、归一化、再转弧度取 cos」，而不是 cos(2 * 弧度)。
        final float degrees1 = normalize180f((float) Math.toDegrees(angle1Radians));
        final float degrees2 = normalize180f((float) Math.toDegrees(angle2Radians));

        final double cos1 = Math.cos(angle1Radians);
        final double sin1 = Math.sin(angle1Radians);
        final double cos2 = Math.cos(angle2Radians);
        final double sin2 = Math.sin(angle2Radians);
        // 4.0.5 对 vecDifference 做 rotateY 时把角度截断成 float（Vector.rotateY((float) angle1.angleRadians)），
        // 这个 1e-7 量级的截断会传递到 deltaSide/deltaForward，必须复刻。
        final double rotateCos = Math.cos((float) angle1Radians);
        final double rotateSin = Math.sin((float) angle1Radians);

        final double differenceX = xEnd - xStart;
        final double differenceZ = zEnd - zStart;
        final double deltaSide = differenceX * rotateCos + differenceZ * rotateSin;
        final double deltaForward = differenceZ * rotateCos - differenceX * rotateSin;

        double localH1 = 0.0D;
        double localK1 = 0.0D;
        double localR1 = 0.0D;
        double localTStart1 = 0.0D;
        double localTEnd1 = 0.0D;
        double localH2 = 0.0D;
        double localK2 = 0.0D;
        double localR2 = 0.0D;
        double localTStart2 = 0.0D;
        double localTEnd2 = 0.0D;
        boolean localReverseT1 = false;
        boolean localReverseT2 = false;
        boolean localIsStraight1 = true;
        boolean localIsStraight2 = true;

        if (isParallel(degrees1, degrees2)) {
            if (Math.abs(deltaForward) < ACCEPT_THRESHOLD) {
                // 1.a 两角平行且共线 -> 单段
                localH1 = cos1;
                localK1 = sin1;
                if (Math.abs(localH1) >= 0.5D && Math.abs(localK1) >= 0.5D) {
                    localR1 = (localH1 * zStart - localK1 * xStart) / localH1 / localH1;
                    localTStart1 = xStart / localH1;
                    localTEnd1 = xEnd / localH1;
                } else {
                    final double divisor = Math.cos(radiansOf(normalize180f(degrees1 + degrees1)));
                    localR1 = (localH1 * zStart - localK1 * xStart) / divisor;
                    localTStart1 = (localH1 * xStart - localK1 * zStart) / divisor;
                    localTEnd1 = (localH1 * xEnd - localK1 * zEnd) / divisor;
                }
                localReverseT1 = localTStart1 > localTEnd1;
                localIsStraight1 = true;
                localIsStraight2 = true;
            } else {
                // 1.b 两角平行但错位 -> 双圆
                if (Math.abs(deltaSide) > ACCEPT_THRESHOLD) {
                    final double radius = (deltaForward * deltaForward + deltaSide * deltaSide) / (4.0D * deltaForward);
                    localR1 = Math.abs(radius);
                    localR2 = localR1;
                    localH1 = xStart - radius * sin1;
                    localK1 = zStart + radius * cos1;
                    localH2 = xEnd - radius * sin2;
                    localK2 = zEnd + radius * cos2;
                    localReverseT1 = (deltaForward < 0.0D) != (deltaSide < 0.0D);
                    localReverseT2 = !localReverseT1;
                    localTStart1 = getTBounds(xStart, localH1, zStart, localK1, localR1);
                    localTEnd1 = getTBounds(xStart + differenceX / 2.0D, localH1, zStart + differenceZ / 2.0D, localK1, localR1, localTStart1, localReverseT1);
                    localTStart2 = getTBounds(xStart + differenceX / 2.0D, localH2, zStart + differenceZ / 2.0D, localK2, localR2);
                    localTEnd2 = getTBounds(xEnd, localH2, zEnd, localK2, localR2, localTStart2, localReverseT2);
                    localIsStraight1 = false;
                    localIsStraight2 = false;
                }
                // else: 退化，全部保持 0 / true（与 4.0.5 一致）
            }
        } else {
            // 3. 两角不平行：直线段优先 / 圆弧优先
            // getOpposite() 走 float 角度域（+180 再归一化），不是「弧度 + PI」
            final boolean flip1 = deltaSide < -ACCEPT_THRESHOLD;
            final float newDegrees1 = flip1 ? normalize180f(degrees1 + 180.0F) : degrees1;
            final double newAngle1Radians = flip1 ? radiansOf(newDegrees1) : angle1Radians;
            final boolean flip2 = (cos2 * differenceX + sin2 * differenceZ) < -ACCEPT_THRESHOLD;
            final float newDegrees2 = flip2 ? normalize180f(degrees2 + 180.0F) : degrees2;
            final double newAngle2Radians = flip2 ? radiansOf(newDegrees2) : angle2Radians;
            final double angleForward = Math.atan2(deltaForward, deltaSide);
            // MTR: newAngle2.sub(newAngle1) —— float 角度域相减再归一化转弧度。
            // 操作数顺序不可颠倒：该差值的「符号」决定走哪条几何分支。
            final double angleDifference = radiansOf(normalize180f(newDegrees2 - newDegrees1));

            if (Math.signum(angleForward) == Math.signum(angleDifference)) {
                final double absAngleForward = Math.abs(angleForward);
                final double newCos1 = Math.cos(newAngle1Radians);
                final double newSin1 = Math.sin(newAngle1Radians);
                final double newCos2 = Math.cos(newAngle2Radians);
                final double newSin2 = Math.sin(newAngle2Radians);
                final double cosDifference = Math.cos(angleDifference);
                final double sinDifference = Math.sin(angleDifference);
                final double tanDifference = Math.tan(angleDifference);
                final double halfTanDifference = Math.tan(angleDifference / 2.0D);

                if (absAngleForward - Math.abs(angleDifference / 2.0D) < ACCEPT_THRESHOLD) {
                    // 直线段优先
                    final double offsetSide = Math.abs(deltaForward / halfTanDifference);
                    final double remainingSide = deltaSide - offsetSide;
                    final double deltaXEnd = xStart + remainingSide * newCos1;
                    final double deltaZEnd = zStart + remainingSide * newSin1;
                    localH1 = newCos1;
                    localK1 = newSin1;
                    if (Math.abs(localH1) >= 0.5D && Math.abs(localK1) >= 0.5D) {
                        localR1 = (localH1 * zStart - localK1 * xStart) / localH1 / localH1;
                        localTStart1 = xStart / localH1;
                        localTEnd1 = deltaXEnd / localH1;
                    } else {
                        final double divisor = Math.cos(radiansOf(normalize180f(newDegrees1 + newDegrees1)));
                        localR1 = (localH1 * zStart - localK1 * xStart) / divisor;
                        localTStart1 = (localH1 * xStart - localK1 * zStart) / divisor;
                        localTEnd1 = (localH1 * deltaXEnd - localK1 * deltaZEnd) / divisor;
                    }
                    localIsStraight1 = true;
                    localReverseT1 = localTStart1 > localTEnd1;
                    final double radius = deltaForward / (1.0D - cosDifference);
                    localR2 = Math.abs(radius);
                    localH2 = deltaXEnd - radius * newSin1;
                    localK2 = deltaZEnd + radius * newCos1;
                    localReverseT2 = deltaForward < 0.0D;
                    localTStart2 = getTBounds(deltaXEnd, localH2, deltaZEnd, localK2, localR2);
                    localTEnd2 = getTBounds(xEnd, localH2, zEnd, localK2, localR2, localTStart2, localReverseT2);
                    localIsStraight2 = false;
                } else if (absAngleForward - Math.abs(angleDifference) < ACCEPT_THRESHOLD) {
                    // 圆弧优先
                    final double crossSide = deltaForward / tanDifference;
                    final double remainingSide = (deltaSide - crossSide) * (1.0D + cosDifference);
                    final double remainingForward = (deltaSide - crossSide) * sinDifference;
                    final double deltaXEnd = xStart + remainingSide * newCos1 - remainingForward * newSin1;
                    final double deltaZEnd = zStart + remainingSide * newSin1 + remainingForward * newCos1;
                    final double radius = (deltaSide - deltaForward / tanDifference) / halfTanDifference;
                    localR1 = Math.abs(radius);
                    localH1 = xStart - radius * newSin1;
                    localK1 = zStart + radius * newCos1;
                    localIsStraight1 = false;
                    localReverseT1 = deltaForward < 0.0D;
                    localTStart1 = getTBounds(xStart, localH1, zStart, localK1, localR1);
                    localTEnd1 = getTBounds(deltaXEnd, localH1, deltaZEnd, localK1, localR1, localTStart1, localReverseT1);
                    localH2 = newCos2;
                    localK2 = newSin2;
                    if (Math.abs(localH2) >= 0.5D && Math.abs(localK2) >= 0.5D) {
                        localR2 = (localH2 * deltaZEnd - localK2 * deltaXEnd) / localH2 / localH2;
                        localTStart2 = deltaXEnd / localH2;
                        localTEnd2 = xEnd / localH2;
                    } else {
                        final double divisor = Math.cos(radiansOf(normalize180f(newDegrees2 + newDegrees2)));
                        localR2 = (localH2 * deltaZEnd - localK2 * deltaXEnd) / divisor;
                        localTStart2 = (localH2 * deltaXEnd - localK2 * deltaZEnd) / divisor;
                        localTEnd2 = (localH2 * xEnd - localK2 * zEnd) / divisor;
                    }
                    localIsStraight2 = true;
                    localReverseT2 = localTStart2 > localTEnd2;
                }
                // else: 超出可用范围，全部保持 0 / true（与 4.0.5 一致）
            }
            // else: 符号相反，全部保持 0 / true（与 4.0.5 一致）
        }

        this.h1 = localH1;
        this.k1 = localK1;
        this.r1 = localR1;
        this.tStart1 = localTStart1;
        this.tEnd1 = localTEnd1;
        this.h2 = localH2;
        this.k2 = localK2;
        this.r2 = localR2;
        this.tStart2 = localTStart2;
        this.tEnd2 = localTEnd2;
        this.reverseT1 = localReverseT1;
        this.reverseT2 = localReverseT2;
        this.isStraight1 = localIsStraight1;
        this.isStraight2 = localIsStraight2;
        this.valid = h1 != 0.0D || k1 != 0.0D || h2 != 0.0D || k2 != 0.0D || r1 != 0.0D || r2 != 0.0D
                || tStart1 != 0.0D || tStart2 != 0.0D || tEnd1 != 0.0D || tEnd2 != 0.0D;

        // 4.0.5: verticalRadius = min(verticalRadius, getMaxVerticalRadius())，不做除零保护
        this.verticalRadius = Math.min(verticalRadius, getMaxVerticalRadius());

        // ---- 包围盒：与 4.0.5 一样按 interval=0.1、offsetRadius=0 采样中心线 ----
        final double[] bounds = new double[]{
                Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE
        };
        sampleSegment(h1, k1, r1, tStart1, tEnd1, 0.0D, 0.1D, reverseT1, isStraight1, bounds);
        sampleSegment(h2, k2, r2, tStart2, tEnd2, Math.abs(tEnd1 - tStart1), 0.1D, reverseT2, isStraight2, bounds);
        minX = bounds[0] > bounds[3] ? 0L : (long) Math.floor(bounds[0]);
        minY = bounds[1] > bounds[4] ? 0L : (long) Math.floor(bounds[1]);
        minZ = bounds[2] > bounds[5] ? 0L : (long) Math.floor(bounds[2]);
        maxX = bounds[3] < bounds[0] ? 0L : (long) Math.ceil(bounds[3]);
        maxY = bounds[4] < bounds[1] ? 0L : (long) Math.ceil(bounds[4]);
        maxZ = bounds[5] < bounds[2] ? 0L : (long) Math.ceil(bounds[5]);
    }

    // ==================== 基础量 ====================

    public double getLength() {
        return getLength1() + getLength2();
    }

    public double getLength1() {
        return Math.abs(tEnd1 - tStart1);
    }

    public double getLength2() {
        return Math.abs(tEnd2 - tStart2);
    }

    public double getHorizontalRadius1() {
        return isStraight1 ? 0.0D : Math.abs(r1);
    }

    public double getHorizontalRadius2() {
        return isStraight2 ? 0.0D : Math.abs(r2);
    }

    public double getVerticalRadius() {
        return verticalRadius;
    }

    public int getShapeMode() {
        return shape;
    }

    public boolean isValid() {
        return valid;
    }

    /** 是否启用了纵坡 Hermite 剖面。 */
    public boolean isHermite() {
        return hermite;
    }

    public RailRollProfile getRollProfile() {
        return rollProfile;
    }

    public double getHalfGauge() {
        return halfGauge;
    }

    /** 4.0.5 的 {@code getMaxVerticalRadius()}：平轨（高差为 0）会得到 +Infinity，不做保护。 */
    public double getMaxVerticalRadius() {
        final double length = getLength();
        final double height = yEnd - yStart;
        return Math.floor((length * length + height * height) * 100.0D / Math.abs(4.0D * height)) / 100.0D;
    }

    // ==================== 位置 ====================

    /**
     * 中心线位置。
     *
     * @param rawValue 沿轨道的参数（0 .. {@link #getLength()}）
     * @param reverse  是否反转参数方向（与 4.0.5 的 {@code getPosition(rawValue, reverse)} 同义）
     * @return {@code {x, y, z}}
     */
    public double[] getPosition(double rawValue, boolean reverse) {
        final double count1 = getLength1();
        final double count2 = getLength2();
        final double clampedValue = clampSafe(rawValue, 0.0D, count1 + count2);
        final double value = reverse ? count1 + count2 - clampedValue : clampedValue;
        final double y = getPositionY(value);

        final double[] xz;
        if (value <= count1) {
            xz = getPositionXZ(h1, k1, r1, (reverseT1 ? -1.0D : 1.0D) * value + tStart1, 0.0D, isStraight1);
        } else {
            xz = getPositionXZ(h2, k2, r2, (reverseT2 ? -1.0D : 1.0D) * (value - count1) + tStart2, 0.0D, isStraight2);
        }
        return new double[]{xz[0], y, xz[2]};
    }

    /**
     * 横向偏移处的截面点（供适配层自绘轨面/带滚转的截面），y 取该参数的竖直剖面值。
     *
     * @param radiusOffset 横向偏移（与 4.0.5 {@code render(..., offsetRadius1, offsetRadius2)} 的语义一致）
     */
    public double[] getOffsetPosition(double value, double radiusOffset) {
        final double count1 = getLength1();
        final double clamped = clampSafe(value, 0.0D, getLength());
        final double[] xz;
        if (clamped <= count1) {
            xz = getPositionXZ(h1, k1, r1, (reverseT1 ? -1.0D : 1.0D) * clamped + tStart1, radiusOffset, isStraight1);
        } else {
            xz = getPositionXZ(h2, k2, r2, (reverseT2 ? -1.0D : 1.0D) * (clamped - count1) + tStart2, radiusOffset, isStraight2);
        }
        return new double[]{xz[0], getPositionY(clamped), xz[2]};
    }

    /**
     * 竖直剖面（含滚转抬升）。附加姿态为默认时与 4.0.5 逐位一致。
     */
    public double getPositionY(double value) {
        return basePositionY(value) + rollLift(value);
    }

    /**
     * 滚转抬升（ANTE 语义）：{@code 半轨距 · |sin(滚转角(value))|}。
     * 内轨保持标高、中心线抬升，列车高度随 {@link #getPosition} 自动跟随。
     */
    public double rollLift(double value) {
        if (rollProfile.isZero()) {
            return 0.0D;
        }
        return Math.abs(halfGauge * Math.sin(getRollRadians(value)));
    }

    /** 取指定参数处的滚转角（弧度）。剖面键为归一化位置。 */
    public double getRollRadians(double value) {
        return rollProfile.getRadians(fractionAt(value));
    }

    /**
     * 取指定参数处的滚转角（弧度），并按 {@code reverse} 镜像剖面坐标
     * （与 ANTE 的 {@code if (reversed) value = length - value} 同义；符号翻转属于渲染帧约定，由适配层处理）。
     */
    public double getRollRadians(double value, boolean reverse) {
        return getRollRadians(reverse ? getLength() - value : value);
    }

    /** 归一化位置（0..1，长度<=0 时为 0）。 */
    public double fractionAt(double value) {
        final double length = getLength();
        if (length <= 0.0D) {
            return 0.0D;
        }
        return clampSafe(value, 0.0D, length) / length;
    }

    // ==================== 参数反解（轨面滚转必需） ====================

    /**
     * 由水平坐标闭式反解轨道参数：把绘制出的截面点映射回滚转剖面用。
     * <p>
     * 直线段用投影闭式解；圆弧段用 {@code t = r·atan2(z - k, x - h)} 并在本段 t 区间内挑选候选
     * （区间外则夹到端点）。两段都投影后取世界距离更近的一段，确定性、O(1)。
     *
     * @return 沿轨道的参数（0 .. {@link #getLength()}）
     */
    public double parameterAt(double x, double z) {
        final double candidate1 = projectOnSegment(h1, k1, r1, tStart1, tEnd1, reverseT1, isStraight1, 0.0D, x, z);
        final double candidate2 = projectOnSegment(h2, k2, r2, tStart2, tEnd2, reverseT2, isStraight2, getLength1(), x, z);
        final double length = getLength();

        if (Double.isNaN(candidate1)) {
            return clampSafe(candidate2, 0.0D, length);
        }
        if (Double.isNaN(candidate2)) {
            return clampSafe(candidate1, 0.0D, length);
        }

        final double[] position1 = getPosition(candidate1, false);
        final double[] position2 = getPosition(candidate2, false);
        final double distance1 = squaredDistance(position1[0], position1[2], x, z);
        final double distance2 = squaredDistance(position2[0], position2[2], x, z);
        return clampSafe(distance2 < distance1 ? candidate2 : candidate1, 0.0D, length);
    }

    // ==================== 内部：位置公式（照抄 4.0.5 getPositionXZ） ====================

    private static double[] getPositionXZ(double h, double k, double r, double t, double radiusOffset, boolean isStraight) {
        if (isStraight) {
            return new double[]{
                    h * t + k * ((Math.abs(h) >= 0.5D && Math.abs(k) >= 0.5D ? 0.0D : r) + radiusOffset) + 0.5D,
                    0.0D,
                    k * t + h * (r - radiusOffset) + 0.5D
            };
        } else {
            return new double[]{
                    h + (r + radiusOffset) * Math.cos(t / r) + 0.5D,
                    0.0D,
                    k + (r + radiusOffset) * Math.sin(t / r) + 0.5D
            };
        }
    }

    // ==================== 内部：竖直剖面（照抄 4.0.5 getPositionY） ====================

    private double basePositionY(double value) {
        if (hermite) {
            return hermitePositionY(value);
        }
        if (yStart == yEnd) {
            return yStart;
        }

        final double length = getLength();

        switch (shape) {
            case SHAPE_TWO_RADII:
                if (verticalRadius <= 0.0D) {
                    return (value / length) * (yEnd - yStart) + yStart;
                }
                final double vTheta = getVTheta();
                final double curveLength = Math.sin(vTheta) * verticalRadius;
                final double curveHeight = (1.0D - Math.cos(vTheta)) * verticalRadius;
                final int sign = yStart < yEnd ? 1 : -1;
                if (value < curveLength) {
                    return sign * (verticalRadius - Math.sqrt(verticalRadius * verticalRadius - value * value)) + yStart;
                } else if (value > length - curveLength) {
                    final double remaining = length - value;
                    return -sign * (verticalRadius - Math.sqrt(verticalRadius * verticalRadius - remaining * remaining)) + yEnd;
                } else {
                    return sign * (((value - curveLength) / (length - 2.0D * curveLength)) * (Math.abs(yEnd - yStart) - 2.0D * curveHeight) + curveHeight) + yStart;
                }
            case SHAPE_CABLE:
                if (value < 0.5D) {
                    return yStart;
                } else if (value > length - 0.5D) {
                    return yEnd;
                }
                final double cableOffsetValue = value - 0.5D;
                final double offsetLength = length - 1.0D;
                final double posY = yStart + (yEnd - yStart) * cableOffsetValue / offsetLength;
                final double dip = offsetLength * offsetLength / 4.0D / CABLE_CURVATURE_SCALE;
                return posY + (dip > MAX_CABLE_DIP ? MAX_CABLE_DIP / dip : 1.0D) * (cableOffsetValue - offsetLength) * cableOffsetValue / CABLE_CURVATURE_SCALE;
            default:
                final double intercept = length / 2.0D;
                final double yChange;
                final double yInitial;
                final double offsetValue;
                if (value < intercept) {
                    yChange = (yEnd - yStart) / 2.0D;
                    yInitial = yStart;
                    offsetValue = value;
                } else {
                    yChange = (yStart - yEnd) / 2.0D;
                    yInitial = yEnd;
                    offsetValue = length - value;
                }
                return yChange * offsetValue * offsetValue / (intercept * intercept) + yInitial;
        }
    }

    /**
     * 三次 Hermite 纵坡剖面：端点高度 yStart/yEnd 与端点切线 slope1/slope2 同时满足。
     * 参数为「沿轨道的水平参数长度」（与 MTR 的 {@code getPositionY} 同一参数化）。
     */
    private double hermitePositionY(double value) {
        final double length = getLength();
        if (length <= 0.0D) {
            return yStart;
        }
        final double t = clampSafe(value, 0.0D, length) / length;
        final double t2 = t * t;
        final double t3 = t2 * t;
        final double h00 = 2.0D * t3 - 3.0D * t2 + 1.0D;
        final double h10 = t3 - 2.0D * t2 + t;
        final double h01 = -2.0D * t3 + 3.0D * t2;
        final double h11 = t3 - t2;
        return h00 * yStart + h10 * (slope1 * length) + h01 * yEnd + h11 * (slope2 * length);
    }

    private double getVTheta() {
        final double height = Math.abs(yEnd - yStart);
        final double length = getLength();
        return 2.0D * Math.atan2(
                Math.sqrt(height * height - 4.0D * verticalRadius * height + length * length) - length,
                height - 4.0D * verticalRadius
        );
    }

    // ==================== 内部：包围盒采样（照抄 4.0.5 renderSegment 的迭代方式） ====================

    /**
     * 包围盒采样：迭代方式与 4.0.5 {@code renderSegment} 一致。
     * <p>
     * 关键：4.0.5 的包围盒是<b>在 render 回调里</b>累计的，而回调只在存在「上一次采样」时才触发，
     * 回调参数携带「上一采样 + 当前采样」两组角点。因此采样数 &lt; 2 时包围盒<b>完全不被累计</b>
     * （随后 {@code bounds[0] > bounds[3]} 判定为真，包围盒归零）。本方法严格复刻该行为。
     */
    private void sampleSegment(double h, double k, double r, double tStart, double tEnd, double rawValueOffset, double interval, boolean reverseT, boolean isStraight, double[] bounds) {
        final double count = Math.abs(tEnd - tStart);
        final double increment = count < 0.5D || interval <= 0.0D ? 0.5D : count / Math.round(count) * interval;
        boolean hasPrevious = false;
        double previousX = 0.0D;
        double previousY = 0.0D;
        double previousZ = 0.0D;
        for (double i = 0.0D; i < count + increment - RENDER_SEGMENT_LOOP_MARGIN; i += increment) {
            final double t = (reverseT ? -1.0D : 1.0D) * i + tStart;
            final double y = getPositionY(i + rawValueOffset);
            final double[] corner = getPositionXZ(h, k, r, t, 0.0D, isStraight);
            if (hasPrevious) {
                accumulate(bounds, previousX, previousY, previousZ);
                accumulate(bounds, corner[0], y, corner[2]);
            }
            previousX = corner[0];
            previousY = y;
            previousZ = corner[2];
            hasPrevious = true;
        }
    }

    private static void accumulate(double[] bounds, double x, double y, double z) {
        bounds[0] = Math.min(x, bounds[0]);
        bounds[1] = Math.min(y, bounds[1]);
        bounds[2] = Math.min(z, bounds[2]);
        bounds[3] = Math.max(x, bounds[3]);
        bounds[4] = Math.max(y, bounds[4]);
        bounds[5] = Math.max(z, bounds[5]);
    }

    // ==================== 内部：工具 ====================

    private static double getTBounds(double x, double h, double z, double k, double r) {
        return Math.atan2(z - k, x - h) * r;
    }

    private static double getTBounds(double x, double h, double z, double k, double r, double tStart, boolean reverse) {
        final double t = getTBounds(x, h, z, k, r);
        if (t < tStart && !reverse) {
            return t + 2.0D * Math.PI * r;
        } else if (t > tStart && reverse) {
            return t - 2.0D * Math.PI * r;
        } else {
            return t;
        }
    }

    /**
     * {@code Angle.isParallel} 的等价判定：同向或反向。
     * <p>
     * MTR 的枚举实现是引用比较（{@code this == angle || this == angle.getOpposite()}），
     * FangSu 的幻影角度则在 {@code AngleMixin} 里改成数值比较（float 角度 + 1e-3 容差）。
     * 这里用后者：对枚举角度结果与前者一致（枚举都是 22.5° 的倍数）。
     */
    private static boolean isParallel(float degrees1, float degrees2) {
        final double difference = normalize180f(degrees1 - degrees2);
        return Math.abs(difference) < 1.0E-3D || Math.abs(Math.abs(difference) - 180.0D) < 1.0E-3D;
    }

    /** 与 MTR {@code Angle} 一致：把 float 角度归一化到 [-180,180)。 */
    private static float normalize180f(float degrees) {
        float result = degrees % 360.0F;
        if (result < -180.0F) {
            result += 360.0F;
        } else if (result >= 180.0F) {
            result -= 360.0F;
        }
        return result;
    }

    /** 与 MTR {@code Angle.angleRadians = Math.toRadians(angleDegrees)} 一致（由 float 角度求弧度）。 */
    private static double radiansOf(float degrees) {
        return Math.toRadians((double) degrees);
    }

    private static double clampSafe(double value, double minimum, double maximum) {
        return value < minimum ? minimum : (value > maximum ? maximum : value);
    }

    private static double squaredDistance(double x1, double z1, double x2, double z2) {
        final double dx = x2 - x1;
        final double dz = z2 - z1;
        return dx * dx + dz * dz;
    }

    /**
     * 把世界水平坐标投影到某一段上，返回「沿整条轨道的参数」；该段退化时返回 {@link Double#NaN}。
     */
    private double projectOnSegment(double h, double k, double r, double tStart, double tEnd, boolean reverse, boolean isStraight, double rawValueOffset, double x, double z) {
        final double count = Math.abs(tEnd - tStart);
        if (count == 0.0D || (h == 0.0D && k == 0.0D && r == 0.0D)) {
            return Double.NaN;
        }
        final double lower = Math.min(tStart, tEnd);
        final double upper = Math.max(tStart, tEnd);

        final double t;
        if (isStraight) {
            final double base = Math.abs(h) >= 0.5D && Math.abs(k) >= 0.5D ? 0.0D : r;
            // x = h*t + k*base + 0.5, z = k*t + h*r + 0.5 -> 方向向量为 (h, k)
            final double centreX = x - 0.5D - k * base;
            final double centreZ = z - 0.5D - h * r;
            final double denominator = h * h + k * k;
            final double projected = (centreX * h + centreZ * k) / denominator;
            t = clampSafe(projected, lower, upper);
        } else {
            final double raw = r * Math.atan2(z - 0.5D - k, x - 0.5D - h);
            // 本段 t 区间可能跨越 ±2πr 的整数倍，挑选落在区间内的候选，否则夹到端点
            final double period = 2.0D * Math.PI * r;
            double best = Double.NaN;
            double bestDistance = Double.MAX_VALUE;
            for (int shift = -2; shift <= 2; shift++) {
                final double candidate = raw + shift * period;
                final double clamped = clampSafe(candidate, lower, upper);
                final double distance = Math.abs(candidate - clamped);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = clamped;
                }
            }
            t = best;
        }

        if (Double.isNaN(t)) {
            return Double.NaN;
        }
        final double parameterInSegment = reverse ? -(t - tStart) : (t - tStart);
        return rawValueOffset + parameterInSegment;
    }
}
