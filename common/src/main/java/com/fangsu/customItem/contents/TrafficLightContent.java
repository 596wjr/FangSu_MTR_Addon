package com.fangsu.customItem.contents;

import com.fangsu.Main;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 交通灯内容配置。
 * <p>
 * 对应旧版脚本 {@code traffic_light.js} 里 {@code basic_traffic_light.json} 的单个条目：
 * 「traffic_light」数组下的每个对象包含 {@code id}/{@code text}/{@code model}/{@code flipV}/{@code subModels}。
 * 其中 {@code subModels} 是「子模型标签 -> OBJ 分组名」的映射，渲染时按标签取对应分组进行绘制，
 * 例如 {@code { "main": "main", "red": "r", "yellow": "y", "green": "g" }}。
 * <p>
 * 另可选 {@code displays}：数码管显示组配置，形如
 * {@code { "red": [ { "a":"digit_r_1_a", "b":"digit_r_1_b", ..., "g":"digit_r_1_g" }, {...} ], "green": [...] }}，
 * 每个数组元素是一位数码管的段名表（第1个=第1位、第2个=第2位…），用于 8 段显示渲染。
 */
public class TrafficLightContent extends BaseContent {
    private final String text;
    private final String model;
    private final boolean flipV;
    private final Map<String, String> subModels;
    /** 数码管显示组：组名(red/green) -> 一组(每位)段名表；每位表按 a..g(及可选 h) 给出段模型名。 */
    private final Map<String, List<Map<String, String>>> displays;
    /** 数码管段水平镜像：true 时 b↔f、c↔e 交换（补偿模型段几何镜像），默认 false。 */
    private final boolean displayMirrorX;

    private TrafficLightContent(JsonObject json) {
        super(json);
        text = json.has("text") ? json.get("text").getAsString() : getId();
        model = json.get("model").getAsString();
        flipV = json.has("flipV") && json.get("flipV").getAsBoolean();
        subModels = parseSubModels(json.get("subModels"));
        displays = parseDisplays(json.get("displays"));
        displayMirrorX = json.has("displayMirrorX") && json.get("displayMirrorX").getAsBoolean();
    }

    public String getText() {
        return text;
    }

    public String getModel() {
        return model;
    }

    public boolean isFlipV() {
        return flipV;
    }

    /** 数码管段是否做水平镜像交换（b↔f、c↔e）。 */
    public boolean isDisplayMirrorX() {
        return displayMirrorX;
    }

    /**
     * 「子模型标签 -> OBJ 分组名」映射，例如 {@code {"main":"main","red":"r","yellow":"y","green":"g"}}。
     */
    public Map<String, String> getSubModels() {
        return subModels;
    }

    /**
     * 数码管显示组：组名 -> 每位段名表列表（第1个=第1位）。段名表为 {@code 段字母->段模型名}。
     */
    public Map<String, List<Map<String, String>>> getDisplays() {
        return displays;
    }

    /**
     * 常亮的子模型标签（灯箱主体），对应旧版脚本中恒被绘制的 {@code main}。
     */
    public String getMainSubModel() {
        return "main";
    }

    private static Map<String, String> parseSubModels(JsonElement element) {
        Map<String, String> result = new LinkedHashMap<>();
        if (element == null || !element.isJsonObject()) return result;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                result.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return result;
    }

    /** 解析 displays：组 -> 一维数组，每元素为一「段字母->段模型名」对象。 */
    private static Map<String, List<Map<String, String>>> parseDisplays(JsonElement element) {
        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
        if (element == null || !element.isJsonObject()) return result;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            JsonElement arr = entry.getValue();
            if (arr == null || !arr.isJsonArray()) continue;
            List<Map<String, String>> digits = new ArrayList<>();
            for (JsonElement digitEl : arr.getAsJsonArray()) {
                if (digitEl == null || !digitEl.isJsonObject()) continue;
                Map<String, String> seg = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> se : digitEl.getAsJsonObject().entrySet()) {
                    if (se.getValue() != null && se.getValue().isJsonPrimitive()) {
                        seg.put(se.getKey(), se.getValue().getAsString());
                    }
                }
                if (!seg.isEmpty()) digits.add(seg);
            }
            if (!digits.isEmpty()) result.put(entry.getKey(), digits);
        }
        return result;
    }

    public static class TrafficLightLoader extends BaseLoader {
        public static void load(String type, String path, JsonObject content) {
            ContentManager cm = ContentManager.getInstance();
            for (Map.Entry<String, JsonElement> entry : content.entrySet()) {
                String entryKey = entry.getKey();
                JsonElement entryValue = entry.getValue();
                if (entryValue == null || !entryValue.isJsonArray()) {
                    Main.LOGGER.warn("Failed to load content {} of {}({}): JSON is null or empty", entryKey, type, path);
                    continue;
                }
                JsonArray entryArray = entryValue.getAsJsonArray();
                for (int i = 0; i < entryArray.size(); i++) {
                    JsonElement detailElement = entryArray.get(i);
                    if (detailElement == null || !detailElement.isJsonObject()) {
                        Main.LOGGER.warn("Failed to load content index {} in {} of {}({}): JSON is null or empty", i, entryKey, type, path);
                        continue;
                    }
                    JsonObject detailObject = detailElement.getAsJsonObject();
                    cm.addContent(type, path, new TrafficLightContent(detailObject));
                }
            }
        }
    }
}
