package com.fangsu.levelcrossing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 平交路口单个方块里的一段轨道：从 MTR 轨道中心线截取的那一小截，以及它的横向几何。
 * <p>
 * 坐标系全部是<b>方块局部坐标</b>：X+ 向东、Z+ 向南、Y+ 向上，
 * 原点取本方块的最小角（底面西北角），单位为格。
 */
public final class TrackSlice {

    /** MTR 轨道 id（用于反查 {@link LevelCrossingGaugeTable}）。 */
    public String railId = "";
    /** 轨道前进方向，MTR 约定为 atan2(z, x) 的角度（度）。 */
    public double yawDeg;
    /** 轨距（格）。 */
    public double gauge;
    /** 槽外沿到中心线的横向距离（格，正数）。 */
    public double grooveOuter;
    /** 槽内沿到中心线的横向距离（格，正数）。 */
    public double grooveInner;
    /** 槽深（格）。 */
    public double grooveDepth;
    /**
     * 槽中心到轨道中心线的横向距离（格）；{@code <= 0} 表示「居中于 grooveInner..grooveOuter」。
     * <p>
     * 钢面取的是<b>整条钢轨可见轮廓</b>（rail.obj 最宽层 0.213173），而槽仍要<b>居中于轨头</b>
     * （轨头中心 0.7516915），所以槽位不能简单取内外沿中点。
     */
    public double grooveCenter;
    /** 轨顶在本方块坐标系里的高度（格，0 = 方块底面）。 */
    public double railTopY;
    /** 是否主轨（穿格最长的那条，决定铺面俯仰/超高等整体姿态）。 */
    public boolean primary;
    /** 横向缩放：几何里所有横坐标按 {@code offset + scale * v} 映射（缺省 1.0）。 */
    public double lateralScale = 1.0D;
    /** 横向偏移（格，正值 = 向轨道外侧）：缺省 0.0。 */
    public double lateralOffset = 0.0D;
    /**
     * 轨顶高度是否已经解析到「真实值」（NTE 模型包围盒 / 参数表 / 玩家覆盖）。
     * <p>
     * 采样跑在工作线程上时不能碰客户端资源，拿不到 NTE 模型包围盒就把这里置 false，
     * {@code railTopY} 只是默认值占位；主线程收尾时会解析并<b>重新采样</b>（最多 2 次）。
     * 旧存档没有这个键 → 按 true（已解析）处理，保持兼容。
     */
    public boolean railTopResolved = true;

    /**
     * <b>唯一的横向映射入口</b>：{@code v' = lateralOffset + lateralScale * v}。
     * 断面的横坐标、UV 归一化基准、走廊偏移都必须经过它（几何里只允许存在这一处公式）。
     * 纯数学、无依赖，所以纯数学类 {@code TrackStrips} 也能直接调用。
     */
    public double mapLateral(double v) {
        return lateralOffset + lateralScale * v;
    }

    /** 中心线采样点，方块局部 XZ；与 {@link #zs} 等长、按弧长升序。 */
    public double[] xs = new double[0];
    public double[] zs = new double[0];

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("rail_id", railId);
        tag.putDouble("yaw_deg", yawDeg);
        tag.putDouble("gauge", gauge);
        tag.putDouble("groove_outer", grooveOuter);
        tag.putDouble("groove_inner", grooveInner);
        tag.putDouble("groove_depth", grooveDepth);
        tag.putDouble("groove_center", grooveCenter);
        tag.putDouble("rail_top_y", railTopY);
        tag.putBoolean("primary", primary);
        tag.putDouble("lateral_scale", lateralScale);
        tag.putDouble("lateral_offset", lateralOffset);
        tag.putBoolean("rail_top_resolved", railTopResolved);
        tag.put("xs", toDoubleList(xs));
        tag.put("zs", toDoubleList(zs));
        return tag;
    }

    public static TrackSlice fromTag(CompoundTag tag) {
        TrackSlice slice = new TrackSlice();
        slice.railId = tag.getString("rail_id");
        slice.yawDeg = tag.getDouble("yaw_deg");
        slice.gauge = tag.getDouble("gauge");
        slice.grooveOuter = tag.getDouble("groove_outer");
        slice.grooveInner = tag.getDouble("groove_inner");
        slice.grooveDepth = tag.getDouble("groove_depth");
        // 旧存档没有这个键：按 0（居中于内外沿）处理，保持历史行为
        slice.grooveCenter = tag.contains("groove_center") ? tag.getDouble("groove_center") : 0.0D;
        slice.railTopY = tag.getDouble("rail_top_y");
        slice.primary = tag.getBoolean("primary");
        // 旧存档没有这两个键：缺省 1.0 / 0.0，行为与历史一致
        slice.lateralScale = tag.contains("lateral_scale") ? tag.getDouble("lateral_scale") : 1.0D;
        slice.lateralOffset = tag.contains("lateral_offset") ? tag.getDouble("lateral_offset") : 0.0D;
        // 旧存档没有这个键：按「已解析」处理，行为与历史一致
        slice.railTopResolved = !tag.contains("rail_top_resolved") || tag.getBoolean("rail_top_resolved");
        slice.xs = toDoubleArray(tag, "xs");
        slice.zs = toDoubleArray(tag, "zs");
        return slice;
    }

    private static ListTag toDoubleList(double[] values) {
        ListTag list = new ListTag();
        for (double value : values) {
            list.add(DoubleTag.valueOf(value));
        }
        return list;
    }

    private static double[] toDoubleArray(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_DOUBLE);
        double[] values = new double[list.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = list.getDouble(i);
        }
        return values;
    }
}
