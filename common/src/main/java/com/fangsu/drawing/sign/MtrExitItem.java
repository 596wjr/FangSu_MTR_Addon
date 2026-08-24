package com.fangsu.drawing.sign;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigSpec;
import com.fangsu.extraConfig.RunnableConfig;
import com.fangsu.scripting.G2dTextHelper;
import com.fangsu.ui.ExitSelectionScreen;
import com.fangsu.ui.StationSelectionScreen;
import com.fangsu.utils.MtrUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mtr.data.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.fangsu.mappings.ResourceLocation;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * MTR 原版「选择出入口」指示牌项：先选择车站，再从该车站的出入口中选择要显示的出口字母牌。
 * 复刻 MTR EXIT_LETTER 牌的显示效果（exit_letter_blank 贴图 + MTR 字体）。
 */
public class MtrExitItem extends SignItem {
    private static final ResourceLocation ICON_LOCATION = new ResourceLocation("mtr:textures/block/sign/exit_letter.png");

    private long stationId = -1; // 从 JSON 解析的原始车站 ID，用于保留数据
    private Station station;
    private List<String> exits = new ArrayList<>();

    public MtrExitItem(JsonObject json) {
        if (json.has("stn") && json.get("stn").isJsonPrimitive()) {
            stationId = json.getAsJsonPrimitive("stn").getAsLong();
            station = MtrUtil.getStationById(stationId);
        }
        if (json.has("exits") && json.get("exits").isJsonArray()) {
            for (var e : json.getAsJsonArray("exits")) {
                if (e.isJsonPrimitive()) exits.add(e.getAsString());
            }
        }
    }

    @Override
    protected JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        if (station != null) {
            stationId = station.id;
            json.addProperty("stn", station.id);
        } else if (stationId != -1) {
            json.addProperty("stn", stationId);
        }
        JsonArray arr = new JsonArray();
        for (String e : exits) arr.add(e);
        json.add("exits", arr);
        return json;
    }

    @Override
    public String getType() {
        return "mtr_exit";
    }

    @Override
    public float getWidth(Graphics2D g, float unit) {
        if (exits.isEmpty()) return unit;
        return unit * exits.size();
    }

    @Override
    public void draw(SignDrawContext ctx) {
        Graphics2D g = ctx.graphics();
        float u = ctx.unit();
        float x = ctx.x();
        float y = ctx.y();
        if (exits.isEmpty()) {
            // 未选择出入口：画一个空白出口牌占位
            MtrSignRenderer.drawExitBlank(g, (int) x, (int) y, (int) u);
            return;
        }
        for (int i = 0; i < exits.size(); i++) {
            float tileX = x + i * u;
            String exit = exits.get(i);
            String letter = exit.substring(0, 1);
            String number = exit.length() > 1 ? exit.substring(1) : "";
            MtrSignRenderer.drawExitBlank(g, (int) tileX, (int) y, (int) u);
            g.setColor(Color.WHITE);
            if (number.isEmpty()) {
                G2dTextHelper.drawStrUnified(g, MtrSignRenderer.latinFont(), letter, (int) (tileX + u / 2f), (int) (y + u * 0.8f), u * 0.55f, 1);
            } else {
                G2dTextHelper.drawStrUnified(g, MtrSignRenderer.latinFont(), letter, (int) (tileX + u * 0.42f), (int) (y + u * 0.78f), u * 0.5f, 1);
                G2dTextHelper.drawStrUnified(g, MtrSignRenderer.latinFont(), number, (int) (tileX + u * 0.78f), (int) (y + u * 0.82f), u * 0.3f, 1);
            }
        }
    }

    @Override
    public ResourceLocation getIconLocation() {
        return ICON_LOCATION;
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> list = new ArrayList<>();
        list.add(new RunnableConfig(
                ComponentHelper.translatable("ui.fangsu.common.selectStn"),
                new ConfigSpec("func"),
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.setScreen(new StationSelectionScreen(
                                ComponentHelper.translatable("ui.fangsu.common.selectStn"),
                                List.of(),
                                (v) -> {
                                    if (v != null && !v.isEmpty()) {
                                        stationId = v.get(0);
                                        station = MtrUtil.getStationById(stationId);
                                        exits.clear();
                                    }
                                },
                                mc.player.getOnPos(), 1, Minecraft.getInstance().screen));
                    }
                }
        ));
        list.add(new RunnableConfig(
                ComponentHelper.translatable("ui.fangsu.common.selectExit"),
                new ConfigSpec("func"),
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null && station != null) {
                        mc.setScreen(new ExitSelectionScreen(
                                ComponentHelper.translatable("ui.fangsu.common.selectExit"),
                                new ArrayList<>(exits),
                                (v) -> {
                                    if (v != null) {
                                        exits.clear();
                                        exits.addAll(v);
                                        exits.sort(Comparator.naturalOrder());
                                    }
                                },
                                station, 16, Minecraft.getInstance().screen));
                    }
                }
        ));
        return list;
    }
}
