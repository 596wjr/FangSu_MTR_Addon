package com.fangsu.drawing.sign;

import com.google.gson.JsonObject;
import com.fangsu.mappings.ResourceLocation;

import java.awt.*;

public class UnknownItem extends SignItem {
    private final JsonObject oriJson;

    public UnknownItem(JsonObject json) {
        oriJson = json;
    }

    @Override
    protected JsonObject saveToJson() {
        return oriJson;
    }

    @Override
    public String getType() {
        return "unknown";
    }

    @Override
    public float getWidth(Graphics2D g, float unit) {
        return unit;
    }

    @Override
    public void draw(SignDrawContext ctx) {
        Graphics2D g = ctx.graphics();
        int x = Math.round(ctx.x());
        int y = Math.round(ctx.y());
        int u = Math.round(ctx.unit());
        g.setColor(new Color(34, 34, 34));
        g.fillRect(x, y, u, u);
        int bar = Math.max(2, u / 5);
        g.setColor(new Color(0, 0, 0));
        g.fillRect(x, y, u, bar);
        g.fillRect(x, y + u - bar, u, bar);
        g.fillRect(x, y, bar, u);
        g.fillRect(x + u - bar, y, bar, u);
        g.setColor(new Color(255, 90, 255));
        g.fillRect(x + bar, y + bar, Math.max(1, u - bar * 2), Math.max(1, u - bar * 2));
    }

    @Override
    public ResourceLocation getIconLocation() {
        return new ResourceLocation("mtrsteamloco:imgnotfound.png");
    }
}
