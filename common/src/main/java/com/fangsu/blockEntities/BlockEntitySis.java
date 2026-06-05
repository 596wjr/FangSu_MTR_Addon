package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.contents.StationInfoSignContent;
import com.fangsu.drawing.sign.BaseSisDrawing;
import com.fangsu.drawing.sign.SisDrawManager;
import com.fangsu.extraConfig.*;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.mtr.LocalStation;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.utils.*;
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

import java.util.*;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_SIS;

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
    private Map<String, Object> drawState = new HashMap<>();

    /**
     * 上次注册绘制的标识，避免重复注册
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

        String scriptPath = content.getScript();

        // 通过 SisDrawManager 获取绘制实例（支持 Java 类和 JS 脚本）
        BaseSisDrawing drawing = SisDrawManager.createDrawing(scriptPath);
        if (drawing == null) return;

        sisDrawing = drawing;

        // 注册绘制
        if (!scriptDone) {
            GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
            int arrowDirection = getExtraConfigInt("arrowDirection", 0);
            String drawInfoId = "SIS_" + scriptPath + "_" + arrowDirection;
            if (drawInfoId.equals(lastRegisteredDrawInfoId)) {
                scriptDone = true;
                return;
            }
            // 如果之前注册过不同标识的绘制，先移除
            gtHelper.removeDrawGraphic(getBlockPos());
            gtHelper.addDrawGraphicWithGt(getBlockPos(),
                    new GraphicsTextureHelper.DrawInfo(
                            drawInfoId,
                            texW, texH, true, false
                    ),
                    gt -> {
                        BaseSisDrawing drawer = sisDrawing;
                        if (drawer == null) return;

                        // 构建路线数据
                        List<LocalRoute> routes = new ArrayList<>();
                        if (stn.getRaw() != null)
                            for (mtr.data.Platform plat : MtrUtil.getPlatformByStation(stn.getRaw())) {
                                var platRoutes = MtrUtil.getRouteByPlatform(plat);
                                routes.addAll(platRoutes);
                            }
                        LocalRoute[] routeArray = routes.toArray(new LocalRoute[routes.size()]);

                        BlockEntitySis.SISDrawInfo drawInfo = new BlockEntitySis.SISDrawInfo(
                                new int[]{0, 0, texW, texH}, stn, routeArray, this);

                        drawer.draw(gt, drawState, arrowDirection, texW, texH, drawInfo);
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
        sisDrawing = null;
        GraphicsTextureHelper.getInstance().removeDrawGraphic(getBlockPos());
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {

        if (markedError) return Shapes.empty();
        return setShape(state);
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        if (markedError) return Shapes.block();
        return Shapes.block();
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        configs.add(new RunnableConfig(
                Component.translatable("ui.fangsu.common.selectStn"),
                new ConfigSpec("action"),
                () -> {
                    ClientHooks.openStationSelectionScreen(
                            Component.translatable("ui.fangsu.common.selectStn"),
                            null,
                            l -> {
                                if (l != null && !l.isEmpty()) {
                                    Long stationId = l.get(0);
                                    extraConfigs.put("station", stationId.toString());
                                    var rawStn = MtrUtil.getStationById(stationId);
                                    if (rawStn == null) stn = new LocalStation();
                                    else stn = new LocalStation(rawStn);
                                    scriptDone = false;
                                    sendUpdateC2S();
                                }
                            },
                            getBlockPos(), 1
                    );
                }
        ));
        return configs;
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        infos.add(createSubModelSelectInfo("content", DEFAULT_SUB_MODEL));
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
        public BlockEntitySis block;

        public SISDrawInfo(int[] texArea, LocalStation station, LocalRoute[] routes, BlockEntitySis block) {
            this.texArea = texArea;
            this.station = station;
            this.routes = routes;
            this.stationRoutes = routes;
            this.block = block;
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
