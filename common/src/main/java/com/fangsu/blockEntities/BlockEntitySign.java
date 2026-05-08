package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.customItem.contents.SignContent;
import com.fangsu.extraConfig.BoolConfig;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigSpec;
import com.fangsu.extraConfig.NumberInputConfig;
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

import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.blocks.BaseObjBlock;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_SIGN;

public class BlockEntitySign extends BaseObjBlockEntity implements Syncable {
    private static final String DEFAULT_MAIN_MODEL = "fangsu:sign/mtr_sign/mtr_sign.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_sign_a";
    private static final String MAIN_MODEL_KEY = "sign";
    protected String subModel;


    private DynamicModelHolder dmhLeft, dmhCenter, dmhRight, dmhDispFront, dmhDispBack, dmhPole;
    private GraphicsTexture gtFront, gtBack;
    private CollisionBoxUtil.CollisionBox shapeLeft, shapeCenter, shapeRight, shapePole;
    private int unit = 8;

    private double length = 2;
    private boolean showLeftPole = true, showRightPole = true;
    private int leftPolePos = 8, rightPolePos = 8;
    private boolean isMtrTheme = false;
    private double mtrPoleOffset = 0d;
    private int defaultBgColor = -1;

    private boolean requiresRedraw = true;

    private Map<String, List<SignItem>> itemsFront, itemsBack;
    private boolean frontReady, frontCompleted, backReady, backCompleted;

    public BlockEntitySign(BlockPos pos, BlockState state) {
        super(BLOCK_ENTITY_SIGN.get(), pos, state);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("length", "2");
        ensureExtraConfig("itemsFront", "{}");
        ensureExtraConfig("itemsBack", "{}");
        ensureExtraConfig("showLeftPole", "true");
        ensureExtraConfig("leftPolePos", "8");
        ensureExtraConfig("showRightPole", "true");
        ensureExtraConfig("rightPolePos", "8");

        itemsFront = initItems(extraConfigs.get("itemsFront"));
        itemsBack = initItems(extraConfigs.get("itemsBack"));

        length = Double.parseDouble(extraConfigs.get("length"));
        showLeftPole = "true".equals(extraConfigs.get("showLeftPole"));
        showRightPole = "true".equals(extraConfigs.get("showRightPole"));
        leftPolePos = Integer.parseInt(extraConfigs.get("leftPolePos"));
        rightPolePos = Integer.parseInt(extraConfigs.get("rightPolePos"));

        mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);

