package com.fangsu.ui;

import com.fangsu.Main;
import com.fangsu.blockEntities.BaseObjBlockEntity;
import com.fangsu.signItems.SignDrawContext;
import com.fangsu.signItems.SignItem;
import com.fangsu.signItems.SignItemFactory;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 参考 signsc.js 的双阶段结构：
 * 1) 选择 front/back + left/middle/right 的 6 行；
 * 2) 编辑某一行的元素，并从 signItems 面板插入新元素。
 */
public class SignConfigUI extends Screen {

    private static final Gson GSON = new Gson();
    private static final int ROW_COUNT = 6;

    private final BaseObjBlockEntity blockEntity;
    private final Map<String, List<List<JsonObject>>> displayState = new HashMap<>();
    private final List<JsonObject> signItems = new ArrayList<>();

    private int modeFlag = 0;
    private int selectedRow = -1;
    private int sideEditing = -1;
    private float[] rowScroll = new float[ROW_COUNT];
    private float paletteScroll = 0;

    public SignConfigUI(BaseObjBlockEntity blockEntity) {
        super(Component.translatable("ui.fangsu.sign.title"));
        this.blockEntity = blockEntity;
        initDisplayState();
        loadSignItems();
    }

    @Override
    protected void init() {
        super.init();
        if (rowScroll.length != ROW_COUNT) {
            rowScroll = new float[ROW_COUNT];
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xFF101010);

        if (modeFlag == 0) {
            drawSelectionScreen(graphics, mouseX, mouseY);
        } else {
            drawEditingScreen(graphics, mouseX, mouseY);
        }

        graphics.drawString(font, this.title, 10, 10, 0xFFFFFF, false);
    }

    private void drawSelectionScreen(GuiGraphics graphics, int mouseX, int mouseY) {
        int i = 0;
        int rowHeight = Math.max(40, (int) (height * 0.14f));
        for (String side : List.of("front", "back")) {
            for (int part = 0; part < 3; part++) {
                int rowY = 40 + i * rowHeight;
                int rowBottom = rowY + rowHeight - 4;
                boolean selected = mouseY >= rowY && mouseY <= rowBottom;

                graphics.fill(12, rowY, width - 12, rowBottom, selected ? 0x55222222 : 0x441A1A1A);
                graphics.drawString(font, side + " - " + partName(part), 16, rowY + 6, 0xFFFFFF, false);

                List<JsonObject> lane = displayState.get(side).get(part);
                drawLane(graphics, lane, 18 + rowScroll[i], rowY + 18, part == 2 ? 2 : part, 18f);
                i++;
            }
        }
    }

    private void drawEditingScreen(GuiGraphics graphics, int mouseX, int mouseY) {
        LaneRef laneRef = rowIndexToLane(selectedRow);
        if (laneRef == null) {
            modeFlag = 0;
            return;
        }

        List<JsonObject> lane = laneRef.lane();
        graphics.fill(12, 36, width - 12, 92, 0x441E1E1E);
        graphics.drawString(font, Component.translatable("ui.fangsu.sign.editing").getString() + " " + laneRef.face() + " - " + partName(laneRef.part()), 16, 44, 0xFFFFFF, false);
        graphics.drawString(font, "ESC: back", width - 70, 44, 0xCCCCCC, false);

        float x = 24;
        float y = 62;
        float u = 24;

        if (lane.isEmpty()) {
            drawPlus(graphics, x, y, u);
        }

        for (int idx = 0; idx < lane.size(); idx++) {
            JsonObject token = lane.get(idx);
            float tokenW = getTokenWidth(graphics, token, u, laneRef.part());
            boolean hover = mouseX >= x && mouseX <= x + tokenW && mouseY >= y && mouseY <= y + u;

            if (hover) {
                graphics.fill((int) x, (int) y, (int) (x + tokenW), (int) (y + u), 0x33FFFFFF);
                graphics.drawString(font, "L:edit R:del", (int) x, (int) (y - 10), 0xE0E0E0, false);
            }

            drawToken(graphics, token, x, y, u, laneRef.part());

            boolean addHover = mouseX >= x + tokenW && mouseX <= x + tokenW + u / 2f && mouseY >= y && mouseY <= y + u;
            if (addHover || sideEditing == idx) {
                drawPlus(graphics, x + tokenW, y, u / 2f);
            }

            x += tokenW + (u * 0.35f);
        }

        drawPalette(graphics, mouseX, mouseY, laneRef, lane);
    }

