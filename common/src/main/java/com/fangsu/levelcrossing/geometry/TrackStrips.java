package com.fangsu.levelcrossing.geometry;

import com.fangsu.levelcrossing.CellSnapshot;
import com.fangsu.levelcrossing.TrackSlice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 把每条轨道在格内的中心线变成「槽型钢走廊」多边形（纯数学，不依赖 Minecraft）。
 * <p>
 * 每条轨道左右各一条走廊：中心线两侧偏移 [grooveInner, grooveOuter] 的带。
 * 走廊裁剪到单位格 [0,1]²，点数超限时按弧长均匀抽稀——顶点数是每格渲染开销的主要来源。
 */
public final class TrackStrips {

    /** 单条走廊多边形顶点上限。 */
    public static final int MAX_BAND_POINTS = 64;

    /**
     * 扫掠用的中心线采样上限。扫掠顶点数 = 段数 × 断面段数 × 4，
     * 中心线不抽稀的话一个采样过密的格子就会撞上每格顶点预算。
     */
    public static final int MAX_CENTERLINE_POINTS = 32;

    /** 中心线两端沿切向外延的长度：采样点常常正好停在格界内侧，外延一点可以避免铺面留缝。 */
    private static final double END_EXTEND = 0.35;

    /**
     * 曲率细分的容差（格）：折线顶点的「转角 × 偏移量」超过它就二分插中点。
     * <p>
     * 抽稀到 {@link #MAX_CENTERLINE_POINTS} 个点后，一条圆弧只剩折线的拐角；
     * 拐角处偏移误差 ≈ 转角 × 偏移量，容差 4‰ 格（≈0.06mm）已远低于可见阈值。
     */
    public static final double CURVE_SUBDIVIDE_TOLERANCE = 0.004;

    /**
     * 细分后的中心线点数上限 = <b>32</b>（与 {@link #MAX_CENTERLINE_POINTS} 同值，即「细分不再加密」）。
     * <p>
     * 依据（T41 实测）：弯道外侧缝隙的修复来自 {@link #offsetMiter} 的<b>真斜接</b>，
     * 而不是把中心线点加密 —— 对「抽稀后的折线」二分插中点，中点仍在弦上（几何完全不变）。
     * T41 对照数据（90° 圆弧，R=8 / R=20，32 点）：斜接后环带面积相对误差
     * 5.01e-04 / 2.66e-04，而旧的「平均切线法线」偏移是 6.57e-04 / 4.20e-04；
     * 细分到 64 点时误差几乎不动（5.01e-04），但扫掠顶点数翻倍。
     * 实机日志也印证：64 点时单格 1.38 万顶点、1 秒 3 次重建 → 玩家「很卡 + 新放置不显示」。
     */
    public static final int MAX_SUBDIVIDED_POINTS = 32;

    /**
     * 单格钢轨顶点预算（与 {@code CrossingMeshBuilder.MAX_VERTICES_PER_CELL} 同值，本类不引它以免耦合）。
     * <p>
     * 一条 band 的估算顶点 ≈ (点数−1) × 断面 8 段 × 4（<b>不含</b>铣切分片系数，是保守下限）；
     * 留 10% 余量后每条 band 允许的点数 = 1 + 7200 / (band 数 × 32)：四 band 时只有 1+56 → 被
     * {@link #MAX_SUBDIVIDED_POINTS}=32 压住，即「不加密」。
     * <p>
     * 历史口径：曾经是 4000（细分被压到 29 点）、后来 24000（放到 64 点，结果单格 1.38 万顶点）。
     * 现在与 {@code CrossingMeshBuilder.MAX_VERTICES_PER_CELL=8000} 对齐：细分只做「按曲率把拐角段
     * 二分」这条保底，不再为了精度加密到 64 点（加密对斜接误差没有贡献，见 MAX_SUBDIVIDED_POINTS 注释）。
     */
    private static final int CELL_RAIL_VERTEX_BUDGET = 8000;

    /**
     * 斜接延伸上限（miter-limit）：{@code 1/sin(θ/2)} 超过它时退化为角平分线方向、
     * 延伸量夹到 {@code MITER_LIMIT × |dist|}，避免锐利转角处甩出一根长尖刺。
     */
    private static final double MITER_LIMIT = 2.0;

