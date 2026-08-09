package com.fangsu.customItem;

import com.fangsu.Main;
import com.fangsu.scripting.JsonHelper;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轨道模型管理器，负责从以下来源收集可用的自定义轨道模型：
 * <ul>
 *   <li>NTE（NewTrainExpansion）风格：{@code mtrsteamloco:rails} 目录下所有 JSON 文件</li>
 *   <li>MTR 原生自定义资源：{@code mtr:mtr_custom_resources.json} 中的 "rails" 数组</li>
 * </ul>
 *
 * <p>NTE {@code mtrsteamloco:rails} 目录下的 JSON 支持两种格式：</p>
 * <pre>
 * // 单个模型
 * { "name": "Testing Yellow Stuff", "model": "mtrsteamloco:rails/cube_yellow.obj", "repeatInterval": 1.0 }
 *
 * // 多个模型（按 key 分组）
 * {
 *   "key1": { "name": "Name1", "model": "mtrsteamloco:rails/model1.obj" },
 *   "key2": { "name": "Name2", "model": "mtrsteamloco:rails/model2.obj", "repeatInterval": 1.0, "flipV": true }
 * }
 * </pre>
 *
 * <p>{@code mtr:mtr_custom_resources.json} 中的 "rails" 数组：</p>
 * <pre>
 * {
 *   "rails": [
 *     { "id": "my_custom_rail", "name": "My custom Rail",
 *       "modelResource": "mtr:custom_rails/my_custom_rail.obj", "repeatInterval": 0.5 }
 *   ]
 * }
 * </pre>
 */
public class NteRailManager {
    private static final ResourceLocation RAIL_LOCATION = new ResourceLocation("mtrsteamloco", "rails");
    private static final ResourceLocation MTR_CUSTOM_RESOURCES = new ResourceLocation("mtr", "mtr_custom_resources.json");
    private static final String RAILS_KEY = "rails";

    private static final NteRailManager INSTANCE = new NteRailManager();

    /** 已加载的轨道，按 id 索引（保留加载顺序）。 */
    private final Map<String, NteRail> rails = new LinkedHashMap<>();

    public static NteRailManager getInstance() {
        return INSTANCE;
    }

    /** 初始化并加载全部轨道。 */
    public void init() {
        load();
    }

    /**
     * 加载 {@code mtrsteamloco:rails} 目录下的所有 JSON 文件，以及
     * {@code mtr:mtr_custom_resources.json} 中 "rails" 数组定义的轨道。
     */
    public void load() {
        rails.clear();
        loadNteDirectoryRails();
        loadMtrCustomRails();
        Main.debug("Loaded {} custom rails", rails.size());
    }

    // ==================== 访问器 ====================

    /** 返回全部已加载轨道（不可修改视图），按加载顺序排列。 */
    public List<NteRail> getRails() {
        return Collections.unmodifiableList(new ArrayList<>(rails.values()));
    }

    /** 按 id 查找轨道，不存在时返回 null。 */
    @Nullable
    public NteRail getRail(String id) {
        return id == null ? null : rails.get(id);
    }

    /** 判断指定 id 的轨道是否存在。 */
    public boolean hasRail(String id) {
        return id != null && rails.containsKey(id);
    }

    /** 返回全部已加载轨道的 id 集合（不可修改）。 */
    public List<String> getRailIds() {
        return Collections.unmodifiableList(new ArrayList<>(rails.keySet()));
    }

    // ==================== 加载逻辑 ====================

    /** 加载 NTE 风格：{@code mtrsteamloco:rails} 目录下的所有 JSON 文件。 */
    private void loadNteDirectoryRails() {
        List<ResourceLocation> files = ResourceUtil.listResources("mtrsteamloco", "rails", Collections.singletonList(".json"));
        if (files.isEmpty()) {
            return;
        }
        for (ResourceLocation file : files) {
            String path = file.getPath();
            // 跳过目录本身，只处理实际的 JSON 文件
            if (path.endsWith("/") || path.endsWith("rails") || !path.endsWith(".json")) {
                continue;
            }
            try {
                JsonElement root = ResourceUtil.simpleLoadAsJson(file);
                if (root == null || !root.isJsonObject()) {
                    continue;
                }
                parseNteRailFile(file, root.getAsJsonObject());
            } catch (Exception e) {
                Main.LOGGER.warn("[FangSu] Failed to load NTE rail file {}: {}", file, e.getMessage());
            }
        }
    }

