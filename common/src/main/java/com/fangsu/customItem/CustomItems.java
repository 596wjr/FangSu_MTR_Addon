package com.fangsu.customItem;

import com.fangsu.Main;
import com.fangsu.customItem.contents.ContentManager;
import com.fangsu.customItem.contents.ContentResourceLoader;
import com.fangsu.customItem.contents.ScreendoorGlassContent;
import com.fangsu.utils.ContentInfoUtil;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomItems {
    private static final String CONTENT_PATH = "fangsu:custom_blocks.json";

    private static final CustomItems instance = new CustomItems();
    public static Map<String, List<ModelSelectInfo>> items = new HashMap<>();

    public static CustomItems getInstance() {
        return instance;
    }

    private CustomItems() {

    }

    public void reset() {
        items.clear();
        ContentResourceLoader.reset();
        ScreendoorGlassContent.reset();
        ContentManager cm = ContentManager.getInstance();
        cm.reset();
    }

    public void init() {
        reset();

        JsonElement root = ResourceUtil.loadAsJSON(new ResourceLocation(CONTENT_PATH));
        if (root == null || !root.isJsonObject()) {
            Main.LOGGER.error("Failed to load contents: root JSON is null or not an object!");
            return;
        }
        JsonObject rootObj = root.getAsJsonObject();
        if (rootObj.keySet().isEmpty()) {
            Main.LOGGER.error("Failed to load contents: root JSON object is empty (resource '{}' may not be available)!", CONTENT_PATH);
            return;
        }
        long begin = System.currentTimeMillis();
        for (Map.Entry<String, JsonElement> entry : rootObj.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonArray()) {
                Main.LOGGER.warn("Failed to load contents: value is null or empty!");
                continue;
            }
            ContentManager cm = ContentManager.getInstance();
            long contentBegin = System.currentTimeMillis();
            List<ModelSelectInfo> thisItemInfo = getModelSelectInfos(value);
            items.put(key, thisItemInfo);
            if (cm.canLoadType(key)) {
                for (ModelSelectInfo modelSelectInfo : thisItemInfo) {
                    cm.loadItem(key, modelSelectInfo.getContent());
                }
            } else {
                ContentInfoUtil.preloadByType(key, thisItemInfo);
            }
            Main.LOGGER.debug("Loaded content {} in {} ms", key, System.currentTimeMillis() - contentBegin);
        }
        Main.LOGGER.info("Loaded {} items in {} ms", items.size(), System.currentTimeMillis() - begin);
    }

    private static @NotNull List<ModelSelectInfo> getModelSelectInfos(JsonElement value) {
        JsonArray contentArray = value.getAsJsonArray();
        List<ModelSelectInfo> thisItemInfo = new ArrayList<>();
        for (int i = 0; i < contentArray.size(); i++) {
            JsonElement detailElement = contentArray.get(i);
            if (detailElement == null || !detailElement.isJsonObject()) continue;
            JsonObject detailObj = detailElement.getAsJsonObject();

            String text = detailObj.get("text").getAsString();
            String content = detailObj.get("content").getAsString();
            String contentText = detailObj.has("contentText") ? detailObj.get("contentText").getAsString() : null;
            if (contentText == null || contentText.isEmpty())
                thisItemInfo.add(new ModelSelectInfo(text, content));
            else thisItemInfo.add(new ModelSelectInfo(text, content, contentText));
        }
        return thisItemInfo;
    }

    public static Map<String, Object> getContentInfo(String mainModel, String nestedKeyPath, String subModel) {
        Map<String, Map<String, Object>> loaded = ContentResourceLoader.loadMapByPath(new ResourceLocation(mainModel), nestedKeyPath);
        if (loaded == null || !loaded.containsKey(subModel)) {
            return null;
        }
        return loaded.get(subModel);
    }

    public static List<ModelSelectInfo> getModelSelectInfos(String mainModel, String nestedKeyPath) {
        return ContentResourceLoader.loadModelSelectInfos(new ResourceLocation(mainModel), nestedKeyPath);
    }
}
