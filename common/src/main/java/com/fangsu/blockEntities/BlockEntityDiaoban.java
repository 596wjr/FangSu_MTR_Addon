package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.CustomItemLoader;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.extraConfig.ConfigEntry;
import com.fangsu.extraConfig.ConfigSpec;
import com.fangsu.extraConfig.NumberInputConfig;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.mtr.LocalRouteDetail;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_DIAOBAN;

public class BlockEntityDiaoban extends BaseObjBlockEntity {
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
    private int doorLightType;
    private int length;
    private int arrowDirection;
    private int unit;
    private Map<String, Object> drawState = new HashMap<>();

    private List<RouteSelectionScreen.RouteSelectInfo> routes;

    private volatile int scriptLoadToken = 0;

    public BlockEntityDiaoban(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_DIAOBAN.get(), blockPos, blockState);

    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("extraConfig", "{}");
        ensureExtraConfig("routes", "[]");
        ensureExtraConfig("arrowDirection", "0");
        ensureExtraConfig("length", "2");

        mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
        drawScript = CustomItemHelper.checkSubModel(this, "drawScript", DEFAULT_DRAW_SCRIPT);

        length = getExtraConfigInt("length", 2);
        arrowDirection = getExtraConfigInt("arrowDirection", 0);

        List<JsonElement> rawRoutes = Main.JSON_PARSER.parse(getExtraConfig("routes")).getAsJsonArray().asList();
        routes = new ArrayList<>();
        for (JsonElement rawRoute : rawRoutes) {
            if (!rawRoute.isJsonArray() || rawRoute.getAsJsonArray().size() != 2) continue;
            JsonArray a = rawRoute.getAsJsonArray();
            routes.add(new RouteSelectionScreen.RouteSelectInfo(MtrUtil.getRouteById(a.get(0).getAsLong()), MtrUtil.getPlatformById(a.get(1).getAsLong())));
        }

        try {
            loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(mainModel), "content");
            if (loaded == null || !loaded.containsKey(subModel)) {
                markedError = true;
                return;
            }
            Map<String, Object> current = loaded.get(subModel);
            boolean flipV = current.containsKey("flipV") && (boolean) current.get("flipV");
            String modelKey = (String) current.get("model");
            Map<String, DynamicModelHolder> models = ResourceUtil.loadPartedDmh(new ResourceLocation(modelKey), flipV);
            String modelKeyLeft = "l", modelKeyCenter = "center", modelKeyRight = "r", modelKeyDlOn = "", modelKeyDlOff = "";
            if (current.containsKey("subModel") && current.get("subModel") instanceof Map<?, ?> m) {
                if (m.containsKey("left") && m.get("left") != null) modelKeyLeft = m.get("left").toString();
                if (m.containsKey("center") && m.get("center") != null) modelKeyCenter = m.get("center").toString();
                if (m.containsKey("right") && m.get("right") != null) modelKeyRight = m.get("right").toString();
            }
            if (current.containsKey("doorlight") && current.get("doorlight") instanceof Map<?, ?> m) {
                if (m.containsKey("on") && m.get("on") != null) modelKeyDlOn = m.get("on").toString();
                if (m.containsKey("off") && m.get("off") != null) modelKeyDlOff = m.get("off").toString();
                if (m.containsKey("type") && m.get("type") != null) {
                    String rawType = m.get("type").toString();
                    doorLightType = switch (rawType) {
                        case "common", "simple" -> 0;
                        default -> throw new IllegalStateException("Unexpected value: " + rawType);
                    };
                }
            }
            dmhLeft = models.get(modelKeyLeft);
            dmhCenter = models.get(modelKeyCenter);
            dmhRight = models.get(modelKeyRight);
            if (!"".equals(modelKeyDlOn)) dmhDlOn = models.get(modelKeyDlOn);
            if (!"".equals(modelKeyDlOff)) dmhDlOff = models.get(modelKeyDlOff);

            double leftSpace = 0, rightSpace = 0;
            double y1 = 0.75, z1 = 0.25, y2 = 0.25, z2 = 0.25;
            unit = 8;
            if (current.containsKey("left_space") && current.get("left_space") instanceof Number)
                leftSpace = ((Number) current.get("left_space")).doubleValue();
            if (current.containsKey("right_space") && current.get("right_space") instanceof Number)
                rightSpace = ((Number) current.get("right_space")).doubleValue();
            if (current.containsKey("tex") && current.get("tex") instanceof List<?> l) {
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
            if (current.containsKey("unit") && current.get("unit") instanceof Number n) unit = n.intValue();
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

            int texSize = 64;
            if (current.containsKey("texSize") && current.get("texSize") instanceof Number n) texSize = n.intValue();
            texW = texSize * length + 1;
            texH = texSize;
            initScriptDrawingAsync();

        } catch (Exception e) {
            Main.LOGGER.warn("Failed to load diaoban: {}", e.getMessage());
        }
    }

    @Override
    public void whenRendering() {
        ObjBlockScriptContext ctx = this.scriptContext;

        Matrices mat = new Matrices();
        mat.translate((-0.5 * unit * (length - 1)) / 16 + 0.5, 0, 0);
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

        if (dmhDisp != null) {
            if (dmhDisp.getUploadedModel() != null && GraphicsTextureHelper.getInstance().hasDrawGraphic(getBlockPos())) {
                dmhDisp.getUploadedModel().replaceAllTexture(GraphicsTextureHelper.getInstance().getBlockGraphics(getBlockPos()).identifier);
            }
            ctx.drawModel(dmhDisp, mat);
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
                    sendUpdateC2S();
                }
        ));
        return configs;
    }

    private void initScriptDrawingAsync() {
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
                (g, detail) -> {
                    if (routes.isEmpty()) return;
                    ScriptHolderBase holder = scriptHolder;
                    if (holder == null) return;
                    LocalRoute route;
                    Platform plat = null;
                    int index = 0;
                    if (!routes.isEmpty()) {
//                        RouteSelectionScreen.RouteSelectInfo routeSelectInfo = (RouteSelectionScreen.RouteSelectInfo) detail.get("route");
                        RouteSelectionScreen.RouteSelectInfo routeSelectInfo = routes.get(0);
                        route = routeSelectInfo.route;
                        plat = routeSelectInfo.plat;
                        if (plat != null)
                            index = route.getPlatformIdIndex(routeSelectInfo.plat.id);
                    } else route = new LocalRoute();
                    ScriptManager.getInstance().requestRunFunction(holder, "draw", g, drawState,
                            new DrawInfoDiaoban(
                                    route.asRouteDetail(), arrowDirection, plat, index, new int[]{0, 0, texW, texH}
                            ));
                },
                () -> {
//                    Map<String, Object> map = new HashMap<>();
//                    RouteSelectionScreen.RouteSelectInfo routeSelectInfo = routes.get(0);
//                    map.put("route", routeSelectInfo);
//                    return map;
                    return null;
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


}
