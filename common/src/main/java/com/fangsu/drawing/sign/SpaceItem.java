package com.fangsu.drawing.sign;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigSpec;
import com.fangsu.extraConfig.NumberInputConfig;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.util.List;

public class SpaceItem extends SignItem {
    private float length = 1f;

    public SpaceItem(JsonObject json) {
        if (json.has("length") && json.get("length").isJsonPrimitive()) {
            length = json.getAsJsonPrimitive("length").getAsFloat();
        } else length = 1f;
    }

    @Override
    protected JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        json.addProperty("length", length);
        return json;
    }

    @Override
    public String getType() {
        return "space";
    }

    @Override
    public float getWidth(Graphics2D g, float unit) {
        return Math.max(length, 0.1f) * unit;
    }

    @Override
    public void draw(SignDrawContext ctx) {

    }

    @Override
    public ResourceLocation getIconLocation() {
        return new ResourceLocation("fangsu:textures/signitems/space.png");
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        return List.of(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.common.length"),
                new ConfigSpec("num").setParam("min", new com.google.gson.JsonPrimitive(0.1f)).setParam("max", new com.google.gson.JsonPrimitive(8f)),
                () -> length,
                v -> length = v
        ));
    }
}
