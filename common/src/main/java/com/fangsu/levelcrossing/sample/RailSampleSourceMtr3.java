package com.fangsu.levelcrossing.sample;

import com.fangsu.Main;
import com.fangsu.customItem.NteRailManager;
import com.fangsu.customItem.NteRailManager.NteRail;
import com.fangsu.mappings.rail.RailPoseExtra;
import com.fangsu.util.NodeConnector;
import mtr.client.ClientData;
import mtr.data.Rail;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MTR3 版轨道采样源：同步遍历 {@code mtr.client.ClientData.RAILS}。
 * <p>
 * <b>为什么必须 synchronized</b>：{@code ClientData.RAILS} 是网络线程写入的普通 HashMap
 * （{@code ClientData} 收到轨道包时整表替换），主线程裸读会撞上
 * {@code ConcurrentModificationException}。写法照抄 ANTE 的
 * {@code RailDistanceRenderer.render}：整段遍历包在 {@code synchronized (ClientData.RAILS)} 里。
 * <p>
 * <b>为什么先做折线预筛</b>：一个跑久了的存档有几万条轨道，而每个平交路口格子只关心自己前后两格。
 * 若对每条轨道都按 0.25 格精采样，单次重算就是几十万次 {@code getPosition}（内部全是三角函数）。
 * 所以这里先用 8 段折线做点到折线的距离粗筛，把候选压到个位数，精采样交给
 * {@link LevelCrossingSampler}。
 */
public final class RailSampleSourceMtr3 implements RailSampleSource {

    private static final RailSampleSourceMtr3 INSTANCE = new RailSampleSourceMtr3();

    /** 粗筛折线段数。8 段对 MTR 的最大曲率（半径 2 格）已经足够贴近真实轨迹。 */
    private static final int PREFILTER_SEGMENTS = 8;

    /** 折线近似带来的额外宽容（格）：半径 2 格的圆在 8 段折线下的矢高约 0.1 格，取 0.5 格留足余量。 */
    private static final double PREFILTER_SLACK = 0.5D;

    /** 认不出具体轨道型号时使用的 id，让 {@code LevelCrossingGaugeTable} 回落到 defaults。 */
    private static final String DEFAULT_RAIL_ID = "mtr:default";

    /**
     * ANTE（mtrsteamloco）给 {@code mtr.data.Rail} 挂的 mixin 接口，二进制名随平台带前缀，
     * 且 ANTE 是软依赖，因此只能反射查找。
     */
    private static final String[] ANTE_RAIL_EXTRA_SUPPLIER = {
            "cn.zbx1425.mtrsteamloco.data.RailExtraSupplier",
            "fabric.cn.zbx1425.mtrsteamloco.data.RailExtraSupplier",
            "forge.cn.zbx1425.mtrsteamloco.data.RailExtraSupplier"
    };

    /** 已解析到的 {@code getModelKey} 方法；{@code null} 表示 NTE 不在或接口不匹配。 */
    private static volatile Method modelKeyMethod;
    /** 是否已经尝试过解析（区分"还没找"和"找过但没找到"）。 */
    private static volatile boolean modelKeyResolved = false;

    public static RailSampleSourceMtr3 getInstance() {
        return INSTANCE;
    }

    private RailSampleSourceMtr3() {
    }

