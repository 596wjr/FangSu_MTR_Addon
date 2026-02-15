package com.fangsu.signItems;

import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigSpec;
import com.fangsu.extraConfig.RunnableConfig;
import com.fangsu.scripting.G2dTextHelper;
import com.fangsu.scripting.RouteNameUtil;
import com.fangsu.scripting.TextUtil;
import com.fangsu.ui.RouteSelectionScreen;
import com.fangsu.utils.ColorUtil;
import com.fangsu.utils.MtrUtil;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonObject;
import mtr.data.Route;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RouteItem extends SignItem {
    private Route route;
    private Font font;

    public RouteItem(JsonObject json) {
        if (json.has("route") && json.get("route").isJsonPrimitive()) {
            route = MtrUtil.getRouteById(json.getAsJsonPrimitive("route").getAsLong());
        } else route = null;
        font = ResourceUtil.loadFont(new ResourceLocation("fangsu:fonts/source-han-sans-bold.otf"));
    }

    @Override
    protected JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        if (route != null) {
            json.addProperty("route", route.id);
        }
        return json;
    }

    @Override
    public String getType() {
        return "route";
    }

    @Override
    public float getWidth(Graphics2D g, float unit) {
        String routeName = getRouteName();
        boolean isNumLine = RouteNameUtil.isNumLine(routeName);
        float width = unit * 0.6f;
        if (isNumLine) {
            String name = RouteNameUtil.getCJKLineName(TextUtil.getCjkParts(routeName));
            width += G2dTextHelper.getUnifiedStringWidth(g, font, name, unit * 0.8f);
            width += G2dTextHelper.getMultiLinesWidth(g, font, unit * 0.7f, "号线", TextUtil.getNonCjkParts(routeName));
        } else {
            width += G2dTextHelper.getMultiLinesWidth(g, font, unit * 0.8f, TextUtil.getNonExtraParts(routeName).split("\\|"));
        }
        return width;
    }

    @Override
    public void draw(SignDrawContext ctx) {
        Graphics2D g = ctx.graphics();
        float u = ctx.unit();
        int x = (int) ctx.x();
        int y = (int) ctx.y();
        float width = getWidth(g, u);
        Color c = getRouteColor();
        String routeName = getRouteName();
        boolean isNumLine = RouteNameUtil.isNumLine(routeName);
        g.setColor(c);
        g.fillRoundRect(x, y, (int) width, (int) u, (int) (u / 10), (int) (u / 10));
        g.setColor(ColorUtil.isLightColor(c) ? Color.BLACK : Color.WHITE);
        if (isNumLine) {
            int currentX = x + (int) (u * 0.25f);
            String name = RouteNameUtil.getCJKLineName(TextUtil.getCjkParts(routeName));
            currentX += G2dTextHelper.drawStrUnified(g, font, name, currentX, (int) (y + u * 0.8f), u * 0.8f, 0);
            currentX += G2dTextHelper.drawStrMultiLines(g, font, currentX, y + (int) (u * 0.15f), (int) (u * 0.75f), 0, "号线", TextUtil.getNonCjkParts(routeName));
        } else {
            G2dTextHelper.drawStrMultiLines(g, font, (int) (x + u * 0.25f), y + (int) (u * 0.125f), (int) (u * 0.8f), 1, TextUtil.getNonExtraParts(routeName).split("\\|"));
        }
    }

    @Override
    public ResourceLocation getIconLocation() {
        return new ResourceLocation("fangsu:sign/routea.png");
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> list = new ArrayList<ConfigEntry<?>>();
        list.add(new RunnableConfig(
                Component.translatable("aaa"),
                new ConfigSpec("func"),
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.setScreen(new RouteSelectionScreen(
                                Component.translatable("bbb"),
                                List.of(),
                                (v) -> {
                                    if (v != null && !v.isEmpty())
                                        route = MtrUtil.getRouteById((v.get(0)));
                                },
                                mc.player.getOnPos(), 1, Minecraft.getInstance().screen));
                    }
                }
        ));
        return list;
    }

    private String getRouteName() {
        if (route == null) return "未命名|Undefined";
        return route.name;
    }

    private Color getRouteColor() {
        if (route == null) return new Color(0xabcdef);
        return new Color(route.color);
    }
}
