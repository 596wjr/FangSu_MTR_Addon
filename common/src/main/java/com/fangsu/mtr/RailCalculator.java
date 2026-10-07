package com.fangsu.mtr;

import java.util.ArrayList;
import java.util.List;

/**
 * 万向节点轨道几何求解器。
 * <p>
 * 自 NTE mtrsteamloco {@code data/RailCalculator.java} 移植（MIT License, Copyright (c) 2022-present Zbx1425），
 * 算法逐行保留，仅去掉了原文件中的 {@code main} 测试入口与 {@code System.out} 调试输出
 * （仓库约定：禁止 System.out / System.err，日志一律走 {@code Main.LOGGER}）。
 * <p>
 * 核心能力：
 * <ul>
 *   <li>{@link #calculate} — 给定两端点坐标与两端切向角，求出「直线 + 圆弧」或「圆弧 + 直线」两段几何</li>
 *   <li>{@link #calculateMaxRadiusAngle} — 一端切向固定、另一端未绑定时，求“最大半径圆弧”在自由端的切向角</li>
 * </ul>
 * 坐标约定与 MTR 一致：X 轴向东、Z 轴向南，角度为 atan2(z, x) 的弧度/角度值。
 */
public class RailCalculator {
    public static final double PRECISION = 1e-3;

    /**
     * 二维向量（X-Z 平面）。
     */
    public static class Vec2 {
        public final double x, z;

        public Vec2(double x, double z) {
            this.x = x;
            this.z = z;
        }

        public Vec2 add(Vec2 other) {
            return new Vec2(x + other.x, z + other.z);
        }

        public Vec2 sub(Vec2 other) {
            return new Vec2(x - other.x, z - other.z);
        }

        public Vec2 rotateDeg(double angle) {
            return rotateRad(Math.toRadians(angle));
        }

        public Vec2 rotateRad(double angle) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            return new Vec2(x * cos - z * sin, x * sin + z * cos);
        }

        public double length() {
            return Math.sqrt(x * x + z * z);
        }

        public double distance(Vec2 other) {
            return sub(other).length();
        }

        public double radian() {
            return Math.atan2(z, x);
        }

        public double degree() {
            return Math.toDegrees(radian());
        }

        public double sin() {
            return z / length();
        }

        public double cos() {
            return x / length();
        }

        public Vec2 scale(double factor) {
            return new Vec2(x * factor, z * factor);
        }

        public Vec2 normalize() {
            double length = length();
            if (length < PRECISION) {
                return new Vec2(0, 0);
            }
            return new Vec2(x / length, z / length);
        }