        try {
            SignContent.SignDisplayInfo displayInfo = ContentInfoUtil.getSignDisplayInfo(mainModel, subModel);
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

            {
                Map<?, ?> pole = displayInfo.pole();
                String modelKey = (String) pole.get("subModel");
                dmhPole = models.get(modelKey);
                if (pole.containsKey("shape") && pole.get("shape") instanceof List<?> l) {
                    shapePole = new CollisionBoxUtil.CollisionBox(l);
                }
            }

            isMtrTheme = displayInfo.isMtrTheme();
            mtrPoleOffset = displayInfo.mtrPoleOffset();
            defaultBgColor = displayInfo.defaultBgColor();

            RawMeshBuilder rawModelBuilderFront = new RawMeshBuilder(4, "lighttranslucent", new ResourceLocation("fangsu:sign/def_face1.png")),
                    rawMeshBuilderBack = new RawMeshBuilder(4, "lighttranslucent", new ResourceLocation("fangsu:sign/def_face1.png"));
            RawModel dispRawModelFront = new RawModel(),
                    dispRawModelBack = new RawModel();
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
            List<List<Double>> finalSlotBack = List.of(
                    List.of(0.5 * unit * length / 16, y2, -z2),
                    List.of(0.5 * unit * length / 16, y1, -z1),
                    List.of(-0.5 * unit * length / 16, y1, -z1),
                    List.of(-0.5 * unit * length / 16, y2, -z2)
            );
            addQuad(rawModelBuilderFront, finalSlotFront, false);
            addQuad(rawMeshBuilderBack, finalSlotBack, true);
            dispRawModelFront.append(rawModelBuilderFront.getMesh());
            dispRawModelFront.generateNormals();
            dispRawModelBack.append(rawMeshBuilderBack.getMesh());
            dispRawModelBack.generateNormals();
            dmhDispFront = new DynamicModelHolder();
            dmhDispFront.uploadLater(dispRawModelFront);
            dmhDispBack = new DynamicModelHolder();
            dmhDispBack.uploadLater(dispRawModelBack);

            frontReady = false;
            frontCompleted = false;
            backReady = false;
            backCompleted = false;

            requiresRedraw = true;
        } catch (Exception e) {
            Main.LOGGER.warn(e.getMessage());
        }
    }

    @Override
    public void whenRendering() {
        ObjBlockScriptContext ctx = this.scriptContext;
        if (requiresRedraw) {
            itemsFront = initItems(extraConfigs.get("itemsFront"));
            itemsBack = initItems(extraConfigs.get("itemsBack"));

            if (gtFront != null) gtFront.closeLater();
            if (gtBack != null) gtBack.closeLater();
            gtFront = new GraphicsTexture((int) (unit * 72 * length + 1), unit * 72 + 1);
            gtBack = new GraphicsTexture((int) (unit * 72 * length + 1), unit * 72 + 1);


            if (gtFront != null && !gtFront.isClosed.get()) {
                var g = gtFront.graphics;
                boolean frontIsEmpty =
                        itemsFront.isEmpty() ||
                                ((itemsFront.containsKey("left") && itemsFront.get("left").isEmpty()) &&
                                        (itemsFront.containsKey("right") && itemsFront.get("right").isEmpty()) &&
                                        (itemsFront.containsKey("center") && itemsFront.get("center").isEmpty())
                                );
                if (defaultBgColor == -1 || frontIsEmpty) {
                    g.setComposite(AlphaComposite.Clear); // 设置透明混合模式
                    g.fillRect(0, 0, gtFront.width, gtFront.height);   // 填充整个区域
                    g.setComposite(AlphaComposite.SrcOver); // 恢复默认混合模式
                } else {
                    g.setColor(new Color(defaultBgColor));
                    g.fillRect(0, 0, gtFront.width, gtFront.height);
                }
                if (itemsFront != null) {
                    if (itemsFront.containsKey("left")) {
                        if (checkAllReady(gtFront.graphics, gtFront.height * 0.8f, itemsFront.get("left")))
                            drawLane(gtFront, itemsFront.get("left"), 0, gtFront.height * 0.1f, 0, gtFront.height * 0.8f);
                    }
                    if (itemsFront.containsKey("right")) {
                        if (checkAllReady(gtFront.graphics, gtFront.height * 0.8f, itemsFront.get("right")))
                            drawLane(gtFront, itemsFront.get("right"), gtFront.width, gtFront.height * 0.1f, 2, gtFront.height * 0.8f);
                    }
                    if (itemsFront.containsKey("center")) {
                        if (checkAllReady(gtFront.graphics, gtFront.height * 0.8f, itemsFront.get("center")))
                            drawLane(gtFront, itemsFront.get("center"), gtFront.width * 0.5f, gtFront.height * 0.1f, 1, gtFront.height * 0.8f);
                    }
                }
                frontCompleted = checkCompleted(itemsFront);
                if (frontCompleted)
                    gtFront.upload();
            }
            if (gtBack != null && !gtBack.isClosed.get()) {
                var g = gtBack.graphics;
                boolean backIsEmpty =
                        itemsBack.isEmpty() ||
                                ((itemsBack.containsKey("left") && itemsBack.get("left").isEmpty()) &&
                                        (itemsBack.containsKey("right") && itemsBack.get("right").isEmpty()) &&
                                        (itemsBack.containsKey("center") && itemsBack.get("center").isEmpty())
                                );
                if (defaultBgColor == -1 || backIsEmpty) {
                    g.setComposite(AlphaComposite.Clear); // 设置透明混合模式
                    g.fillRect(0, 0, gtFront.width, gtFront.height);   // 填充整个区域
                    g.setComposite(AlphaComposite.SrcOver); // 恢复默认混合模式
                } else {
                    g.setColor(new Color(defaultBgColor));
                    g.fillRect(0, 0, gtFront.width, gtFront.height);
                }
                if (itemsBack != null) {
                    if (itemsBack.containsKey("left"))
                        if (checkAllReady(gtBack.graphics, gtBack.height * 0.8f, itemsBack.get("left")))
                            drawLane(gtBack, itemsBack.get("left"), 0, gtBack.height * 0.1f, 0, gtBack.height * 0.8f);
                    if (itemsBack.containsKey("right"))
                        if (checkAllReady(gtBack.graphics, gtBack.height * 0.8f, itemsBack.get("right")))
                            drawLane(gtBack, itemsBack.get("right"), gtBack.width, gtFront.height * 0.1f, 2, gtBack.height * 0.8f);
                    if (itemsBack.containsKey("center"))
                        if (checkAllReady(gtBack.graphics, gtBack.height * 0.8f, itemsBack.get("center")))
                            drawLane(gtBack, itemsBack.get("center"), gtBack.width * 0.5f, gtFront.height * 0.1f, 1, gtBack.height * 0.8f);
                }
                backCompleted = checkCompleted(itemsBack);
                if (backCompleted)
                    gtBack.upload();
            }
            requiresRedraw = !(frontCompleted && backCompleted);
        }
        if (dmhDispFront != null && dmhDispFront.getUploadedModel() != null) {
            dmhDispFront.getUploadedModel().replaceAllTexture(gtFront.identifier);
        }
        ctx.drawModel(dmhDispFront, null);
        if (dmhDispBack != null && dmhDispBack.getUploadedModel() != null) {
            dmhDispBack.getUploadedModel().replaceAllTexture(gtBack.identifier);
        }
        ctx.drawModel(dmhDispBack, null);

        Matrices mat = new Matrices();
        mat.pushPose();
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
        if (dmhPole != null) {
            if (showLeftPole) {
                mat.pushPose();
                if (!isMtrTheme)
                    mat.translate(leftPolePos / 16d, 0, 0);
                else mat.translate(mtrPoleOffset / 16d, 0, 0);
                ctx.drawModel(dmhPole, mat);
                mat.popPose();
            }
            if (showRightPole) {
                mat.pushPose();
                mat.translate(unit * length / 16, 0, 0);
                if (!isMtrTheme)
                    mat.translate(-rightPolePos / 16d, 0, 0);
                else mat.translate(-mtrPoleOffset / 16d, 0, 0);
                ctx.drawModel(dmhPole, mat);
                mat.popPose();
            }
        } else mat.popPose();
    }

    private boolean checkAllReady(Graphics2D g, float unit, List<SignItem> items) {
        boolean allReady = true;
        for (SignItem item : items) {
            item.getWidth(g, unit);
            allReady &= item.isReady();
        }
        return allReady;
    }

    private boolean checkCompleted(Map<String, List<SignItem>> items) {
        if (items == null) return true;
        boolean completed = true;
        for (List<SignItem> item : items.values()) {
            if (item == null) continue;
            for (SignItem i : item) {
                completed &= i.isReady();
            }
        }
        return completed;
    }

    @Override
    public InteractionResult whenUseWithBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ClientHooks.openSignConfigScreen(2, List.of(itemsFront, itemsBack), (saveItems) -> {
            itemsFront = saveItems.get(0);
            itemsBack = saveItems.get(1);
            extraConfigs.put("itemsFront", toItemsJson(itemsFront).toString());
            extraConfigs.put("itemsBack", toItemsJson(itemsBack).toString());
            requiresRedraw = true;
            sendUpdateC2S();
        });
        return InteractionResult.SUCCESS;
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
        configs.add(new BoolConfig(
                Component.translatable("ui.fangsu.sign.dispLeftPole"),
                new ConfigSpec("bool"),
                () -> this.showLeftPole,
                (v) -> {
                    this.showLeftPole = v;
                    extraConfigs.put("showLeftPole", showLeftPole ? "true" : "false");
                }
        ).setSaveOnChange(true));
        configs.add(new NumberInputConfig(
                Component.translatable("ui.fangsu.sign.leftPolePos"),
                new ConfigSpec("num").setParam("isInt", new JsonPrimitive(true)),
                () -> this.leftPolePos + 0f,
                (v) -> {
                    this.leftPolePos = v.intValue();
                    extraConfigs.put("leftPolePos", leftPolePos + "");
                }
        ).setShowCondition((v) -> this.showLeftPole && (!isMtrTheme)));
        configs.add(new BoolConfig(
                Component.translatable("ui.fangsu.sign.dispRightPole"),
                new ConfigSpec("bool"),
                () -> this.showRightPole,
                (v) -> {
                    this.showRightPole = v;
                    extraConfigs.put("showRightPole", showRightPole ? "true" : "false");
                }
        ).setSaveOnChange(true));
        configs.add(new NumberInputConfig(
                Component.translatable("ui.fangsu.sign.rightPolePos"),
                new ConfigSpec("num").setParam("isInt", new JsonPrimitive(true)),
                () -> this.rightPolePos + 0f,
                (v) -> {
                    this.rightPolePos = v.intValue();
                    extraConfigs.put("rightPolePos", rightPolePos + "");
                }
        ).setShowCondition((v) -> this.showRightPole && (!isMtrTheme)));
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
        Vec3 unitTrans = transformOffset(facing, new Vec3(unit / 16d, 0, 0));

        VoxelShape shape = Shapes.empty();
        double startX = -0.5 * unit * length / 16d;
