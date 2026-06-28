package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mappings.GsonHelper;
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
import com.fangsu.extraConfig.*;
import com.fangsu.utils.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.util.concurrent.ConcurrentHashMap;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_PIDS;

public class BlockEntityPids extends FunctionalObjBlockEntity {
    private static final String DEFAULT_MAIN_MODEL = "fangsu:pids/mtr_pids.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_pids_3b";
    private static final String MAIN_MODEL_KEY = "pids";

    protected String subModel;

    private DynamicModelHolder dmhMain, dmhDisp = new DynamicModelHolder();
    private CollisionBoxUtil.CollisionBox shape;
    private Map<String, JsonElement> userExtraConfigs;

    private volatile BasePidsDrawing pidsDrawing;
    private int texW, texH;
    private Map<String, Object> drawState = new ConcurrentHashMap<>();
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
        List<JsonElement> rawPlats = GsonHelper.asList(Main.JSON_PARSER.parse(getExtraConfig("plats")).getAsJsonArray());
        plats = new ArrayList<>();
        for (JsonElement rawPlat : rawPlats) {
            plats.add(rawPlat.getAsLong());
        }

        try {
            userExtraConfigs = GsonHelper.asMap(Main.JSON_PARSER.parse(getExtraConfig("extraConfig", "{}")).getAsJsonObject());
        } catch (Throwable ignored) {
            userExtraConfigs = new ConcurrentHashMap<>();
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
     * 涓婃娉ㄥ唽缁樺埗鐨勬爣璇嗭紝閬垮厤閲嶅娉ㄥ唽
     */
    private String lastRegisteredDrawInfoId = "";

    private void initDrawingAsync() {
        if (drawScriptKey == null || drawScriptKey.isEmpty()) return;

        GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();

        final String scriptKey = drawScriptKey;

        // 閫氳繃 PidsDrawManager 鑾峰彇缁樺埗瀹炰緥锛堟敮锟?Java 绫诲拰 JS 鑴氭湰锟?
        if (pidsDrawing == null) {
            pidsDrawing = PidsDrawManager.createDrawing(scriptKey);
        }
        if (pidsDrawing == null) return;

        // 鍘婚噸锛氬鏋滅粯鍒舵爣璇嗘湭鍙樺寲锛岃鏄庢暟鎹湭鏇存柊锛屾棤闇€閲嶆柊娉ㄥ唽
        String extraConfigStr = userExtraConfigs != null ? userExtraConfigs.toString() : "";
        String drawInfoId = "PIDS_" + scriptKey + "_" + plats + "_" + extraConfigStr;
        if (drawInfoId.equals(lastRegisteredDrawInfoId)) return;
        lastRegisteredDrawInfoId = drawInfoId;

        // 绉婚櫎鏃х粯鍒跺啀娉ㄥ唽鏂扮粯鍒
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
        // 纭繚缁樺埗宸叉敞鍐岋紙涓嶳IS/SIS/Diaoban淇濇寔涓€鑷达級
        initDrawingAsync();

        ObjBlockScriptContext ctx = this.scriptContext;
        if (dmhMain != null) ctx.drawModel(dmhMain, null);

        // 浠呭湪璐村浘灏辩华鍚庢墠缁樺埗 display 妯″瀷锛堜笌RIS/SIS/Diaoban淇濇寔涓€鑷达級
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

        // 閲嶈锛氭竻绌烘棫鏁版嵁
        extraConfigs.clear();

        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(64);
            String value = buf.readUtf(16384); // 寤鸿锟?sign 淇濇寔涓€锟?
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
        // 涓嬮潰锟?PIDS 涓撳睘閫昏緫
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
            userExtraConfigs = GsonHelper.asMap(Main.JSON_PARSER
                    .parse(extraConfigs.getOrDefault("extraConfig", "{}"))
                    .getAsJsonObject());
        } catch (Exception e) {
            userExtraConfigs = new ConcurrentHashMap<>();
        }


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
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        // 锟?content 锟?extraConfig 瀹氫箟鍔ㄦ€佺敓鎴愰厤缃」
        try {
            // 姣忔閲嶆柊璇诲彇 subModel锛岄伩鍏嶅垏鎹富妯″瀷鍚庡瓧娈垫湭鍚屾
            String currentSubModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
            PidsContent content = ContentInfoUtil.getPidsContent(mainModel, currentSubModel);
            if (content != null && !content.getExtraConfigDefs().isEmpty()) {
                for (JsonObject def : content.getExtraConfigDefs()) {
                    String savePos = def.has("savePos") ? def.get("savePos").getAsString() : null;
                    JsonElement defaultVal = def.has("default") ? def.get("default") : null;

                    ConfigEntry<?> entry = JsonConfigParser.parse(
                            def,
                            () -> {
                                if (savePos != null && userExtraConfigs != null && userExtraConfigs.containsKey(savePos)) {
                                    JsonElement el = userExtraConfigs.get(savePos);
                                    return convertJsonToType(el, def);
                                }
                                if (defaultVal != null) {
                                    return convertJsonToType(defaultVal, def);
                                }
                                return getTypeDefault(def);
                            },
                            v -> {
                                if (savePos != null) {
                                    if (userExtraConfigs == null) userExtraConfigs = new ConcurrentHashMap<>();
                                    userExtraConfigs.put(savePos, new com.google.gson.JsonPrimitive(String.valueOf(v)));
                                    extraConfigs.put("extraConfig", Main.GSON.toJson(userExtraConfigs));
                                    sendUpdateC2S();
                                }
                            }
                    );
                    configs.add(entry);
                }
            }
        } catch (Exception e) {
            Main.LOGGER.warn("Failed to load pids extraConfig for {}: {}", mainModel, e.getMessage());
        }
        return configs;
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        infos.add(createSubModelSelectInfo("content", DEFAULT_SUB_MODEL));
        infos.add(new SubModelMethodInfo(
                ComponentHelper.translatable("ui.fangsu.common.selectPlat"),
                () -> {
                    int maxSelect = 16;
                    try {
                        String currentSubModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
                        PidsContent pidsContent = ContentInfoUtil.getPidsContent(mainModel, currentSubModel);
                        if (pidsContent != null && pidsContent.getScriptSettings().has("max_select")) {
                            maxSelect = pidsContent.getScriptSettings().get("max_select").getAsInt();
                        }
                    } catch (Exception ignored) {}
                    ClientHooks.openPlatformSelectScreen(
                            ComponentHelper.translatable("ui.fangsu.common.selectPlat"),
                            plats,
                            l -> {
                                plats = l;
                                extraConfigs.put("plats", Main.GSON.toJson(plats));
                                sendUpdateC2S();
                            },
                            getBlockPos(), maxSelect
                    );
                }
        ));
        return infos;
    }