    /**
     * @param poly       裁剪到格内的走廊多边形（逆时针无所谓，渲染时按期望法线纠正绕序）
     * @param side       +1 = 中心线 +v 一侧，-1 = 另一侧
     * @param slice      所属轨道
     * @param centerline 生成该走廊时用的中心线（已含端部外延），扫掠直接复用它
     */
    public record Band(List<Vec2> poly, int side, TrackSlice slice, List<Vec2> centerline) {
    }

    private TrackStrips() {
    }

    /** 逐轨道、逐侧生成走廊；无轨道的快照返回空表。 */
    public static List<Band> bands(CellSnapshot snap) {
        List<Band> out = new ArrayList<>();
        if (snap == null || snap.tracks == null) return out;
        // 细分点数按顶点预算收紧：预算不足时宁可不细分，也不能让渲染端整条跳过某一侧钢轨
        int bandCount = Math.max(1, snap.tracks.size() * 2);
        int pointBudget = 1 + CELL_RAIL_VERTEX_BUDGET * 9 / 10 / (bandCount * 8 * 4);
        int pointLimit = Math.min(MAX_SUBDIVIDED_POINTS, Math.max(MAX_CENTERLINE_POINTS, pointBudget));
        for (TrackSlice slice : snap.tracks) {
            if (slice == null) continue;
            List<Vec2> line = decimateOpen(centerline(slice), MAX_CENTERLINE_POINTS);
            if (line.size() < 2) continue;
            double inner = Math.max(0, slice.grooveInner);
            double outer = Math.max(inner + 1e-4, slice.grooveOuter);
            // 抽稀只在弦上留点：先按曲率把拐角段细分，再走真斜接偏移。
            // 外侧缝隙的根因是「等距法线偏移」，修复在 offsetMiter；细分让偏移与格界裁切有更密的落点。
            double maxOffset = Math.max(Math.abs(slice.mapLateral(inner)), Math.abs(slice.mapLateral(outer)));
            line = subdivideByCurvature(line, maxOffset, pointLimit);
            if (line.size() < 2) continue;
            for (int side = -1; side <= 1; side += 2) {
                // 走廊偏移同样走唯一的映射入口（TrackSlice.mapLateral），不在这里另写公式
                List<Vec2> near = offsetMiter(line, slice.mapLateral(side * inner));
                List<Vec2> far = offsetMiter(line, slice.mapLateral(side * outer));
                List<Vec2> poly = new ArrayList<>(near.size() + far.size());
                poly.addAll(near);
                Collections.reverse(far);
                poly.addAll(far);
                List<Vec2> clipped = PolyOps.clipToRect(poly, 0, 0, 1, 1);
                if (clipped.size() < 3) continue;
                clipped = decimate(clipped, MAX_BAND_POINTS);
                if (clipped.size() < 3) continue;
                out.add(new Band(clipped, side, slice, line));
            }
        }
        return out;
    }

    /** 轨道在格内的中心线采样；没有采样点时退化成过格心的直线。 */
    public static List<Vec2> centerline(TrackSlice slice) {
        List<Vec2> pts = new ArrayList<>();
        if (slice != null && slice.xs != null && slice.zs != null
                && Math.min(slice.xs.length, slice.zs.length) >= 2) {
            int n = Math.min(slice.xs.length, slice.zs.length);
            for (int i = 0; i < n; i++) {
                pts.add(new Vec2(slice.xs[i], slice.zs[i]));
            }
            pts = PolyOps.clean(pts);
            if (pts.size() < 2) return new ArrayList<>();
            return extendEnds(pts);
        }
        // 没有采样点：用过格心、方向取 yaw 的直线，长度直接超出单位格，交给裁剪收口
        double yaw = Math.toRadians(slice == null ? 0 : slice.yawDeg);
        double dx = Math.cos(yaw);
        double dz = Math.sin(yaw);
        pts.add(new Vec2(0.5 - dx * 0.75, 0.5 - dz * 0.75));
        pts.add(new Vec2(0.5 + dx * 0.75, 0.5 + dz * 0.75));
        return pts;
    }

    private static List<Vec2> extendEnds(List<Vec2> pts) {
        if (pts.size() < 2) return pts;
        List<Vec2> out = new ArrayList<>(pts);
        Vec2 head = pts.get(0);
        Vec2 second = pts.get(1);
        if (head.sub(second).length() > 1e-9) {
            out.add(0, head.add(head.sub(second).normalized().mul(END_EXTEND)));
        }
        Vec2 tail = pts.get(pts.size() - 1);
        Vec2 beforeTail = pts.get(pts.size() - 2);
        if (tail.sub(beforeTail).length() > 1e-9) {
            out.add(tail.add(tail.sub(beforeTail).normalized().mul(END_EXTEND)));
        }
        return out;
    }