//        if (shapeLeft != null) {
//            VoxelShape s = CollisionBoxUtil.cachedRotatedShape(posLong, shapeLeft, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
//            shape = Shapes.or(shape, s.move(startX + (unit / 16d) + trans.x, trans.y, trans.z));
//        }
        for (int i = 0; i < length / (unit / 8d); i++) {
            if (shapeCenter != null) {
                double x = startX + unit / 32d + i * unitTrans.x;
                double z = startX + unit / 32d + i * unitTrans.z;
                VoxelShape s = CollisionBoxUtil.cachedRotatedShape(posLong, shapeCenter, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
                shape = Shapes.or(shape, s.move(x + trans.x, trans.y, z + trans.z));
            }
        }
//        if (shapeRight != null) {
//            double x = startX + unit / 32d + (length / (unit / 8d)) * (unit / 16d) + unit / 32d;
//            VoxelShape s = CollisionBoxUtil.cachedRotatedShape(posLong, shapeRight, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
//            shape = Shapes.or(shape, s.move(x + (unit / 16d) + trans.x, trans.y, trans.z));
//        }
//        if (shapePole != null) {
//            if (showLeftPole) {
//                double x = startX +
//                        (!isMtrTheme ?
//                                leftPolePos / 16d : mtrPoleOffset / 16d);
//                VoxelShape s = CollisionBoxUtil.cachedRotatedShape(posLong, shapePole, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
//                shape = Shapes.or(shape, s.move(x + trans.x, trans.y, trans.z));
//            }
//            if (showRightPole) {
//                double x = startX + (unit * length) / 16d - (!isMtrTheme ? rightPolePos / 16d : mtrPoleOffset / 16d);
//                VoxelShape s = CollisionBoxUtil.cachedRotatedShape(posLong, shapePole, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
//                shape = Shapes.or(shape, s.move(x + trans.x, trans.y, trans.z));
//            }
//        }
        return shape;
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        infos.add(createSubModelSelectInfo("common", DEFAULT_SUB_MODEL));
        infos.add(new SubModelMethodInfo(Component.translatable("ui.fangsu.sign.editSign"), () -> {
            if (itemsFront == null) itemsFront = new HashMap<>();
            if (itemsBack == null) itemsBack = new HashMap<>();
            ClientHooks.openSignConfigScreen(2, List.of(itemsFront, itemsBack), (saveItems) -> {
                itemsFront = saveItems.get(0);
                itemsBack = saveItems.get(1);
                extraConfigs.put("itemsFront", toItemsJson(itemsFront).toString());
                extraConfigs.put("itemsBack", toItemsJson(itemsBack).toString());
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

        itemsFront = initItems(extraConfigs.get("itemsFront"));
        itemsBack = initItems(extraConfigs.get("itemsBack"));

        length = Double.parseDouble(extraConfigs.getOrDefault("length", "2"));
        showLeftPole = "true".equals(extraConfigs.get("showLeftPole"));
        showRightPole = "true".equals(extraConfigs.get("showRightPole"));
        leftPolePos = Integer.parseInt(extraConfigs.getOrDefault("leftPolePos", "8"));
        rightPolePos = Integer.parseInt(extraConfigs.getOrDefault("rightPolePos", "8"));

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
