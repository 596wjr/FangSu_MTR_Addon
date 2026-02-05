package com.fangsu.utils;

import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomItemHelper {
    /**
     * 校验主模型配置，若为空则回填默认值。
     */
    public static String checkMainModel(BaseObjBlockEntity entity, String defaultMainModel) {
        if (entity.mainModel == null) {
            entity.mainModel = defaultMainModel;
            return defaultMainModel;
        }
        return entity.mainModel;
    }

    /**
     * 校验子模型配置，若未配置则回填默认值。
     */
    public static String checkSubModel(BaseObjBlockEntity entity, String key, String defaultMainModel) {
        if (entity.subModels == null) {
            entity.subModels = new HashMap<>();
            entity.subModels.put(key, defaultMainModel);
            return defaultMainModel;
        } else if (entity.subModels.containsKey(key)) {
            return entity.subModels.get(key);
        } else {
            entity.subModels.put(key, defaultMainModel);
            return defaultMainModel;
        }
    }

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
    public static Map<String, Map<String, Object>> optimizeCustomItemJSON(ResourceLocation location, String baseKey) throws Exception {
        String globalRegisterKey = "Identifier" + location.getPath() + "@JsonObject";
        if (register.containsKey(globalRegisterKey)) {
            return (Map<String, Map<String, Object>>) register.get(globalRegisterKey);
        }
        JsonObject json = ResourceUtil.loadAsJSON(location).getAsJsonObject();
        Map<String, Map<String, Object>> baseMap = new HashMap<>();
        if (json.has(baseKey) && json.get(baseKey).isJsonArray()) {
            JsonArray baseArray = json.get(baseKey).getAsJsonArray();
            for (int i = 0; i < baseArray.size(); i++) {
                JsonElement element = baseArray.get(i);
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                if (obj.has("id")) {
                    baseMap.put(obj.get("id").getAsString(), serializeJsonObject(obj));
                }
            }
        }
        register.put(globalRegisterKey, baseMap);
        return baseMap;
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