    private void drawPalette(GuiGraphics graphics, int mouseX, int mouseY, LaneRef laneRef, List<JsonObject> lane) {
        int top = 110;
        int lineItems = Math.max(1, (width - 24) / 30);
        int cell = 24;
        int gap = 4;

        graphics.enableScissor(12, top, width - 12, height - 12);
        for (int idx = 0; idx < signItems.size(); idx++) {
            int row = idx / lineItems;
            int col = idx % lineItems;
            int x = 16 + col * (cell + gap);
            int y = top + (int) paletteScroll + row * (cell + gap);
            boolean hover = mouseX >= x && mouseX <= x + cell && mouseY >= y && mouseY <= y + cell;

            graphics.fill(x, y, x + cell, y + cell, hover ? 0x33FFFFFF : 0x22000000);
            JsonObject token = signItems.get(idx);
            drawToken(graphics, token, x + 2, y + 2, cell - 4, laneRef.part());
            if (hover) {
                graphics.drawString(font, "+", x + 9, y + 8, 0xFFFFFF, false);
            }
        }
        graphics.disableScissor();
    }

    private void drawLane(GuiGraphics graphics, List<JsonObject> lane, float startX, float y, int align, float u) {
        float x = startX;
        if (align == 2) {
            x = width - 20 + startX;
        }
        for (JsonObject token : lane) {
            float tokenWidth = getTokenWidth(graphics, token, u, align);
            drawToken(graphics, token, x, y, u, align);
            x += (align == 2 ? -1 : 1) * (tokenWidth + u * 0.1f);
        }
    }

    private float getTokenWidth(GuiGraphics graphics, JsonObject token, float unit, int align) {
        String type = token.get("type").getAsString();
        if (SignItemFactory.has(type)) {
            SignItem item = SignItemFactory.get(type);
            return Math.max(unit * 0.5f, item.getWidth(new SignDrawContext(graphics, 0, 0, unit, align, token.get("content"))));
        }
        if ("space".equals(type)) {
            return unit * token.getAsJsonObject("content").get("width").getAsFloat();
        }
        if ("str".equals(type)) {
            String text = token.getAsJsonObject("content").get("text").getAsString();
            return Math.max(unit * 0.6f, font.width(text.replace('|', ' ')) * (unit / 18f));
        }
        return unit;
    }


    private float getTokenWidthForHit(JsonObject token, float unit, int align) {
        String type = token.has("type") ? token.get("type").getAsString() : "";
        if ("space".equals(type) && token.has("content") && token.getAsJsonObject("content").has("width")) {
            return unit * token.getAsJsonObject("content").get("width").getAsFloat();
        }
        if ("str".equals(type) && token.has("content") && token.getAsJsonObject("content").has("text")) {
            String text = token.getAsJsonObject("content").get("text").getAsString();
            return Math.max(unit * 0.6f, font.width(text.replace('|', ' ')) * (unit / 18f));
        }
        if (SignItemFactory.has(type)) {
            return unit;
        }
        return unit;
    }
    private void drawToken(GuiGraphics graphics, JsonObject token, float x, float y, float unit, int align) {
        String type = token.get("type").getAsString();
        JsonObject content = token.getAsJsonObject("content");

        if (SignItemFactory.has(type)) {
            SignItemFactory.get(type).draw(new SignDrawContext(graphics, x, y, unit, align, content));
            return;
        }

        if ("str".equals(type)) {
            String text = content.has("text") ? content.get("text").getAsString() : "";
            graphics.drawString(font, text.replace('|', ' '), (int) x, (int) y + 8, 0xFFFFFF, false);
        } else if ("space".equals(type)) {
            graphics.fill((int) x, (int) y + (int) (unit / 2), (int) (x + unit), (int) (y + unit / 2 + 1), 0x66AAAAAA);
        } else {
            String icon = token.has("icon") ? token.get("icon").getAsString() : "?";
            graphics.fill((int) x, (int) y, (int) (x + unit), (int) (y + unit), 0x55181818);
            graphics.drawString(font, icon.contains(":") ? icon.substring(icon.indexOf(':') + 1).replace(".png", "") : icon,
                    (int) x + 1, (int) y + 8, 0xFFEFEFEF, false);
        }
    }

