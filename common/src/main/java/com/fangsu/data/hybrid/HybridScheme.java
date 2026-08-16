package com.fangsu.data.hybrid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 混合方案：一组带权重的方块（含完整 blockstate 属性），供画布「方案引用」格按权重随机抽选。
 * <p>
 * 方案存储在任务级别（{@link HybridSliceTask#schemes}），随任务 NBT/JSON 复制、导出、导入；
 * 也可通过 {@link HybridCreatorJsonIO#schemeToJson}/{@link HybridCreatorJsonIO#parseScheme}
 * 单独导出/导入（跨任务复用）。
 * <p>
 * 权重 ≥ 0，0 = 不参与抽选（条目保留、可编辑）；方块未注册（跨版本/mod 缺失）时
 * blockState 为 null，条目保留但抽选与预览跳过，UI 显示「未知方块」。
 */
public class HybridScheme {

    public static final String TAG_NAME = "name";
    public static final String TAG_ENTRIES = "entries";
    public static final String TAG_BLOCK = "block";
    public static final String TAG_PROPS = "props";
    public static final String TAG_WEIGHT = "weight";

    public String name;
    public final List<SchemeEntry> entries = new ArrayList<>();

    public HybridScheme() {
    }

    public HybridScheme(String name) {
        this.name = name;
    }

    /** 拷贝构造器（任务复制用）：BlockState 不可变可共享，条目对象新建 */
    public HybridScheme(HybridScheme other) {
        this(other.name);
        for (SchemeEntry entry : other.entries) {
            final SchemeEntry copy = new SchemeEntry();
            copy.blockState = entry.blockState;
            copy.weight = entry.weight;
            entries.add(copy);
        }
    }

    public CompoundTag toCompoundTag() {
        final CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString(TAG_NAME, name == null ? "" : name);
        final ListTag entriesTag = new ListTag();
        for (SchemeEntry entry : entries) {
            final CompoundTag entryTag = new CompoundTag();
            if (entry.blockState != null) {
                entryTag.putString(TAG_BLOCK, HybridSliceTask.getBlockName(entry.blockState.getBlock()));
                entryTag.putString(TAG_PROPS, HybridSliceTask.HybridCreatorLump.serializeProps(entry.blockState));
            } else {
                entryTag.putString(TAG_BLOCK, "");
                entryTag.putString(TAG_PROPS, "");
            }
            entryTag.putInt(TAG_WEIGHT, Math.max(0, entry.weight));
            entriesTag.add(entryTag);
        }
        compoundTag.put(TAG_ENTRIES, entriesTag);
        return compoundTag;
    }

    /**
     * 从 NBT 读入方案。方块名/属性串可来自手改的 JSON，非法时置空该条目（保留、可编辑），
     * 不向上抛——服务端 onConnect 里任务构造失败会炸掉整个构建交互。
     */
    public static HybridScheme fromCompoundTag(CompoundTag compoundTag) {
        final HybridScheme scheme = new HybridScheme(compoundTag.getString(TAG_NAME));
        for (Tag tag : compoundTag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND)) {
            final CompoundTag entryTag = (CompoundTag) tag;
            final SchemeEntry entry = new SchemeEntry();
            try {
                entry.blockState = HybridSliceTask.HybridCreatorLump.parseState(
                        entryTag.getString(TAG_BLOCK), entryTag.getString(TAG_PROPS));
            } catch (RuntimeException e) {
                // 方块名非法（空串/畸形 ResourceLocation）→ 置空，条目保留，不崩任务
                entry.blockState = null;
            }
            entry.weight = Math.max(0, entryTag.getInt(TAG_WEIGHT));
            scheme.entries.add(entry);
        }
        return scheme;
    }

    /** 画布预览/卡片图标：权重最高的有效条目状态；空/全无效返回 null */
    public BlockState representativeState() {
        BlockState best = null;
        int bestWeight = -1;
        for (SchemeEntry entry : entries) {
            if (entry.blockState != null && entry.weight > bestWeight) {
                best = entry.blockState;
                bestWeight = entry.weight;
            }
        }
        return best;
    }

    /** 抽选用总有效权重（blockState 有效且 weight > 0 之和）；long 防溢出 */
    public long totalWeight() {
        long total = 0;
        for (SchemeEntry entry : entries) {
            if (entry.blockState != null && entry.weight > 0) total += entry.weight;
        }
        return total;
    }

    /** 方案中的一个方块条目：方块状态 + 权重（0 = 不参与抽选） */
    public static class SchemeEntry {
        /** null = 未注册/损坏（跨版本或 mod 缺失），抽选与预览跳过 */
        public BlockState blockState;
        public int weight;
    }
}
