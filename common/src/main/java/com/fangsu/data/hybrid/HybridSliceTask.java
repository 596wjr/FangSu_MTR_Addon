package com.fangsu.data.hybrid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 混合构建器的切片任务数据类。
 * <p>
 * 参照 ANTE 复合创建器（CompoundCreator.SliceTask）设计，但针对方速混合构建器做了裁剪：
 * <ul>
 *   <li>切片 = 轨道横截面：矩阵宽（列）方向垂直于轨道（水平法向），行方向 = 高度（向上）；
 *       沿轨道按「间隔」重复放置完整矩阵（间距 = 间隔+1 格，0 = 每格无缝铺满）</li>
 *   <li>废弃 length/increment/useYaw/usePitch/useRoll 与 step（不做矩阵旋转，改为逐列
 *       「宽度为 1 的桥梁构建」；step 仅保留 NBT 兼容）</li>
 * </ul>
 */
public class HybridSliceTask {

    public static final String TAG_TYPE = "type";
    public static final String TAG_ORDER = "order";
    public static final String TAG_NAME = "name";
    public static final String TAG_WIDTH = "width";
    public static final String TAG_HEIGHT = "height";
    public static final String TAG_START = "start";
    public static final String TAG_STEP = "step";
    public static final String TAG_INTERVAL = "interval";
    public static final String TAG_LUMPS = "lumps";
    public static final String TYPE = "Slice";

    public int order;
    public String name;
    public int width;
    public int height;
    public double start;
    /** 已废弃：旧版「切片间距」字段，仅保留 NBT 兼容读写，构建不再使用 */
    public double step;
    /** 间隔（默认 0）：每隔多少格放置一次切片（间距 = 间隔+1 格，0 = 每格无缝铺满）；
     *  null = 只建一块 */
    public Double interval;
    public List<HybridCreatorLump> lumps;

    public HybridSliceTask() {
        this(0, TYPE);
        width = 11;
        height = 11;
        start = 0;
        step = 1.0;
        interval = 0.0;
        lumps = new ArrayList<>();
        for (int i = 0; i < width * height; i++) {
            lumps.add(new HybridCreatorLump(null, true));
        }
    }

    public HybridSliceTask(int order, String name) {
        this.order = order;
        this.name = name;
    }

    public HybridSliceTask(HybridSliceTask other) {
        this(other.order, other.name);
        width = other.width;
        height = other.height;
        start = other.start;
        step = other.step;
        interval = other.interval;
        lumps = HybridCreatorLump.copyFrom(other.lumps);
    }

    public HybridSliceTask(CompoundTag compoundTag) {
        order = compoundTag.getInt(TAG_ORDER);
        name = compoundTag.getString(TAG_NAME);
        width = compoundTag.getInt(TAG_WIDTH);
        height = compoundTag.getInt(TAG_HEIGHT);
        start = compoundTag.getDouble(TAG_START);
        step = compoundTag.contains(TAG_STEP) ? compoundTag.getDouble(TAG_STEP) : 1.0;
        // 旧版本任务 NBT 无 interval 键（当时默认 null=只建一块）；读旧数据时按 0.0（无缝连续）
        // 兼容处理，避免「只在起始位置建一个切片」；只想建一块请在配置屏清空间隔
        interval = compoundTag.contains(TAG_INTERVAL) ? compoundTag.getDouble(TAG_INTERVAL) : 0.0;
        lumps = HybridCreatorLump.fromByteArray(compoundTag.getByteArray(TAG_LUMPS));
        // 防御：数据损坏或手工改 NBT 导致尺寸非法/格数不匹配时归一，保证构建时 get 不越界
        if (width < 1) width = 1;
        if (height < 1) height = 1;
        if (step <= 0) step = 1.0;
        if (lumps.size() != width * height) {
            lumps = new ArrayList<>();
            for (int i = 0; i < width * height; i++) {
                lumps.add(new HybridCreatorLump(null, true));
            }
        }
    }

