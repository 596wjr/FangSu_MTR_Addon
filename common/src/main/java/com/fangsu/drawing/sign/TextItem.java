package com.fangsu.drawing.sign;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.extraConfig.*;
import com.fangsu.scripting.G2dTextHelper;
import com.fangsu.scripting.TextUtil;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.network.chat.Component;
import com.fangsu.mappings.ResourceLocation;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TextItem extends SignItem {
    private Color color;
    private String text;
    private ResourceLocation fontLocation;
    private int align;

    public TextItem(JsonObject json) {
        if (json.has("color") && json.get("color").isJsonPrimitive()) {
            color = Color.decode(json.get("color").getAsString());
        } else color = Color.WHITE;
        if (json.has("text") && json.get("text").isJsonPrimitive()) {
            text = json.get("text").getAsString();
        } else text = "文本|text";
        if (json.has("font") && json.get("font").isJsonPrimitive()) {
            fontLocation = new ResourceLocation(json.get("font").getAsString());
        } else fontLocation = new ResourceLocation("fangsu:fonts/source-han-sans.otf");
        if (json.has("align") && json.get("align").isJsonPrimitive()) {
            align = json.get("align").getAsInt();
        } else align = 1;
    }

    @Override
    protected JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        json.addProperty("color", color.getRGB());
        json.addProperty("text", text);
        json.addProperty("font", fontLocation.toString());
        json.addProperty("align", align);
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
        font = ResourceUtil.loadFont(fontLocation);
        return G2dTextHelper.getMultiLinesWidth(g, font, (int) unit, lines);
    }

    @Override
    public void draw(SignDrawContext ctx) {
        String[] lines = TextUtil.getNonExtraParts(text).split("\\|");
        Graphics2D g = ctx.graphics();
        float u = ctx.unit();
        g.setColor(color);
        Font font;
        font = ResourceUtil.loadFont(fontLocation);
        G2dTextHelper.drawStrMultiLines(g, font, (int) ctx.x(), (int) ctx.y() - (int) u, (int) u, align, lines);
    }

    @Override
    public ResourceLocation getIconLocation() {
        return new ResourceLocation("fangsu:sign/texts.png");
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        configs.add(new StringConfig(
                ComponentHelper.translatable("ui.fangsu.common.text"),
                new ConfigSpec("str"),
                () -> this.text,
                (v) -> this.text = v
        ));
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.common.color"),
                new ConfigSpec("num").setParam("isHex", new JsonPrimitive(true)).setParam("isInt", new JsonPrimitive(true)),
                () -> (this.color.getRGB() & 0xFFFFFF) + 0f,
                (v) -> this.color = new Color(v.intValue() | 0xFF000000)
        ));
        configs.add(new EnumConfig(
                ComponentHelper.translatable("ui.fangsu.common.align"),
                new ConfigSpec("list"),
                List.of(
                        ComponentHelper.translatable("ui.fangsu.common.alignLeft"),
                        ComponentHelper.translatable("ui.fangsu.common.alignCenter"),
                        ComponentHelper.translatable("ui.fangsu.common.alignRight")
                ),
                () -> this.align,
                (v) -> this.align = v
        ));
        configs.add(new StringConfig(
                ComponentHelper.translatable("ui.fangsu.common.fontLocation"),
                new ConfigSpec("str"),
                () -> this.fontLocation.toString(),
                (v) -> this.fontLocation = new ResourceLocation(v)
        ));
        return configs;
    }

    public TextItem setAlign(int align) {
        this.align = align;
        return this;
    }
}
