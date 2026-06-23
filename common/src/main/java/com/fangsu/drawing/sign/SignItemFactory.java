package com.fangsu.drawing.sign;

import com.fangsu.Main;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public final class SignItemFactory {
    private static final ResourceLocation SIGN_LOCATION = new ResourceLocation("fangsu:sign/script_sign.json");

    private static final Map<String, Function<JsonObject, SignItem>> REGISTRY = new HashMap<>();
    public static final List<SignItem> EDITOR_ITEMS = new ArrayList<>();

    private SignItemFactory() {
    }

    static {
        REGISTRY.put("str", TextItem::new);
        REGISTRY.put("img", ImageItem::new);
        REGISTRY.put("space", SpaceItem::new);
        REGISTRY.put("multiline", MultiLineItem::new);
        REGISTRY.put("diving_line", DivingLineItem::new);
        REGISTRY.put("route", RouteItemA::new);
        REGISTRY.put("routeb", RouteItemB::new);
        REGISTRY.put("destination", DestinationItem::new);
        REGISTRY.put("trainicon", TrainIconItem::new);
    }

    public static Function<JsonObject, SignItem> get(String type) {
        Function<JsonObject, SignItem> item = REGISTRY.get(type);
        if (item == null) {
            return UnknownItem::new;
        }
        return item;
    }

    private static void registerBuiltInSign() {
        EDITOR_ITEMS.add(new DivingLineItem(new JsonObject()));
    }

    public static boolean has(String type) {
        return REGISTRY.containsKey(type);
    }

    public static void init() {
        registerBuiltInSign();
        registerJsItems();
        JsonElement builtInSign = ResourceUtil.simpleLoadAsJson(new ResourceLocation("fangsu:sign/builtin_sign.json"));
        if (builtInSign.isJsonObject()) {
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
        Map<String, SignItem> mtrItems = getMtrItems(mtrItem);
        for (Map.Entry<String, SignItem> entry : mtrItems.entrySet()) {
            SignItem current = entry.getValue();
            EDITOR_ITEMS.add(current);
        }
    }

    private static @NotNull Map<String, SignItem> getMtrItems(JsonElement mtrJson) {
        Map<String, SignItem> mtrItems = new HashMap<>();
        if (mtrJson == null || !mtrJson.isJsonObject()) {
            Main.LOGGER.warn("[FangSu] MTR custom_resources.json is not available or not a JSON object");
            return mtrItems;
        }
        JsonObject obj = mtrJson.getAsJsonObject();
        if (!obj.has("custom_signs")) {
            Main.LOGGER.info("[FangSu] MTR custom_resources.json has no custom_signs section");
            return mtrItems;
        }
        JsonElement customSigns = obj.get("custom_signs");
        if (customSigns.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : customSigns.getAsJsonObject().entrySet()) {
                try {
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject itemObject = entry.getValue().getAsJsonObject();
                    if (!itemObject.has("texture_id") || !itemObject.get("texture_id").isJsonPrimitive()) continue;
                    String texture_id = itemObject.getAsJsonPrimitive("texture_id").getAsString();
                    String custom_text = itemObject.has("custom_text") ? itemObject.getAsJsonPrimitive("custom_text").getAsString() : null;
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
                } catch (Exception e) {
                    Main.LOGGER.warn("[FangSu] Failed to parse MTR custom sign entry: {}", entry.getKey(), e);
                }
            }
        } else if (customSigns.isJsonArray()) {
            JsonArray array = customSigns.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                try {
                    JsonElement item = array.get(i);
                    if (!item.isJsonObject()) continue;
                    JsonObject itemObject = item.getAsJsonObject();
                    if (!itemObject.has("texture_id") || !itemObject.get("texture_id").isJsonPrimitive()) continue;
                    String texture_id = itemObject.getAsJsonPrimitive("texture_id").getAsString();
                    String custom_text = itemObject.has("custom_text") ? itemObject.getAsJsonPrimitive("custom_text").getAsString() : null;
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
                } catch (Exception e) {
                    Main.LOGGER.warn("[FangSu] Failed to parse MTR custom sign entry at index: {}", i, e);
                }
            }
        }
        Main.LOGGER.info("[FangSu] Loaded {} MTR custom sign icons", mtrItems.size());
        return mtrItems;
    }

    private static void registerJsItems() {
        JsonElement signJsonElement = ResourceUtil.loadAsJSON(SIGN_LOCATION);
        if (signJsonElement == null || !signJsonElement.isJsonObject()) return;
        JsonObject signJsonObject = signJsonElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : signJsonObject.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (!value.isJsonObject()) continue;
            JsonObject valueObject = value.getAsJsonObject();
            if (!valueObject.has("content")) continue;
            String finalKey = "JS_" + key;
            String content = valueObject.getAsJsonPrimitive("content").getAsString();
            ResourceLocation icon = valueObject.has("icon") ? new ResourceLocation(valueObject.getAsJsonPrimitive("icon").getAsString()) : null;
            if (valueObject.has("extraConfig")) {
                List<JsonObject> configs = new ArrayList<>();
                JsonElement extraConfig = valueObject.get("extraConfig");
                if (extraConfig.isJsonArray()) {
                    for (JsonElement item : extraConfig.getAsJsonArray()) {
                        if (!item.isJsonObject()) continue;
                        configs.add(item.getAsJsonObject());
                    }
                } else if (extraConfig.isJsonPrimitive()) {
                    String config = extraConfig.getAsJsonPrimitive().getAsString();
                    if ("route".equals(config)) {
                        JsonObject configObject = new JsonObject();
                        configObject.addProperty("type", "route");
                        configObject.addProperty("savePos", "route");
                        configs.add(configObject);
                    } else if ("destination".equals(config)) {
                        JsonObject configObject = new JsonObject();
                        configObject.addProperty("type", "destination");
                        configObject.addProperty("savePos", "destination");
                        configs.add(configObject);
                    }
                }
                REGISTRY.put(finalKey, json -> new JsItem(finalKey, new ResourceLocation(content), icon, configs, json));
                EDITOR_ITEMS.add(new JsItem(finalKey, new ResourceLocation(content), icon, configs, new JsonObject()));
            } else {
                REGISTRY.put(finalKey, json -> new JsItem(finalKey, null, icon, null, json));
                EDITOR_ITEMS.add(new JsItem(finalKey, null, icon, null, new JsonObject()));
            }
        }
    }
}
