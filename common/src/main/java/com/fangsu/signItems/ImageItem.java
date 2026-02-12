package com.fangsu.signItems;

import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.io.IOException;

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
        int align = ctx.align();
        if (image == null) {
            try {
                image = ResourceUtil.loadImage(imageLocation);
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
}
