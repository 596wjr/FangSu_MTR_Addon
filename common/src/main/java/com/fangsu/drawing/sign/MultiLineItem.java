package com.fangsu.drawing.sign;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mappings.ResourceLocation;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigSpec;
import com.fangsu.extraConfig.EnumConfig;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MultiLineItem extends LayoutItem {

    private static final List<String> LANE_KEYS = List.of("top", "bottom");

    // 每行独立的水平对齐：0 左对齐 1 居中 2 右对齐
    private int topAlign = 0;
    private int bottomAlign = 0;

    public MultiLineItem(JsonObject json) {
        super(json);
        if (json.has("topAlign") && json.get("topAlign").isJsonPrimitive()) {
            topAlign = json.get("topAlign").getAsInt();
        }
        if (json.has("bottomAlign") && json.get("bottomAlign").isJsonPrimitive()) {
            bottomAlign = json.get("bottomAlign").getAsInt();
        }
    }

    @Override
    public List<String> getLaneKeys() {
        return LANE_KEYS;
    }

    @Override
    public String getType() {
        return "multiline";
    }

    @Override
    protected JsonObject saveToJson() {
        JsonObject json = super.saveToJson();
        json.addProperty("topAlign", topAlign);
        json.addProperty("bottomAlign", bottomAlign);
        return json;
    }

    @Override
    public float getWidth(Graphics2D g, float unit) {
        float inner = Math.max(getLaneWidth(g, "top", unit), getLaneWidth(g, "bottom", unit));
        return Math.max(unit, inner + unit * 0.25f);
    }

    @Override
    public void draw(SignDrawContext ctx) {
        Graphics2D g = ctx.graphics();
        float padding = ctx.unit() * 0.125f;
        float laneUnit = ctx.unit() / 2f;
        float topY = ctx.y();
        float bottomY = ctx.y() + laneUnit;
        float width = getWidth(g, ctx.unit());

        drawAlignedLane(ctx, "top", topY, laneUnit, width, padding, topAlign);
        drawAlignedLane(ctx, "bottom", bottomY, laneUnit, width, padding, bottomAlign);

        if (ctx.selected()) {
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

    /**
     * 按该行配置的对齐方式绘制一行内容（水平对齐在 MultiLineItem 的整个宽度内计算）。
     */
    private void drawAlignedLane(SignDrawContext ctx, String laneKey, float y, float laneUnit, float width, float padding, int align) {
        float laneW = getLaneWidth(ctx.graphics(), laneKey, ctx.unit());
        float startX;
        switch (align) {
            case 1:
                startX = ctx.x() + (width - laneW) / 2f;   // 居中
                break;
            case 2:
                startX = ctx.x() + width - padding - laneW; // 右对齐
                break;
            default:
                startX = ctx.x() + padding;                  // 左对齐
                break;
        }
        drawLane(ctx, laneKey, startX, y, laneUnit, ctx.selected());
    }

    @Override
    public ResourceLocation getIconLocation() {
        return new ResourceLocation("fangsu:textures/signitems/multi_line.png");
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        configs.add(alignConfig(
                ComponentHelper.translatable("ui.fangsu.sign.layout_top_align"),
                () -> topAlign,
                (v) -> topAlign = v));
        configs.add(alignConfig(
                ComponentHelper.translatable("ui.fangsu.sign.layout_bottom_align"),
                () -> bottomAlign,
                (v) -> bottomAlign = v));
        return configs;
    }

    private static EnumConfig alignConfig(Component title, Supplier<Integer> getter, Consumer<Integer> setter) {
        return new EnumConfig(
                title,
                new ConfigSpec("list"),
                List.of(
                        ComponentHelper.translatable("ui.fangsu.common.alignLeft"),
                        ComponentHelper.translatable("ui.fangsu.common.alignCenter"),
                        ComponentHelper.translatable("ui.fangsu.common.alignRight")
                ),
                getter,
                setter);
    }
}
