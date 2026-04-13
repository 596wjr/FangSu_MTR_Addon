package com.fangsu.customItem;

import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Deprecated
public class CustomItemLoader {

    /**
     * JSON 缓存，避免重复解析。
     */
    private static final Map<String, Map<String, ? extends Object>> register = new HashMap<>();

    /**
     * 优化 JSON：读取默认 content 节点。
     */
    public static Map<String, Map<String, Object>> optimizeCustomItemJSON(ResourceLocation location) throws Exception {
        return optimizeCustomItemJSON(location, "content");
    }

    /**
     * 将自定义 JSON 解析成 Map，并进行缓存。
     */
    public static Map<String, Map<String, Object>> optimizeCustomItemJSON(ResourceLocation location, String nestedKeyPath) throws Exception {
        String globalRegisterKey = "Identifier" + location.getPath() + "@JsonObject@" + nestedKeyPath;
        if (register.containsKey(globalRegisterKey)) {
            return (Map<String, Map<String, Object>>) register.get(globalRegisterKey);
        }

        JsonObject json = ResourceUtil.loadAsJSON(location).getAsJsonObject();
        Map<String, Map<String, Object>> resultMap = new HashMap<>();

        // 解析嵌套路径
        JsonElement targetElement = getNestedJsonElement(json, nestedKeyPath);

        if (targetElement == null) return null;

        if (targetElement.isJsonArray()) {
            JsonArray targetArray = targetElement.getAsJsonArray();

            for (int i = 0; i < targetArray.size(); i++) {
                JsonElement element = targetArray.get(i);
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                if (obj.has("id")) {
                    resultMap.put(obj.get("id").getAsString(), serializeJsonObject(obj));
                }
            }
        }

        register.put(globalRegisterKey, resultMap);
        return resultMap;
    }

    private static JsonElement getNestedJsonElement(JsonObject json, String path) {
        if (path == null || path.isEmpty()) {
            return json;
        }

        String[] parts = path.split("\\.");
        JsonElement current = json;

        for (String part : parts) {
            if (current == null || current.isJsonNull()) {
                return null;
            }

            // 检查是否为数组索引
            if (part.matches(".+\\[\\d+\\]$")) {
                // 处理数组索引，如 "door.left[0]"
                String[] arrayParts = part.split("\\[");
                String arrayKey = arrayParts[0];
                int index = Integer.parseInt(arrayParts[1].replace("]", ""));

                if (current.isJsonObject()) {
                    JsonObject obj = current.getAsJsonObject();
                    if (obj.has(arrayKey) && obj.get(arrayKey).isJsonArray()) {
                        JsonArray array = obj.get(arrayKey).getAsJsonArray();
                        if (index >= 0 && index < array.size()) {
                            current = array.get(index);
                        } else {
                            return null;
                        }
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                // 普通对象键
                if (current.isJsonObject()) {
                    JsonObject obj = current.getAsJsonObject();
                    if (obj.has(part)) {
                        current = obj.get(part);
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }

        return current;
    }

    /**
     * 递归序列化 JSON 对象。
     */
    public static Map<String, Object> serializeJsonObject(JsonObject json) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            Object value = readJsonValue(entry.getValue());
            if (value != null) {
                map.put(entry.getKey(), value);
            }
        }
        return map;
    }

    /**
     * 递归序列化 JSON 数组。
     */
    public static List<Object> serializeJsonArray(JsonArray array) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            Object value = readJsonValue(array.get(i));
            if (value != null) {
                list.add(value);
            }
        }
        return list;
    }

    /**
     * 将 JsonElement 转为 Java 对象。
     */
    private static Object readJsonValue(JsonElement element) {
        if (element.isJsonObject()) {
            return serializeJsonObject(element.getAsJsonObject());
        }
        if (element.isJsonArray()) {
            return serializeJsonArray(element.getAsJsonArray());
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString()) {
                return element.getAsJsonPrimitive().getAsString();
            }
            if (element.getAsJsonPrimitive().isNumber()) {
                return element.getAsJsonPrimitive().getAsDouble();
            }
            if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsJsonPrimitive().getAsBoolean();
            }
        }
        return null;
    }
}
