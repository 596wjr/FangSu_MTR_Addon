package com.fangsu.levelcrossing.geometry;

import com.fangsu.Main;
import com.fangsu.levelcrossing.CellSnapshot;
import com.fangsu.levelcrossing.CrossingSettings;
import com.fangsu.levelcrossing.TrackSlice;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 把一个平交路口格子的几何装配成 RawModel。
 * <p>
 * 三个 mesh 各用一张贴图：top（铺面顶面 + 外侧斜面）、rail（槽型钢）、ground（地面层）。
 * 铺面用耳朵裁剪三角化（凹多边形也正确），每个三角形用一个重复顶点凑成 faceSize=4 的四边形，
 * 多出来的退化三角形面积为零，不影响渲染。
 * <p>
 * 法线一律取<b>几何法线</b>（三角形自身 (b-a)×(c-a)），期望朝向只用来定绕序；
 * 绕序要翻时法线同步取反，光照才不会和面朝向打架。
 * <p>
 * UV 一律用平面坐标线性映射（水平面 u=x、v=z），相邻格才能纹理连续。
 */
public final class CrossingMeshBuilder {

    /**
     * 格界<b>竖直面</b>向内缩的量（顶面不缩，顶面一律铺满 0..1）。
     * 相邻方块各画自己的竖直面，不缩就会共面 z-fighting；顶面共面则是平接，不需要缩。
     */
    public static final double CELL_INSET = DeckSurface.MIN_CELL_INSET;
    /**
     * 单格顶点上限（默认值）= 8000。
     * <p>
     * 口径：普通格 ~400 顶点，最刁钻的「急弯 + 两轨交叉」格（R=2.5）在 32 点中心线 + 碎片瘦身后
     * 也必须落在 8000 以内（T44 断言）。实机日志里 1.38 万顶点那种格子是 64 点中心线 + 铣切碎片
     * 爆炸叠出来的，超预算会走 {@link #emitRails} 的降级阶梯（隔点取稀 + 丢碎渣），
     * 再不够才强行发射并 warn —— 任何情况下都不丢 band（旧代码 4000 时会整条跳过 = 「斜线缺一段」）。
     */
    public static final int MAX_VERTICES_PER_CELL = 8000;

    /**
     * 单个断面段面最多按被几片铣切影响来放大（1+4 = 5 倍）。
     * 只用于 {@link #roughEstimateRailVertices} 那条「便宜下界」，预算判定不用它。
     */
    private static final int MAX_CUT_FACTOR = 4;

    /** 实际使用的预算：默认 {@link #MAX_VERTICES_PER_CELL}，自检/调试可临时改小来验证降级阶梯。 */
    private static volatile int vertexBudget = MAX_VERTICES_PER_CELL;

    /**
     * <b>自适应估算系数</b>：{@code estimate = roughArithmetic × ESTIMATE_FACTOR}。
     * <p>
     * 为什么要它：第 35 轮把 estimate 改成「先扫进临时 mesh 数真实顶点」，结果每格<b>建了两遍</b>
     * （实机 17 秒的任务里一半是白烧）。现在只建一遍：先用「便宜算式 × 系数」预判要不要降级，
     * 真建完再用 {@code actual / rough} 反向学习系数（夹到 [1, 8]，冷启动不会低估）。
     */
    private static volatile double ESTIMATE_FACTOR = 1.0;

    /** 系数上限：学得越保守越安全；超过这个倍数的格子直接按降级明细建。 */
    public static final double ESTIMATE_FACTOR_MAX = 8.0;

    /** 上一次 build 的实际顶点是否超预算（BE 据此把 degradeLevel 抬一级，下一次重建才降级）。 */
    private static volatile boolean lastBuildOverBudget;

    public static double estimateFactor() {
        return ESTIMATE_FACTOR;
    }

    /** 自检复位用。 */
    public static void resetEstimateFactor() {
        ESTIMATE_FACTOR = 1.0;
    }

    public static boolean lastBuildOverBudget() {
        return lastBuildOverBudget;
    }

    /** 超预算仍强行发射的告警次数（自检用来断言「恰好一次」）。 */
    private static final java.util.concurrent.atomic.AtomicLong BUDGET_WARNS =
            new java.util.concurrent.atomic.AtomicLong();

    /** 改预算（仅自检/调试；正式路径不会调用，传 ≤0 表示恢复默认）。 */
    public static void setVertexBudget(int budget) {
        vertexBudget = budget > 0 ? budget : MAX_VERTICES_PER_CELL;
    }

    public static int vertexBudget() {
        return vertexBudget;
    }

    public static long railBudgetWarnings() {
        return BUDGET_WARNS.get();
    }

    /**
     * 最近一次 {@link #emitRails} 实际用的明细级别：1 = 全明细，2 = 中心线隔点取稀（降级）。
     * 自检用它复算「期望面数」，也用来向用户/日志说明是否降过级。
     */
    private static volatile int lastRailDetailLevel = 1;

    public static int lastRailDetailLevel() {
        return lastRailDetailLevel;
    }

    public static void resetRailBudgetWarnings() {
        BUDGET_WARNS.set(0);
    }

    /** 斜坡面数上限（格边只有四条）/ mitre 轮次上限 / 单个坡面顶点上限：防御脏输入的硬上限。 */
    private static final int MAX_RAMP_FACES = 4;
    private static final int MAX_MITRE_ROUNDS = 8;
    private static final int MAX_RAMP_POLY_POINTS = 16;

    /** 一次性告警去重表：同类问题只打一次，避免每帧刷屏。 */
    private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void warnOnce(String what) {
        if (WARNED.add(what)) {
            Main.LOGGER.warn("[levelcrossing] {}", what);
        }
    }

