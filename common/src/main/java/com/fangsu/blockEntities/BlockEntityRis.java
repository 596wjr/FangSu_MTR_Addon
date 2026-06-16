package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mappings.GsonHelper;
import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.customItem.contents.RouteInfoSignContent;
import com.fangsu.drawing.ris.BaseRisDrawing;
import com.fangsu.drawing.ris.RisDrawManager;
import com.fangsu.extraConfig.*;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.shape.RawShape;
import com.fangsu.shape.RotatableShapeHelper;
import com.fangsu.shape.ShapeCollection;
import com.fangsu.ui.RouteSelectInfo;
import com.fangsu.utils.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_RIS;

public class BlockEntityRis extends BaseObjBlockEntity implements RouteDrawer {

    private static final String MAIN_MODEL_KEY = "route_info_sign";
    private static final String DEFAULT_MAIN_MODEL = "fangsu:route_info_sign/mtr_route_info_sign.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_ris_wm";

    private RouteInfoSignContent content;

    private volatile BaseRisDrawing risDrawing;

    private DynamicModelHolder dmhMain;
    private DynamicModelHolder dmhDisp = new DynamicModelHolder();

    private boolean firstInit = false;
    private boolean scriptDone = false;

    private int texW, texH;
    private Map<String, Object> drawState = new HashMap<>();
    private List<RouteSelectInfo> routes;
    Map<String, JsonElement> userExtraConfigs;

    private ShapeCollection shape;

    /**
     * 涓婃娉ㄥ唽缁樺埗鐨勬爣璇嗭紝閬垮厤閲嶅娉ㄥ唽
     */
    private String lastRegisteredDrawInfoId = "";

    public BlockEntityRis(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_RIS.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("extraConfig", "{}");

        String mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        String subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);

        try {
            userExtraConfigs = GsonHelper.asMap(Main.JSON_PARSER.parse(getExtraConfig("extraConfig", "{}")).getAsJsonObject());
        } catch (Throwable ignored) {
            userExtraConfigs = new HashMap<>();
        }

