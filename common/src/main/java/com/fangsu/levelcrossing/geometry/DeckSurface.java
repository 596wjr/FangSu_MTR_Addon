package com.fangsu.levelcrossing.geometry;

import com.fangsu.levelcrossing.CellSnapshot;
import com.fangsu.levelcrossing.CrossingSettings;
import com.fangsu.levelcrossing.TrackSlice;

import java.util.ArrayList;
import java.util.List;

/**
 * 铺面/地面/轨顶的高度场与平面布局（纯数学，不依赖 Minecraft）。
 * <p>
 * 平面约定（全项目统一，P2 采样必须逐字一致）：
 * <pre>
 *   dir          = (cos(yaw), sin(yaw))，yaw 取主轨 yawDeg 的弧度
 *   u(x,z)       =  (x-0.5)*dirX + (z-0.5)*dirZ
 *   v(x,z)       = -(x-0.5)*dirZ + (z-0.5)*dirX
 *   deckY(x,z)   = deckTopY + tan(pitch)*u + tan(roll)*v
 *   groundY(x,z)= deckY - (deckTopY - groundTopY)
 *   railTopY     = deckY + (slice.railTopY - deckTopY)
 * </pre>
 * 没有轨道时 dir 退化为 (1,0)、pitch/roll 视为 0，deckY 变成常数。
 * <p>
 * <b>铺面直接铺满 [0,1]²</b>：相邻的平交路口格各自铺到格界，两块顶面在格界处严丝合缝。
 * 斜坡只在「远离轨道」的那两条边向外延伸，落在方块底面（或配置的 {@code rampFootY}）上。
 */
public final class DeckSurface {

    /**
     * 竖直面（裙边/铺面侧壁）贴格界时向内让出的极小量，纯粹为避免与邻居的同类竖直面共面 z-fighting。
     * <p>
     * <b>只作用于竖直面</b>：顶面（铺面/地面）一律铺满 0..1，否则相邻格之间会出现缝
     * （历史 bug：曾用 deckInset 当水平内缩，默认 0.02 → 相邻格之间固定 0.04 的缝）。
     */
    public static final double MIN_CELL_INSET = 0.0005;

    /** 格边编号：0 = x 最小侧，1 = x 最大侧，2 = z 最小侧，3 = z 最大侧。 */
    public static final int SIDE_NEG_X = 0;
    public static final int SIDE_POS_X = 1;
    public static final int SIDE_NEG_Z = 2;
    public static final int SIDE_POS_Z = 3;

    /** 斜坡长度钳制区间（格）：至少 0.25，最多 1 格（用户口径「在 1 格内延伸到地面」）。 */
    public static final double MIN_RAMP_RUN = 0.25D;
    public static final double MAX_RAMP_RUN = 1.0D;

    /**
     * 出坡判定阈值：边外法线与轨道方向夹角的余弦绝对值小于这个值即出坡，
     * 等价于夹角落在 <b>46°..134°</b>（{@code cos 46° = 0.694658}）。
     * <p>
     * 数学后果（写死在自检 T13/T18 里，避免以后误以为放宽能覆盖斜轨）：
     * <pre>
     *   轨道 yaw    最近格边 |dot|   46–134° 下是否出坡
     *   0°          0               出坡
     *   30°         0.500           出坡
     *   39.8°       0.768           不出坡
     *   45°         0.707           不出坡（差一点点）
     * </pre>
     * 也就是说 39.8° 的斜轨仍然不出坡；要覆盖它下限必须 ≤39.8°。阈值由用户口径确定，不得擅自再改。
     */
    public static final double RAMP_NORMAL_DOT_LIMIT = Math.cos(Math.toRadians(46));

    /**
     * @param side    格边编号（{@link #SIDE_NEG_X} 等）
     * @param outward 该边向外的单位法线
     * @param run     斜坡水平长度（格，0.25..1）
     * @param deck    坡顶（格边处）的高度，沿边由 {@link #deckY} 决定
     */
    public record Ramp(int side, Vec2 outward, double run) {
    }

    private DeckSurface() {
    }

    /** 主轨：primary==true 的那条，没有则取第一条，没有轨道返回 null。 */
    public static TrackSlice primary(CellSnapshot snap) {
        if (snap == null || snap.tracks == null || snap.tracks.isEmpty()) return null;
        for (TrackSlice slice : snap.tracks) {
            if (slice != null && slice.primary) return slice;
        }
        for (TrackSlice slice : snap.tracks) {
            if (slice != null) return slice;
        }
        return null;
    }

    public static double dirX(CellSnapshot snap) {
        TrackSlice p = primary(snap);
        return p == null ? 1 : Math.cos(Math.toRadians(p.yawDeg));
    }

    public static double dirZ(CellSnapshot snap) {
        TrackSlice p = primary(snap);
        return p == null ? 0 : Math.sin(Math.toRadians(p.yawDeg));
    }

    public static double u(CellSnapshot snap, double x, double z) {
        return (x - 0.5) * dirX(snap) + (z - 0.5) * dirZ(snap);
    }

    public static double v(CellSnapshot snap, double x, double z) {
        return -(x - 0.5) * dirZ(snap) + (z - 0.5) * dirX(snap);
    }

    public static double deckY(CellSnapshot snap, double x, double z) {
        if (snap == null) return 0;
        if (snap.tracks == null || snap.tracks.isEmpty()) return snap.deckTopY;
        double pitch = Math.toRadians(snap.pitchDeg);
        double roll = Math.toRadians(snap.rollDeg);
        return snap.deckTopY + Math.tan(pitch) * u(snap, x, z) + Math.tan(roll) * v(snap, x, z);
    }

