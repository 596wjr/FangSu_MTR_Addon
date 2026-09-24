package com.fangsu.modular;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.List;

/**
 * 积木程序与 JSON 的互转编码器。
 * <p>文档 JSON 结构：
 * <pre>
 * { "scripts": [ { "x": .., "y": .., "blocks": [ block, ... ] }, ... ] }
 * block = { "type": "...",
 *           "parts": [ { "k":"LABEL","name":"..." },
 *                      { "k":"NUMBER","name":"..","v": <num>,
 *                        "expr": { ... 嵌套数据块 ... } },   // expr 可选
 *                      { "k":"CHOICE","name":"..","v":".." } ],
 *           "containers": { "slotName": [ block, ... ], ... } }
 * </pre>
 * 只存储业务数据（type + 值槽字面值/嵌套与容器）。形状、颜色、分组、下拉项、栈终止标记等
 * 模板信息不落盘，由工厂按 type 复制模板重建（见 {@link #blockFromJson}）。
 */
public final class ModularCodec {

    private ModularCodec() {
    }

    /* ====================== 序列化 ====================== */

    public static JsonObject blockToJson(ModularBlock block) {
        JsonObject obj = new JsonObject();
        // 只存业务数据：type 唯一标识 + 各值槽的字面值/嵌套。形状、颜色、分组、终止标记等均属模板信息，
        // 反序列化时由工厂按 type 复制模板重建，无需（也不应）存入文档。
        obj.addProperty("type", block.getType());

        JsonArray parts = new JsonArray();
        for (ModularComponent c : block.getComponentList()) {
            JsonObject pc = new JsonObject();
            pc.addProperty("k", c.getKind().name());
            pc.addProperty("name", c.getName());
            switch (c.getKind()) {
                case LABEL:
                    break;
                case NUMBER:
                    pc.add("v", new JsonPrimitive(c.asNumber()));
                    break;
                case TEXT:
                    pc.add("v", new JsonPrimitive(c.asString()));
                    break;
                case BOOLEAN:
                    pc.add("v", new JsonPrimitive(c.asBoolean()));
                    break;
                case CHOICE:
                    pc.add("v", new JsonPrimitive(c.asString()));
                    break;
                default:
                    break;
            }
            if (c.hasExpr()) {
                pc.add("expr", blockToJson(c.getExpr()));
            }
            parts.add(pc);
        }
        obj.add("parts", parts);

        if (!block.getContainers().isEmpty()) {
            JsonObject containers = new JsonObject();
            for (ContainerObject co : block.getContainers()) {
                JsonArray arr = new JsonArray();
                for (ModularBlock child : co.getBlockList()) arr.add(blockToJson(child));
                containers.add(co.getName(), arr);
            }
            obj.add("containers", containers);
        }
        return obj;
    }

    public static String documentToJson(ModularDocument doc) {
        JsonObject obj = new JsonObject();
        JsonArray scripts = new JsonArray();
        for (ModularScript s : doc.getScripts()) {
            JsonObject so = new JsonObject();
            so.addProperty("x", s.getX());
            so.addProperty("y", s.getY());
            JsonArray blocks = new JsonArray();
            for (ModularBlock b : s.getBlocks()) blocks.add(blockToJson(b));
            so.add("blocks", blocks);
            scripts.add(so);
        }
        obj.add("scripts", scripts);
        return obj.toString();
    }

    /* ====================== 反序列化 ====================== */

    public static ModularDocument documentFromJson(String jsonString) {
        ModularDocument doc = new ModularDocument();
        if (jsonString == null || jsonString.trim().isEmpty()) return doc;
        JsonObject obj = com.google.gson.JsonParser.parseString(jsonString).getAsJsonObject();
        if (obj.has("scripts") && obj.get("scripts").isJsonArray()) {
            for (JsonElement se : obj.getAsJsonArray("scripts")) {
                JsonObject so = se.getAsJsonObject();
                ModularScript script = new ModularScript(
                        so.has("x") ? so.get("x").getAsDouble() : 0,
                        so.has("y") ? so.get("y").getAsDouble() : 0);
                if (so.has("blocks") && so.get("blocks").isJsonArray()) {
                    for (JsonElement be : so.getAsJsonArray("blocks")) {
                        if (be.isJsonObject()) script.add(blockFromJson(be.getAsJsonObject()));
                    }
                }
                doc.addScript(script);
            }
        }
        return doc;
    }

    public static ModularBlock blockFromJson(JsonObject json) {
        ModularBlockFactory factory = ModularBlockFactory.getInstance();
        String type = json.get("type").getAsString();
        ModularBlock template = factory.getDefaultBlock(type);
        if (template == null) throw new IllegalStateException("Unknown ModularBlock type '" + type + "'");
        ModularBlock out = template.copy();

        // parts：按模板顺序对齐（编辑器/模板保证顺序稳定），这里按 name 覆写
        if (json.has("parts") && json.get("parts").isJsonArray()) {
            JsonArray parts = json.getAsJsonArray("parts");
            int idx = 0;
            for (ModularComponent c : out.getComponentList()) {
                if (idx >= parts.size()) break;
                JsonObject pc = parts.get(idx).getAsJsonObject();
                idx++;
                String pk = pc.has("k") ? pc.get("k").getAsString() : c.getKind().name();
                if (!pk.equals(c.getKind().name())) continue;
                switch (c.getKind()) {
                    case NUMBER:
                        if (pc.has("v")) c.setNumber(pc.get("v").getAsDouble());
                        break;
                    case TEXT:
                        if (pc.has("v")) c.setString(pc.get("v").getAsString());
                        break;
                    case BOOLEAN:
                        if (pc.has("v")) c.setBoolean(pc.get("v").getAsBoolean());
                        break;
                    case CHOICE:
                        if (pc.has("v")) c.setString(pc.get("v").getAsString());
                        break;
                    default:
                        break;
                }
                if (pc.has("expr") && pc.get("expr").isJsonObject()) {
                    c.setExpr(blockFromJson(pc.getAsJsonObject("expr")));
                }
            }
        }

        if (json.has("containers") && json.get("containers").isJsonObject()) {
            JsonObject containers = json.getAsJsonObject("containers");
            for (ContainerObject co : out.getContainers()) {
                co.mutableList().clear();
                JsonArray arr = containers.getAsJsonArray(co.getName());
                if (arr == null) continue;
                for (JsonElement child : arr) {
                    if (child.isJsonObject()) co.mutableList().add(blockFromJson(child.getAsJsonObject()));
                }
            }
        }
        return out;
    }

    /** 兼容旧入口：返回首个脚本的块（无脚本时为空）。 */
    public static List<ModularBlock> rootBlocks(String jsonString) {
        ModularDocument doc = documentFromJson(jsonString);
        if (doc.getScripts().isEmpty()) return List.of();
        return doc.getScripts().get(0).getBlocks();
    }
}
