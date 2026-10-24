package com.fangsu.levelcrossing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个平交路口方块的几何快照：方块实体算一次、存 NBT、再交给渲染器。
 * <p>
 * 坐标同样是方块局部坐标（X+ 东、Z+ 南、Y+ 上、原点 = 方块最小角，单位格）。
 */
public final class CellSnapshot {

    /** 结构版本；改动字段语义时 +1，旧存档快照会被当成空快照丢弃而不是误读。 */
    public static final int FORMAT_VERSION = 1;

    /** 最多容纳的轨道条数。 */
    public static final int MAX_TRACKS = 8;

    /** 主轨纵坡（沿轨方向俯仰，度）。 */
    public double pitchDeg;
    /** 主轨超高（横断面滚转，度）。 */
    public double rollDeg;
    /** 主轨处铺面顶面高度（方块局部）。 */
    public double deckTopY;
    /** 地面层顶面高度（方块局部）。 */
    public double groundTopY;
    /** 计算时的 level.getGameTime()，用于判断快照是否过期。 */
    public long computedAt;

    /**
     * 钢轨顶面偏置（格）：钢轨断面的 {@code y=0} 平面 = 原版轨顶 + 本值。
     * <p>
     * {@link TrackSlice#railTopY} 的语义<b>保持「原版轨顶」不变</b>（对齐日志要用它当基准），
     * 偏置单独记在这里，只由几何侧（{@code DeckSurface.railTopY}）加上去。
     */
    public double railTopBias;

    public final List<TrackSlice> tracks = new ArrayList<>();

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", FORMAT_VERSION);
        tag.putDouble("pitch_deg", pitchDeg);
        tag.putDouble("roll_deg", rollDeg);
        tag.putDouble("deck_top_y", deckTopY);
        tag.putDouble("ground_top_y", groundTopY);
        tag.putLong("computed_at", computedAt);
        tag.putDouble("rail_top_bias", railTopBias);

        ListTag trackList = new ListTag();
        int count = Math.min(tracks.size(), MAX_TRACKS);
        for (int i = 0; i < count; i++) {
            TrackSlice slice = tracks.get(i);
            if (slice != null) trackList.add(slice.toTag());
        }
        tag.put("tracks", trackList);
        return tag;
    }

    public static CellSnapshot fromTag(CompoundTag tag) {
        CellSnapshot snapshot = new CellSnapshot();
        if (tag.getInt("v") != FORMAT_VERSION) {
            // 版本不匹配：返回空快照，让上层当作"未计算"重新采样
            return snapshot;
        }
        snapshot.pitchDeg = tag.getDouble("pitch_deg");
        snapshot.rollDeg = tag.getDouble("roll_deg");
        snapshot.deckTopY = tag.getDouble("deck_top_y");
        snapshot.groundTopY = tag.getDouble("ground_top_y");
        snapshot.computedAt = tag.getLong("computed_at");
        // 旧存档没有这个键：按 0 处理（保持历史行为，不突然抬升）
        snapshot.railTopBias = tag.contains("rail_top_bias") ? tag.getDouble("rail_top_bias") : 0.0;

        ListTag trackList = tag.getList("tracks", Tag.TAG_COMPOUND);
        for (int i = 0; i < trackList.size() && snapshot.tracks.size() < MAX_TRACKS; i++) {
            CompoundTag sliceTag = trackList.getCompound(i);
            if (sliceTag != null) snapshot.tracks.add(TrackSlice.fromTag(sliceTag));
        }
        return snapshot;
    }
}
