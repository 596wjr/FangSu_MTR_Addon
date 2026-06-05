package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.customItem.contents.PidsContent;
import com.fangsu.drawing.pids.BasePidsDrawing;
import com.fangsu.drawing.pids.PidsDrawManager;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.utils.*;
import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
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

import java.util.*;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_PIDS;

public class BlockEntityPids extends BaseObjBlockEntity {
    private static final String DEFAULT_MAIN_MODEL = "fangsu:pids/mtr_pids.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_pids_3b";
    private static final String MAIN_MODEL_KEY = "pids";

    protected String subModel;

    private DynamicModelHolder dmhMain, dmhDisp = new DynamicModelHolder();
    private CollisionBoxUtil.CollisionBox shape;
    private Map<String, JsonElement> userExtraConfigs;

    private volatile BasePidsDrawing pidsDrawing;
    private int texW, texH;
    private Map<String, Object> drawState = new HashMap<>();
    private String drawScriptKey;

    private List<Long> plats;

    public BlockEntityPids(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_PIDS.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("extraConfig", "{}");
        ensureExtraConfig("plats", "[]");
        mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
        pidsDrawing = null;
        drawState.clear();
        lastRegisteredDrawInfoId = "";
        List<JsonElement> rawPlats = Main.JSON_PARSER.parse(getExtraConfig("plats")).getAsJsonArray().asList();
        plats = new ArrayList<>();
        for (JsonElement rawPlat : rawPlats) {
            plats.add(rawPlat.getAsLong());
        }

        try {
            userExtraConfigs = Main.JSON_PARSER.parse(getExtraConfig("extraConfig", "{}")).getAsJsonObject().asMap();
        } catch (Throwable ignored) {
            userExtraConfigs = new HashMap<>();
        }

        try {
            PidsContent content = ContentInfoUtil.getPidsContent(mainModel, subModel);
            if (content == null) {
                markedError = true;
                return;
            }
            List<Integer> texSize = content.getTexSize();
            texW = texSize.size() > 0 ? texSize.get(0) : 128;
            texH = texSize.size() > 1 ? texSize.get(1) : 128;
            Main.LOGGER.info("texW={}, texH={}", texW, texH);
            if (!content.getScript().isEmpty()) {
                drawScriptKey = content.getScript();
                initDrawingAsync();
            }
            boolean flipV = content.isFlipV();
            String model = content.getModel();
            dmhMain = ResourceUtil.loadDmh(new ResourceLocation(model), flipV);
            if (!content.getSlots().isEmpty()) {
                RawMeshBuilder builder = new RawMeshBuilder(4, "light", new ResourceLocation("fangsu:pids/black.png"));
                for (List<List<Double>> currentSlot : content.getSlots()) {
                    List<List<Double>> finalList = new ArrayList<>();
                    for (List<Double> point : currentSlot) {
                        if (point.size() == 3) finalList.add(point);
                    }
                    if (finalList.size() != 4) {
                        Main.LOGGER.warn("Invalid slot quad data: {}", currentSlot);
                        continue;
                    }
                    ModelHelper.addQuad(builder, finalList, false);
                }
                RawModel dispRawModel = new RawModel();
                dispRawModel.append(builder.getMesh());
                dispRawModel.generateNormals();
                dmhDisp.uploadLater(dispRawModel);
            }
            if (!content.getShape().isEmpty()) {
                this.shape = new CollisionBoxUtil.CollisionBox(content.getShape());
            }
        } catch (Exception e) {
            Main.LOGGER.warn(e.getMessage());
            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                Main.LOGGER.warn(stackTraceElement.toString());
            }
            markedError = true;
        }
    }


    /**
     * 上次注册绘制的标识，避免重复注册
     */
    private String lastRegisteredDrawInfoId = "";

    private void initDrawingAsync() {
        if (drawScriptKey == null || drawScriptKey.isEmpty()) return;

        GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();

        final String scriptKey = drawScriptKey;

        // 通过 PidsDrawManager 获取绘制实例（支持 Java 类和 JS 脚本）
        if (pidsDrawing == null) {
            pidsDrawing = PidsDrawManager.createDrawing(scriptKey);
        }
        if (pidsDrawing == null) return;

        // 去重：如果绘制标识未变化，说明数据未更新，无需重新注册
        String drawInfoId = "PIDS_" + scriptKey + "_" + plats;
        if (drawInfoId.equals(lastRegisteredDrawInfoId)) return;
        lastRegisteredDrawInfoId = drawInfoId;

        // 移除旧绘制再注册新绘制
        gtHelper.removeDrawGraphic(getBlockPos());
        gtHelper.addDrawGraphicWithGt(getBlockPos(),
                new GraphicsTextureHelper.DrawInfo(
                        drawInfoId,
                        texW, texH, false, false
                ),
                (gt) -> {
                    BasePidsDrawing drawer = pidsDrawing;
                    if (drawer == null) return;
                    drawer.draw(gt, getArrivalInfoList(), drawState, texW, texH,
                            new DrawInfoPids(getArrivalInfoList(), new int[]{0, 0, texW, texH}, scriptContext, this));
                }
        );
    }

    @Override
    public void whenDisposing() {
        drawState.clear();
        pidsDrawing = null;
        GraphicsTextureHelper.getInstance().removeDrawGraphic(getBlockPos());
    }

    @Override
    public void whenRendering() {
        // 确保绘制已注册（与RIS/SIS/Diaoban保持一致）
        initDrawingAsync();

        ObjBlockScriptContext ctx = this.scriptContext;
        if (dmhMain != null) ctx.drawModel(dmhMain, null);

        // 仅在贴图就绪后才绘制 display 模型（与RIS/SIS/Diaoban保持一致）
        if (dmhDisp != null && dmhDisp.getUploadedModel() != null
                && GraphicsTextureHelper.getInstance().isTextureAvailable(getBlockPos())) {
            GraphicsTexture gt = GraphicsTextureHelper.getInstance().getBlockGraphics(getBlockPos());
            if (gt != null && gt.isValid()) {
                dmhDisp.getUploadedModel().replaceAllTexture(gt.identifier);
                ctx.drawModel(dmhDisp.getUploadedModel(), null);
            }
        }
    }

    @Override
    public void whenSaving(Map<String, String> extraConfigs) {
        // plats
        if (plats != null) {
            extraConfigs.put("plats", Main.GSON.toJson(plats));
        } else {
            extraConfigs.put("plats", "[]");
        }

        // userExtraConfigs
        if (userExtraConfigs != null) {
            extraConfigs.put("extraConfig", Main.GSON.toJson(userExtraConfigs));
        } else {
            extraConfigs.put("extraConfig", "{}");
        }
    }

    @Override
    public void writeC2S(FriendlyByteBuf buf) {
        buf.writeFloat(translateX);
        buf.writeFloat(translateY);
        buf.writeFloat(translateZ);
        buf.writeFloat(rotateX);
        buf.writeFloat(rotateY);
        buf.writeFloat(rotateZ);
        buf.writeUtf(mainModel);

        buf.writeInt(extraConfigs.size());
        for (String key : extraConfigs.keySet()) {
            buf.writeUtf(key);
            buf.writeUtf(extraConfigs.get(key));
        }

        buf.writeInt(subModels.size());
        for (String key : subModels.keySet()) {
            buf.writeUtf(key);
            buf.writeUtf(subModels.get(key));
        }
    }

    @Override
    public void readC2S(FriendlyByteBuf buf) {

        translateX = buf.readFloat();
        translateY = buf.readFloat();
        translateZ = buf.readFloat();
        rotateX = buf.readFloat();
        rotateY = buf.readFloat();
        rotateZ = buf.readFloat();

        mainModel = buf.readUtf();

        // 重要：清空旧数据
        extraConfigs.clear();

        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            String value = buf.readUtf(16384); // 建议和 sign 保持一致
            extraConfigs.put(key, value);
        }

        subModels.clear();
        size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            String value = buf.readUtf(128);
            subModels.put(key, value);
        }

        // ==========================
        // 下面是 PIDS 专属逻辑
        // ==========================

        try {
            plats = Main.GSON.fromJson(
                    extraConfigs.getOrDefault("plats", "[]"),
                    new com.google.gson.reflect.TypeToken<List<Long>>() {
                    }.getType()
            );
        } catch (Exception e) {
            plats = new ArrayList<>();
        }

        try {
            userExtraConfigs = Main.JSON_PARSER
                    .parse(extraConfigs.getOrDefault("extraConfig", "{}"))
                    .getAsJsonObject()
                    .asMap();
        } catch (Exception e) {
            userExtraConfigs = new HashMap<>();
        }

        GraphicsTextureHelper.getInstance().removeDrawGraphic(getBlockPos());
        // 重置绘制标识，确保 whenLoading() 中的 initDrawingAsync() 会重新注册
        lastRegisteredDrawInfoId = "";

        whenLoading();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(
                    worldPosition,
                    getBlockState(),
                    getBlockState(),
                    3
            );
        }

        this.setChanged();
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
    public VoxelShape setShape(BlockState state) {
        if (markedError || shape == null) return Shapes.block();
        Direction facing = state.getValue(BaseObjBlock.FACING);
        Vec3 trans = transformOffset(facing, new Vec3(translateX, translateY, translateZ));
        float rotX = this.rotateX;
        float rotY = this.rotateY + (float) Math.toRadians(-facing.toYRot());
        float rotZ = this.rotateZ;
        long posLong = worldPosition.asLong();

        VoxelShape finalShape = Shapes.empty();
        if (shape != null) {
            VoxelShape thisShape = CollisionBoxUtil.cachedRotatedShape(posLong, shape, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
            finalShape = Shapes.or(finalShape, thisShape.move(trans.x, trans.y, trans.z));
        }
        return finalShape;
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
        if (markedError || shape == null) return Shapes.empty();
        return setShape(state);
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        infos.add(createSubModelSelectInfo("content", DEFAULT_SUB_MODEL));
        infos.add(new SubModelMethodInfo(
                Component.translatable("ui.fangsu.common.selectPlat"),
                () -> {
                    ClientHooks.openPlatformSelectScreen(
                            Component.translatable("ui.fangsu.common.selectPlat"),
                            plats,
                            l -> {
                                plats = l;
                                extraConfigs.put("plats", Main.GSON.toJson(plats));
                                sendUpdateC2S();
                            },
                            getBlockPos(), 16
                    );
                }
        ));
        return infos;
    }

    private List<MtrUtil.PidsArrivalInfo> getArrivalInfoList() {
        return MtrUtil.getPidsArrivalInfoList(plats);
    }

    public static final class DrawInfoPids {
        public final List<MtrUtil.PidsArrivalInfo> arrivalInfoList;
        public final int[] texArea;
        public final ObjBlockScriptContext ctx;
        public final BlockEntityPids entity;

        public DrawInfoPids(
                List<MtrUtil.PidsArrivalInfo> arrivalInfoList,
                int[] texArea,
                ObjBlockScriptContext ctx,
                BlockEntityPids entity
        ) {
            this.arrivalInfoList = arrivalInfoList;
            this.texArea = texArea;
            this.ctx = ctx;
            this.entity = entity;
        }

        public List<MtrUtil.PidsArrivalInfo> arrivalInfoList() {
            return arrivalInfoList;
        }

        public int[] texArea() {
            return texArea;
        }

        public ObjBlockScriptContext ctx() {
            return ctx;
        }

        public BlockEntityPids entity() {
            return entity;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (DrawInfoPids) obj;
            return Objects.equals(this.arrivalInfoList, that.arrivalInfoList) &&
                    Objects.equals(this.texArea, that.texArea) &&
                    Objects.equals(this.ctx, that.ctx) &&
                    Objects.equals(this.entity, that.entity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(arrivalInfoList, texArea, ctx, entity);
        }

        @Override
        public String toString() {
            return "DrawInfoPids[" +
                    "arrivalInfoList=" + arrivalInfoList + ", " +
                    "texArea=" + texArea + ", " +
                    "ctx=" + ctx + ", " +
                    "entity=" + entity + ']';
        }

    }
}
