package com.fangsu.turnout.geometry;

import com.fangsu.levelcrossing.LevelCrossingStyles;
import com.fangsu.levelcrossing.geometry.PolyOps;
import com.fangsu.levelcrossing.geometry.Vec2;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.turnout.TurnoutRailParams;
import com.fangsu.turnout.TurnoutBinding;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 单开道岔几何核心：本地坐标系、轨道采样站、钢轨断面、条带扫掠与网格发射。
 *
 * <p><b>本地坐标系</b>（全类统一，{@link TurnoutBinding#anchor()} 与 {@code baseYawDeg} 定死）：</p>
 * <ul>
 *   <li>原点 = toe 处轨面中心（anchor）；</li>
 *   <li>+X = 直股在 toe 处远离的方向（baseYaw），+Y 向上，+Z = 右侧；</li>
 *   <li>长度单位一律「格」（1 格 = 1 米），与 {@link TurnoutRailParams} 的度量一致。</li>
 * </ul>
 *
 * <p><b>结构照抄平交路口</b>：{@code CrossingMeshBuilder} 那套「条带扫掠 + 几何法线定绕序 + UV 按弧长」的
 * 约定在这里逐条复用（见 {@link #emit}），只是坐标系从「方块最小角 + XZ∈[0,1]²」换成了
 * 「anchor + 沿轨道弧长」，裁剪也从「格界矩形」换成了「道岔自身的局部平面」。</p>
 *
 * <p><b>面数预算</b>：采样步长 {@link #SAMPLE_STEP}=0.05 格（切向用差分、超高用 tilt），
 * 但真正扫掠的折线会被抽稀到 {@link #MAX_STATIONS} 个站，否则一条 30 格的轨道会扫出上万个顶点。</p>
 */
public final class TurnoutGeometry {

    /** 非主轨（曲股）在交叉处的抬升量，与 {@code GroovedRailProfile.CROSSING_LIFT} 同值同义：抬起来的那份赢 z-fight。 */
    public static final double CROSSING_LIFT = 0.0004D;

    /**
     * 非主轨整体抬升（格），与 {@code GroovedRailProfile.NON_PRIMARY_LIFT} 同值。
     * <p>
     * 曲股在 toe 附近与直股几乎重合（真实道岔里那本来就是同一根轨），两条扫掠带会共面闪烁；
     * 把整条曲股抬高 2 mm 是平交路口已经验证过的解法，肉眼不可见但彻底消灭 z-fight。
     */
    public static final double NON_PRIMARY_LIFT = 0.002D;

    /** 采样步长（格）：position + 差分切向 + tilt 都按它取点。 */
    public static final double SAMPLE_STEP = 0.05D;

    /** 扫掠折线的站数上限（再密就是白烧顶点，成型的弯路 72 站已看不出折线）。 */
    public static final int MAX_STATIONS = 72;

    /**
     * 轮缘槽宽度（格）：与平交路口的 {@code GroovedRailProfile.SLOT_WIDTH} 同值 0.045。
     * <p><b>只用在辙叉与护轨的局部区域</b>（让车轮轮缘通过），不是把整条轨道做成电车槽轨。</p>
     */
    public static final double FLANGEWAY = 0.045D;

    /**
     * 工字钢断面里「轨底」那一段的索引（{@link #section} 的第 0 段 = 底左 → 底右）。
     * <p>轨底面正好坐在枕木顶面（同高共面），发出来只会和 MTR 的枕木 z-fighting，所以扫掠时跳过。</p>
     */
    public static final int BEAM_BOTTOM_SEGMENT = 0;

    /** 尖轨默认长度（格）。 */
    public static final double BLADE_LENGTH = 3.0D;

    /** 尖轨尖端宽度（格）：根部等宽 = 钢轨宽，线性收窄到这里。 */
    public static final double BLADE_TIP_WIDTH = 0.02D;

    /** 护轨长度（格）/ 宽度倍率（相对钢轨宽）。 */
    public static final double CHECK_RAIL_LENGTH = 1.5D;
    public static final double CHECK_RAIL_WIDTH_RATIO = 1.35D;

    /** 尖轨平移量对标准轨距的比例：0.06 格 @ 1435 mm。 */
    public static final double BLADE_TRAVEL_PER_GAUGE = 0.06D / 1.435D;

    /** 钢轨断面缺省值（rail_gauge.json defaults 的硬编码副本，参数无效时兜底）。 */
    private static final double FALLBACK_GROOVE_INNER = 0.6675D;
    private static final double FALLBACK_GROOVE_OUTER = 0.785883D;
    private static final double FALLBACK_GROOVE_DEPTH = 0.09375D;
    private static final double FALLBACK_RAIL_TOP_Y = 0.26428D;

    /** 轮缘槽宽度上限（格），与 {@code GroovedRailProfile.SLOT_WIDTH} 同值。 */
    private static final double SLOT_WIDTH = 0.045D;

    private static final double EPS = 1e-9D;

    private TurnoutGeometry() {
    }

    // ==================== 本地坐标系 ====================

    /**
     * 世界 ↔ 本地 的坐标变换（只含平移 + 绕 Y 旋转，与渲染侧的 pose 严格互逆）。
     * <p>
     * 本地基：{@code e_x = (cos yaw, 0, sin yaw)}、{@code e_z = (-sin yaw, 0, cos yaw)}，
     * 于是 {@code e_x × e_y = e_z}（右手系），本地坐标 = {@code (d·e_x, d.y, d·e_z)}。
     */
    public static final class Frame {

        private final double anchorX;
        private final double anchorY;
        private final double anchorZ;
        private final double ex;
        private final double ez;

        public Frame(double anchorX, double anchorY, double anchorZ, double yawDeg) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            double yaw = Math.toRadians(yawDeg);
            this.ex = Math.cos(yaw);
            this.ez = Math.sin(yaw);
        }

        /** 世界点 → 本地坐标（相对 anchor）。 */
        public double[] toLocal(double worldX, double worldY, double worldZ) {
            double dx = worldX - anchorX;
            double dz = worldZ - anchorZ;
            return new double[]{dx * ex + dz * ez, worldY - anchorY, -dx * ez + dz * ex};
        }

        public double anchorX() {
            return anchorX;
        }

        public double anchorY() {
            return anchorY;
        }

        public double anchorZ() {
            return anchorZ;
        }
    }

    // ==================== 采样站 ====================

    /**
     * 一个采样站：本地坐标 + 水平单位切向 + 超高 + 距 toe 的弧长。
     *
     * @param x    本地 X（格）
     * @param y    本地 Y（相对 anchor，格）
     * @param z    本地 Z（格）
     * @param tx   水平单位切向 X 分量
     * @param tz   水平单位切向 Z 分量
     * @param tilt 超高（弧度，绕切向滚转；正值 = 本地 +Z 侧抬起）
     * @param arc  距 toe 的弧长（格，单调不减）
     */
    public record Station(double x, double y, double z, double tx, double tz, double tilt, double arc) {

        /** 该站处的横向单位向量 {@code L = (-tz, 0, tx)}（本地 +Z 一定在 L 的正方向一侧）。 */
        public double lateralX() {
            return -tz;
        }

        public double lateralZ() {
            return tx;
        }

        /** 与另一站的平面距离。 */
        public double distanceTo(Station other) {
            return Math.hypot(x - other.x, z - other.z);
        }
    }

    public static double totalArc(List<Station> stations) {
        if (stations == null || stations.isEmpty()) return 0.0D;
        return stations.get(stations.size() - 1).arc();
    }

    /** 按弧长线性插值取一站（切向做归一化插值，超高线性）。 */
    public static Station interpolate(List<Station> stations, double arc) {
        if (stations == null || stations.isEmpty()) {
            return new Station(0, 0, 0, 1, 0, 0, 0);
        }
        Station first = stations.get(0);
        if (arc <= first.arc()) return first;
        Station last = stations.get(stations.size() - 1);
        if (arc >= last.arc()) return last;
        // 保留全密度曲线后，以二分定位区间，避免每个 OBJ 顶点都线性扫描整条腿。
        int low = 0, high = stations.size() - 1;
        while (low + 1 < high) {
            int mid = (low + high) >>> 1;
            if (stations.get(mid).arc() <= arc) low = mid; else high = mid;
        }
        {
            int i = low;
            Station a = stations.get(i);
            Station b = stations.get(i + 1);
            double span = b.arc() - a.arc();
            double t = span < EPS ? 0.0D : (arc - a.arc()) / span;
            double tx = a.tx() + (b.tx() - a.tx()) * t;
            double tz = a.tz() + (b.tz() - a.tz()) * t;
            double len = Math.hypot(tx, tz);
            if (len < EPS) {
                tx = a.tx();
                tz = a.tz();
            } else {
                tx /= len;
                tz /= len;
            }
            return new Station(a.x() + (b.x() - a.x()) * t,
                    a.y() + (b.y() - a.y()) * t,
                    a.z() + (b.z() - a.z()) * t,
                    tx, tz,
                    a.tilt() + (b.tilt() - a.tilt()) * t,
                    arc);
        }
    }

    /** 取 {@code [fromArc, toArc]} 这一段采样站（两端插值补齐，长度不足时返回空表）。 */
    public static List<Station> slice(List<Station> stations, double fromArc, double toArc) {
        List<Station> out = new ArrayList<>();
        if (stations == null || stations.size() < 2) return out;
        double total = totalArc(stations);
        double a = Math.max(0.0D, Math.min(fromArc, total));
        double b = Math.max(0.0D, Math.min(toArc, total));
        if (b - a < SAMPLE_STEP) return out;
        out.add(interpolate(stations, a));
        for (Station s : stations) {
            if (s.arc() > a + EPS && s.arc() < b - EPS) out.add(s);
        }
        out.add(interpolate(stations, b));
        return out.size() >= 2 ? out : new ArrayList<>();
    }

    /**
     * 按弧长把折线抽稀到不超过 {@code max} 个站（首尾保留）。
     * <p>
     * 与平交路口的 {@code TrackStrips.decimateOpen} 同一思路：扫掠顶点数 ≈ 站数 × 断面段数 × 4，
     * 采样密度必须与顶点预算挂钩，采样步长只负责「取点准」，不负责「都进缓冲」。
     */
    public static List<Station> decimate(List<Station> stations, int max) {
        int n = stations == null ? 0 : stations.size();
        if (n <= max || max < 2) return stations;
        double total = totalArc(stations);
        if (total < EPS) return stations;
        List<Station> out = new ArrayList<>(max);
        double step = total / (max - 1);
        out.add(stations.get(0));
        for (int i = 1; i < max - 1; i++) {
            Station s = interpolate(stations, i * step);
            if (out.get(out.size() - 1).distanceTo(s) > 1e-6 || Math.abs(s.arc() - out.get(out.size() - 1).arc()) > 1e-6) {
                out.add(s);
            }
        }
        Station last = stations.get(n - 1);
        if (out.get(out.size() - 1).arc() < last.arc() - 1e-6) out.add(last);
        return out;
    }

    // ==================== 断面 ====================

    /**
     * 钢轨断面：<b>工字钢</b>（轨头 + 轨腰 + 轨底），10 点闭合折线 {@code [(v, y), ...]}（首点不重复）。
     *
     * <p><b>为什么不沿用槽型钢</b>：平交路口的「槽型钢」是<b>电车槽轨</b>（内板 + 轮缘槽 + 钢板，
     * 见 {@link #grooveSectionLegacy}），道岔这里要的是真实钢轨断面的工字钢，所以按所选模型的实测值
     * （{@link TurnoutRailParams.BeamProfile}）生成。</p>
     *
     * <p><b>坐标</b>：{@code v} 以<b>本轨轨头中心</b>为 0（左右对称），{@code y} 是<b>模型空间绝对高度</b>
     * （0 = 轨道模型原点平面 / 方块底面），即 轨底 = {@code baseY}、轨顶 = {@code topY}。
     * 这样几何的「0 平面」和 MTR 自己渲染轨道模型时的原点平面一致，高度天然对齐。</p>
     *
     * <p><b>绕序</b>：逆时针（在 (v,y) 平面内正面积）。{@link #sweep} 会按断面自身的有符号面积
     * 判定朝向，所以逆时针/顺时针都能正确算出外法线。</p>
     */
    public static List<double[]> section(TurnoutRailParams params) {
        TurnoutRailParams.BeamProfile beam = beam(params);
        double base = beam.baseY();
        double footTop = Math.max(beam.footTopY(), base + 1e-4D);
        double webTop = Math.max(beam.webTopY(), footTop + 1e-4D);
        double top = Math.max(beam.topY(), webTop + 1e-4D);
        double footHalf = Math.max(beam.footHalf(), 1e-3D);
        double webHalf = clamp(beam.webHalf(), 1e-3D, footHalf - 1e-4D);
        double headHalf = clamp(beam.headHalf(), webHalf, footHalf);
        double topHalf = clamp(beam.headTopHalf(), webHalf, headHalf);

        // 逆时针：底左 → 底右 → 腰右 → 头右 → 顶右 → 顶左 → 头左 → 腰左 → 回底左
        List<double[]> pts = new ArrayList<>(11);
        pts.add(new double[]{-footHalf, base});
        pts.add(new double[]{footHalf, base});
        pts.add(new double[]{webHalf, footTop});
        pts.add(new double[]{webHalf, webTop});
        pts.add(new double[]{headHalf, webTop});
        pts.add(new double[]{topHalf, top});
        pts.add(new double[]{-topHalf, top});
        pts.add(new double[]{-headHalf, webTop});
        pts.add(new double[]{-webHalf, webTop});
        pts.add(new double[]{-webHalf, footTop});
        return pts;
    }

    /**
     * <b>已弃用（legacy）</b>：平交路口的「槽型钢」断面（内板 / 轮缘槽 / 钢板）。
     * <p>它是<b>电车槽轨</b>的形状，道岔要工字钢，所以 {@link #section} 已改成
     * {@link TurnoutRailParams.BeamProfile}。这段代码保留是因为平交路口那套断面语义仍有参考价值
     * （{@code GroovedRailProfile.section} 与它同构），<b>当前没有任何调用点</b>。</p>
     */
    public static List<double[]> grooveSectionLegacy(TurnoutRailParams params) {
        double gIn = params == null ? FALLBACK_GROOVE_INNER : params.grooveInner();
        double gOut = params == null ? FALLBACK_GROOVE_OUTER : params.grooveOuter();
        if (!(gOut > gIn + 1e-4D)) {
            gIn = FALLBACK_GROOVE_INNER;
            gOut = FALLBACK_GROOVE_OUTER;
        }
        double width = gOut - gIn;
        double slotWidth = Math.min(SLOT_WIDTH, width * 0.5D);
        double center = (gIn + gOut) * 0.5D;
        double slotIn = center - slotWidth * 0.5D;
        double slotOut = center + slotWidth * 0.5D;
        double depth = clamp(params == null ? FALLBACK_GROOVE_DEPTH : params.grooveDepth(), 0.02D, 0.14D);
        double body = clamp(params == null ? FALLBACK_RAIL_TOP_Y : params.railTopY(), 0.10D, 0.40D);

        List<double[]> pts = new ArrayList<>(8);
        pts.add(new double[]{gIn, -body});
        pts.add(new double[]{gIn, 0});
        pts.add(new double[]{slotIn, 0});
        pts.add(new double[]{slotIn, -depth});
        pts.add(new double[]{slotOut, -depth});
        pts.add(new double[]{slotOut, 0});
        pts.add(new double[]{gOut, 0});
        pts.add(new double[]{gOut, -body});
        return pts;
    }

    /** 断面各点的累积周长比例（0..1），给 UV 的 v 用，保证相邻面纹理连续。 */
    public static double[] perimeter(List<double[]> section) {
        int n = section.size();
        double[] at = new double[n];
        double acc = 0;
        for (int i = 1; i < n; i++) {
            double[] a = section.get(i - 1);
            double[] b = section.get(i);
            acc += Math.hypot(b[0] - a[0], b[1] - a[1]);
            at[i] = acc;
        }
        acc += Math.hypot(section.get(0)[0] - section.get(n - 1)[0],
                section.get(0)[1] - section.get(n - 1)[1]);
        if (acc < EPS) acc = 1.0D;
        for (int i = 0; i < n; i++) at[i] /= acc;
        return at;
    }

    // ==================== 工字钢度量（多处共用，集中一处避免各算一遍） ====================

    /** 取该轨道的工字钢断面实测值（params 为空时回落 mtr:default 的折算值）。 */
    public static TurnoutRailParams.BeamProfile beam(TurnoutRailParams params) {
        TurnoutRailParams.BeamProfile beam = params == null ? null : params.beam();
        return beam == null ? TurnoutRailParams.BeamProfile.MTR : beam;
    }

    /** 钢轨的横向总宽（取最宽的轨底）。 */
    public static double railWidth(TurnoutRailParams params) {
        return beam(params).footHalf() * 2.0D;
    }

    /** 钢轨内侧沿（轨底内沿）到轨道中心线的距离。 */
    public static double railInner(TurnoutRailParams params) {
        return railCenter(params) - beam(params).footHalf();
    }

    /** 钢轨外侧沿（轨底外沿）到轨道中心线的距离。 */
    public static double railOuter(TurnoutRailParams params) {
        return railCenter(params) + beam(params).footHalf();
    }

    /**
     * 钢轨中心到轨道中心线的横向距离（格）。
     * <p>基准 = {@code gaugeBlocks / 2}，再加 {@link TurnoutRailParams#lateralBias()}
     * （默认值是「模型实测轨头中心 − gauge/2」，用来和 MTR 自己渲染的轨道对齐）。</p>
     */
    public static double railCenter(TurnoutRailParams params) {
        if (params == null) {
            return TurnoutRailParams.DEFAULT_GAUGE_BLOCKS * 0.5D;
        }
        return params.railCenterOffset();
    }

    /** 钢轨底面高度（模型空间绝对 y，0 = 轨道模型原点平面）。 */
    public static double beamBaseY(TurnoutRailParams params) {
        return beam(params).baseY();
    }

    /** 轨顶高度（模型空间绝对 y）。 */
    public static double beamTopY(TurnoutRailParams params) {
        return beam(params).topY();
    }

    /** 工字钢总高（轨底 → 轨顶）。 */
    public static double bodyDepth(TurnoutRailParams params) {
        return Math.max(1e-3D, beamTopY(params) - beamBaseY(params));
    }

    // ==================== 站上取点 ====================

    /**
     * 断面点 {@code (v, h)} 在某个采样站上的本地世界坐标（含超高滚转与整体抬升）。
     *
     * @param v 横向坐标（已含左右侧符号），正方向 = 该站本地 +Z 侧
     * @param h 相对轨面的高度
     */
    public static double[] point(Station s, double v, double h, double yLift) {
        double cr = Math.cos(s.tilt());
        double sr = Math.sin(s.tilt());
        double lx = s.lateralX();
        double lz = s.lateralZ();
        // 横向轴绕切向转 tilt：L' = L·cos + Y·sin，Y' = -L·sin + Y·cos
        double x = s.x() + v * lx * cr - h * lx * sr;
        double y = s.y() + v * sr + h * cr + yLift;
        double z = s.z() + v * lz * cr - h * lz * sr;
        return new double[]{x, y, z};
    }

    // ==================== 网格发射 ====================

    /**
     * 两个材质的接收桶（水平面 → rail_top 条带、竖直/斜面 → rail_side），与平交路口完全一致的观感来源。
     * <p>
     * 缺图时两张都回退 {@link LevelCrossingStyles#RAIL_TEXTURE}，绝不把错误贴图贴到钢轨上。
     */
    public static final class Sink {

        private final RawMeshBuilder top;
        private final RawMeshBuilder side;
        /** 素材部位按「材质贴图」分桶（拼装层用；惰性创建）。 */
        private final java.util.Map<ResourceLocation, RawMeshBuilder> materialBuckets = new java.util.LinkedHashMap<>();
        private int emissFaces;

        public Sink(ResourceLocation topTex, ResourceLocation sideTex) {
            this.top = new RawMeshBuilder(4, "exterior", topTex);
            this.side = new RawMeshBuilder(4, "exterior", sideTex);
        }

        /** 按 {@link TurnoutRailParams} 建桶（贴图缺省走已验证的平交路口钢轨贴图）。 */
        public static Sink of(TurnoutRailParams params) {
            ResourceLocation top = LevelCrossingStyles.railTopTexture();
            ResourceLocation side = LevelCrossingStyles.railSideTexture();
            if (top == null) top = LevelCrossingStyles.RAIL_TEXTURE;
            if (side == null) side = LevelCrossingStyles.RAIL_TEXTURE;
            return new Sink(top, side);
        }

        public int emittedFaces() {
            return emissFaces;
        }

        public int vertices() {
            int n = countVertices(top) + countVertices(side);
            for (RawMeshBuilder builder : materialBuckets.values()) n += countVertices(builder);
            return n;
        }

        /** 把当前所有桶合成一份模型（尖轨收集器用它把自己变成独立 RawModel）；空几何返回 null。 */
        public RawModel toModel() {
            RawModel model = new RawModel();
            mergeInto(model);
            if (model.getFaceCount() == 0) return null;
            model.triangulate();
            return model;
        }

        public void mergeInto(RawModel model) {
            if (model == null) return;
            model.append(top.getMesh());
            model.append(side.getMesh());
            for (RawMeshBuilder builder : materialBuckets.values()) {
                model.append(builder.getMesh());
            }
        }

        /**
         * 取（或惰性创建）某个贴图的材质桶。拼装素材部位时按材质的 {@code map_Kd} 分桶，
         * 这样 {@code rail_top} / {@code rail_side} / {@code zhenmu} / {@code daoding} 各用各的贴图 ——
         * 工字钢的顶面/侧面正是靠这两张贴图区分开的，混桶会把它画成一根「管」。
         */
        public RawMeshBuilder bucket(ResourceLocation texture) {
            ResourceLocation tex = texture != null ? texture : LevelCrossingStyles.RAIL_TEXTURE;
            RawMeshBuilder builder = materialBuckets.get(tex);
            if (builder == null) {
                builder = new RawMeshBuilder(4, "exterior", tex);
                materialBuckets.put(tex, builder);
            }
            return builder;
        }

        /**
         * 逐顶点写入一个三角形：<b>直接用调用方给的法线与 UV，不纠正绕序、不重算法线</b>。
         * <p>拼装素材部位时，位置与法线都已经变换到本地空间、UV 已经是素材原值（含 V 翻转），
         * 这里只负责把顶点塞进对应的材质桶（第 4 点复制最后一点，维持 faceSize=4 的既有约定）。</p>
         */
        public void emitRaw(RawMeshBuilder out, double[][] pts, float[][] uvs, float[][] normals) {
            if (out == null || pts == null || pts.length < 3) return;
            int n = pts.length;
            for (int i = 0; i < n; i++) {
                rawVertex(out, pts[i],
                        uvs == null || i >= uvs.length ? null : uvs[i],
                        normals == null || i >= normals.length ? null : normals[i]);
            }
            rawVertex(out, pts[n - 1],
                    uvs == null || n - 1 >= uvs.length ? null : uvs[n - 1],
                    normals == null || n - 1 >= normals.length ? null : normals[n - 1]);
            emissFaces++;
        }

        private static void rawVertex(RawMeshBuilder out, double[] p, float[] uv, float[] n) {
            float nx = n == null ? 0.0F : n[0];
            float ny = n == null ? 1.0F : n[1];
            float nz = n == null ? 0.0F : n[2];
            float u = uv == null ? 0.0F : uv[0];
            float v = uv == null ? 0.0F : uv[1];
            out.vertex(p[0], p[1], p[2]).normal(nx, ny, nz).uv(u, v).endVertex();
        }

        private static int countVertices(RawMeshBuilder builder) {
            if (builder == null || builder.getMesh() == null || builder.getMesh().vertices == null) return 0;
            return builder.getMesh().vertices.size();
        }

        /**
         * 发射一个凸多边形面（3/4 点）：法线取<b>几何法线</b>，绕序按期望朝向纠正，
         * 纠正后法线同步取反（同 {@code CrossingMeshBuilder.triangle}），光照与面朝向才不打架。
         * <p>
         * 每个三角形仍发 4 个顶点（第 4 点复制第 3 点），保持 {@code faceSize=4} 的既有约定。
         */
        public void emit(double[][] pts, float[][] uvs, double ex, double ey, double ez) {
            if (pts == null || pts.length < 3 || uvs == null || uvs.length != pts.length) return;
            // 只发凸多边形（本类所有面都是三/四边形），按扇形拆成三角形逐个纠正绕序
            for (int i = 1; i + 1 < pts.length; i++) {
                emitTriangle(pts[0], uvs[0], pts[i], uvs[i], pts[i + 1], uvs[i + 1], ex, ey, ez);
            }
        }

        private void emitTriangle(double[] pa, float[] ua, double[] pb, float[] ub,
                                  double[] pc, float[] uc, double ex, double ey, double ez) {
            double[] a = pa;
            double[] b = pb;
            double[] c = pc;
            double nx = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]);
            double ny = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
            double nz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
            double len = Math.hypot(Math.hypot(nx, ny), nz);
            if (!(len > 1e-12D) || !Double.isFinite(len)) return;
            nx /= len;
            ny /= len;
            nz /= len;
            if (nx * ex + ny * ey + nz * ez < 0) {
                double[] tmpP = b;
                b = c;
                c = tmpP;
                float[] tmpU = ub;
                ub = uc;
                uc = tmpU;
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            RawMeshBuilder out = Math.abs(ny) >= Math.abs(nx) && Math.abs(ny) >= Math.abs(nz) ? top : side;
            vertex(out, a, nx, ny, nz, ua);
            vertex(out, b, nx, ny, nz, ub);
            vertex(out, c, nx, ny, nz, uc);
            vertex(out, c, nx, ny, nz, uc);
            emissFaces++;
        }

        private static void vertex(RawMeshBuilder out, double[] p, double nx, double ny, double nz, float[] uv) {
            out.vertex(p[0], p[1], p[2]).normal((float) nx, (float) ny, (float) nz).uv(uv[0], uv[1]).endVertex();
        }
    }

    /** 建一个空的接收桶（供各 Builder 共用同一份贴图策略）。 */
    public static Sink newSink(TurnoutRailParams params) {
        return Sink.of(params);
    }

    /**
     * 沿采样站扫掠一份断面（钢轨中心线在 {@code side * lateralCenter} 处）。
     *
     * @param stations       采样站（至少 2 站，弧长单调）
     * @param section        断面闭合折线（{@link #section}；v 以本轨中心为 0）
     * @param side           +1 = 该轨在本地 +Z 侧，-1 = -Z 侧
     * @param lateralCenter  该轨中心到轨道中心线的横向距离（{@link #railCenter}）
     * @param yLift          整体抬升（格；交叉处 0.0004）
     * @param repeatInterval 贴图沿轨的复现间隔（格）
     * @param skipSegment    不发射的断面段索引（轨底；-1 = 全发）
     * @param capEnds        是否封两端面
     */
    public static void sweep(Sink sink, List<Station> stations, List<double[]> section, int side,
                             double lateralCenter, double yLift, double repeatInterval,
                             int skipSegment, boolean capEnds) {
        if (sink == null || stations == null || stations.size() < 2 || section == null || section.size() < 3) {
            return;
        }
        double invRepeat = repeatInterval > 1e-4D ? 1.0D / repeatInterval : 1.0D;
        double[] perim = perimeter(section);
        // 断面绕序自适应：逆时针（正面积）时外法线 = (dh, -dv)，顺时针时 = (-dh, dv)。
        // 这样工字钢（逆时针）与 legacy 槽型钢（顺时针）都能得到朝外的法线。
        double orient = sectionArea(section) >= 0 ? 1.0D : -1.0D;
        int n = stations.size();
        int m = section.size();
        for (int i = 0; i + 1 < n; i++) {
            Station s0 = stations.get(i);
            Station s1 = stations.get(i + 1);
            float u0 = (float) (s0.arc() * invRepeat);
            float u1 = (float) (s1.arc() * invRepeat);
            for (int k = 0; k < m; k++) {
                if (k == skipSegment) continue;
                int k2 = (k + 1) % m;
                double[] pa = section.get(k);
                double[] pb = section.get(k2);
                double dv = pb[0] - pa[0];
                double dh = pb[1] - pa[1];
                double segLen = Math.hypot(dv, dh);
                if (!(segLen > 1e-9D)) continue;
                double nv = orient * dh / segLen;
                double nh = -orient * dv / segLen;
                double vA = side * (lateralCenter + pa[0]);
                double vB = side * (lateralCenter + pb[0]);
                double[] p00 = point(s0, vA, pa[1], yLift);
                double[] p01 = point(s0, vB, pb[1], yLift);
                double[] p11 = point(s1, vB, pb[1], yLift);
                double[] p10 = point(s1, vA, pa[1], yLift);
                double[] nrm = expectedNormal(s0, side * nv, nh);
                float va = (float) perim[k];
                float vb = (float) perim[k2];
                sink.emit(new double[][]{p00, p01, p11, p10},
                        new float[][]{{u0, va}, {u0, vb}, {u1, vb}, {u1, va}},
                        nrm[0], nrm[1], nrm[2]);
            }
        }
        if (capEnds) {
            cap(sink, stations.get(0), section, side, lateralCenter, yLift, true);
            cap(sink, stations.get(n - 1), section, side, lateralCenter, yLift, false);
        }
    }

    /** 断面的有符号面积（(v,h) 平面），只用来判绕序。 */
    private static double sectionArea(List<double[]> section) {
        int n = section.size();
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double[] a = section.get(i);
            double[] b = section.get((i + 1) % n);
            sum += a[0] * b[1] - b[0] * a[1];
        }
        return sum * 0.5D;
    }

    /**
     * 端面封口：把断面按 {@link PolyOps#triangulate} 三角化（槽型的凹多边形也正确），
     * 期望法线取切向（起点朝后、终点朝前）。
     */
    public static void cap(Sink sink, Station s, List<double[]> section, int side,
                           double lateralCenter, double yLift, boolean atStart) {
        if (sink == null || s == null || section == null || section.size() < 3) return;
        List<Vec2> planar = new ArrayList<>(section.size());
        for (double[] p : section) planar.add(new Vec2(p[0], p[1]));
        List<Vec2> ring = PolyOps.clean(planar);
        if (ring.size() < 3) return;
        double nx = atStart ? -s.tx() : s.tx();
        double nz = atStart ? -s.tz() : s.tz();
        for (int[] tri : PolyOps.triangulate(ring)) {
            double[][] pts = new double[3][];
            float[][] uvs = new float[3][];
            for (int k = 0; k < 3; k++) {
                Vec2 q = ring.get(tri[k]);
                pts[k] = point(s, side * (lateralCenter + q.x()), q.z(), yLift);
                uvs[k] = new float[]{(float) q.x(), (float) q.z()};
            }
            sink.emit(pts, uvs, nx, 0, nz);
        }
    }

    /** 断面段的外法线（含超高滚转）在本地空间的方向。 */
    private static double[] expectedNormal(Station s, double nv, double nh) {
        double cr = Math.cos(s.tilt());
        double sr = Math.sin(s.tilt());
        double lx = s.lateralX();
        double lz = s.lateralZ();
        // n = L'·nv + Y'·nh
        return new double[]{lx * (cr * nv - sr * nh), sr * nv + cr * nh, lz * (cr * nv - sr * nh)};
    }

    // ==================== 平面工具（辙叉用） ====================

    /** 点到折线的平面距离（折线为本地 XZ 上的 (x,z) 数组对）。 */
    public static double distanceToPolyline(double px, double pz, List<Station> line) {
        if (line == null || line.size() < 2) return Double.POSITIVE_INFINITY;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 1 < line.size(); i++) {
            Station a = line.get(i);
            Station b = line.get(i + 1);
            double abx = b.x() - a.x();
            double abz = b.z() - a.z();
            double len2 = abx * abx + abz * abz;
            double t = len2 < EPS ? 0.0D : ((px - a.x()) * abx + (pz - a.z()) * abz) / len2;
            t = Math.max(0.0D, Math.min(1.0D, t));
            double qx = a.x() + abx * t;
            double qz = a.z() + abz * t;
            double d = Math.hypot(px - qx, pz - qz);
            if (d < best) best = d;
        }
        return best;
    }

    /**
     * 把一条采样折线横向平移 {@code lateral}（正 = 本地 +Z 侧），得到该侧钢轨的中心线。
     * 不需要斜接：道岔的弯道曲率远小于平交路口的急弯，等距法线偏移的误差在 0.1 mm 量级。
     */
    public static List<Station> offsetLine(List<Station> stations, double lateral) {
        List<Station> out = new ArrayList<>();
        if (stations == null) return out;
        for (Station s : stations) {
            double lx = s.lateralX();
            double lz = s.lateralZ();
            out.add(new Station(s.x() + lx * lateral, s.y(), s.z() + lz * lateral,
                    s.tx(), s.tz(), s.tilt(), s.arc()));
        }
        return out;
    }

    /** 两段有向折线在平面上的交点的弧长参数（以 {@code moving} 的弧长表示）；无交点返回 NaN。 */
    public static double crossingArc(List<Station> fixed, List<Station> moving) {
        if (fixed == null || moving == null || fixed.size() < 2 || moving.size() < 2) return Double.NaN;
        for (int i = 0; i + 1 < moving.size(); i++) {
            Station m0 = moving.get(i);
            Station m1 = moving.get(i + 1);
            double d0 = signedSide(fixed, m0);
            double d1 = signedSide(fixed, m1);
            if (!Double.isFinite(d0) || !Double.isFinite(d1)) continue;
            if (d0 == 0) return m0.arc();
            if (d0 * d1 > 0) continue;
            double span = d1 - d0;
            double t = Math.abs(span) < 1e-12D ? 0.0D : d0 / (d0 - d1);
            return m0.arc() + (m1.arc() - m0.arc()) * Math.max(0.0D, Math.min(1.0D, t));
        }
        return Double.NaN;
    }

    /**
     * 点相对「有向折线」的带符号距离：正 = 在折线的本地 +Z 侧（左侧法线），负 = 另一侧。
     * <p>
     * 取最近的那一段算，用于判交点两侧的符号变化。
     */
    private static double signedSide(List<Station> line, Station p) {
        double best = Double.POSITIVE_INFINITY;
        double sign = 0;
        for (int i = 0; i + 1 < line.size(); i++) {
            Station a = line.get(i);
            Station b = line.get(i + 1);
            double abx = b.x() - a.x();
            double abz = b.z() - a.z();
            double len = Math.hypot(abx, abz);
            if (len < EPS) continue;
            double t = ((p.x() - a.x()) * abx + (p.z() - a.z()) * abz) / (len * len);
            t = Math.max(0.0D, Math.min(1.0D, t));
            double qx = a.x() + abx * t;
            double qz = a.z() + abz * t;
            double d = Math.hypot(p.x() - qx, p.z() - qz);
            if (d < best) {
                best = d;
                // 叉积：ab × ap 的 Z 分量（本地 XZ 平面），正 = p 在 ab 的 +Z 侧
                double cross = abx * (p.z() - a.z()) - abz * (p.x() - a.x());
                sign = cross >= 0 ? 1 : -1;
            }
        }
        return Double.isFinite(best) ? best * sign : Double.NaN;
    }

    /** 两段切向的水平夹角（弧度，0..π/2 折回后取绝对值）。 */
    public static double angleBetween(Station a, Station b) {
        double dot = a.tx() * b.tx() + a.tz() * b.tz();
        dot = Math.max(-1.0D, Math.min(1.0D, dot));
        double ang = Math.acos(dot);
        return ang > Math.PI * 0.5D ? Math.PI - ang : ang;
    }

    public static double clamp(double v, double min, double max) {
        if (!Double.isFinite(v)) return min;
        return v < min ? min : (v > max ? max : v);
    }

    /** 平滑缓动（smoothstep）：尖轨位移用它，两端速度为零，切换不会有「弹一下」的观感。 */
    public static double smoothstep(double t) {
        double x = clamp(t, 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }
}