    /**
     * 中心线整体平移 dist（正数为 +v 侧，即 (-t.z, t.x) 方向）。
     * <p>
     * 内点取「相邻两段各自偏移线的交点」= 真斜接（miter）。旧实现按平均切线取<b>单个</b>法线点，
     * 凸转角处这个点落在真偏移线<b>内侧</b>，于是外侧曲线沿每一个折拐累计出一条缝——
     * 这正是弯道外侧缝隙的根因（法线等距偏移不是等距偏移）。
     * <p>
     * 尖刺防护：延伸量 {@code 1/sin(θ/2)}（θ = 相邻两段夹角，等价于 {@code 1/cos(φ/2)}，
     * φ = 转角）超过 {@link #MITER_LIMIT} 时退化为角平分线方向、延伸量夹到
     * {@code MITER_LIMIT × |dist|}。端点没有内侧交点，用单侧法线平移。
     *
     * @param dist 偏移量，符号沿用 +v 约定（正 = 法线的 (-t.z, t.x) 一侧）
     */
    public static List<Vec2> offsetMiter(List<Vec2> line, double dist) {
        List<Vec2> out = new ArrayList<>(line.size());
        int n = line.size();
        if (n == 0) return out;
        if (n == 1) {
            out.add(line.get(0));
            return out;
        }
        for (int i = 0; i < n; i++) {
            Vec2 p = line.get(i);
            if (i == 0 || i == n - 1) {
                // 端点：只有一侧的段可用，按该段法线平移
                Vec2 t = i == 0 ? line.get(1).sub(p) : p.sub(line.get(n - 2));
                if (t.length() < 1e-9) {
                    out.add(p);
                    continue;
                }
                t = t.normalized();
                out.add(new Vec2(p.x() - t.z() * dist, p.z() + t.x() * dist));
                continue;
            }
            Vec2 t0 = p.sub(line.get(i - 1));
            Vec2 t1 = line.get(i + 1).sub(p);
            if (t0.length() < 1e-9 || t1.length() < 1e-9) {
                out.add(p);
                continue;
            }
            t0 = t0.normalized();
            t1 = t1.normalized();
            Vec2 n0 = new Vec2(-t0.z(), t0.x());
            Vec2 n1 = new Vec2(-t1.z(), t1.x());
            Vec2 bis = n0.add(n1);
            double half = 0.5 * bis.length();
            if (half < 1e-9) {
                // 180° 折返：两段偏移线平行无交点，退化到前一段的单侧法线
                out.add(new Vec2(p.x() - t0.z() * dist, p.z() + t0.x() * dist));
                continue;
            }
            bis = bis.normalized();
            double extension = Math.min(1.0 / half, MITER_LIMIT);
            out.add(new Vec2(p.x() + bis.x() * extension * dist, p.z() + bis.z() * extension * dist));
        }
        return out;
    }

    /**
     * 按曲率细分折线：某处转角 θ 满足 {@code |θ| × maxOffset > CURVE_SUBDIVIDE_TOLERANCE} 时，
     * 把该转角两侧的段二分插中点，直到误差达标或点数达到 {@link #MAX_SUBDIVIDED_POINTS}。
     * <p>
     * 抽稀后的折线没有弧上的采样点，二分插入的中点仍在弦上（不改变折线形状，只加密采样）；
     * 它保证斜接偏移与格界裁切在每个拐角附近都有足够的落点，避免「一个格界只切到一段弦」。
     * 真正修复外侧缝隙的是 {@link #offsetMiter}。
     *
     * @param line      抽稀后的中心线
     * @param maxOffset 该轨用到的最远横向偏移（越大，同样转角下误差越大）
     */
    public static List<Vec2> subdivideByCurvature(List<Vec2> line, double maxOffset) {
        return subdivideByCurvature(line, maxOffset, MAX_SUBDIVIDED_POINTS);
    }