    /**
     * 解析单个 NTE 轨道 JSON 文件。支持两种顶层结构：
     * <ol>
     *   <li>单模型格式：顶层对象本身就是轨道定义（含 "name"/"model"）</li>
     *   <li>多模型格式：顶层对象按 key 映射到各自的轨道定义</li>
     * </ol>
     *
     * @param file 资源位置（用于推导单模型格式下的默认 id）
     * @param root 文件根对象
     */
    private void parseNteRailFile(ResourceLocation file, JsonObject root) {
        // 单模型格式：顶层含有 "model" 字段
        if (root.has("model")) {
            String id = deriveIdFromLocation(file);
            putRail(createNteRail(id, root, "model"));
            return;
        }

        // 多模型格式：遍历每个 key
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonObject()) {
                continue;
            }
            putRail(createNteRail(entry.getKey(), value.getAsJsonObject(), "model"));
        }
    }

    /** 加载 MTR 原生自定义资源：{@code mtr:mtr_custom_resources.json} 的 "rails" 数组。 */
    private void loadMtrCustomRails() {
        JsonElement mtrCustomResources = ResourceUtil.loadAsJSON(MTR_CUSTOM_RESOURCES);
        if (mtrCustomResources == null || !mtrCustomResources.isJsonObject()) {
            return;
        }
        JsonObject root = mtrCustomResources.getAsJsonObject();
        if (!root.has(RAILS_KEY) || !root.get(RAILS_KEY).isJsonArray()) {
            return;
        }
        JsonArray railsArray = root.getAsJsonArray(RAILS_KEY);
        for (int i = 0; i < railsArray.size(); i++) {
            JsonElement railElement = railsArray.get(i);
            if (railElement == null || !railElement.isJsonObject()) {
                continue;
            }
            JsonObject railObject = railElement.getAsJsonObject();
            // MTR 原生轨道使用 "modelResource"，而非 NTE 的 "model"
            putRail(createNteRail(railObject, "modelResource"));
        }
    }

    // ==================== 辅助方法 ====================

    /** 将轨道写入缓存，id 为空或重名时跳过并记录警告。 */
    private void putRail(NteRail rail) {
        if (rail == null) {
            return;
        }
        if (rail.id().isEmpty() || rail.model().isEmpty()) {
            Main.debug("[FangSu] Skipped NTE rail with missing id/model");
            return;
        }
        if (rails.containsKey(rail.id())) {
            Main.LOGGER.warn("[FangSu] Duplicate NTE rail id '{}', overwriting previous entry", rail.id());
        }
        rails.put(rail.id(), rail);
    }

    /** 从 NTE 目录 JSON 文件构建轨道（模型字段名为 "model"）。 */
    private NteRail createNteRail(String id, JsonObject json, String modelKey) {
        String name = JsonHelper.getOrDefaultString(json, "name", "");
        String model = JsonHelper.getOrDefaultString(json, modelKey, "");
        double repeatInterval = JsonHelper.getOrDefaultDouble(json, "repeatInterval", 0.5);
        boolean flipV = JsonHelper.getOrDefaultBoolean(json, "flipV", false);
        return new NteRail(id, name, model, repeatInterval, flipV);
    }

    /** 从 MTR "rails" 数组项构建轨道（id 必填）。 */
    private NteRail createNteRail(JsonObject json, String modelKey) {
        String id = JsonHelper.getOrDefaultString(json, "id", "");
        String name = JsonHelper.getOrDefaultString(json, "name", "");
        String model = JsonHelper.getOrDefaultString(json, modelKey, "");
        double repeatInterval = JsonHelper.getOrDefaultDouble(json, "repeatInterval", 0.5);
        boolean flipV = JsonHelper.getOrDefaultBoolean(json, "flipV", false);
        return new NteRail(id, name, model, repeatInterval, flipV);
    }

    /** 由资源位置推导单模型格式的默认 id（取去掉 ".json" 后缀的文件名）。 */
    private static String deriveIdFromLocation(ResourceLocation location) {
        String path = location.getPath();
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        if (fileName.endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length() - ".json".length());
        }
        return fileName;
    }

    /** 一条自定义轨道的不可变描述。实现了 {@link ModelSelectInfo} 以便用于选择界面。 */
    public record NteRail(String id, String name, String model, double repeatInterval, boolean flipV) implements ModelSelectInfo {
        public NteRail fromJson(JsonObject json) {
            return NteRailManager.getInstance().createNteRail(id, json, "model");
        }

        @Override
        public String getText() {
            return name;
        }

        @Override
        public String getContent() {
            return id;
        }

        @Override
        public String getContentText() {
            return name;
        }

        @Override
        public @Nullable JsonObject getDefault() {
            return null;
        }
    }
}

