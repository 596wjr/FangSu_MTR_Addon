package com.fangsu.train;

import com.fangsu.Main;
import com.fangsu.train.DoorOpenRecord.DoorOpenSide;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
//#if MC_VERSION >= 12002
//$$ import net.minecraft.util.datafix.DataFixTypes;
//#endif

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开门方向缓存的世界存档数据（仅服务端使用）。
 * <p>
 * 结构：站台 id -> 记录列表（<b>新在前</b>，下标 0 是最新观测），配一个 {@code totalRecords} 计数。
 * 所有写入/NBT 读取之后都会 {@link #trim()}：单站台上限、站台数上限、总记录上限，超限一律淘汰「最旧」。
 * <p>
 * 本类与 {@link DoorOpenRecord} 一样是双端共享类的服务端半边，<b>不得</b>引用任何客户端类。
 */
public final class DoorOpenCacheData extends SavedData {

    /** 存档文件名（不含扩展名）。 */
    public static final String DATA_NAME = "fangsu_door_open_cache";
    /** 每个站台最多保留的记录数。 */
    public static final int MAX_PER_PLATFORM = 8;
    /** 最多保留的站台数。 */
    public static final int MAX_PLATFORMS = 1024;
    /** 全局最多保留的记录数。 */
    public static final int MAX_TOTAL = 4096;
    /** 列车配置型号字符串最大长度。 */
    public static final int MAX_TYPE_LEN = 128;
    /** 车节数上限。 */
    public static final int MAX_CARS = 64;
    /** 列车总长度上限（米）。 */
    public static final int MAX_LEN_M = 2000;

    // NBT 键名（严格按契约，读写必须对称）
    private static final String KEY_VERSION = "version";
    private static final String KEY_RECORDS = "records";
    private static final String KEY_PLATFORM = "Platform";
    private static final String KEY_TRAIN_TYPE = "TrainType";
    private static final String KEY_CARS = "Cars";
    private static final String KEY_LENGTH = "Length";
    private static final String KEY_REVERSED = "Reversed";
    private static final String KEY_OBSERVED_AT = "ObservedAt";
    private static final String KEY_SIDES = "Sides";
    private static final int NBT_VERSION = 1;

    private final Map<Long, List<DoorOpenRecord>> byPlatform = new LinkedHashMap<>();
    private int totalRecords = 0;

    /** 取（或创建）主世界存档上的缓存实例。 */
    public static DoorOpenCacheData get(MinecraftServer server) {
        //#if MC_VERSION >= 12002
        //$$ return server.overworld().getDataStorage().computeIfAbsent(
        //$$         new SavedData.Factory<>(DoorOpenCacheData::new, DoorOpenCacheData::load, DataFixTypes.LEVEL),
        //$$         DATA_NAME);
        //#else
        return server.overworld().getDataStorage()
                .computeIfAbsent(DoorOpenCacheData::load, DoorOpenCacheData::new, DATA_NAME);
        //#endif
    }

    /**
     * 写入一条观测记录：按 {@code (type, cars, len)} 去重替换，新记录放队首，然后裁剪超限数据。
     * 非法记录（范围越界 / 含 UNKNOWN）直接丢弃，不入库。
     */
    public void upsert(long platformId, DoorOpenRecord record) {
        if (!isValidRecord(record)) {
            Main.debug("[开门缓存] 丢弃非法记录，platformId={}", platformId);
            return;
        }
        List<DoorOpenRecord> list = byPlatform.computeIfAbsent(platformId, k -> new ArrayList<>());
        // 同配置旧记录被新观测替换（先移除，再统一插到队首）
        for (int i = 0; i < list.size(); i++) {
            DoorOpenRecord old = list.get(i);
            if (old.trainTypeId().equals(record.trainTypeId())
                    && old.trainCars() == record.trainCars()
                    && old.lengthMeters() == record.lengthMeters()) {
                list.remove(i);
                break;
            }
        }
        list.add(0, record);
        totalRecords++;
        trim();
        setDirty();
    }

    /** 返回不可变副本，供网络层下发/服务端逻辑遍历，避免调用方改到内部容器。 */
    public Map<Long, List<DoorOpenRecord>> snapshot() {
        Map<Long, List<DoorOpenRecord>> copy = new LinkedHashMap<>();
        for (Map.Entry<Long, List<DoorOpenRecord>> entry : byPlatform.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * 校验一条记录是否允许入库/上线：字段在约定范围内且不含 UNKNOWN。
     * 不校验 {@code sides.length == trainCars}（网络层另行校验），以便复用本方法做纯范围判断。
     */
    public static boolean isValidRecord(DoorOpenRecord r) {
        if (r == null) return false;
        String type = r.trainTypeId();
        if (type == null || type.length() > MAX_TYPE_LEN) return false;
        int cars = r.trainCars();
        if (cars <= 0 || cars > MAX_CARS) return false;
        int length = r.lengthMeters();
        if (length < 0 || length > MAX_LEN_M) return false;
        DoorOpenSide[] sides = r.sides();
        if (sides.length == 0 || sides.length > MAX_CARS) return false;
        for (DoorOpenSide side : sides) {
            if (!DoorOpenSide.isStorable(side)) return false;
        }
        return true;
    }

    /**
     * 裁剪到各项上限（每次写入/读档后调用）。
     * 第 1 步修正单站台数量；第 2 步超站台数上限时整站台移除「最新记录最旧」者；
     * 第 3 步超总记录上限时删「最新记录最旧」站台的最旧一条。
     */
    private void trim() {
        // 先按容器重算总数，保证 load / upsert 混合路径下计数始终一致
        int sum = 0;
        for (List<DoorOpenRecord> list : byPlatform.values()) {
            sum += list.size();
        }
        totalRecords = sum;

        // 1) 每个站台：新在前，从队尾（最旧）开始删
        for (List<DoorOpenRecord> list : byPlatform.values()) {
            while (list.size() > MAX_PER_PLATFORM) {
                list.remove(list.size() - 1);
                totalRecords--;
            }
        }
        byPlatform.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // 2) 站台数上限：按「该站台最新记录的时间戳」找最陈旧的站台整块移除
        while (byPlatform.size() > MAX_PLATFORMS) {
            Long stalestPlatform = findStalestPlatform();
            if (stalestPlatform == null) break;
            List<DoorOpenRecord> removed = byPlatform.remove(stalestPlatform);
            if (removed != null) totalRecords -= removed.size();
        }

        // 3) 总记录上限：每次删「最陈旧站台」的队尾（最旧）一条
        while (totalRecords > MAX_TOTAL) {
            Long stalestPlatform = findStalestPlatform();
            if (stalestPlatform == null) break;
            List<DoorOpenRecord> list = byPlatform.get(stalestPlatform);
            if (list == null || list.isEmpty()) {
                byPlatform.remove(stalestPlatform);
                continue;
            }
            list.remove(list.size() - 1);
            totalRecords--;
            if (list.isEmpty()) byPlatform.remove(stalestPlatform);
        }
    }

    /** 找「最新记录最旧」的站台 id；容器为空返回 null。 */
    private Long findStalestPlatform() {
        Long stalest = null;
        long stalestNewest = Long.MAX_VALUE;
        for (Map.Entry<Long, List<DoorOpenRecord>> entry : byPlatform.entrySet()) {
            List<DoorOpenRecord> list = entry.getValue();
            if (list.isEmpty()) continue;
            long newest = list.get(0).observedAt();
            if (newest < stalestNewest) {
                stalestNewest = newest;
                stalest = entry.getKey();
            }
        }
        return stalest;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(KEY_VERSION, NBT_VERSION);
        ListTag recordsTag = new ListTag();
        for (Map.Entry<Long, List<DoorOpenRecord>> entry : byPlatform.entrySet()) {
            long platformId = entry.getKey();
            for (DoorOpenRecord record : entry.getValue()) {
                CompoundTag item = new CompoundTag();
                item.putLong(KEY_PLATFORM, platformId);
                item.putString(KEY_TRAIN_TYPE, record.trainTypeId());
                item.putInt(KEY_CARS, record.trainCars());
                item.putInt(KEY_LENGTH, record.lengthMeters());
                item.putBoolean(KEY_REVERSED, record.reversed());
                item.putLong(KEY_OBSERVED_AT, record.observedAt());
                DoorOpenSide[] sides = record.sides();
                int[] ordinals = new int[sides.length];
                for (int i = 0; i < sides.length; i++) {
                    DoorOpenSide side = sides[i];
                    ordinals[i] = side == null ? DoorOpenSide.UNKNOWN.ordinal() : side.ordinal();
                }
                item.putIntArray(KEY_SIDES, ordinals);
                recordsTag.add(item);
            }
        }
        tag.put(KEY_RECORDS, recordsTag);
        return tag;
    }

    /** 读档：逐项校验，坏条目记 warn 后跳过，最后统一裁剪。 */
    private static DoorOpenCacheData load(CompoundTag tag) {
        DoorOpenCacheData data = new DoorOpenCacheData();
        int version = tag.getInt(KEY_VERSION);
        if (version != NBT_VERSION) {
            Main.LOGGER.warn("[开门缓存] 存档版本为 {}（期望 {}），仍按兼容方式读取", version, NBT_VERSION);
        }
        ListTag recordsTag = tag.getList(KEY_RECORDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < recordsTag.size(); i++) {
            try {
                CompoundTag item = recordsTag.getCompound(i);
                long platformId = item.getLong(KEY_PLATFORM);
                String type = item.getString(KEY_TRAIN_TYPE);
                int cars = item.getInt(KEY_CARS);
                int length = item.getInt(KEY_LENGTH);
                boolean reversed = item.getBoolean(KEY_REVERSED);
                long observedAt = item.getLong(KEY_OBSERVED_AT);
                int[] ordinals = item.getIntArray(KEY_SIDES);
                DoorOpenSide[] sides = new DoorOpenSide[ordinals.length];
                for (int j = 0; j < ordinals.length; j++) {
                    sides[j] = DoorOpenSide.fromOrdinalSafe(ordinals[j]);
                }
                DoorOpenRecord record = new DoorOpenRecord(sides, type, cars, length, reversed, observedAt);
                if (!isValidRecord(record)) {
                    Main.LOGGER.warn("[开门缓存] 跳过非法记录 #{}（platformId={}，type={}，cars={}，len={}）",
                            i, platformId, type, cars, length);
                    continue;
                }
                // 存档本来就按「新在前」写入，这里保持顺序追加
                data.byPlatform.computeIfAbsent(platformId, k -> new ArrayList<>()).add(record);
            } catch (Exception e) {
                // 单个坏条目不影响整份缓存
                Main.LOGGER.warn("[开门缓存] 读取记录 #" + i + " 失败，已跳过", e);
            }
        }
        data.trim();
        return data;
    }
}
