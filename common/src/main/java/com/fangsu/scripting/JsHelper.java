package com.fangsu.scripting;

import com.google.gson.*;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsHelper {
    public static Value toValue(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return Value.asValue(null);
        }
        if (json.isJsonPrimitive()) {
            JsonPrimitive primitive = json.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return Value.asValue(primitive.getAsBoolean());
            }
            if (primitive.isString()) {
                return Value.asValue(primitive.getAsString());
            }
            if (primitive.isNumber()) {
                return Value.asValue(primitive.getAsNumber());
            }
        }
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            List<Object> values = new ArrayList<>();
            for (JsonElement element : array) {
                values.add(toRawObject(element));
            }
            return Value.asValue(ProxyArray.fromList(values));
        }
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                map.put(entry.getKey(), toRawObject(entry.getValue()));
            }
            return Value.asValue(ProxyObject.fromMap(map));
        }
        return Value.asValue(null);
    }

    private static Object toRawObject(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return null;
        }

        if (json.isJsonPrimitive()) {
            JsonPrimitive primitive = json.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isString()) {
                return primitive.getAsString();
            }
            if (primitive.isNumber()) {
                return primitive.getAsNumber();
            }
        }

        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            List<Object> values = new ArrayList<>();
            for (JsonElement element : array) {
                values.add(toRawObject(element));
            }
            return ProxyArray.fromList(values);
        }

        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                map.put(entry.getKey(), toRawObject(entry.getValue()));
            }
            return ProxyObject.fromMap(map);
        }

        return null;
    }

    public static JsonElement toJsonElement(Value value) {
        if (value == null || value.isNull()) {
            return JsonNull.INSTANCE;
        }
        if (value.isString()) {
            return new JsonPrimitive(value.asString());
        }
        if (value.isBoolean()) {
            return new JsonPrimitive(value.asBoolean());
        }
        if (value.isNumber()) {
            return new JsonPrimitive(value.asDouble());
        }
        if (value.hasArrayElements()) {
            JsonArray array = new JsonArray();
            long size = value.getArraySize();
            for (long i = 0; i < size; i++) {
                Value element = value.getArrayElement(i);
                array.add(toJsonElement(element));
            }
            return array;
        }
        if (value.isHostObject() || value.hasMembers()) {
            JsonObject object = new JsonObject();
            for (String key : value.getMemberKeys()) {
                Value member = value.getMember(key);
                object.add(key, toJsonElement(member));
            }
            return object;
        }
        return JsonNull.INSTANCE;
    }
}
