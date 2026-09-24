package com.fangsu.data.hybrid;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * 混合构建器的「放置组合」任务数据类（与切片任务 {@link HybridSliceTask} 并列的新任务类型）。
 * <p>
 * 与切片（把方块矩阵当轨道横截面铺满）不同，放置组合沿轨道逐站放置一组**离散对象**，
 * 每个对象是：
 * <ul>
 *   <li>一个目标方块（方速可平移旋转方块 / MTR4 装饰物件的 Block，跨版本用完整方块名）；</li>
 *   <li>该方块在**轨道参考系**内的偏移 (dt=沿切向, dn=沿法向, du=沿 up) 与局部旋转；</li>
 *   <li>方块实体预设 NBT（translate/rotate/mainModel/subModels/extraConfigs 如吊板 length/选线路），
 *       捕获自世界中已摆好配置好的方块（见设计文档 §4）。</li>
 * </ul>
 * 构建时每站独立取局部 n/t/up 定向（曲线站自动跟随），竖直基向量由 {@link VerticalMode} 决定
 * （垂直轨道平面 vs 垂直地面），斜向/曲线空隙按「阶梯 + 内角补块」闭合（复用补角逻辑）。
 */
public class HybridPlacementTask {

    public static final String TAG_TYPE = "type";
    public static final String TAG_ORDER = "order";
    public static final String TAG_NAME = "name";
    public static final String TAG_START = "start";
    public static final String TAG_INTERVAL = "interval";
    public static final String TAG_THICKNESS = "thickness";
    public static final String TAG_THICK_DIR = "thickDir";
    /** 竖直基向量模式：TRACK(垂直轨道平面) / GROUND(垂直地面) */
    public static final String TAG_VERTICAL = "vertical";
    /** 是否开启斜向内角补块（阶梯空隙闭合，见设计 §3.5）；默认开 */
    public static final String TAG_PATCH_CORNER = "patchCorner";
    /** 对象列表 */
    public static final String TAG_OBJECTS = "objects";
    public static final String TYPE = "Placement";

    public int order;
    public String name;
    /** 轨道起点沿切向的偏移（首站位置） */
    public double start;
    /** 组间空隙格数（0/留空 = 无缝循环铺满，与切片同语义） */
    public Double interval;
    /** 厚度（组数），沿用切片语义；1 = 每 N 格放一次 */
    public int thickness;
    /** 厚度延伸方向：true = 向里(+t)、false = 向外(-t) */
    public boolean thickDirection;
    /** 竖直基向量模式 */
    public VerticalMode verticalMode = VerticalMode.GROUND;
    /** 斜向内角补块开关（默认开） */
    public boolean patchCorner = true;
    public final List<PlacementObject> objects = new ArrayList<>();

    public HybridPlacementTask() {
        this(0, TYPE);
        start = 0;
        interval = 0.0;
        thickness = 1;
        thickDirection = true;
    }

    public HybridPlacementTask(int order, String name) {
        this.order = order;
        this.name = name;
    }

    public HybridPlacementTask(HybridPlacementTask other) {
        this(other.order, other.name);
        start = other.start;
        interval = other.interval;
        thickness = other.thickness;
        thickDirection = other.thickDirection;
        verticalMode = other.verticalMode;
        patchCorner = other.patchCorner;
        for (PlacementObject obj : other.objects) objects.add(new PlacementObject(obj));
    }

    public HybridPlacementTask(CompoundTag compoundTag) {
        order = compoundTag.getInt(TAG_ORDER);
        name = compoundTag.getString(TAG_NAME);
        start = compoundTag.getDouble(TAG_START);
        interval = compoundTag.contains(TAG_INTERVAL) ? compoundTag.getDouble(TAG_INTERVAL) : 0.0;
        thickness = compoundTag.contains(TAG_THICKNESS) ? Math.max(1, compoundTag.getInt(TAG_THICKNESS)) : 1;
        thickDirection = compoundTag.contains(TAG_THICK_DIR) && compoundTag.getBoolean(TAG_THICK_DIR);
        verticalMode = VerticalMode.parse(compoundTag.getString(TAG_VERTICAL));
        patchCorner = !compoundTag.contains(TAG_PATCH_CORNER) || compoundTag.getBoolean(TAG_PATCH_CORNER);
        if (compoundTag.contains(TAG_OBJECTS)) {
            for (Tag tag : compoundTag.getList(TAG_OBJECTS, Tag.TAG_COMPOUND)) {
                objects.add(PlacementObject.fromCompoundTag((CompoundTag) tag));
            }
        }
    }

