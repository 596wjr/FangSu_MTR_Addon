package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.CustomItemLoader;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.customItem.contents.DiaobanContent;
import com.fangsu.extraConfig.*;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.mtr.LocalRouteDetail;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.ui.RouteSelectionScreen;
import com.fangsu.userScripts.PidsScriptHolder;
import com.fangsu.userScripts.ScriptHolderBase;
import com.fangsu.userScripts.ScriptManager;
import com.fangsu.utils.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import mtr.data.Platform;
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
import java.util.concurrent.CompletableFuture;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_DIAOBAN;

public class BlockEntityDiaoban extends BaseObjBlockEntity implements IPlatformDoor {
    private static final String DEFAULT_MAIN_MODEL = "fangsu:diaoban/mtr_diaoban.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_diaoban_a";
    private static final String DEFAULT_DRAW_SCRIPT = "fangsu:diaoban/blank.js";
    private static final String MAIN_MODEL_KEY = "diaoban";

    protected String subModel;
    protected String drawScript;

    private DynamicModelHolder dmhLeft, dmhCenter, dmhRight, dmhDlOn, dmhDlOff, dmhDisp = new DynamicModelHolder();
    private CollisionBoxUtil.CollisionBox shape;
    private Map<String, JsonElement> userExtraConfigs;

    private volatile ScriptHolderBase scriptHolder;
    private Map<String, Map<String, Object>> loaded;
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

    private List<RouteSelectionScreen.RouteSelectInfo> routes;

    private volatile int scriptLoadToken = 0;

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

        reloadRoute();

        try {
            loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(mainModel), "content");
            if (loaded == null || !loaded.containsKey(subModel)) {
                markedError = true;
                return;
            }
            Map<String, Object> current = loaded.get(subModel);
            DiaobanContent.DiaobanDisplayInfo displayInfo = DiaobanContent.DiaobanDisplayInfo.fromMap(current);
            if (displayInfo == null) {
                markedError = true;
                return;
            }
            boolean flipV = displayInfo.isFlipV();
            String modelKey = displayInfo.getModel();
            Map<String, DynamicModelHolder> models = ResourceUtil.loadPartedDmh(new ResourceLocation(modelKey), flipV);
            String modelKeyLeft = "l", modelKeyCenter = "center", modelKeyRight = "r", modelKeyDlOn = "", modelKeyDlOff = "";
            Map<String, String> subModelMap = displayInfo.getSubModel();
            if (subModelMap.containsKey("left")) modelKeyLeft = subModelMap.get("left");
            if (subModelMap.containsKey("center")) modelKeyCenter = subModelMap.get("center");
            if (subModelMap.containsKey("right")) modelKeyRight = subModelMap.get("right");

