package com.fangsu.signItems;

import com.fangsu.scripting.G2dTextHelper;
import com.fangsu.scripting.TextUtil;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.io.IOException;

public class TextItem extends SignItem {
    private Color color;
    private String text;
    private ResourceLocation fontLocation;

    public TextItem(JsonObject json) {
        if (json.has("color") && json.get("color").isJsonPrimitive()) {
            color = Color.decode(json.get("color").getAsString());
        } else color = Color.BLACK;
        if (json.has("text") && json.get("text").isJsonPrimitive()) {
            text = json.get("text").getAsString();
        } else text = "文本|text";
        if (json.has("font") && json.get("font").isJsonPrimitive()) {
            fontLocation = new ResourceLocation(json.get("font").getAsString());
        } else fontLocation = new ResourceLocation("fangsu:fonts/source-han-sans.otf");
    }

    @Override
    protected JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        json.addProperty("color", color.getRGB());
        json.addProperty("text", text);
        json.addProperty("font", fontLocation.getPath());
        return json;
    }

    @Override
    public String getType() {
        return "str";
    }

    @Override
    public float getWidth(Graphics2D g, float unit) {
        String[] lines = TextUtil.getNonExtraParts(text).split("\\|");
        g.setColor(color);
        Font font;
        try {
            font = ResourceUtil.loadFont(fontLocation);
        } catch (IOException e) {
            font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        return G2dTextHelper.getMultiLinesWidth(g, font, (int) unit, lines);
    }

    @Override
    public void draw(SignDrawContext ctx) {
        String[] lines = TextUtil.getNonExtraParts(text).split("\\|");
        Graphics2D g = ctx.graphics();
        float u = ctx.unit();
        g.setColor(color);
        Font font;
        try {
            font = ResourceUtil.loadFont(fontLocation);
        } catch (IOException e) {
            font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        G2dTextHelper.drawStrMultiLines(g, font, (int) ctx.x(), (int) ((int) ctx.y() + u), (int) u, ctx.align(), lines);
    }

    @Override
    public ResourceLocation getIconLocation() {
        return new ResourceLocation("fangsu:sign/texts.png");
    }
}
