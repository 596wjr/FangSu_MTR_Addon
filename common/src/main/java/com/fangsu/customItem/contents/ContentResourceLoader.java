package com.fangsu.customItem.contents;

import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public final class ContentResourceLoader {
    private static final Map<String, Map<String, Map<String, Object>>> CACHE = new HashMap<>();
    private static final Map<String, JsonObject> ROOT_CACHE = new HashMap<>();

    private ContentResourceLoader() {
    }

    public static void reset() {
        CACHE.clear();
        ROOT_CACHE.clear();
    }

    public static JsonObject loadRoot(ResourceLocation location) {
        return ROOT_CACHE.computeIfAbsent(location.toString(), k -> {
            JsonElement element = ResourceUtil.loadAsJSON(location);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        });
    }

    public static Map<String, Map<String, Object>> loadMapByPath(ResourceLocation location, String nestedKeyPath) {
        String cacheKey = location + "#" + nestedKeyPath;
        if (CACHE.containsKey(cacheKey)) {
            return CACHE.get(cacheKey);
        }
        JsonObject root = loadRoot(location);
        if (root == null) {
            return null;
        }
        JsonElement targetElement = getNestedJsonElement(root, nestedKeyPath);
        if (targetElement == null || !targetElement.isJsonArray()) {
            return null;
        }
        Map<String, Map<String, Object>> resultMap = new HashMap<>();
        JsonArray targetArray = targetElement.getAsJsonArray();
        for (int i = 0; i < targetArray.size(); i++) {
            JsonElement element = targetArray.get(i);
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            if (!obj.has("id")) continue;
            resultMap.put(obj.get("id").getAsString(), serializeJsonObject(obj));
        }
        CACHE.put(cacheKey, resultMap);
        return resultMap;
    }

    public static List<ModelSelectInfo> loadModelSelectInfos(ResourceLocation location, String nestedKeyPath) {
        List<ModelSelectInfo> infos = new ArrayList<>();
        Map<String, Map<String, Object>> loaded = loadMapByPath(location, nestedKeyPath);
        if (loaded == null) return infos;
        for (Map<String, Object> item : loaded.values()) {
            String text = item.get("text") instanceof String s ? s : "";
            String content = item.get("id") instanceof String s ? s : "";
            String contentText = item.get("contentText") instanceof String s ? s : null;
            if (contentText != null) infos.add(new ModelSelectInfo(text, content, contentText));
            else infos.add(new ModelSelectInfo(text, content));
        }
        return infos;
    }

    private static JsonElement getNestedJsonElement(JsonObject json, String path) {
        if (path == null || path.isEmpty()) {
            return json;
        }
        String[] parts = path.split("\\.");
        JsonElement current = json;
        for (String part : parts) {
            if (current == null || current.isJsonNull()) return null;
            if (part.matches(".+\\[\\d+\\]$")) {
                String[] arrayParts = part.split("\\[");
                String arrayKey = arrayParts[0];
                int index = Integer.parseInt(arrayParts[1].replace("]", ""));
                if (!current.isJsonObject()) return null;
                JsonObject obj = current.getAsJsonObject();
                if (!obj.has(arrayKey) || !obj.get(arrayKey).isJsonArray()) return null;
                JsonArray array = obj.get(arrayKey).getAsJsonArray();
                if (index < 0 || index >= array.size()) return null;
                current = array.get(index);
            } else {
                if (!current.isJsonObject()) return null;
                JsonObject obj = current.getAsJsonObject();
                if (!obj.has(part)) return null;
                current = obj.get(part);
            }
        }
        return current;
    }

    private static Map<String, Object> serializeJsonObject(JsonObject json) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            Object value = readJsonValue(entry.getValue());
            if (value != null) map.put(entry.getKey(), value);
        }
        return map;
    }

    private static List<Object> serializeJsonArray(JsonArray array) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            Object value = readJsonValue(array.get(i));
            if (value != null) list.add(value);
        }
        return list;
    }

    private static Object readJsonValue(JsonElement element) {
        if (element.isJsonObject()) return serializeJsonObject(element.getAsJsonObject());
        if (element.isJsonArray()) return serializeJsonArray(element.getAsJsonArray());
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString()) return element.getAsString();
            if (element.getAsJsonPrimitive().isBoolean()) return element.getAsBoolean();
            if (element.getAsJsonPrimitive().isNumber()) return element.getAsDouble();
        }
        return null;
    }
}