    private CrossingMeshBuilder() {
    }

    /**
     * 装配一个格子的模型。
     * <p>
     * <b>诊断加固</b>（用户现场「全部不显示」，必须先把失败变响亮）：
     * <ol>
     *   <li>四个 emit 阶段各自 try/catch 隔离：某阶段抛异常只丢该阶段，其余照画
     *       （至少铺面能出来，现场就能区分「全空」还是「只缺钢轨」）；</li>
     *   <li>结束时打一行 mesh 统计（顶点数 + <b>面数</b>，真实 RawMesh 的 faces 才是渲染真正消费的东西）；</li>
     *   <li>统计为零（顶点或面）直接升级为 warn，并附快照关键值，避免「静默空白」。</li>
     * </ol>
     */
    public static RawModel build(CellSnapshot snap, CrossingSettings cfg,
                                 ResourceLocation topTex,
                                 ResourceLocation railTex,
                                 ResourceLocation groundTex) {
        return build(snap, cfg, topTex, railTex, groundTex, 0, 0L);
    }

    public static RawModel build(CellSnapshot snap, CrossingSettings cfg,
                                 ResourceLocation topTex,
                                 ResourceLocation railTex,
                                 ResourceLocation groundTex,
                                 int degradeLevel) {
        return build(snap, cfg, topTex, railTex, groundTex, degradeLevel, 0L);
    }

    /**
     * @param degradeLevel 0 = 全明细；&gt;0 = 直接用降级明细（中心线隔点取稀 + 碎渣瘦身）。
     *                     由 BE 在上一次 build 超预算之后抬一级传入 —— <b>同一次里绝不建两遍</b>。
     */
    public static RawModel build(CellSnapshot snap, CrossingSettings cfg,
                                 ResourceLocation topTex,
                                 ResourceLocation railTex,
                                 ResourceLocation groundTex,
                                 int degradeLevel,
                                 long posSeed) {
        return build(snap, cfg, topTex, railTex, railTex, groundTex, degradeLevel, posSeed);
    }

    /**
     * 全参版本：顶面材质与侧面材质分开传（{@code railTopTex} = 128×16 条带，{@code railSideTex} = 128×128）。
     *
     * @param posSeed 方块坐标（{@code BlockPos.asLong()}）：用于<b>确定性随机</b>裁切窗口，0 = 不偏移
     */
    public static RawModel build(CellSnapshot snap, CrossingSettings cfg,
                                 ResourceLocation topTex,
                                 ResourceLocation railTopTex,
                                 ResourceLocation railSideTex,
                                 ResourceLocation groundTex,
                                 int degradeLevel,
                                 long posSeed) {
        RawModel model = new RawModel();
        if (snap == null || cfg == null) {
            Main.LOGGER.warn("[levelcrossing] build skipped snap={} cfg={}", snap == null, cfg == null);
            return model;
        }

        RawMeshBuilder top = new RawMeshBuilder(4, "exterior", topTex);
        // 钢轨两个桶各自一张图（第 57 轮用户口径）：
        //   railTop 桶（水平面）→ rail_top 128×16，v 原样 0→1（完整「深-浅-深」）；
        //   railSide 桶（竖直面）→ rail_side 128×128，保留每块随机窗口（uOff/vOff 随机 + 裁切）。
        // 缺图回退（双保险：调用方传的 RL 已经解析过，这里再挡一次，绝不让错误贴图出现在钢轨上）
        if (com.fangsu.levelcrossing.LevelCrossingStyles.railTexturesMissing()) {
            railTopTex = com.fangsu.levelcrossing.LevelCrossingStyles.RAIL_TEXTURE;
            railSideTex = com.fangsu.levelcrossing.LevelCrossingStyles.RAIL_TEXTURE;
        }
        RawMeshBuilder railTop = new RawMeshBuilder(4, "exterior", railTopTex);
        RawMeshBuilder railSide = new RawMeshBuilder(4, "exterior", railSideTex);
        RawMeshBuilder ground = new RawMeshBuilder(4, "exterior", groundTex);
        // 发射期审计 + 每格构造缓存：清空计数与「凸分解 / 槽条带」缓存
        GroovedRailProfile.resetAudit();
        GroovedRailProfile.clearCutCache();
        // 每块随机裁切窗口：用方块坐标做确定性随机（同 pos 恒定，不闪、各客户端一致）
        GroovedRailProfile.setUvWindow(posSeed);

        int topV0 = vertexCount(top);
        int topF0 = faceCount(top);
        int pavementVertices = stage("pavement", () -> emitPavement(top, snap, cfg));
        int topV1 = vertexCount(top);
        int topF1 = faceCount(top);
        int rampVertices = stage("ramp", () -> emitRamps(top, snap, cfg));
        // 钢轨阶段：面按 |ny| 分进 railTop / railSide（分桶在发射点做，几何不变）
        // 邻格信息一并传下去：邻格也是平交路口时就<b>不封端面</b>（接缝处不该立竖直板）
        GroovedRailProfile.setSideSink(railSide);
        GroovedRailProfile.setNeighbourCrossing(cfg.neighbourCrossing);
        int railVertices;
        try {
            railVertices = stage("rail", () -> emitRails(railTop, snap, degradeLevel));
        } finally {
            GroovedRailProfile.setSideSink(null);
            GroovedRailProfile.setNeighbourCrossing(null);
        }
        int groundVertices = stage("ground", () -> emitGround(ground, snap, cfg));

        int pavementV = topV1 - topV0;
        int pavementF = topF1 - topF0;
        int rampV = vertexCount(top) - topV1;
        int rampF = faceCount(top) - topF1;
        // 预算/统计一律用<b>两个桶合计</b>，不能让拆分把预算算漏
        int railTopV = vertexCount(railTop);
        int railTopF = faceCount(railTop);
        int railSideV = vertexCount(railSide);
        int railSideF = faceCount(railSide);
        int railV = railTopV + railSideV;
        int railF = railTopF + railSideF;
        int groundV = vertexCount(ground);
        int groundF = faceCount(ground);

        model.append(top.getMesh());
        model.append(railTop.getMesh());
        model.append(railSide.getMesh());
        model.append(ground.getMesh());
        model.triangulate();

        int totalV = pavementV + rampV + railV + groundV;
        int totalF = pavementF + rampF + railF + groundF;
        String stats = "[levelcrossing] mesh stats pavement=" + pavementV + "v/" + pavementF + "f"
                + " ramp=" + rampV + "v/" + rampF + "f"
                + " railTop=" + railTopV + "v/" + railTopF + "f"
                + " railSide=" + railSideV + "v/" + railSideF + "f"
                + " ground=" + groundV + "v/" + groundF + "f"
                + (GroovedRailProfile.auditSlimPieces() > 0
                        ? " slim=" + GroovedRailProfile.auditSlimDropped() + "/" + GroovedRailProfile.auditSlimPieces()
                        + " detail=" + lastRailDetailLevel
                        : " detail=" + lastRailDetailLevel);
        if (totalV == 0 || totalF == 0) {
            Main.LOGGER.warn(stats);
            Main.LOGGER.warn("[levelcrossing] mesh EMPTY tracks={} deck={} railTop={} pitch={} roll={} stageVerts={}/{}/{}/{}",
                    snap.tracks == null ? -1 : snap.tracks.size(), snap.deckTopY, firstRailTop(snap),
                    snap.pitchDeg, snap.rollDeg,
                    pavementVertices, rampVertices, railVertices, groundVertices);
        } else {
            Main.LOGGER.info(stats);
        }

        // ---- 全网格审计 ----
        // 用户实机「有面、但整片什么都不显示」最可能是顶点里混进了 NaN/Inf：
        // 一个 NaN 就会让整条顶点缓冲的包围盒失效，GPU 直接什么都不画。
        // 计数在发射点累计（覆盖 100% 进缓冲的三角形），非有限值的三角形已在 tri() 里剔除。
        long emitted = GroovedRailProfile.auditEmitted();
        long dropped = GroovedRailProfile.auditDropped();
        long nan = GroovedRailProfile.auditNan();
        long inf = GroovedRailProfile.auditInf();
        long zeroArea = GroovedRailProfile.auditZeroArea();
        String audit = "[levelcrossing] mesh audit posless verts=" + totalV + " faces=" + totalF
                + " tris=" + emitted + " nan=" + nan + " inf=" + inf
                + " dropped=" + dropped + " zeroArea=" + zeroArea + " mats=" + model.meshList.size();
        if (nan > 0 || inf > 0 || dropped > 0) {
            Main.LOGGER.warn(audit);
            Main.LOGGER.warn("[levelcrossing] mesh audit first bad tri {}", GroovedRailProfile.auditFirstBad());
        } else {
            Main.LOGGER.info(audit);
        }

        if (totalV > vertexBudget) {
            Main.LOGGER.warn("[levelcrossing] 单格顶点数 {} 超过上限 {}，几何可能被截断",
                    totalV, vertexBudget);
        }
        return model;
    }

