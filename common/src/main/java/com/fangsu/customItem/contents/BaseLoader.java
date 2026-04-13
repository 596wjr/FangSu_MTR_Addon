package com.fangsu.customItem.contents;

import com.google.gson.JsonObject;

public abstract class BaseLoader {
    public abstract void load(String type, String path, JsonObject content);
}
