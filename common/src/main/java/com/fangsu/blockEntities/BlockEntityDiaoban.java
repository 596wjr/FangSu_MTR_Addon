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
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.shape.ShapeCollection;
import com.fangsu.ui.RouteSelectInfo;
import com.fangsu.utils.ContentInfoUtil;
import com.fangsu.utils.CustomItemHelper;
import com.fangsu.utils.GraphicsTextureHelper;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_DIAOBAN;

public class BlockEntityDiaoban extends BaseDisplayBlockEntity implements IPlatformDoor, RouteDrawer {

    private static final String DEFAULT_MAIN_MODEL = "fangsu:diaoban/mtr_diaoban.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_diaoban_a";
    private static final String DEFAULT_DRAW_SCRIPT = "fangsu:diaoban/blank.js";
    private static final String MAIN_MODEL_KEY = "diaoban";

    protected String subModel;
    protected String drawScript;

    private DynamicModelHolder dmhLeft, dmhCenter, dmhRight, dmhDlOn, dmhDlOff;
    private ModelCluster modelLeft, modelCenter, modelRight;
    private boolean leftLoaded, centerLoaded, rightLoaded;
    private ShapeCollection shapeLeft;
    private ShapeCollection shapeCenter;
    private ShapeCollection shapeRight;
    private ShapeCollection fullShape;

    private volatile BaseDiaobanDrawing drawing;
    private boolean withDoorlight;
    private int doorLightType;
    private int length;
    private int arrowDirection;
    private int unit;

    private boolean doorTarget;
    private float doorValue;

    private boolean scriptInit = false;
    private List<RouteSelectInfo> routes = new ArrayList<>();

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
                        case "common", "simple" -> 0;
                        case "blink" -> 1;
                        default -> -1;
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
                    if (tex.get(0).size() == 2) {
                        y1 = tex.get(0).get(0);
                        z1 = tex.get(0).get(1);
                    }
                    if (tex.get(1).size() == 2) {
                        y2 = tex.get(1).get(0);
                        z2 = tex.get(1).get(1);
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

            // 构建形状：从像素坐标（0~16）转换为世界坐标（0~1）
            Map<String, List<Double>> shapeMap = content.getShape();
            shapeLeft = buildShapeFromList(shapeMap.get("left"));
            shapeCenter = buildShapeFromList(shapeMap.get("center"));
            shapeRight = buildShapeFromList(shapeMap.get("right"));
            fullShape = buildFullShape();
            shape = fullShape; // 让基类的 setShape/setCollisionShape 使用 fullShape

            leftLoaded = false;
            centerLoaded = false;
            rightLoaded = false;

            firstInit = true;
            scriptInit = false;
            resetDrawingState();
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to load diaoban: {}", e.getMessage());
        }
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

        // 路线颜色纹理替换（仅首次加载或路线变更时执行）
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

        // 绘制分段模型（左 / 中×N / 右）
        double startX = (-0.5 * unit * (length - 1)) / 16.0;
        if (leftLoaded) {
            Matrices matLeft = new Matrices();
            matLeft.translate(startX, 0, 0);
            ctx.drawModel(modelLeft, matLeft);
        }
        if (rightLoaded) {
            Matrices matRight = new Matrices();
            matRight.translate(startX + (unit * (length - 1)) / 16.0, 0, 0);
            ctx.drawModel(modelRight, matRight);
        }
        if (centerLoaded) {
            Matrices matCenter = new Matrices();
            for (int i = 0; i < length - 2; i++) {
                double centerX = startX + (i + 1) * unit / 16.0;
                matCenter.setIdentity();
                matCenter.translate(centerX, 0, 0);
                ctx.drawModel(modelCenter, matCenter);
            }
        }

        // 绘制显示面纹理
        renderDisplayModel(ctx);

        // 门灯渲染
        if (doorLightType >= 0 && withDoorlight) {
            switch (doorLightType) {
                case 0:
                    ctx.drawModel(doorValue > 0 ? dmhDlOn : dmhDlOff, null);
                    break;
                case 1:
                    boolean blinkOn = (doorValue >= 0.2 && doorValue <= 0.4)
                            || (doorValue >= 0.6 && doorValue <= 0.8)
                            || doorValue >= 1;
                    ctx.drawModel(blinkOn ? dmhDlOn : dmhDlOff, null);
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
                                // 璺嚎鏇存柊锛岄噸缃鑹叉浛鎹㈡爣蹇楋紝浠ヤ娇涓嬩竴娆＄粯鍒朵娇鐢ㄦ纭殑璺嚎棰滆壊
                                leftLoaded = false;
                                centerLoaded = false;
                                rightLoaded = false;
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

        // 阶段1：同步获取 Drawing 实例（首次调用时执行）
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
        if (drawing == null) {
            return;
        }

        // 阶段2：Drawing 已就绪，注册绘制
        routes = reloadRoute(getExtraConfig("routes", "[]"));
        // 路线重新加载，重置颜色替换标记，以便使用正确的路线颜色重新填充 routecolor
        leftLoaded = false;
        centerLoaded = false;
        rightLoaded = false;

        String drawInfoId = "DIAOBAN_" + drawKey + "_" + routes + "_" + arrowDirection;

        tryRegisterDrawing(drawInfoId, texW, texH,
                gt -> {
                    BaseDiaobanDrawing drawer = drawing;
                    if (drawer != null) {
                        drawer.draw(gt, routes, drawState, arrowDirection, texW, texH);
                    }
                }
        );
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
        super.whenDisposing();
    }
}
