package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.CustomItemLoader;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.extraConfig.BoolConfig;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigSpec;
import com.fangsu.extraConfig.NumberInputConfig;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.signItems.SignDrawContext;
import com.fangsu.signItems.SignItem;
import com.fangsu.signItems.SignItemFactory;
import com.fangsu.utils.CollisionBoxUtil;
import com.fangsu.utils.CustomItemHelper;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_SIGN_ON_WALL;

public class BlockEntitySignOnWall extends BaseObjBlockEntity implements Syncable {
    private static final String DEFAULT_MAIN_MODEL = "fangsu:sign/beijing/beijing_sign.json";
    private static final String DEFAULT_SUB_MODEL = "beijing_sign_a_onwall";
    private static final String MAIN_MODEL_KEY = "sign";
    protected String subModel;

    private Map<String, Map<String, Object>> loaded;

    private DynamicModelHolder dmhLeft, dmhCenter, dmhRight, dmhDispFront;
    private GraphicsTexture gtFront;
    private CollisionBoxUtil.CollisionBox shapeLeft, shapeCenter, shapeRight;
    private int unit = 8;

    private double length = 2;

    private boolean requiresRedraw = true;

    private Map<String, List<SignItem>> items;

    public BlockEntitySignOnWall(BlockPos pos, BlockState state) {
        super(BLOCK_ENTITY_SIGN_ON_WALL.get(), pos, state);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("length", "2");
        ensureExtraConfig("items", "{}");
        ensureExtraConfig("itemsBack", "{}");
        ensureExtraConfig("showLeftPole", "true");
        ensureExtraConfig("leftPolePos", "8");
        ensureExtraConfig("showRightPole", "true");
        ensureExtraConfig("rightPolePos", "8");

        items = initItems(extraConfigs.get("items"));

        length = Double.parseDouble(extraConfigs.get("length"));

        mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);

        try {
            loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(mainModel), "on_wall");
            if (loaded == null || !loaded.containsKey(subModel)) {
                markedError = true;
                return;
            }
            Map<String, Object> current = loaded.get(subModel);
            boolean flipV = current.containsKey("flipV") && (boolean) current.get("flipV");
            String model = (String) current.get("model");
            Map<String, DynamicModelHolder> models = ResourceUtil.loadPartedDmh(new ResourceLocation(model), flipV);
            if (current.containsKey("unit") && current.get("unit") instanceof Number v) unit = v.intValue();
            if (current.get("main") instanceof Map<?, ?> main) {
                String modelKey = (String) main.get("subModel");
                dmhCenter = models.get(modelKey);
                if (main.containsKey("shape") && main.get("shape") instanceof List<?> l)
                    shapeCenter = new CollisionBoxUtil.CollisionBox(l);
            }
            if (current.get("side") instanceof Map<?, ?> side) {
                if (side.get("left") instanceof Map<?, ?> left) {
                    String modelKey = (String) left.get("subModel");
                    dmhLeft = models.get(modelKey);
                    if (left.containsKey("shape") && left.get("shape") instanceof List<?> l) {
                        shapeLeft = new CollisionBoxUtil.CollisionBox(l);
                    }
                }
                if (side.get("right") instanceof Map<?, ?> right) {
                    String modelKey = (String) right.get("subModel");
                    dmhRight = models.get(modelKey);
                    if (right.containsKey("shape") && right.get("shape") instanceof List<?> l) {
                        shapeRight = new CollisionBoxUtil.CollisionBox(l);
                    }
                }
            }