    private List<MtrUtil.PidsArrivalInfo> getArrivalInfoList() {
        return MtrUtil.getPidsArrivalInfoList(plats);
    }

    /* ============ extraConfig 杈呭姪鏂规硶 ============ */

    /**
     * 锟?JsonElement 杞崲涓洪€傚悎浼犲叆 setter 鐨勭被鍨嬶紙鏍规嵁 extraConfig def 锟?type 鎺ㄦ柇锛夛拷?
     */
    @SuppressWarnings("unchecked")
    static <T> T convertJsonToType(JsonElement el, JsonObject def) {
        String type = def.get("type").getAsString();
        return (T) switch (type) {
            case "number", "number_input" -> {
                if (def.has("param") && def.get("param").isJsonObject()
                        && def.getAsJsonObject("param").get("isInt") != null
                        && def.getAsJsonObject("param").get("isInt").getAsBoolean()) {
                    yield (Number) el.getAsInt();
                }
                yield (Number) el.getAsFloat();
            }
            case "bool" -> (Boolean) el.getAsBoolean();
            case "list" -> (Integer) el.getAsInt();
            default -> (T) el.getAsString();
        };
    }

    /**
     * 鏍规嵁 extraConfig def 锟?type 杩斿洖璇ョ被鍨嬬殑 Java 榛樿鍊硷拷?
     */
    @SuppressWarnings("unchecked")
    static <T> T getTypeDefault(JsonObject def) {
        String type = def.get("type").getAsString();
        return (T) switch (type) {
            case "number", "number_input" -> {
                boolean isInt = def.has("param") && def.get("param").isJsonObject()
                        && def.getAsJsonObject("param").get("isInt") != null
                        && def.getAsJsonObject("param").get("isInt").getAsBoolean();
                yield (Number) (isInt ? 0 : 0f);
            }
            case "bool" -> (Boolean) false;
            case "list" -> (Integer) 0;
            default -> (String) "";
        };
    }

    public static final class DrawInfoPids {
        public final List<MtrUtil.PidsArrivalInfo> arrivalInfoList;
        public final int[] texArea;
        public final ObjBlockScriptContext ctx;
        public final BlockEntityPids entity;
        /** 鐢ㄦ埛鑷畾涔夐厤缃紙鏉ヨ嚜 content JSON 锟?extraConfig锛夛紝閿负 savePos */
        public final Map<String, Object> extraConfig;

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
            this.extraConfig = convertUserExtraConfigs(entity.userExtraConfigs);
        }

        /**
         * 锟?{@code Map<String, JsonElement>} 杞崲锟?{@code Map<String, Object>} 锟?JS 浣跨敤锟?
         */
        private static Map<String, Object> convertUserExtraConfigs(Map<String, JsonElement> raw) {
            if (raw == null) return new HashMap<>();
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : raw.entrySet()) {
                JsonElement val = e.getValue();
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
                    Objects.equals(this.entity, that.entity) &&
                    Objects.equals(this.extraConfig, that.extraConfig);
        }

        @Override
        public int hashCode() {
            return Objects.hash(arrivalInfoList, texArea, ctx, entity, extraConfig);
        }

        @Override
        public String toString() {
            return "DrawInfoPids[" +
                    "arrivalInfoList=" + arrivalInfoList + ", " +
                    "texArea=" + texArea + ", " +
                    "ctx=" + ctx + ", " +
                    "entity=" + entity + ", " +
                    "extraConfig=" + extraConfig + ']';
        }

    }
}
