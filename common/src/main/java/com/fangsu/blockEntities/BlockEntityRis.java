package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.Main;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.customItem.contents.RouteInfoSignContent;
import com.fangsu.drawing.ris.BaseRisDrawing;
import com.fangsu.drawing.ris.RisDrawManager;
import com.fangsu.extraConfig.*;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.shape.RotatableShapeHelper;
import com.fangsu.ui.RouteSelectInfo;
import com.fangsu.utils.CustomItemHelper;
import com.fangsu.utils.ContentInfoUtil;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_RIS;

public class BlockEntityRis extends BaseDisplayBlockEntity implements RouteDrawer {

    private static final String MAIN_MODEL_KEY = "route_info_sign";
    private static final String DEFAULT_MAIN_MODEL = "fangsu:route_info_sign/mtr_route_info_sign.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_ris_wm";

    private RouteInfoSignContent content;

    private volatile BaseRisDrawing risDrawing;

    private DynamicModelHolder dmhMain;
    private List<RouteSelectInfo> routes;

    public BlockEntityRis(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_RIS.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        // whenLoading 可能改变 shape，清除形状缓存使 setShape 重新计算
        RotatableShapeHelper.getInstance().removeCache(getWorldPos());

        ensureExtraConfig("extraConfig", "{}");

        String mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        String subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);

        parseUserExtraConfigs(getExtraConfig("extraConfig", "{}"));

        try {
            content = ContentInfoUtil.getRisContent(mainModel, subModel);
            if (content == null) {
                throw new NullPointerException("[" + getBlockPos() + "]Content is null!");
            }

            dmhMain = ResourceUtil.loadDmh(new ResourceLocation(content.getModel()), content.isFlipV());
            buildDisplayFromSlots(content.getSlots(), "fangsu:pids/black.png");

            texW = content.getTexSize()[0];
            texH = content.getTexSize()[1];
            shape = buildShapeFromArray(content.getShape());

            routes = reloadRoute(getExtraConfig("routes", "[]"));

            firstInit = true;
            resetDrawingState();
        } catch (Exception e) {
            Main.LOGGER.error("Route info sign content load error", e);
            markedError = true;
        }
    }

    private void initDrawingAsync() {
        if (content == null || !firstInit) {
            return;
        }

        String scriptPath = content.getScript();
        BaseRisDrawing drawing = RisDrawManager.createDrawing(scriptPath);
        if (drawing == null) {
            return;
        }

        // 检查路线数据是否已加载完成（id==0表示MTR数据尚未就绪），避免用fallback数据绘制
        boolean hasRealRoutes = routes.stream().anyMatch(r -> r.route != null && r.route.id != 0L);
        if (!hasRealRoutes) {
            return;
        }

        risDrawing = drawing;

        int arrowDirection = getExtraConfigInt("arrowDirection", 0);
        String drawInfoId = "RIS_" + scriptPath + "_" + routes + "_" + arrowDirection;

        tryRegisterDrawing(drawInfoId, texW, texH,
                gt -> {
                    BaseRisDrawing drawer = risDrawing;
                    if (drawer != null) {
                        drawer.draw(gt, routes, drawState, arrowDirection, texW, texH);
                    }
                }
        );
    }

    @Override
    public void whenRendering() {
        if (markedError) return;

        if (!scriptDone) {
            // 节流：仅在距上次重试足够长时间后才重新加载路线并尝试初始化绘制
            // 首次加载同步执行（仅一次，性能无影响）
            if (shouldRetryInit()) {
                routes = reloadRoute(getExtraConfig("routes", "[]"));
                initDrawingAsync();
            }
        } else if (content != null && shouldCheckDataChange()) {
            // 异步检测外部 MTR 数据变更（路线颜色等）
            // 主线程快照 MTR 数据后，后台线程完成 JSON 解析 + 查找，避免阻塞渲染
            triggerAsyncRouteReload(getExtraConfig("routes", "[]"));
        }

        // 检查异步重载结果
        List<RouteSelectInfo> newRoutes = pollAsyncRoutes();
        if (newRoutes != null && !routesEqual(newRoutes, routes)) {
            routes = newRoutes;
            resetDrawingState();
            return;
        }

        ObjBlockScriptContext ctx = this.scriptContext;
        ctx.drawModel(dmhMain, null);
        renderDisplayModel(ctx);
    }



    @Override
    public void whenDisposing() {
        risDrawing = null;
        super.whenDisposing();
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
                    resetDrawingState();
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
                                resetDrawingState();
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
