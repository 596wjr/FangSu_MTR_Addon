package com.fangsu.customItem;

import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class CustomItems {
    private static final String KEY_CUSTOM_ITEM_JSON = "fangsu:custom_blocks.json";

    public static Map<String, Map> items = new HashMap<String, Map>();
    private static JsonObject itemJson;

    public static void init() {
        JsonElement itemJsonElement = ResourceUtil.loadAsJSON(new ResourceLocation(KEY_CUSTOM_ITEM_JSON));
        if (itemJsonElement != null && !itemJsonElement.isJsonNull() && itemJsonElement.isJsonObject())
            itemJson = itemJsonElement.getAsJsonObject();

        //TODO 加载
    }
}
