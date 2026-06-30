package com.fangsu.drawing.sign;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigSpec;
import com.fangsu.extraConfig.NumberInputConfig;
import com.fangsu.extraConfig.StringConfig;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import com.fangsu.mappings.ResourceLocation;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImageItem extends SignItem {
    private ResourceLocation imageLocation;
    private float scale;

    private Image image;

    public ImageItem(JsonObject json) {
        if (json.has("img") && json.get("img").isJsonPrimitive()) {
            this.imageLocation = new ResourceLocation(json.get("img").getAsString());
        }
        if (json.has("image") && json.get("image").isJsonPrimitive()) {
            this.imageLocation = new ResourceLocation(json.get("image").getAsString());
        }
        if (json.has("scale") && json.get("scale").isJsonPrimitive()) {
            this.scale = json.get("scale").getAsFloat();
        } else scale = 1f;
        if (json.has("text") && json.get("text").isJsonPrimitive()) {
            this.text = json.get("text").getAsString();
            this.withText = true;
        }
    }

    @Override
    protected JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        if (imageLocation != null) {
            json.addProperty("image", imageLocation.toString());
        }
        json.addProperty("scale", scale);
        return json;
    }

    @Override
    public String getType() {
        return "img";
    }

    @Override
    public float getWidth(Graphics2D g, float unit) {
        return unit;
    }

    @Override
    public void draw(SignDrawContext ctx) {
        Graphics2D g = ctx.graphics();
        float u = ctx.unit();
        float x = ctx.x();
        float y = ctx.y();
        float corner = u * (1 - scale) / 2;
        if (image == null) {
            try {
                image = ResourceUtil.loadImage(imageLocation.getRaw());
            } catch (IOException e) {
                image = null;
            }
        }
        if (image != null) {
            g.drawImage(image, (int) (x + corner), (int) (y + corner), (int) (u - 2 * corner), (int) (u - 2 * corner), null);
        }
    }

    @Override
    public ResourceLocation getIconLocation() {
        return imageLocation;
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        configs.add(new StringConfig(
                ComponentHelper.translatable("ui.fangsu.common.text"),
                new ConfigSpec("str"),
                () -> imageLocation == null ? "" : imageLocation.toString(),
                (v) -> {
                    if (v != null && !v.isBlank()) {
                        imageLocation = new ResourceLocation(v);
                        image = null;
                    }
                }
        ));
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.common.scale"),
                new ConfigSpec("num").setParam("min", new com.google.gson.JsonPrimitive(0.1f)).setParam("max", new com.google.gson.JsonPrimitive(2f)),
                () -> scale,
                v -> scale = v
        ));
        return configs;
    }
}
