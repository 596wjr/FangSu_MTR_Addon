package com.fangsu.extraConfig;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

public class ConfigSpec {

    public final String type;
    public final Map<String, JsonElement> params = new HashMap<>();

    public ConfigSpec(String type) {
        this.type = type;
    }

    public ConfigSpec setParam(String key, JsonElement value) {
        params.put(key, value);
        return this;
    }

    public static ConfigSpec fromJson(JsonObject json) {
        if (!json.has("type")) {
            throw new IllegalArgumentException("Config json missing type");
        }

        ConfigSpec spec = new ConfigSpec(json.get("type").getAsString());

        for (var e : json.entrySet()) {
            if (!"type".equals(e.getKey())) {
                spec.params.put(e.getKey(), e.getValue());
            }
        }
        return spec;
    }

    public String getString(String key, String def) {
        return params.containsKey(key) ? params.get(key).getAsString() : def;
    }

    public int getInt(String key, int def) {
        return params.containsKey(key) ? params.get(key).getAsInt() : def;
    }

    public float getFloat(String key, float def) {
        return params.containsKey(key) ? params.get(key).getAsFloat() : def;
    }

    public boolean getBool(String key, boolean def) {
        return params.containsKey(key) ? params.get(key).getAsBoolean() : def;
    }
}
