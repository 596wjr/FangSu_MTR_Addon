package com.fangsu.drawing.diaoban;

import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class DiaobanDrawManager {
    private static final ResourceLocation SCRIPTS_LOCATION = new ResourceLocation("fangsu:diaoban/diaoban_scripts.json");

    private DiaobanDrawManager() {
    }

    public static List<ModelSelectInfo> getDrawOptions() {
        List<ModelSelectInfo> result = new ArrayList<>();
        injectScriptDrawers(result);
        return result;
    }

    private static void injectScriptDrawers(List<ModelSelectInfo> out) {
        try {
            JsonObject loaded = ResourceUtil.loadAsJSON(SCRIPTS_LOCATION).getAsJsonObject();
            if (!loaded.has("content")) return;
            JsonArray array = loaded.getAsJsonArray("content");
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                String text = item.get("text").getAsString();
                String content = item.get("content").getAsString();
                if (item.has("contentText")) {
                    out.add(new ModelSelectInfo(text, content, item.get("contentText").getAsString()));
                } else {
                    out.add(new ModelSelectInfo(text, content));
                }
            }
        } catch (Exception ignored) {
        }
    }
}
