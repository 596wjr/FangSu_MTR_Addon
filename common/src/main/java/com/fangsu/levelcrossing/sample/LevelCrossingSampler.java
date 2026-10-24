package com.fangsu.levelcrossing.sample;

import com.fangsu.Main;
import com.fangsu.customItem.NteRailManager;
import com.fangsu.customItem.NteRailManager.NteRail;
import com.fangsu.levelcrossing.CellSnapshot;
import com.fangsu.levelcrossing.CrossingSettings;
import com.fangsu.levelcrossing.LevelCrossingGaugeTable;
import com.fangsu.levelcrossing.TrackSlice;
import com.fangsu.render.sowcerext.model.RawMesh;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.Vertex;
import com.fangsu.utils.ResourceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平交路口方块的几何采样器：把轨道中心线裁成本格的一段，算出铺面高度与姿态。
 * <p>
 * <b>坐标系</b>：全部输出都是方块局部坐标（X+ 东、Z+ 南、Y+ 上、原点 = 方块最小角，单位格）。
 * 因此本类内部一律先做 {@code world - pos} 的平移，后续几何判断都在 {@code [-outerExtend, 1+outerExtend]} 的方格上说。
 * <p>
 * <b>为什么自己写多边形裁切</b>：命中判定只需要"轨道全宽走廊与本格矩形相交多少"，
 * 用一次 Sutherland–Hodgman 裁切就够了；引 P1 的 geometry 包会让采样层反向依赖渲染层，
 * 两边改一处就得同步改另一处。
 */
public final class LevelCrossingSampler {

    /** 中心线采样步长（格）。0.25 是"一格四个点"，足够让 1/16 精度的道口不出折角。 */
    public static final double SAMPLE_STEP = 0.25D;

    /** 单条轨道的中心线点数上限，与 C2S 校验里的上限保持一致。 */
    public static final int MAX_POINTS_PER_TRACK = 64;

    /** 走廊与本格的交叠长度低于这个值就不算命中，避免"擦个边"就长出一段轨道。 */
    public static final double MIN_OVERLAP = 0.05D;

    /** MTR3 原版轨面高度：轨枕 1/16 格 + 钢轨 0.01 格。 */
    public static final double DEFAULT_RAIL_TOP_Y = 0.0625D + 0.01D;

    /** 地面层厚度：铺面顶面往下固定 0.10 格就是地面层顶面（等厚，不做坡度跟随）。 */
    public static final double GROUND_THICKNESS = 0.10D;

    private static final double MAX_PITCH_DEG = 45.0D;
    private static final double MAX_ROLL_DEG = 20.0D;

    /** 求纵坡时在轨道中点两侧各取 1.5 格，合计 3 格 —— 太短会被单个采样噪声放大，太长会跨过变坡点。 */
    private static final double PITCH_PROBE = 1.5D;

    /** 单条轨道最多算多少段，防止超长轨道把一次重算拖成秒级。 */
    private static final int MAX_SEGMENTS = 4096;

    private static final double FALLBACK_GAUGE = 1.435D;

    /** 轨道模型包围盒顶的静态缓存（按模型路径），只算一次。{@code -1} 表示算过但没算出来。 */
    private static final Map<String, Double> MODEL_TOP_CACHE = new ConcurrentHashMap<>();

    private LevelCrossingSampler() {
    }

