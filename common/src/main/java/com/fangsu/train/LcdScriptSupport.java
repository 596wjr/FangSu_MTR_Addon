package com.fangsu.train;

import com.fangsu.userScripts.LcdScriptHolder;
import com.fangsu.userScripts.ScriptHolderBase;
import com.fangsu.userScripts.ScriptManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 列车 LCD 的 JS 脚本支撑：holder 获取 + 脚本可见参数（info/extraConfig）构建。
 * 双版本（MTR3/MTR4）内容一致，仅包路径差异。
 * <p>
 * 脚本入口契约（与 PIDS 脚本同风格）：
 * function draw(g, state, trainStatus, info, extraConfig)
 * g            : java.awt.Graphics2D（整张 LCD 纹理，本次调用需画满）
 * state        : 每列车持久的状态 Map（跨帧）
 * trainStatus  : TrainStatus（公共字段/方法直读）
 * info         : { id, script, texSize: [w,h], slots: [{name, texArea, pos, offsets...}] }
 * extraConfig  : lcd.extraConfig 配置（缺省 {}）
 */
public final class LcdScriptSupport {

    private LcdScriptSupport() {
    }

    /**
     * 获取/创建脚本 holder（同脚本路径全局共享）。
     * ScriptManager 未初始化（纯服务端等场景）时返回 null，调用方需判空。
     */
    public static ScriptHolderBase getHolder(String scriptPath) {
        return ScriptManager.getInstance().getOrInitHolder(new ResourceLocation(scriptPath), LcdScriptHolder::new);
    }

    /**
     * 构建脚本可见的 info 对象。
     */
    public static Map<String, Object> buildInfo(LcdInfo lcdInfo) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", lcdInfo.id());
        if (lcdInfo.hasScript()) {
            info.put("script", lcdInfo.script());
        }
        JsonObject slots = lcdInfo.slotsInfo();
        if (slots != null) {
            if (slots.has("texSize")) {
                info.put("texSize", jsonToValue(slots.get("texSize")));
            }
            if (slots.has("slots")) {
                info.put("slots", jsonToValue(slots.get("slots")));
            }
        }
        return info;
    }

    /**
     * 构建脚本可见的 extraConfig（lcd.extraConfig），未配置时返回空 Map。
     */
    public static Map<String, Object> buildExtraConfig(LcdInfo lcdInfo) {
        JsonObject ec = lcdInfo.scriptExtraConfigJson();
        if (ec == null) {
            return new HashMap<>();
        }
        return (Map<String, Object>) jsonToValue(ec);
    }

    /**
     * JsonElement 深转为 JS 友好的 Object（Map/List/基本类型），
     * 行为对齐 MC 的 GsonHelper.asMap：数字整数转 int、布尔转 boolean、其余转字符串。
     */
    private static Object jsonToValue(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) {
            var prim = el.getAsJsonPrimitive();
            if (prim.isNumber()) {
                double d = prim.getAsDouble();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return (int) d;
                }
                return d;
            }
            if (prim.isBoolean()) {
                return prim.getAsBoolean();
            }
            return prim.getAsString();
        }
        if (el.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement e : el.getAsJsonArray()) {
                list.add(jsonToValue(e));
            }
            return list;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            map.put(e.getKey(), jsonToValue(e.getValue()));
        }
        return map;
    }
}
