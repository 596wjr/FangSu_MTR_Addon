package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.Main;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.customItem.contents.SignOnWallContent;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigSpec;
import com.fangsu.extraConfig.NumberInputConfig;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.drawing.sign.SignDrawContext;
import com.fangsu.drawing.sign.SignItem;
import com.fangsu.drawing.sign.SignItemFactory;
import com.fangsu.utils.CollisionBoxUtil;
import com.fangsu.utils.ContentInfoUtil;
import com.fangsu.utils.CustomItemHelper;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_SIGN_ON_WALL;

import com.fangsu.blocks.BaseObjBlock;

public class BlockEntitySignOnWall extends BaseObjBlockEntity implements Syncable {
    private static final String DEFAULT_MAIN_MODEL = "fangsu:sign/beijing/beijing_sign.json";
    private static final String DEFAULT_SUB_MODEL = "beijing_sign_a_onwall";
    private static final String MAIN_MODEL_KEY = "sign";
    protected String subModel;


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
            SignOnWallContent.SignOnWallDisplayInfo displayInfo = ContentInfoUtil.getSignOnWallDisplayInfo(mainModel, subModel);
            if (displayInfo == null) {
                markedError = true;
                return;
            }
            Map<String, DynamicModelHolder> models = ResourceUtil.loadPartedDmh(new ResourceLocation(displayInfo.model()), displayInfo.flipV());
            unit = displayInfo.unit();
            {
                Map<?, ?> main = displayInfo.main();
                String modelKey = (String) main.get("subModel");
                dmhCenter = models.get(modelKey);
                if (main.containsKey("shape") && main.get("shape") instanceof List<?> l)
                    shapeCenter = new CollisionBoxUtil.CollisionBox(l);
            }
            {
                Map<?, ?> side = displayInfo.side();
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
            List<?> texZone = displayInfo.tex();
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


            if (gtFront != null && !gtFront.isClosed.get()) {
                var g = gtFront.graphics;
                g.setComposite(AlphaComposite.Clear); // 璁剧疆閫忔槑娣峰悎妯″紡
                g.fillRect(0, 0, gtFront.width, gtFront.height);   // 濉厖鏁翠釜鍖哄煙
                g.setComposite(AlphaComposite.SrcOver); // 鎭㈠榛樿娣峰悎妯″紡
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
    public InteractionResult whenUseWithBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ClientHooks.openSignConfigScreen(1, List.of(items), saveItems -> {
            items = saveItems.get(0);
            extraConfigs.put("items", toItemsJson(items).toString());
            requiresRedraw = true;
            sendUpdateC2S();
        });
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult whenUseWithOther(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
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
                ComponentHelper.translatable("ui.fangsu.common.length"),
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
    public VoxelShape setCollisionShape(BlockState state) {
        return getFinalShape(state);
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        return getFinalShape(state);
    }

    private VoxelShape getFinalShape(BlockState state) {
        Direction facing = state.getValue(BaseObjBlock.FACING);
        Vec3 trans = transformOffset(facing, new Vec3(translateX, translateY, translateZ));
        float rotX = this.rotateX;
        float rotY = this.rotateY + (float) Math.toRadians(-facing.toYRot());
        float rotZ = this.rotateZ;
        long posLong = worldPosition.asLong();

        VoxelShape shape = Shapes.empty();
        double startX = -0.5 * unit * length / 16d;
        double n = length / (unit / 8d);

        // 灏嗗眬閮╔杞村亸绉绘寜鏈濆悜鏃嬭浆鍒颁笘鐣屽潗锟?
        java.util.function.Function<Double, Vec3> localToWorld = (localX) -> {
            Vec3 v = new Vec3(localX, 0, 0);
            return v.yRot((float) Math.toRadians(-facing.toYRot()));
        };

        if (shapeLeft != null) {
            VoxelShape s = CollisionBoxUtil.cachedRotatedShape(posLong, shapeLeft, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
            Vec3 offset = localToWorld.apply(startX);
            shape = Shapes.or(shape, s.move(offset.x + trans.x, trans.y, offset.z + trans.z));
        }
        for (int i = 0; i < n; i++) {
            if (shapeCenter != null) {
                double localOffsetX = startX + unit / 32d + i * unit / 16d;
                Vec3 offset = localToWorld.apply(localOffsetX);
                VoxelShape s = CollisionBoxUtil.cachedRotatedShape(posLong, shapeCenter, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
                shape = Shapes.or(shape, s.move(offset.x + trans.x, trans.y, offset.z + trans.z));
            }
        }
        if (shapeRight != null) {
            // right鍦ㄦ覆鏌撲腑鐨勪綅锟? startX + n * unit/16
            double rightLocalX = startX + n * unit / 16d;
            Vec3 offset = localToWorld.apply(rightLocalX);
            VoxelShape s = CollisionBoxUtil.cachedRotatedShape(posLong, shapeRight, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
            shape = Shapes.or(shape, s.move(offset.x + trans.x, trans.y, offset.z + trans.z));
        }
        return shape;
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        infos.add(createSubModelSelectInfo("on_wall", DEFAULT_SUB_MODEL));
        infos.add(new SubModelMethodInfo(ComponentHelper.translatable("ui.fangsu.sign.editSign"), () -> {
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

        // 濡傛灉闇€瑕佸弽杞硶鍚戯紙姣斿鑳岄潰锟?
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
