package com.fangsu.drawing.sign;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.extraConfig.*;
import com.fangsu.scripting.G2dTextHelper;
import com.fangsu.scripting.TextUtil;
import com.fangsu.mtr.LocalRoute;
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

public class RouteDestinationItem extends SignItem {
    private LocalRoute route;
    private Font font;
    private int align;

    public RouteDestinationItem(JsonObject json) {
        if (json.has("route")) {
            route = MtrUtil.getRouteById(json.getAsJsonPrimitive("route").getAsLong());
        } else route = null;
        if (json.has("align")) {
            align = json.getAsJsonPrimitive("align").getAsInt();
        } else align = 0;
        font = ResourceUtil.loadFont(new ResourceLocation("fangsu:fonts/source-han-sans-bold.otf").getRaw());
    }

    @Override
    protected JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        if (route != null) json.addProperty("route", route.id);
        json.addProperty("align", align);
        return json;
    }

    @Override
    public String getType() {
        return "destination_route";
    }

    @Override
    public float getWidth(Graphics2D g, float unit) {
        String[] lines = TextUtil.getNonExtraParts(getDest()).split("\\|");
        return G2dTextHelper.getMultiLinesWidth(g, font, (int) unit, lines);
    }

    @Override
    public void draw(SignDrawContext ctx) {
        String[] lines = TextUtil.getNonExtraParts(getDest()).split("\\|");
        Graphics2D g = ctx.graphics();
        float u = ctx.unit();
        g.setColor(Color.WHITE);
        G2dTextHelper.drawStrMultiLines(g, font, (int) ctx.x(), (int) ctx.y() - (int) u, (int) u, align, lines);
    }

    @Override
    public ResourceLocation getIconLocation() {
        return new ResourceLocation("fangsu:sign/destination_route.png");
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
        list.add(new EnumConfig(
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
        return list;
    }

    private String getDest() {
        if (route == null) return "开往 未命名|To undefined";
        String rawDest = MtrUtil.getDestinationByRoute(route);
        return TextUtil.addPrefix(rawDest, "开往", "To", true);
    }
}