    public static double groundY(CellSnapshot snap, double x, double z) {
        if (snap == null) return 0;
        return deckY(snap, x, z) - (snap.deckTopY - snap.groundTopY);
    }

    /**
     * <b>钢轨断面的 y=0 平面</b>（= 我们的钢轨顶面高度场）。
     * <p>
     * = 铺面高度 + (该轨原版轨顶 - 铺面基准) + {@link CellSnapshot#railTopBias}。
     * 偏置只放在这<b>唯一一处</b>：几何（扫掠/端面/切口面）与碰撞盒都读它，
     * 「钢轨整体上移 bias」就是一个数的事（用户口径：与原版轨顶完全对齐但稍高一点）。
     * {@link TrackSlice#railTopY} 仍是「原版轨顶」，对齐日志拿它当基准。
     */
    public static double railTopY(TrackSlice slice, CellSnapshot snap, double x, double z) {
        if (slice == null || snap == null) return groundY(snap, x, z);
        return deckY(snap, x, z) + (slice.railTopY - snap.deckTopY) + snap.railTopBias;
    }

    /**
     * 铺面矩形：<b>严格铺满单位格 [0,1]²</b>。
     * <p>
     * 水平方向一律不外扩/内缩：相邻的平交路口格各自铺到格界，两块顶面在格界处恰好相接，
     * 一整片道口才连成一块（用户反馈的「每一块不连着」就是这里内缩造成的）。
     * 垂直下沉由配置项 {@code deckInset} 单独负责（在 sampler 里参与 {@code deckTopY}），与本方法无关。
     */
    public static List<Vec2> deckRect(CrossingSettings cfg) {
        return PolyOps.rect(0, 0, 1, 1);
    }

    /**
     * 铺面顶面：铺面矩形减去每条钢轨走廊，返回互不重叠的凸片。
     * <p>
     * 斜坡在格<b>外</b>，不再从铺面里挖条带（旧实现在格内挖倒角，既切碎了铺面又出不了坡）。
     */
    public static List<List<Vec2>> pavementTops(CellSnapshot snap, CrossingSettings cfg) {
        List<List<Vec2>> pieces = new ArrayList<>();
        pieces.add(deckRect(cfg));
        for (TrackStrips.Band band : TrackStrips.bands(snap)) {
            pieces = subtractAll(pieces, band.poly());
            if (pieces.isEmpty()) break;
        }
        // 差集碎片可能是凹的：凹片一律拆成凸片，扇形/耳朵裁剪都只在凸片上退化最轻
        List<List<Vec2>> out = new ArrayList<>(pieces.size());
        for (List<Vec2> piece : pieces) {
            if (piece.size() < 3 || Math.abs(PolyOps.area(piece)) <= PolyOps.AREA_EPS) continue;
            out.addAll(PolyOps.convexParts(piece));
        }
        return out;
    }

    /** 把一组互不重叠的片各自减去同一个凸多边形。 */
    private static List<List<Vec2>> subtractAll(List<List<Vec2>> pieces, List<Vec2> clip) {
        List<List<Vec2>> next = new ArrayList<>();
        for (List<Vec2> piece : pieces) {
            next.addAll(PolyOps.subtractConvex(piece, clip));
        }
        return next;
    }

    /** 该边的向外单位法线。 */
    public static Vec2 outwardOf(int side) {
        return switch (side) {
            case SIDE_NEG_X -> new Vec2(-1, 0);
            case SIDE_POS_X -> new Vec2(1, 0);
            case SIDE_NEG_Z -> new Vec2(0, -1);
            default -> new Vec2(0, 1);
        };
    }

    /**
     * 哪些格边要出斜坡。
     * <p>
     * 规则（用户口径）：该边<b>垂直于轨道方向</b>（外法线与轨道方向夹角 80°..100°，
     * 等价于「边平行于铁轨方向」）时才出坡；边平行于铁轨法线（即 ⟂ 铁轨）的边不出坡。
     * <p>
     * 另外：<b>邻居是同类平交路口时不出坡</b>（该侧只能平接），否则一整片道口会每隔一格冒一条坡。
     */
    public static boolean[] rampSides(CellSnapshot snap, CrossingSettings cfg) {
        boolean[] sides = new boolean[4];
        if (cfg == null) return sides;
        double dx = dirX(snap);
        double dz = dirZ(snap);
        for (int side = 0; side < 4; side++) {
            boolean neighbour = cfg.neighbourCrossing != null && cfg.neighbourCrossing[side];
            if (neighbour) continue;
            Vec2 out = outwardOf(side);
            double dot = Math.abs(out.x() * dx + out.z() * dz);
            if (dot < RAMP_NORMAL_DOT_LIMIT) {
                sides[side] = true;
            }
        }
        return sides;
    }

    /** 斜坡水平长度：配置里的 {@code bevelWidth} 当「坡长」用，钳到 0.25..1。 */
    public static double rampRun(CrossingSettings cfg) {
        double run = cfg == null ? MAX_RAMP_RUN : cfg.bevelWidth;
        return Math.max(MIN_RAMP_RUN, Math.min(MAX_RAMP_RUN, run));
    }

    /** 按 {@link #rampSides} 生成斜坡描述（不含几何）。 */
    public static List<Ramp> ramps(CellSnapshot snap, CrossingSettings cfg) {
        List<Ramp> list = new ArrayList<>(2);
        boolean[] sides = rampSides(snap, cfg);
        double run = rampRun(cfg);
        for (int side = 0; side < 4; side++) {
            if (sides[side]) {
                list.add(new Ramp(side, outwardOf(side), run));
            }
        }
        return list;
    }
}
