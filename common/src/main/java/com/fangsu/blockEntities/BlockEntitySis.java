package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.Main;
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
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.shape.RotatableShapeHelper;
import com.fangsu.utils.CustomItemHelper;
import com.fangsu.utils.ContentInfoUtil;
import com.fangsu.utils.GraphicsTextureHelper;
import com.fangsu.utils.ResourceUtil;
import com.fangsu.utils.MtrUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_SIS;

public class BlockEntitySis extends BaseDisplayBlockEntity {
    private static final String MAIN_MODEL_KEY = "station_info_sign";
    private static final String DEFAULT_MAIN_MODEL = "fangsu:sis/mtr_sis.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_sis_1";

    private StationInfoSignContent content;

    private volatile BaseSisDrawing sisDrawing;

    private DynamicModelHolder dmhMain;

    private LocalStation stn;

    public BlockEntitySis(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_SIS.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        // whenLoading 可能改变 shape，清除形状缓存使 setShape 重新计算
        RotatableShapeHelper.getInstance().removeCache(getWorldPos());

        ensureExtraConfig("extraConfig", "{}");
        ensureExtraConfig("station", "0");

        String mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        String subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);

        parseUserExtraConfigs(getExtraConfig("extraConfig", "{}"));

        try {
            content = ContentInfoUtil.getSisContent(mainModel, subModel);
            if (content == null) {
                throw new IllegalStateException("[" + getBlockPos() + "]Content is null!");
            }

            dmhMain = ResourceUtil.loadDmh(new ResourceLocation(content.getModel()), content.isFlipV());
            buildDisplayFromSlots(content.getSlots(), "fangsu:pids/black.png");

            texW = content.getTexSize()[0];
            texH = content.getTexSize()[1];
            shape = buildShapeFromArray(content.getShape());

            var rawStn = MtrUtil.getStationById(Long.parseLong(getExtraConfig("station", "0")));
            stn = (rawStn == null) ? new LocalStation() : new LocalStation(rawStn);

            firstInit = true;
            resetDrawingState();
        } catch (Exception e) {
            Main.LOGGER.error("Station info sign content load error", e);
            markedError = true;
        }
    }

    private void initDrawingAsync() {
        if (!firstInit) return;

        // 如果车站数据还未加载完成（rawStn == null 或名称为"?"），跳过本次绘制注册
        if (stn.getRaw() == null || "?".equals(stn.name)) {
            return;
        }

        String scriptPath = content.getScript();
        BaseSisDrawing drawing = SisDrawManager.createDrawing(scriptPath);
        if (drawing == null) return;

        // 在注册绘制前构建路线并检查数据是否完整，避免在lambda中使用到不完整数据
        List<LocalRoute> routes = new ArrayList<>();
        if (stn.getRaw() != null) {
            for (mtr.data.Platform plat : MtrUtil.getPlatformByStation(stn.getRaw())) {
                routes.addAll(MtrUtil.getRouteByPlatform(plat));
            }
        }
        if (routes.isEmpty()) {
            return;
        }

        sisDrawing = drawing;

        LocalRoute[] routeArray = routes.toArray(new LocalRoute[0]);
        int arrowDirection = getExtraConfigInt("arrowDirection", 0);
        String drawInfoId = "SIS_" + scriptPath + "_" + stn.id + "_" + arrowDirection;

        tryRegisterDrawing(drawInfoId, texW, texH,
                gt -> {
                    BaseSisDrawing drawer = sisDrawing;
                    if (drawer == null) return;

                    SISDrawInfo drawInfo = new SISDrawInfo(
                            new int[]{0, 0, texW, texH}, stn, routeArray, new BlockInfo(this), this);

                    drawer.draw(gt, drawState, arrowDirection, texW, texH, drawInfo);
                }
        );
    }

    @Override
    public void whenRendering() {
        if (markedError) return;

        // 如果车站数据还未加载完成，节流重试
        if (stn != null && stn.getRaw() == null && firstInit && shouldRetryInit()) {
            var rawStn = MtrUtil.getStationById(Long.parseLong(getExtraConfig("station", "0")));
            if (rawStn != null) {
                stn = new LocalStation(rawStn);
                resetDrawingState();
            }
        }

        if (!scriptDone) {
            initDrawingAsync();
        } else if (content != null && stn != null && stn.getRaw() != null && shouldCheckDataChange()) {
            // 检测外部 MTR 数据变更（如车站名称、路线颜色），使用独立长间隔（2秒）
            var rawStn = MtrUtil.getStationById(Long.parseLong(getExtraConfig("station", "0")));
            if (rawStn != null) {
                String oldName = stn.name;
                int oldColor = stn.color;
                stn = new LocalStation(rawStn);
                if (!stn.name.equals(oldName) || stn.color != oldColor) {
                    resetDrawingState();
                    return;
                }
            }
        }

        ObjBlockScriptContext ctx = this.scriptContext;
        ctx.drawModel(dmhMain, null);
        renderDisplayModel(ctx);
    }

    @Override
    public void whenDisposing() {
        sisDrawing = null;
        super.whenDisposing();
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

        // 根据 content 的 extraConfig 定义动态生成配置项
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
                                    userExtraConfigs.put(savePos, new JsonPrimitive(String.valueOf(v)));
                                    extraConfigs.put("extraConfig", Main.GSON.toJson(userExtraConfigs));
                                    resetDrawingState();
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
                                    resetDrawingState();
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
