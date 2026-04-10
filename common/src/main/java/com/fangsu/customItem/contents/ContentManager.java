package com.fangsu.customItem.contents;

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

public class ContentManager {
    private static final String CONTENT_PATH = "fangsu:custom_blocks.json";

    private static ContentManager instance = new ContentManager();
    private Map<String, Map<String, List<BaseContent>>> contents;
    private Map<String, ContentConstructor> constructors;

    public static ContentManager getInstance() {
        return instance;
    }

    private ContentManager() {
        contents = new HashMap<>();
        constructors = new HashMap<>();
    }

    public void reset() {
        contents.clear();
    }

    public Map<String, List<BaseContent>> getContent(String type) {
        if (contents.containsKey(type)) {
            return contents.get(type);
        }
        return null;
    }

    public void loadItem(String type, String path) {
        ResourceLocation pathLocation = new ResourceLocation(path);
        JsonElement itemElement = ResourceUtil.loadAsJSON(pathLocation);
        if (itemElement == null || !itemElement.isJsonObject()) {
            Main.LOGGER.warn("Failed to load content {}({}): JSON is null or empty", type, path);
            return;
        }
        long begin = System.currentTimeMillis();
        JsonObject itemObject = itemElement.getAsJsonObject();
        Map<String, List<BaseContent>> map = new HashMap<>();
        ContentConstructor constructor;
        if (constructors.containsKey(type)) constructor = constructors.get(type);
        else {
            Main.LOGGER.warn("Failed to load content {}({}): unknown type", type, path);
            return;
        }
        for (Map.Entry<String, JsonElement> entry : itemObject.entrySet()) {
            String entryKey = entry.getKey();
            JsonElement entryValue = entry.getValue();
            if (entryValue == null || !entryValue.isJsonArray()) {
                Main.LOGGER.warn("Failed to load content {} of {}({}): JSON is null or empty", entryKey, type, path);
                continue;
            }
            JsonArray entryArray = entryValue.getAsJsonArray();
            List<BaseContent> list = new ArrayList<>();
            for (int i = 0; i < entryArray.size(); i++) {
                JsonElement detailElement = entryArray.get(i);
                if (detailElement == null || !detailElement.isJsonObject()) {
                    Main.LOGGER.warn("Failed to load content index {} in {} of {}({}): JSON is null or empty", i, entryKey, type, path);
                    continue;
                }
                JsonObject detailObject = detailElement.getAsJsonObject();
                BaseContent content = constructor.createContent(detailObject);
                list.add(content);
            }
            map.put(type, list);
        }
        Main.LOGGER.debug("Loaded {} items of {}({}) in {} ms", map.size(), type, path, System.currentTimeMillis() - begin);
        contents.put(type, map);
    }

    public void registerContent(String type, ContentConstructor constructor) {
        constructors.put(type, constructor);
    }

    @FunctionalInterface
    public interface ContentConstructor {
        BaseContent createContent(JsonObject json);
    }
}