    /** 单阶段包装：抛异常时打一条 warn 并返回 0，其余阶段继续（绝不让一个阶段毁掉整格）。 */
    private static int stage(String name, java.util.function.Supplier<Integer> body) {
        try {
            return body.get();
        } catch (RuntimeException e) {
            Main.LOGGER.warn("[levelcrossing] emit failed stage={} ex={}: {}",
                    name, e.getClass().getSimpleName(), e.getMessage());
            return 0;
        }
    }

    /** 真实 RawMesh 与自检桩都有的顶点表长度（null 安全）。 */
    private static int vertexCount(RawMeshBuilder builder) {
        if (builder == null || builder.getMesh() == null || builder.getMesh().vertices == null) return 0;
        return builder.getMesh().vertices.size();
    }

    /** 真实 RawMesh 的 faces 才是渲染消费的单位；桩里同样维护。null 安全。 */
    private static int faceCount(RawMeshBuilder builder) {
        if (builder == null || builder.getMesh() == null || builder.getMesh().faces == null) return 0;
        return builder.getMesh().faces.size();
    }

    private static double firstRailTop(CellSnapshot snap) {
        if (snap == null || snap.tracks == null || snap.tracks.isEmpty() || snap.tracks.get(0) == null) {
            return Double.NaN;
        }
        return snap.tracks.get(0).railTopY;
    }

    /** 铺面顶面：每个碎片按耳朵裁剪出三角形（凹片也正确），法线取几何法线。 */
    public static int emitPavement(RawMeshBuilder out, CellSnapshot snap, CrossingSettings cfg) {
        int count = 0;
        for (List<Vec2> poly : DeckSurface.pavementTops(snap, cfg)) {
            if (poly.size() < 3) continue;
            double[][] pts = new double[poly.size()][3];
            for (int i = 0; i < poly.size(); i++) {
                Vec2 p = poly.get(i);
                pts[i] = new double[]{p.x(), DeckSurface.deckY(snap, p.x(), p.z()), p.z()};
            }
            polygon(out, pts, 0, 1, 0);
            count += (pts.length - 2) * 4;
        }
        return count;
    }

