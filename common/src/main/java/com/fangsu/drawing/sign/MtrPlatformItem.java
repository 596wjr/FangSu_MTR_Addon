package com.fangsu.drawing.sign;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigSpec;
import com.fangsu.extraConfig.RunnableConfig;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.scripting.G2dTextHelper;
import com.fangsu.scripting.TextUtil;
import com.fangsu.ui.PlatformSelectionScreen;
import com.fangsu.utils.MtrUtil;
import com.google.gson.JsonObject;
import mtr.data.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.fangsu.mappings.ResourceLocation;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MTR 原版「选择站台」指示牌项：复刻 MTR 铁路指示牌 PLATFORM 牌的显示效果，
 * 用 MTR 的 circle 贴图（线路色）+ MTR 字体绘制站台号，右侧显示终点文字（无"开往"前缀）。
 */
public class MtrPlatformItem extends SignItem {
    private static final ResourceLocation ICON_LOCATION = new ResourceLocation("mtr:textures/block/sign/platform.png");

    private long platformId = -1; // 从 JSON 解析的原始站台 ID，用于保留数据
    private Platform plat;

    public MtrPlatformItem(JsonObject json) {
        if (json.has("plat") && json.get("plat").isJsonPrimitive()) {
            platformId = json.getAsJsonPrimitive("plat").getAsLong();
            plat = MtrUtil.getPlatformById(platformId);
        }
    }

    @Override
    protected JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        if (plat != null) {
            platformId = plat.id;
            json.addProperty("plat", plat.id);
        } else if (platformId != -1) {
            json.addProperty("plat", platformId);
        }
        return json;
    }

    @Override
    public String getType() {
        return "mtr_platform";
    }

    @Override
    public float getWidth(Graphics2D g, float unit) {
        float width = unit;
        String dest = TextUtil.getNonExtraParts(getDest());
        width += unit * 0.15f + G2dTextHelper.getMultiLinesWidth(g, MtrSignRenderer.cjkFont(), MtrSignRenderer.latinFont(), unit * 0.6f, dest.split("\\|"));
        return width;
    }

    @Override
    public void draw(SignDrawContext ctx) {
        Graphics2D g = ctx.graphics();
        float u = ctx.unit();
        float x = ctx.x();
        float y = ctx.y();
        float curX = x;

        int routeColor = getRouteColor();

        // 站台号圆标（复刻 MTR circle 贴图 + 线路色），站台号水平拉伸填满圆标宽度
        MtrSignRenderer.drawCircle(g, routeColor, (int) curX, (int) y, (int) u);
        g.setColor(Color.WHITE);
        String number = getPlatformNumber();
        int numMaxW = (int) (u * 0.9f);
        G2dTextHelper.drawStrUnifiedWithStretch(g, MtrSignRenderer.latinFont(), number, (int) (curX + u / 2f), (int) (y + u * 0.84f), u * 0.7f, numMaxW, 1);
        curX += u;

        // 终点文字（MTR CJK/拉丁字体，无"开往"前缀）
        curX += u * 0.15f;
        g.setColor(Color.WHITE);
        G2dTextHelper.drawStrMultiLines(g, MtrSignRenderer.cjkFont(), MtrSignRenderer.latinFont(), (int) curX, (int) (y + u * 0.1f) - (int) (u * 0.6f), (int) (u * 0.6f), 0, TextUtil.getNonExtraParts(getDest()).split("\\|"));
    }

    @Override
    public ResourceLocation getIconLocation() {
        return ICON_LOCATION;
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> list = new ArrayList<>();
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
                                    if (v != null && !v.isEmpty()) {
                                        platformId = v.get(0);
                                        plat = MtrUtil.getPlatformById(platformId);
                                    }
                                },
                                mc.player.getOnPos(), 1, Minecraft.getInstance().screen));
                    }
                }
        ));
        return list;
    }

    private String getPlatformNumber() {
        if (plat == null) return "-";
        return plat.name == null || plat.name.isEmpty() ? "-" : plat.name;
    }

    private String getDest() {
        if (plat == null) return "未命名|Undefined";
        String raw = MtrUtil.getDestinationByPlatform(plat);
        if (raw == null || raw.isEmpty()) return "未命名|Undefined";
        return raw;
    }

    private int getRouteColor() {
        if (plat == null) return 0xabcdef;
        List<LocalRoute> routes = MtrUtil.getRouteByPlatform(plat);
        if (routes != null && !routes.isEmpty()) {
            return routes.get(0).color;
        }
        return plat.color;
    }
}
