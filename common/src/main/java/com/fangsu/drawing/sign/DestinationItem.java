package com.fangsu.drawing.sign;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.extraConfig.*;
import com.fangsu.scripting.G2dTextHelper;
import com.fangsu.scripting.TextUtil;
import com.fangsu.ui.PlatformSelectionScreen;
import com.fangsu.utils.MtrUtil;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonObject;
import mtr.data.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DestinationItem extends SignItem {
    private Platform plat, cachePlat = null;
    private Font font;
    private int align;
    private String cacheText = "";

    public DestinationItem(JsonObject json) {
        if (json.has("plat")) {
            plat = MtrUtil.getPlatformById(json.getAsJsonPrimitive("plat").getAsLong());
        } else plat = null;
        if (json.has("align")) {
            align = json.getAsJsonPrimitive("align").getAsInt();
        } else align = 0;
        font = ResourceUtil.loadFont(new ResourceLocation("fangsu:fonts/source-han-sans-bold.otf"));
    }

    @Override
    protected JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        if (plat != null) json.addProperty("plat", plat.id);
        json.addProperty("align", align);
        return json;
    }

    @Override
    public String getType() {
        return "destination";
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
        return new ResourceLocation("fangsu:sign/destination.png");
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> list = new ArrayList<ConfigEntry<?>>();
        list.add(new RunnableConfig(
                ComponentHelper.translatable("ui.fangsu.common.selectPlat"),
                new ConfigSpec("func"),
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.setScreen(new PlatformSelectionScreen(
                                ComponentHelper.translatable("ui.fangsu.common.selectPlat"),
                                List.of(),
                                (v) -> {
                                    if (v != null && !v.isEmpty())
                                        plat = MtrUtil.getPlatformById((v.get(0)));
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
        if (MtrUtil.isAllDestination(plat))
            return "终点站|Terminals";
        if (plat == null) return "开往 未命名|To undefined";
        if ((cachePlat != null && cachePlat.equals(plat)) && !cacheText.isEmpty()) return cacheText;
        cachePlat = plat;
        String rawDest = MtrUtil.getDestinationByPlatform(plat);
        String dest = TextUtil.addPrefix(rawDest, "开往", "To", true);
        cacheText = dest;
        return dest;
    }
}