    /** 格边的两个端点（{@code t=0} 与 {@code t=1} 沿该边参数化）。 */
    private static Vec2 edgePoint(int side, double t) {
        return switch (side) {
            case DeckSurface.SIDE_NEG_X -> new Vec2(0, t);
            case DeckSurface.SIDE_POS_X -> new Vec2(1, t);
            case DeckSurface.SIDE_NEG_Z -> new Vec2(t, 0);
            default -> new Vec2(t, 1);
        };
    }

    /**
     * <b>内部记录：斜向坡面是已知搁置项（用户决定）。</b>格边与轨道夹角不落在 80–100° 时不生成坡，
     * 这是用户明确接受的现状，不要为此再改规则；轴对齐轨道仍正常出坡。
     * 该说明只写在这里，任何玩家可见文本都不得出现"暂不/搁置/未实现"之类字样。
     * <p>
     * 斜坡（道口路肩）：在 {@link DeckSurface#rampSides} 选中的格边上，<b>向格外</b>延伸
     * {@code run} 格（0.25..1），高度由格边处的 {@code deckY} 线性降到坡脚
     * {@code cfg.rampFootY}（默认 0 = 本方块下方那个方块的顶面）。
     * <p>
     * 每条坡只发三块面：坡面 + 两个端面三角（底面朝下看不见，省略）。
     * 这不是「格内倒角」：铺面本身不再被切，坡完全落在方块之外，所以相邻格的铺面能平接。
     */
    /** 一条坡面：所属格边 + 已 mitre 的坡面多边形（平面坐标）。 */
    public record RampFace(DeckSurface.Ramp ramp, List<Vec2> poly) {
    }

    /**
     * 计算所有坡面多边形（含角落 mitre）。抽成 public 是给自检用的：
     * T18-3 要验证两条坡在角落切完之后交集面积为 0。
     */
    public static List<RampFace> rampFaces(CellSnapshot snap, CrossingSettings cfg) {
        List<DeckSurface.Ramp> ramps = DeckSurface.ramps(snap, cfg);
        List<RampFace> out = new ArrayList<>(ramps.size());
        // 防御：ramps 由四条格边筛出，最多 4 条；这里再夹一次上限，任何异常输入都不会放大工作量。
        if (ramps.size() > MAX_RAMP_FACES) {
            warnOnce("ramps>4，已截断");
            ramps = ramps.subList(0, MAX_RAMP_FACES);
        }
        int rounds = 0;
        for (DeckSurface.Ramp ramp : ramps) {
            Vec2 outward = ramp.outward();
            Vec2 a = edgePoint(ramp.side(), 0);
            Vec2 b = edgePoint(ramp.side(), 1);
            // 坡面多边形：内沿在格边上（高度 deckY），外沿向外 run（高度 rampFootY）
            List<Vec2> poly = new ArrayList<>(4);
            poly.add(a);
            poly.add(b);
            poly.add(b.add(outward.mul(ramp.run())));
            poly.add(a.add(outward.mul(ramp.run())));
            // 角落 mitre：两条相邻边都出坡时两块坡面会在角落重叠（互相穿插），
            // 按「离自己那条边更近」把重叠的一半切掉，接缝是一条斜线，不交叉也不留洞。
            // <p>
            // 显式迭代上限：切分本身不递归，但历史实现（已删除的 subdivideBevel）是递归的，
            // 这里保留一个上限 + 一次性 warn，保证任何输入都不会变成无界循环；
            // 同时限制切完的顶点数，避免脏输入把顶点数炸到几千个。
            for (DeckSurface.Ramp other : ramps) {
                if (other.side() == ramp.side()) continue;
                if (++rounds > MAX_MITRE_ROUNDS) {
                    warnOnce("mitre 轮次超限，已停止继续切分");
                    break;
                }
                poly = clipNearestOwnEdge(poly, ramp, other);
                if (poly.size() < 3) break;
                if (poly.size() > MAX_RAMP_POLY_POINTS) {
                    warnOnce("mitre 结果顶点过多，已跳过该坡面");
                    poly = new ArrayList<>();
                    break;
                }
            }
            if (poly.size() >= 3) {
                out.add(new RampFace(ramp, poly));
            }
        }
        return out;
    }

    public static int emitRamps(RawMeshBuilder out, CellSnapshot snap, CrossingSettings cfg) {
        int count = 0;
        double foot = cfg.rampFootY;
        boolean[] sides = DeckSurface.rampSides(snap, cfg);
        for (RampFace face : rampFaces(snap, cfg)) {
            DeckSurface.Ramp ramp = face.ramp();
            List<Vec2> poly = face.poly();
            Vec2 outward = ramp.outward();
            Vec2 a = edgePoint(ramp.side(), 0);
            Vec2 b = edgePoint(ramp.side(), 1);
            Vec2 aOut = a.add(outward.mul(ramp.run()));
            Vec2 bOut = b.add(outward.mul(ramp.run()));
            Vec2 along = b.sub(a).normalized();
            double ya = DeckSurface.deckY(snap, a.x(), a.z());
            double yb = DeckSurface.deckY(snap, b.x(), b.z());

            double[][] pts = new double[poly.size()][3];
            for (int i = 0; i < poly.size(); i++) {
                Vec2 p = poly.get(i);
                // 到本格边的有向距离 d 决定高度：d=0 是 deckY，d=run 是坡脚
                double d = p.sub(a).dot(outward);
                double t = ramp.run() < 1e-9 ? 1 : Math.max(0, Math.min(1, d / ramp.run()));
                double deckHere = deckAtEdgePoint(snap, p, outward, d);
                pts[i] = new double[]{p.x(), deckHere + (foot - deckHere) * t, p.z()};
            }
            // 期望法线朝上并朝外，写进顶点的是几何法线（y 分量必须 > 0）
            polygon(out, pts, -outward.x() * 0.5, 1, -outward.z() * 0.5);
            count += (pts.length - 2) * 4;

            // 端面封边：只在「该端没有相邻坡」的一端补，mitre 那一端由邻坡接上
            if (!sides[perpendicularSide(ramp.side(), 0)]) {
                count += capQuad(out, a, aOut, foot, ya, new Vec2(-along.x(), -along.z()));
            }
            if (!sides[perpendicularSide(ramp.side(), 1)]) {
                count += capQuad(out, b, bOut, foot, yb, along);
            }
        }
        return count;
    }

