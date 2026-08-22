package com.fangsu.render.lift;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 单个自定义电梯的 properties.json 解析结果（MTR4 列车 properties 风格）。
 *
 * <p>{@code parts} 是数组，每个部位含 {@code names}(模型组名)、{@code positionDefinitions}(命名位置引用)、
 * {@code condition}、{@code renderStage}、{@code type} 及显示字段。实际放置位置由系统按电梯宽/深/高
 * 自动生成（positionDefinitions 只是命名约定，用户无需也不能自定义坐标）；门用
 * {@code door_left} / {@code door_right} 命名并参与开合动画。
 */
public class LiftAssemblyProperties {

    private final List<Part> parts = new ArrayList<>();
    private final String id;
    private final String name;
    private final String description;
    private final float cellSize;
    private final int minWidth;
    private final int minHeight;
    private final int minDepth;

    public LiftAssemblyProperties(JsonObject json) {
        id = json.has("id") ? json.get("id").getAsString() : "custom";
        name = json.has("name") ? json.get("name").getAsString() : id;
        description = json.has("description") ? json.get("description").getAsString() : "";
        cellSize = json.has("cell_size") ? json.get("cell_size").getAsFloat() : 8F;
        minWidth = json.has("min_width") ? (int) Math.round(json.get("min_width").getAsDouble()) : 2;
        minHeight = json.has("min_height") ? (int) Math.round(json.get("min_height").getAsDouble()) : 2;
        minDepth = json.has("min_depth") ? (int) Math.round(json.get("min_depth").getAsDouble()) : 2;

        if (json.has("parts") && json.get("parts").isJsonArray()) {
            JsonArray partsArray = json.getAsJsonArray("parts");
            for (JsonElement element : partsArray) {
                if (!element.isJsonObject()) continue;
                JsonObject partObj = element.getAsJsonObject();
                Part part = new Part();
                if (partObj.has("names") && partObj.get("names").isJsonArray()) {
                    part.names = new ArrayList<>();
                    for (JsonElement n : partObj.getAsJsonArray("names")) {
                        if (n.isJsonPrimitive()) part.names.add(n.getAsString());
                    }
                }
                if (partObj.has("positionDefinitions") && partObj.get("positionDefinitions").isJsonArray()) {
                    part.positionDefinitions = new ArrayList<>();
                    for (JsonElement pd : partObj.getAsJsonArray("positionDefinitions")) {
                        if (pd.isJsonPrimitive()) {
                            part.positionDefinitions.add(new PositionRef(pd.getAsString(), 0));
                        } else if (pd.isJsonObject()) {
                            JsonObject p = pd.getAsJsonObject();
                            String name = p.has("pos") ? p.get("pos").getAsString() : "";
                            double dist = p.has("distance") ? p.get("distance").getAsDouble()
                                    : p.has("dist") ? p.get("dist").getAsDouble() : 0;
                            part.positionDefinitions.add(new PositionRef(name, dist));
                        }
                    }
                }
                part.condition = partObj.has("condition") ? partObj.get("condition").getAsString() : "NORMAL";
                part.renderStage = partObj.has("renderStage") ? partObj.get("renderStage").getAsString() : "EXTERIOR";
                part.type = partObj.has("type") ? partObj.get("type").getAsString() : "NORMAL";
                part.displayType = partObj.has("displayType") ? partObj.get("displayType").getAsString() : "";
                part.displayDefaultText = partObj.has("displayDefaultText") ? partObj.get("displayDefaultText").getAsString() : "";
                part.displayColor = partObj.has("displayColor") ? partObj.get("displayColor").getAsString() : "FF0000";
                part.doorXMultiplier = partObj.has("doorXMultiplier") ? partObj.get("doorXMultiplier").getAsDouble() : 0;
                part.doorZMultiplier = partObj.has("doorZMultiplier") ? partObj.get("doorZMultiplier").getAsDouble() : 0;
                parts.add(part);
            }
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public float getCellSize() { return cellSize; }
    public int getMinWidth() { return minWidth; }
    public int getMinHeight() { return minHeight; }
    public int getMinDepth() { return minDepth; }

    public List<Part> getParts() { return parts; }

    /** 某部位是否为门（门动画）。 */
    public static boolean isDoorPart(String logical) {
        return "door_left".equalsIgnoreCase(logical) || "door_right".equalsIgnoreCase(logical);
    }

    /** 某部位是否为电梯专用条件。 */
    public static boolean isLiftCondition(String condition) {
        switch (condition.toUpperCase(Locale.ROOT)) {
            case "GOING_UP":
            case "GOING_DOWN":
            case "STOPPED":
                return true;
            default:
                return false;
        }
    }

    /** 一个部位（MTR4 列车 properties 风格）。 */
    public static final class Part {
        public List<String> names = Collections.emptyList();
        public List<PositionRef> positionDefinitions = Collections.emptyList();
        public String condition = "NORMAL";
        public String renderStage = "EXTERIOR";
        public String type = "NORMAL";
        public String displayType = "";
        public String displayDefaultText = "";
        public String displayColor = "FF0000";
        public double doorXMultiplier;
        public double doorZMultiplier;
    }

    /** 位置引用：命名位置 + 偏移（distance，格）。 */
    public static final class PositionRef {
        public final String name;
        public final double distance;

        public PositionRef(String name, double distance) {
            this.name = name;
            this.distance = distance;
        }
    }
}