    /**
     * 采样本格，产出可以直接渲染/同步的 {@link CellSnapshot}。
     * <p>
     * 只在客户端调用：轨道数据住在客户端的轨道表里，服务端根本没有（因此服务端只能收 C2S 快照）。
     */
    public static CellSnapshot snapshot(Level level, BlockPos pos, CrossingSettings cfg) {
        CellSnapshot snapshot = new CellSnapshot();
        snapshot.computedAt = level.getGameTime();

        // 半径 = 本格(1.0) + 外扩 + 1.0 余量：外扩会让铺面伸出方块，余量给"轨道只是斜穿一角"留采样空间
        double radius = 1.0D + cfg.outerExtend + 1.0D;
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;

        List<RailSampleSource.RailPath> paths;
        try {
            paths = RailSampleSources.get().nearby(level, centerX, centerZ, radius);
        } catch (RuntimeException e) {
            Main.LOGGER.warn("[FangSu] Level crossing: rail query failed at {}", pos, e);
            paths = List.of();
        }

        List<Candidate> candidates = new ArrayList<>();
        for (RailSampleSource.RailPath path : paths) {
            try {
                Candidate candidate = evaluate(pos, cfg, path);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            } catch (RuntimeException e) {
                // 单条轨道算炸不应该毁掉整格的铺面
                Main.LOGGER.warn("[FangSu] Level crossing: skipping rail '{}' at {}", path.railId(), pos, e);
            }
        }

        // 交叠越长越主：主轨决定铺面的俯仰、超高与基准高度
        candidates.sort(Comparator.comparingDouble(Candidate::overlap).reversed());
        int keep = Math.min(candidates.size(), CellSnapshot.MAX_TRACKS);
        for (int i = 0; i < keep; i++) {
            TrackSlice slice = candidates.get(i).slice();
            slice.primary = i == 0;
            snapshot.tracks.add(slice);
        }

        if (keep > 0) {
            Candidate primary = candidates.get(0);
            snapshot.pitchDeg = primaryPitch(primary.geometry());
            snapshot.rollDeg = primaryRoll(primary.geometry());
            snapshot.deckTopY = primary.slice().railTopY - cfg.deckInset;
        } else {
            // 没有轨道：铺面退化成平的一层，只铺地面 + 铺面
            snapshot.pitchDeg = 0;
            snapshot.rollDeg = 0;
            snapshot.deckTopY = DEFAULT_RAIL_TOP_Y - cfg.deckInset;
        }
        snapshot.groundTopY = snapshot.deckTopY - GROUND_THICKNESS;

        // 方块被放歪（比如架空轨道下方而不是轨面）时，高度可能跑出同步区间。
        // 这里夹回来，而不是让服务端校验把整包快照丢掉 —— 丢掉的话客户端会每帧重算再每帧被拒。
        snapshot.deckTopY = clampFinite(snapshot.deckTopY, 0.0D, 1.0D, DEFAULT_RAIL_TOP_Y - cfg.deckInset);
        snapshot.groundTopY = clampFinite(snapshot.groundTopY, -0.2D, snapshot.deckTopY, snapshot.deckTopY - GROUND_THICKNESS);
        // 钢轨顶面偏置（原版轨顶之上再抬多少）：只记在快照上，几何侧统一在 DeckSurface.railTopY 加
        snapshot.railTopBias = cfg.railTopBias;

        // 出口量化：相邻格各自用自己的采样点算高度，浮点级差异会让两块铺面在公共边上差一丝，
        // 密铺时就是一条细小台阶。量化到固定格点后，数值上几乎相同的相邻格会落到同一个值。
        // <b>量化必须在世界坐标里做</b>（传本方块的 Y）：MTR 画立体轨道时会按位置加 ≤0.09° 的伪随机滚转、
        // 且按 repeatInterval 分段旋转，所以同一条轨在不同采样点的轨顶高度本就有亚毫米抖动；
        // 只在方块局部量化，会让「世界高度相同但方块 Y 不同」的相邻两格落到不同格点（最多差 1/128 格）。
        quantizeSnapshot(snapshot, pos.getY());

        return snapshot;
    }

    // ==================== 出口量化 ====================

    /**
     * 高度量化步长：<b>1/256 格 = 0.00390625（0.4mm）</b>。
     * <p>
     * 历史上是 1/64 = 15.6mm，最大绝对偏差半个步长 = 8mm —— 用户看到的就是「轨面跟原版轨道对不上」。
     * 1/256 把上限压到 0.4mm（亚毫米级，肉眼不可见），同时仍然满足量化本来的目的：
     * 量化是在<b>世界坐标</b>上做的（见 {@link #quantizeSnapshot(CellSnapshot, int)}），
     * 所以「世界高度相同、所在方块 Y 不同」的相邻格依旧落到同一格点。
     */
    public static final double HEIGHT_QUANTUM = 1.0D / 256.0D;

    /** 角度量化步长：0.01°。 */
    public static final double ANGLE_QUANTUM = 0.01D;

