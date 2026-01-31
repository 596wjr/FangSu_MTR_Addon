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
    public static String checkMainModel(BaseObjBlockEntity entity, String defaultMainModel) {
        if (entity.mainModel == null) {
            entity.mainModel = defaultMainModel;
            return defaultMainModel;
        }
        return entity.mainModel;
    }

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

    private static Map<String, Map<String, ? extends Object>> register = new HashMap<>();

    public static Map<String, Map<String, Object>> optimizeCustomItemJSON(ResourceLocation location) throws Exception {
        return optimizeCustomItemJSON(location, "content");
    }

    public static Map<String, Map<String, Object>> optimizeCustomItemJSON(ResourceLocation location, String baseKey) throws Exception {
        String GlobalRegisterKey = "Identifier" + location.getPath() + "@JsonObject";
        if (register.containsKey(GlobalRegisterKey)) {
            return (Map<String, Map<String, Object>>) register.get(GlobalRegisterKey);
        }
        JsonObject json = ResourceUtil.loadAsJSON(location).getAsJsonObject();
        Map<String, Map<String, Object>> baseMap = new HashMap<>();
        if (json.has(baseKey))
            if (json.get(baseKey).isJsonArray()) {
                JsonArray baseArray = json.get(baseKey).getAsJsonArray();
                for (int i = 0; i < baseArray.size(); i++) {
                    JsonElement element = baseArray.get(i);
                    if (element.isJsonObject()) {
                        JsonObject obj = element.getAsJsonObject();
                        if (obj.has("id")) {
                            baseMap.put(obj.get("id").getAsString(), serializeJsonObject(obj));
                        } else continue;
                    }
                }
            }
        register.put(GlobalRegisterKey, baseMap);
        return baseMap;
    }

    public static Map<String, Object> serializeJsonObject(JsonObject json) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            JsonElement element = entry.getValue();
            if (element.isJsonObject()) {
                map.put(entry.getKey(), serializeJsonObject(element.getAsJsonObject()));
            } else if (element.isJsonArray()) {
                map.put(entry.getKey(), serializeJsonArray(element.getAsJsonArray()));
            } else if (element.isJsonPrimitive()) {
                if (element.getAsJsonPrimitive().isString()) {
                    map.put(entry.getKey(), element.getAsJsonPrimitive().getAsString());
                } else if (element.getAsJsonPrimitive().isNumber()) {
                    map.put(entry.getKey(), element.getAsJsonPrimitive().getAsDouble());
                } else if (element.getAsJsonPrimitive().isBoolean()) {
                    map.put(entry.getKey(), element.getAsJsonPrimitive().getAsBoolean());
                }
            }
        }
        return map;
    }

    public static List<Object> serializeJsonArray(JsonArray array) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (element.isJsonObject()) {
                list.add(serializeJsonObject(element.getAsJsonObject()));
            } else if (element.isJsonArray()) {
                list.add(serializeJsonArray(element.getAsJsonArray()));
            } else if (element.isJsonPrimitive()) {
                if (element.getAsJsonPrimitive().isString()) {
                    list.add(element.getAsJsonPrimitive().getAsString());
                } else if (element.getAsJsonPrimitive().isNumber()) {
                    list.add(element.getAsJsonPrimitive().getAsDouble());
                } else if (element.getAsJsonPrimitive().isBoolean()) {
                    list.add(element.getAsJsonPrimitive().getAsBoolean());
                }
            }
        }
        return list;
    }
}
