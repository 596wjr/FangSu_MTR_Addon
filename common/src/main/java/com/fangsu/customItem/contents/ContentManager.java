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

    private static final ContentManager instance = new ContentManager();
    private final Map<String, Map<String, List<BaseContent>>> contents;
    private final Map<String, ContentLoader> loaders;

    public static ContentManager getInstance() {
        return instance;
    }

    private ContentManager() {
        contents = new HashMap<>();
        loaders = new HashMap<>();

        registerContent("ticketBarrier", TicketBarrierContent.TicketBarrierLoader::load);
        registerContent("diaoban", DiaobanContent.DiaobanLoader::load);
        registerContent("pids", PidsContent.PidsLoader::load);
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
        JsonObject itemObject = itemElement.getAsJsonObject();
        ContentLoader loader;
        if (loaders.containsKey(type)) loader = loaders.get(type);
        else {
            Main.LOGGER.warn("Failed to load content {}({}): unknown type", type, path);
            return;
        }
        loader.loadContent(type, path, itemObject);
    }

    public void registerContent(String type, ContentLoader constructor) {
        loaders.put(type, constructor);
    }

    protected void addContent(String type, String path, BaseContent content) {
        if (contents == null) return;
        if (contents.containsKey(type)) {
            Map<String, List<BaseContent>> map = contents.get(type);
            if (map.containsKey(path)) {
                map.get(path).add(content);
            } else {
                List<BaseContent> list = new ArrayList<>();
                list.add(content);
                map.put(path, list);
            }
        } else {
            Map<String, List<BaseContent>> map = new HashMap<>();
            List<BaseContent> list = new ArrayList<>();
            list.add(content);
            map.put(path, list);
            contents.put(type, map);
        }
    }

    @FunctionalInterface
    public interface ContentLoader {
        public void loadContent(String type, String path, JsonObject content);
    }
}
