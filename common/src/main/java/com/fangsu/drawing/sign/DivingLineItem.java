package com.fangsu.drawing.sign;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.extraConfig.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.util.ArrayList;

public class DivingLineItem extends SignItem {
    private Color color;

    public DivingLineItem(JsonObject json) {
        if (json.has("color") && json.get("color").isJsonPrimitive()) {
            color = Color.decode(json.get("color").getAsString());
        } else color = Color.WHITE;
    }

    @Override
    protected JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        json.addProperty("color", color.getRGB());
        return json;
    }

    @Override
    public String getType() {
        return "diving_line";
    }

    @Override
    public float getWidth(Graphics2D g, float unit) {
        return unit * 0.2f;
    }

    @Override
    public void draw(SignDrawContext ctx) {
        Graphics2D g = ctx.graphics();
        float x = ctx.x();
        float y = ctx.y();
        float u = ctx.unit();

        g.setColor(color);
        g.fillRect((int) (x + u * 0.075f), (int) y, (int) (u * 0.05f), (int) u);
    }

    @Override
    public java.util.List<ConfigEntry<?>> getConfigs() {
        java.util.List<ConfigEntry<?>> configs = new ArrayList<>();
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.common.color"),
                new ConfigSpec("num").setParam("isHex", new JsonPrimitive(true)).setParam("isInt", new JsonPrimitive(true)),
                () -> (this.color.getRGB() & 0xFFFFFF) + 0f,
                (v) -> this.color = new Color(v.intValue() | 0xFF000000)
        ));
        return configs;
    }

    @Override
    public ResourceLocation getIconLocation() {
        return new ResourceLocation("fangsu:textures/signitems/diving_line.png");
    }
}
