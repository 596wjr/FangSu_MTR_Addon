package com.fangsu.train;

import com.fangsu.Main;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

public record LcdInfo(String id, JsonObject slotsInfo, JsonObject extra) {
    public static LcdInfo fromJson(JsonObject json) throws Exception {
        if (!json.has("id") || !json.has("slots")) throw new IllegalArgumentException();
        String id = json.get("id").getAsString();
        String slots = json.get("slots").getAsString();
        ResourceLocation slotsLocation = new ResourceLocation(slots);
        JsonObject slotsJson = Main.JSON_PARSER.parse(ResourceUtil.loadString(slotsLocation)).getAsJsonObject();
        return new LcdInfo(id, slotsJson, json);
    }
}