    /**
     * 把快照里的连续量量化到固定格点，让相邻格的平面在公共边上落到同一高度。
     * <p>
     * 背景：{@code deckTopY} 由本格自己的采样结果算出（{@code 本格最近轨道点的局部 railTopY - deckInset}），
     * 相邻两格的采样点不同 → 浮点级差异 → 两块铺面在公共边不同高，密铺时看得到细台阶/细缝。
     * <p>
     * <b>代价</b>：绝对高度最多偏差半个步长 = <b>1/128 格（约 8mm）</b>；换来的是相邻格严格同高。
     * <p>
     * {@code groundTopY} 一律由量化后的 {@code deckTopY} 重算，<b>不</b>独立量化 ——
     * 否则两者的固定差（{@link #GROUND_THICKNESS}）会漂，地面层与铺面厚度就不再是常数。
     */
    public static void quantizeSnapshot(CellSnapshot snapshot) {
        quantizeSnapshot(snapshot, 0);
    }

    /**
     * 世界坐标量化（{@code blockY} = 本方块的世界 Y）。
     * <p>
     * 「先加方块 Y → 量化 → 再减回」：量化格点落在<b>世界</b>网格上，于是「世界高度相同、
     * 所在方块 Y 不同」的相邻两格会落到同一个格点，密铺时公共边严格同高。
     * 若只在方块局部量化，两格的局部基准不同，同样的世界高度会落到不同格点（最多差 1/128 格 ≈ 8mm）。
     * <p>
     * {@code groundTopY} 仍由量化后的 {@code deckTopY} 重算（见下面注释）。
     */
    public static void quantizeSnapshot(CellSnapshot snapshot, int blockY) {
        if (snapshot == null) {
            return;
        }
        snapshot.deckTopY = quantizeHeight(blockY + snapshot.deckTopY) - blockY;
        snapshot.groundTopY = snapshot.deckTopY - GROUND_THICKNESS;
        snapshot.pitchDeg = quantizeAngle(snapshot.pitchDeg);
        snapshot.rollDeg = quantizeAngle(snapshot.rollDeg);
        if (snapshot.tracks != null) {
            for (TrackSlice slice : snapshot.tracks) {
                if (slice != null) {
                    slice.railTopY = quantizeHeight(blockY + slice.railTopY) - blockY;
                }
            }
        }
    }

