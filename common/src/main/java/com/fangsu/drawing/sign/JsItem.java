package com.fangsu.drawing.sign;

import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.scripting.JsHelper;
import com.fangsu.userScripts.ScriptHolderBase;
import com.fangsu.userScripts.ScriptManager;
import com.fangsu.userScripts.SignItemScriptHolder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.graalvm.polyglot.Value;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsItem extends SignItem {
    private final String id;
    private final ResourceLocation scriptLocation;
    private final ResourceLocation iconLocation;
    private final List<JsonObject> configs;
    private ScriptHolderBase scriptHolder;

    private float width = 1f;
    private boolean widthInit = false;

    private boolean isReady = false;
    private boolean isCompleted = false;

    private Map<String, Value> extra;

    public JsItem(String id, ResourceLocation scriptLocation, List<JsonObject> configs, JsonObject json) {
        this(id, scriptLocation, null, configs, json);
    }

    public JsItem(String id, ResourceLocation scriptLocation, ResourceLocation iconLocation, List<JsonObject> configs, JsonObject json) {
        super();
        this.id = id;
        this.scriptLocation = scriptLocation;
        this.iconLocation = iconLocation;
        this.configs = configs;

        ScriptManager scriptManager = ScriptManager.getInstance();
        this.scriptHolder = scriptManager.getOrInitHolder(this.scriptLocation, SignItemScriptHolder::new);

        extra = new HashMap<>();
        if (json.has("extra") && json.get("extra").isJsonObject()) {
            JsonObject extraJson = json.get("extra").getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : extraJson.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                extra.put(key, JsHelper.toValue(value));
            }
        }
    }

    @Override
    protected JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Value> entry : extra.entrySet()) {
            String key = entry.getKey();
            Value value = entry.getValue();
            json.add(key, JsHelper.toJsonElement(value));
        }
        return json;
    }

    @Override
    public String getType() {
        return id;
    }

    @Override
    public float getWidth(Graphics2D g, float unit) {
        if (!widthInit) {
            ScriptManager scriptManager = ScriptManager.getInstance();
            scriptManager.requestRunFunctionWithResult(scriptHolder, v -> {
                width = v.asFloat();
                isReady = true;
            }, "getWidth", g, unit, extra);
            widthInit = true;
        }
        return width;
    }

    @Override
    public void draw(SignDrawContext ctx) {
        ScriptManager scriptManager = ScriptManager.getInstance();
        Graphics2D g = ctx.graphics();
        float x = ctx.x();
        float y = ctx.y();
        float unit = ctx.unit();
        int align = ctx.align();

        scriptManager.requestRunFunctionWithCallback(scriptHolder, () -> {
            isCompleted = true;
        }, "draw", g, x, y, unit, align, extra);
    }

    @Override
    public ResourceLocation getIconLocation() {
        return iconLocation;
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        return super.getConfigs();
    }

    @Override
    public boolean isReady() {
        return isReady;
    }

    @Override
    public boolean isCompleted() {
        return isCompleted;
    }
}