    /** 斜坡端面：把坡的端边垂直补到坡脚平面（三点 + 退化第四点）。 */
    private static int capQuad(RawMeshBuilder out, Vec2 inner, Vec2 outer, double foot, double topY, Vec2 normal) {
        quad(out,
                inner.x(), foot, inner.z(),
                outer.x(), foot, outer.z(),
                inner.x(), topY, inner.z(),
                inner.x(), topY, inner.z(),
                normal.x(), 0, normal.z());
        return 4;
    }

    /**
     * 某条格边在参数 {@code t} 端点相邻的那条垂直格边。
     * 用于判断「这一端有没有邻坡」（有就不补端面，交给 mitre 接缝）。
     */
    private static int perpendicularSide(int side, int end) {
        return switch (side) {
            case DeckSurface.SIDE_NEG_X, DeckSurface.SIDE_POS_X ->
                    end == 0 ? DeckSurface.SIDE_NEG_Z : DeckSurface.SIDE_POS_Z;
            default -> end == 0 ? DeckSurface.SIDE_NEG_X : DeckSurface.SIDE_POS_X;
        };
    }

    /** 坡面上任意点的高度基准：沿该格边取的那个点（pitch/roll 下沿边线性变化）。 */
    private static double deckAtEdgePoint(CellSnapshot snap, Vec2 p, Vec2 outward, double d) {
        Vec2 onEdge = p.sub(outward.mul(d));
        return DeckSurface.deckY(snap, onEdge.x(), onEdge.z());
    }

    /**
     * 两条坡在角落重叠的裁切：把多边形按「到自己的格边更近」切开，只保留自己这一半。
     * 分界线是 {@code d_self == d_other} 的直线（两条格边平行时不存在，直接返回原多边形）。
     */
    private static List<Vec2> clipNearestOwnEdge(List<Vec2> poly, DeckSurface.Ramp self, DeckSurface.Ramp other) {
        Vec2 gx = self.outward().sub(other.outward());
        if (gx.length() < 1e-12) {
            return poly;
        }
        Vec2 aSelf = edgePoint(self.side(), 0);
        Vec2 aOther = edgePoint(other.side(), 0);
        // d_self(p) - d_other(p) = 0  <=>  (p-aSelf)·nSelf - (p-aOther)·nOther = 0
        // 两法线都是轴对齐单位向量，可解出直线上的两点
        double c = -aSelf.dot(self.outward()) + aOther.dot(other.outward());
        double gxz = gx.x();
        double gzz = gx.z();
        Vec2 p0 = Math.abs(gxz) >= Math.abs(gzz) ? new Vec2(-c / gxz, 0.25) : new Vec2(0.25, -c / gzz);
        Vec2 p1 = Math.abs(gxz) >= Math.abs(gzz) ? new Vec2(-c / gxz, 0.75) : new Vec2(0.75, -c / gzz);
        List<List<Vec2>> parts = PolyOps.splitByLine(poly, p0, p1);
        List<Vec2> best = new ArrayList<>();
        double bestScore = Double.POSITIVE_INFINITY;
        for (List<Vec2> part : parts) {
            if (part.size() < 3) continue;
            double cx = 0;
            double cz = 0;
            for (Vec2 q : part) {
                cx += q.x();
                cz += q.z();
            }
            Vec2 centroid = new Vec2(cx / part.size(), cz / part.size());
            double dSelf = centroid.sub(aSelf).dot(self.outward());
            double dOther = centroid.sub(aOther).dot(other.outward());
            double score = dSelf - dOther;
            if (score < bestScore) {
                bestScore = score;
                best = part;
            }
        }
        return best.isEmpty() ? poly : best;
    }

