package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.client.ClientHooks;
import com.fangsu.customItem.CustomItemLoader;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.SubModelMethodInfo;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.userScripts.PidsScriptHolder;
import com.fangsu.userScripts.ScriptHolderBase;
import com.fangsu.userScripts.ScriptManager;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_PIDS;

public class BlockEntityPids extends BaseObjBlockEntity {
    private static final String DEFAULT_MAIN_MODEL = "fangsu:pids/mtr_pids.json";
    private static final String DEFAULT_SUB_MODEL = "mtr_pids_3b";
    private static final String MAIN_MODEL_KEY = "pids";

    protected String subModel;

    private DynamicModelHolder mainHolder, dispHolder = new DynamicModelHolder();
    private CollisionBoxUtil.CollisionBox shape;
    private Map<String, JsonElement> userExtraConfigs;

    private ScriptHolderBase scriptHolder;
    private Map<String, Map<String, Object>> loaded;
    private int texW, texH;
    private Map<String, Object> drawState = new HashMap<>();

    private List<Long> plats;
    private static final ExecutorService PIDS_SCRIPT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fangsu-pids-script-loader");
        t.setDaemon(true);
        return t;
    });
    private volatile int scriptLoadToken = 0;

    public BlockEntityPids(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_PIDS.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("extraConfig", "{}");
        ensureExtraConfig("plats", "[]");
        mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
        List<JsonElement> rawPlats = Main.JSON_PARSER.parse(getExtraConfig("plats")).getAsJsonArray().asList();
        plats = new ArrayList<>();
        for (JsonElement rawPlat : rawPlats) {
            plats.add(rawPlat.getAsLong());
        }

        try {
            userExtraConfigs = Main.JSON_PARSER.parse(getExtraConfigOrDefault("extraConfig", "{}")).getAsJsonObject().asMap();
        } catch (Throwable ignored) {
            userExtraConfigs = new HashMap<>();
        }

        try {
            loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(mainModel), "content");
            if (loaded == null || !loaded.containsKey(subModel)) {
                markedError = true;
                return;
            }
            Map<String, Object> current = loaded.get(subModel);
            if (current.containsKey("texSize") && current.get("texSize") instanceof List<?> l) {
                if (l.size() > 0) texW = ((Number) l.get(0)).intValue();
                else texW = 128;
                if (l.size() > 1) texH = ((Number) l.get(1)).intValue();
                else texH = 128;
            } else {
                texW = 128;
                texH = 128;
            }
            Main.LOGGER.info("texW={}, texH={}", texW, texH);
            if (current.containsKey("script")) {
                initScriptDrawingAsync(current);
            }
            boolean flipV = current.containsKey("flipV") && (boolean) current.get("flipV");
            String model = (String) current.get("model");
            mainHolder = ResourceUtil.loadDmh(new ResourceLocation(model), flipV);
            if (current.containsKey("slots") && current.get("slots") instanceof List<?> slotsList) {
                RawMeshBuilder builder = new RawMeshBuilder(4, "light", new ResourceLocation("fangsu:pids/black.png"));
                for (Object slot : slotsList) {
                    if (!(slot instanceof List<?> currentSlot)) continue;
                    List<List<Double>> finalList = new ArrayList<>();
                    for (Object o : currentSlot) {
                        if (!(o instanceof List<?> a)) continue;
                        List<Double> temp = new ArrayList<>();
                        for (Object o2 : a) {
                            if (!(o2 instanceof Number)) continue;
                            temp.add(((Number) o2).doubleValue());
                        }
                        if (temp.size() == 3) {
                            finalList.add(temp);
                        }
                    }
                    if (finalList.size() != 4) {
                        Main.LOGGER.warn("Invalid slot quad data: {}", slot);
                        continue;
                    }
                    ModelHelper.addQuad(builder, finalList, false);
                }
                RawModel dispRawModel = new RawModel();
                dispRawModel.append(builder.getMesh());
                dispRawModel.generateNormals();
                dispHolder.uploadLater(dispRawModel);
            }
        } catch (Exception e) {
            Main.LOGGER.warn(e.getMessage());
            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                Main.LOGGER.warn(stackTraceElement.toString());
            }
            markedError = true;
        }
    }


    private void initScriptDrawingAsync(Map<String, Object> current) {
        GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
        gtHelper.removeDrawGraphic(getBlockPos());

        final int thisLoadToken = ++scriptLoadToken;
        scriptHolder = null;

        String scriptPath = (String) current.get("script");
        ResourceLocation location = new ResourceLocation(scriptPath);

        gtHelper.addDrawGraphic(getBlockPos(),
                new GraphicsTextureHelper.DrawInfo(
                        "PIDS_" + scriptPath + "_" + plats,
                        texW, texH, false, false
                ),
                (g, detail) -> {
                    ScriptHolderBase holder = scriptHolder;
                    if (holder == null) return;
                    holder.runFunction("draw", g, drawState,
                            new DrawInfoPids((List<MtrUtil.PidsArrivalInfo>) detail.get("arrivalInfoList"), new int[]{0, 0, texW, texH}, scriptContext, this),
                            userExtraConfigs);
                },
                () -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("arrivalInfoList", getArrivalInfoList());
                    return map;
                }
        );

        CompletableFuture.runAsync(() -> {
            try {
                ScriptManager manager = ScriptManager.getInstance();
                manager.initHolder(location, PidsScriptHolder::new);
                ScriptHolderBase loadedHolder = manager.getHolder(location);
                if (loadedHolder != null && thisLoadToken == scriptLoadToken) {
                    scriptHolder = loadedHolder;
                }
            } catch (Throwable e) {
                Main.LOGGER.error("Failed to load PIDS script async {}", location, e);
            }
        }, PIDS_SCRIPT_EXECUTOR);
    }

    @Override
    public void whenDisposing() {
        scriptLoadToken++;
        drawState.clear();
        scriptHolder = null;
        GraphicsTextureHelper.getInstance().removeDrawGraphic(getBlockPos());
    }

    @Override
    public void whenRendering() {
        ObjBlockScriptContext ctx = this.scriptContext;
        if (mainHolder != null) ctx.drawModel(mainHolder, null);
        if (dispHolder != null) {
            if (dispHolder.getUploadedModel() != null) {
                GraphicsTexture gt = GraphicsTextureHelper.getInstance().getBlockGraphics(getBlockPos());
                if (gt != null)
                    dispHolder.getUploadedModel().replaceAllTexture(gt.identifier);
            }
            ctx.drawModel(dispHolder, null);
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
    public InteractionResult whenUseWithinBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
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
        List<ModelSelectInfo> thisInfo = new ArrayList<>();
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
        infos.add(new SubModelDispInfo(
                Component.translatable("ui.fangsu.block.subModelSelect"),
                thisInfo,
                (be) -> this.subModels.getOrDefault("subModel", DEFAULT_SUB_MODEL),
                (be, v) -> this.subModels.put("subModel", v)));
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

    private static Vec3 transformOffset(Direction facing, Vec3 trans) {
        return switch (facing) {
            case NORTH -> new Vec3(trans.x, trans.y, -trans.z);
            case SOUTH -> new Vec3(-trans.x, trans.y, trans.z);
            case WEST -> new Vec3(trans.z, trans.y, -trans.x);
            case EAST -> new Vec3(-trans.z, trans.y, trans.x);
            default -> trans;
        };
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
