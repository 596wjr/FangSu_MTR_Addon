package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.customItem.contents.RouteInfoSignContent;
import com.fangsu.extraConfig.*;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.ui.RouteSelectionScreen;
import com.fangsu.userScripts.PidsScriptHolder;
import com.fangsu.userScripts.ScriptHolderBase;
import com.fangsu.userScripts.ScriptManager;
import com.fangsu.utils.*;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_RIS;

public class BlockEntityRis extends BaseObjBlockEntity implements RouteDrawer {
    private static final String MAIN_MODEL_KEY = "route_info_sign";
    private static final String DEFAULT_MAIN_MODEL = "fangsu:route_info_sign/mtr_route_info_sign.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_ris_wm";

    private RouteInfoSignContent content;

    private volatile ScriptHolderBase scriptHolder;

    private DynamicModelHolder dmhMain;
    private DynamicModelHolder dmhDisp = new DynamicModelHolder();

    private boolean firstInit = false;
    private boolean scriptInit = false;
    private final AtomicBoolean scriptLoaded = new AtomicBoolean(false);
    private boolean scriptDone = false;

    private volatile int scriptLoadToken = 0;
    private int texW, texH;
    private Map<String, Object> drawState = new HashMap<>();
    private List<RouteSelectionScreen.RouteSelectInfo> routes;

    private CollisionBoxUtil.CollisionBox shape;

    public BlockEntityRis(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_RIS.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("extraConfig", "{}");

        String mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        String subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);

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

            if (content.getShape() != null && content.getShape().length > 0)
                shape = new CollisionBoxUtil.CollisionBox(content.getShape());
            else shape = null;

            routes = reloadRoute(getExtraConfig("routes", "[]"));

            firstInit = true;

            scriptInit = false;
            scriptLoaded.set(false);
            scriptDone = false;
        } catch (Exception e) {
            Main.LOGGER.error("Route info sign content load error", e);
            markedError = true;
        }
    }

    private void initScriptDrawingAsync() {
        if (content == null) return;

        GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
        gtHelper.removeDrawGraphic(getBlockPos());

        final int thisLoadToken = ++scriptLoadToken;

        String scriptPath = content.getScript();
        ResourceLocation location = new ResourceLocation(scriptPath);
        if (!scriptInit || !scriptLoaded.get()) {
//            Main.LOGGER.info("initing script");
            AtomicBoolean isLoadError = new AtomicBoolean(false);
            CompletableFuture.runAsync(() -> {
                try {
                    ScriptHolderBase loadedHolder = ScriptManager.getInstance().getOrInitHolder(location, PidsScriptHolder::new);
                    if (loadedHolder != null && thisLoadToken == scriptLoadToken) {
                        scriptHolder = loadedHolder;
                        scriptLoaded.set(true);
//                        Main.LOGGER.info("[RIS] script loaded");
                    } else {
                        isLoadError.set(true);
                    }
                } catch (Throwable e) {
                    Main.LOGGER.error("Failed to load Route info sign script async {}", location, e);
                    isLoadError.set(true);
                }
            }, ScriptManager.SCRIPT_EXECUTOR);
            if (!isLoadError.get())
                scriptInit = true;
        }
        if (scriptLoaded.get() && !scriptDone) {
//            Main.LOGGER.info("registering drawing");
            routes = reloadRoute(getExtraConfig("routes", "[]"));
            gtHelper.addDrawGraphicWithGt(getBlockPos(),
                    new GraphicsTextureHelper.DrawInfo(
                            "RIS_" + scriptPath + "_" + routes + "_" + getExtraConfigInt("arrowDirection", 0),
                            texW, texH, true, false
                    ),
                    gt -> drawFunction(gt, scriptHolder, routes, drawState, getExtraConfigInt("arrowDirection", 0), texW, texH)
                    //TODO 支持多选
            );
            scriptDone = true;
        }
    }

    @Override
    public void whenRendering() {
        if ((!scriptInit || !scriptDone)) {
            initScriptDrawingAsync();
        }
        ObjBlockScriptContext ctx = this.scriptContext;
        ctx.drawModel(dmhMain, null);

        if (dmhDisp.getUploadedModel() != null && scriptDone && GraphicsTextureHelper.getInstance().hasDrawGraphic(getBlockPos())) {
            if (GraphicsTextureHelper.getInstance().isTextureAvailable(getBlockPos()) &&
                    GraphicsTextureHelper.getInstance().getBlockGraphics(getBlockPos()).isValid())
                dmhDisp.getUploadedModel().replaceAllTexture(GraphicsTextureHelper.getInstance().getBlockGraphics(getBlockPos()).identifier);
        }
        ctx.drawModel(dmhDisp, null);

    }

    @Override
    public void whenDisposing() {
        GraphicsTextureHelper.getInstance().removeDrawGraphic(getBlockPos());
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
//        if (markedError)
        return Shapes.empty();
//        return setShape(state);
    }

    @Override
    public VoxelShape setShape(BlockState state) {
//        if (markedError)
//            return Shapes.block();
//
//        Direction facing = state.getValue(BaseObjBlock.FACING);
//        Vec3 trans = transformOffset(facing, new Vec3(translateX, translateY, translateZ));
//        float rotX = this.rotateX;
//        float rotY = this.rotateY + (float) Math.toRadians(-facing.toYRot());
//        float rotZ = this.rotateZ;
//        long posLong = worldPosition.asLong();
//        if (shape != null) {
//            return CollisionBoxUtil.cachedRotatedShape(posLong, shape, trans, rotX, rotY, rotZ, 0.1f);
//        }

        return Shapes.block();
    }

    @Override
    public InteractionResult whenUseWithBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        int arrowDirection = getExtraConfigInt("arrowDirection", 0);
        arrowDirection += 1;
        if (arrowDirection >= 3) arrowDirection = 0;
        extraConfigs.put("arrowDirection", String.valueOf(arrowDirection));
        sendUpdateC2S();
        return InteractionResult.SUCCESS;
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        configs.add(new EnumConfig(
                Component.translatable("ui.fangsu.diaoban.arrowDirection"),
                new ConfigSpec("list"),
                List.of(
                        Component.translatable("ui.fangsu.diaoban.arrowNone"),
                        Component.translatable("ui.fangsu.diaoban.arrowLeft"),
                        Component.translatable("ui.fangsu.diaoban.arrowRight")
                ),
                () -> getExtraConfigInt("arrowDirection", 0),
                (v) -> {
                    extraConfigs.put("arrowDirection", v.toString());
                    sendUpdateC2S();
                }
        ));
        return configs;
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        infos.add(createSubModelSelectInfo("content", DEFAULT_SUB_MODEL));
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
    public String getMainModelKey() {
        return MAIN_MODEL_KEY;
    }
}
