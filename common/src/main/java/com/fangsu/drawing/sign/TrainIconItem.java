package com.fangsu.drawing.sign;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigSpec;
import com.fangsu.extraConfig.RunnableConfig;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.ui.RouteSelectionScreen;
import com.fangsu.utils.MtrUtil;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.fangsu.mappings.ResourceLocation;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TrainIconItem extends SignItem {
    private LocalRoute route;
    private static final ResourceLocation ICON_LOCATION = new ResourceLocation("fangsu:sign/trainicon.png");

    public TrainIconItem(JsonObject json) {
        if (json.has("route")) route = MtrUtil.getRouteById(json.get("route").getAsLong());
        else route = null;
    }

    @Override
    protected JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        if (route != null) json.addProperty("route", route.id);
        return json;
    }

    @Override
    public String getType() {
        return "trainicon";
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
        if (route != null) {
            g.setColor(new Color(route.color));
            g.fillRoundRect((int) (x), (int) y, (int) u, (int) u, (int) (u * 0.15), (int) (u * 0.15));
        }
        try {
            g.drawImage(ResourceUtil.loadImage(ICON_LOCATION.getRaw()), (int) x, (int) y, (int) u, (int) u, null);
        } catch (IOException ignored) {
        }
    }

    @Override
    public ResourceLocation getIconLocation() {
        return ICON_LOCATION;
    }

    @Override
    public java.util.List<ConfigEntry<?>> getConfigs() {
        java.util.List<ConfigEntry<?>> list = new ArrayList<ConfigEntry<?>>();
        list.add(new RunnableConfig(
                ComponentHelper.translatable("ui.fangsu.common.selectRoute"),
                new ConfigSpec("func"),
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.setScreen(new RouteSelectionScreen(
                                ComponentHelper.translatable("ui.fangsu.common.selectRoute"),
                                List.of(),
                                (v) -> {
                                    if (v != null && !v.isEmpty())
                                        route = v.get(0).route;
                                },
                                mc.player.getOnPos(), 1, Minecraft.getInstance().screen));
                    }
                }
        ));
        return list;
    }
}
