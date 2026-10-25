package com.fangsu.turnout.marker;

import com.fangsu.Main;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端私有的轨道节点标记存储（不参与网络同步，服务端不保存）。
 * <p>
 * 键为「维度 ResourceLocation + 坐标」的扁平字符串，同一坐标在不同维度互不干扰；
 * 首次访问时懒加载 {@code config/fangsu/node_markers.json}，任何增删改立即整表写回
 * （标记由玩家手动放置，频率极低，合并写入即可，不需要逐帧或定时刷盘）。
 * 文件读写失败只打 debug 日志，不影响游戏。
 */
public final class NodeMarkerStore {

    private static final String CONFIG_DIR = "config";
    private static final String SUB_DIR = "fangsu";
    private static final String FILE_NAME = "node_markers.json";

    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, String>>() {
    }.getType();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static NodeMarkerStore instance;

    /** 完整存储（所有维度），键形如 {@code minecraft:overworld|1|2|3} */
    private final Map<String, MarkerColor> entries = new LinkedHashMap<>();
    private boolean loaded;

    private NodeMarkerStore() {
    }

    public static NodeMarkerStore getInstance() {
        if (instance == null) instance = new NodeMarkerStore();
        return instance;
    }

    /** 查询当前维度某个节点的标记，未标记返回 null */
    public NodeMarker get(BlockPos pos) {
        ensureLoaded();
        final MarkerColor color = entries.get(key(pos));
        return color == null ? null : new NodeMarker(pos, color);
    }

    /** 写入/覆盖标记：同一节点红蓝互斥，后放的覆盖先放的 */
    public void set(BlockPos pos, MarkerColor color) {
        if (pos == null || color == null) return;
        ensureLoaded();
        final String key = key(pos);
        if (key == null) return;
        if (entries.put(key, color) == color) return;
        save();
    }

    /** 移除标记，返回是否真的移除了 */
    public boolean remove(BlockPos pos) {
        if (pos == null) return false;
        ensureLoaded();
        final String key = key(pos);
        if (key == null || entries.remove(key) == null) return false;
        save();
        return true;
    }

    /**
     * 批量移除（道岔绑定成功后"消耗掉"的那几个节点）：<b>只删给定坐标</b>，不动其它标记——
     * 玩家可能同时准备了多个道岔的标记，所以绝不能退化成 {@link #clearAll()}。
     *
     * @return 实际被删掉的个数
     */
    public int removeAll(Collection<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) return 0;
        ensureLoaded();
        int removed = 0;
        for (BlockPos pos : positions) {
            if (pos == null) continue;
            final String key = key(pos);
            if (key == null) continue;
            if (entries.remove(key) != null) removed++;
        }
        if (removed > 0) save();
        return removed;
    }

    /** 当前维度全部标记的快照（渲染线程遍历用；不暴露内部 map） */
    public List<NodeMarker> snapshot() {
        ensureLoaded();
        final String dimension = currentDimension();
        if (dimension == null) return List.of();
        final String prefix = dimension + "|";
        final List<NodeMarker> list = new ArrayList<>();
        for (final Map.Entry<String, MarkerColor> entry : entries.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            final BlockPos pos = parsePos(entry.getKey());
            if (pos != null) list.add(new NodeMarker(pos, entry.getValue()));
        }
        return list;
    }

    /** 清空当前维度标记并立即保存 */
    public void clearAll() {
        ensureLoaded();
        final String dimension = currentDimension();
        if (dimension == null) return;
        final String prefix = dimension + "|";
        if (entries.keySet().removeIf(key -> key.startsWith(prefix))) save();
    }

    /** 当前维度的 ResourceLocation 字符串，客户端无世界时返回 null */
    private static String currentDimension() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        return minecraft.level.dimension().location().toString();
    }

    /** 坐标 → 存储键；无世界时返回 null（此时不接受写入,避免写入错误维度） */
    private static String key(BlockPos pos) {
        final String dimension = currentDimension();
        if (dimension == null) return null;
        return dimension + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    /** 存储键 → 坐标，畸形键返回 null（手改文件时容错） */
    private static BlockPos parsePos(String key) {
        final String[] parts = key.split("\\|");
        if (parts.length != 4) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Path filePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve(CONFIG_DIR).resolve(SUB_DIR).resolve(FILE_NAME);
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        final Path path = filePath();
        if (!Files.isRegularFile(path)) return;
        try {
            final Map<String, String> raw = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), MAP_TYPE);
            if (raw == null) return;
            for (final Map.Entry<String, String> entry : raw.entrySet()) {
                try {
                    final MarkerColor color = MarkerColor.valueOf(entry.getValue());
                    if (parsePos(entry.getKey()) != null) entries.put(entry.getKey(), color);
                } catch (IllegalArgumentException ignored) {
                    // 颜色名非法（手改文件）：跳过该条
                }
            }
        } catch (Exception e) {
            Main.debug("节点标记读取失败: " + e);
        }
    }

    private void save() {
        final Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(entries, MAP_TYPE), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Main.debug("节点标记保存失败: " + e);
        }
    }
}
