package com.fangsu.customItem.contents;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.function.Function;

public abstract class BaseContent {
    private final String id;

    protected BaseContent(JsonObject json) {
        id = json.get("id").getAsString();
    }

    protected BaseContent(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    protected static <T> T getOrDefault(JsonObject json, String key, T defaultValue, Function<JsonElement, T> function) {
        if (!json.has(key)) return defaultValue;
        return function.apply(json.get(key));
    }
}