    public CompoundTag toCompoundTag() {
        final CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString(TAG_TYPE, TYPE);
        compoundTag.putInt(TAG_ORDER, order);
        compoundTag.putString(TAG_NAME, name == null ? "" : name);
        compoundTag.putDouble(TAG_START, start);
        if (interval != null) compoundTag.putDouble(TAG_INTERVAL, interval);
        compoundTag.putInt(TAG_THICKNESS, thickness);
        compoundTag.putBoolean(TAG_THICK_DIR, thickDirection);
        compoundTag.putString(TAG_VERTICAL, verticalMode.name());
        compoundTag.putBoolean(TAG_PATCH_CORNER, patchCorner);
        final ListTag objectsTag = new ListTag();
        for (PlacementObject obj : objects) objectsTag.add(obj.toCompoundTag());
        compoundTag.put(TAG_OBJECTS, objectsTag);
        return compoundTag;
    }

    /** 竖直基向量模式 */
    public enum VerticalMode {
        /** 垂直轨道平面：up = normalize(cross(n, t))，物体随轨道倾斜（坡道/竖曲线贴合轨道） */
        TRACK,
        /** 垂直地面：up = (0,1,0)，物体始终竖直 */
        GROUND;

        public static VerticalMode parse(String s) {
            for (VerticalMode m : values()) if (m.name().equals(s)) return m;
            return GROUND;
        }
    }

    /** 组合中的一个放置对象 */
    public static class PlacementObject {
        public static final String TAG_BLOCK = "block";
        public static final String TAG_DT = "dt";
        public static final String TAG_DN = "dn";
        public static final String TAG_DU = "du";
        public static final String TAG_YAW = "yaw";
        public static final String TAG_PITCH = "pitch";
        public static final String TAG_ROLL = "roll";
        public static final String TAG_BE_NBT = "beNbt";
        public static final String TAG_MTR_DECOR = "mtrDecor";
        /** 捕获时源方块的朝向（Direction.ordinal()），构建时用于按轨道重新定向（见 HybridPlacementAction） */
        public static final String TAG_FACING = "facing";

        /** 目标方块完整名（如 fangsu:diaoban）；跨 MC 版本稳定 */
        public String blockName;
        /** 沿切向 t 的偏移（格） */
        public double dt;
        /** 沿法向 n 的偏移（格） */
        public double dn;
        /** 沿 up 的偏移（格） */
        public double du;
        /** 相对轨道系的局部旋转（弧度） */
        public double yaw, pitch, roll;
        /** 方块实体预设 NBT（translate/rotate/mainModel/extraConfigs 等），可为 null */
        public CompoundTag beNbt;
        /** 是否为 MTR4 装饰物件（MTR3 构建时该方块未注册则跳过） */
        public boolean mtrDecoration;
        /** 捕获时源方块的朝向（Direction.ordinal()，默认 0=南）；构建时按轨道重新定向 */
        public byte sourceFacing;

        public PlacementObject() {
            blockName = "";
        }

        public PlacementObject(PlacementObject other) {
            this.blockName = other.blockName;
            this.dt = other.dt;
            this.dn = other.dn;
            this.du = other.du;
            this.yaw = other.yaw;
            this.pitch = other.pitch;
            this.roll = other.roll;
            this.beNbt = other.beNbt == null ? null : other.beNbt.copy();
            this.mtrDecoration = other.mtrDecoration;
            this.sourceFacing = other.sourceFacing;
        }

        public CompoundTag toCompoundTag() {
            final CompoundTag tag = new CompoundTag();
            tag.putString(TAG_BLOCK, blockName == null ? "" : blockName);
            tag.putDouble(TAG_DT, dt);
            tag.putDouble(TAG_DN, dn);
            tag.putDouble(TAG_DU, du);
            tag.putDouble(TAG_YAW, yaw);
            tag.putDouble(TAG_PITCH, pitch);
            tag.putDouble(TAG_ROLL, roll);
            if (beNbt != null) tag.put(TAG_BE_NBT, beNbt);
            tag.putBoolean(TAG_MTR_DECOR, mtrDecoration);
            tag.putByte(TAG_FACING, sourceFacing);
            return tag;
        }

        public static PlacementObject fromCompoundTag(CompoundTag tag) {
            final PlacementObject obj = new PlacementObject();
            obj.blockName = tag.getString(TAG_BLOCK);
            obj.dt = tag.getDouble(TAG_DT);
            obj.dn = tag.getDouble(TAG_DN);
            obj.du = tag.getDouble(TAG_DU);
            obj.yaw = tag.getDouble(TAG_YAW);
            obj.pitch = tag.getDouble(TAG_PITCH);
            obj.roll = tag.getDouble(TAG_ROLL);
            obj.beNbt = tag.contains(TAG_BE_NBT) ? tag.getCompound(TAG_BE_NBT) : null;
            obj.mtrDecoration = tag.getBoolean(TAG_MTR_DECOR);
            obj.sourceFacing = tag.contains(TAG_FACING) ? tag.getByte(TAG_FACING) : (byte) Direction.SOUTH.ordinal();
            return obj;
        }
    }
}
