package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mappings.GsonHelper;
import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.customItem.contents.PidsContent;
import com.fangsu.drawing.pids.BasePidsDrawing;
import com.fangsu.drawing.pids.PidsDrawManager;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.shape.RawShape;
import com.fangsu.shape.RotatableShapeHelper;
import com.fangsu.shape.ShapeCollection;
import com.fangsu.shape.ShapeUtil;
import com.fangsu.extraConfig.*;
import com.fangsu.utils.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_PIDS;

public class BlockEntityPids extends FunctionalObjBlockEntity {
    private static final String DEFAULT_MAIN_MODEL = "fangsu:pids/mtr_pids.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_pids_3b";
    private static final String MAIN_MODEL_KEY = "pids";

    protected String subModel;

    private DynamicModelHolder dmhMain, dmhDisp = new DynamicModelHolder();
    private ShapeCollection shape;
    private Map<String, JsonElement> userExtraConfigs;

    private volatile BasePidsDrawing pidsDrawing;
    private int texW, texH;
    private Map<String, Object> drawState = new ConcurrentHashMap<>();
    private String drawScriptKey;

    private List<Long> plats;

    public BlockEntityPids(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_PIDS.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("extraConfig", "{}");
        ensureExtraConfig("plats", "[]");
        mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
        pidsDrawing = null;
        drawState.clear();
        List<JsonElement> rawPlats = GsonHelper.asList(Main.JSON_PARSER.parse(getExtraConfig("plats")).getAsJsonArray());
        plats = new ArrayList<>();
        for (JsonElement rawPlat : rawPlats) {
            plats.add(rawPlat.getAsLong());
        }

        try {
            userExtraConfigs = GsonHelper.asMap(Main.JSON_PARSER.parse(getExtraConfig("extraConfig", "{}")).getAsJsonObject());
        } catch (Throwable ignored) {
            userExtraConfigs = new ConcurrentHashMap<>();
        }

        // 服务端不需要加载模型和绘制，跳过客户端专属操作
        if (level == null || !level.isClientSide) return;

        try {
            PidsContent content = ContentInfoUtil.getPidsContent(mainModel, subModel);
            if (content == null) {
                if (level != null && level.isClientSide) markedError = true;
                return;
            }
            if (!content.getScript().isEmpty()) {
                drawScriptKey = content.getScript();
            }
            boolean flipV = content.isFlipV();
            String model = content.getModel();

            if (content.isShouldSpilt()) {
                // ===== 拼接（shouldSpilt）模式：宽度/高度网格拼接，参照 AdvBoard =====
                int width = getExtraConfigInt("width", 3);
                int height = getExtraConfigInt("height", 2);
                loadSpiltContent(content, flipV, width, height);
            } else {
                List<Integer> texSize = content.getTexSize();
                texW = texSize.size() > 0 ? texSize.get(0) : 128;
                texH = texSize.size() > 1 ? texSize.get(1) : 128;
                Main.LOGGER.info("texW={}, texH={}", texW, texH);
                dmhMain = ResourceUtil.loadDmh(new ResourceLocation(model), flipV);
                if (!content.getSlots().isEmpty()) {
                    RawMeshBuilder builder = new RawMeshBuilder(4, "light", new ResourceLocation("fangsu:pids/black.png"));
                    for (List<List<Double>> currentSlot : content.getSlots()) {
                        List<List<Double>> finalList = new ArrayList<>();
                        for (List<Double> point : currentSlot) {
                            if (point.size() == 3) finalList.add(point);
                        }
                        if (finalList.size() != 4) {
                            Main.LOGGER.warn("Invalid slot quad data: {}", currentSlot);
                            continue;
                        }
                        ModelHelper.addQuad(builder, finalList, false);
                    }
                    RawModel dispRawModel = new RawModel();
                    dispRawModel.append(builder.getMesh());
                    dispRawModel.generateNormals();
                    dmhDisp.uploadLater(dispRawModel);
                }
                if (!content.getShape().isEmpty()) {
                    this.shape = buildShapeCollection(content.getShape());
                }
            }
            if (drawScriptKey != null) {
                initDrawingAsync();
            }
        } catch (Exception e) {
            Main.LOGGER.warn(e.getMessage());
            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                Main.LOGGER.warn(stackTraceElement.toString());
            }
            if (level != null && level.isClientSide) markedError = true;
        }
    }

    /**
     * 拼接（shouldSpilt）模式：从 parted 模型加载 9 个子模型，按 宽×高 网格拼接出完整模型、
     * 显示面与碰撞形状。移植自旧版 pids.js 的宽度/高度拼接功能，实现参照 AdvBoard。
     */
    private void loadSpiltContent(PidsContent content, boolean flipV, int width, int height) throws Exception {
        String model = content.getModel();

        // dmhMain 字段默认未初始化（声明时仅 dmhDisp 赋值），拼接路径需在此创建
        dmhMain = new DynamicModelHolder();

        var rawModelMap = ResourceUtil.loadPartedModel(new ResourceLocation(model), flipV);
        var subModelKeyMap = content.getSubModels();
        RawModel[] models = new RawModel[PidsContent.MODEL_KEYS.length];
        for (int i = 0; i < PidsContent.MODEL_KEYS.length; i++) {
            String key = PidsContent.MODEL_KEYS[i];
            String subKey = subModelKeyMap.getOrDefault(key, key);
            RawModel m = rawModelMap.get(subKey);
            if (m == null) {
                throw new RuntimeException("Submodel " + subKey + " not found in " + model);
            }
            models[i] = m.copy();
        }

        RawModel spiltModel = ModelHelper.buildSpiltModel(
                models[0], models[1], models[2],
                models[3], models[4], models[5],
                models[6], models[7], models[8],
                width, height, content.getWidthUnit(), content.getHeightUnit()
        );
        if (content.getOffset() != null) {
            spiltModel.applyTranslation(content.getOffset()[0], content.getOffset()[1], content.getOffset()[2]);
        }
        dmhMain.uploadLater(spiltModel);

        // 显示面：跨越整个拼接网格的前/后面板，尺寸由 bar 边框决定（参照 AdvBoard）
        double x1 = -0.5 * content.getWidthUnit() * width + content.getBars().getOrDefault(PidsContent.BAR_KEYS[0], 0d); // 左
        double x2 = 0.5 * content.getWidthUnit() * width - content.getBars().getOrDefault(PidsContent.BAR_KEYS[1], 0d); // 右
        double y1 = content.getHeightUnit() * height - content.getBars().getOrDefault(PidsContent.BAR_KEYS[2], 0d);   // 上
        double y2 = content.getBars().getOrDefault(PidsContent.BAR_KEYS[3], 0d);                                        // 下

        RawModel dispRawModel = new RawModel();
        for (Map.Entry<String, Double> side : content.getFaces().entrySet()) {
            boolean isBack = !(side.getKey().toLowerCase().contains("front") || side.getKey().equalsIgnoreCase("front"));
            double z = side.getValue();
            RawMeshBuilder builder = new RawMeshBuilder(4, "light", new ResourceLocation("fangsu:pids/black.png"));

            double[] pos1 = new double[]{isBack ? -x1 : x1, y1, z};
            double[] pos2 = new double[]{isBack ? -x1 : x1, y2, z};
            double[] pos3 = new double[]{isBack ? -x2 : x2, y2, z};
            double[] pos4 = new double[]{isBack ? -x2 : x2, y1, z};

            ModelHelper.addQuad(builder, new double[][]{pos1, pos2, pos3, pos4}, false);
            RawModel faceModel = new RawModel();
            faceModel.append(builder.getMesh());
            faceModel.generateNormals();
            if (content.getOffset() != null) {
                faceModel.applyTranslation(content.getOffset()[0], content.getOffset()[1], content.getOffset()[2]);
            }
            dispRawModel.append(faceModel);
        }
        dmhDisp.uploadLater(dispRawModel);

        // 拼接模式下显示纹理分辨率随宽/高放大（与旧版 pids.js 一致：150 * 宽/高）
        texW = 150 * width;
        texH = 150 * height;
        Main.LOGGER.info("PIDS spilt texW={}, texH={}", texW, texH);

        // 拼接碰撞形状：把 9 个子模型的碰撞盒按网格拼接
        var shapeRawMap = content.getShapes();
        ShapeCollection[] shapes = new ShapeCollection[PidsContent.MODEL_KEYS.length];
        for (int i = 0; i < PidsContent.MODEL_KEYS.length; i++) {
            String key = PidsContent.MODEL_KEYS[i];
            List<List<Double>> boxes = shapeRawMap.get(key);
            ShapeCollection sc = new ShapeCollection();
            if (boxes != null) {
                for (List<Double> box : boxes) {
                    if (box.size() >= 6) sc.add(new RawShape(box));
                }
            }
            shapes[i] = sc;
        }
        ShapeCollection spiltShape = ShapeUtil.buildSpiltShape(
                shapes[0], shapes[1], shapes[2],
                shapes[3], shapes[4], shapes[5],
                shapes[6], shapes[7], shapes[8],
                width, height, content.getWidthUnit(), content.getHeightUnit()
        );
        if (content.getOffset() != null) {
            spiltShape.moveAll(content.getOffset()[0], content.getOffset()[1], content.getOffset()[2]);
        }
        this.shape = spiltShape;
    }

    /**
     * 将 content 中定义的像素坐标碰撞盒（0~16）转换为 ShapeCollection（世界单位 0~1），
     * 与 AdvBoardContent 的碰撞盒约定一致。
     */
    private static ShapeCollection buildShapeCollection(List<List<Double>> pixelBoxes) {
        ShapeCollection sc = new ShapeCollection();
        if (pixelBoxes == null) return sc;
        for (List<Double> box : pixelBoxes) {
            if (box == null || box.size() < 6) continue;
            double[] world = new double[6];
            for (int i = 0; i < 6; i++) {
                world[i] = box.get(i) / 16d;
            }
            sc.add(new RawShape(world));
        }
        return sc;
    }


    /**
     * 涓婃娉ㄥ唽缁樺埗鐨勬爣璇嗭紝閬垮厤閲嶅娉ㄥ唽
     */
    private String lastRegisteredDrawInfoId = "";

    private void initDrawingAsync() {
        if (drawScriptKey == null || drawScriptKey.isEmpty()) return;
        // 拼接/加载未就绪时 texW/texH 可能仍为 0，跳过注册避免创建无效纹理
        if (texW <= 0 || texH <= 0) return;

        GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();

        final String scriptKey = drawScriptKey;

        // 閫氳繃 PidsDrawManager 鑾峰彇缁樺埗瀹炰緥锛堟敮锟?Java 绫诲拰 JS 鑴氭湰锟?
        if (pidsDrawing == null) {
            pidsDrawing = PidsDrawManager.createDrawing(scriptKey);
        }
        if (pidsDrawing == null) return;

        // 鍘婚噸锛氬鏋滅粯鍒舵爣璇嗘湭鍙樺寲锛岃鏄庢暟鎹湭鏇存柊锛屾棤闇€閲嶆柊娉ㄥ唽
        String extraConfigStr = userExtraConfigs != null ? userExtraConfigs.toString() : "";
        String drawInfoId = "PIDS_" + scriptKey + "_" + plats + "_" + extraConfigStr;
        // 拼接模式：把宽度/高度并入绘制 ID，避免不同尺寸的拼接屏误共享纹理
        PidsContent idContent = ContentInfoUtil.getPidsContent(mainModel, subModel);
        if (idContent != null && idContent.isShouldSpilt()) {
            drawInfoId = drawInfoId + "_w" + getExtraConfigInt("width", 3) + "_h" + getExtraConfigInt("height", 2);
        }
        if (drawInfoId.equals(lastRegisteredDrawInfoId)) return;
        lastRegisteredDrawInfoId = drawInfoId;

        // 检查 drawInfoId 是否已被其他方块注册（同内容共享纹理）
        if (gtHelper.hasDrawInfoId(drawInfoId)) {
            final boolean sizeMatches = gtHelper.getRegisteredGraphicSizeByDrawInfoId(drawInfoId, texW, texH);
            if (sizeMatches) {
                // 已有相同内容的纹理：直接绑定共享，不创建新纹理、不触发重绘
                gtHelper.bindToExistingDrawInfo("block_" + getBlockPos().getX() + "_" + getBlockPos().getY() + "_" + getBlockPos().getZ(), drawInfoId);
                return;
            }
        }

        // 绉婚櫎鏃х粯鍒跺啀娉ㄥ唽鏂扮粯鍒
        gtHelper.removeDrawGraphic(getBlockPos());
        gtHelper.addDrawGraphicWithGt(getBlockPos(),
                new GraphicsTextureHelper.DrawInfo(
                        drawInfoId,
                        texW, texH, false, false
                ),
                (gt) -> {
                    BasePidsDrawing drawer = pidsDrawing;
                    if (drawer == null) return;
                    drawer.draw(gt, getArrivalInfoList(), drawState, texW, texH,
                            new DrawInfoPids(getArrivalInfoList(), new int[]{0, 0, texW, texH}, scriptContext, this));
                }
        );
    }

    @Override
    public void whenDisposing() {
        drawState.clear();
        pidsDrawing = null;
        RotatableShapeHelper.getInstance().removeCache(getWorldPos());
        GraphicsTextureHelper.getInstance().removeDrawGraphic(getBlockPos());
    }

    @Override
    public void whenRendering() {
        // 纭繚缁樺埗宸叉敞鍐岋紙涓嶳IS/SIS/Diaoban淇濇寔涓€鑷达級
        initDrawingAsync();

        ObjBlockScriptContext ctx = this.scriptContext;
        if (dmhMain != null) ctx.drawModel(dmhMain, null);

        // 浠呭湪璐村浘灏辩华鍚庢墠缁樺埗 display 妯″瀷锛堜笌RIS/SIS/Diaoban淇濇寔涓€鑷达級
        if (dmhDisp != null && dmhDisp.getUploadedModel() != null
                && GraphicsTextureHelper.getInstance().isTextureAvailable(getBlockPos())) {
            GraphicsTexture gt = GraphicsTextureHelper.getInstance().getBlockGraphics(getBlockPos());
            if (gt != null && gt.isValid()) {
                dmhDisp.getUploadedModel().replaceAllTexture(gt.identifier);
                ctx.drawModel(dmhDisp.getUploadedModel(), null);
            }
        }
    }

    @Override
    public void whenSaving(Map<String, String> extraConfigs) {
        // plats
        if (plats != null) {
            extraConfigs.put("plats", Main.GSON.toJson(plats));
        } else {
            extraConfigs.put("plats", "[]");
        }

        // userExtraConfigs
        if (userExtraConfigs != null) {
            extraConfigs.put("extraConfig", Main.GSON.toJson(userExtraConfigs));
        } else {
            extraConfigs.put("extraConfig", "{}");
        }
    }

    @Override
    public void writeC2S(FriendlyByteBuf buf) {
        buf.writeFloat(translateX);
        buf.writeFloat(translateY);
        buf.writeFloat(translateZ);
        buf.writeFloat(rotateX);
        buf.writeFloat(rotateY);
        buf.writeFloat(rotateZ);
        buf.writeUtf(mainModel);

        buf.writeInt(extraConfigs.size());
        for (String key : extraConfigs.keySet()) {
            buf.writeUtf(key);
            buf.writeUtf(extraConfigs.get(key));
        }

        buf.writeInt(subModels.size());
        for (String key : subModels.keySet()) {
            buf.writeUtf(key);
            buf.writeUtf(subModels.get(key));
        }
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

        // 閲嶈锛氭竻绌烘棫鏁版嵁
        extraConfigs.clear();

        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            String value = buf.readUtf(16384); // 寤鸿锟?sign 淇濇寔涓€锟?
            extraConfigs.put(key, value);
        }

        subModels.clear();
        size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            String value = buf.readUtf(128);
            subModels.put(key, value);
        }

        // ==========================
        // 涓嬮潰锟?PIDS 涓撳睘閫昏緫
        // ==========================

        try {
            plats = Main.GSON.fromJson(
                    extraConfigs.getOrDefault("plats", "[]"),
                    new com.google.gson.reflect.TypeToken<List<Long>>() {
                    }.getType()
            );
        } catch (Exception e) {
            plats = new ArrayList<>();
        }

        try {
            userExtraConfigs = GsonHelper.asMap(Main.JSON_PARSER
                    .parse(extraConfigs.getOrDefault("extraConfig", "{}"))
                    .getAsJsonObject());
        } catch (Exception e) {
            userExtraConfigs = new ConcurrentHashMap<>();
        }


        lastRegisteredDrawInfoId = "";

        whenLoading();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    3
            );
        }

        this.setChanged();
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
    public VoxelShape setCollisionShape(BlockState state) {
        if (markedError || shape == null || shape.isEmpty()) return Shapes.empty();
        // 与形状一致
        return setShape(state);
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        if (markedError || shape == null || shape.isEmpty()) return Shapes.block();
        Direction facing = state.getValue(BaseObjBlock.FACING);
        Vec3 trans = transformOffset(facing, new Vec3(translateX, translateY, translateZ));
        float rotX = this.rotateX;
        float rotY = this.rotateY + (float) Math.toRadians(-facing.toYRot());
        float rotZ = this.rotateZ;

        RotatableShapeHelper helper = RotatableShapeHelper.getInstance();
        VoxelShape rotated = helper.getShapeForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ);
        if (rotated == null) {
            // 首次调用时缓存尚未初始化，直接基于原始形状构建
            helper.initForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ, this.shape);
            rotated = helper.getShapeForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ);
        }
        return rotated.move(trans.x, trans.y, trans.z).optimize();
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        // 拼接（shouldSpilt）模式下提供宽度/高度配置，参照 AdvBoard
        try {
            String currentSubModel0 = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
            PidsContent spiltCheck = ContentInfoUtil.getPidsContent(mainModel, currentSubModel0);
            if (spiltCheck != null && spiltCheck.isShouldSpilt()) {
                configs.add(new NumberInputConfig(
                        ComponentHelper.translatable("ui.fangsu.common.width"),
                        new ConfigSpec("int").setParam("isInt", new com.google.gson.JsonPrimitive(true)),
                        () -> (float) getExtraConfigInt("width", 3),
                        (f) -> {
                            setExtraConfig("width", String.valueOf(f.intValue()));
                            sendUpdateC2S();
                        }
                ));
                configs.add(new NumberInputConfig(
                        ComponentHelper.translatable("ui.fangsu.common.height"),
                        new ConfigSpec("int").setParam("isInt", new com.google.gson.JsonPrimitive(true)),
                        () -> (float) getExtraConfigInt("height", 2),
                        (f) -> {
                            setExtraConfig("height", String.valueOf(f.intValue()));
                            sendUpdateC2S();
                        }
                ));
            }
        } catch (Exception ignored) {
        }
        // 锟?content 锟?extraConfig 瀹氫箟鍔ㄦ€佺敓鎴愰厤缃」
        try {
            // 姣忔閲嶆柊璇诲彇 subModel锛岄伩鍏嶅垏鎹富妯″瀷鍚庡瓧娈垫湭鍚屾
            String currentSubModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
            PidsContent content = ContentInfoUtil.getPidsContent(mainModel, currentSubModel);
            if (content != null && !content.getExtraConfigDefs().isEmpty()) {
                for (JsonObject def : content.getExtraConfigDefs()) {
                    String savePos = def.has("savePos") ? def.get("savePos").getAsString() : null;
                    JsonElement defaultVal = def.has("default") ? def.get("default") : null;

                    ConfigEntry<?> entry = JsonConfigParser.parse(
                            def,
                            () -> {
                                if (savePos != null && userExtraConfigs != null && userExtraConfigs.containsKey(savePos)) {
                                    JsonElement el = userExtraConfigs.get(savePos);
                                    return convertJsonToType(el, def);
                                }
                                if (defaultVal != null) {
                                    return convertJsonToType(defaultVal, def);
                                }
                                return getTypeDefault(def);
                            },
                            v -> {
                                if (savePos != null) {
                                    if (userExtraConfigs == null) userExtraConfigs = new ConcurrentHashMap<>();
                                    userExtraConfigs.put(savePos, new com.google.gson.JsonPrimitive(String.valueOf(v)));
                                    extraConfigs.put("extraConfig", Main.GSON.toJson(userExtraConfigs));
                                    sendUpdateC2S();
                                }
                            }
                    );
                    configs.add(entry);
                }
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to load pids extraConfig for {}: {}", mainModel, e.getMessage());
        }
        return configs;
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        infos.add(createSubModelSelectInfo("content", DEFAULT_SUB_MODEL));
        infos.add(new SubModelMethodInfo(
                ComponentHelper.translatable("ui.fangsu.common.selectPlat"),
                () -> {
                    int maxSelect = 16;
                    try {
                        String currentSubModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
                        PidsContent pidsContent = ContentInfoUtil.getPidsContent(mainModel, currentSubModel);
                        if (pidsContent != null && pidsContent.getScriptSettings().has("max_select")) {
                            maxSelect = pidsContent.getScriptSettings().get("max_select").getAsInt();
                        }
                    } catch (Exception ignored) {}
                    ClientHooks.openPlatformSelectScreen(
                            ComponentHelper.translatable("ui.fangsu.common.selectPlat"),
                            plats,
                            l -> {
                                plats = l;
                                extraConfigs.put("plats", Main.GSON.toJson(plats));
                                sendUpdateC2S();
                            },
                            getBlockPos(), maxSelect
                    );
                }
        ));
        return infos;
    }

    private List<MtrUtil.PidsArrivalInfo> getArrivalInfoList() {
        return MtrUtil.getPidsArrivalInfoList(plats);
    }

    /* ============ extraConfig 杈呭姪鏂规硶 ============ */

    /**
     * 锟?JsonElement 杞崲涓洪€傚悎浼犲叆 setter 鐨勭被鍨嬶紙鏍规嵁 extraConfig def 锟?type 鎺ㄦ柇锛夛拷?
     */
    @SuppressWarnings("unchecked")
    static <T> T convertJsonToType(JsonElement el, JsonObject def) {
        String type = def.get("type").getAsString();
        return (T) switch (type) {
            case "number", "number_input" -> {
                if (def.has("param") && def.get("param").isJsonObject()
                        && def.getAsJsonObject("param").get("isInt") != null
                        && def.getAsJsonObject("param").get("isInt").getAsBoolean()) {
                    yield (Number) el.getAsInt();
                }
                yield (Number) el.getAsFloat();
            }
            case "bool" -> (Boolean) el.getAsBoolean();
            case "list" -> (Integer) el.getAsInt();
            default -> (T) el.getAsString();
        };
    }

    /**
     * 鏍规嵁 extraConfig def 锟?type 杩斿洖璇ョ被鍨嬬殑 Java 榛樿鍊硷拷?
     */
    @SuppressWarnings("unchecked")
    static <T> T getTypeDefault(JsonObject def) {
        String type = def.get("type").getAsString();
        return (T) switch (type) {
            case "number", "number_input" -> {
                boolean isInt = def.has("param") && def.get("param").isJsonObject()
                        && def.getAsJsonObject("param").get("isInt") != null
                        && def.getAsJsonObject("param").get("isInt").getAsBoolean();
                yield (Number) (isInt ? 0 : 0f);
            }
            case "bool" -> (Boolean) false;
            case "list" -> (Integer) 0;
            default -> (String) "";
        };
    }

    public static final class DrawInfoPids {
        public final List<MtrUtil.PidsArrivalInfo> arrivalInfoList;
        public final int[] texArea;
        public final ObjBlockScriptContext ctx;
        public final BlockEntityPids entity;
        /** 鐢ㄦ埛鑷畾涔夐厤缃紙鏉ヨ嚜 content JSON 锟?extraConfig锛夛紝閿负 savePos */
        public final Map<String, Object> extraConfig;

        public DrawInfoPids(
                List<MtrUtil.PidsArrivalInfo> arrivalInfoList,
                int[] texArea,
                ObjBlockScriptContext ctx,
                BlockEntityPids entity
        ) {
            this.arrivalInfoList = arrivalInfoList;
            this.texArea = texArea;
            this.ctx = ctx;
            this.entity = entity;
            this.extraConfig = convertUserExtraConfigs(entity.userExtraConfigs);
        }

        /**
         * 锟?{@code Map<String, JsonElement>} 杞崲锟?{@code Map<String, Object>} 锟?JS 浣跨敤锟?
         */
        private static Map<String, Object> convertUserExtraConfigs(Map<String, JsonElement> raw) {
            if (raw == null) return new HashMap<>();
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : raw.entrySet()) {
                JsonElement val = e.getValue();
                if (val.isJsonPrimitive()) {
                    var prim = val.getAsJsonPrimitive();
                    if (prim.isNumber()) {
                        double d = prim.getAsDouble();
                        if (d == Math.floor(d) && !Double.isInfinite(d)) {
                            result.put(e.getKey(), (int) d);
                        } else {
                            result.put(e.getKey(), d);
                        }
                    } else if (prim.isBoolean()) {
                        result.put(e.getKey(), prim.getAsBoolean());
                    } else {
                        result.put(e.getKey(), prim.getAsString());
                    }
                } else {
                    result.put(e.getKey(), val.getAsString());
                }
            }
            return result;
        }

        public List<MtrUtil.PidsArrivalInfo> arrivalInfoList() {
            return arrivalInfoList;
        }

        public int[] texArea() {
            return texArea;
        }

        public ObjBlockScriptContext ctx() {
            return ctx;
        }

        public BlockEntityPids entity() {
            return entity;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (DrawInfoPids) obj;
            return Objects.equals(this.arrivalInfoList, that.arrivalInfoList) &&
                    Objects.equals(this.texArea, that.texArea) &&
                    Objects.equals(this.ctx, that.ctx) &&
                    Objects.equals(this.entity, that.entity) &&
                    Objects.equals(this.extraConfig, that.extraConfig);
        }

        @Override
        public int hashCode() {
            return Objects.hash(arrivalInfoList, texArea, ctx, entity, extraConfig);
        }

        @Override
        public String toString() {
            return "DrawInfoPids[" +
                    "arrivalInfoList=" + arrivalInfoList + ", " +
                    "texArea=" + texArea + ", " +
                    "ctx=" + ctx + ", " +
                    "entity=" + entity + ", " +
                    "extraConfig=" + extraConfig + ']';
        }

    }
}