    /**
     * 调整矩阵尺寸并保持中心对齐搬运原有方块（照 ANTE CompoundCreator.setWidthAndHeight）。
     * 仅支持奇数尺寸，否则返回 false。
     */
    public boolean setWidthAndHeight(int width, int height) {
        if (width < 1 || height < 1) return false;
        if (width % 2 != 1 || height % 2 != 1) return false;
        if (width == this.width && height == this.height) return false;

        List<HybridCreatorLump> newLumps = new ArrayList<>();
        for (int i = 0; i < width * height; i++) newLumps.add(new HybridCreatorLump(null, true));
        int thiMidX = width / 2;
        int thiMidY = height / 2;
        int oldMidX = this.width / 2;
        int oldMidY = this.height / 2;

        for (int i = -thiMidY; i <= thiMidY; i++) {
            int thiy = thiMidY + i;
            int oldy = oldMidY + i;
            if (oldy < 0 || oldy >= this.height) continue;
            for (int j = -thiMidX; j <= thiMidX; j++) {
                int thix = thiMidX + j;
                int oldx = oldMidX + j;
                if (oldx < 0 || oldx >= this.width) continue;
                newLumps.set(thiy * width + thix, this.lumps.get(oldy * this.width + oldx));
            }
        }
        this.width = width;
        this.height = height;
        this.lumps = newLumps;
        return true;
    }

    public CompoundTag toCompoundTag() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString(TAG_TYPE, TYPE);
        compoundTag.putInt(TAG_ORDER, order);
        compoundTag.putString(TAG_NAME, name);
        compoundTag.putInt(TAG_WIDTH, width);
        compoundTag.putInt(TAG_HEIGHT, height);
        compoundTag.putDouble(TAG_START, start);
        compoundTag.putDouble(TAG_STEP, step);
        if (interval != null) {
            compoundTag.putDouble(TAG_INTERVAL, interval);
        }
        compoundTag.putByteArray(TAG_LUMPS, HybridCreatorLump.toByteArray(lumps));
        return compoundTag;
    }

    /**
     * 矩阵中的一个格子：方块状态 + 是否替换模式（照 ANTE CompoundCreator.Lump）。
     */
    public static class HybridCreatorLump {
        public BlockState blockState;
        public boolean replacement;

        public HybridCreatorLump(HybridCreatorLump other) {
            this(other.blockState, other.replacement);
        }

        public HybridCreatorLump(BlockState blockState, boolean replacement) {
            this.blockState = blockState;
            this.replacement = replacement;
        }

        public static byte[] toByteArray(List<HybridCreatorLump> lumps) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            try {
                dos.writeInt(lumps.size());
                for (HybridCreatorLump lump : lumps) {
                    boolean hasBlockState = lump.blockState != null;
                    dos.writeBoolean(hasBlockState);
                    if (hasBlockState) {
                        // 1.18.2~1.20.1 都是 Block.getId（javap 实证），无需 #if
                        dos.writeInt(Block.getId(lump.blockState));
                    }
                    dos.writeBoolean(lump.replacement);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return bos.toByteArray();
        }

        public static List<HybridCreatorLump> fromByteArray(byte[] bytes) {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            DataInputStream dis = new DataInputStream(bis);
            List<HybridCreatorLump> lumps = new ArrayList<>();
            try {
                int size = dis.readInt();
                for (int i = 0; i < size; i++) {
                    boolean hasBlockState = dis.readBoolean();
                    BlockState blockState = null;
                    if (hasBlockState) {
                        // 读取：1.18.2~1.20.4 均有 Block.stateById（与写方向 Block.getId 对称），无需 #if
                        blockState = Block.stateById(dis.readInt());
                    }
                    boolean replacement = dis.readBoolean();
                    lumps.add(new HybridCreatorLump(blockState, replacement));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return lumps;
        }

        public static List<HybridCreatorLump> copyFrom(List<HybridCreatorLump> lumps) {
            List<HybridCreatorLump> result = new ArrayList<>();
            for (HybridCreatorLump lump : lumps) {
                result.add(new HybridCreatorLump(lump));
            }
            return result;
        }
    }
}
