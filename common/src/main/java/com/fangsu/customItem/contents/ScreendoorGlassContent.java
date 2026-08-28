package com.fangsu.customItem.contents;

import com.fangsu.Main;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.BasicModelSelectInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScreendoorGlassContent extends BaseContent {
    private final String text;
    private final String contentText;
    private final String side;
    private final String subModel;
    private final List<List<Double>> shape;
    private final String auto;

    // 门灯配置（原生实现，替代旧版 JS script 注入）：doorlight: { model, subModel }
    private final String doorlightModel;
    private final String doorlightSubModel;

    // ── static storage: data per mainModel path ──
    private static final Map<String, List<JsonObject>> AUTO_COMMANDS = new HashMap<>();
    private static final Map<String, MainModelInfo> MAIN_MODEL_INFO = new HashMap<>();

    public static void reset() {
        AUTO_COMMANDS.clear();
        MAIN_MODEL_INFO.clear();
    }

    private ScreendoorGlassContent(JsonObject json, String side) {
        super(side + "." + json.get("id").getAsString());
        this.side = side;
        this.text = getOrDefault(json, "text", "", JsonElement::getAsString);
        this.contentText = getOrDefault(json, "contentText", (String) null, JsonElement::getAsString);
        this.subModel = getOrDefault(json, "subModel", (String) null, JsonElement::getAsString);
        this.auto = getOrDefault(json, "auto", (String) null, JsonElement::getAsString);
        this.shape = parseShapeFromJson(json);
        // 优先原生 doorlight 对象：{ "model": "...", "subModel": "..." }
        if (json.has("doorlight") && json.get("doorlight").isJsonObject()) {
            JsonObject dl = json.getAsJsonObject("doorlight");
            this.doorlightModel = getOrDefault(dl, "model", (String) null, JsonElement::getAsString);
            this.doorlightSubModel = getOrDefault(dl, "subModel", (String) null, JsonElement::getAsString);
        } else {
            // 兼容旧版 JSON：从 script 段识别 doorlight.js（仅此脚本有效，其余脚本一律忽略、不执行）
            String[] legacy = parseDoorlightFromLegacyScript(json);
            this.doorlightModel = legacy[0];
            this.doorlightSubModel = legacy[1];
        }
    }

    // ── 旧版 doorlight.js 兼容解析 ──

    private static final Pattern LEGACY_DL_MODEL_PATTERN = Pattern.compile("doorLightModel\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern LEGACY_DL_SUBMODEL_PATTERN = Pattern.compile("doorLightSubModel\\s*=\\s*\"([^\"]*)\"");

    /**
     * 兼容旧版 JSON 中直接嵌入的 script 段：仅当 scriptFile 以 doorlight.js 结尾时，
     * 从 scriptText 中提取 doorLightModel / doorLightSubModel 作为门灯配置。
     * 其余任何内嵌脚本（非 doorlight.js）都被视为无效并忽略，且不会执行任何 JS。
     *
     * @return [model, subModel]；未识别到有效 doorlight.js 时两者均为 null
     */
    private static String[] parseDoorlightFromLegacyScript(JsonObject json) {
        if (!json.has("script") || !json.get("script").isJsonArray()) return new String[]{null, null};
        for (JsonElement elem : json.getAsJsonArray("script")) {
            if (elem == null || !elem.isJsonObject()) continue;
            JsonObject script = elem.getAsJsonObject();
            if (!script.has("scriptFile")) continue;
            String scriptFile = script.get("scriptFile").getAsString();
            if (scriptFile == null || !scriptFile.endsWith("doorlight.js")) continue;
            String scriptText = script.has("scriptText") ? script.get("scriptText").getAsString() : "";
            String model = null, subModel = null;
            Matcher mm = LEGACY_DL_MODEL_PATTERN.matcher(scriptText);
            if (mm.find()) model = mm.group(1);
            Matcher sm = LEGACY_DL_SUBMODEL_PATTERN.matcher(scriptText);
            if (sm.find()) subModel = sm.group(1);
            return new String[]{model, subModel};
        }
        return new String[]{null, null};
    }

    // ── getters ──

    public String getText() {
        return text;
    }

    public String getContentText() {
        return contentText;
    }

    public String getSide() {
        return side;
    }

    public String getSubModel() {
        return subModel;
    }

    public List<List<Double>> getShape() {
        return shape;
    }

    public String getAuto() {
        return auto;
    }

    public String getDoorlightModel() {
        return doorlightModel;
    }

    public String getDoorlightSubModel() {
        return doorlightSubModel;
    }

    // ── static helpers for callers ──

    /** Build a Map&lt;id, Map&lt;field,value&gt;&gt; for left glass entries, compatible with old callers. */
    public static Map<String, Map<String, Object>> loadLeftEntries(String mainModel) {
        return loadEntriesBySide(mainModel, "left");
    }

    /** Build a Map&lt;id, Map&lt;field,value&gt;&gt; for right glass entries. */
    public static Map<String, Map<String, Object>> loadRightEntries(String mainModel) {
        return loadEntriesBySide(mainModel, "right");
    }

    private static Map<String, Map<String, Object>> loadEntriesBySide(String mainModel, String side) {
        Map<String, List<BaseContent>> map = ContentManager.getInstance().getContent("screendoor");
        if (map == null) return new HashMap<>();
        List<BaseContent> list = map.get(mainModel);
        if (list == null) return new HashMap<>();
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (BaseContent bc : list) {
            if (!(bc instanceof ScreendoorGlassContent gc)) continue;
            if (!side.equals(gc.side)) continue;
            String id = gc.getId().substring(side.length() + 1); // strip "left." / "right."
            Map<String, Object> entry = new HashMap<>();
            if (gc.text != null && !gc.text.isEmpty()) entry.put("text", gc.text);
            if (gc.contentText != null) entry.put("contentText", gc.contentText);
            if (gc.subModel != null) entry.put("subModel", gc.subModel);
            if (gc.shape != null && !gc.shape.isEmpty()) entry.put("shape", gc.shape);
            if (gc.auto != null) entry.put("auto", gc.auto);
            if (gc.doorlightModel != null) entry.put("doorlightModel", gc.doorlightModel);
            if (gc.doorlightSubModel != null) entry.put("doorlightSubModel", gc.doorlightSubModel);
            result.put(id, entry);
        }
        return result;
    }

    public static List<ModelSelectInfo> loadLeftModelSelectInfos(String mainModel) {
        return loadModelSelectInfosBySide(mainModel, "left");
    }

    public static List<ModelSelectInfo> loadRightModelSelectInfos(String mainModel) {
        return loadModelSelectInfosBySide(mainModel, "right");
    }

    private static List<ModelSelectInfo> loadModelSelectInfosBySide(String mainModel, String side) {
        List<ModelSelectInfo> infos = new ArrayList<>();
        Map<String, List<BaseContent>> map = ContentManager.getInstance().getContent("screendoor");
        if (map == null) return infos;
        List<BaseContent> list = map.get(mainModel);
        if (list == null) return infos;
        for (BaseContent bc : list) {
            if (!(bc instanceof ScreendoorGlassContent gc)) continue;
            if (!side.equals(gc.side)) continue;
            String id = gc.getId().substring(side.length() + 1); // strip prefix
            String contentText = gc.contentText != null ? gc.contentText : "";
            if (contentText.isEmpty())
                infos.add(new BasicModelSelectInfo(gc.text, id));
            else
                infos.add(new BasicModelSelectInfo(gc.text, id, contentText));
        }
        return infos;
    }

    public static List<JsonObject> loadAutoCommands(String mainModel, String autoKey) {
        List<JsonObject> commands = AUTO_COMMANDS.get(mainModel + "#" + autoKey);
        return commands != null ? commands : new ArrayList<>();
    }

    public static MainModelInfo loadMainModelInfo(String mainModel) {
        return MAIN_MODEL_INFO.get(mainModel);
    }

    // ── loader ──

    public static class ScreendoorGlassLoader {
        public static void load(String type, String path, JsonObject content) {
            ContentManager cm = ContentManager.getInstance();
            if (!content.has("glass") || !content.get("glass").isJsonObject()) return;
            JsonObject glass = content.getAsJsonObject("glass");

            // 1) parse entries under glass.left / glass.right
            for (String side : new String[]{"left", "right"}) {
                if (!glass.has(side) || !glass.get(side).isJsonArray()) continue;
                JsonArray arr = glass.getAsJsonArray(side);
                for (int i = 0; i < arr.size(); i++) {
                    JsonElement elem = arr.get(i);
                    if (elem == null || !elem.isJsonObject()) {
                        Main.LOGGER.warn("Failed to load screendoor glass entry index {} in {}: not an object", i, side);
                        continue;
                    }
                    cm.addContent(type, path, new ScreendoorGlassContent(elem.getAsJsonObject(), side));
                }
            }

            // 2) parse auto-command arrays under glass (e.g. glass.auto)
            for (Map.Entry<String, JsonElement> entry : glass.entrySet()) {
                String key = entry.getKey();
                if ("left".equals(key) || "right".equals(key)) continue;
                if (!entry.getValue().isJsonArray()) continue;
                JsonArray arr = entry.getValue().getAsJsonArray();
                List<JsonObject> commands = new ArrayList<>();
                for (int i = 0; i < arr.size(); i++) {
                    if (arr.get(i).isJsonObject()) commands.add(arr.get(i).getAsJsonObject());
                }
                if (!commands.isEmpty()) {
                    AUTO_COMMANDS.put(path + "#" + key, commands);
                }
            }

            // 3) parse root model / flipV
            if (content.has("model")) {
                String model = content.get("model").getAsString();
                boolean flipV = content.has("flipV") && content.get("flipV").getAsBoolean();
                MAIN_MODEL_INFO.put(path, new MainModelInfo(model, flipV));
            }
        }
    }

    public record MainModelInfo(String model, boolean flipV) {
    }

    // ── internal helpers ──

    private static List<List<Double>> parseShapeFromJson(JsonObject json) {
        if (!json.has("shape") || !json.get("shape").isJsonArray()) return null;
        JsonArray arr = json.getAsJsonArray("shape");
        List<List<Double>> result = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement elem = arr.get(i);
            if (!elem.isJsonArray()) continue;
            List<Double> inner = new ArrayList<>();
            for (int j = 0; j < elem.getAsJsonArray().size(); j++) {
                JsonElement v = elem.getAsJsonArray().get(j);
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                    inner.add(v.getAsDouble());
                }
            }
            if (!inner.isEmpty()) result.add(inner);
        }
        return result.isEmpty() ? Collections.emptyList() : result;
    }
}