    /** 高度量化到 1/256 格（0.4mm）：把「轨面差 8mm」压到亚毫米。 */
    public static double quantizeHeight(double value) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return Math.round(value * 256.0D) / 256.0D;
    }

    /** 角度量化到 0.01°。 */
    public static double quantizeAngle(double value) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return Math.round(value * 100.0D) / 100.0D;
    }

    // ==================== 单条轨道的评估 ====================

    /**
     * 评估一条候选轨道：采样 → 算交叠 → 组装 {@link TrackSlice}。
     *
     * @return 交叠不足或几何退化时返回 {@code null}
     */
    private static Candidate evaluate(BlockPos pos, CrossingSettings cfg, RailSampleSource.RailPath path) {
        if (!(path.handle() instanceof RailSampleSource.RailGeometry geometry)) {
            return null;
        }
        double length = geometry.length();
        if (!Double.isFinite(length) || length <= 1.0E-4D) {
            return null;
        }

        LevelCrossingGaugeTable.GaugeInfo info = LevelCrossingGaugeTable.getInstance().lookup(path.railId());
        // 参数表可能被手改坏，先在采样侧夹到 C2S 校验允许的区间，免得算得出来却发不出去
        double gauge = clampFinite(info.gauge(), 0.5001D, 3.0D, FALLBACK_GAUGE);
        double grooveOuter = clampFinite(info.grooveOuter(), 1.0E-3D, 2.0D, 0.72D);
        double grooveInner = clampFinite(info.grooveInner(), 1.0E-4D, grooveOuter - 1.0E-4D, grooveOuter * 0.5D);
        double grooveDepthSource = cfg.grooveDepthOverride >= 0 ? cfg.grooveDepthOverride : info.grooveDepth();
        double grooveDepth = clampFinite(grooveDepthSource, 1.0E-4D, 0.05D, 0.01D);

        double halfWidth = gauge * 0.5D + grooveOuter;

        int segments = (int) Math.ceil(length / SAMPLE_STEP);
        segments = Math.max(1, Math.min(segments, MAX_SEGMENTS));

        double[] localX = new double[segments + 1];
        double[] localZ = new double[segments + 1];
        double[] worldY = new double[segments + 1];
        for (int i = 0; i <= segments; i++) {
            Vec3 point = geometry.position(length * i / segments);
            localX[i] = point.x - pos.getX();
            localZ[i] = point.z - pos.getZ();
            worldY[i] = point.y;
        }

        double minEdge = -cfg.outerExtend;
        double maxEdge = 1.0D + cfg.outerExtend;
        double overlap = overlapLength(localX, localZ, halfWidth, minEdge, minEdge, maxEdge, maxEdge);
        if (overlap < MIN_OVERLAP) {
            return null;
        }

        // 只保留落在"本格外扩 + 走廊半宽"里的中心线点：这是 P1 生成条带要用的那一截
        List<double[]> kept = new ArrayList<>();
        int nearestIndex = -1;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i <= segments; i++) {
            double dx = localX[i] - 0.5D;
            double dz = localZ[i] - 0.5D;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
            if (localX[i] < minEdge - halfWidth || localX[i] > maxEdge + halfWidth
                    || localZ[i] < minEdge - halfWidth || localZ[i] > maxEdge + halfWidth) {
                continue;
            }
            kept.add(new double[]{localX[i], localZ[i], worldY[i]});
        }

        if (kept.size() < 2) {
            // 走廊只擦到一格角：退化成"离格中心最近的两点"，保证下游至少有一条线可以扫掠
            kept.clear();
            int from = Math.max(0, Math.min(segments - 1, nearestIndex - 1));
            for (int i = from; i <= Math.min(segments, from + 1); i++) {
                kept.add(new double[]{localX[i], localZ[i], worldY[i]});
            }
        }

        TrackSlice slice = new TrackSlice();
        slice.railId = path.railId();
        slice.gauge = gauge;
        slice.grooveOuter = grooveOuter;
        slice.grooveInner = grooveInner;
        slice.grooveDepth = grooveDepth;
        // 槽中心（轨头中心）：钢面取「整条钢轨可见轮廓」后，槽位不能简单取内外沿中点
        slice.grooveCenter = info.grooveCenter();
        // 横向可校准：JSON 里的值 × 玩家在界面里调的倍率 / + 增量（默认 1.0 / 0.0 = 不干预）
        slice.lateralScale = LevelCrossingGaugeTable.clampLateralScale(
                info.lateralScale() * (cfg == null ? 1.0 : cfg.lateralScaleMul));
        slice.lateralOffset = LevelCrossingGaugeTable.clampLateralOffset(
                info.lateralOffset() + (cfg == null ? 0.0 : cfg.lateralOffsetAdd));
        slice.yawDeg = yawAt(localX, localZ, nearestIndex, segments);
        // 轨顶 = 该轨道在格附近的中心线高度（局部）+ 轨道型号的轨顶偏移
        double centerLocalY = worldY[Math.max(0, Math.min(segments, nearestIndex))] - pos.getY();
        double profileTop = railProfileTop(path.railId(), cfg, info);
        if (Double.isNaN(profileTop)) {
            // 工作线程上拿不到 NTE 模型包围盒：先按默认值占位，并把「未解析」记进快照。
            // 主线程 applyComputedResult 会解析这些 railId 并重新提交一次采样（最多 2 次）。
            slice.railTopResolved = false;
            profileTop = DEFAULT_RAIL_TOP_Y;
        }
        slice.railTopY = centerLocalY + profileTop;
        fillCenterline(slice, kept);

        return new Candidate(overlap, slice, geometry);
    }

    /**
     * 走廊（中心线两侧各 {@code halfWidth}）与方格矩形的交叠长度。
     * <p>
     * 逐 0.25 格分段：每段张成一个梯形走廊四边形，用 Sutherland–Hodgman 裁到矩形里，
     * 累加面积后除以走廊总宽 = "等效覆盖长度"。用面积折算而不是只看中心线，
     * 是因为宽轨即使在格角斜切也应该被算作命中。
     */
    private static double overlapLength(double[] xs, double[] zs, double halfWidth,
                                        double minX, double minZ, double maxX, double maxZ) {
        double area = 0;
        for (int i = 1; i < xs.length; i++) {
            double dx = xs[i] - xs[i - 1];
            double dz = zs[i] - zs[i - 1];
            double segmentLength = Math.sqrt(dx * dx + dz * dz);
            if (segmentLength < 1.0E-9D) {
                continue;
            }
            double nx = -dz / segmentLength * halfWidth;
            double nz = dx / segmentLength * halfWidth;

            // 先做便宜的包围盒剔除：绝大多数段离这一格很远
            double segMinX = Math.min(xs[i - 1], xs[i]) - Math.abs(nx);
            double segMaxX = Math.max(xs[i - 1], xs[i]) + Math.abs(nx);
            double segMinZ = Math.min(zs[i - 1], zs[i]) - Math.abs(nz);
            double segMaxZ = Math.max(zs[i - 1], zs[i]) + Math.abs(nz);
            if (segMaxX < minX || segMinX > maxX || segMaxZ < minZ || segMinZ > maxZ) {
                continue;
            }

            double[] quadX = {xs[i - 1] + nx, xs[i] + nx, xs[i] - nx, xs[i - 1] - nx};
            double[] quadZ = {zs[i - 1] + nz, zs[i] + nz, zs[i] - nz, zs[i - 1] - nz};
            area += clippedArea(quadX, quadZ, minX, minZ, maxX, maxZ);
        }
        return area / (2.0D * halfWidth);
    }

    /** 多边形（凸四边形）与轴对齐矩形的交面积。 */
    private static double clippedArea(double[] xs, double[] zs,
                                      double minX, double minZ, double maxX, double maxZ) {
        List<double[]> polygon = new ArrayList<>(4);
        for (int i = 0; i < xs.length; i++) {
            polygon.add(new double[]{xs[i], zs[i]});
        }
        for (int edge = 0; edge < 4; edge++) {
            polygon = clipHalfPlane(polygon, edge, minX, minZ, maxX, maxZ);
            if (polygon.size() < 3) {
                return 0;
            }
        }
        double twiceArea = 0;
        for (int i = 0; i < polygon.size(); i++) {
            double[] a = polygon.get(i);
            double[] b = polygon.get((i + 1) % polygon.size());
            twiceArea += a[0] * b[1] - b[0] * a[1];
        }
        return Math.abs(twiceArea) * 0.5D;
    }

    /** Sutherland–Hodgman 单条边界裁切；边界 0/1 是 X 的上下界，2/3 是 Z 的上下界。 */
    private static List<double[]> clipHalfPlane(List<double[]> polygon, int edge,
                                                double minX, double minZ, double maxX, double maxZ) {
        List<double[]> out = new ArrayList<>(polygon.size() + 2);
        int count = polygon.size();
        for (int i = 0; i < count; i++) {
            double[] current = polygon.get(i);
            double[] previous = polygon.get((i + count - 1) % count);
            boolean currentInside = insideHalfPlane(current, edge, minX, minZ, maxX, maxZ);
            boolean previousInside = insideHalfPlane(previous, edge, minX, minZ, maxX, maxZ);
            if (currentInside != previousInside) {
                out.add(intersectHalfPlane(previous, current, edge, minX, minZ, maxX, maxZ));
            }
            if (currentInside) {
                out.add(current);
            }
        }
        return out;
    }

    private static boolean insideHalfPlane(double[] point, int edge,
                                           double minX, double minZ, double maxX, double maxZ) {
        return switch (edge) {
            case 0 -> point[0] >= minX;
            case 1 -> point[0] <= maxX;
            case 2 -> point[1] >= minZ;
            default -> point[1] <= maxZ;
        };
    }

    private static double[] intersectHalfPlane(double[] from, double[] to, int edge,
                                               double minX, double minZ, double maxX, double maxZ) {
        boolean onX = edge <= 1;
        double limit = switch (edge) {
            case 0 -> minX;
            case 1 -> maxX;
            case 2 -> minZ;
            default -> maxZ;
        };
        double fromValue = onX ? from[0] : from[1];
        double toValue = onX ? to[0] : to[1];
        double delta = toValue - fromValue;
        double t = Math.abs(delta) < 1.0E-12D ? 0 : (limit - fromValue) / delta;
        return new double[]{from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t};
    }

    // ==================== 姿态 ====================

    /** 取离格中心最近那一段的切向。方向约定与 MTR 一致：{@code atan2(z, x)}。 */
    private static double yawAt(double[] xs, double[] zs, int nearestIndex, int segments) {
        int index = Math.max(0, Math.min(segments - 1, nearestIndex));
        double dx = xs[index + 1] - xs[index];
        double dz = zs[index + 1] - zs[index];
        if (Math.sqrt(dx * dx + dz * dz) < 1.0E-6D && index > 0) {
            dx = xs[index] - xs[index - 1];
            dz = zs[index] - zs[index - 1];
        }
        if (Math.sqrt(dx * dx + dz * dz) < 1.0E-6D) {
            return 0;
        }
        return Math.toDegrees(Math.atan2(dz, dx));
    }

    /** 主轨纵坡：中点两侧各 1.5 格的 {@code getPosition} 高差求 atan。 */
    private static double primaryPitch(RailSampleSource.RailGeometry geometry) {
        double length = geometry.length();
        if (!Double.isFinite(length) || length <= 1.0E-4D) {
            return 0;
        }
        double mid = length * 0.5D;
        double start = Math.max(0, mid - PITCH_PROBE);
        double end = Math.min(length, start + 2 * PITCH_PROBE);
        if (end - start < 1.0E-3D) {
            return 0;
        }
        Vec3 from = geometry.position(start);
        Vec3 to = geometry.position(end);
        double horizontal = Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z));
        if (horizontal < 1.0E-6D) {
            return 0;
        }
        double degrees = Math.toDegrees(Math.atan2(to.y - from.y, horizontal));
        return clampFinite(degrees, -MAX_PITCH_DEG, MAX_PITCH_DEG, 0);
    }

    /**
     * 主轨超高：问实现要一个"度"，然后夹到同步区间。
     * <p>
     * 数值怎么来由 {@link RailSampleSource.RailGeometry#tiltDegrees()} 决定 —— MTR3 读两端节点姿态取平均，
     * MTR4 读 {@code RailMath} 的 tilt 曲线。采样器只负责钳位，不做任何版本判断，
     * 这样两树的采样算法才能逐字同源。
     */
    private static double primaryRoll(RailSampleSource.RailGeometry geometry) {
        try {
            return clampFinite(geometry.tiltDegrees(), -MAX_ROLL_DEG, MAX_ROLL_DEG, 0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ==================== 轨顶高度 ====================

    /**
     * 轨道型号的"轨顶相对中心线"的高度，四条来源按优先级：
     * <ol>
     *   <li>{@code cfg.railTopOverride >= 0}（玩家手填）</li>
     *   <li>{@code LevelCrossingGaugeTable} 的 {@code railTopY >= 0}</li>
     *   <li>NTE 轨道模型包围盒顶（按模型路径静态缓存）</li>
     *   <li>MTR3 原版轨面 {@code 0.0625 + 0.01}</li>
     * </ol>
     */
    private static double railProfileTop(String railId, CrossingSettings cfg, LevelCrossingGaugeTable.GaugeInfo info) {
        if (cfg.railTopOverride >= 0) {
            return cfg.railTopOverride;
        }
        if (info.railTopY() >= 0) {
            return info.railTopY();
        }
        double modelTop = modelTop(railId);
        if (Double.isNaN(modelTop)) {
            // <b>未解析</b>（工作线程上不能碰资源）：把标记原样抛给调用方，
            // 由上层记下「这条轨的轨顶还没解析」，在主线程解析后<b>重新采样</b>自愈。
            // 绝不在这里静默用默认值 —— 那会让装了 NTE 的玩家铺面整体错位。
            return Double.NaN;
        }
        if (modelTop >= 0) {
            return modelTop;
        }
        return DEFAULT_RAIL_TOP_Y;
    }

    private static double modelTop(String railId) {
        NteRail rail = NteRailManager.getInstance().getRail(railId);
        if (rail == null || rail.model() == null || rail.model().isEmpty()) {
            return -1;
        }
        String modelPath = rail.model();
        Double cached = MODEL_TOP_CACHE.get(modelPath);
        if (cached != null) {
            return cached;
        }
        // 资源系统没就绪时不要缓存失败结果，否则一次过早的调用会把 -1 永久钉在表里
        if (!ResourceUtil.isInitialized()) {
            return -1;
        }
        // <b>线程安全红线</b>：{@code ResourceUtil.loadModel} 会碰客户端资源/GL，只能在主线程调用。
        // 采样跑在 {@code fangsu-levelcrossing-worker} 上时会走到这里：
        // 返回 <b>NaN = 「未解析」标记</b>（不是 -1，-1 表示"确实没有模型，用默认值"）。
        // 上层据此记录未解析的 railId，在主线程解析完再重新采样一次（自愈）。
        if (!onClientMainThread()) {
            Main.debug("[levelcrossing] modelTop unresolved (worker thread) rail={}", railId);
            return Double.NaN;
        }
        double top = loadModelTop(modelPath);
        MODEL_TOP_CACHE.put(modelPath, top);
        return top;
    }

    /**
     * <b>主线程预取</b>：解析某个 railId 的 NTE 模型包围盒顶并写入静态缓存。
     * 工作线程采样时只读这份缓存；未命中会返回 NaN 触发上层自愈重采样。
     *
     * @return true = 缓存里已经有可用值（或确实没有模型 → 用默认值）
     */
    public static boolean resolveModelTop(String railId) {
        if (railId == null || !onClientMainThread()) return false;
        double top = modelTop(railId);   // 主线程：未命中的会在这里加载并写缓存
        return !Double.isNaN(top);
    }

    /** 是否在客户端主线程（只有主线程允许碰资源/GL）。 */
    private static boolean onClientMainThread() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            return mc != null && mc.isSameThread();
        } catch (Throwable t) {
            return false;
        }
    }

    private static double loadModelTop(String modelPath) {
        try {
            RawModel rawModel = ResourceUtil.loadModel(new ResourceLocation(modelPath), false);
            if (rawModel == null) {
                return -1;
            }
            double maxY = Double.NEGATIVE_INFINITY;
            for (RawMesh mesh : rawModel.meshList.values()) {
                if (mesh == null || mesh.vertices == null) {
                    continue;
                }
                for (Vertex vertex : mesh.vertices) {
                    if (vertex != null && vertex.position != null) {
                        maxY = Math.max(maxY, vertex.position.y());
                    }
                }
            }
            return maxY > Double.NEGATIVE_INFINITY ? maxY : -1;
        } catch (Exception e) {
            Main.LOGGER.warn("[FangSu] Level crossing: failed to read rail model '{}'", modelPath, e);
            return -1;
        }
    }

    // ==================== 中心线落表 ====================

    /**
     * 把保留下来的中心线点写进 {@link TrackSlice}，超过上限时等距抽稀。
     * <p>
     * 抽稀必须<B>保留首尾</B>：P1 靠首尾点确定条带的两端，丢了会把轨道截短一截。
     */
    private static void fillCenterline(TrackSlice slice, List<double[]> points) {
        int size = points.size();
        int stride = Math.max(1, (int) Math.ceil((double) size / MAX_POINTS_PER_TRACK));
        List<Integer> picked = new ArrayList<>();
        for (int i = 0; i < size; i += stride) {
            picked.add(i);
        }
        if (picked.isEmpty() || picked.get(picked.size() - 1) != size - 1) {
            picked.add(size - 1);
        }
        int count = Math.min(picked.size(), MAX_POINTS_PER_TRACK);
        double[] xs = new double[count];
        double[] zs = new double[count];
        for (int i = 0; i < count; i++) {
            double[] point = points.get(picked.get(i));
            xs[i] = point[0];
            zs[i] = point[1];
        }
        slice.xs = xs;
        slice.zs = zs;
    }

    // ==================== 工具 ====================

    /**
     * 把 {@code value} 夹进 {@code [min, max]}；非有限值直接返回 {@code fallback}。
     * <p>
     * 采样链路上任何一步算出 NaN 都会让整包快照被服务端拒收，所以每个出口都要过一遍这里。
     */
    private static double clampFinite(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** 一条候选轨道的中间结果：交叠长度决定排序，几何句柄留着算姿态。 */
    private record Candidate(double overlap, TrackSlice slice, RailSampleSource.RailGeometry geometry) {
    }
}