        try {
            content = ContentInfoUtil.getRisContent(mainModel, subModel);
            if (content == null) {
                throw new NullPointerException("[" + getBlockPos() + "]Content is null!");
            }

            dmhMain = ResourceUtil.loadDmh(new ResourceLocation(content.getModel()), content.isFlipV());

            RawMeshBuilder rawMeshBuilder = new RawMeshBuilder(4, "exterior", new ResourceLocation("fangsu:pids/black.png"));
            for (float[][] slot : content.getSlots()) {
                ModelHelper.addQuad(rawMeshBuilder, slot, false);
            }
            RawModel dispRawModel = new RawModel();
            dispRawModel.append(rawMeshBuilder.getMesh());
            dispRawModel.generateNormals();
            dmhDisp.uploadLater(dispRawModel);

            texW = content.getTexSize()[0];
            texH = content.getTexSize()[1];

            if (content.getShape() != null && content.getShape().length > 0) {
                shape = new ShapeCollection();
                for (float[] s : content.getShape()) {
                    if (s == null || s.length < 6) {
                        continue;
                    }
                    shape.add(new RawShape(
                            s[0] / 16.0, s[1] / 16.0, s[2] / 16.0,
                            s[3] / 16.0, s[4] / 16.0, s[5] / 16.0
                    ));
                }
            } else {
                shape = null;
            }

            routes = reloadRoute(getExtraConfig("routes", "[]"));

            firstInit = true;

            scriptDone = false;
            lastRegisteredDrawInfoId = "";
        } catch (Exception e) {
            Main.LOGGER.error("Route info sign content load error", e);
            markedError = true;
        }
    }

    private void initDrawingAsync() {
        if (content == null || !firstInit) {
            return;
        }

        // 閫氳繃 RisDrawManager 鑾峰彇缁樺埗瀹炰緥锛堟敮锟?Java 绫诲拰 JS 鑴氭湰锟?
        String scriptPath = content.getScript();
        BaseRisDrawing drawing = RisDrawManager.createDrawing(scriptPath);
        if (drawing == null) {
            return;
        }

        risDrawing = drawing;

        // 娉ㄥ唽缁樺埗
        if (!scriptDone) {
            GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
            // 鏌ヨ矾绾挎暟鎹槸鍚﹀凡鍔犺浇瀹屾垚锛坕d==0琛ㄧずMTR鏁版嵁灏氭湭灏辩华锛夛紝阆垮厤鐢╢allback鏁版嵁缁樺埗
            boolean hasRealRoutes = routes.stream().anyMatch(r -> r.route != null && r.route.id != 0L);
            if (!hasRealRoutes) {
                // MTR鏁版嵁鍙婃椂鏁版嵁灏氭湭灏辩华锛岃烦杩囨湰娆＄粯鍒舵敞鍐岋紝涓嬫害鏌舵椂閲嶈瘯
                return;
            }
            int arrowDirection = getExtraConfigInt("arrowDirection", 0);
            String drawInfoId = "RIS_" + scriptPath + "_" + routes + "_" + arrowDirection;
            if (drawInfoId.equals(lastRegisteredDrawInfoId)) {
                scriptDone = true;
                return;
            }
            gtHelper.removeDrawGraphic(getBlockPos());
            gtHelper.addDrawGraphicWithGt(getBlockPos(),
                    new GraphicsTextureHelper.DrawInfo(
                            drawInfoId,
                            texW, texH, true, false
                    ),
                    gt -> {
                        BaseRisDrawing drawer = risDrawing;
                        if (drawer == null) {
                            return;
                        }
                        drawer.draw(gt, routes, drawState, arrowDirection, texW, texH);
                    }
            );
            lastRegisteredDrawInfoId = drawInfoId;
            scriptDone = true;
        }
    }

    @Override
    public void whenRendering() {
        if (!scriptDone) {
            // 每次渲染时重新加载路线数据，检测MTR数据是否已就绪
            routes = reloadRoute(getExtraConfig("routes", "[]"));
            initDrawingAsync();
        }

        ObjBlockScriptContext ctx = this.scriptContext;
        ctx.drawModel(dmhMain, null);

        // 浠呭湪璐村浘灏辩华鍚庢墠缁樺埗 display 妯″瀷
        if (scriptDone && dmhDisp.getUploadedModel() != null
                && GraphicsTextureHelper.getInstance().isTextureAvailable(getBlockPos())) {
            GraphicsTexture tex = GraphicsTextureHelper.getInstance().getBlockGraphics(getBlockPos());
            if (tex != null && tex.isValid()) {
                dmhDisp.getUploadedModel().replaceAllTexture(tex.identifier);
                ctx.drawModel(dmhDisp.getUploadedModel(), null);
            }
        }
    }

    @Override
    public void whenDisposing() {
        risDrawing = null;
        RotatableShapeHelper.getInstance().removeCache(getWorldPos());
        GraphicsTextureHelper.getInstance().removeDrawGraphic(getBlockPos());
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
        if (markedError || shape == null || shape.isEmpty()) {
            return Shapes.empty();
        }
        return setShape(state);
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        if (markedError || shape == null || shape.isEmpty()) {
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
            helper.initForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ, shape);
            rotated = helper.getShapeForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ);
        }
        return rotated.move(trans.x, trans.y, trans.z).optimize();
    }

    @Override
    public InteractionResult whenUseWithBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        int arrowDirection = getExtraConfigInt("arrowDirection", 0);
        arrowDirection += 1;
        if (arrowDirection >= 3) {
            arrowDirection = 0;
        }
        extraConfigs.put("arrowDirection", String.valueOf(arrowDirection));
        sendUpdateC2S();
        return InteractionResult.SUCCESS;
    }

    @Override
    public void whenSaving(Map<String, String> extraConfigs) {
        if (userExtraConfigs != null) {
            extraConfigs.put("extraConfig", Main.GSON.toJson(userExtraConfigs));
        } else {
            extraConfigs.put("extraConfig", "{}");
        }
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        configs.add(new EnumConfig(
                ComponentHelper.translatable("ui.fangsu.diaoban.arrowDirection"),
                new ConfigSpec("list"),
                List.of(
                        ComponentHelper.translatable("ui.fangsu.diaoban.arrowNone"),
                        ComponentHelper.translatable("ui.fangsu.diaoban.arrowLeft"),
                        ComponentHelper.translatable("ui.fangsu.diaoban.arrowRight")
                ),
                () -> getExtraConfigInt("arrowDirection", 0),
                (v) -> {
                    extraConfigs.put("arrowDirection", v.toString());
                    scriptDone = false;
                    lastRegisteredDrawInfoId = "";
                    sendUpdateC2S();
                }
        ));

        // 锟?content 锟?extraConfig 瀹氫箟鍔ㄦ€佺敓鎴愰厤缃」
        // 姣忔閲嶆柊鑾峰彇 content锛岄伩鍏嶅垏鎹富妯″瀷鍚庡瓧娈垫湭鍚屾
        try {
            String currentSubModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
            String mM = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
            RouteInfoSignContent currentContent = ContentInfoUtil.getRisContent(mM, currentSubModel);
            if (currentContent != null && !currentContent.getExtraConfigDefs().isEmpty()) {
                for (JsonObject def : currentContent.getExtraConfigDefs()) {
                    String savePos = def.has("savePos") ? def.get("savePos").getAsString() : null;
                    JsonElement defaultVal = def.has("default") ? def.get("default") : null;

                    ConfigEntry<?> entry = JsonConfigParser.parse(
                            def,
                            () -> {
                                if (savePos != null && userExtraConfigs != null && userExtraConfigs.containsKey(savePos)) {
                                    return BlockEntityPids.convertJsonToType(userExtraConfigs.get(savePos), def);
                                }
                                if (defaultVal != null) {
                                    return BlockEntityPids.convertJsonToType(defaultVal, def);
                                }
                                return BlockEntityPids.getTypeDefault(def);
                            },
                            v -> {
                                if (savePos != null) {
                                    if (userExtraConfigs == null) {
                                        userExtraConfigs = new HashMap<>();
                                    }
                                    userExtraConfigs.put(savePos, new com.google.gson.JsonPrimitive(String.valueOf(v)));
                                    extraConfigs.put("extraConfig", Main.GSON.toJson(userExtraConfigs));
                                    scriptDone = false;
                                    lastRegisteredDrawInfoId = "";
                                    sendUpdateC2S();
                                }
                            }
                    );
                    configs.add(entry);
                }
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to load ris extraConfig: {}", e.getMessage());
        }

        return configs;
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        infos.add(createSubModelSelectInfo("content", DEFAULT_SUB_MODEL));
        infos.add(new SubModelMethodInfo(
                ComponentHelper.translatable("ui.fangsu.common.selectRoute"),
                () -> {
                    int maxSelect = 1;
                    try {
                        String currentSubModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
                        String mM = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
                        RouteInfoSignContent risContent = ContentInfoUtil.getRisContent(mM, currentSubModel);
                        if (risContent != null && risContent.getScriptSettings().has("max_select")) {
                            maxSelect = risContent.getScriptSettings().get("max_select").getAsInt();
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
                                scriptDone = false;
                                lastRegisteredDrawInfoId = "";
                                sendUpdateC2S();
                            },
                            getBlockPos(), maxSelect
                    );
                }
        ));
        return infos;
    }

    @Override
    public String getMainModelKey() {
        return MAIN_MODEL_KEY;
    }
}
