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
        registerContent("duanmen", DuanmenContent.DuanmenLoader::load);
        registerContent("route_info_sign", RouteInfoSignContent.RouteInfoSignLoader::load);
        registerContent("adv_board", AdvBoardContent.AdvBoardLoader::load);
        registerContent("station_info_sign", StationInfoSignContent.StationInfoSignLoader::load);
        registerContent("screendoor", (type, path, content) -> {
            ScreendoorDoorContent.ScreendoorDoorLoader.load(type, path, content);
            ScreendoorGlassContent.ScreendoorGlassLoader.load(type, path, content);
        });
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
        if (itemObject.keySet().isEmpty()) {
            Main.LOGGER.warn("Failed to load content {}({}): JSON object has no keys (resource may not be available)", type, path);
            return;
        }
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

    public boolean canLoadType(String type) {
        return loaders.containsKey(type);
    }

    public <T extends BaseContent> T getContentById(String type, String path, String id, Class<T> clazz) {
        Map<String, List<BaseContent>> typeMap = contents.get(type);
        if (typeMap == null) return null;
        List<BaseContent> contentList = typeMap.get(path);
        if (contentList == null) return null;
        for (BaseContent content : contentList) {
            if (content != null && id.equals(content.getId()) && clazz.isInstance(content)) {
                return clazz.cast(content);
            }
        }
        return null;
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
        void loadContent(String type, String path, JsonObject content);
    }
}
