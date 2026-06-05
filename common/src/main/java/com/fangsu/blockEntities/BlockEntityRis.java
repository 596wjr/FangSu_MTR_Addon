package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.customItem.contents.RouteInfoSignContent;
import com.fangsu.drawing.sign.BaseRisDrawing;
import com.fangsu.drawing.sign.RisDrawManager;
import com.fangsu.extraConfig.*;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.ui.RouteSelectionScreen;
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
    private List<RouteSelectionScreen.RouteSelectInfo> routes;

    private CollisionBoxUtil.CollisionBox shape;

    /**
     * 上次注册绘制的标识，避免重复注册
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

            scriptDone = false;
            lastRegisteredDrawInfoId = "";
        } catch (Exception e) {
            Main.LOGGER.error("Route info sign content load error", e);
            markedError = true;
        }
    }

    private void initDrawingAsync() {
        if (content == null || !firstInit) return;

        // 通过 RisDrawManager 获取绘制实例（支持 Java 类和 JS 脚本）
        String scriptPath = content.getScript();
        BaseRisDrawing drawing = RisDrawManager.createDrawing(scriptPath);
        if (drawing == null) return;

        risDrawing = drawing;

        // 注册绘制
        if (!scriptDone) {
            GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
            routes = reloadRoute(getExtraConfig("routes", "[]"));
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
                        if (drawer == null) return;
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
            initDrawingAsync();
        }

        ObjBlockScriptContext ctx = this.scriptContext;
        ctx.drawModel(dmhMain, null);

        // 仅在贴图就绪后才绘制 display 模型
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