            RawMeshBuilder rawModelBuilderFront = new RawMeshBuilder(4, "lighttranslucent", new ResourceLocation("fangsu:sign/def_face1.png"));
            RawModel dispRawModelFront = new RawModel();
            List<?> texZone = (List<?>) current.get("tex");
            double y1 = (double) ((List<?>) texZone.get(0)).get(0),
                    z1 = (double) ((List<?>) texZone.get(0)).get(1);
            double y2 = (double) ((List<?>) texZone.get(1)).get(0),
                    z2 = (double) ((List<?>) texZone.get(1)).get(1);
            List<List<Double>> finalSlotFront = List.of(
                    List.of(-0.5 * unit * length / 16, y2, z2),
                    List.of(-0.5 * unit * length / 16, y1, z1),
                    List.of(0.5 * unit * length / 16, y1, z1),
                    List.of(0.5 * unit * length / 16, y2, z2)
            );
            addQuad(rawModelBuilderFront, finalSlotFront, false);
            dispRawModelFront.append(rawModelBuilderFront.getMesh());
            dispRawModelFront.generateNormals();
            dmhDispFront = new DynamicModelHolder();
            dmhDispFront.uploadLater(dispRawModelFront);

            requiresRedraw = true;
        } catch (Exception e) {
            Main.LOGGER.warn(e.getMessage());
        }
    }

    @Override
    public void whenRendering() {
        ObjBlockScriptContext ctx = this.scriptContext;
        if (requiresRedraw) {
            items = initItems(extraConfigs.get("items"));

            if (gtFront != null) gtFront.closeLater();
            gtFront = new GraphicsTexture((int) (unit * 72 * length + 1), unit * 72 + 1);


            if (gtFront != null && !gtFront.isClosed) {
                var g = gtFront.graphics;
                g.setComposite(AlphaComposite.Clear); // 设置透明混合模式
                g.fillRect(0, 0, gtFront.width, gtFront.height);   // 填充整个区域
                g.setComposite(AlphaComposite.SrcOver); // 恢复默认混合模式
                if (items != null) {
                    if (items.containsKey("left"))
                        drawLane(gtFront, items.get("left"), 0, gtFront.height * 0.1f, 0, gtFront.height * 0.8f);
                    if (items.containsKey("right"))
                        drawLane(gtFront, items.get("right"), gtFront.width, gtFront.height * 0.1f, 2, gtFront.height * 0.8f);
                    if (items.containsKey("center"))
                        drawLane(gtFront, items.get("center"), gtFront.width * 0.5f, gtFront.height * 0.1f, 1, gtFront.height * 0.8f);
                }
                gtFront.upload();
            }
            requiresRedraw = false;
        }
        if (dmhDispFront != null && dmhDispFront.getUploadedModel() != null) {
            dmhDispFront.getUploadedModel().replaceAllTexture(gtFront.identifier);
        }
        ctx.drawModel(dmhDispFront, null);

        Matrices mat = new Matrices();
        mat.translate(-0.5 * unit * length / 16, 0, 0);
        mat.pushPose();
        ctx.drawModel(dmhLeft, mat);
        for (int i = 0; i < length / (unit / 8d); i++) {
            if (i != 0) mat.translate(unit / 16d, 0, 0);
            else mat.translate(unit / 32d, 0, 0);
            ctx.drawModel(dmhCenter, mat);
        }
        mat.translate(unit / 32d, 0, 0);
        ctx.drawModel(dmhRight, mat);
        mat.popPose();
    }

    @Override
    public void whenSaving(Map<String, String> extraConfigs) {
    }

    @Override
    public InteractionResult whenUseWithinBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public String getMainModelKey() {
        return MAIN_MODEL_KEY;
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        configs.add(new NumberInputConfig(
                Component.translatable("ui.fangsu.common.length"),
                new ConfigSpec("num").setParam("isInt", new JsonPrimitive(true)).setParam("min", new JsonPrimitive(2)),
                () -> (float) (this.length),
                (v) -> {
                    this.length = v.intValue();
                    extraConfigs.put("length", length + "");
                }
        ));
        return configs;
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        List<ModelSelectInfo> thisInfo = new ArrayList<>();
        try {
            loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(this.mainModel), "common");
            for (String key : loaded.keySet()) {
                Map<String, Object> item = loaded.get(key);
                String text = "";
                String content = "";
                String contentText = null;
                if (item.containsKey("text") && item.get("text") instanceof String s) text = s;
                if (item.containsKey("id") && item.get("id") instanceof String s) content = s;
                if (item.containsKey("contentText") && item.get("contentText") instanceof String s) contentText = s;
                if (contentText != null) thisInfo.add(new ModelSelectInfo(text, content, contentText));
                else thisInfo.add(new ModelSelectInfo(text, content));
            }
        } catch (Exception ignored) {
        }
        infos.add(new SubModelDispInfo(
                Component.translatable("ui.fangsu.block.subModelSelect"),
                thisInfo,
                (be) -> this.subModels.getOrDefault("subModel", DEFAULT_SUB_MODEL),
                (be, v) -> this.subModels.put("subModel", v)));
        infos.add(new SubModelMethodInfo(Component.translatable("ui.fangsu.sign.editSign"), () -> {
            if (items == null) items = new HashMap<>();
            ClientHooks.openSignConfigScreen(1, List.of(items), saveItems -> {
                items = saveItems.get(0);
                extraConfigs.put("items", toItemsJson(items).toString());
                requiresRedraw = true;
                sendUpdateC2S();
            });
        }));
        return infos;
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

    @Override
    public void readC2S(FriendlyByteBuf buf) {
        translateX = buf.readFloat();
        translateY = buf.readFloat();
        translateZ = buf.readFloat();
        rotateX = buf.readFloat();
        rotateY = buf.readFloat();
        rotateZ = buf.readFloat();
        mainModel = buf.readUtf();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            String value = buf.readUtf(16384);
            extraConfigs.put(key, value);
        }

        size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            String value = buf.readUtf(128);
            subModels.put(key, value);
        }
        if (level != null && level.isClientSide == false) {
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    3
            );
            this.setChanged();
        }

        requiresRedraw = true;

        items = initItems(extraConfigs.get("items"));

        length = Double.parseDouble(extraConfigs.getOrDefault("length", "2"));

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

    private void addQuad(RawMeshBuilder builder, List<List<Double>> quad, boolean reverse) {
        float[] normal = ModelHelper.calculateNormal(quad.get(0), quad.get(1), quad.get(2));

        // 如果需要反转法向（比如背面）
        if (reverse) {
            normal[0] *= -1;
            normal[1] *= -1;
            normal[2] *= -1;
        }

        builder.vertex(quad.get(0).get(0), quad.get(0).get(1), quad.get(0).get(2))
                .normal(normal[0], normal[1], normal[2]).uv(0, 0).endVertex()
                .vertex(quad.get(1).get(0), quad.get(1).get(1), quad.get(1).get(2))
                .normal(normal[0], normal[1], normal[2]).uv(0, 1).endVertex()
                .vertex(quad.get(2).get(0), quad.get(2).get(1), quad.get(2).get(2))
                .normal(normal[0], normal[1], normal[2]).uv(1, 1).endVertex()
                .vertex(quad.get(3).get(0), quad.get(3).get(1), quad.get(3).get(2))
                .normal(normal[0], normal[1], normal[2]).uv(1, 0).endVertex();
    }

    private void drawLane(GraphicsTexture gt, List<SignItem> lane, float startX, float y, int align, float u) {
        Graphics2D g = gt.graphics;
        if (lane == null || lane.isEmpty()) return;
//        Shape oriClip = g.getClip();
        float x = startX;
        if (align == 2) {
            float totalWidth = 0;
            for (SignItem token : lane) totalWidth += getTokenWidth(g, token, u) + u * 0.1f;
            x = startX - totalWidth;
        } else if (align == 1) {
            float totalWidth = 0;
            for (SignItem token : lane) totalWidth += getTokenWidth(g, token, u) + u * 0.1f;
            x = startX - (totalWidth) / 2f;
        }
        for (SignItem token : lane) {
            float tokenWidth = getTokenWidth(g, token, u);
//            g.setClip(new Rectangle((int) x, (int) y, (int) tokenWidth, (int) u));
            SignDrawContext ctx = new SignDrawContext(g, (x), (y), (u), align, false);
            token.draw(ctx);
            x += tokenWidth + u * 0.1f;
//            g.setClip(oriClip);
        }
    }

    private float getTokenWidth(Graphics2D graphics, SignItem token, float unit) {
        return token.getWidth(graphics, unit);
    }

}