    @Override
    public List<RailPath> nearby(Level level, double centerX, double centerZ, double radius) {
        if (!(radius > 0)) {
            return List.of();
        }

        // 端点对 -> 保留的那条轨道。MTR 会把同一对节点在 RAILS 里存两份（两个方向各一条），
        // 不去重会让同一个道口画出两条完全重叠的轨道。
        Map<String, Rail> byPair = new HashMap<>();

        try {
            synchronized (ClientData.RAILS) {
                for (Map.Entry<BlockPos, Map<BlockPos, Rail>> outer : ClientData.RAILS.entrySet()) {
                    BlockPos pos1 = outer.getKey();
                    Map<BlockPos, Rail> inner = outer.getValue();
                    if (pos1 == null || inner == null) {
                        continue;
                    }
                    for (Map.Entry<BlockPos, Rail> entry : inner.entrySet()) {
                        BlockPos pos2 = entry.getKey();
                        Rail rail = entry.getValue();
                        if (pos2 == null || rail == null) {
                            continue;
                        }
                        if (!maybeNear(rail, centerX, centerZ, radius)) {
                            continue;
                        }
                        String key = pairKey(pos1, pos2);
                        Rail previous = byPair.get(key);
                        // 同一对端点出现两条时留更长的那条：MTR 的"反向副本"长度相同，无所谓；
                        // 若真的存在不同几何，长的更可能是用户实际布设的那条。
                        if (previous == null || safeLength(rail) > safeLength(previous)) {
                            byPair.put(key, rail);
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            // 极少见：数据包正在整表替换，锁内仍可能撞上迭代器失效。已收集到的先用着。
            Main.LOGGER.warn("[FangSu] Level crossing: rail table changed while scanning, using partial result", e);
        }

        List<RailPath> result = new ArrayList<>(byPair.size());
        for (Map.Entry<String, Rail> entry : byPair.entrySet()) {
            Rail rail = entry.getValue();
            BlockPos[] endpoints = splitPairKey(entry.getKey());
            if (endpoints == null) {
                continue;
            }
            result.add(new RailPath(resolveRailId(rail), new Mtr3Geometry(level, rail, endpoints[0], endpoints[1])));
        }
        return result;
    }

    // ==================== 粗筛 ====================

    /**
     * 轨道是否可能落在搜索圆内：用 8 段折线近似整条轨道，求圆心到折线的最短水平距离。
     */
    private static boolean maybeNear(Rail rail, double centerX, double centerZ, double radius) {
        double length = safeLength(rail);
        if (!(length > 1.0E-4D)) {
            return false;
        }
        double limit = radius + PREFILTER_SLACK;
        double limitSqr = limit * limit;

        double prevX = 0;
        double prevZ = 0;
        boolean hasPrev = false;
        for (int i = 0; i <= PREFILTER_SEGMENTS; i++) {
            Vec3 position = rail.getPosition(length * i / PREFILTER_SEGMENTS);
            if (position == null) {
                return false;
            }
            if (hasPrev && distanceToSegmentSqr(centerX, centerZ, prevX, prevZ, position.x, position.z) <= limitSqr) {
                return true;
            }
            prevX = position.x;
            prevZ = position.z;
            hasPrev = true;
        }
        return false;
    }

    private static double distanceToSegmentSqr(double px, double pz,
                                               double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lenSqr = dx * dx + dz * dz;
        double t = lenSqr < 1.0E-12D ? 0 : ((px - ax) * dx + (pz - az) * dz) / lenSqr;
        t = Math.max(0, Math.min(1, t));
        double qx = ax + dx * t;
        double qz = az + dz * t;
        double ddx = px - qx;
        double ddz = pz - qz;
        return ddx * ddx + ddz * ddz;
    }

    private static double safeLength(Rail rail) {
        try {
            double length = rail.getLength();
            return Double.isFinite(length) ? length : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ==================== 端点对键 ====================

    /** 无序端点对键：{@code (a,b)} 与 {@code (b,a)} 必须落到同一个键上。 */
    private static String pairKey(BlockPos pos1, BlockPos pos2) {
        String a = posKey(pos1);
        String b = posKey(pos2);
        return compare(pos1, pos2) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private static String posKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static int compare(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) return Integer.compare(a.getX(), b.getX());
        if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
        return Integer.compare(a.getZ(), b.getZ());
    }

    private static BlockPos[] splitPairKey(String key) {
        int split = key.indexOf('|');
        if (split <= 0) {
            return null;
        }
        BlockPos pos1 = parsePos(key.substring(0, split));
        BlockPos pos2 = parsePos(key.substring(split + 1));
        return pos1 == null || pos2 == null ? null : new BlockPos[]{pos1, pos2};
    }

    private static BlockPos parsePos(String text) {
        String[] parts = text.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== 轨道 id ====================

    /**
     * 解析用于查几何参数表的轨道 id。
     * <p>
     * MTR3 的 {@code Rail} 本身没有 id 字段，唯一能标识"用了哪个轨道模型"的是 ANTE 挂在它身上的
     * {@code getModelKey()}（自定义轨道才有值，原版轨道为空串）。拿到模型路径后再折回
     * {@code NteRailManager} 的 id —— 方速的轨道表是按 NteRail id 索引的。
     */
    private static String resolveRailId(Rail rail) {
        // 刻意不做按 Rail 的缓存：{@code ClientData.RAILS} 每次同步都整表替换，
        // 以 Rail 为键的 map 会把这些对象一直钉在堆里（跨存档不释放）。
        // 只有命中判定的少数几条轨道会走到这里，反射一次的开销可以忽略。
        return computeRailId(rail);
    }

    private static String computeRailId(Rail rail) {
        String modelKey = readModelKey(rail);
        if (modelKey == null || modelKey.isEmpty() || "null".equals(modelKey)) {
            return DEFAULT_RAIL_ID;
        }
        NteRailManager manager = NteRailManager.getInstance();
        if (manager.getRail(modelKey) != null) {
            return modelKey;
        }
        for (NteRail candidate : manager.getRails()) {
            if (modelKey.equals(candidate.model())) {
                return candidate.id();
            }
        }
        // 认不出 NteRail 时直接把模型路径当 id：几何参数表查不到会回落到 defaults 并只警告一次，
        // 而模型路径本身仍能让 railTopY 的"模型包围盒顶"分支拿到正确模型。
        return modelKey;
    }

    private static String readModelKey(Rail rail) {
        Method method = modelKeyMethod();
        if (method == null || !method.getDeclaringClass().isInstance(rail)) {
            return null;
        }
        try {
            Object value = method.invoke(rail);
            return value instanceof String text ? text : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Method modelKeyMethod() {
        if (!modelKeyResolved) {
            synchronized (RailSampleSourceMtr3.class) {
                if (!modelKeyResolved) {
                    modelKeyMethod = locateModelKeyMethod();
                    modelKeyResolved = true;
                }
            }
        }
        return modelKeyMethod;
    }

    private static Method locateModelKeyMethod() {
        ClassLoader loader = Rail.class.getClassLoader();
        for (String className : ANTE_RAIL_EXTRA_SUPPLIER) {
            try {
                Class<?> type = Class.forName(className, false, loader);
                return type.getMethod("getModelKey");
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
                // ANTE 没装就是这个分支，属于正常情况，不写日志免得刷屏
            }
        }
        return null;
    }

    // ==================== 几何句柄 ====================

    /**
     * 几何句柄。
     * <p>
     * {@code level} 只用来在 {@link #tiltDegrees()} 里读节点姿态（{@code NodeConnector.readRailPose} 需要世界对象），
     * 生命周期与本对象一致：它只在一次 {@link #nearby} 的返回值里存活，不会跨 tick 持有世界引用。
     */
    private record Mtr3Geometry(Level level, Rail rail, BlockPos pos1, BlockPos pos2) implements RailGeometry {

        @Override
        public double length() {
            return safeLength(rail);
        }

        @Override
        public Vec3 position(double t) {
            double length = length();
            double clamped = Math.max(0, Math.min(length, t));
            Vec3 value = rail.getPosition(clamped);
            return value == null ? Vec3.ZERO : value;
        }

        /**
         * 超高（度）＝两端节点滚转的平均值。
         * <p>
         * <b>单位与符号来源</b>：{@code RailPoseExtra.roll1Degrees / roll2Degrees} 本身就是"度"，
         * 符号沿用 {@code NodeConnector.readRailPose} 的端点语义（轨道帧的滚转，右侧抬高为正）——
         * 与 P2 采样器里原来的算法逐值等价，只是把取数位置从采样器挪到了这里。
         */
        @Override
        public double tiltDegrees() {
            try {
                RailPoseExtra pose = NodeConnector.readRailPose(level, pos1, pos2);
                if (pose == null) {
                    return 0;
                }
                double degrees = (pose.roll1Degrees + pose.roll2Degrees) * 0.5D;
                return Double.isFinite(degrees) ? degrees : 0;
            } catch (RuntimeException e) {
                return 0;
            }
        }
    }
}