        public String toString() {
            return String.format("Vec2(%.2f, %.2f)", x, z);
        }
    }

    /**
     * 直线 Ax + Bz + C = 0。
     */
    public static class Line {
        public final double A, B, C; // Ax + By + C = 0

        public Line(Vec2 p1, Vec2 p2) {
            A = p1.z - p2.z;
            B = p2.x - p1.x;
            C = p1.x * p2.z - p2.x * p1.z;
        }

        public Vec2 intersection(Line other) {
            double det = A * other.B - other.A * B;
            if (Math.abs(det) < PRECISION) {
                return null;
            }
            double x = (B * other.C - other.B * C) / det;
            double z = (other.A * C - A * other.C) / det;
            return new Vec2(x, z);
        }

        public boolean parallel(Line other) {
            return Math.abs(A * other.B - other.A * B) < PRECISION;
        }

        public double distance(Vec2 p) {
            return Math.abs(A * p.x + B * p.z + C) / Math.sqrt(A * A + B * B);
        }

        public Line perpendicular(Vec2 p) {
            Vec2 d = direction().rotateDeg(90);
            return new Line(p, p.add(d));
        }

        public Vec2 direction() {
            return new Vec2(B, -A).normalize();
        }

        public String toString() {
            return String.format("Line: %s*x + %s*z + %s = 0", A, B, C);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (obj == this) return true;
            if (obj instanceof Line other) {
                boolean crossAB = Math.abs(A * other.B - other.A * B) < PRECISION;
                boolean crossAC = Math.abs(A * other.C - other.A * C) < PRECISION;
                boolean crossBC = Math.abs(B * other.C - other.B * C) < PRECISION;
                return crossAB && crossAC && crossBC;
            }
            return false;
        }
    }

    /**
     * 圆。
     */
    public static class Circle {
        public final Vec2 center;
        public final double radius;

        public Circle(Vec2 center, double radius) {
            this.center = center;
            this.radius = radius;
        }

        public List<Vec2> intersections(Line line) {
            List<Vec2> result = new ArrayList<>();
            double d = line.distance(center);
            if (d > radius + PRECISION) {
                return result;
            }

            Line perp = line.perpendicular(center);
            Vec2 foot = line.intersection(perp);
            if (foot == null) {
                return result;
            }

            if (Math.abs(d - radius) <= PRECISION) {
                result.add(foot);
                return result;
            }

            double t = Math.sqrt(radius * radius - d * d);
            Vec2 dir = line.direction();
            double dirLength = dir.length();
            if (dirLength < PRECISION) {
                return result;
            }
            Vec2 unitDir = new Vec2(dir.x / dirLength, dir.z / dirLength);
            Vec2 delta = new Vec2(unitDir.x * t, unitDir.z * t);
            result.add(foot.add(delta));
            result.add(foot.sub(delta));
            return result;
        }
    }

    /**
     * MTR {@code Rail} 内部使用的段落参数（圆：圆心 h,k 半径 r，参数区间 tStart~tEnd；
     * 直线：h,k 为单位方向，r 为截距，同 MTR 的两种直线参数形式）。
     */
    public static class Section {
        public final double h, k, r, tStart, tEnd;
        public final boolean reverseT, isStraight;

        public Section() {
            this(0, 0, 0, 0, 0, false, true);
        }

        public Section(double h, double k, double r, double tStart, double tEnd, boolean reverseT, boolean isStraight) {
            this.h = h;
            this.k = k;
            this.r = r;
            this.tStart = tStart;
            this.tEnd = tEnd;
            this.reverseT = reverseT;
            this.isStraight = isStraight;
        }

        public double getLength() {
            return Math.abs(tEnd - tStart);
        }

        public boolean isValid() {
            boolean b1 = Double.isFinite(h);
            boolean b2 = Double.isFinite(k);
            boolean b3 = Double.isFinite(r);
            boolean b4 = Double.isFinite(tStart);
            boolean b5 = Double.isFinite(tEnd);
            return b1 && b2 && b3 && b4 && b5;
        }
    }

    public interface Shape {
        Section toSection();
    }

    /**
     * 圆弧（圆心 + 起点 + 终点）。
     */
    public static class Arc implements Shape {
        private final Vec2 center, start, end;
        private final double radius;

        public Arc(Vec2 center, Vec2 start, Vec2 end) {
            this.center = center;
            this.radius = center.distance(start);
            this.start = start;
            this.end = end;
        }

        @Override
        public Section toSection() {
            // 圆心坐标 (h, k) 和半径 r
            double h = center.x;
            double k = center.z;
            double r = radius;

            // 计算起点和终点相对于圆心的角度（弧度）
            Vec2 startRel = start.sub(center);
            double thetaStart = Math.atan2(startRel.z, startRel.x);

            Vec2 endRel = end.sub(center);
            double thetaEnd = Math.atan2(endRel.z, endRel.x);

            // 计算标准化的角度差，保持在 [-π, π] 范围内
            double deltaTheta = thetaEnd - thetaStart;
            deltaTheta = (deltaTheta + Math.PI) % (2 * Math.PI);
            if (deltaTheta < 0) deltaTheta += 2 * Math.PI;
            deltaTheta -= Math.PI;

            // 计算t参数的范围，考虑半径
            double tStart = thetaStart * r;
            double tEnd = tStart + deltaTheta * r;

            // 判断是否需要反转参数方向
            boolean reverseT = deltaTheta < 0;

            return new Section(h, k, r, tStart, tEnd, reverseT, false);
        }
    }

    /**
     * 直线段。
     */
    public static class Segment implements Shape {
        private final Vec2 start;
        private final Vec2 end;

        public Segment(Vec2 start, Vec2 end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public Section toSection() {
            Vec2 delta = end.sub(start);
            double len = delta.length();
            if (len < PRECISION) {
                // 无效的线段，返回无效的Section
                return new Section(Double.NaN, Double.NaN, Double.NaN, 0, 0, false, true);
            }
            double h = delta.x / len; // 单位方向向量的x分量
            double k = delta.z / len; // 单位方向向量的z分量
            boolean isForm1 = Math.abs(h) >= 0.5 && Math.abs(k) >= 0.5;

            double r, tStart, tEnd;
            if (isForm1) {
                // 形式1: x = h*T, z = k*T + h*r
                r = (h * start.z - k * start.x) / (h * h);
                tStart = start.x / h;
                tEnd = end.x / h;
            } else {
                // 形式2: x = h*T + k*r, z = k*T + h*r
                double div = 2 * h * h - 1; // 等于h² - k²，因为h² + k²=1
                r = (h * start.z - k * start.x) / div;
                tStart = (h * start.x - k * start.z) / div;
                tEnd = (h * end.x - k * end.z) / div;
            }

            boolean reverseT = tStart > tEnd;
            return new Section(h, k, r, tStart, tEnd, reverseT, true);
        }
    }

    /**
     * 求解结果：两段几何（第二段可能为空 Section，表示只需一段）。
     */
    public static class Group {
        public static Group EMPTY = new Group(new Section(), new Section());

        public final Section first;
        public final Section second;

        public Group(Section first, Section second) {
            this.first = first;
            this.second = second;
        }
    }

    /**
     * 给定起点、终点与两端切向角（弧度），求轨道两段几何。
     *
     * @return 求解结果；几何无解时返回 {@code null}
     */
    public static Group calculate(double startX, double startZ, double endX, double endZ, double startAngle, double endAngle) {
        return _calculate(startX, startZ, endX, endZ, startAngle, endAngle);
    }

    /**
     * 退化为一条直线（两端未绑定万向节点时使用）。
     */
    public static Group segment(double startX, double startZ, double endX, double endZ) {
        Segment seg = new Segment(new Vec2(startX, startZ), new Vec2(endX, endZ));
        return new Group(seg.toSection(), new Section());
    }

    private static Group _calculate(double startX, double startZ, double endX, double endZ, double startAngle, double endAngle) {
        Vec2 S = new Vec2(startX, startZ);
        double alpha = startAngle;
        Vec2 E = new Vec2(endX, endZ);
        double beta = endAngle;

        Vec2 vSS1 = new Vec2(1, 0).rotateRad(alpha);
        Vec2 S1 = S.add(vSS1);

        Vec2 vEE1 = new Vec2(1, 0).rotateRad(beta);
        Vec2 E1 = E.add(vEE1);

        Line SS1 = new Line(S, S1);
        Line EE1 = new Line(E, E1);

        if (SS1.parallel(EE1)) {
            // 两端切向平行
            if (SS1.equals(EE1)) {
                // 共线 → 直线
                Segment seg = new Segment(S, E);
                return new Group(seg.toSection(), new Section());
            }

            // 平行但不同线 → 两段等半径圆弧（S 形）
            Line SE = new Line(S, E);

            Vec2 vSE = E.sub(S);

            Vec2 vSD = vSE.scale(1.0D / 4.0D);

            Vec2 D = S.add(vSD);

            Line l1 = SE.perpendicular(D);
            Line l2 = SS1.perpendicular(S);
            Vec2 O1 = l1.intersection(l2);

            Vec2 vEF = vSD.scale(-1);
            Vec2 F = E.add(vEF);

            Line l3 = SE.perpendicular(F);
            Line l4 = EE1.perpendicular(E);
            Vec2 O2 = l3.intersection(l4);

            if (l2.equals(l4)) {
                // 方向无效
                return null;
            }

            if (O1 == null || O2 == null) {
                // 无交点
                return null;
            }

            Vec2 vSM = vSE.scale(1.0D / 2.0D);
            Vec2 M = S.add(vSM);

            return new Group(new Arc(O1, S, M).toSection(), new Arc(O2, M, E).toSection());
        }

        Vec2 M = SS1.intersection(EE1);

        if (M == null) {
            // 无交点
            return null;
        }

        Vec2 vMS = S.sub(M);
        Vec2 vME = E.sub(M);
        double theta = vME.degree() - vMS.degree();
        double dME = M.distance(E);
        double dMS = M.distance(S);
        double diff = dME - dMS;

        if (diff > PRECISION) { // 曲线在前
            Line p1 = SS1.perpendicular(S);

            Vec2 vMF = vMS.rotateDeg(theta);
            Vec2 F = M.add(vMF);
            Line p2 = EE1.perpendicular(F);

            Vec2 O = p1.intersection(p2);

            if (O == null) {
                // 无交点
                return null;
            }

            Arc arc = new Arc(O, S, F);
            Segment seg = new Segment(F, E);
            return new Group(arc.toSection(), seg.toSection());
        } else if (diff < -PRECISION) { // 曲线在后
            Line p1 = EE1.perpendicular(E);

            Vec2 vMF = vME.rotateDeg(-theta);
            Vec2 F = M.add(vMF);
            Line p2 = SS1.perpendicular(F);

            Vec2 O = p1.intersection(p2);
            if (O == null) {
                // 无交点
                return null;
            }

            Segment seg = new Segment(S, F);
            Arc arc = new Arc(O, F, E);
            return new Group(seg.toSection(), arc.toSection());
        } else { // 一段曲线
            Line p1 = SS1.perpendicular(S);
            Line p2 = EE1.perpendicular(E);
            Vec2 O = p1.intersection(p2);
            if (O == null) {
                // 无交点
                return null;
            }

            return new Group(new Arc(O, S, E).toSection(), new Section());
        }
    }

    /**
     * 求“最大半径圆弧”在自由端（E 点）的切向角。
     * <p>
     * 固定端 S 的切向为 startAngle（弧度），求过 E 点且与 S 处方向相切的圆（唯一解），
     * 返回该圆在 E 点处的切向角（度）。这是满足平滑连接的“最大半径”圆弧。
     *
     * @param startX     固定端 X
     * @param startZ     固定端 Z
     * @param endX       自由端 X
     * @param endZ       自由端 Z
     * @param startAngle 固定端切向角（弧度）
     * @return 自由端切向角（度）；无解时返回 {@code null}
     */
    public static Double calculateMaxRadiusAngle(double startX, double startZ, double endX, double endZ, double startAngle) {
        Vec2 S = new Vec2(startX, startZ);
        double alpha = startAngle;
        Vec2 E = new Vec2(endX, endZ);

        Vec2 vSS1 = new Vec2(1, 0).rotateRad(alpha);
        Vec2 S1 = S.add(vSS1);
        Line SS1 = new Line(S, S1);

        Line SE = new Line(S, E);

        if (SS1.equals(SE)) {
            // 共线 → 直线
            return Math.toDegrees(startAngle);
        }

        Line SD = SS1.perpendicular(S);

        Vec2 vSE = E.sub(S);
        Vec2 vSF = vSE.scale(1.0D / 2.0D);
        Vec2 F = S.add(vSF);

        Vec2 D = SD.intersection(SE.perpendicular(F));

        if (D == null) {
            // 无交点
            return null;
        }

        Line DE = new Line(D, E);

        Line l = DE.perpendicular(E);

        double dir = l.direction().degree();

        Group group = _calculate(startX, startZ, endX, endZ, startAngle, Math.toRadians(dir));

        if (group == null) {
            // 无效的 section
            return null;
        }

        return dir;
    }
}
