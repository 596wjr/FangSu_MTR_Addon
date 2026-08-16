package com.fangsu.data.hybrid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 混合构建器的切片任务数据类。
 * <p>
 * 参照 ANTE 复合创建器（CompoundCreator.SliceTask）设计，但针对方速混合构建器做了裁剪：
 * <ul>
 *   <li>切片 = 轨道横截面：矩阵宽（列）方向垂直于轨道（水平法向），行方向 = 高度（向上）；
 *       沿轨道按「间隔」重复放置完整矩阵（间距 = 间隔+1 格，0 = 每格无缝铺满）</li>
 *   <li>厚度 = 每组连续切片的数量：每组 N 个切片，每片有独立横截面矩阵（lumps 平铺为
 *       N 组 × 宽×高），画布可切换编辑；构建按 1,2,3,1,2,3… 循环。间隔 = 每组之间的
 *       空隙格数（留空或 0 = 无缝循环铺满）</li>
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
    /** 方块名表（StringTag 列表，顺序即索引；lumps 字节流里用索引引用，参照原版合成表的「名字→id」模式） */
    public static final String TAG_BLOCK_NAMES = "blockNames";
    /** 厚度（组数）：每组由 N 个连续切片组成，每片有独立横截面矩阵（lumps 平铺为 N 组 × 宽×高） */
    public static final String TAG_THICKNESS = "thickness";
    /** 厚度延伸方向：true = 向里（轨道 +t 方向，画布罗盘的 × 叉）、false = 向外（-t，· 点） */
    public static final String TAG_THICK_DIR = "thickDir";
    public static final String TYPE = "Slice";

    public int order;
    public String name;
    public int width;
    public int height;
    public double start;
    /** 已废弃：旧版「切片间距」字段，仅保留 NBT 兼容读写，构建不再使用 */
    public double step;
    /** 间隔（默认 0）：每组切片之间的空隙格数（0 = 无缝循环铺满，与留空等价）；
     *  null = 留空，按无缝处理 */
    public Double interval;
    /** 厚度（默认 1）：每组由 N 个连续切片组成，每片有独立横截面矩阵（画布可切换编辑） */
    public int thickness;
    /** 厚度延伸方向（默认向里 ×）：true = 向里（轨道 +t）、false = 向外（-t）；
     *  默认向里——start=0 时向外延伸首组会被轨道头夹断 */
    public boolean thickDirection;
    /** 所有组的矩阵格平铺列表：第 g 组第 (i,j) 格 = lumps.get(g*width*height + i*width + j) */
    public List<HybridCreatorLump> lumps;

    public HybridSliceTask() {
        this(0, TYPE);
        width = 11;
        height = 11;
        start = 0;
        step = 1.0;
        interval = 0.0;
        thickness = 1;
        thickDirection = true;
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
        thickness = other.thickness;
        thickDirection = other.thickDirection;
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
        // 兼容处理——与「间隔留空 = 无缝循环」的现行语义一致，无行为差异
        interval = compoundTag.contains(TAG_INTERVAL) ? compoundTag.getDouble(TAG_INTERVAL) : 0.0;
        // 厚度（组数）：旧数据无 thickness 键 → 1（单组，行为不变）；方向默认向里（厚度 1 时无效果）
        thickness = compoundTag.contains(TAG_THICKNESS) ? compoundTag.getInt(TAG_THICKNESS) : 1;
        thickDirection = compoundTag.contains(TAG_THICK_DIR) && compoundTag.getBoolean(TAG_THICK_DIR);
        // 方块名表（新格式，跨 MC 版本稳定）：lumps 字节流存表索引；旧数据无 blockNames 键
        // → 旧格式（运行时 Block.stateById，跨版本会漂移），仅兼容读取
        if (compoundTag.contains(TAG_BLOCK_NAMES)) {
            final ListTag namesTag = compoundTag.getList(TAG_BLOCK_NAMES, Tag.TAG_STRING);
            final List<String> names = new ArrayList<>();
            for (Tag tag : namesTag) names.add(tag.getAsString());
            lumps = HybridCreatorLump.fromByteArray(compoundTag.getByteArray(TAG_LUMPS), names);
        } else {
            lumps = HybridCreatorLump.fromByteArrayLegacy(compoundTag.getByteArray(TAG_LUMPS));
        }
        // 防御：数据损坏或手工改 NBT 导致尺寸/厚度非法、格数不匹配时归一，保证构建时 get 不越界
        if (width < 1) width = 1;
        if (height < 1) height = 1;
        if (step <= 0) step = 1.0;
        if (thickness < 1) thickness = 1;
        if (lumps.size() != thickness * width * height) {
            lumps = new ArrayList<>();
            for (int i = 0; i < thickness * width * height; i++) {
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
        for (int i = 0; i < thickness * width * height; i++) newLumps.add(new HybridCreatorLump(null, true));
        int thiMidX = width / 2;
        int thiMidY = height / 2;
        int oldMidX = this.width / 2;
        int oldMidY = this.height / 2;

        // 每组的矩阵独立中心对齐搬运（厚度片的横截面内容互不影响）
        for (int g = 0; g < thickness; g++) {
            final int newBase = g * width * height;
            final int oldBase = g * this.width * this.height;
            for (int i = -thiMidY; i <= thiMidY; i++) {
                int thiy = thiMidY + i;
                int oldy = oldMidY + i;
                if (oldy < 0 || oldy >= this.height) continue;
                for (int j = -thiMidX; j <= thiMidX; j++) {
                    int thix = thiMidX + j;
                    int oldx = oldMidX + j;
                    if (oldx < 0 || oldx >= this.width) continue;
                    newLumps.set(newBase + thiy * width + thix, this.lumps.get(oldBase + oldy * this.width + oldx));
                }
            }
        }
        this.width = width;
        this.height = height;
        this.lumps = newLumps;
        return true;
    }

    /**
     * 调整厚度（组数）：扩组补空矩阵、缩组截断。仅当新值 ≥1 且不同时生效（照 setWidthAndHeight）。
     */
    public boolean setThickness(int thickness) {
        if (thickness < 1 || thickness == this.thickness) return false;
        final int groupSize = width * height;
        final List<HybridCreatorLump> newLumps = new ArrayList<>();
        for (int g = 0; g < thickness; g++) {
            for (int i = 0; i < groupSize; i++) {
                if (g < this.thickness) {
                    newLumps.add(this.lumps.get(g * groupSize + i));
                } else {
                    newLumps.add(new HybridCreatorLump(null, true));
                }
            }
        }
        this.thickness = thickness;
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
        compoundTag.putInt(TAG_THICKNESS, thickness);
        compoundTag.putBoolean(TAG_THICK_DIR, thickDirection);
        // 先给每个唯一方块名分配索引（按出现顺序），lumps 字节流里用索引引用（参照原版
        // 合成表的「名字 → id」模式）：数据层是完整方块名（minecraft:stone），跨 MC 版本
        // 稳定，不依赖运行时注册表 id（Block.getId 会随版本/加载顺序漂移）
        final List<String> names = new ArrayList<>();
        for (HybridCreatorLump lump : lumps) {
            if (lump.blockState != null) {
                final String name = getBlockName(lump.blockState.getBlock());
                if (!names.contains(name)) names.add(name);
            }
        }
        final ListTag namesTag = new ListTag();
        for (String name : names) namesTag.add(StringTag.valueOf(name));
        compoundTag.put(TAG_BLOCK_NAMES, namesTag);
        compoundTag.putByteArray(TAG_LUMPS, HybridCreatorLump.toByteArray(lumps, names));
        // 混合方案：空则不写键（旧版导出的 JSON 形态保持最小差异）
        return compoundTag;
    }

    /** 方块 → 完整名字（如 minecraft:stone），跨 MC 版本稳定（不 import，用全限定名保证多版本可编译） */
    static String getBlockName(Block block) {
        //#if MC_VERSION >= 11903
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
        //#else
        //$$ return net.minecraft.core.Registry.BLOCK.getKey(block).toString();
        //#endif
    }

    /** 名字 → 方块；未注册（mod 缺失/名字变更）返回 null，调用方置空处理 */
    static Block getBlockByName(String name) {
        //#if MC_VERSION >= 11903
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new ResourceLocation(name));
        //#else
        //$$ return net.minecraft.core.Registry.BLOCK.get(new ResourceLocation(name));
        //#endif
    }

    /**
     * 矩阵中的一个格子：方块状态 + 是否替换模式 + 混合方案引用（照 ANTE CompoundCreator.Lump）。
     * <p>
     * schemeIndex >= 0 = 引用构建级（物品 NBT 顶层）{@code schemes} 列表的索引
     * （构建时按权重随机抽选方块）；-1 = 普通方块格（blockState 存自身方块，可为 null = 空）。
     */
    public static class HybridCreatorLump {
        public BlockState blockState;
        public boolean replacement;
        /** 混合方案引用索引（≥0 = 引用构建级 schemes 列表），-1 = 普通方块格 */
        public int schemeIndex = -1;

        public HybridCreatorLump(HybridCreatorLump other) {
            this(other.blockState, other.replacement, other.schemeIndex);
        }

        public HybridCreatorLump(BlockState blockState, boolean replacement) {
            this(blockState, replacement, -1);
        }

        public HybridCreatorLump(BlockState blockState, boolean replacement, int schemeIndex) {
            this.blockState = blockState;
            this.replacement = replacement;
            this.schemeIndex = schemeIndex;
        }

        /**
         * 序列化（新格式，配合 {@code names} 名字表）：每格 = type 字节 + 内容 + replacement。
         * <p>
         * type 字节兼容旧格式：旧版 writeBoolean(false/true) 写 0x00/0x01，
         * 新格式 0=空、1=普通方块（旧 true）、2=混合方案引用（后跟 writeInt 方案索引），
         * 旧数据 readByte 读出 0/1 语义完全等价，无需迁移。
         * 普通方块内容 = 名字表索引 + 属性串（如 "type=bottom,facing=north"，键按状态属性集合顺序）
         * 保留完整状态，朝向等属性不用在 UI 里手选也不会丢。
         */
        public static byte[] toByteArray(List<HybridCreatorLump> lumps, List<String> names) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            try {
                dos.writeInt(lumps.size());
                for (HybridCreatorLump lump : lumps) {
                    if (lump.schemeIndex >= 0) {
                        dos.writeByte(2);                // 混合方案引用（新）
                        dos.writeInt(lump.schemeIndex);
                    } else if (lump.blockState != null) {
                        dos.writeByte(1);                // 普通方块（旧 writeBoolean(true) 亦写 0x01）
                        // 先分配名字索引再引用（参照原版合成表模式），跨 MC 版本稳定
                        dos.writeInt(names.indexOf(HybridSliceTask.getBlockName(lump.blockState.getBlock())));
                        dos.writeUTF(serializeProps(lump.blockState));
                    } else {
                        dos.writeByte(0);                // 空（旧 writeBoolean(false) 亦写 0x00）
                    }
                    dos.writeBoolean(lump.replacement);
                }
            } catch (IOException e) {
                com.fangsu.Main.LOGGER.error("[HybridCreator] 写入 lumps 失败", e);
            }
            return bos.toByteArray();
        }

        /** 读取新格式：type 字节 + 名字表索引 + 属性串 → 完整方块状态；0x02 = 方案引用 */
        public static List<HybridCreatorLump> fromByteArray(byte[] bytes, List<String> names) {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            DataInputStream dis = new DataInputStream(bis);
            List<HybridCreatorLump> lumps = new ArrayList<>();
            try {
                int size = dis.readInt();
                for (int i = 0; i < size; i++) {
                    // 类型字节：0x00/0x01 与旧 writeBoolean(false/true) 逐字节等价，旧数据免迁移；
                    // 0x02 = 方案引用（新格式）
                    final byte type = dis.readByte();
                    BlockState blockState = null;
                    int schemeIndex = -1;
                    if (type == 1) {
                        final int index = dis.readInt();
                        if (index >= 0 && index < names.size()) {
                            blockState = parseState(names.get(index), dis.readUTF());
                        } else {
                            dis.readUTF();               // 对齐流，防御索引越界
                        }
                    } else if (type == 2) {
                        schemeIndex = dis.readInt();
                        if (schemeIndex < 0) schemeIndex = -1;  // 防御负数；≥ schemes.size() 的悬空引用由构建/UI 侧跳过
                    } else if (type != 0) {
                        // 未知类型（未来扩展/损坏）：后续字段布局未知，中止 → 上层 size 校验重建空矩阵
                        throw new IOException("未知的混合方块格类型: " + type);
                    }
                    boolean replacement = dis.readBoolean();
                    lumps.add(new HybridCreatorLump(blockState, replacement, schemeIndex));
                }
            } catch (IOException e) {
                com.fangsu.Main.LOGGER.error("[HybridCreator] 读取 lumps 失败", e);
            }
            return lumps;
        }

        /** 旧格式（运行时 Block.stateById，跨 MC 版本会漂移）：仅兼容读取旧任务数据 */
        public static List<HybridCreatorLump> fromByteArrayLegacy(byte[] bytes) {
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

        /** 完整状态 → 属性串（"key1=value1,key2=value2"，按状态属性集合顺序稳定） */
        static String serializeProps(BlockState state) {
            final StringBuilder sb = new StringBuilder();
            for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
                if (sb.length() > 0) sb.append(',');
                sb.append(entry.getKey().getName()).append('=').append(entry.getValue());
            }
            return sb.toString();
        }

        /** 名字 + 属性串 → 完整状态；方块未注册返回 null，跨版本缺失的属性跳过。
         *  注意：name 非法（空串/畸形 ResourceLocation）会抛异常，调用方（如解析手改 JSON）须自行捕获 */
        static BlockState parseState(String name, String props) {
            final Block block = HybridSliceTask.getBlockByName(name);
            if (block == null) return null;
            BlockState state = block.defaultBlockState();
            if (props.isEmpty()) return state;
            for (String pair : props.split(",")) {
                final int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                final Property<?> property = block.getStateDefinition().getProperty(pair.substring(0, eq));
                if (property == null) continue;
                state = applyProperty(state, property, pair.substring(eq + 1));
            }
            return state;
        }

        /**
         * 还原单个属性（照原版 NbtUtils.readProperty 的写法）：泛型方法 T extends Comparable<T>
         * 由编译器推断具体属性类型，无需 raw 强转——Property<Comparable> 的实例化会因裸
         * Comparable 不满足「T extends Comparable<T>」的范围而编译失败。
         */
        private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
            try {
                final T v = property.getValue(value).orElse(null);
                if (v == null) return state;
                return state.setValue(property, v);
            } catch (RuntimeException e) {
                // 属性值跨版本变更/损坏：保留默认值，不因一个属性崩掉整个任务
                return state;
            }
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
