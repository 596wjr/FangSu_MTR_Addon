package com.fangsu.levelcrossing;

import com.fangsu.Main;
import com.fangsu.customItem.NteRailManager.NteRail;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轨道几何参数表：按轨道 id 查轨距、槽宽、轨顶高度等。
 * <p>
 * 数据来源有两处，优先级从高到低：
 * <ol>
 *   <li>{@code fangsu:rail_gauge.json} 的 "rails" 表（方速自有数据）</li>
 *   <li>NTE / MTR 自定义轨道 JSON 里顺带写的同名字段（{@link #overrideFromNteRail}）</li>
 *   <li>同一文件的 "defaults"</li>
 * </ol>
 */
public final class LevelCrossingGaugeTable {

    private static final ResourceLocation GAUGE_FILE = new ResourceLocation("fangsu", "rail_gauge.json");

    /** 标准轨距（毫米）。轨道 JSON 未声明 {@code gaugeMm} 时按它换算轨距。 */
    public static final int DEFAULT_GAUGE_MM = 1435;

    /** 换算出的轨距（格）合理区间：超出视为写坏，回落标准轨距而非算出荒唐几何。 */
    private static final double MIN_GAUGE_BLOCKS = 0.5D;
    private static final double MAX_GAUGE_BLOCKS = 3.0D;

    private static volatile LevelCrossingGaugeTable instance;

    /** 一条轨道的横向几何；{@code railTopY < 0} 表示由渲染时按轨道模型自动推断。 */
    public record GaugeInfo(double gauge, double grooveOuter, double grooveInner,
                            double grooveDepth, double railTopY,
                            double lateralScale, double lateralOffset,
                            double grooveCenter) {

        /** 便捷构造器：旧调用点（7 参）保持可用，{@code grooveCenter = 0} 表示「槽居中于 inner..outer」。 */
        public GaugeInfo(double gauge, double grooveOuter, double grooveInner,
                         double grooveDepth, double railTopY,
                         double lateralScale, double lateralOffset) {
            this(gauge, grooveOuter, grooveInner, grooveDepth, railTopY, lateralScale, lateralOffset, 0.0D);
        }

        /** 便捷构造器：旧调用点（5 参）保持可用，横向校准取缺省 1.0 / 0.0。 */
        public GaugeInfo(double gauge, double grooveOuter, double grooveInner,
                         double grooveDepth, double railTopY) {
            this(gauge, grooveOuter, grooveInner, grooveDepth, railTopY, 1.0D, 0.0D);
        }
    }

    /**
     * 兜底默认值（rail_gauge.json 读不到时用）：<b>内板 + 轮缘槽 + 钢板盖住原版轨头</b>（第 51 轮用户口径）。
     * <p>
     * 内板 {@code [0.6675, 0.6725]}（厚 0.005，顶面与钢板同高 = railTopY + bias），
     * 轮缘槽 {@code [0.6725, 0.7175]}（宽 0.045，落在原版轨头之外，原版轨道不会从槽里穿出），
     * 钢板 {@code [0.7175, 0.785883]}（== 原版轨头顶面宽 0.068383，抬高 bias 盖住它）。
     * 横带 = {@code [0.6675, 0.785883]}，槽中心 {@code grooveCenter = 0.695}。
     */
    private GaugeInfo defaults = new GaugeInfo(1.435, 0.785883, 0.6675, 0.09375, 0.26428,
            1.0D, 0.0D, 0.695D);
    private final Map<String, GaugeInfo> jsonRails = new ConcurrentHashMap<>();
    private final Map<String, GaugeInfo> nteOverrides = new ConcurrentHashMap<>();
    /** 轨道 JSON 里声明的毫米轨距；只改横向位置/宽度，不动槽宽与轨顶高。 */
    private final Map<String, Integer> gaugeMmOverrides = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    private volatile boolean loaded = false;

    private LevelCrossingGaugeTable() {
    }

    public static LevelCrossingGaugeTable getInstance() {
        LevelCrossingGaugeTable local = instance;
        if (local == null) {
            synchronized (LevelCrossingGaugeTable.class) {
                local = instance;
                if (local == null) {
                    local = new LevelCrossingGaugeTable();
                    instance = local;
                }
            }
        }
        return local;
    }

    public GaugeInfo defaults() {
        ensureLoaded();
        return defaults;
    }

    /** 按轨道 id 查几何参数；查不到时回落 defaults，并且同一条轨道只警告一次。 */
    public GaugeInfo lookup(String railId) {
        GaugeInfo info = lookupOrNull(railId);
        if (info != null) {
            return info;
        }
        if (railId != null && !railId.isEmpty() && warned.add(railId)) {
            Main.LOGGER.warn("[FangSu] Level crossing: no gauge entry for rail '{}', falling back to defaults", railId);
        }
        return defaults;
    }

    /**
     * 按轨道 id 查几何参数，<b>查不到时返回 null 且不告警</b>。
     * 供需要区分「表里有这条轨道」与「回落到 defaults」的调用方使用（如道岔参数解析）。
     * <p>
     * 若该轨道额外声明了毫米轨距（{@link #overrideGaugeMm}），返回的副本只替换 {@code gauge}，
     * 槽宽、槽深、轨顶高等断面参数保持原值。
     */
    @Nullable
    public GaugeInfo lookupOrNull(String railId) {
        ensureLoaded();
        if (railId == null || railId.isEmpty()) {
            return null;
        }
        GaugeInfo info = jsonRails.get(railId);
        if (info == null) {
            info = nteOverrides.get(railId);
        }
        if (info == null) {
            return null;
        }
        Integer gaugeMm = gaugeMmOverrides.get(railId);
        if (gaugeMm == null) {
            return info;
        }
        return new GaugeInfo(gaugeMmToBlocks(gaugeMm), info.grooveOuter(), info.grooveInner(),
                info.grooveDepth(), info.railTopY(), info.lateralScale(), info.lateralOffset(),
                info.grooveCenter());
    }

    /**
     * 毫米轨距 → 格 的<b>唯一换算入口</b>：{@code gaugeBlocks = gaugeMm / 1000.0}。
     * 非法输入（null、非正数、换算后超出 [0.5, 3] 格）一律回落标准轨距 {@value #DEFAULT_GAUGE_MM} mm。
     */
    public static double gaugeMmToBlocks(@Nullable Integer gaugeMm) {
        if (gaugeMm == null || gaugeMm <= 0) {
            return DEFAULT_GAUGE_MM / 1000.0D;
        }
        double blocks = gaugeMm / 1000.0D;
        if (blocks < MIN_GAUGE_BLOCKS || blocks > MAX_GAUGE_BLOCKS) {
            return DEFAULT_GAUGE_MM / 1000.0D;
        }
        return blocks;
    }

    /**
     * 登记一条轨道的毫米轨距，只影响横向位置/宽度计算（平交路口 corridors 的半宽、道岔轨距）。
     * 优先级与 {@link #overrideFromNteRail} 一致：方速自有 {@code rail_gauge.json} 已有条目时不覆盖。
     */
    public static void overrideGaugeMm(String railId, @Nullable Integer gaugeMm) {
        if (railId == null || railId.isEmpty() || gaugeMm == null || gaugeMm <= 0) {
            return;
        }
        LevelCrossingGaugeTable table = getInstance();
        table.ensureLoaded();
        if (table.jsonRails.containsKey(railId)) {
            return;
        }
        table.gaugeMmOverrides.put(railId, gaugeMm);
    }

    /**
     * 把 NTE/MTR 自定义轨道里顺带声明的几何参数并入表。
     * 方速自有 JSON 优先，已有条目时不覆盖。
     */
    public static void overrideFromNteRail(NteRail rail) {
        if (rail == null || rail.id() == null || rail.id().isEmpty()) {
            return;
        }
        LevelCrossingGaugeTable table = getInstance();
        table.ensureLoaded();
        if (table.jsonRails.containsKey(rail.id())) {
            return;
        }
        // 毫米轨距单独记账，避免把它混进槽宽/轨顶高这类断面参数
        overrideGaugeMm(rail.id(), rail.gaugeMm());
        GaugeInfo base = table.defaults;
        table.nteOverrides.put(rail.id(), new GaugeInfo(
                pick(rail.gauge(), base.gauge()),
                pick(rail.grooveOuter(), base.grooveOuter()),
                pick(rail.grooveInner(), base.grooveInner()),
                pick(rail.grooveDepth(), base.grooveDepth()),
                pick(rail.railTopY(), base.railTopY())
        ));
    }

    /** 资源重载时清空缓存，下一次查询重新读 JSON。 */
    public void reload() {
        loaded = false;
        jsonRails.clear();
        nteOverrides.clear();
        gaugeMmOverrides.clear();
        warned.clear();
        defaults = new GaugeInfo(1.435, 0.785883, 0.6675, 0.09375, 0.26428, 1.0D, 0.0D, 0.695D);
    }

    private synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        // 资源系统还没就绪时不置位，等 NteRailManager 重载或首次查询时再试
        if (!ResourceUtil.isInitialized()) {
            return;
        }
        loaded = true;

        JsonElement rootElement = ResourceUtil.loadAsJSON(GAUGE_FILE);
        if (rootElement == null || !rootElement.isJsonObject()) {
            return;
        }
        JsonObject root = rootElement.getAsJsonObject();

        GaugeInfo fallback = defaults;
        if (root.has("defaults") && root.get("defaults").isJsonObject()) {
            defaults = readGauge(root.getAsJsonObject("defaults"), fallback);
        }
        GaugeInfo base = defaults;

        if (root.has("rails") && root.get("rails").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("rails").entrySet()) {
                JsonElement value = entry.getValue();
                if (value == null || !value.isJsonObject()) {
                    continue;
                }
                // 缺字段逐个回落到 defaults，而不是整条丢弃
                jsonRails.put(entry.getKey(), readGauge(value.getAsJsonObject(), base));
            }
        }
    }

    private static GaugeInfo readGauge(JsonObject json, GaugeInfo fallback) {
        return new GaugeInfo(
                readDouble(json, "gauge", fallback.gauge()),
                readDouble(json, "grooveOuter", fallback.grooveOuter()),
                readDouble(json, "grooveInner", fallback.grooveInner()),
                readDouble(json, "grooveDepth", fallback.grooveDepth()),
                readDouble(json, "railTopY", fallback.railTopY()),
                clampLateralScale(readDouble(json, "lateralScale", fallback.lateralScale())),
                clampLateralOffset(readDouble(json, "lateralOffset", fallback.lateralOffset())),
                readDouble(json, "grooveCenter", fallback.grooveCenter())
        );
    }

    /** 横向缩放钳到 [0.5, 2]：再极端就没有几何意义，且会让 UV 归一化退化。 */
    public static double clampLateralScale(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 1.0D;
        return Math.max(0.5D, Math.min(2.0D, value));
    }

    /** 横向偏移钳到 [-0.5, 0.5]（格）：超过半格就跑到相邻钢轨上去了。 */
    public static double clampLateralOffset(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0D;
        return Math.max(-0.5D, Math.min(0.5D, value));
    }

    private static double readDouble(JsonObject json, String key, double fallback) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return element.getAsDouble();
    }

    private static double pick(Double value, double fallback) {
        return value != null ? value : fallback;
    }
}