            Map<String, Object> doorlightMap = displayInfo.getDoorlight();
            if (!doorlightMap.isEmpty()) {
                if (doorlightMap.get("on") != null) modelKeyDlOn = doorlightMap.get("on").toString();
                if (doorlightMap.get("off") != null) modelKeyDlOff = doorlightMap.get("off").toString();
                if (doorlightMap.get("type") != null) {
                    String rawType = doorlightMap.get("type").toString();
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

            double leftSpace = displayInfo.getLeftSpace(), rightSpace = displayInfo.getRightSpace();
            double y1 = 0.75, z1 = 0.25, y2 = 0.25, z2 = 0.25;
            unit = displayInfo.getUnit();
            List<List<Double>> tex = displayInfo.getTex();
            if (!tex.isEmpty()) {
                List<?> l = tex;
                if (l.size() == 2) {
                    if (l.get(0) instanceof List<?> l1) {
                        if (l1.size() == 2) {
                            y1 = (Double) l1.get(0);
                            z1 = (Double) l1.get(1);
                        }
                    }
                    if (l.get(1) instanceof List<?> l2) {
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

            int texSize = displayInfo.getTexSize();
            texW = texSize * length + 1;
            texH = texSize;


            firstInit = true;
            scriptInit = false;

        } catch (Exception e) {
            Main.LOGGER.warn("Failed to load diaoban: {}", e.getMessage());
        }
    }

    @Override
    public void whenRendering() {
        if (firstInit && !scriptInit) {
            initScriptDrawingAsync();
        }

        ObjBlockScriptContext ctx = this.scriptContext;

        if (!routes.isEmpty()) {
            LocalRoute r1 = routes.get(0).route;
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
    public void whenSaving(Map<String, String> extraConfigs) {
    }

    @Override
    public InteractionResult whenUseWithOther(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
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
        List<ModelSelectInfo> thisInfo = new ArrayList<>();
        List<ModelSelectInfo> drawFuncs = new ArrayList<>();
        try {
            loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(this.mainModel), "content");
            if (loaded != null) {
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
            }
        } catch (Exception ignored) {
        }
        try {
            JsonObject loaded = ResourceUtil.loadAsJSON(new ResourceLocation("fangsu:diaoban/diaoban_scripts.json")).getAsJsonObject();
            if (loaded.has("content")) {
                JsonArray c = loaded.getAsJsonArray("content");
                for (JsonElement e : c) {
                    if (!e.isJsonObject()) continue;
                    JsonObject item = e.getAsJsonObject();
                    String text = item.get("text").getAsString();
                    String content = item.get("content").getAsString();
                    String contentText = "";
                    if (item.has("contentText")) {
                        contentText = item.get("contentText").getAsString();
                        drawFuncs.add(new ModelSelectInfo(text, content, contentText));
                    } else drawFuncs.add(new ModelSelectInfo(text, content));

                }
            }
        } catch (Exception ignored) {
        }
        infos.add(new SubModelDispInfo(
                Component.translatable("ui.fangsu.block.subModelSelect"),
                thisInfo,
                (be) -> this.subModels.getOrDefault("subModel", DEFAULT_SUB_MODEL),
                (be, v) -> this.subModels.put("subModel", v)));
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
                    extraConfigs.put("doorSide", v.toString());
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

    private void initScriptDrawingAsync() {
        reloadRoute();

        GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
        gtHelper.removeDrawGraphic(getBlockPos());

        final int thisLoadToken = ++scriptLoadToken;

        String scriptPath = drawScript;
        ResourceLocation location = new ResourceLocation(scriptPath);

        gtHelper.addDrawGraphic(getBlockPos(),
                new GraphicsTextureHelper.DrawInfo(
                        "DIAOBAN_" + scriptPath + "_" + routes + "_" + arrowDirection,
                        texW, texH, true, false
                ),
                (g) -> {
                    ScriptHolderBase holder = scriptHolder;
                    if (holder == null) return;
                    LocalRoute route = null;
                    Platform plat = null;
                    int index = 0;
                    if (!routes.isEmpty()) {
                        RouteSelectionScreen.RouteSelectInfo routeSelectInfo = routes.get(0);
                        route = routeSelectInfo.route;
                        plat = routeSelectInfo.plat;
                        if (plat != null)
                            index = route.getPlatformIdIndex(routeSelectInfo.plat.id);
                    }
                    if (route == null) {
                        route = new LocalRoute();
                        Main.LOGGER.error("route not found");
                    }
                    ScriptManager.getInstance().requestRunFunction(holder, "draw", g, drawState,
                            new DrawInfoDiaoban(
                                    route.asRouteDetail(), arrowDirection, plat, index, new int[]{0, 0, texW, texH}
                            ));
                }
                //TODO 支持多选
        );

        CompletableFuture.runAsync(() -> {
            try {
                ScriptHolderBase loadedHolder = ScriptManager.getInstance().getOrInitHolder(location, PidsScriptHolder::new);
                if (loadedHolder != null && thisLoadToken == scriptLoadToken) {
                    scriptHolder = loadedHolder;
                }
            } catch (Throwable e) {
                Main.LOGGER.error("Failed to load Diaoban script async {}", location, e);
            }
        }, ScriptManager.SCRIPT_EXECUTOR);

        scriptInit = true;
    }

    private void reloadRoute() {
        List<JsonElement> rawRoutes = Main.JSON_PARSER.parse(getExtraConfig("routes", "[]")).getAsJsonArray().asList();
        Main.LOGGER.info("rawRoutes {}", rawRoutes);
        routes = new ArrayList<>();
        for (JsonElement rawRoute : rawRoutes) {
            if (rawRoute.getAsJsonArray().size() < 2) continue;
            JsonArray a = rawRoute.getAsJsonArray();
            routes.add(new RouteSelectionScreen.RouteSelectInfo(MtrUtil.getRouteById(a.get(0).getAsLong()), MtrUtil.getPlatformById(a.get(1).getAsLong())));
        }
        Main.LOGGER.info("Loaded {} routes", routes.size());
        Main.LOGGER.info(routes.toString());
    }

    public static final class DrawInfoDiaoban {
        public final LocalRouteDetail routeInfo;
        public final int arrowDirection;
        public final Platform plat;
        public final int index;
        public final int[] texArea;

        public DrawInfoDiaoban(LocalRouteDetail routeInfo, int arrowDirection, Platform plat, int index,
                               int[] texArea) {
            this.routeInfo = routeInfo;
            this.arrowDirection = arrowDirection;
            this.plat = plat;
            this.index = index;
            this.texArea = texArea;
        }

        public LocalRouteDetail routeInfo() {
            return routeInfo;
        }

        public int arrowDirection() {
            return arrowDirection;
        }

        public Platform plat() {
            return plat;
        }

        public int index() {
            return index;
        }

        public int[] texArea() {
            return texArea;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (DrawInfoDiaoban) obj;
            return Objects.equals(this.routeInfo, that.routeInfo) &&
                    this.arrowDirection == that.arrowDirection &&
                    Objects.equals(this.plat, that.plat) &&
                    this.index == that.index &&
                    Arrays.equals(this.texArea, that.texArea);
        }

        @Override
        public int hashCode() {
            return Objects.hash(routeInfo, arrowDirection, plat, index, texArea);
        }

        @Override
        public String toString() {
            return "DrawInfoDiaoban[" +
                    "routeInfo=" + routeInfo + ", " +
                    "arrowDirection=" + arrowDirection + ", " +
                    "plat=" + plat + ", " +
                    "index=" + index + ", " +
                    "texArea=" + Arrays.toString(texArea) + ']';
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
        GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
        gtHelper.removeDrawGraphic(getBlockPos());
    }
}
