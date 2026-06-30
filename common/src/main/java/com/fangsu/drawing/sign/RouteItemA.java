package com.fangsu.drawing.sign;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigSpec;
import com.fangsu.extraConfig.RunnableConfig;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.scripting.G2dTextHelper;
import com.fangsu.scripting.RouteNameUtil;
import com.fangsu.scripting.TextUtil;
import com.fangsu.ui.RouteSelectionScreen;
import com.fangsu.utils.MtrUtil;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.fangsu.mappings.ResourceLocation;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RouteItemA extends SignItem {
    private LocalRoute route;
    private long routeId = -1; // 从JSON解析的原始路线ID，用于在route为null时保留数据
    private Font font;

    public RouteItemA(JsonObject json) {
        if (json.has("route") && json.get("route").isJsonPrimitive()) {
            routeId = json.getAsJsonPrimitive("route").getAsLong();
            route = MtrUtil.getRouteById(routeId);
        } else {
            route = null;
            routeId = -1;
        }
        font = ResourceUtil.loadFont(new ResourceLocation("fangsu:fonts/source-han-sans-bold.otf").getRaw());
    }

    @Override
    protected JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        if (route != null) {
            json.addProperty("route", route.id);
        } else if (routeId != -1) {
            // 路线对象未加载成功时，保留原始ID
            json.addProperty("route", routeId);
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
        float width = unit * 0.2f;
        if (isNumLine) {
            String name = RouteNameUtil.getCJKLineName(TextUtil.getCjkParts(routeName));
            width += G2dTextHelper.getUnifiedStringWidth(g, font, name, unit * 0.7f);
            width += G2dTextHelper.getMultiLinesWidth(g, font, unit * 0.65f, "号线", TextUtil.getNonCjkParts(routeName));
        } else {
            width += G2dTextHelper.getMultiLinesWidth(g, font, unit * 0.7f, TextUtil.getNonExtraParts(routeName).split("\\|"));
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
        g.fillRect(x, (int) (y + u * 0.8), (int) width, (int) (u * 0.2));
        g.setColor(Color.WHITE);
        if (isNumLine) {
            int currentX = x + (int) (u * 0.1f);
            String name = RouteNameUtil.getCJKLineName(TextUtil.getCjkParts(routeName));
            currentX += G2dTextHelper.drawStrUnified(g, font, name, currentX, (int) (y + u * 0.7f), u * 0.75f, 0);
            currentX += G2dTextHelper.drawStrMultiLines(g, font, currentX, y + (int) (u * 0.1f) - (int) (u * 0.65f), (int) (u * 0.65f), 0, "号线", TextUtil.getNonCjkParts(routeName));
        } else {
            G2dTextHelper.drawStrMultiLines(g, font, (int) (x + u * 0.1f), y + (int) (u * 0.075f) - (int) (u * 0.7f), (int) (u * 0.7f), 1, TextUtil.getNonExtraParts(routeName).split("\\|"));
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

    private String getRouteName() {
        if (route == null) return "未命名|Undefined";
        return route.name;
    }

    private Color getRouteColor() {
        if (route == null) return new Color(0xabcdef);
        return new Color(route.color);
    }
}
