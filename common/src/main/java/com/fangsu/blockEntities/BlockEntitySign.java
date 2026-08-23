package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
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
import com.fangsu.drawing.sign.SignFaceData;
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

public class BlockEntitySign extends FunctionalObjBlockEntity {
    private static final String DEFAULT_MAIN_MODEL = "fangsu:sign/mtr_sign/mtr_sign.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_sign_a";
    private static final String MAIN_MODEL_KEY = "sign";
    protected String subModel;


    private DynamicModelHolder dmhLeft, dmhCenter, dmhRight, dmhPole;
    private CollisionBoxUtil.CollisionBox shapeLeft, shapeCenter, shapeRight, shapePole;
    private int unit = 8;

    private double length = 2;
    private boolean showLeftPole = true, showRightPole = true;
    private int leftPolePos = 8, rightPolePos = 8;
    private boolean isMtrTheme = false;
    private double mtrPoleOffset = 0d;
    private int defaultBgColor = -1;

    private boolean requiresRedraw = true;

    private List<FaceDisplay> displays = new ArrayList<>();

    /** 每个显示面：名称 + 两个角点 + 纹理/模型 + 用户数据。 */
    private static class FaceDisplay {
        final String name;
        final double y1, z1, y2, z2;
        GraphicsTexture gt;
        DynamicModelHolder dmh;
        SignFaceData data; // items + bgColor
        boolean completed;

        FaceDisplay(String name, double y1, double z1, double y2, double z2) {
            this.name = name;
            this.y1 = y1;
            this.z1 = z1;
            this.y2 = y2;
            this.z2 = z2;
        }
    }

    public BlockEntitySign(BlockPos pos, BlockState state) {
        super(BLOCK_ENTITY_SIGN.get(), pos, state);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("length", "2");
        ensureExtraConfig("faces", "[]");
        ensureExtraConfig("showLeftPole", "true");
        ensureExtraConfig("leftPolePos", "8");
        ensureExtraConfig("showRightPole", "true");
        ensureExtraConfig("rightPolePos", "8");

        length = Double.parseDouble(extraConfigs.get("length"));
        showLeftPole = "true".equals(extraConfigs.get("showLeftPole"));
        showRightPole = "true".equals(extraConfigs.get("showRightPole"));
        leftPolePos = Integer.parseInt(extraConfigs.get("leftPolePos"));
        rightPolePos = Integer.parseInt(extraConfigs.get("rightPolePos"));

        mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);

        // 服务端不需要加载模型和形状，跳过客户端专属操作（initItems会触发SignItemFactory加载客户端类）
        if (level == null || !level.isClientSide) return;

        // 服务端不需要加载模型和形状，跳过客户端专属操作
        if (level == null || !level.isClientSide) return;

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

            // 按 tex 枚举面，构建每个面的显示四边形与模型
            displays = new ArrayList<>();
            List<String> texFaces = displayInfo.texFaces();
            List<SignFaceData> cfgFaces = loadFaceData();
            for (int i = 0; i < texFaces.size(); i++) {
                String name = texFaces.get(i);
                double[] corners = displayInfo.texCorners(name);
                if (corners == null) continue;
                FaceDisplay fd = new FaceDisplay(name, corners[0], corners[1], corners[2], corners[3]);
                // 始终以 tex 面名为准，配置只提供 items/bgColor（按名匹配，其次按位置）
                SignFaceData data = findFaceByName(cfgFaces, name);
                if (data == null && i < cfgFaces.size()) data = cfgFaces.get(i);
                if (data != null) {
                    fd.data = new SignFaceData(name, data.getLanes(), data.getBgColor());
                } else {
                    fd.data = new SignFaceData(name, new HashMap<>(), 0);
                }
                displays.add(fd);
            }
            for (FaceDisplay fd : displays) {
                RawMeshBuilder builder = new RawMeshBuilder(4, "lighttranslucent", new ResourceLocation("fangsu:sign/def_face1.png"));
                RawModel raw = new RawModel();
                boolean isBack = fd.z1 < 0;
                double x = 0.5 * unit * length / 16;
                List<List<Double>> slot;
                if (isBack) {
                    // 背面：x 顺序反向（纹理左右镜像）+ 法线反转
                    slot = List.of(
                            List.of(x, fd.y2, fd.z2),
                            List.of(x, fd.y1, fd.z1),
                            List.of(-x, fd.y1, fd.z1),
                            List.of(-x, fd.y2, fd.z2));
                } else {
                    slot = List.of(
                            List.of(-x, fd.y2, fd.z2),
                            List.of(-x, fd.y1, fd.z1),
                            List.of(x, fd.y1, fd.z1),
                            List.of(x, fd.y2, fd.z2));
                }
                addQuad(builder, slot, isBack);
                raw.append(builder.getMesh());
                raw.generateNormals();
                fd.dmh = new DynamicModelHolder();
                fd.dmh.uploadLater(raw);
            }

