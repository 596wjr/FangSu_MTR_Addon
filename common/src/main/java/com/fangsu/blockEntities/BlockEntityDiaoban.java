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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
    private String shapeLeftSerialized = "";
    private String shapeCenterSerialized = "";
    private String shapeRightSerialized = "";
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
    private boolean drawInit = false;

    private List<RouteSelectionScreen.RouteSelectInfo> routes;

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

            Map<String, List<Double>> shapeMap = content.getShape();
            shapeLeftSerialized = ShapeSerializer.serialize(shapeMap.get("left"));
            shapeCenterSerialized = ShapeSerializer.serialize(shapeMap.get("center"));
            shapeRightSerialized = ShapeSerializer.serialize(shapeMap.get("right"));


            firstInit = true;
            drawInit = false;

        } catch (Exception e) {
            Main.LOGGER.warn("Failed to load diaoban: {}", e.getMessage());
        }
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
        return getDiaobanShape(state, true);
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        return getDiaobanShape(state, false);
    }

    private VoxelShape getDiaobanShape(BlockState state, boolean collision) {
        try {
            String serialized = buildDiaobanShapeString();
            if (serialized.isEmpty()) return collision ? Shapes.empty() : Shapes.block();
            Direction facing = state.getValue(BaseObjBlock.FACING);
            int yRot = Math.floorMod((int) facing.toYRot(), 360);
            VoxelShape shape = ShapeSerializer.getShape(serialized, yRot);

            if (rotateX != 0 || rotateY != 0 || rotateZ != 0) {
                VoxelShape rotated = Shapes.empty();
                long posLong = worldPosition.asLong();
                for (AABB box : shape.toAabbs()) {
                    CollisionBoxUtil.CollisionBox collisionBox = new CollisionBoxUtil.CollisionBox(box.minX * 16, box.minY * 16, box.minZ * 16, box.maxX * 16, box.maxY * 16, box.maxZ * 16);
                    VoxelShape part = CollisionBoxUtil.cachedRotatedShape(posLong, collisionBox, Vec3.ZERO, rotateX, rotateY, rotateZ, 0.1f);
                    rotated = Shapes.or(rotated, part);
                }
                shape = rotated.optimize();
            }

            Vec3 trans = transformOffset(facing, new Vec3(translateX, translateY, translateZ));
            return shape.move(trans.x, trans.y, trans.z);
        } catch (Exception e) {
            return collision ? Shapes.empty() : Block.box(0, 0, 0, 16, 16, 16);
        }
    }

    private String buildDiaobanShapeString() {
        if (length <= 0) return "";
        List<String> parts = new ArrayList<>();
        double startX = (-0.5 * unit * (length - 1));
        appendShapeWithOffset(parts, shapeLeftSerialized, startX);
        for (int i = 0; i < length - 2; i++) {
            appendShapeWithOffset(parts, shapeCenterSerialized, startX + (i + 1) * unit);
        }
        appendShapeWithOffset(parts, shapeRightSerialized, startX + (length - 1) * unit);
        return String.join("/", parts);
    }

    private void appendShapeWithOffset(List<String> parts, String serialized, double offsetX) {
        if (serialized == null || serialized.isEmpty()) return;
        String[] boxes = serialized.split("/");
        for (String box : boxes) {
            String[] values = box.split(",");
            if (values.length != 6) continue;
            try {
                double x1 = Double.parseDouble(values[0].trim()) + offsetX;
                double x2 = Double.parseDouble(values[3].trim()) + offsetX;
                parts.add(x1 + "," + values[1].trim() + "," + values[2].trim() + "," + x2 + "," + values[4].trim() + "," + values[5].trim());
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void whenRendering() {
        if (firstInit && !drawInit) {
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
            // 第一个中心模型位置 = startX + unit/16，之后每次递增 unit/16
            double centerX = startX + (i + 1) * unit / 16.0;
            matCenter.setIdentity();  // 重置矩阵
            matCenter.translate(centerX, 0, 0);
            ctx.drawModel(dmhCenter, matCenter);
        }

        if (dmhDisp != null) {
            if (dmhDisp.getUploadedModel() != null && GraphicsTextureHelper.getInstance().hasDrawGraphic(getBlockPos())) {
                dmhDisp.getUploadedModel().replaceAllTexture(GraphicsTextureHelper.getInstance().getBlockGraphics(getBlockPos()).identifier);
            }
            Matrices matDisp = new Matrices();
            double dispX = 0;
            //(-0.5 * unit * length) / 16.0;
            matDisp.translate(dispX, 0, 0);
            ctx.drawModel(dmhDisp, matDisp);
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

        GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
        gtHelper.removeDrawGraphic(getBlockPos());

        routes = reloadRoute(getExtraConfig("routes", "[]"));
        final String drawKey = drawScript;
        drawing = DiaobanDrawManager.createDrawing(drawScript);
        gtHelper.addDrawGraphicWithGt(getBlockPos(),
                new GraphicsTextureHelper.DrawInfo(
                        "DIAOBAN_" + drawKey + "_" + routes + "_" + arrowDirection,
                        texW, texH, true, false
                ),
                gt -> {
                    BaseDiaobanDrawing drawer = drawing;
                    if (drawer != null) {
                        drawer.draw(gt, routes, drawState, arrowDirection, texW, texH);
                    }
                }
        );
        drawInit = true;
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
        GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
        gtHelper.removeDrawGraphic(getBlockPos());
    }
}