    /**
     * 发射全部钢轨 band —— <b>每格只建一遍</b>。
     * <p>
     * 第 35 轮的「先扫进临时 mesh 数真实顶点、超了就重建」让每格建两遍，实机 17 秒的任务里
     * 一半是白烧。现在改成：
     * <ol>
     *   <li>用<b>便宜的算术估算 × 自适应系数</b>（{@link #ESTIMATE_FACTOR}）预判要不要降级，不扫几何；</li>
     *   <li>只构建一次；结果直接发射（几何完整，绝不丢 band）；</li>
     *   <li>实测超预算时只做两件事：置 {@link #lastBuildOverBudget}（BE 下一次重建抬一级 degradeLevel）
     *       与把 {@code actual / rough} 学进系数（夹到 [1, 8]）。<b>同一次里不再重建</b>。</li>
     * </ol>
     */
    private static int emitRails(RawMeshBuilder out, CellSnapshot snap, int degradeLevel) {
        // band 表只算一次：槽铣切要在扫掠时看到其它轨道的槽条带
        List<TrackStrips.Band> bands = TrackStrips.bands(snap);
        boolean deep = Boolean.getBoolean("fangsu.levelcrossing.debug");
        int budget = vertexBudget;

        // 便宜的算术估算（不扫几何）：决定这一次要不要直接用降级明细
        int rough = 0;
        for (TrackStrips.Band band : bands) {
            if (band.slice() == null) continue;
            rough += roughEstimateRailVertices(band, band.slice(), snap, bands);
        }
        int estimate = (int) Math.ceil(rough * ESTIMATE_FACTOR);
        boolean degrade = degradeLevel > 0 || estimate > budget;
        int detailLevel = degrade ? 2 : 1;

        List<com.fangsu.render.sowcerext.model.RawMesh> built = new ArrayList<>(bands.size());
        int total;
        GroovedRailProfile.setSlimPieces(degrade);
        try {
            total = buildRails(built, bands, snap, degrade, deep, detailLevel);
        } finally {
            GroovedRailProfile.setSlimPieces(false);
        }

        // 自适应学习：actual / rough，夹到 [1, 8]（只学不比 rough 更小的倍率，避免低估后少降级）
        if (rough > 0) {
            ESTIMATE_FACTOR = Math.min(ESTIMATE_FACTOR_MAX, Math.max(1.0, total / (double) rough));
        }
        lastBuildOverBudget = total > budget;
        lastRailDetailLevel = detailLevel;
        if (lastBuildOverBudget && degrade) {
            // 降级之后仍然超预算：只 warn 一次，几何照发（宁可超预算也不缺几何）
            BUDGET_WARNS.incrementAndGet();
            Main.LOGGER.warn("[levelcrossing] rail budget exceeded pos=? verts={} cap={} (emitted anyway)",
                    total, budget);
        } else if (lastBuildOverBudget && deep) {
            Main.debug("[levelcrossing] rail over budget verts={} cap={} → 下一次重建升 degradeLevel",
                    total, budget);
        } else if (deep) {
            Main.debug("[levelcrossing] rail estimate={} actual={} factor={} detail={} cap={}",
                    estimate, total, ESTIMATE_FACTOR, detailLevel, budget);
        }
        for (com.fangsu.render.sowcerext.model.RawMesh mesh : built) out.getMesh().append(mesh);
        return total;
    }

    /**
     * 把每条 band 扫进各自的临时 mesh，返回真实顶点总量（同时填 {@code sink}）。
     *
     * @param reduce true = 用降级明细（中心线隔点取稀）
     */
    private static int buildRails(List<com.fangsu.render.sowcerext.model.RawMesh> sink,
                                  List<TrackStrips.Band> bands, CellSnapshot snap,
                                  boolean reduce, boolean deep, int detailLevel) {
        int total = 0;
        for (TrackStrips.Band band : bands) {
            TrackSlice slice = band.slice();
            if (slice == null) {
                // 没有轨道片的 band 画不出断面，记一条就跳过（不能让它把整个 rail 阶段打崩）
                Main.LOGGER.warn("[levelcrossing] band 缺少 TrackSlice side={}，跳过", band.side());
                continue;
            }
            TrackStrips.Band use = reduce ? reduceRailDetail(band) : band;
            RawMeshBuilder temp = new RawMeshBuilder(4, "exterior", null);
            GroovedRailProfile.sweep(temp, use, slice, snap, bands);
            int n = vertexCount(temp);
            total += n;
            sink.add(temp.getMesh());
            if (deep) {
                double deckBelowRail = snap.deckTopY - slice.railTopY;
                Main.debug("[levelcrossing] band side={} primary={} section={} slotCuts={} lines={} detail={} verts={} rough={}",
                        band.side(), slice.primary,
                        GroovedRailProfile.section(slice, deckBelowRail).size(),
                        GroovedRailProfile.slotCuts(band, bands).size(),
                        GroovedRailProfile.clipLinesToCell(band.centerline(), slice.grooveOuter).size(),
                        detailLevel, n, roughEstimateRailVertices(use, slice, snap, bands));
            }
        }
        return total;
    }

    /** 降级用：中心线隔点取稀（保留首尾），带宽/断面不变，顶点数约减半。自检也用它复算期望面数。 */
    public static TrackStrips.Band reduceRailDetail(TrackStrips.Band band) {
        List<Vec2> line = band.centerline();
        if (line == null || line.size() < 5) return band;
        List<Vec2> thin = new ArrayList<>((line.size() + 1) / 2 + 1);
        for (int i = 0; i < line.size(); i += 2) thin.add(line.get(i));
        if ((line.size() - 1) % 2 != 0) thin.add(line.get(line.size() - 1));
        if (thin.size() < 3) return band;
        return new TrackStrips.Band(band.poly(), band.side(), band.slice(), thin);
    }

    /**
     * 该 band 的顶点数<b>精确计数</b>：真的扫掠一遍（扫进临时 mesh，不进输出），再返回顶点数。
     * <p>
     * <b>为什么不用纯算式估算</b>：铣切把每个断面段面按补集裁剪切成多块，切出来的块数
     * 取决于「被几片凸片切、每片几条边、切完还剩多少非退化块」，是<b>乘法级</b>的增长。
     * 实测（R=2.5 两轨交叉格，4 条 band）：
     * <pre>
     *   长带(path=63) 实测 10716 / 11892 顶点，而「1+min(分片数,4)」算式给出 10176（差 5%~17%）；
     *   短带(path=4)  实测 2492 / 4144 顶点，同一算式只给出 736（差 3.4~5.6 倍）——
     *   因为短带整条都压在交叉区里，每个面都被切成十几个碎块。任何「按带平均」的算式都盖不住这种差异。
     * </pre>
     * 所以预算判定改用「先建到计数器」（成本 = 本来就躲不掉的一次扫掠，见
     * {@link #buildRails}：建完直接 append，不重复扫）。
     *
     * @return 该 band 的真实顶点数
     */
    public static int estimateRailVertices(TrackStrips.Band band, TrackSlice slice, CellSnapshot snap,
                                           List<TrackStrips.Band> bands) {
        if (band == null || slice == null || snap == null) return 0;
        RawMeshBuilder probe = new RawMeshBuilder(4, "exterior", null);
        GroovedRailProfile.sweep(probe, band, slice, snap, bands);
        return vertexCount(probe);
    }

