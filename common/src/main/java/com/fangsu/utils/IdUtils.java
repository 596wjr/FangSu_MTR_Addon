package com.fangsu.utils;

import com.fangsu.Main;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.lang.reflect.Array;
import java.util.*;

/**
 * 模拟 JS 的 getShortId 函数：使用 Gson 进行规范化处理并计算 FNV-1a 哈希。
 */
public class IdUtils {

    /**
     * 生成对象的短 ID（FNV-1a 哈希的 16 进制字符串）。
     *
     * @param obj 任意对象
     * @return 16 进制哈希字符串（小写）
     */
    public static String getShortId(Object obj) {
        String normalized = normalizeToString(obj);
        return fnv1aHex(normalized);
    }

    // ==================== 规范化字符串 ====================

    private static String normalizeToString(Object obj) {
        if (obj == null) {
            return "null";
        }

        // 字符串 -> 直接返回（不加引号）
        if (obj instanceof String) {
            return (String) obj;
        }

        // 数字、布尔 -> 直接转为字符串（同 JS String()）
        if (obj instanceof Number || obj instanceof Boolean) {
            return String.valueOf(obj);
        }

        // 数组或集合 -> 按 JS 数组处理
        if (obj instanceof Collection || obj.getClass().isArray()) {
            return arrayToString(asList(obj));
        }

        // Map 或普通对象 -> 转换为 JsonObject 再序列化
        JsonElement jsonElement = convertToJsonElement(obj);
        return Main.GSON.toJson(jsonElement);
    }

    /**
     * 将任意对象转换为规范化的 JsonElement
     */
    private static JsonElement convertToJsonElement(Object obj) {
        if (obj == null) {
            return null;
        }

        // 处理基本类型和字符串
        if (obj instanceof String) {
            return new JsonPrimitive((String) obj);
        }
        if (obj instanceof Number) {
            return new JsonPrimitive((Number) obj);
        }
        if (obj instanceof Boolean) {
            return new JsonPrimitive((Boolean) obj);
        }

        // 处理数组/集合
        if (obj instanceof Collection || obj.getClass().isArray()) {
            Collection<?> list = asList(obj);
            // 将每个元素转为字符串并排序
            List<String> stringElements = new ArrayList<>();
            for (Object e : list) {
                stringElements.add(elementToSortString(e));
            }
            Collections.sort(stringElements);

            // 构建 JSON 数组
            JsonArray jsonArray = new JsonArray();
            for (String s : stringElements) {
                jsonArray.add(s);
            }
            return jsonArray;
        }

        // 处理 Map
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            // 按键排序
            TreeMap<String, JsonElement> sortedMap = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                sortedMap.put(key, convertToJsonElement(entry.getValue()));
            }

            JsonObject jsonObject = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : sortedMap.entrySet()) {
                jsonObject.add(entry.getKey(), entry.getValue());
            }
            return jsonObject;
        }

        // 其他对象：尝试反射获取字段（但更简单的方式是转为 Map）
        return convertToJsonElement(objectToMap(obj));
    }

    /**
     * 将数组/集合转为规范化字符串（元素转为字符串并排序，再 JSON 序列化）
     */
    private static String arrayToString(Collection<?> list) {
        List<String> stringElements = new ArrayList<>();
        for (Object e : list) {
            stringElements.add(elementToSortString(e));
        }
        Collections.sort(stringElements);

        JsonArray jsonArray = new JsonArray();
        for (String s : stringElements) {
            jsonArray.add(s);
        }
        return Main.GSON.toJson(jsonArray);
    }

    /**
     * 将任意对象转为用于排序的字符串（模拟 JS 的 String(e)）
     */
    private static String elementToSortString(Object e) {
        if (e == null) return "null";
        return String.valueOf(e);
    }

    /**
     * 将任意对象转为 Map（利用反射获取字段）
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectToMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }

        Map<String, Object> result = new HashMap<>();
        // 简单实现：使用 Gson 先将对象转为 JsonObject，再转成 Map
        // 这样能保证字段名的正确性
        try {
            JsonElement jsonElement = Main.GSON.toJsonTree(obj);
            if (jsonElement.isJsonObject()) {
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                    result.put(entry.getKey(), jsonElementToObject(entry.getValue()));
                }
            } else {
                // 如果不是对象，就作为一个特殊值
                result.put("value", obj.toString());
            }
        } catch (Exception e) {
            // 转换失败，退化为 toString()
            result.put("value", String.valueOf(obj));
        }
        return result;
    }

    /**
     * 将 JsonElement 转换为 Java 对象
     */
    private static Object jsonElementToObject(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                return primitive.getAsNumber();
            }
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            return primitive.getAsString();
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            JsonArray array = element.getAsJsonArray();
            for (JsonElement e : array) {
                list.add(jsonElementToObject(e));
            }
            return list;
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                map.put(entry.getKey(), jsonElementToObject(entry.getValue()));
            }
            return map;
        }
        return null;
    }

    /**
     * 将数组或集合转为 List
     */
    private static Collection<?> asList(Object obj) {
        if (obj instanceof Collection) {
            return (Collection<?>) obj;
        }
        if (obj.getClass().isArray()) {
            int length = Array.getLength(obj);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(Array.get(obj, i));
            }
            return list;
        }
        throw new IllegalArgumentException("Not an array or collection: " + obj.getClass());
    }

    // ==================== FNV-1a 哈希 ====================

    private static final int FNV1_32_INIT = 0x811c9dc5;
    private static final int FNV1_32_PRIME = 0x01000193;

    private static String fnv1aHex(String str) {
        int hash = FNV1_32_INIT;
        for (int i = 0; i < str.length(); i++) {
            hash ^= str.charAt(i);       // 按 16 位字符异或（与原 JS 一致）
            hash *= FNV1_32_PRIME;
            // 确保结果为 32 位有符号整数（Java 会自动处理溢出）
        }
        return Integer.toHexString(hash); // 返回小写 16 进制
    }
}