    /** 同 {@link #subdivideByCurvature(List, double)}，但点数上限由调用方给（bands 要按顶点预算收紧）。 */
    private static List<Vec2> subdivideByCurvature(List<Vec2> line, double maxOffset, int limit) {
        if (line == null || line.size() < 2) return line;
        double offset = Math.abs(maxOffset);
        if (offset < 1e-9) return line;
        int max = Math.max(2, limit);
        List<Vec2> cur = new ArrayList<>(line);
        boolean changed = true;
        while (changed && cur.size() < max) {
            changed = false;
            List<Vec2> next = new ArrayList<>(Math.min(max, cur.size() * 2));
            int room = max - cur.size(); // 还能插几个中点：到上限就停，绝不丢原有顶点
            for (int i = 0; i < cur.size(); i++) {
                next.add(cur.get(i));
                if (i + 1 >= cur.size()) continue;
                // 该段两端任一转角超差就二分（段是两条转角共享的弦）
                if (room > 0 && (turnExceeds(cur, i, offset) || turnExceeds(cur, i + 1, offset))) {
                    next.add(cur.get(i).lerp(cur.get(i + 1), 0.5));
                    room--;
                    changed = true;
                }
            }
            cur = next;
        }
        return cur;
    }

    /** 顶点 i 的转角 × 偏移量是否超过容差（端点无转角）。 */
    private static boolean turnExceeds(List<Vec2> line, int i, double offset) {
        if (i <= 0 || i >= line.size() - 1) return false;
        Vec2 t0 = line.get(i).sub(line.get(i - 1));
        Vec2 t1 = line.get(i + 1).sub(line.get(i));
        if (t0.length() < 1e-9 || t1.length() < 1e-9) return false;
        t0 = t0.normalized();
        t1 = t1.normalized();
        // 转角 = 单位切向的夹角（用 atan2 取带符号角，再取绝对值）
        double turn = Math.abs(Math.atan2(t0.cross(t1), t0.dot(t1)));
        return turn * offset > CURVE_SUBDIVIDE_TOLERANCE;
    }

    /** 开放折线按弧长均匀抽稀，首尾点保持不动。 */
    private static List<Vec2> decimateOpen(List<Vec2> line, int max) {
        int n = line.size();
        if (n <= max || max < 2) return line;
        double[] seg = new double[n - 1];
        double total = 0;
        for (int i = 0; i < n - 1; i++) {
            seg[i] = line.get(i).sub(line.get(i + 1)).length();
            total += seg[i];
        }
        if (total < 1e-9) return line;
        double step = total / (max - 1);
        List<Vec2> out = new ArrayList<>(max);
        out.add(line.get(0));
        double cursor = 0;
        double target = step;
        for (int i = 0; i < n - 1 && out.size() < max - 1; i++) {
            double start = cursor;
            double end = cursor + seg[i];
            while (target < end - 1e-12 && out.size() < max - 1) {
                double t = seg[i] < 1e-12 ? 0 : (target - start) / seg[i];
                Vec2 p = line.get(i).lerp(line.get(i + 1), t);
                if (out.get(out.size() - 1).sub(p).length() > 1e-9) out.add(p);
                target += step;
            }
            cursor = end;
        }
        Vec2 last = line.get(n - 1);
        if (out.get(out.size() - 1).sub(last).length() > 1e-9) out.add(last);
        return out;
    }

    /** 闭合多边形按弧长均匀抽稀，首点与末点保持不动（端点是格界上的封口点）。 */
    private static List<Vec2> decimate(List<Vec2> poly, int max) {
        if (poly.size() <= max || max < 3) return poly;
        int n = poly.size();
        double[] seg = new double[n];
        double total = 0;
        for (int i = 0; i < n; i++) {
            Vec2 a = poly.get(i);
            Vec2 b = poly.get((i + 1) % n);
            seg[i] = a.sub(b).length();
            total += seg[i];
        }
        if (total < 1e-9) return poly;
        double step = total / (max - 1);
        List<Vec2> out = new ArrayList<>(max);
        out.add(poly.get(0));
        double cursor = 0;
        double target = step;
        for (int i = 0; i < n && out.size() < max - 1; i++) {
            double start = cursor;
            double end = cursor + seg[i];
            while (target < end - 1e-12 && out.size() < max - 1) {
                double t = seg[i] < 1e-12 ? 0 : (target - start) / seg[i];
                Vec2 a = poly.get(i);
                Vec2 b = poly.get((i + 1) % n);
                Vec2 p = a.lerp(b, t);
                if (out.get(out.size() - 1).sub(p).length() > 1e-9) out.add(p);
                target += step;
            }
            cursor = end;
        }
        Vec2 last = poly.get(n - 1);
        if (out.get(out.size() - 1).sub(last).length() > 1e-9) out.add(last);
        return out;
    }
}
