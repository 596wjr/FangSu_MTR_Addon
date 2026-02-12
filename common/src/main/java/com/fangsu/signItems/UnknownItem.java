package com.fangsu.signItems;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

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
        return 1;
    }

    @Override
    public void draw(SignDrawContext ctx) {
        Graphics2D g = ctx.graphics();
        int x = (int) ctx.x();
        int y = (int) ctx.y();
        int align = ctx.align();
        int u = (int) ctx.unit();
        int baseX = x + (align == 2 ? -u : 0);
        g.setColor(Color.black);
        g.fillRect(baseX, y - u, u, u);
        g.setColor(new Color(255, 100, 100));
        g.drawRect(baseX, y - u, u / 2, u / 2);
        g.drawRect(baseX + u / 2, y - u / 2, u, u);
    }

    @Override
    public ResourceLocation getIconLocation() {
        return new ResourceLocation("mtrsteamloco:imgnotfound.png");
    }
}
