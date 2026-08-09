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

    /** 是否配置了 JS 脚本（lcd.script 非空）。配置了脚本则优先走 JS 绘制路径 */
    public boolean hasScript() {
        return extra != null && extra.has("script") && !extra.get("script").getAsString().isBlank();
    }

    /** 脚本资源路径（ResourceLocation 字符串） */
    public String script() {
        return extra.get("script").getAsString();
    }

    /** lcd.extraConfig 配置对象，未配置时返回 null */
    public JsonObject scriptExtraConfigJson() {
        return extra != null && extra.has("extraConfig") ? extra.getAsJsonObject("extraConfig") : null;
    }
}
