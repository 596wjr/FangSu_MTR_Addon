package com.fangsu.levelcrossing.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 平面多边形运算（纯数学，不依赖 Minecraft）。
 * <p>
 * 约定：多边形按「逆时针」存储（在 (x,z) 平面上有符号面积为正）。
 * 平交路口只需要「单位格矩形 − 若干凸条带」这一种布尔运算，
 * 因此这里用逐条边切分的凸多边形差集，避免引入通用多边形布尔库。
 */
public final class PolyOps {

    /** 面积小于该值的碎片直接丢弃：单位格是 1×1，1e-7 已经是亚像素级。 */
    public static final double AREA_EPS = 1e-7;

    private static final double WELD_EPS = 1e-9;

    private PolyOps() {
    }

    /** 轴对齐矩形，逆时针（正面积）。 */
    public static List<Vec2> rect(double minX, double minZ, double maxX, double maxZ) {
        List<Vec2> out = new ArrayList<>(4);
        out.add(new Vec2(minX, minZ));
        out.add(new Vec2(maxX, minZ));
        out.add(new Vec2(maxX, maxZ));
        out.add(new Vec2(minX, maxZ));
        return out;
    }

    /** 有符号面积；逆时针为正。 */
    public static double area(List<Vec2> poly) {
        if (poly == null || poly.size() < 3) return 0;
        double sum = 0;
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            Vec2 a = poly.get(i);
            Vec2 b = poly.get((i + 1) % n);
            sum += a.x() * b.z() - b.x() * a.z();
        }
        return sum * 0.5;
    }

    /** Sutherland–Hodgman 裁剪到矩形，保留输入顶点顺序。 */
    public static List<Vec2> clipToRect(List<Vec2> poly, double minX, double minZ,
                                        double maxX, double maxZ) {
        List<Vec2> cur = clean(poly);
        if (cur.size() < 3) return new ArrayList<>();
        cur = clipAxis(cur, 0, minX, true);
        if (cur.size() < 3) return new ArrayList<>();
        cur = clipAxis(cur, 0, maxX, false);
        if (cur.size() < 3) return new ArrayList<>();
        cur = clipAxis(cur, 1, minZ, true);
        if (cur.size() < 3) return new ArrayList<>();
        cur = clipAxis(cur, 1, maxZ, false);
        return clean(cur);
    }

    /**
     * subject 减去凸 clip：把 clip 的每条边当成「外半平面」，按边逐块切分 subject。
     * <p>
     * clip 的第 i 块 = subject ∩ (前 i 条边的内侧) ∩ (第 i 条边的外侧)，
     * 这些块互不重叠且并集正好是 subject − clip，因此可以安全地拼成铺面。
     */
    public static List<List<Vec2>> subtractConvex(List<Vec2> subject, List<Vec2> clip) {
        List<List<Vec2>> out = new ArrayList<>();
        List<Vec2> s = clean(subject);
        if (s.size() < 3) return out;
        List<Vec2> c = clean(clip);
        if (c.size() < 3) {
            out.add(s);
            return out;
        }
        if (area(c) < 0) {
            c = new ArrayList<>(c);
            Collections.reverse(c);
        }
        int n = c.size();
        for (int i = 0; i < n; i++) {
            List<Vec2> piece = s;
            for (int j = 0; j < i; j++) {
                if (piece.size() < 3) break;
                piece = clipHalfPlane(piece, c.get(j), c.get((j + 1) % n), true);
            }
            if (piece.size() < 3) continue;
            piece = clipHalfPlane(piece, c.get(i), c.get((i + 1) % n), false);
            List<Vec2> cleaned = clean(piece);
            if (cleaned.size() >= 3 && Math.abs(area(cleaned)) > AREA_EPS) out.add(cleaned);
        }
        return out;
    }

    /** 用一条过 p0、方向 p1-p0 的直线裁多边形，keepLeft 保留直线左侧（叉积 >= 0）。 */
    public static List<Vec2> clipLine(List<Vec2> poly, Vec2 p0, Vec2 p1, boolean keepLeft) {
        List<Vec2> s = clean(poly);
        if (s.size() < 3) return new ArrayList<>();
        Vec2 d = p1.sub(p0);
        if (d.length() < 1e-12) return new ArrayList<>();
        return clean(clipHalfPlane(s, p0, p0.add(d), keepLeft));
    }

    /** 用直线把多边形切成两片，返回 {左侧, 右侧}。 */
    public static List<List<Vec2>> splitByLine(List<Vec2> poly, Vec2 p0, Vec2 p1) {
        List<List<Vec2>> out = new ArrayList<>(2);
        out.add(clipLine(poly, p0, p1, true));
        out.add(clipLine(poly, p0, p1, false));
        return out;
    }

    /** 两个凸多边形的交集（subject 被凸 clip 逐边裁）。非凸输入不保证正确。 */
    public static List<Vec2> clipConvex(List<Vec2> subject, List<Vec2> clip) {
        List<Vec2> s = clean(subject);
        List<Vec2> c = clean(clip);
        if (s.size() < 3 || c.size() < 3) return new ArrayList<>();
        if (area(c) < 0) {
            c = new ArrayList<>(c);
            Collections.reverse(c);
        }
        int n = c.size();
        for (int i = 0; i < n; i++) {
            s = clipHalfPlane(s, c.get(i), c.get((i + 1) % n), true);
            if (s.size() < 3) return new ArrayList<>();
        }
        return clean(s);
    }

    /** 去重（相邻重合点 + 首尾闭合点），并剔除非有限坐标。 */
    public static List<Vec2> clean(List<Vec2> poly) {
        List<Vec2> out = new ArrayList<>();
        if (poly == null) return out;
        for (Vec2 p : poly) {
            if (p == null || !p.isFinite()) continue;
            if (!out.isEmpty() && out.get(out.size() - 1).sub(p).length() < WELD_EPS) continue;
            out.add(p);
        }
        while (out.size() > 1 && out.get(0).sub(out.get(out.size() - 1)).length() < WELD_EPS) {
            out.remove(out.size() - 1);
        }
        return out;
    }


    /** 逆时针（或顺时针）简单多边形是否为凸。共线点不算反射顶点。 */
    public static boolean isConvex(List<Vec2> poly) {
        List<Vec2> p = clean(poly);
        if (p.size() < 3) return false;
        if (p.size() == 3) return true;
        int n = p.size();
        int sign = 0;
        for (int i = 0; i < n; i++) {
            Vec2 a = p.get(i);
            Vec2 b = p.get((i + 1) % n);
            Vec2 c = p.get((i + 2) % n);
            double cr = b.sub(a).cross(c.sub(b));
            if (Math.abs(cr) < WELD_EPS) continue;
            int s = cr > 0 ? 1 : -1;
            if (sign == 0) sign = s;
            else if (s != sign) return false;
        }
        return true;
    }

    /**
     * 耳朵裁剪（ear clipping）三角化任意简单多边形，返回顶点下标三元组。
     * <p>
     * 结果对凹多边形也成立；面积与输入一致（有符号面积守恒）。
     * 输入按逆时针/顺时针都可，内部统一成逆时针再裁。
     */
    public static List<int[]> triangulate(List<Vec2> poly) {
        List<int[]> out = new ArrayList<>();
        List<Vec2> p = clean(poly);
        int n = p.size();
        if (n < 3) return out;
        boolean ccw = area(p) >= 0;
        List<Integer> ring = new ArrayList<>(n);
        if (ccw) {
            for (int i = 0; i < n; i++) ring.add(i);
        } else {
            for (int i = n - 1; i >= 0; i--) ring.add(i);
        }
        int guard = 0;
        while (ring.size() > 3 && guard++ < 4 * n * n + 16) {
            boolean clipped = false;
            for (int i = 0; i < ring.size(); i++) {
                Vec2 a = p.get(ring.get(i));
                Vec2 b = p.get(ring.get((i + 1) % ring.size()));
                Vec2 c = p.get(ring.get((i + 2) % ring.size()));
                // 反射顶点或退化顶点不是耳朵
                if (b.sub(a).cross(c.sub(b)) <= WELD_EPS) continue;
                boolean blocked = false;
                for (int j = 0; j < ring.size(); j++) {
                    int ji = ring.get(j);
                    if (ji == ring.get(i) || ji == ring.get((i + 1) % ring.size())
                            || ji == ring.get((i + 2) % ring.size())) continue;
                    if (pointInTriangle(p.get(ji), a, b, c)) {
                        blocked = true;
                        break;
                    }
                }
                if (blocked) continue;
                out.add(new int[]{ring.get(i), ring.get((i + 1) % ring.size()), ring.get((i + 2) % ring.size())});
                ring.remove((i + 1) % ring.size());
                clipped = true;
                break;
            }
            if (!clipped) {
                // 数值退化兜底：剩下的按扇形收尾，宁可多几个三角形也不丢面积
                for (int i = 1; i + 1 < ring.size(); i++) {
                    out.add(new int[]{ring.get(0), ring.get(i), ring.get(i + 1)});
                }
                return out;
            }
        }
        if (ring.size() == 3) out.add(new int[]{ring.get(0), ring.get(1), ring.get(2)});
        return out;
    }

    /**
     * 凸分解记忆化（同一次 build 内同一个多边形只分解一次）。
     * <p>
     * 凹多边形的耳切是 O(n²)：交叉格里同一条 band 的 poly 会被 {@code slotCuts} 反复喂进
     * 凸分解（每个对方 band 一次），64 点折线每次重算是实机 17 秒任务的主要嫌疑之一。
     * 用 {@link java.util.IdentityHashMap}（同一个列表实例才复用），由
     * {@link #clearConvexCache()} 在每次 build 开始时清空，避免跨格累积。
     */
    private static final java.util.Map<List<Vec2>, List<List<Vec2>>> CONVEX_CACHE =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    /** 一次 build 开始前清空凸分解缓存（也防止条目无限增长）。 */
    public static void clearConvexCache() {
        CONVEX_CACHE.clear();
    }

    /** 带记忆化的 {@link #convexParts}：同一个（Identity 相同的）多边形只分解一次。 */
    public static List<List<Vec2>> convexPartsCached(List<Vec2> poly) {
        if (poly == null) return new ArrayList<>();
        List<List<Vec2>> hit = CONVEX_CACHE.get(poly);
        if (hit != null) return hit;
        List<List<Vec2>> parts = convexParts(poly);
        if (CONVEX_CACHE.size() < 2048) CONVEX_CACHE.put(poly, parts);
        return parts;
    }

    /** 把多边形拆成凸片：本身是凸的就原样返回，否则退回三角形（三角形必凸）。 */
    public static List<List<Vec2>> convexParts(List<Vec2> poly) {
        List<List<Vec2>> out = new ArrayList<>();
        List<Vec2> p = clean(poly);
        if (p.size() < 3) return out;
        if (isConvex(p)) {
            out.add(p);
            return out;
        }
        for (int[] tri : triangulate(p)) {
            List<Vec2> t = new ArrayList<>(3);
            t.add(p.get(tri[0]));
            t.add(p.get(tri[1]));
            t.add(p.get(tri[2]));
            if (Math.abs(area(t)) > AREA_EPS) out.add(t);
        }
        if (out.isEmpty()) out.add(p);
        return out;
    }

    /** 点在三角形内（含边界，带容差）。 */
    public static boolean pointInTriangle(Vec2 p, Vec2 a, Vec2 b, Vec2 c) {
        double d1 = b.sub(a).cross(p.sub(a));
        double d2 = c.sub(b).cross(p.sub(b));
        double d3 = a.sub(c).cross(p.sub(c));
        boolean hasNeg = d1 < -WELD_EPS || d2 < -WELD_EPS || d3 < -WELD_EPS;
        boolean hasPos = d1 > WELD_EPS || d2 > WELD_EPS || d3 > WELD_EPS;
        return !(hasNeg && hasPos);
    }

    /** 点在多边形内（射线法，边界视为在内）。 */
    public static boolean contains(List<Vec2> poly, Vec2 p) {
        List<Vec2> q = clean(poly);
        int n = q.size();
        if (n < 3) return false;
        // 先判边界，避免射线正好掠过顶点
        for (int i = 0; i < n; i++) {
            Vec2 a = q.get(i);
            Vec2 b = q.get((i + 1) % n);
            if (onSegment(a, b, p)) return true;
        }
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Vec2 a = q.get(i);
            Vec2 b = q.get(j);
            if ((a.z() > p.z()) != (b.z() > p.z())) {
                double x = a.x() + (p.z() - a.z()) / (b.z() - a.z()) * (b.x() - a.x());
                if (p.x() < x) inside = !inside;
            }
        }
        return inside;
    }

    private static boolean onSegment(Vec2 a, Vec2 b, Vec2 p) {
        Vec2 ab = b.sub(a);
        Vec2 ap = p.sub(a);
        if (Math.abs(ab.cross(ap)) > 1e-12) return false;
        double d = ap.dot(ab);
        return d >= -WELD_EPS && d <= ab.dot(ab) + WELD_EPS;
    }

    private static List<Vec2> clipAxis(List<Vec2> poly, int axis, double value, boolean keepGreater) {
        if (poly.size() < 3) return new ArrayList<>();
        List<Vec2> out = new ArrayList<>(poly.size() + 4);
        Vec2 prev = poly.get(poly.size() - 1);
        boolean prevIn = insideAxis(prev, axis, value, keepGreater);
        for (Vec2 cur : poly) {
            boolean curIn = insideAxis(cur, axis, value, keepGreater);
            if (curIn != prevIn) out.add(crossAxis(prev, cur, axis, value));
            if (curIn) out.add(cur);
            prev = cur;
            prevIn = curIn;
        }
        return out;
    }

    private static boolean insideAxis(Vec2 p, int axis, double value, boolean keepGreater) {
        double c = axis == 0 ? p.x() : p.z();
        return keepGreater ? c >= value - WELD_EPS : c <= value + WELD_EPS;
    }

    private static Vec2 crossAxis(Vec2 a, Vec2 b, int axis, double value) {
        double ca = axis == 0 ? a.x() : a.z();
        double cb = axis == 0 ? b.x() : b.z();
        double den = cb - ca;
        if (Math.abs(den) < 1e-15) return a;
        return a.lerp(b, (value - ca) / den);
    }

    private static List<Vec2> clipHalfPlane(List<Vec2> poly, Vec2 a, Vec2 b, boolean keepLeft) {
        if (poly.size() < 3) return new ArrayList<>();
        Vec2 d = b.sub(a).normalized();
        List<Vec2> out = new ArrayList<>(poly.size() + 4);
        Vec2 prev = poly.get(poly.size() - 1);
        boolean prevIn = insideHalfPlane(d, a, prev, keepLeft);
        for (Vec2 cur : poly) {
            boolean curIn = insideHalfPlane(d, a, cur, keepLeft);
            if (curIn != prevIn) out.add(crossHalfPlane(prev, cur, a, d));
            if (curIn) out.add(cur);
            prev = cur;
            prevIn = curIn;
        }
        return out;
    }

    private static boolean insideHalfPlane(Vec2 d, Vec2 a, Vec2 p, boolean keepLeft) {
        double cr = d.cross(p.sub(a));
        return keepLeft ? cr >= -WELD_EPS : cr <= WELD_EPS;
    }

    private static Vec2 crossHalfPlane(Vec2 p, Vec2 q, Vec2 a, Vec2 d) {
        double den = d.cross(q.sub(p));
        if (Math.abs(den) < 1e-15) return p;
        return p.lerp(q, -d.cross(p.sub(a)) / den);
    }
}
