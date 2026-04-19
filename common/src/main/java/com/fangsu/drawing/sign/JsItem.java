package com.fangsu.drawing.sign;

import com.fangsu.userScripts.ScriptHolderBase;
import com.fangsu.userScripts.ScriptManager;
import com.fangsu.userScripts.SignItemScriptHolder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.graalvm.polyglot.Value;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class JsItem extends SignItem {
    private final String id;
    private final ResourceLocation scriptLocation;
    private ScriptHolderBase scriptHolder;

    private float width = 1f;
    private boolean widthInit = false;

    private Map<String, Value> extra;

    public JsItem(String id, ResourceLocation scriptLocation, JsonObject json) {
        super();
        this.id = id;
        this.scriptLocation = scriptLocation;

        ScriptManager scriptManager = ScriptManager.getInstance();
        this.scriptHolder = scriptManager.getOrInitHolder(this.scriptLocation, SignItemScriptHolder::new);

        extra = new HashMap<>();
    }

    @Override
    protected JsonObject saveToJson() {
        return null;
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

        scriptManager.requestRunFunction(scriptHolder, "draw", g, x, y, unit, align, extra);
    }

    @Override
    public ResourceLocation getIconLocation() {
        return null;
    }
}
