package com.fangsu.mappings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gson 兼容工具类，统一处理 {@link JsonArray#asList()} 和
 * {@link JsonObject#asMap()} 在不同 Gson 版本中的可用性。
 */
public class GsonHelper {

    /**
     * 将 JsonArray 转为 List&lt;JsonElement&gt;。
     * 1.20.1 的 Gson 版本支持 asList()，1.18.2 可能不支持。
     */
    public static List<JsonElement> asList(JsonArray array) {
        //#if MC_VERSION >= 12000
        return array.asList();
        //#else
        //$$ List<JsonElement> result = new ArrayList<>();
        //$$ for (JsonElement el : array) result.add(el);
        //$$ return result;
        //#endif
    }

    /**
     * 将 JsonObject 转为 Map&lt;String, JsonElement&gt;。
     * 1.20.1 的 Gson 版本支持 asMap()，1.18.2 可能不支持。
     */
    public static Map<String, JsonElement> asMap(JsonObject obj) {
        //#if MC_VERSION >= 12000
        return obj.asMap();
        //#else
        //$$ Map<String, JsonElement> result = new HashMap<>();
        //$$ for (Map.Entry<String, JsonElement> entry : obj.entrySet()) result.put(entry.getKey(), entry.getValue());
        //$$ return result;
        //#endif
    }
}