    /**
     * 便宜的成本模型（不扫几何）：{@code base + base × 铣切面积占比 × (合并后裁片数 − 1)}。
     * <p>
     * 关键认识：补集裁剪只作用在<b>交叉区</b>那些面上，其余面都是 1 块 —— 早先「整条 band 都乘 cutFactor」
     * 会把正常格估高好几倍（于是不必要的降级）。这里按「被挖面积 / 钢带面积」的比例放大，
     * 与实测同量级（R=2.5 急弯交叉格：估 ~3.1k，实测 ~3.8k）。
     */
    private static int roughEstimateRailVertices(TrackStrips.Band band, TrackSlice slice, CellSnapshot snap,
                                                 List<TrackStrips.Band> bands) {
        double deckBelowRail = snap.deckTopY - slice.railTopY;
        int ring = GroovedRailProfile.section(slice, deckBelowRail).size();
        int path = Math.max(1, band.centerline().size() - 1);
        int capQuads = (ring + 1) / 2;
        int base = path * ring * 4 + capQuads * 2 * 4;
        List<List<Vec2>> merged = GroovedRailProfile.mergedCutPolys(band, bands);
        if (merged.size() < 2) return base;
        double bandArea = Math.abs(PolyOps.area(band.poly()));
        if (bandArea <= 1e-9) return base;
        double cutArea = 0;
        for (List<Vec2> piece : merged) cutArea += Math.abs(PolyOps.area(piece));
        double ratio = Math.max(0.0, Math.min(1.0, cutArea / bandArea));
        int extra = Math.min(MAX_CUT_FACTOR, merged.size() - 1);
        return (int) Math.ceil(base + base * ratio * extra);
    }

    /** 该 band 被几片铣切区域切到（按凸片与带宽投影的相交计；诊断用）。 */
    static int cutsAffecting(TrackStrips.Band band, List<TrackStrips.Band> bands) {
        if (bands == null || band.poly() == null || band.poly().size() < 3) return 0;
        int pieces = 0;
        List<List<Vec2>> convex = PolyOps.convexParts(band.poly());
        for (GroovedRailProfile.SlotCut cut : GroovedRailProfile.slotCuts(band, bands)) {
            for (List<Vec2> piece : cut.pieces()) {
                if (piece == null || piece.size() < 3) continue;
                for (List<Vec2> c : convex) {
                    List<Vec2> inter = PolyOps.clipConvex(c, piece);
                    if (inter.size() >= 3 && Math.abs(PolyOps.area(inter)) > GroovedRailProfile.CROSSING_AREA_EPS) {
                        pieces++;
                        break;
                    }
                }
            }
        }
        return pieces;
    }

    /** 地面层（顶面一圈台阶 + 铺面侧壁 + 裙边）。{@code public} 是为了自检能单独统计这条链路。 */
    public static int emitGround(RawMeshBuilder out, CellSnapshot snap, CrossingSettings cfg) {
        double inset = CELL_INSET;
        double minX = inset;
        double minZ = inset;
        double maxX = 1 - inset;
        double maxZ = 1 - inset;

        List<Vec2> deck = DeckSurface.deckRect(cfg);
        double dx0 = deck.get(0).x();
        double dz0 = deck.get(0).z();
        double dx1 = deck.get(2).x();
        double dz1 = deck.get(2).z();
        // 铺面矩形可能被钳到格界上，这时地面环宽度为 0
        dx0 = Math.min(Math.max(dx0, minX), maxX);
        dz0 = Math.min(Math.max(dz0, minZ), maxZ);
        dx1 = Math.min(Math.max(dx1, minX), maxX);
        dz1 = Math.min(Math.max(dz1, minZ), maxZ);

        int count = 0;
        // 地面层<b>不画顶面</b>：它永远在铺面之下，只会在挖空带（钢轨处）被看见一小块，
        // 用户截图里"钢轨旁露出的土黄色方片"就是它。需要露出的地方由钢带实体自己填满（见 T22-2）。

        boolean[] ramp = DeckSurface.rampSides(snap, cfg);
        boolean[] neighbour = cfg == null || cfg.neighbourCrossing == null
                ? new boolean[4] : cfg.neighbourCrossing;

        // 三类边三种处理：
        //   · 邻居是同类平交路口 → 平接，什么都不画（否则一整片道口每隔一格多一条竖边/一条缝）；
        //   · 有斜坡 → 竖面由坡的楔形代替（坡面已经贴在格界上，再画竖面会插进坡里）；
        //   · 其余 → 从地面层顶画到铺面边缘的竖直面 + 到底面的裙边。
        boolean[] wall = new boolean[4];
        for (int i = 0; i < 4; i++) {
            wall[i] = !neighbour[i] && !ramp[i];
        }
        if (wall[DeckSurface.SIDE_NEG_X]) {
            count += deckSide(out, snap, new Vec2(dx0, dz0), new Vec2(dx0, dz1), new Vec2(-1, 0));
            count += skirt(out, snap, new Vec2(minX, minZ), new Vec2(minX, maxZ), new Vec2(-1, 0));
        }
        if (wall[DeckSurface.SIDE_POS_X]) {
            count += deckSide(out, snap, new Vec2(dx1, dz0), new Vec2(dx1, dz1), new Vec2(1, 0));
            count += skirt(out, snap, new Vec2(maxX, minZ), new Vec2(maxX, maxZ), new Vec2(1, 0));
        }
        if (wall[DeckSurface.SIDE_NEG_Z]) {
            count += deckSide(out, snap, new Vec2(dx0, dz0), new Vec2(dx1, dz0), new Vec2(0, -1));
            count += skirt(out, snap, new Vec2(minX, minZ), new Vec2(maxX, minZ), new Vec2(0, -1));
        }
        if (wall[DeckSurface.SIDE_POS_Z]) {
            count += deckSide(out, snap, new Vec2(dx0, dz1), new Vec2(dx1, dz1), new Vec2(0, 1));
            count += skirt(out, snap, new Vec2(minX, maxZ), new Vec2(maxX, maxZ), new Vec2(0, 1));
        }
        return count;
    }

