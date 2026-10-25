package com.fangsu.turnout;

import com.fangsu.Main;
import com.fangsu.levelcrossing.LevelCrossingStyles;
import com.fangsu.levelcrossing.sample.RailSampleSource;
import com.fangsu.levelcrossing.sample.RailSampleSources;
import com.fangsu.turnout.geometry.TurnoutGeometry;
import com.fangsu.turnout.geometry.TurnoutMesh;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 道岔几何的<b>唯一构建入口</b>：采样 → 静态部分（基本轨 / 辙叉 / 护轨）→ 两根尖轨，并带一份小缓存。
 *
 * <p><b>采样：沿「多段轨道」走，而不是只取直连那一根</b>。MTR 的轨道是<b>节点-节点分段</b>存的，
 * 蓝标记(toe) 与红标记(远端) 之间经常隔着一两个中间节点，一条腿只能由多段拼出来。做法：</p>
 * <ol>
 *   <li>首选绑定里随包下发的节点路径（{@code TurnoutBinding#straightPath()} / {@code #branchPath()}，
 *       服务端算好、客户端直接读，天然两端一致），按节点对逐段取几何；</li>
 *   <li>路径为空（老存档 / 加载失败）时，用 {@link RailSampleSource#nearby} + 各段的 {@code pos1/pos2}
 *       <b>按节点身份链式跟随</b>（每段走完从它的出口节点找下一段，跳数上限 {@link #MAX_HOPS}），
 *       并打一行 {@code [turnout][diag] geometry path fallback}（限频）；</li>
 *   <li>每段按 {@link TurnoutGeometry#SAMPLE_STEP}=0.05 格连续采样，<b>把所有点拼成一条连续中心线</b>
 *       之后才统一求切向与弧长——所以拼缝处切向天然连续；超高按点各自携带（各段自己的 {@code tiltDegrees()}）。</li>
 * </ol>
 * <p>站点一律留在以 anchor 为原点的本地坐标系里（{@code frame.toLocal} 已做过平移，不再额外补偿）。</p>
 *
 * <p><b>容错</b>：任何一步失败都只返回 {@link TurnoutMesh#EMPTY} 并 warn 一次（按原因去重），
 * 绝不在渲染线程抛异常、也绝不刷屏。仅无路径的旧存档在采样失败时退回弦线几何；新绑定等待完整同步，
 * 缺数据时由方块实体自动重试。</p>
 *
 * <p><b>缓存</b>：键 = 绑定 + 解析后的轨道 id，容量 8（LRU）。几何是纯数据，多份共用没问题：
 * 每个方块实体各自 {@code uploadLater}（内部会拷贝材质），互不影响。</p>
 */
public final class TurnoutGeometryBuilder {

    /** 候选轨道「离 toe 最近点」的允许误差（格）：超过它说明这条轨道不经过 toe。 */
    private static final double MAX_TOE_DISTANCE = 1.0D;

    /** 采样腿末端到目标节点的允许误差（格）：超过说明方向选错了。 */
    private static final double MAX_END_DISTANCE = 6.0D;

    /** 粗扫步长（格）：先粗找最近参数，再细化。 */
    private static final double SCAN_STEP = 0.2D;

    /** 缓存容量。 */
    private static final int CACHE_CAPACITY = 8;
    /** 同时限制几何总量，不能用“八个”超大道岔占满客户端堆。正在使用的模型不受此缓存上限裁减。 */
    private static final int CACHE_VERTEX_LIMIT = 500_000;

    /** 采样腿最多取多长（格）：防止一条超长轨道把顶点数拖爆。 */
    private static final double MAX_LEG_LENGTH = 96.0D;

    /** 一次性告警去重（同类问题只打一次）。 */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private static final Map<CacheKey, TurnoutMesh> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<CacheKey, TurnoutMesh>(CACHE_CAPACITY, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, TurnoutMesh> eldest) {
                    return size() > CACHE_CAPACITY;
                }
            });

    private TurnoutGeometryBuilder() {
    }

    /** 缓存键：绑定（record，自带 equals）+ 解析后的轨道 id。 */
    private record CacheKey(TurnoutBinding binding, String railId) {
    }

    /**
     * 构建（或从缓存取）一个道岔的几何。
     *
     * @param railModelId 当前选择的轨道模型 id（为 null 时退用绑定里的）
     * @return 永不为 null；无几何时是 {@link TurnoutMesh#EMPTY}
     */
    public static TurnoutMesh build(Level level, TurnoutBinding binding, String railModelId) {
        if (level == null || binding == null || binding.anchor() == null) {
            return TurnoutMesh.EMPTY;
        }
        final TurnoutRailParams params = TurnoutRailParams.resolve(
                railModelId != null && !railModelId.isEmpty() ? railModelId : binding.railModelId());
        final CacheKey key = new CacheKey(binding, params.railId());
        final TurnoutMesh cached = CACHE.get(key);
        if (cached != null) return cached;

        TurnoutMesh mesh;
        try {
            mesh = buildUncached(level, binding, params);
        } catch (java.util.concurrent.CancellationException cancelled) {
            throw cancelled;
        } catch (RuntimeException e) {
            warnOnce("build-failed", "几何构建异常 toe=" + binding.toe() + " ex="
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            mesh = TurnoutMesh.EMPTY;
        }
        if (mesh != null && mesh.totalFaces() > 0) {
            // 大模型的去重在几何工作线程做，避免首次上传在游戏主线程重新哈希几十万个顶点。
            if(mesh.staticParts()!=null)mesh.staticParts().prepareForUpload();
            for(TurnoutMesh.BladePart part:mesh.parts())part.model().prepareForUpload();
            synchronized(CACHE) {
                if(mesh.totalVertices()<=CACHE_VERTEX_LIMIT) {
                    CACHE.put(key,mesh);
                    long vertices=0;for(TurnoutMesh entry:CACHE.values())vertices+=entry.totalVertices();
                    var iterator=CACHE.entrySet().iterator();
                    while(vertices>CACHE_VERTEX_LIMIT&&iterator.hasNext()){vertices-=iterator.next().getValue().totalVertices();iterator.remove();}
                }
            }
        }
        return mesh == null ? TurnoutMesh.EMPTY : mesh;
    }

    /** 清空缓存（换维度/重载时由渲染侧调用，避免旧绑定一直占着 LRU 槽位）。 */
    public static void clearCache() {
        CACHE.clear();
    }

    /** 失败重试和手动重建只失效当前绑定，不能反复清空其他控制器的缓存。 */
    public static void invalidate(TurnoutBinding binding,String model) {
        TurnoutRailParams params=TurnoutRailParams.resolve(model);
        CACHE.remove(new CacheKey(binding,params.railId()));
    }

    // ==================== 主流程 ====================

    private static TurnoutMesh buildUncached(Level level, TurnoutBinding binding, TurnoutRailParams params) {
        // 先把钢轨贴图的「存在性」判一次（缺图时两条材质桶都回退兜底贴图，绝不显示错误贴图）。
        // 该检查在工作线程是安全的：内部用 Minecraft.isSameThread() 判定，非主线程走类路径探测。
        LevelCrossingStyles.ensureRailTextures();
        final Vec3 anchor = binding.anchor();
        final TurnoutGeometry.Frame frame =
                new TurnoutGeometry.Frame(anchor.x, anchor.y, anchor.z, binding.baseYawDeg());

        final Sampler sampler = new Sampler(level, frame);
        if (binding.kind() != TurnoutKind.SINGLE && binding.kind() != TurnoutKind.DOUBLE) {
            List<List<TurnoutGeometry.Station>> routes = new ArrayList<>();
            for (TurnoutTrack track : binding.tracks()) {
                // 所有轨段共用整个道岔的 frame；路径起点可以在道岔另一头。
                TurnoutBinding legBinding = new TurnoutBinding(track.start(), track.end(), track.end(),
                        binding.railModelId(), anchor, binding.baseYawDeg(), track.nodes(), track.nodes());
                Leg leg = sampleLeg(sampler, legBinding, track.nodes(), track.end(), "compound");
                if (!leg.sampled() || leg.stations().size() < 2) return TurnoutMesh.EMPTY;
                routes.add(leg.stations());
            }
            return com.fangsu.turnout.geometry.CompoundTurnoutBuilder.build(binding, routes, params);
        }
        Leg straightLeg = sampleLeg(sampler, binding, binding.straightPath(), binding.straightEnd(), "straight");
        List<TurnoutGeometry.Station> straight = straightLeg.stations();
        if (straight.size() < 2) {
            warnOnce("no-straight", "直股采样失败 toe=" + binding.toe());
            return TurnoutMesh.EMPTY;
        }
        Leg branchLeg = sampleLeg(sampler, binding, binding.branchPath(), binding.branchEnd(), "branch");
        List<TurnoutGeometry.Station> branch = branchLeg.stations();
        boolean hasBranch = branch.size() >= 2;
        final boolean sampled = straightLeg.sampled() && branchLeg.sampled();
        if (!binding.straightPath().isEmpty() && !binding.branchPath().isEmpty() && (!sampled || !hasBranch)) {
            return TurnoutMesh.EMPTY;
        }

        // 曲股朝本地哪一侧张开：取采样链上 |z| 最大的站
        int branchSign = 1;
        if (hasBranch) {
            double maxZ = 0;
            for (TurnoutGeometry.Station s : branch) {
                if (Math.abs(s.z()) > Math.abs(maxZ)) maxZ = s.z();
            }
            if (Math.abs(maxZ) > 0.05D) branchSign = maxZ > 0 ? 1 : -1;
        }

        return com.fangsu.turnout.geometry.SingleTurnoutBuilder.build(
                straight, branch, params, branchSign, sampled);
    }

    // ==================== 多段采样 ====================


    /** 跳数上限与服务端路径校验一致。 */
    private static final int MAX_HOPS = TurnoutTopology.MAX_HOPS;

    /** 拼缝处小于它就视为同一个点（不重复追加）；大于它就要补桥接点，否则扫掠会拉出一道横跨的四边形。 */
    private static final double BRIDGE_EPS = 0.02D;

    /**
     * 跨段接缝的合并阈值（格）：两段在共享节点处的端点位置会有极小的数值差，
     * 落在阈值内就把两点<b>取平均</b>合成一个站点（复用同一站，不是各生成一份），接缝处就没有断口。
     */
    private static final double SEAM_MERGE = 0.10D;

    /** 桥接点数量上限：防御脏数据（两段节点相距极远时也只补这么多）。 */
    private static final int MAX_BRIDGE_POINTS = 64;

    /** 找「下一段轨道」时的搜索半径（格）：节点周围一小圈足够。 */
    private static final double NEXT_RAIL_RADIUS = 4.0D;

    /** 路径兜底日志的限频帧数（按构建次数计，避免刷屏）。 */
    private static final int FALLBACK_LOG_TICKS = 200;

    /**
     * 一个采样点：本地坐标 + 该段自己的超高（弧度）。
     * <p>
     * 为什么 tilt 要按点存：一条腿由多段轨道拼成，各段的 {@code tiltDegrees()} 不同；
     * 拼成一条中心线后统一求切向/弧长，超高必须跟着点走才不会在拼缝处突变。
     */
    private record Sample(double x, double y, double z, double tilt) {
    }

    /** 一条轨道腿：站点 + 是否真的采到（false = 弦线兜底）。 */
    private record Leg(List<TurnoutGeometry.Station> stations, boolean sampled) {
    }

    /** 链式跟随的中间结果：连续采样点 + 是否真的走到了目标节点。 */
    private record Chain(List<Sample> samples, boolean reached) {
    }

    /**
     * 一次 build 内共享的采样器：持 {@code nearby} 的结果缓存。
     * <p>
     * 为什么要缓存：多段 × 多候选会反复问同一个节点附近有哪些轨道，
     * 而 MTR 的 {@code nearby} 是一次空间扫描，一次构建里重复十几次纯属浪费。
     */
    private static final class Sampler {

        private final Level level;
        private final TurnoutGeometry.Frame frame;
        private final Map<Long, List<RailSampleSource.RailGeometry>> cache = new java.util.HashMap<>();

        Sampler(Level level, TurnoutGeometry.Frame frame) {
            this.level = level;
            this.frame = frame;
        }

        TurnoutGeometry.Frame frame() {
            return frame;
        }

        Level level() {
            return level;
        }

        /** 某个世界点附近的轨道（带缓存；异常时返回空表，绝不上抛）。 */
        List<RailSampleSource.RailGeometry> near(double worldX, double worldZ, double radius) {
            long key = (((long) Math.round(worldX * 4.0D)) << 40)
                    ^ (((long) Math.round(worldZ * 4.0D)) << 12)
                    ^ Math.round(radius * 4.0D);
            List<RailSampleSource.RailGeometry> hit = cache.get(key);
            if (hit != null) return hit;
            List<RailSampleSource.RailGeometry> out = new ArrayList<>();
            try {
                RailSampleSource source = RailSampleSources.get();
                if (source != null) {
                    List<RailSampleSource.RailPath> paths = source.nearby(level, worldX, worldZ, radius);
                    if (paths != null) {
                        for (RailSampleSource.RailPath path : paths) {
                            if (path != null && path.handle() instanceof RailSampleSource.RailGeometry geo) {
                                out.add(geo);
                            }
                        }
                    }
                }
            } catch (RuntimeException e) {
                warnOnce("nearby", "轨道采样异常 ex=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            cache.put(key, out);
            return out;
        }
    }

    // ==================== 轨道几何读取 ====================

    private static double safeLength(RailSampleSource.RailGeometry geo) {
        try {
            double length = geo.length();
            return Double.isFinite(length) ? length : 0.0D;
        } catch (RuntimeException e) {
            return 0.0D;
        }
    }

    @Nullable
    private static double[] localAt(RailSampleSource.RailGeometry geo, TurnoutGeometry.Frame frame, double t) {
        Vec3 p;
        try {
            p = geo.position(t);
        } catch (RuntimeException e) {
            return null;
        }
        if (p == null || !Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z)) return null;
        return frame.toLocal(p.x, p.y, p.z);
    }

    private static double tiltRadians(RailSampleSource.RailGeometry geo,double parameter,double direction) {
        try {
            double degrees=geo.tiltDegrees(parameter);
            // 反向走轨时左右法向互换，滚转也必须变号，才能与普通轨道的端部断面吻合。
            return Double.isFinite(degrees)?Math.toRadians(degrees)*direction:0;
        } catch (RuntimeException e) {
            return 0.0D;
        }
    }

    /**
     * 轨道上「离某个本地点最近」的弧长参数：先按 {@link #SCAN_STEP} 粗扫，再在附近细化 10 倍。
     *
     * @return 无有效采样点时返回 NaN
     */
    private static double paramNearestLocal(RailSampleSource.RailGeometry geo, TurnoutGeometry.Frame frame,
                                            double localX, double localZ) {
        double length = safeLength(geo);
        if (!(length > 0.2D)) return Double.NaN;
        double bestT = Double.NaN;
        double bestD = Double.POSITIVE_INFINITY;
        for (double t = 0.0D; t <= length; t += SCAN_STEP) {
            double[] local = localAt(geo, frame, t);
            if (local == null) continue;
            double d = Math.hypot(local[0] - localX, local[2] - localZ);
            if (d < bestD) {
                bestD = d;
                bestT = t;
            }
        }
        if (!Double.isFinite(bestT)) return Double.NaN;
        double lo = Math.max(0.0D, bestT - SCAN_STEP);
        double hi = Math.min(length, bestT + SCAN_STEP);
        for (double t = lo; t <= hi; t += SCAN_STEP * 0.1D) {
            double[] local = localAt(geo, frame, t);
            if (local == null) continue;
            double d = Math.hypot(local[0] - localX, local[2] - localZ);
            if (d < bestD) {
                bestD = d;
                bestT = t;
            }
        }
        return bestT;
    }

    /** 轨道上离某个 MTR 节点最近（按节点中心水平距离）的弧长参数。 */
    private static double paramNearestNode(RailSampleSource.RailGeometry geo, TurnoutGeometry.Frame frame,
                                           BlockPos node) {
        double[] local = nodeLocal(frame, node);
        double length = safeLength(geo);
        double[] start = localAt(geo, frame, 0), end = localAt(geo, frame, length);
        if (start == null || end == null) return Double.NaN;
        // 节点就是分段端点；按端点身份取精确的 0/length，避免粗搜索留下厘米级缺口。
        return Math.hypot(local[0]-start[0], local[2]-start[2])
                <= Math.hypot(local[0]-end[0], local[2]-end[2]) ? 0 : length;
    }

    /** 节点中心在本地坐标系里的位置（高度取 anchor 高度，只用于平面匹配）。 */
    private static double[] nodeLocal(TurnoutGeometry.Frame frame, BlockPos node) {
        return frame.toLocal(node.getX() + 0.5D, frame.anchorY(), node.getZ() + 0.5D);
    }

    /** 轨道「离 anchor（本地原点）最近点」的距离：用于筛掉不经过 toe 的候选。 */
    private static double nearestAnchorDistance(RailSampleSource.RailGeometry geo, TurnoutGeometry.Frame frame) {
        double t = paramNearestLocal(geo, frame, 0.0D, 0.0D);
        if (!Double.isFinite(t)) return Double.POSITIVE_INFINITY;
        double[] local = localAt(geo, frame, t);
        return local == null ? Double.POSITIVE_INFINITY : Math.hypot(local[0], local[2]);
    }

    @Nullable
    private static BlockPos safePos1(RailSampleSource.RailGeometry geo) {
        try {
            return geo.pos1();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static BlockPos safePos2(RailSampleSource.RailGeometry geo) {
        try {
            return geo.pos2();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 这条轨道的两端节点里，是否有一端就是 {@code node}。 */
    private static boolean touchesNode(RailSampleSource.RailGeometry geo, BlockPos node) {
        BlockPos p1 = safePos1(geo);
        BlockPos p2 = safePos2(geo);
        return (p1 != null && p1.equals(node)) || (p2 != null && p2.equals(node));
    }

    /** 这条轨道的另一端（已知一端是 {@code node}）；对不上返回 null。 */
    @Nullable
    private static BlockPos otherNode(RailSampleSource.RailGeometry geo, BlockPos node) {
        BlockPos p1 = safePos1(geo);
        BlockPos p2 = safePos2(geo);
        if (p1 != null && p1.equals(node)) return p2;
        if (p2 != null && p2.equals(node)) return p1;
        return null;
    }

    /** 采样链末端更靠近哪个端点节点（按本地平面距离）。 */
    @Nullable
    private static BlockPos nearestNode(RailSampleSource.RailGeometry geo, TurnoutGeometry.Frame frame, Sample tail) {
        BlockPos p1 = safePos1(geo);
        BlockPos p2 = safePos2(geo);
        if (p1 == null || p2 == null) return p1 != null ? p1 : p2;
        double[] l1 = nodeLocal(frame, p1);
        double[] l2 = nodeLocal(frame, p2);
        double d1 = Math.hypot(tail.x() - l1[0], tail.z() - l1[2]);
        double d2 = Math.hypot(tail.x() - l2[0], tail.z() - l2[2]);
        return d1 <= d2 ? p1 : p2;
    }

    private static double nodeDistanceXZ(BlockPos a, BlockPos b) {
        return Math.hypot((a.getX() + 0.5D) - (b.getX() + 0.5D), (a.getZ() + 0.5D) - (b.getZ() + 0.5D));
    }

    // ==================== 采样（多段） ====================

    /**
     * 采一条「从 toe 出发、走到目标节点」的轨道腿。
     * <p><b>首选</b>绑定里随包下发的节点路径（服务端算好，客户端直接读，两端天然一致）；
     * 路径为空（老存档 / 加载失败）才退回「按 pos1/pos2 的节点链式跟随」，再不行才用弦线兜底。
     *
     * @param path 该股的节点路径（可空；空 = 不可用）
     */
    private static Leg sampleLeg(Sampler sampler, TurnoutBinding binding, @Nullable List<BlockPos> path,
                                 BlockPos endNode, String tag) {
        final Vec3 anchor = binding.anchor();
        final double endX = endNode.getX() + 0.5D;
        final double endZ = endNode.getZ() + 0.5D;
        final double radius = Math.max(8.0D, Math.hypot(endX - anchor.x, endZ - anchor.z) + 4.0D);

        Chain chain = null;
        if (path != null && path.size() >= 2) {
            // 新绑定以权威路径为准。缺失任一段表示同步尚未完成，不能用直线桥接冒充成功。
            chain = followNodes(sampler, path, endNode);
            if (!chain.reached() || chain.samples().size() < 2) return new Leg(List.of(), false);
            return new Leg(toStations(chain.samples()), true);
        }
        List<RailSampleSource.RailGeometry> touching = new ArrayList<>();
        for (RailSampleSource.RailGeometry geo : sampler.near(anchor.x, anchor.z, radius)) {
            if (nearestAnchorDistance(geo, sampler.frame()) <= MAX_TOE_DISTANCE) touching.add(geo);
        }
        if (chain == null || !chain.reached()) {
            // (2) 路径不可用 / 没走通：按节点身份链式跟随（每个「经过 toe 的候选段」各试一次，取评分更好的）
            if (chain == null) logPathFallback(binding, tag);
            for (RailSampleSource.RailGeometry geo : touching) {
                Chain candidate = followNodeChain(sampler, geo, endNode);
                if (candidate.samples().size() < 2) continue;
                if (chain == null || score(candidate, sampler.frame(), endX, endZ)
                        < score(chain, sampler.frame(), endX, endZ)) {
                    chain = candidate;
                }
            }
        }
        if (chain == null || chain.samples().size() < 2) {
            warnOnce("chain-" + tag, "多段采样失败，退回弦线几何 " + tag + " toe=" + binding.toe());
            return new Leg(fallbackLeg(sampler.frame(), endNode), false);
        }

        List<TurnoutGeometry.Station> stations = toStations(chain.samples());
        // 保留实际采样点和端部切向，不能为省面数改变与普通轨道相接的曲线。
        double miss = endDistance(stations, sampler.frame(), endX, endZ);
        boolean sampled = chain.reached();
        if (!sampled) {
            warnOnce("far-" + tag, "采样腿没走到目标节点 " + tag + " toe=" + binding.toe()
                    + " err=" + String.format(java.util.Locale.ROOT, "%.2f", miss));
        }
        return new Leg(stations, sampled);
    }

    /** 候选链的评分：优先「真走到了目标节点」，其次末端离目标节点更近。 */
    private static double score(Chain chain, TurnoutGeometry.Frame frame, double endX, double endZ) {
        return (chain.reached() ? 0.0D : 1000.0D)
                + endDistance(toStations(chain.samples()), frame, endX, endZ);
    }

    /**
     * 按节点序列逐段采样（{@code node[i] → node[i+1]}）。
     * <p>某一段在附近找不到时用两个节点中心的直线补上（bridged），保证不断链。
     *
     * @param endNode 目标节点：末节点与它相等时视为「走通」
     */
    private static Chain followNodes(Sampler sampler, List<BlockPos> path, BlockPos endNode) {
        List<Sample> out = new ArrayList<>();
        boolean complete = true;
        for (int i = 0; i + 1 < path.size(); i++) {
            BlockPos a = path.get(i);
            BlockPos b = path.get(i + 1);
            if (a == null || b == null) {
                complete = false;
                continue;
            }
            RailSampleSource.RailGeometry segment = segmentBetween(sampler, a, b);
            if (segment == null) {
                complete = false;
                if (!appendBridge(out, nodeLocal(sampler.frame(), a), nodeLocal(sampler.frame(), b))) {
                    warnOnce("bridge", "节点之间既没有轨道也无法桥接，采样腿可能不完整");
                }
                continue;
            }
            double tA = paramNearestNode(segment, sampler.frame(), a);
            double tB = paramNearestNode(segment, sampler.frame(), b);
            if (!Double.isFinite(tA) || !Double.isFinite(tB)) {
                complete = false;
                continue;
            }
            appendSegment(out, segment, sampler.frame(), tA, tB);
        }
        BlockPos last = path.isEmpty() ? null : path.get(path.size() - 1);
        boolean reached = complete && last != null && last.equals(endNode);
        return new Chain(out, reached);
    }

    /**
     * 按节点身份链式跟随：从 {@code first} 走完一段，再从它的出口节点找下一段，直到目标节点。
     * <p>只用 {@link RailSampleSource.RailGeometry#pos1()} / {@code pos2()} 做节点匹配，
     * 因此天然支持「多段」；跳数上限 {@link #MAX_HOPS}，且不会回头（同一段只走一次）。
     */
    private static Chain followNodeChain(Sampler sampler, RailSampleSource.RailGeometry first, BlockPos target) {
        List<Sample> out = new ArrayList<>();
        RailSampleSource.RailGeometry current = first;
        boolean reached = false;
        double tEntry = paramNearestLocal(current, sampler.frame(), 0.0D, 0.0D);
        if (!Double.isFinite(tEntry)) return new Chain(out, false);
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            double length = safeLength(current);
            if (!(length > 0.2D)) break;
            // 从「离 toe 近的一端」朝另一端走
            double tExit = tEntry <= length * 0.5D ? length : 0.0D;
            appendSegment(out, current, sampler.frame(), tEntry, tExit);
            if (out.isEmpty()) break;
            if (arcLength(out) >= MAX_LEG_LENGTH) break;
            Sample tail = out.get(out.size() - 1);
            BlockPos exitNode = nearestNode(current, sampler.frame(), tail);
            if (exitNode == null) break;
            if (exitNode.equals(target)) {
                reached = true;
                break;
            }
            RailSampleSource.RailGeometry next = nextSegment(sampler, exitNode, current, target);
            if (next == null) break;
            double tNext = paramNearestNode(next, sampler.frame(), exitNode);
            if (!Double.isFinite(tNext)) break;
            current = next;
            tEntry = tNext;
        }
        return new Chain(out, reached);
    }

    /** 两端正好是 {@code a} / {@code b} 的那一段轨道；找不到返回 null。 */
    @Nullable
    private static RailSampleSource.RailGeometry segmentBetween(Sampler sampler, BlockPos a, BlockPos b) {
        for (RailSampleSource.RailGeometry geo : sampler.near(a.getX() + 0.5D, a.getZ() + 0.5D, NEXT_RAIL_RADIUS)) {
            BlockPos p1 = safePos1(geo);
            BlockPos p2 = safePos2(geo);
            if (p1 == null || p2 == null) continue;
            if ((p1.equals(a) && p2.equals(b)) || (p1.equals(b) && p2.equals(a))) return geo;
        }
        return null;
    }

    /**
     * 从出口节点找下一段：要求一端就是 {@code exitNode}、不是刚走过的那段，
     * 并在多个候选里挑「另一端离目标节点最近」的那条（确定性，不会来回打转）。
     */
    @Nullable
    private static RailSampleSource.RailGeometry nextSegment(Sampler sampler, BlockPos exitNode,
                                                             RailSampleSource.RailGeometry previous, BlockPos target) {
        RailSampleSource.RailGeometry best = null;
        double bestScore = Double.MAX_VALUE;
        for (RailSampleSource.RailGeometry geo :
                sampler.near(exitNode.getX() + 0.5D, exitNode.getZ() + 0.5D, NEXT_RAIL_RADIUS)) {
            if (geo == previous) continue;
            if (!touchesNode(geo, exitNode)) continue;
            BlockPos far = otherNode(geo, exitNode);
            if (far == null) continue;
            double score = nodeDistanceXZ(far, target);
            if (score < bestScore) {
                bestScore = score;
                best = geo;
            }
        }
        return best;
    }

    /**
     * 在一条轨道上从 {@code tFrom} 采样到 {@code tTo}（方向自适应），追加进 {@code out}。
     * <p><b>跨段接缝</b>：新段第一个点若离上一段末点在 {@link #SEAM_MERGE} 内
     * （共享节点处两段的端点位置有极小数值差，属于同一点），就把两点<b>取平均合成一个站点</b>，
     * 直接复用同一站、不各生成一份；差得更多才补桥接点。两种情况都不会让扫掠拉出横跨的四边形。
     */
    private static void appendSegment(List<Sample> out, RailSampleSource.RailGeometry geo,
                                      TurnoutGeometry.Frame frame, double tFrom, double tTo) {
        if (!Double.isFinite(tFrom) || !Double.isFinite(tTo)) return;
        double span = Math.abs(tTo - tFrom);
        if (span < 1e-6D) return;
        double direction = tTo >= tFrom ? 1.0D : -1.0D;
        int steps = Math.max(1, (int) Math.ceil(span / TurnoutGeometry.SAMPLE_STEP));
        double budget = Math.max(0.0D, MAX_LEG_LENGTH - arcLength(out));
        double consumed = 0.0D;
        double[] previous = null;
        boolean firstPoint = true;
        for (int i = 0; i <= steps; i++) {
            double t = tFrom + direction * span * ((double) i / (double) steps);
            double tilt = tiltRadians(geo,t,direction);
            double[] local = localAt(geo, frame, t);
            if (local == null) continue;
            if (previous != null) {
                consumed += Math.sqrt((local[0] - previous[0]) * (local[0] - previous[0])
                        + (local[1] - previous[1]) * (local[1] - previous[1])
                        + (local[2] - previous[2]) * (local[2] - previous[2]));
                if (consumed > budget) return;
            }
            previous = local;
            Sample sample = new Sample(local[0], local[1], local[2], tilt);
            if (firstPoint) {
                firstPoint = false;
                if (seam(out, sample)) continue;
            }
            if (i == steps) {
                double[] probe = localAt(geo, frame, tTo - direction * Math.min(0.001D, span * 0.01D));
                if (probe != null) push(out, new Sample(probe[0], probe[1], probe[2], tilt));
                // 精确终点不能被 BRIDGE_EPS 的近点去重吞掉。
                out.add(sample);
            } else {
                push(out, sample);
                if (i == 0) {
                    double[] probe = localAt(geo, frame, tFrom + direction * Math.min(0.001D, span * 0.01D));
                    if (probe != null) out.add(new Sample(probe[0], probe[1], probe[2], tilt));
                }
            }
        }
    }

    /**
     * 接缝合并：上一个末点与新段首点在 {@link #SEAM_MERGE} 内时，用两点的<b>平均值</b>替换末点并返回 true。
     * <p>「两段共用一个节点」在数据上就是同一个位置，只是浮点上差几毫米；取平均后接缝处只有一个站点，
     * 扫掠不会在这里生成断口或重叠面。
     */
    private static boolean seam(List<Sample> out, Sample next) {
        if (out.isEmpty()) return false;
        Sample last = out.get(out.size() - 1);
        double distance = Math.sqrt((next.x() - last.x()) * (next.x() - last.x())
                + (next.y() - last.y()) * (next.y() - last.y())
                + (next.z() - last.z()) * (next.z() - last.z()));
        if (distance > SEAM_MERGE) return false;
        out.set(out.size() - 1, new Sample(
                (last.x() + next.x()) * 0.5D,
                (last.y() + next.y()) * 0.5D,
                (last.z() + next.z()) * 0.5D,
                (last.tilt() + next.tilt()) * 0.5D));
        return true;
    }

    /** 追加一个采样点：重复点跳过，距离过远先补桥接点。 */
    private static void push(List<Sample> out, Sample next) {
        if (out.isEmpty()) {
            out.add(next);
            return;
        }
        Sample last = out.get(out.size() - 1);
        double dx = next.x() - last.x();
        double dy = next.y() - last.y();
        double dz = next.z() - last.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= BRIDGE_EPS) return;
        if (distance > TurnoutGeometry.SAMPLE_STEP * 1.5D) {
            int pieces = Math.min(MAX_BRIDGE_POINTS, (int) Math.ceil(distance / TurnoutGeometry.SAMPLE_STEP));
            for (int i = 1; i < pieces; i++) {
                double k = (double) i / (double) pieces;
                out.add(new Sample(last.x() + dx * k, last.y() + dy * k, last.z() + dz * k,
                        last.tilt() + (next.tilt() - last.tilt()) * k));
            }
        }
        out.add(next);
    }

    /** 两个本地点之间直接补一段（节点之间没有轨道时的兜底）。 */
    private static boolean appendBridge(List<Sample> out, double[] fromLocal, double[] toLocal) {
        push(out, new Sample(fromLocal[0], fromLocal[1], fromLocal[2], 0.0D));
        push(out, new Sample(toLocal[0], toLocal[1], toLocal[2], 0.0D));
        return out.size() >= 2;
    }

    /** 采样点链的累计弧长（3D）。 */
    private static double arcLength(List<Sample> samples) {
        double sum = 0;
        for (int i = 1; i < samples.size(); i++) {
            Sample a = samples.get(i - 1);
            Sample b = samples.get(i);
            sum += Math.sqrt((b.x() - a.x()) * (b.x() - a.x())
                    + (b.y() - a.y()) * (b.y() - a.y())
                    + (b.z() - a.z()) * (b.z() - a.z()));
        }
        return sum;
    }

    /**
     * 采样点链 → 采样站：切向用中心差分（端点单侧）、弧长按 3D 距离累计、<b>超高按点各自携带</b>。
     * <p>
     * 多段是<b>先拼成一条点链再求切向</b>的，加上接缝处共用同一站点，所以拼缝处切向天然连续、不会出现折角。
     */
    private static List<TurnoutGeometry.Station> toStations(List<Sample> samples) {
        int n = samples.size();
        List<TurnoutGeometry.Station> out = new ArrayList<>(n);
        if (n < 2) return out;
        double arc = 0;
        for (int i = 0; i < n; i++) {
            Sample here = samples.get(i);
            double dx;
            double dz;
            if (i == 0) {
                dx = samples.get(1).x() - here.x();
                dz = samples.get(1).z() - here.z();
            } else if (i == n - 1) {
                dx = here.x() - samples.get(n - 2).x();
                dz = here.z() - samples.get(n - 2).z();
            } else {
                dx = samples.get(i + 1).x() - samples.get(i - 1).x();
                dz = samples.get(i + 1).z() - samples.get(i - 1).z();
            }
            double len = Math.hypot(dx, dz);
            double tx = len < 1e-9D ? 1.0D : dx / len;
            double tz = len < 1e-9D ? 0.0D : dz / len;
            if (i > 0) {
                Sample prev = samples.get(i - 1);
                arc += Math.sqrt((here.x() - prev.x()) * (here.x() - prev.x())
                        + (here.y() - prev.y()) * (here.y() - prev.y())
                        + (here.z() - prev.z()) * (here.z() - prev.z()));
            }
            out.add(new TurnoutGeometry.Station(here.x(), here.y(), here.z(), tx, tz, here.tilt(), arc));
        }
        return out;
    }

    /** 弦线兜底：anchor → 目标节点中心，直线采样。 */
    private static List<TurnoutGeometry.Station> fallbackLeg(TurnoutGeometry.Frame frame, BlockPos endNode) {
        double[] end = frame.toLocal(endNode.getX() + 0.5D, frame.anchorY(), endNode.getZ() + 0.5D);
        List<Sample> samples = new ArrayList<>();
        double length = Math.hypot(end[0], end[2]);
        if (length < 0.3D) {
            samples.add(new Sample(0.0D, 0.0D, 0.0D, 0.0D));
            samples.add(new Sample(1.0D, 0.0D, 0.0D, 0.0D));
            return toStations(samples);
        }
        int steps = Math.max(2, (int) Math.ceil(Math.min(MAX_LEG_LENGTH, length) / TurnoutGeometry.SAMPLE_STEP));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            samples.add(new Sample(end[0] * t, 0.0D, end[2] * t, 0.0D));
        }
        return toStations(samples);
    }

    /** 采样腿末端到目标节点的平面距离（都在本地平面上比）。 */
    private static double endDistance(List<TurnoutGeometry.Station> stations, TurnoutGeometry.Frame frame,
                                      double endX, double endZ) {
        if (stations == null || stations.isEmpty()) return Double.POSITIVE_INFINITY;
        TurnoutGeometry.Station last = stations.get(stations.size() - 1);
        double[] endLocal = frame.toLocal(endX, frame.anchorY(), endZ);
        return Math.hypot(last.x() - endLocal[0], last.z() - endLocal[2]);
    }

    /**
     * 路径兜底诊断日志（限频）：{@code [turnout][diag] geometry path fallback}。
     * <p>只在「绑定里没有路径、本类按节点链式跟随」时打一行；按方块位置去重 + 全局最小间隔，
     * 既能在现场确认走的是哪条路，也不会刷屏。
     */
    private static void logPathFallback(TurnoutBinding binding, String tag) {
        long now = System.currentTimeMillis();
        if (now - lastFallbackLogAt < FALLBACK_LOG_TICKS * 50L) return;
        if (!FALLBACK_LOGGED.add(binding.toe().asLong())) return;
        lastFallbackLogAt = now;
        Main.LOGGER.info("[turnout][diag] geometry path fallback toe={} leg={} (binding path empty)",
                binding.toe(), tag);
    }

    /** 已打过兜底日志的方块（按 asLong 去重）。 */
    private static final Set<Long> FALLBACK_LOGGED = ConcurrentHashMap.newKeySet();
    /** 上一次兜底日志的时间戳（毫秒），用于限频。 */
    private static volatile long lastFallbackLogAt;

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) Main.LOGGER.warn("[turnout] {}", message);
    }
}
