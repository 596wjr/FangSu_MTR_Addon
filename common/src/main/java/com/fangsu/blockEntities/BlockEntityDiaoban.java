package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.Main;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.customItem.contents.DiaobanContent;
import com.fangsu.drawing.diaoban.DiaobanDrawManager;
import com.fangsu.drawing.diaoban.BaseDiaobanDrawing;
import com.fangsu.extraConfig.*;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.shape.*;
import com.fangsu.ui.RouteSelectInfo;
import com.fangsu.utils.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import java.util.*;
import java.util.List;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_DIAOBAN;

public class BlockEntityDiaoban extends BaseObjBlockEntity implements IPlatformDoor, RouteDrawer {

    private static final String DEFAULT_MAIN_MODEL = "fangsu:diaoban/mtr_diaoban.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_diaoban_a";
    private static final String DEFAULT_DRAW_SCRIPT = "fangsu:diaoban/blank.js";
    private static final String MAIN_MODEL_KEY = "diaoban";

    protected String subModel;
    protected String drawScript;

    private DynamicModelHolder dmhLeft, dmhCenter, dmhRight, dmhDlOn, dmhDlOff, dmhDisp = new DynamicModelHolder();
    private ModelCluster modelLeft, modelCenter, modelRight;
    private boolean leftLoaded, centerLoaded, rightLoaded;
    private ShapeCollection shapeLeft;
    private ShapeCollection shapeCenter;
    private ShapeCollection shapeRight;
    private ShapeCollection fullShape;
    private Map<String, JsonElement> userExtraConfigs;

    private volatile BaseDiaobanDrawing drawing;
    private int texW, texH;
    private boolean withDoorlight;
    private int doorLightType;
    private int length;
    private int arrowDirection;
    private int unit;
    private Map<String, Object> drawState = new HashMap<>();

    private boolean doorTarget;
    private float doorValue;

    private boolean firstInit = false;
    private boolean scriptInit = false;
    private boolean scriptDone = false;

    private List<RouteSelectInfo> routes;

    /**
     * 涓婃娉ㄥ唽缁樺埗鐨勬爣璇嗭紝閬垮厤閲嶅娉ㄥ唽
     */
    private String lastRegisteredDrawInfoId = "";