            requiresRedraw = true;
        } catch (Exception e) {
            Main.LOGGER.warn(e.getMessage());
        }
    }

    @Override
    public void whenRendering() {
        ObjBlockScriptContext ctx = this.scriptContext;
        if (requiresRedraw) {
            List<SignFaceData> cfg = loadFaceData();
            for (int i = 0; i < displays.size(); i++) {
                FaceDisplay fd = displays.get(i);
                // 始终保留 tex 面名，只刷新 items/bgColor（按名匹配，其次按位置）
                SignFaceData data = findFaceByName(cfg, fd.name);
                if (data == null && i < cfg.size()) data = cfg.get(i);
                if (data != null) {
                    fd.data = new SignFaceData(fd.name, data.getLanes(), data.getBgColor());
                }
            }
            boolean allCompleted = true;
            for (FaceDisplay fd : displays) {
                if (fd.gt != null) fd.gt.closeLater();
                fd.gt = new GraphicsTexture((int) (unit * 72 * length + 1), unit * 72 + 1);
                drawFace(fd);
                allCompleted &= fd.completed;
                if (fd.completed) fd.gt.upload();
            }
            requiresRedraw = !allCompleted;
        }
        for (FaceDisplay fd : displays) {
            if (fd.dmh != null && fd.dmh.getUploadedModel() != null && fd.gt != null) {
                fd.dmh.getUploadedModel().replaceAllTexture(fd.gt.identifier);
                ctx.drawModel(fd.dmh, null);
            }
        }
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

    private void drawFace(FaceDisplay fd) {
        Map<String, List<SignItem>> items = fd.data != null ? fd.data.getLanes() : new HashMap<>();
        boolean isEmpty = items.isEmpty()
                || ((!items.containsKey("left") || items.get("left").isEmpty())
                && (!items.containsKey("right") || items.get("right").isEmpty())
                && (!items.containsKey("center") || items.get("center").isEmpty()));
        int userColor = fd.data != null ? fd.data.getBgColor() : 0;
        fillBg(fd.gt.graphics, fd.gt, userColor, isEmpty);
        if (items.containsKey("left"))
            if (checkAllReady(fd.gt.graphics, fd.gt.height * 0.8f, items.get("left")))
                drawLane(fd.gt, items.get("left"), 0, fd.gt.height * 0.1f, 0, fd.gt.height * 0.8f);
        if (items.containsKey("right"))
            if (checkAllReady(fd.gt.graphics, fd.gt.height * 0.8f, items.get("right")))
                drawLane(fd.gt, items.get("right"), fd.gt.width, fd.gt.height * 0.1f, 2, fd.gt.height * 0.8f);
        if (items.containsKey("center"))
            if (checkAllReady(fd.gt.graphics, fd.gt.height * 0.8f, items.get("center")))
                drawLane(fd.gt, items.get("center"), fd.gt.width * 0.5f, fd.gt.height * 0.1f, 1, fd.gt.height * 0.8f);
        fd.completed = checkCompleted(items);
    }

    private void fillBg(Graphics2D g, GraphicsTexture gt, int userColor, boolean isEmpty) {
        if (userColor != 0) {
            // 用户指定背景色优先（覆盖模型默认背景色）
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, gt.width, gt.height);
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(userColor));
            g.fillRect(0, 0, gt.width, gt.height);
        } else if (defaultBgColor != -1 && !isEmpty) {
            g.setColor(new Color(defaultBgColor));
            g.fillRect(0, 0, gt.width, gt.height);
        } else {
            // 透明背景
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, gt.width, gt.height);
            g.setComposite(AlphaComposite.SrcOver);
        }
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
        if (!level.isClientSide) return InteractionResult.SUCCESS;
        openSignEdit();
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
                ComponentHelper.translatable("ui.fangsu.common.length"),
                new ConfigSpec("num").setParam("isInt", new JsonPrimitive(true)).setParam("min", new JsonPrimitive(2)),
                () -> (float) (this.length),
                (v) -> {
                    this.length = v.intValue();
                    extraConfigs.put("length", length + "");
                }
        ));
        configs.add(new BoolConfig(
                ComponentHelper.translatable("ui.fangsu.sign.dispLeftPole"),
                new ConfigSpec("bool"),
                () -> this.showLeftPole,
                (v) -> {
                    this.showLeftPole = v;
                    extraConfigs.put("showLeftPole", showLeftPole ? "true" : "false");
                }
        ).setSaveOnChange(true));
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.sign.leftPolePos"),
                new ConfigSpec("num").setParam("isInt", new JsonPrimitive(true)),
                () -> this.leftPolePos + 0f,
                (v) -> {
                    this.leftPolePos = v.intValue();
                    extraConfigs.put("leftPolePos", leftPolePos + "");
                }
        ).setShowCondition((v) -> this.showLeftPole && (!isMtrTheme)));
        configs.add(new BoolConfig(
                ComponentHelper.translatable("ui.fangsu.sign.dispRightPole"),
                new ConfigSpec("bool"),
                () -> this.showRightPole,
                (v) -> {
                    this.showRightPole = v;
                    extraConfigs.put("showRightPole", showRightPole ? "true" : "false");
                }
        ).setSaveOnChange(true));
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.sign.rightPolePos"),
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
        if (shapePole != null) {
            if (showLeftPole) {
                double poleOffset = (!isMtrTheme ? leftPolePos : mtrPoleOffset) / 16d;
                double leftPoleLocalX = startX + poleOffset;
                Vec3 offset = localToWorld.apply(leftPoleLocalX);
                VoxelShape s = CollisionBoxUtil.cachedRotatedShape(posLong, shapePole, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
                shape = Shapes.or(shape, s.move(offset.x + trans.x, trans.y, offset.z + trans.z));
            }
            if (showRightPole) {
                double poleOffset = (!isMtrTheme ? rightPolePos : mtrPoleOffset) / 16d;
                double rightPoleLocalX = startX + (unit * length) / 16d - poleOffset;
                Vec3 offset = localToWorld.apply(rightPoleLocalX);
                VoxelShape s = CollisionBoxUtil.cachedRotatedShape(posLong, shapePole, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
                shape = Shapes.or(shape, s.move(offset.x + trans.x, trans.y, offset.z + trans.z));
            }
        }
        return shape;
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        infos.add(createSubModelSelectInfo("common", DEFAULT_SUB_MODEL));
        infos.add(new SubModelMethodInfo(ComponentHelper.translatable("ui.fangsu.sign.editSign"), () -> {
            openSignEdit();
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
        super.readC2S(buf);

        length = Double.parseDouble(extraConfigs.getOrDefault("length", "2"));
        showLeftPole = "true".equals(extraConfigs.get("showLeftPole"));
        showRightPole = "true".equals(extraConfigs.get("showRightPole"));
        leftPolePos = Integer.parseInt(extraConfigs.getOrDefault("leftPolePos", "8"));
        rightPolePos = Integer.parseInt(extraConfigs.getOrDefault("rightPolePos", "8"));

        // initItems 会触发 SignItemFactory 加载客户端类，仅在客户端执行
        if (level == null || !level.isClientSide) return;

        requiresRedraw = true;

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

    /* ===================== 新版 faces 存储（兼容旧版） ===================== */

    /** 读取 faces 配置；兼容旧版 itemsFront/itemsBack 存储（自动转换为新版并写回）。 */
    private List<SignFaceData> loadFaceData() {
        List<SignFaceData> faces = parseFaces(extraConfigs.getOrDefault("faces", "[]"));
        if (faces.isEmpty() && (extraConfigs.containsKey("itemsFront") || extraConfigs.containsKey("itemsBack"))) {
            faces = List.of(
                    new SignFaceData("front", initItems(extraConfigs.getOrDefault("itemsFront", "{}")),
                            parseIntSafe(extraConfigs.getOrDefault("bgColorFront", "0"))),
                    new SignFaceData("back", initItems(extraConfigs.getOrDefault("itemsBack", "{}")),
                            parseIntSafe(extraConfigs.getOrDefault("bgColorBack", "0")))
            );
            // 自动转换为新版存储并写回
            extraConfigs.put("faces", toFacesJson(faces).toString());
        }
        return faces;
    }

    /** 按面名在配置中查找对应的面数据，找不到返回 null。 */
    private SignFaceData findFaceByName(List<SignFaceData> faces, String name) {
        if (faces == null || name == null) return null;
        for (SignFaceData f : faces) {
            if (name.equals(f.getName())) return f;
        }
        return null;
    }

    /** 打开指示牌内容编辑（把所有显示面传给 UI，保存时写回）。 */
    private void openSignEdit() {
        List<SignFaceData> current = new ArrayList<>();
        for (FaceDisplay fd : displays) {
            current.add(fd.data != null ? fd.data : new SignFaceData(fd.name, new HashMap<>(), 0));
        }
        ClientHooks.openSignConfigScreen(current, (saveItems) -> {
            for (int i = 0; i < displays.size() && i < saveItems.size(); i++) {
                displays.get(i).data = saveItems.get(i);
            }
            extraConfigs.put("faces", toFacesJson(saveItems).toString());
            requiresRedraw = true;
            sendUpdateC2S();
        });
    }

    /**
     * 解析 faces 配置。
     * 新版：数组内元素为 {name, items, bgColor}；
     * 旧扩展包写法：数组内元素为三列对象 {left,center,right}，按位置第一个 front、第二个 back。
     */
    private List<SignFaceData> parseFaces(String src) {
        List<SignFaceData> result = new ArrayList<>();
        if (src == null || src.isBlank()) return result;
        try {
            JsonArray arr = Main.JSON_PARSER.parse(src).getAsJsonArray();
            int index = 0;
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) continue;
                JsonObject obj = e.getAsJsonObject();
                String name;
                Map<String, List<SignItem>> lanes;
                int bg;
                if (obj.has("items") && obj.get("items").isJsonObject()) {
                    // 新版：{name, items, bgColor}
                    name = obj.has("name") && !obj.get("name").getAsString().isEmpty()
                            ? obj.get("name").getAsString() : faceNameFor(index);
                    lanes = initItems(obj.get("items").getAsJsonObject().toString());
                    bg = readBgColor(obj.get("bgColor"));
                } else if (obj.has("left") || obj.has("center") || obj.has("right")) {
                    // 旧扩展包数组形式：元素即三列对象
                    name = faceNameFor(index);
                    lanes = initItems(obj.toString());
                    bg = 0;
                } else {
                    name = faceNameFor(index);
                    lanes = new HashMap<>();
                    bg = 0;
                }
                result.add(new SignFaceData(name, lanes, bg));
                index++;
            }
        } catch (Exception e) {
            Main.LOGGER.warn("解析指示牌 faces 配置失败: " + src);
        }
        return result;
    }

    /** 序列化为新版 faces 数组：[{name, items, bgColor}, ...] */
    private JsonArray toFacesJson(List<SignFaceData> faces) {
        JsonArray arr = new JsonArray();
        if (faces == null) return arr;
        for (SignFaceData face : faces) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", face.getName() == null || face.getName().isEmpty() ? faceNameFor(arr.size()) : face.getName());
            obj.add("items", toItemsJson(face.getLanes()));
            obj.addProperty("bgColor", face.getBgColor());
            arr.add(obj);
        }
        return arr;
    }

    private String faceNameFor(int index) {
        return switch (index) {
            case 0 -> "front";
            case 1 -> "back";
            default -> "face" + (index + 1);
        };
    }

    private int readBgColor(JsonElement e) {
        if (e == null || e.isJsonNull()) return 0;
        try {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsInt();
            String s = e.getAsString().trim();
            if (s.startsWith("0x") || s.startsWith("0X")) return Integer.parseUnsignedInt(s.substring(2), 16);
            if (s.startsWith("#")) return Integer.parseUnsignedInt(s.substring(1), 16);
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
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
