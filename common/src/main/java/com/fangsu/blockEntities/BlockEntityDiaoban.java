package com.fangsu.blockEntities;

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
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.shape.*;
import com.fangsu.ui.RouteSelectionScreen;
import com.fangsu.utils.*;
import com.google.gson.JsonElement;
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

    private List<RouteSelectionScreen.RouteSelectInfo> routes;

    /** 上次注册绘制的标识，避免重复注册 */
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
            if (subModelMap.containsKey("left")) modelKeyLeft = subModelMap.get("left");
            if (subModelMap.containsKey("center")) modelKeyCenter = subModelMap.get("center");
            if (subModelMap.containsKey("right")) modelKeyRight = subModelMap.get("right");

            Map<String, String> doorlightMap = content.getDoorlight();
            if (!doorlightMap.isEmpty()) {
                if (doorlightMap.get("on") != null) modelKeyDlOn = doorlightMap.get("on");
                if (doorlightMap.get("off") != null) modelKeyDlOff = doorlightMap.get("off");
                if (doorlightMap.get("type") != null) {
                    String rawType = doorlightMap.get("type");
                    doorLightType = switch (rawType) {
                        case "common", "simple" -> 0;
                        case "blink" -> 1;
                        default -> -1;
                    };
                }
            }
            dmhLeft = models.get(modelKeyLeft);
            dmhCenter = models.get(modelKeyCenter);
            dmhRight = models.get(modelKeyRight);
            if (!"".equals(modelKeyDlOn)) dmhDlOn = models.get(modelKeyDlOn);
            if (!"".equals(modelKeyDlOff)) dmhDlOff = models.get(modelKeyDlOff);

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

            // 构建 ShapeCollection，从像素坐标(0~16)转换为世界坐标(0~1)
            Map<String, List<Double>> shapeMap = content.getShape();
            shapeLeft = buildShapeCollection(shapeMap.get("left"));
            shapeCenter = buildShapeCollection(shapeMap.get("center"));
            shapeRight = buildShapeCollection(shapeMap.get("right"));

            // 构建完整形状
            fullShape = buildFullShape();

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
        if (markedError || fullShape == null || fullShape.isEmpty()) return Shapes.empty();
        return setShape(state);
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        if (markedError || fullShape == null || fullShape.isEmpty()) return Shapes.block();
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
     * 从 DiaobanContent 的 List&lt;Double&gt;（像素坐标 0~16）构建 ShapeCollection（世界坐标 0~1）
     */
    private static ShapeCollection buildShapeCollection(List<Double> shapeData) {
        ShapeCollection col = new ShapeCollection();
        if (shapeData == null || shapeData.size() < 6) return col;
        double[] box = new double[6];
        for (int i = 0; i < 6; i++) {
            box[i] = shapeData.get(i) / 16.0;
        }
        col.add(new RawShape(box));
        return col;
    }

    /**
     * 根据 length 拼接 left / center × (length-2) / right 得到完整形状
     */
    private ShapeCollection buildFullShape() {
        if (length <= 0) return new ShapeCollection();
        ShapeCollection result = new ShapeCollection();
        // 起始 X 偏移（像素值），用于形状拼接
        double startX = (-0.5 * unit * (length - 1)) / 16.0;

        // 左
        if (shapeLeft != null) {
            ShapeCollection copy = shapeLeft.copy();
            copy.moveAll(startX, 0, 0);
            result.addAll(copy);
        }

        // 中间（重复 length - 2 次）
        if (shapeCenter != null) {
            for (int i = 0; i < length - 2; i++) {
                ShapeCollection copy = shapeCenter.copy();
                double centerX = startX + (i + 1) * unit / 16.0;
                copy.moveAll(centerX, 0, 0);
                result.addAll(copy);
            }
        }

        // 右
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
                    if (dmhLeft.getUploadedModel() != null)
                        dmhLeft.getUploadedModel().replaceTexture("routecolor.png", gt.identifier);
                    if (dmhCenter.getUploadedModel() != null)
                        dmhCenter.getUploadedModel().replaceTexture("routecolor.png", gt.identifier);
                    if (dmhRight.getUploadedModel() != null)
                        dmhRight.getUploadedModel().replaceTexture("routecolor.png", gt.identifier);
                }
            }
        }

        // 计算初始偏移量，与 JS 版本对齐：(-0.5 * unit * (length - 1)) / 16
        double startX = (-0.5 * unit * (length - 1)) / 16.0;

        // 绘制左模型
        Matrices matLeft = new Matrices();
        matLeft.translate(startX, 0, 0);
        ctx.drawModel(dmhLeft, matLeft);

        // 绘制右模型
        Matrices matRight = new Matrices();
        double rightX = startX + (unit * (length - 1)) / 16.0;
        matRight.translate(rightX, 0, 0);
        ctx.drawModel(dmhRight, matRight);

        // 绘制中心模型
        Matrices matCenter = new Matrices();
        for (int i = 0; i < length - 2; i++) {
            double centerX = startX + (i + 1) * unit / 16.0;
            matCenter.setIdentity();
            matCenter.translate(centerX, 0, 0);
            ctx.drawModel(dmhCenter, matCenter);
        }

        // 仅在贴图就绪后才绘制 display 模型
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
                    if (doorValue > 0) ctx.drawModel(dmhDlOn, null);
                    else ctx.drawModel(dmhDlOff, null);
                    break;
                case 1:
                    if ((doorValue >= 0.2 && doorValue <= 0.4) || (doorValue >= 0.6 && doorValue <= 0.8) || doorValue >= 1)
                        ctx.drawModel(dmhDlOn, null);
                    else ctx.drawModel(dmhDlOff, null);
                    break;
            }
        }
    }

    @Override
    public InteractionResult whenUseWithBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        arrowDirection += 1;
        if (arrowDirection >= 3) arrowDirection = 0;
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
                Component.translatable("ui.fangsu.diaoban.selectDrawFunction"),
                drawFuncs,
                (be) -> this.subModels.getOrDefault("drawScript", DEFAULT_DRAW_SCRIPT),
                (be, v) -> this.subModels.put("drawScript", v)));
        infos.add(new SubModelMethodInfo(
                Component.translatable("ui.fangsu.common.selectRoute"),
                () -> {
                    ClientHooks.openRouteSelectionScreen(
                            Component.translatable("ui.fangsu.common.selectRoute"),
                            null,
                            l -> {
                                routes = l;
                                List<List<Long>> saveRoutes = new ArrayList<>();
                                for (RouteSelectionScreen.RouteSelectInfo info : routes) {
                                    saveRoutes.add(List.of(info.route.id, info.plat.id));
                                }
                                extraConfigs.put("routes", Main.GSON.toJson(saveRoutes));
                                sendUpdateC2S();
                            },
                            getBlockPos(), 1
                    );
                }
        ));
        return infos;
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        configs.add(new NumberInputConfig(
                Component.translatable("ui.fangsu.common.length"),
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
                Component.translatable("ui.fangsu.diaoban.arrowDirection"),
                new ConfigSpec("list"),
                List.of(
                        Component.translatable("ui.fangsu.diaoban.arrowNone"),
                        Component.translatable("ui.fangsu.diaoban.arrowLeft"),
                        Component.translatable("ui.fangsu.diaoban.arrowRight")
                ),
                () -> arrowDirection,
                (v) -> {
                    arrowDirection = v;
                    extraConfigs.put("arrowDirection", v.toString());
                    sendUpdateC2S();
                }
        ));
        configs.add(new BoolConfig(
                Component.translatable("ui.fangsu.diaoban.withDoorlight"),
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
        if (!firstInit) return;

        final String drawKey = drawScript;

        // 阶段1：同步获取 Drawing 实例
        if (!scriptInit) {
            scriptInit = true;
            try {
                drawing = DiaobanDrawManager.createDrawing(drawKey);
            } catch (Throwable e) {
                Main.LOGGER.error("Failed to create diaoban drawing {}", drawKey, e);
            }
            // 如果创建失败，直接标记完成（避免卡在第二阶段一直等待）
            if (drawing == null) {
                scriptDone = true;
                return;
            }
            return;
        }

        // Drawing 尚未就绪，等待（首次创建后的等待）
        if (drawing == null) return;

        // 阶段2：Drawing 已就绪，注册绘制（仅一次）
        if (!scriptDone) {
            GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
            routes = reloadRoute(getExtraConfig("routes", "[]"));
            String drawInfoId = "DIAOBAN_" + drawKey + "_" + routes + "_" + arrowDirection;
            // 如果标识相同（数据未变化），直接标记完成
            if (drawInfoId.equals(lastRegisteredDrawInfoId)) {
                scriptDone = true;
                return;
            }
            // 标识变化，先移除旧绘制再注册新绘制
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
