package com.fangsu.signItems;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;

public class SpaceItem extends SignItem {
    private float length;

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
        return length;
    }

    @Override
    public void draw(SignDrawContext ctx) {

    }

    @Override
    public ResourceLocation getIconLocation() {
        return new ResourceLocation("fangsu:textures/signitems/space.png");
    }
}
