package com.fangsu.signItems;

import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.Function;

public final class SignItemFactory {

    private static final Map<String, Function<JsonObject, SignItem>> REGISTRY = new HashMap<>();
    public static final List<SignItem> EDITOR_ITEMS = new ArrayList<>();

    private SignItemFactory() {
    }

    static {
        REGISTRY.put("str", TextItem::new);
        REGISTRY.put("img", ImageItem::new);
        REGISTRY.put("space", SpaceItem::new);
    }

    public static Function<JsonObject, SignItem> get(String type) {
        Function<JsonObject, SignItem> item = REGISTRY.get(type);
        if (item == null) {
            return UnknownItem::new;
        }
        return item;
    }

    public static boolean has(String type) {
        return REGISTRY.containsKey(type);
    }

    public static void init() {
        JsonElement builtInSign = ResourceUtil.loadAsJSON(new ResourceLocation("fangsu:sign/builtinsign.json"));
        if (builtInSign != null && builtInSign.isJsonObject()) {
            JsonObject obj = builtInSign.getAsJsonObject();
            if (obj.has("signItems") && obj.get("signItems").isJsonArray()) {
                JsonArray array = obj.get("signItems").getAsJsonArray();
                for (JsonElement item : array) {
                    if (!item.isJsonObject()) continue;
                    JsonObject itemObject = item.getAsJsonObject();
                    SignItem current = get(itemObject.getAsJsonPrimitive("type").getAsString()).apply(itemObject.getAsJsonObject("content"));
                    EDITOR_ITEMS.add(current);
                }
            }
        }
        JsonElement mtrItem = ResourceUtil.loadAsJSON(new ResourceLocation("mtr:mtr_custom_resources.json"));
        Map<String, SignItem> mtrItems = new HashMap<>();
        if (mtrItem != null && mtrItem.isJsonObject()) {
            JsonObject obj = mtrItem.getAsJsonObject();
            if (obj.has("custom_signs") && obj.get("custom_signs").isJsonArray()) {
                JsonArray array = obj.get("custom_signs").getAsJsonArray();
                for (JsonElement item : array) {
                    if (!item.isJsonObject()) continue;
                    JsonObject itemObject = item.getAsJsonObject();
                    String texture_id = itemObject.getAsJsonPrimitive("texture_id").getAsString();
                    String custom_text = itemObject.getAsJsonPrimitive("custom_text").getAsString();
                    if (mtrItems.containsKey(texture_id) && custom_text != null) {
                        SignItem current = mtrItems.get(texture_id);
                        current.setText(custom_text);
                    } else {
                        JsonObject json = new JsonObject();
                        json.addProperty("image", texture_id);
                        SignItem current = new ImageItem(json);
                        if (custom_text != null) current.setText(custom_text);
                        mtrItems.put(texture_id, current);
                    }
                }
            }
        }
        for (Map.Entry<String, SignItem> entry : mtrItems.entrySet()) {
            SignItem current = entry.getValue();
            EDITOR_ITEMS.add(current);
        }
    }
}