    /** 铺面侧壁：从地面层顶面顶到铺面边缘（格内不再做倒角，倒角已改成格外的斜坡楔形）。 */
    private static int deckSide(RawMeshBuilder out, CellSnapshot snap,
                                Vec2 a, Vec2 b, Vec2 outward) {
        double bottomA = DeckSurface.groundY(snap, a.x(), a.z());
        double bottomB = DeckSurface.groundY(snap, b.x(), b.z());
        double topA = DeckSurface.deckY(snap, a.x(), a.z());
        double topB = DeckSurface.deckY(snap, b.x(), b.z());
        if (topA <= bottomA && topB <= bottomB) return 0;
        quad(out,
                a.x(), bottomA, a.z(),
                b.x(), bottomB, b.z(),
                b.x(), topB, b.z(),
                a.x(), topA, a.z(),
                outward.x(), 0, outward.z());
        return 4;
    }

    /** 地面层外侧竖直面：从方块底面顶到地面层顶面，向内缩 CELL_INSET。 */
    private static int skirt(RawMeshBuilder out, CellSnapshot snap, Vec2 a, Vec2 b, Vec2 outward) {
        double topA = DeckSurface.groundY(snap, a.x(), a.z());
        double topB = DeckSurface.groundY(snap, b.x(), b.z());
        quad(out,
                a.x(), 0, a.z(),
                b.x(), 0, b.z(),
                b.x(), topB, b.z(),
                a.x(), topA, a.z(),
                outward.x(), 0, outward.z());
        return 4;
    }

    /**
     * 任意简单多边形的三角化：按 (x,z) 投影做耳朵裁剪，每个三角形发成一个退化四边形。
     * expected = 期望朝向，只用来决定绕序。
     */
    private static void polygon(RawMeshBuilder out, double[][] pts, double ex, double ey, double ez) {
        if (pts.length < 3) return;
        List<Vec2> plan = new ArrayList<>(pts.length);
        for (double[] p : pts) plan.add(new Vec2(p[0], p[2]));
        for (int[] tri : PolyOps.triangulate(plan)) {
            triangle(out, pts[tri[0]], pts[tri[1]], pts[tri[2]], ex, ey, ez);
        }
    }

    private static void quad(RawMeshBuilder out,
                             double x0, double y0, double z0,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             double x3, double y3, double z3,
                             double ex, double ey, double ez) {
        double[] a = {x0, y0, z0};
        double[] b = {x1, y1, z1};
        double[] c = {x2, y2, z2};
        double[] d = {x3, y3, z3};
        triangle(out, a, b, c, ex, ey, ez);
        triangle(out, a, c, d, ex, ey, ez);
    }

    /**
     * 发射一个三角形（第 4 点复制第 3 点，保持 faceSize=4 约定）：
     * 法线取几何法线，绕序按 expected 校正；校正后法线同步取反，光照与朝向一致。
     */
    private static boolean triangle(RawMeshBuilder out, double[] a, double[] b, double[] c,
                                    double ex, double ey, double ez) {
        double nx = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]);
        double ny = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
        double nz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        double len = Math.hypot(Math.hypot(nx, ny), nz);
        if (len < 1e-12) return false;
        nx /= len;
        ny /= len;
        nz /= len;
        if (nx * ex + ny * ey + nz * ez < 0) {
            double[] swap = b;
            b = c;
            c = swap;
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        vertex(out, a, nx, ny, nz);
        vertex(out, b, nx, ny, nz);
        vertex(out, c, nx, ny, nz);
        vertex(out, c, nx, ny, nz);
        return true;
    }

    /** UV 分支与法线方向都用几何法线判：|ny| 占优走水平面 (x,z)，否则走竖直/斜面 (x+z, y)。 */
    private static void vertex(RawMeshBuilder out, double[] p, double nx, double ny, double nz) {
        boolean horizontal = Math.abs(ny) >= Math.abs(nx) && Math.abs(ny) >= Math.abs(nz);
        // 铺面 / 斜坡 / 地面：<b>每格 0→1 铺满整张</b>（第 61 轮用户决定，撤销第 59 轮的随机窗口）。
        // 水平面 u = 格内局部 x、v = 格内局部 z；竖直面 u = x+z、v = y。与 pos 无关，不随方块变化。
        float u = horizontal ? (float) p[0] : (float) (p[0] + p[2]);
        float v = horizontal ? (float) p[2] : (float) p[1];
        out.vertex(p[0], p[1], p[2]).normal((float) nx, (float) ny, (float) nz).uv(u, v).endVertex();
    }

}
