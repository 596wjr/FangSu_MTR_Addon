package com.fangsu.customItem;

import com.fangsu.Main;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomItems {
    private static final String KEY_CUSTOM_ITEM_JSON = "fangsu:custom_blocks.json";

    public static Map<String, List<ModelSelectInfo>> items = new HashMap<>();
    private static JsonObject itemJson;

    public static void init() {
        JsonElement itemJsonElement = ResourceUtil.loadAsJSON(new ResourceLocation(KEY_CUSTOM_ITEM_JSON));
        if (itemJsonElement != null && !itemJsonElement.isJsonNull() && itemJsonElement.isJsonObject()) {
            itemJson = itemJsonElement.getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : itemJson.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();

                if (!value.isJsonArray()) continue;
                JsonArray array = value.getAsJsonArray();
                List<ModelSelectInfo> thisItemInfo = new ArrayList<>();
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) continue;
                    JsonObject object = element.getAsJsonObject();
                    String text = object.get("text").getAsString();
                    String content = object.get("content").getAsString();
                    String contentText = object.has("contentText") ? object.get("contentText").getAsString() : null;
                    if (contentText == null || contentText.isEmpty())
                        thisItemInfo.add(new ModelSelectInfo(text, content));
                    else thisItemInfo.add(new ModelSelectInfo(text, content, contentText));
                }

                items.put(key, thisItemInfo);
            }
        }
    }
}
