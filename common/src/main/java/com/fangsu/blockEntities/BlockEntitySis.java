package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mappings.GsonHelper;
import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.customItem.contents.StationInfoSignContent;
import com.fangsu.drawing.sis.BaseSisDrawing;
import com.fangsu.drawing.sis.SisDrawManager;
import com.fangsu.extraConfig.*;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.mtr.LocalStation;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.shape.RawShape;
import com.fangsu.shape.RotatableShapeHelper;
import com.fangsu.shape.ShapeCollection;
import com.fangsu.utils.*;
import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_SIS;

import com.google.gson.JsonObject;

public class BlockEntitySis extends BaseObjBlockEntity {
    private static final String MAIN_MODEL_KEY = "station_info_sign";
    private static final String DEFAULT_MAIN_MODEL = "fangsu:sis/mtr_sis.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_sis_1";

    private StationInfoSignContent content;

    private volatile BaseSisDrawing sisDrawing;

    private DynamicModelHolder dmhMain;
    private DynamicModelHolder dmhDisp = new DynamicModelHolder();

    private boolean firstInit = false;
    private boolean scriptDone = false;

    private int texW, texH;

    private ShapeCollection shape;
    private Map<String, Object> drawState = new HashMap<>();
    Map<String, JsonElement> userExtraConfigs;

    /**
     * 涓婃娉ㄥ唽缁樺埗鐨勬爣璇嗭紝閬垮厤閲嶅娉ㄥ唽
     */
    private String lastRegisteredDrawInfoId = "";

    private LocalStation stn;

    public BlockEntitySis(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_SIS.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("extraConfig", "{}");
        ensureExtraConfig("station", "0");

        String mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        String subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);

        try {
            userExtraConfigs = GsonHelper.asMap(Main.JSON_PARSER.parse(getExtraConfig("extraConfig", "{}")).getAsJsonObject());
        } catch (Throwable ignored) {
            userExtraConfigs = new HashMap<>();
        }

        try {
            content = ContentInfoUtil.getSisContent(mainModel, subModel);
            if (content == null) {
                throw new IllegalStateException("[" + getBlockPos() + "]Content is null!");
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
                    if (s == null || s.length < 6) continue;
                    shape.add(new RawShape(
                            s[0] / 16.0, s[1] / 16.0, s[2] / 16.0,
                            s[3] / 16.0, s[4] / 16.0, s[5] / 16.0
                    ));
                }
            } else shape = null;

            var rawStn = MtrUtil.getStationById(Long.parseLong(getExtraConfig("station", "0")));
            if (rawStn == null) stn = new LocalStation();
            else stn = new LocalStation(rawStn);

            firstInit = true;

