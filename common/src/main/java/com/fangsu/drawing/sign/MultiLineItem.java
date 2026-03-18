package com.fangsu.drawing.sign;

import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.util.List;

public class MultiLineItem extends LayoutItem {

    private static final List<String> LANE_KEYS = List.of("top", "bottom");

    public MultiLineItem(com.google.gson.JsonObject json) {
        super(json);
    }

    @Override
    protected List<String> getLaneKeys() {
        return LANE_KEYS;
    }

    @Override
    public String getType() {
        return "multiline";
    }

    @Override
    public float getWidth(Graphics2D g, float unit) {
        float inner = Math.max(getLaneWidth(g, "top", unit), getLaneWidth(g, "bottom", unit));
        return Math.max(unit, inner + unit * 0.25f);
    }

    @Override
    public void draw(SignDrawContext ctx) {
        float padding = ctx.unit() * 0.125f;
        float laneUnit = ctx.unit() / 2f;
        float topY = ctx.y();
        float bottomY = ctx.y() + laneUnit;

        drawLane(ctx, "top", ctx.x() + padding, topY, laneUnit, ctx.selected());
        drawLane(ctx, "bottom", ctx.x() + padding, bottomY, laneUnit, ctx.selected());

        if (ctx.selected()) {
            Graphics2D g = ctx.graphics();
            Stroke original = g.getStroke();
            g.setColor(new Color(255, 255, 255, 180));
            float[] dash = new float[]{3f, 3f};
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 0));
            if (getLane("top").isEmpty()) {
                g.drawRect((int) (ctx.x() + padding), (int) topY, (int) (ctx.unit() - padding * 2), (int) laneUnit - 1);
            }
            if (getLane("bottom").isEmpty()) {
                g.drawRect((int) (ctx.x() + padding), (int) bottomY, (int) (ctx.unit() - padding * 2), (int) laneUnit - 1);
            }
            g.setStroke(original);
        }
    }

    @Override
    public ResourceLocation getIconLocation() {
        return new ResourceLocation("fangsu:textures/signitems/multi_line.png");
    }
}
