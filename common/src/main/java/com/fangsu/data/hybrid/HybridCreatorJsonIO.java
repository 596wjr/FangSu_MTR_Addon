package com.fangsu.data.hybrid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 混合构建器任务配置的 JSON 导出/导入（NBT ↔ GSON 递归转换），用于便捷分享配置。
 * <p>
 * 文件存放在游戏目录 {@code hybrid_creator/} 文件夹：导出文件名带时间戳
 * （{@code hybrid_creator_yyyyMMdd_HHmmss.json}），导入读取该文件夹下最新的 JSON。
 * <p>
 * 数字 round-trip 说明：NBT 数字在 JSON 中保留为数字，读回时无需精确还原原始类型
 * （{@link CompoundTag#getInt}/{@link CompoundTag#getDouble} 均接受任意
 * {@link NumericTag} 并内部转换）；纯数字数组还原为字节数组（任务 lumps 的存储形态）。
 */
public class HybridCreatorJsonIO {

    private static final String DIR_NAME = "hybrid_creator";
    /** 方案预设子目录：hybrid_creator/schemes/，与任务预设（根目录）隔离 */
    public static final String SCHEME_DIR = "schemes";
    /** 切片预设子目录：hybrid_creator/slices/（单个任务的导出/导入） */
    public static final String SLICE_DIR = "slices";
    /** 1.20.1 的 GsonHelper 无 toStableJson（1.20.3+ 才有），直接用 Gson 序列化。
     *  紧凑输出（无换行空格）：配置要复制到剪贴板分享，压缩后更短 */
    private static final Gson COMPACT_GSON = new GsonBuilder().disableHtmlEscaping().create();

    private HybridCreatorJsonIO() {
    }

    /** 导出 tasks 复合标签到 JSON 文件（自动时间戳命名），返回写入的文件路径（绝对路径） */
    public static String write(CompoundTag tasksTag) throws IOException {
        return write(tasksTag, "hybrid_creator_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()));
    }

    /**
     * 导出 tasks 复合标签到 JSON 文件（指定文件名，自动补 .json）。
     * 文件名做安全过滤：非法路径字符替换为下划线、去「..」防路径穿越。
     */
    public static String write(CompoundTag tasksTag, String fileName) throws IOException {
        return write(tasksTag, fileName, null);
    }

    /** 同 {@link #write(CompoundTag, String)}，subDir 非空时写入 hybrid_creator/<subDir>/ 子目录（如方案预设） */
    public static String write(CompoundTag tasksTag, String fileName, String subDir) throws IOException {
        final String safe = fileName == null ? "hybrid_creator" : fileName.trim().replaceAll("[\"*/:<>?\\\\|]", "_").replace("..", "_");
        final Path dir = presetDir(subDir);
        Files.createDirectories(dir);
        final Path path = dir.resolve((safe.isEmpty() ? "hybrid_creator" : safe) + ".json");
        Files.writeString(path, COMPACT_GSON.toJson(tagToJson(tasksTag)), StandardCharsets.UTF_8);
        return path.toString();
    }

    /** 列出预设文件夹下全部 JSON 文件名（按修改时间新的在前）；文件夹不存在时返回空列表 */
    public static List<String> listJsonFiles() throws IOException {
        return listJsonFiles(null);
    }

    /** 同 {@link #listJsonFiles()}，列出 hybrid_creator/<subDir>/ 下的文件 */
    public static List<String> listJsonFiles(String subDir) throws IOException {
        final Path dir = presetDir(subDir);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(path -> path.toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(path -> -path.toFile().lastModified()))
                    .map(path -> path.getFileName().toString())
                    .toList();
        }
    }

    /** 读取指定文件名的 JSON 并解析为 tasks 复合标签；文件不存在或解析失败时返回 null */
    public static CompoundTag read(String fileName) throws IOException {
        return read(fileName, null);
    }

    /** 同 {@link #read(String)}，从 hybrid_creator/<subDir>/ 子目录读取 */
    public static CompoundTag read(String fileName, String subDir) throws IOException {
        final Path dir = presetDir(subDir);
        final Path path = dir.resolve(fileName).normalize();
        if (!path.startsWith(dir) || !Files.isRegularFile(path)) return null;
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    /** 预设目录：subDir 为 null/空时用根目录 hybrid_creator/，否则用其子目录 */
    private static Path presetDir(String subDir) {
        final Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(DIR_NAME);
        return subDir == null || subDir.isEmpty() ? dir : dir.resolve(subDir);
    }

    /** 解析 JSON 文本为 tasks 复合标签；非法 JSON 返回 null（供「粘贴 JSON 导入」使用） */
    public static CompoundTag parse(String json) {
        try {
            final JsonElement element = JsonParser.parseString(json);
            final Tag tag = jsonToTag(element);
            return tag instanceof CompoundTag compoundTag ? compoundTag : null;
        } catch (IllegalArgumentException | com.google.gson.JsonSyntaxException e) {
            return null;
        }
    }

    /* ===================== 混合方案单独导出/导入（跨任务复用） ===================== */

    /**
     * 导出单个混合方案为 JSON 文本（剪贴板分享用；结构 = 任务 JSON 中 schemes 的单个条目）。
     * 方块名 + 属性串与任务数据同策略（完整名 + 属性串），跨 MC 版本稳定。
     */
    public static String schemeToJson(HybridScheme scheme) {
        return COMPACT_GSON.toJson(tagToJson(scheme.toCompoundTag()));
    }

    /**
     * 解析单个混合方案 JSON 文本；非法 JSON 或结构不符返回 null。
     * 方案条目里的方块名可被手改，畸形名抛异常在此统一拦截（不向上抛）。
     */
    public static HybridScheme parseScheme(String json) {
        try {
            final JsonElement element = JsonParser.parseString(json);
            final Tag tag = jsonToTag(element);
            if (!(tag instanceof CompoundTag compoundTag) || !compoundTag.contains(HybridScheme.TAG_ENTRIES)) {
                return null;
            }
            return HybridScheme.fromCompoundTag(compoundTag);
        } catch (IllegalArgumentException | com.google.gson.JsonSyntaxException | ClassCastException e) {
            return null;
        }
    }

    /* ===================== NBT → JSON ===================== */

    private static JsonElement tagToJson(Tag tag) {
        if (tag instanceof CompoundTag compoundTag) {
            final JsonObject object = new JsonObject();
            for (String key : compoundTag.getAllKeys()) {
                object.add(key, tagToJson(compoundTag.get(key)));
            }
            return object;
        }
        if (tag instanceof ByteArrayTag byteArrayTag) {
            final JsonArray array = new JsonArray();
            for (byte b : byteArrayTag.getAsByteArray()) array.add(b);
            return array;
        }
        if (tag instanceof ListTag listTag) {
            final JsonArray array = new JsonArray();
            for (Tag entry : listTag) array.add(tagToJson(entry));
            return array;
        }
        if (tag instanceof NumericTag numericTag) {
            return new JsonPrimitive(numericTag.getAsNumber());
        }
        // StringTag 等文本型
        return new JsonPrimitive(tag.getAsString());
    }

    /* ===================== JSON → NBT ===================== */

    private static Tag jsonToTag(JsonElement element) {
        if (element.isJsonObject()) {
            final CompoundTag compoundTag = new CompoundTag();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                compoundTag.put(entry.getKey(), jsonToTag(entry.getValue()));
            }
            return compoundTag;
        }
        if (element.isJsonArray()) {
            final JsonArray array = element.getAsJsonArray();
            // 纯数字数组 → 字节数组（任务 lumps 的形态）；否则按 NBT 列表还原
            boolean allNumbers = !array.isEmpty();
            for (JsonElement child : array) {
                if (!child.isJsonPrimitive() || !child.getAsJsonPrimitive().isNumber()) {
                    allNumbers = false;
                    break;
                }
            }
            if (allNumbers) {
                final byte[] bytes = new byte[array.size()];
                for (int i = 0; i < array.size(); i++) bytes[i] = (byte) array.get(i).getAsInt();
                return new ByteArrayTag(bytes);
            }
            final ListTag listTag = new ListTag();
            for (JsonElement child : array) listTag.add(jsonToTag(child));
            return listTag;
        }
        if (element.isJsonPrimitive()) {
            final JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                // 类型不精确还原：整数值存 IntTag，其余存 DoubleTag（读取端可互转）
                final double value = primitive.getAsDouble();
                if (value == Math.rint(value) && !Double.isInfinite(value)) {
                    return IntTag.valueOf((int) value);
                }
                return DoubleTag.valueOf(value);
            }
            if (primitive.isBoolean()) return IntTag.valueOf(primitive.getAsBoolean() ? 1 : 0);
            return StringTag.valueOf(primitive.getAsString());
        }
        throw new IllegalArgumentException("不支持的 JSON 节点: " + element);
    }
}
