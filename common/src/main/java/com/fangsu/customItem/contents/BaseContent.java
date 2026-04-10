package com.fangsu.customItem.contents;

import com.google.gson.JsonObject;

public abstract class BaseContent {
    private final JsonObject json;

    protected BaseContent(JsonObject json) {
        this.json = json;
    }

    public JsonObject getJson() {
        return json;
    }
}
