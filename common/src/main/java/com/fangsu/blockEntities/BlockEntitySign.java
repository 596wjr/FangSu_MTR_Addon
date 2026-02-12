package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.signItems.SignItem;
import com.fangsu.signItems.SignItemFactory;
import com.fangsu.ui.SignConfigUI;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fabric.cn.zbx1425.mtrsteamloco.render.scripting.util.DynamicModelHolder;
import fabric.cn.zbx1425.mtrsteamloco.render.scripting.util.GraphicsTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_SIGN;

public class BlockEntitySign extends BaseObjBlockEntity implements Syncable {
    private static final String DEFAULT_MAIN_MODEL = "fangsu:sign/beijing/beijing_sign.json";
    private static final String DEFAULT_SUB_MODEL = "beijing_sign_a";
    private static final String MAIN_MODEL_KEY = "sign";

    private DynamicModelHolder dmhLeft, dmhCenter, dmhRight, dmhDisp;
    private GraphicsTexture gt;

    private boolean requiresRedraw = true;

    private Map<String, List<SignItem>> itemsFront, itemsBack;

    public BlockEntitySign(BlockPos pos, BlockState state) {
        super(BLOCK_ENTITY_SIGN.get(), pos, state);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("length", "2");
        ensureExtraConfig("itemsFront", "{}");
        ensureExtraConfig("itemsBack", "{}");

        itemsFront = initItems(extraConfigs.get("itemsFront"));
        itemsBack = initItems(extraConfigs.get("itemsBack"));
    }

    @Override
    public void whenRendering() {

    }

    @Override
    public void whenSaving(Map<String, String> extraConfigs) {
        extraConfigs.put("itemsFront", toItemsJson(itemsFront).toString());
        extraConfigs.put("itemsBack", toItemsJson(itemsBack).toString());
    }

    @Override
    public InteractionResult whenUseWithinBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new SignConfigUI(2, List.of(itemsFront, itemsBack),
                    (list) -> {
                        itemsFront = list.get(0);
                        itemsBack = list.get(1);
                        requiresRedraw = true;
                        sendUpdateC2S();
                    }));
        });
        return InteractionResult.PASS;
    }

    @Override
    public String getMainModelKey() {
        return MAIN_MODEL_KEY;
    }

    private Map<String, List<SignItem>> initItems(String src) {
        Map<String, List<SignItem>> items = new HashMap<>();
        JsonObject json = Main.JSON_PARSER.parse(src).getAsJsonObject();
        List<SignItem> itemsLeft;
        List<SignItem> itemsCenter;
        List<SignItem> itemsRight;
        if (json.has("left") && json.get("left").isJsonArray()) {
            itemsLeft = getItems(json.get("left").getAsJsonArray());
        } else itemsLeft = new ArrayList<>();
        if (json.has("center") && json.get("center").isJsonArray()) {
            itemsCenter = getItems(json.get("center").getAsJsonArray());
        } else itemsCenter = new ArrayList<>();
        if (json.has("right") && json.get("right").isJsonArray()) {
            itemsRight = getItems(json.get("right").getAsJsonArray());
        } else itemsRight = new ArrayList<>();
        items.put("left", itemsLeft);
        items.put("center", itemsCenter);
        items.put("right", itemsRight);
        return items;
    }

    private List<SignItem> getItems(JsonArray src) {
        List<SignItem> items = new ArrayList<>();
        for (JsonElement item : src) {
            if (!item.isJsonObject()) continue;
            JsonObject itemObj = item.getAsJsonObject();
            String type = itemObj.get("type").getAsString();
            SignItem currentItem = SignItemFactory.get(type).apply(itemObj);
            items.add(currentItem);
        }
        return items;
    }

    private JsonObject toItemsJson(Map<String, List<SignItem>> items) {
        JsonObject json = new JsonObject();
        if (items == null || items.isEmpty()) return json;
        if (items.containsKey("left")) json.add("left", toItemsJsonArray(items.get("left")));
        if (items.containsKey("center")) json.add("center", toItemsJsonArray(items.get("center")));
        if (items.containsKey("right")) json.add("right", toItemsJsonArray(items.get("right")));
        return json;
    }

    private JsonArray toItemsJsonArray(List<SignItem> items) {
        JsonArray array = new JsonArray();
        if (items == null) {
            return array;
        }
        for (SignItem item : items) {
            array.add(item.toJson());
        }
        return array;
    }
}