    public BlockEntityDiaoban(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_DIAOBAN.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("extraConfig", "{}");

        mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
        drawScript = CustomItemHelper.checkSubModel(this, "drawScript", DEFAULT_DRAW_SCRIPT);

        length = getExtraConfigInt("length", 2);
        arrowDirection = getExtraConfigInt("arrowDirection", 0);
        withDoorlight = getExtraConfigBool("withDoorlight", false);

        routes = reloadRoute(getExtraConfig("routes", "[]"));

        try {
            DiaobanContent content = ContentInfoUtil.getDiaobanContent(mainModel, subModel);
            if (content == null) {
                markedError = true;
                return;
            }
            boolean flipV = content.isFlipV();
            String modelKey = content.getModel();
            Map<String, DynamicModelHolder> models = ResourceUtil.loadPartedDmh(new ResourceLocation(modelKey), flipV);
            String modelKeyLeft = "l", modelKeyCenter = "center", modelKeyRight = "r", modelKeyDlOn = "", modelKeyDlOff = "";
            Map<String, String> subModelMap = content.getSubModel();
            if (subModelMap.containsKey("left")) {
                modelKeyLeft = subModelMap.get("left");
            }
            if (subModelMap.containsKey("center")) {
                modelKeyCenter = subModelMap.get("center");
            }
            if (subModelMap.containsKey("right")) {
                modelKeyRight = subModelMap.get("right");
            }

            Map<String, String> doorlightMap = content.getDoorlight();
            if (!doorlightMap.isEmpty()) {
                if (doorlightMap.get("on") != null) {
                    modelKeyDlOn = doorlightMap.get("on");
                }
                if (doorlightMap.get("off") != null) {
                    modelKeyDlOff = doorlightMap.get("off");
                }
                if (doorlightMap.get("type") != null) {
                    String rawType = doorlightMap.get("type");
                    doorLightType = switch (rawType) {
                        case "common", "simple" ->
                            0;
                        case "blink" ->
                            1;
                        default ->
                            -1;
                    };
                }
            }
            dmhLeft = models.get(modelKeyLeft);
            dmhCenter = models.get(modelKeyCenter);
            dmhRight = models.get(modelKeyRight);
            if (!"".equals(modelKeyDlOn)) {
                dmhDlOn = models.get(modelKeyDlOn);
            }
            if (!"".equals(modelKeyDlOff)) {
                dmhDlOff = models.get(modelKeyDlOff);
            }

            double leftSpace = content.getLeftSpace(), rightSpace = content.getRightSpace();
            double y1 = 0.75, z1 = 0.25, y2 = 0.25, z2 = 0.25;
            unit = content.getUnit();
            List<List<Double>> tex = content.getTex();
            if (!tex.isEmpty()) {
                if (tex.size() == 2) {
                    if (((List<?>) tex).get(0) instanceof List<?> l1) {
                        if (l1.size() == 2) {
                            y1 = (Double) l1.get(0);
                            z1 = (Double) l1.get(1);
                        }
                    }
                    if (((List<?>) tex).get(1) instanceof List<?> l2) {
                        if (l2.size() == 2) {
                            y2 = (Double) l2.get(0);
                            z2 = (Double) l2.get(1);
                        }
                    }
                }
            }
            RawMeshBuilder rawMeshBuilder = new RawMeshBuilder(4, "exterior", new ResourceLocation("fangsu:pids/black.png"));
            List<List<Double>> points = List.of(
                    List.of((-0.5 * unit * length) / 16d + leftSpace, y2, z2),
                    List.of((-0.5 * unit * length) / 16d + leftSpace, y1, z1),
                    List.of((0.5 * unit * length) / 16d - rightSpace, y1, z1),
                    List.of((0.5 * unit * length) / 16d - rightSpace, y2, z2)
            );
            ModelHelper.addQuad(rawMeshBuilder, points, false);
            RawModel dispRawModel = new RawModel();
            dispRawModel.append(rawMeshBuilder.getMesh());
            dispRawModel.generateNormals();
            dmhDisp.uploadLater(dispRawModel);

            int texSize = content.getTexSize();
            texW = texSize * length + 1;
            texH = texSize;

            // 鏋勫缓 ShapeCollection锛屼粠鍍忕礌鍧愭爣(0~16)杞崲涓轰笘鐣屽潗锟?0~1)
            Map<String, List<Double>> shapeMap = content.getShape();
            shapeLeft = buildShapeCollection(shapeMap.get("left"));
            shapeCenter = buildShapeCollection(shapeMap.get("center"));
            shapeRight = buildShapeCollection(shapeMap.get("right"));

            // 鏋勫缓瀹屾暣褰㈢姸
            fullShape = buildFullShape();

            leftLoaded = false;
            centerLoaded = false;
            rightLoaded = false;

            firstInit = true;

            scriptInit = false;
            scriptDone = false;
            lastRegisteredDrawInfoId = "";

        } catch (Exception e) {
            Main.LOGGER.warn("Failed to load diaoban: {}", e.getMessage());
        }
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
        if (markedError || fullShape == null || fullShape.isEmpty()) {
            return Shapes.empty();
        }
        return setShape(state);
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        if (markedError || fullShape == null || fullShape.isEmpty()) {
            return Shapes.block();
        }
        Direction facing = state.getValue(BaseObjBlock.FACING);
        Vec3 trans = transformOffset(facing, new Vec3(translateX, translateY, translateZ));
        float rotX = this.rotateX;
        float rotY = this.rotateY + (float) Math.toRadians(-facing.toYRot());
        float rotZ = this.rotateZ;

        RotatableShapeHelper helper = RotatableShapeHelper.getInstance();
        VoxelShape rotated = helper.getShapeForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ);
        if (rotated == null) {
            helper.initForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ, this.fullShape);
            rotated = helper.getShapeForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ);
        }
        return rotated.move(trans.x, trans.y, trans.z).optimize();
    }

    /**
     * 锟?DiaobanContent 锟?List&lt;Double&gt;锛堝儚绱犲潗锟?0~16锛夋瀯锟?ShapeCollection锛堜笘鐣屽潗锟?
     * 0~1锟?
     */
    private static ShapeCollection buildShapeCollection(List<Double> shapeData) {
        ShapeCollection col = new ShapeCollection();
        if (shapeData == null || shapeData.size() < 6) {
            return col;
        }
        double[] box = new double[6];
        for (int i = 0; i < 6; i++) {
            box[i] = shapeData.get(i) / 16.0;
        }
        col.add(new RawShape(box));
        return col;
    }

    /**
     * 鏍规嵁 length 鎷兼帴 left / center 脳 (length-2) / right 寰楀埌瀹屾暣褰㈢姸
     */
    private ShapeCollection buildFullShape() {
        if (length <= 0) {
            return new ShapeCollection();
        }
        ShapeCollection result = new ShapeCollection();
        // 璧峰 X 鍋忕Щ锛堝儚绱犲€硷級锛岀敤浜庡舰鐘舵嫾锟?
        double startX = (-0.5 * unit * (length - 1)) / 16.0;

        // 锟?
        if (shapeLeft != null) {
            ShapeCollection copy = shapeLeft.copy();
            copy.moveAll(startX, 0, 0);
            result.addAll(copy);
        }

        // 涓棿锛堥噸锟?length - 2 娆★級
        if (shapeCenter != null) {
            for (int i = 0; i < length - 2; i++) {
                ShapeCollection copy = shapeCenter.copy();
                double centerX = startX + (i + 1) * unit / 16.0;
                copy.moveAll(centerX, 0, 0);
                result.addAll(copy);
            }
        }

        // 锟?
        if (shapeRight != null) {
            ShapeCollection copy = shapeRight.copy();
            double rightX = startX + (length - 1) * unit / 16.0;
            copy.moveAll(rightX, 0, 0);
            result.addAll(copy);
        }

        return result;
    }

    @Override
    public void whenRendering() {
        if (!scriptDone) {
            initDrawingAsync();
        }

        ObjBlockScriptContext ctx = this.scriptContext;

        if (!routes.isEmpty()) {
            LocalRoute r1 = routes.get(0).route;
            if (r1 != null) {
                GraphicsTexture gt = ResourceUtil.createSolidColorGT(16, 16, new Color(r1.color));
                if (gt.isValid()) {
                    if (dmhLeft.getUploadedModel() != null && !leftLoaded) {
                        modelLeft = dmhLeft.getUploadedModel().copyForMaterialChanges();
                        modelLeft.replaceTexture("routecolor.png", gt.identifier);
                        leftLoaded = true;
                    }
                    if (dmhCenter.getUploadedModel() != null && !centerLoaded) {
                        modelCenter = dmhCenter.getUploadedModel().copyForMaterialChanges();
                        modelCenter.replaceTexture("routecolor.png", gt.identifier);
                        centerLoaded = true;
                    }
                    if (dmhRight.getUploadedModel() != null && !rightLoaded) {
                        modelRight = dmhRight.getUploadedModel().copyForMaterialChanges();
                        modelRight.replaceTexture("routecolor.png", gt.identifier);
                        rightLoaded = true;
                    }
                }
            }
        }

        // 璁＄畻鍒濆鍋忕Щ閲忥紝锟?JS 鐗堟湰瀵归綈锟?-0.5 * unit * (length - 1)) / 16
        double startX = (-0.5 * unit * (length - 1)) / 16.0;

        // 缁樺埗宸︽ā锟?
        Matrices matLeft = new Matrices();
        matLeft.translate(startX, 0, 0);
        if (leftLoaded) {
            ctx.drawModel(modelLeft, matLeft);
        }

        // 缁樺埗鍙虫ā锟?
        Matrices matRight = new Matrices();
        double rightX = startX + (unit * (length - 1)) / 16.0;
        matRight.translate(rightX, 0, 0);
        if (rightLoaded) {
            ctx.drawModel(modelRight, matRight);
        }

        // 缁樺埗涓績妯″瀷
        Matrices matCenter = new Matrices();
        if (centerLoaded) {
            for (int i = 0; i < length - 2; i++) {
                double centerX = startX + (i + 1) * unit / 16.0;
                matCenter.setIdentity();
                matCenter.translate(centerX, 0, 0);
                ctx.drawModel(modelCenter, matCenter);
            }
        }

        // 浠呭湪璐村浘灏辩华鍚庢墠缁樺埗 display 妯″瀷
        if (scriptDone && dmhDisp != null && dmhDisp.getUploadedModel() != null
                && GraphicsTextureHelper.getInstance().isTextureAvailable(getBlockPos())) {
            GraphicsTexture tex = GraphicsTextureHelper.getInstance().getBlockGraphics(getBlockPos());
            if (tex != null && tex.isValid()) {
                dmhDisp.getUploadedModel().replaceAllTexture(tex.identifier);
                ctx.drawModel(dmhDisp.getUploadedModel(), null);
            }
        }

        if (doorLightType >= 0 && withDoorlight) {
            switch (doorLightType) {
                case 0:
                    if (doorValue > 0) {
                        ctx.drawModel(dmhDlOn, null); 
                    }else {
                        ctx.drawModel(dmhDlOff, null);
                    }
                    break;
                case 1:
                    if ((doorValue >= 0.2 && doorValue <= 0.4) || (doorValue >= 0.6 && doorValue <= 0.8) || doorValue >= 1) {
                        ctx.drawModel(dmhDlOn, null); 
                    }else {
                        ctx.drawModel(dmhDlOff, null);
                    }
                    break;
            }
        }
    }

    @Override
    public InteractionResult whenUseWithBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        arrowDirection += 1;
        if (arrowDirection >= 3) {
            arrowDirection = 0;
        }
        extraConfigs.put("arrowDirection", String.valueOf(arrowDirection));
        sendUpdateC2S();
        return InteractionResult.SUCCESS;
    }

    @Override
    public String getMainModelKey() {
        return MAIN_MODEL_KEY;
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        List<ModelSelectInfo> drawFuncs = DiaobanDrawManager.getDrawOptions();
        infos.add(createSubModelSelectInfo("content", DEFAULT_SUB_MODEL));
        infos.add(new SubModelDispInfo(
                ComponentHelper.translatable("ui.fangsu.diaoban.selectDrawFunction"),
                drawFuncs,
                (be) -> this.subModels.getOrDefault("drawScript", DEFAULT_DRAW_SCRIPT),
                (be, v) -> this.subModels.put("drawScript", v)));
        infos.add(new SubModelMethodInfo(
                ComponentHelper.translatable("ui.fangsu.common.selectRoute"),
                () -> {
                    int maxSelect = 1;
                    try {
                        String currentDrawKey = CustomItemHelper.checkSubModel(this, "drawScript", DEFAULT_DRAW_SCRIPT);
                        JsonObject ss = DiaobanDrawManager.getScriptSettingsByDrawKey(currentDrawKey);
                        if (ss.has("max_select")) {
                            maxSelect = ss.get("max_select").getAsInt();
                        }
                    } catch (Exception ignored) {
                    }
                    ClientHooks.openRouteSelectionScreen(
                            ComponentHelper.translatable("ui.fangsu.common.selectRoute"),
                            null,
                            l -> {
                                routes = l;
                                List<List<Long>> saveRoutes = new ArrayList<>();
                                for (RouteSelectInfo info : routes) {
                                    saveRoutes.add(List.of(info.route.id, info.plat.id));
                                }
                                extraConfigs.put("routes", Main.GSON.toJson(saveRoutes));
                                sendUpdateC2S();
                            },
                            getBlockPos(), maxSelect
                    );
                }
        ));
        return infos;
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.common.length"),
                new ConfigSpec("number_input")
                        .setParam("max", new JsonPrimitive(16))
                        .setParam("min", new JsonPrimitive(2))
                        .setParam("isInt", new JsonPrimitive(true)),
                () -> length * 1f,
                (v) -> {
                    length = v.intValue();
                    extraConfigs.put("length", String.valueOf(v.intValue()));
                    sendUpdateC2S();
                }
        ));
        configs.add(new EnumConfig(
                ComponentHelper.translatable("ui.fangsu.diaoban.arrowDirection"),
                new ConfigSpec("list"),
                List.of(
                        ComponentHelper.translatable("ui.fangsu.diaoban.arrowNone"),
                        ComponentHelper.translatable("ui.fangsu.diaoban.arrowLeft"),
                        ComponentHelper.translatable("ui.fangsu.diaoban.arrowRight")
                ),
                () -> arrowDirection,
                (v) -> {
                    arrowDirection = v;
                    extraConfigs.put("arrowDirection", v.toString());
                    sendUpdateC2S();
                }
        ));
        configs.add(new BoolConfig(
                ComponentHelper.translatable("ui.fangsu.diaoban.withDoorlight"),
                new ConfigSpec("bool"),
                () -> withDoorlight,
                (v) -> {
                    withDoorlight = v;
                    extraConfigs.put("withDoorlight", v.toString());
                    sendUpdateC2S();
                }
        ));
        return configs;
    }

    private void initDrawingAsync() {
        if (!firstInit) {
            return;
        }

        final String drawKey = drawScript;

        // 闃舵1锛氬悓姝ヨ幏锟?Drawing 瀹炰緥
        if (!scriptInit) {
            scriptInit = true;
            try {
                drawing = DiaobanDrawManager.createDrawing(drawKey);
            } catch (Throwable e) {
                Main.LOGGER.error("Failed to create diaoban drawing {}", drawKey, e);
            }
            // 濡傛灉鍒涘缓澶辫触锛岀洿鎺ユ爣璁板畬鎴愶紙閬垮厤鍗″湪绗簩闃舵涓€鐩寸瓑寰咃級
            if (drawing == null) {
                scriptDone = true;
                return;
            }
            return;
        }

        // Drawing 灏氭湭灏辩华锛岀瓑寰咃紙棣栨鍒涘缓鍚庣殑绛夊緟锟?
        if (drawing == null) {
            return;
        }

        // 闃舵2锛欴rawing 宸插氨缁紝娉ㄥ唽缁樺埗锛堜粎涓€娆★級
        if (!scriptDone) {
            GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
            routes = reloadRoute(getExtraConfig("routes", "[]"));
            String drawInfoId = "DIAOBAN_" + drawKey + "_" + routes + "_" + arrowDirection;
            // 濡傛灉鏍囪瘑鐩稿悓锛堟暟鎹湭鍙樺寲锛夛紝鐩存帴鏍囪瀹屾垚
            if (drawInfoId.equals(lastRegisteredDrawInfoId)) {
                scriptDone = true;
                return;
            }
            // 鏍囪瘑鍙樺寲锛屽厛绉婚櫎鏃х粯鍒跺啀娉ㄥ唽鏂扮粯锟?
            gtHelper.removeDrawGraphic(getBlockPos());
            gtHelper.addDrawGraphicWithGt(getBlockPos(),
                    new GraphicsTextureHelper.DrawInfo(
                            drawInfoId,
                            texW, texH, true, false
                    ),
                    gt -> {
                        BaseDiaobanDrawing drawer = drawing;
                        if (drawer != null) {
                            drawer.draw(gt, routes, drawState, arrowDirection, texW, texH);
                        }
                    }
            );
            lastRegisteredDrawInfoId = drawInfoId;
            scriptDone = true;
        }
    }

    @Override
    public boolean getDoorTarget() {
        return doorTarget;
    }

    @Override
    public void setDoorTarget(boolean target) {
        this.doorTarget = target;
    }

    @Override
    public float getDoorValue() {
        return doorValue;
    }

    @Override
    public void setDoorValue(float value) {
        this.doorValue = value;
    }

    @Override
    public void whenDisposing() {
        RotatableShapeHelper.getInstance().removeCache(getWorldPos());
        GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
        gtHelper.removeDrawGraphic(getBlockPos());
    }
}
