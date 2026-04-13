package com.fangsu.customItem.contents;

import com.google.gson.JsonObject;

public abstract class BaseContent {
    private final String id;

    protected BaseContent(JsonObject json) {
        id = json.get("id").getAsString();
    }

    public String getId() {
        return id;
    }
}