    private void drawPlus(GuiGraphics graphics, float x, float y, float size) {
        int cx = (int) (x + size / 2f);
        int cy = (int) (y + size / 2f);
        graphics.fill(cx - 1, (int) y + 2, cx + 1, (int) (y + size - 2), 0xFFFFFFFF);
        graphics.fill((int) x + 2, cy - 1, (int) (x + size - 2), cy + 1, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (modeFlag == 0) {
            int rowHeight = Math.max(40, (int) (height * 0.14f));
            for (int i = 0; i < ROW_COUNT; i++) {
                int rowY = 40 + i * rowHeight;
                if (mouseY >= rowY && mouseY <= rowY + rowHeight - 4) {
                    selectedRow = i;
                    modeFlag = 1;
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        LaneRef laneRef = rowIndexToLane(selectedRow);
        if (laneRef == null) return super.mouseClicked(mouseX, mouseY, button);
        List<JsonObject> lane = laneRef.lane();

        float x = 24;
        float y = 62;
        float u = 24;
        for (int idx = 0; idx < lane.size(); idx++) {
            JsonObject token = lane.get(idx);
            float tokenW = getTokenWidthForHit(token, u, laneRef.part());
            if (mouseX >= x && mouseX <= x + tokenW && mouseY >= y && mouseY <= y + u) {
                if (button == 1) {
                    lane.remove(idx);
                    persistDisplay();
                    return true;
                }
                if (token.has("content") && token.getAsJsonObject("content").has("duiqi")) {
                    JsonObject content = token.getAsJsonObject("content");
                    int current = content.get("duiqi").getAsInt();
                    content.addProperty("duiqi", (current + 1) % 3);
                    persistDisplay();
                }
                return true;
            }
            boolean addHover = mouseX >= x + tokenW && mouseX <= x + tokenW + u / 2f && mouseY >= y && mouseY <= y + u;
            if (addHover) {
                sideEditing = idx;
                return true;
            }
            x += tokenW + (u * 0.35f);
        }

        int top = 110;
        int lineItems = Math.max(1, (width - 24) / 30);
        int cell = 24;
        int gap = 4;
        for (int idx = 0; idx < signItems.size(); idx++) {
            int row = idx / lineItems;
            int col = idx % lineItems;
            int px = 16 + col * (cell + gap);
            int py = top + (int) paletteScroll + row * (cell + gap);
            if (mouseX >= px && mouseX <= px + cell && mouseY >= py && mouseY <= py + cell) {
                if (sideEditing < 0) {
                    sideEditing = lane.size() - 1;
                }
                lane.add(Math.min(sideEditing + 1, lane.size()), deepCopy(signItems.get(idx)));
                sideEditing = -1;
                persistDisplay();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (modeFlag == 0) {
            int rowHeight = Math.max(40, (int) (height * 0.14f));
            for (int i = 0; i < ROW_COUNT; i++) {
                int rowY = 40 + i * rowHeight;
                if (mouseY >= rowY && mouseY <= rowY + rowHeight - 4) {
                    rowScroll[i] += (float) (delta * 8f);
                    return true;
                }
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        if (mouseY >= 110) {
            paletteScroll += (float) (delta * 10f);
            float min = -Math.max(0, (float) Math.ceil((double) signItems.size() / Math.max(1, (width - 24) / 30)) * 28f - (height - 122));
            paletteScroll = Math.max(min, Math.min(0, paletteScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && modeFlag == 1) {
            modeFlag = 0;
            selectedRow = -1;
            sideEditing = -1;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        persistDisplay();
        super.onClose();
    }

    private void initDisplayState() {
        displayState.put("front", new ArrayList<>(List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>())));
        displayState.put("back", new ArrayList<>(List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>())));

        String raw = blockEntity.getExtraConfig("dispItems");
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            loadFace(obj, "front");
            loadFace(obj, "back");
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to parse dispItems: {}", e.getMessage());
        }
    }

    private void loadFace(JsonObject src, String face) {
        if (!src.has(face) || !src.get(face).isJsonArray()) {
            return;
        }
        JsonArray faceArr = src.getAsJsonArray(face);
        for (int i = 0; i < 3 && i < faceArr.size(); i++) {
            JsonElement laneElem = faceArr.get(i);
            if (!laneElem.isJsonArray()) continue;
            List<JsonObject> lane = displayState.get(face).get(i);
            for (JsonElement token : laneElem.getAsJsonArray()) {
                if (token.isJsonObject()) {
                    lane.add(token.getAsJsonObject());
                }
            }
        }
    }

    private void loadSignItems() {
        signItems.clear();
        ResourceLocation builtin = new ResourceLocation("fangsu:sign/builtinsign.json");
        try {
            JsonObject obj = JsonParser.parseString(ResourceUtil.loadString(builtin)).getAsJsonObject();
            if (obj != null && obj.has("signItems") && obj.get("signItems").isJsonArray()) {
                for (JsonElement element : obj.getAsJsonArray("signItems")) {
                    if (element.isJsonObject()) signItems.add(element.getAsJsonObject());
                }
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to load builtinsign.json: {}", e.getMessage());
        }

        for (SignItem signItem : registeredSignItems()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", signItem.getType());
            try {
                if (signItem.getIcon() != null) {
                    obj.addProperty("icon", "mtrsteamloco:imgnotfound.png");
                }
            } catch (Exception ignored) {
            }
            obj.add("content", new JsonObject());
            signItems.add(obj);
        }
    }

    private List<SignItem> registeredSignItems() {
        List<SignItem> result = new ArrayList<>();
        for (String type : List.of("str", "img", "route", "routeb", "trainicon", "destination", "space")) {
            if (SignItemFactory.has(type)) {
                try {
                    result.add(SignItemFactory.get(type));
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    private void persistDisplay() {
        JsonObject root = new JsonObject();
        root.add("front", exportFace("front"));
        root.add("back", exportFace("back"));
        blockEntity.setExtraConfig("dispItems", GSON.toJson(root));
        blockEntity.sendUpdateC2S();
    }

    private JsonArray exportFace(String face) {
        JsonArray arr = new JsonArray();
        for (List<JsonObject> lane : displayState.get(face)) {
            JsonArray laneArr = new JsonArray();
            for (JsonObject token : lane) {
                laneArr.add(token);
            }
            arr.add(laneArr);
        }
        return arr;
    }

    @Nullable
    private LaneRef rowIndexToLane(int index) {
        if (index < 0 || index >= ROW_COUNT) return null;
        String face = index < 3 ? "front" : "back";
        int part = index % 3;
        return new LaneRef(face, part, displayState.get(face).get(part));
    }

    private String partName(int part) {
        return switch (part) {
            case 0 -> "left";
            case 1 -> "middle";
            case 2 -> "right";
            default -> "unknown";
        };
    }

    private JsonObject deepCopy(JsonObject input) {
        return JsonParser.parseString(input.toString()).getAsJsonObject();
    }

    private record LaneRef(String face, int part, List<JsonObject> lane) {
    }
}