            scriptDone = false;
            lastRegisteredDrawInfoId = "";


        } catch (Exception e) {
            Main.LOGGER.error("Station info sign content load error", e);
            markedError = true;
        }
    }

    private void initDrawingAsync() {
        if (!firstInit) return;

        // 濡傛灉杞︾珯鏁版嵁杩樻湭鍔犺浇瀹屾垚锛坮awStn == null 鎴栧悕锟??"锛夛紝璺宠繃鏈缁樺埗娉ㄥ唽
        // 涓嬫 whenRendering 鏃朵細鍐嶆灏濊瘯锛岀洿鍒拌溅绔欐暟鎹氨锟?
        if (stn.getRaw() == null || "?".equals(stn.name)) {
            return;
        }

        String scriptPath = content.getScript();

        // 閫氳繃 SisDrawManager 鑾峰彇缁樺埗瀹炰緥锛堟敮锟?Java 绫诲拰 JS 鑴氭湰锟?
        BaseSisDrawing drawing = SisDrawManager.createDrawing(scriptPath);
        if (drawing == null) return;

        sisDrawing = drawing;

        // 娉ㄥ唽缁樺埗
        if (!scriptDone) {
            GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();

            // 鍦ㄦ敞鍐岀粯鍒跺墠灏辫喘寤鸿矾绾垮苟妫€鏌ユ暟鎹槸鍚﹀畬鏁达紝臩垮厤鍦▁ambda涓娇鐢ㄥ埌涓嶅畬鏁存暟鎹?
            List<LocalRoute> routes = new ArrayList<>();
            if (stn.getRaw() != null)
                for (mtr.data.Platform plat : MtrUtil.getPlatformByStation(stn.getRaw())) {
                    var platRoutes = MtrUtil.getRouteByPlatform(plat);
                    routes.addAll(platRoutes);
                }

            // 濡傛灉杞﹁溅绔欏彴璺嚎鏁版嵁灏氭湭鍔犺浇瀹屾垚锛堣矾绾夸负绌猴級锛岃烦杩囨湰娆＄粯鍒舵敞鍐岋紝涓嬫害鏃舵椂閲嶈瘯
            if (routes.isEmpty()) {
                return;
            }

            LocalRoute[] routeArray = routes.toArray(new LocalRoute[routes.size()]);

            int arrowDirection = getExtraConfigInt("arrowDirection", 0);
            String drawInfoId = "SIS_" + scriptPath + "_" + stn.id + "_" + arrowDirection;
            if (drawInfoId.equals(lastRegisteredDrawInfoId)) {
                scriptDone = true;
                return;
            }
            // 濡傛灉涔嬪墠娉ㄥ唽杩囦笉鍚屾爣璇嗙殑缁樺埗锛屽厛绉婚櫎
            gtHelper.removeDrawGraphic(getBlockPos());
            gtHelper.addDrawGraphicWithGt(getBlockPos(),
                    new GraphicsTextureHelper.DrawInfo(
                            drawInfoId,
                            texW, texH, true, false
                    ),
                    gt -> {
                        BaseSisDrawing drawer = sisDrawing;
                        if (drawer == null) return;

                        BlockEntitySis.SISDrawInfo drawInfo = new BlockEntitySis.SISDrawInfo(
                                new int[]{0, 0, texW, texH}, stn, routeArray, new BlockInfo(this), this);

                        drawer.draw(gt, drawState, arrowDirection, texW, texH, drawInfo);
                    }
            );
            lastRegisteredDrawInfoId = drawInfoId;
            scriptDone = true;
        }
    }

    @Override
    public void whenRendering() {
        // 濡傛灉杞︾珯鏁版嵁灏氭湭鍔犺浇瀹屾垚锛坮awStn == null锛夛紝姣忔娓叉煋鏃跺皾璇曢噸鏂拌幏锟?
        if (stn != null && stn.getRaw() == null && firstInit) {
            var rawStn = MtrUtil.getStationById(Long.parseLong(getExtraConfig("station", "0")));
            if (rawStn != null) {
                stn = new LocalStation(rawStn);
                scriptDone = false;
                lastRegisteredDrawInfoId = "";
            }
        }

        if (!scriptDone) {
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
        sisDrawing = null;
        RotatableShapeHelper.getInstance().removeCache(getWorldPos());
        GraphicsTextureHelper.getInstance().removeDrawGraphic(getBlockPos());
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
        if (markedError || shape == null || shape.isEmpty()) return Shapes.empty();
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
            helper.initForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ, shape);
            rotated = helper.getShapeForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ);
        }
        return rotated.move(trans.x, trans.y, trans.z).optimize();
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

        // 锟?content 锟?extraConfig 瀹氫箟鍔ㄦ€佺敓鎴愰厤缃」
        // 姣忔閲嶆柊鑾峰彇 content锛岄伩鍏嶅垏鎹富妯″瀷鍚庡瓧娈垫湭鍚屾
        try {
            String currentSubModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
            String mM = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
            StationInfoSignContent currentContent = ContentInfoUtil.getSisContent(mM, currentSubModel);
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
                                    if (userExtraConfigs == null) userExtraConfigs = new HashMap<>();
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
            Main.LOGGER.warn("Failed to load sis extraConfig: {}", e.getMessage());
        }

        return configs;
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        infos.add(createSubModelSelectInfo("content", DEFAULT_SUB_MODEL));
        infos.add(new SubModelMethodInfo(
                ComponentHelper.translatable("ui.fangsu.common.selectStn"),
                () -> {
                    int maxSelect = 1;
                    try {
                        String currentSubModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
                        String mM = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
                        StationInfoSignContent sisContent = ContentInfoUtil.getSisContent(mM, currentSubModel);
                        if (sisContent != null && sisContent.getScriptSettings().has("max_select")) {
                            maxSelect = sisContent.getScriptSettings().get("max_select").getAsInt();
                        }
                    } catch (Exception ignored) {}
                    ClientHooks.openStationSelectionScreen(
                            ComponentHelper.translatable("ui.fangsu.common.selectStn"),
                            null,
                            l -> {
                                if (l != null && !l.isEmpty()) {
                                    Long stationId = l.get(0);
                                    extraConfigs.put("station", stationId.toString());
                                    var rawStn = MtrUtil.getStationById(stationId);
                                    if (rawStn == null) stn = new LocalStation();
                                    else stn = new LocalStation(rawStn);
                                    scriptDone = false;
                                    lastRegisteredDrawInfoId = "";
                                    sendUpdateC2S();
                                }
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

    public static final class SISDrawInfo {
        public final int[] texArea;
        public LocalStation station;
        public LocalRoute[] routes, stationRoutes;
        public BlockInfo block;
        /**
         * 鐢ㄦ埛鑷畾涔夐厤缃紙鏉ヨ嚜 content JSON 锟?extraConfig锛夛紝閿负 savePos
         */
        public final Map<String, Object> extraConfig;

        public SISDrawInfo(int[] texArea, LocalStation station, LocalRoute[] routes, BlockInfo block, BlockEntitySis be) {
            this.texArea = texArea;
            this.station = station;
            this.routes = routes;
            this.stationRoutes = routes;
            this.block = block;
            this.extraConfig = convertUserExtraConfigs(be != null ? be.userExtraConfigs : null);
        }

        /**
         * 锟?{@code Map<String, JsonElement>} 杞崲锟?{@code Map<String, Object>} 锟?JS 浣跨敤锟?
         */
        private static Map<String, Object> convertUserExtraConfigs(Map<String, com.google.gson.JsonElement> raw) {
            if (raw == null) return new HashMap<>();
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> e : raw.entrySet()) {
                com.google.gson.JsonElement val = e.getValue();
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

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (SISDrawInfo) obj;
            return
                    Arrays.equals(this.texArea, that.texArea);
        }

        @Override
        public int hashCode() {
            return Objects.hash(texArea);
        }

        @Override
        public String toString() {
            return "SISDrawInfo[" +
                    "texArea=" + texArea + ']';
        }

    }
}
