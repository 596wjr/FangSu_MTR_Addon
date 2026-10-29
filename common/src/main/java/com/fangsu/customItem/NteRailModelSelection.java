package com.fangsu.customItem;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 用 NTE 原有 ModelKey 字符串传输和保存组合，旧的单模型值保持原样。 */
public final class NteRailModelSelection {
    private static final String PREFIX = "fangsu:rail_layers:";
    private static final Gson GSON = new Gson();
    // 渲染器每帧查询主模型，不要对同一组合反复解析 JSON；限制缓存大小避免跨世界累积。
    private static final Map<String, List<String>> CACHE = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
            return size() > 128;
        }
    });
    private static final Set<String> ACCESSORIES = Set.of("ex_dsg", "ex_dsg_mr", "ex_bredge",
            "ex_bredge_mr", "ex_brmiddle", "ex_brmiddle_mr", "ex_brbar", "ex_brbar_mr");

    private NteRailModelSelection() { }

    public static boolean isAccessory(String key) {
        return ACCESSORIES.contains(key);
    }

    public static boolean isCombined(String key) {
        return key != null && key.startsWith(PREFIX);
    }

    public static List<String> decode(String value) {
        if (!isCombined(value)) {
            // 旧存档若只选择了附属，也保留原来的「只画附属」语义。
            return List.of(value == null ? "" : value);
        }
        if (value.length() > 16000) return List.of("");
        List<String> cached = CACHE.get(value);
        if (cached != null) return cached;
        try {
            JsonElement root = JsonParser.parseString(value.substring(PREFIX.length()));
            if (!root.isJsonArray() || root.getAsJsonArray().size() > 32) return List.of("");
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return List.of("");
                result.add(element.getAsString());
            }
            List<String> decoded = result.isEmpty() ? List.of("") : List.copyOf(result);
            CACHE.put(value, decoded);
            return decoded;
        } catch (RuntimeException ignored) {
            return List.of("");
        }
    }

    public static String encode(String primary, Iterable<String> accessories) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add(primary);
        for (String accessory : accessories) {
            if (isAccessory(accessory)) keys.add(accessory);
        }
        return keys.size() == 1 ? primary : PREFIX + GSON.toJson(keys);
    }

}
