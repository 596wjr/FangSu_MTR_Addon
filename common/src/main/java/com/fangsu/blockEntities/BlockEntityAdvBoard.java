package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.contents.AdvBoardContent;
import com.fangsu.extraConfig.*;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.RawMeshBuilder;
import com.fangsu.scripting.GifHelper;
import com.fangsu.scripting.ModelHelper;
import com.fangsu.shape.RawShape;
import com.fangsu.shape.ShapeCollection;
import com.fangsu.shape.ShapeUtil;
import com.fangsu.shape.RotatableShapeHelper;
import com.fangsu.utils.ContentInfoUtil;
import com.fangsu.utils.CustomItemHelper;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_ADV_BOARD;

public class BlockEntityAdvBoard extends FunctionalObjBlockEntity {
    private static final String MAIN_MODEL_KEY = "adv_board";
    private static final String DEFAULT_MAIN_MODEL = "fangsu:adv_board/common_adv.json";
    private static final String DEFAULT_SUB_MODEL = "adv_in_1";
    private static final String DEFAULT_IMAGE_PATH = "fangsu:ticketbarrier/welcome.png";

    private AdvBoardContent content;
    private Map<String, ResourceLocation> currentTextures;
    /** key → GIF 的 ResourceLocation.toString()，用于按 GIF 资源解绑 */
    private Map<String, String> gifBindings;

    private DynamicModelHolder dmhMain;
    private Map<String, DynamicModelHolder> dmhDispMap;

    private ShapeCollection shape;

    public BlockEntityAdvBoard(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_ADV_BOARD.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        String mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        String subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);
        ensureExtraConfig("width", "2");
        ensureExtraConfig("height", "2");
        ensureExtraConfig("images", "{}");

        // 服务端不需要加载模型和形状，跳过客户端专属操作
        if (level == null || !level.isClientSide) return;

        int width = getExtraConfigInt("width", 2);
        int height = getExtraConfigInt("height", 2);

        // 解绑旧 GIF（防止 whenLoading 被重复调用时泄漏）
        if (gifBindings != null) {
            for (String gifId : gifBindings.values()) {
                GifHelper.getInstance().unbindGif(gifId);
            }
        }

        try {
            content = ContentInfoUtil.getAdvBoardContent(mainModel, subModel);
            if (content == null) throw new RuntimeException("Content is null: " + mainModel + "=" + subModel);

            dmhDispMap = new HashMap<>();
            currentTextures = new HashMap<>();
            gifBindings = new HashMap<>();

            dmhMain = new DynamicModelHolder();

            var rawModelMap = ResourceUtil.loadPartedModel(new ResourceLocation(content.getModel()), content.isFlipV());
            var subModelKeyMap = content.getSubModelMap();
            RawModel[] models = new RawModel[AdvBoardContent.MODEL_KEYS.length];
            for (int i = 0; i < AdvBoardContent.MODEL_KEYS.length; i++) {
                models[i] = rawModelMap.get(subModelKeyMap.get(AdvBoardContent.MODEL_KEYS[i])).copy();
            }

            RawModel spiltModel = ModelHelper.buildSpiltModel(
                    models[0], models[1], models[2],
                    models[3], models[4], models[5],
                    models[6], models[7], models[8],
                    width, height, content.getWidthUnit(), content.getHeightUnit()
            );
            dmhMain.uploadLater(spiltModel);

            double x1 = -0.5 * content.getWidthUnit() * width + content.getBars().get(AdvBoardContent.BAR_KEYS[0]); // 宸
            double x2 = 0.5 * content.getWidthUnit() * width - content.getBars().get(AdvBoardContent.BAR_KEYS[2]); // 鍙硏
            double y1 = content.getHeightUnit() * height - content.getBars().get(AdvBoardContent.BAR_KEYS[2]);
            double y2 = content.getBars().get(AdvBoardContent.BAR_KEYS[3]);
            for (Map.Entry<String, Double> side : content.getFaces().entrySet()) {
                boolean isBack = !(side.getKey().toLowerCase().contains("front") || side.getKey().equalsIgnoreCase("front"));
                double z = side.getValue();

                DynamicModelHolder dmh = new DynamicModelHolder();
                RawMeshBuilder builder = new RawMeshBuilder(4, "light", new ResourceLocation("fangsu:pids/black.png"));

                double[] pos1 = new double[]{isBack ? -x1 : x1, y1, z};
                double[] pos2 = new double[]{isBack ? -x1 : x1, y2, z};
                double[] pos3 = new double[]{isBack ? -x2 : x2, y2, z};
                double[] pos4 = new double[]{isBack ? -x2 : x2, y1, z};

                ModelHelper.addQuad(builder,
                        new double[][]{
                                pos1, pos2, pos3, pos4
                        },
                        false
                );

                RawModel faceModel = new RawModel();
                faceModel.append(builder.getMesh());
                faceModel.generateNormals();
                dmh.uploadLater(faceModel);

                dmhDispMap.put(side.getKey(), dmh);
            }

            JsonObject images = Main.JSON_PARSER.parse(getExtraConfig("images", "{}")).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : images.entrySet()) {
                String k = entry.getKey();
                JsonObject v = entry.getValue().getAsJsonObject();
                String type = v.getAsJsonPrimitive("type").getAsString();
                String path = v.getAsJsonPrimitive("path").getAsString();
                switch (type) {
                    case "identifier", "local":
                        if (path.toLowerCase().endsWith(".gif")) {
                            //gif — 一个 GIF 文件只绑定一次，用 ResourceLocation.toString() 作为唯一 ID
                            ResourceLocation gifLocation = new ResourceLocation(path);
                            String gifId = gifLocation.toString();
                            ResourceLocation texLocation = GifHelper.getInstance().bindGif(gifId, gifLocation);
                            if (texLocation != null) {
                                currentTextures.put(k, texLocation);
                                gifBindings.put(k, gifId);
                            }
                        } else {
                            //png
                            currentTextures.put(k, new ResourceLocation(path));
                        }
                        break;
                    case "web":
                        //TODO
                        break;
                }
            }

            var shapeRawMap = content.getShapes();
            var shapeMap = new HashMap<String, ShapeCollection>();
            for (Map.Entry<String, float[][]> entry : shapeRawMap.entrySet()) {
                String k = entry.getKey();
                float[][] v = entry.getValue();
                shapeMap.put(k, new ShapeCollection(v));
            }
            ShapeCollection[] shapes = new ShapeCollection[AdvBoardContent.MODEL_KEYS.length];
            for (int i = 0; i < AdvBoardContent.MODEL_KEYS.length; i++) {
                String shapeKey = AdvBoardContent.MODEL_KEYS[i];
                shapes[i] = shapeMap.get(shapeKey);
                if (shapes[i] == null) {
                    Main.LOGGER.error("Shape {} is null for key {}", shapeKey, subModelKeyMap.get(shapeKey));
                    Main.LOGGER.info("keys: {}", shapeMap.keySet());
                    shapes[i] = new ShapeCollection();
                }
            }
            this.shape = ShapeUtil.buildSpiltShape(
                    shapes[0], shapes[1], shapes[2],
                    shapes[3], shapes[4], shapes[5],
                    shapes[6], shapes[7], shapes[8],
                    width, height, content.getWidthUnit(), content.getHeightUnit()
            );
//            Main.LOGGER.info("shape: {}", this.shape.toString());

        } catch (Exception e) {
            Main.LOGGER.warn(e.getMessage());
            for (StackTraceElement stackTraceElement : e.getStackTrace())
                Main.LOGGER.warn(stackTraceElement.toString());
            if (content != null) {
                Main.LOGGER.warn(content.toString());
            } else {
                Main.LOGGER.warn("content is null, cannot print details");
            }
            if (level != null && level.isClientSide) markedError = true;
        }
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
        if (markedError || shape == null || shape.isEmpty()) return Shapes.empty();
        // 涓庡舰鐘朵竴锟?
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
            // 棣栨璋冪敤鏃剁紦瀛樺皻鏈垵濮嬪寲锛岀洿鎺ュ熀浜庡師濮嬪舰鐘舵瀯锟?
            helper.initForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ, this.shape);
            rotated = helper.getShapeForBlock(getWorldPos(), translateX, translateY, translateZ, rotX, rotY, rotZ);
        }
        return rotated.move(trans.x, trans.y, trans.z).optimize();
    }

    @Override
    public void whenDisposing() {
        RotatableShapeHelper.getInstance().removeCache(getWorldPos());
        // 解绑所有 GIF（根据 gifBindings 中记录的 GIF ID 来解绑）
        if (gifBindings != null) {
            for (String gifId : gifBindings.values()) {
                GifHelper.getInstance().unbindGif(gifId);
            }
        }
    }

    @Override
    public void whenRendering() {
        if (dmhMain != null) scriptContext.drawModel(dmhMain, null);
        if (dmhDispMap != null) for (Map.Entry<String, DynamicModelHolder> entry : dmhDispMap.entrySet()) {
            String key = entry.getKey();
            ResourceLocation location = currentTextures.get(key);

            // 对于 GIF，从 GifHelper 获取最新的纹理位置
            if (location == null) {
                // 通过配置中的图片路径反向查找 GIF 纹理位置
                String path = getExtraConfig("images", "{}");
                try {
                    JsonObject images = Main.JSON_PARSER.parse(path).getAsJsonObject();
                    if (images.has(key)) {
                        String imgPath = images.get(key).getAsJsonObject().get("path").getAsString();
                        if (imgPath.toLowerCase().endsWith(".gif")) {
                            ResourceLocation gifLoc = new ResourceLocation(imgPath);
                            location = GifHelper.getInstance().getGifTextureLocation(gifLoc.toString());
                            if (location != null) {
                                currentTextures.put(key, location);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (location != null) {
                DynamicModelHolder dmh = dmhDispMap.get(key);
                if (dmh.getUploadedModel() != null) dmh.getUploadedModel().replaceAllTexture(location);
                scriptContext.drawModel(dmh.getUploadedModel(), null);
            }
        }
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.common.width"),
                new ConfigSpec("int").setParam("isInt", new JsonPrimitive(true)),
                () -> (float) getExtraConfigInt("width", 2),
                (f) -> {
                    setExtraConfig("width", String.valueOf(f.intValue()));
                    sendUpdateC2S();
                }
        ));
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.common.height"),
                new ConfigSpec("int").setParam("isInt", new JsonPrimitive(true)),
                () -> (float) getExtraConfigInt("height", 2),
                (f) -> {
                    setExtraConfig("height", String.valueOf(f.intValue()));
                    sendUpdateC2S();
                }
        ));

        JsonObject images = Main.JSON_PARSER.parse(getExtraConfig("images", "{}")).getAsJsonObject();
        if (content != null)
            for (String k : content.getFaces().keySet()) {
                JsonObject v = images.has(k) ? images.get(k).getAsJsonObject() : null;
                final String type = (v != null && v.has("type") && v.get("type").isJsonPrimitive())
                        ? v.getAsJsonPrimitive("type").getAsString() : "local";
                final String path = (v != null && v.has("path") && v.get("path").isJsonPrimitive())
                        ? v.getAsJsonPrimitive("path").getAsString() : DEFAULT_IMAGE_PATH;

                int typeIndex = switch (type) {
                    case "identifier", "local" -> 0;
                    case "web" -> 1;

                    default -> throw new IllegalStateException("Unexpected type: " + type);
                };

                configs.add(new EnumConfig(
                        ComponentHelper.translatable("ui.fangsu.adv.type_for", k),
                        new ConfigSpec("list"),
                        List.of(ComponentHelper.translatable("ui.fangsu.adv.type_locale")),
                        () -> typeIndex,
                        (newVal) -> {
                            String newType = switch (newVal) {
                                case 1 -> "web";
                                default -> "local";
                            };
                            updateDispType(k, newType);
                        }
                ));
                configs.add(new ResourceConfig(
                        ComponentHelper.translatable("ui.fangsu.adv.path_for", k),
                        new ConfigSpec("str"),
                        () -> path,
                        (newPath) -> {
                            updateDispPath(k, newPath);
                        },
                        List.of(".png", ".jpg", ".jpeg", ".gif")
                ));
            }

        return configs;
    }

    private void updateDispType(String key, String type) {
        JsonObject ori = Main.JSON_PARSER.parse(getExtraConfig("images", "{}")).getAsJsonObject();
        JsonObject disp = ori.has(key) ? ori.get(key).getAsJsonObject() : new JsonObject();
        disp.addProperty("type", type);
        if (ori.has(key)) ori.remove(key);
        ori.add(key, disp);
        setExtraConfig("images", ori.toString());
        sendUpdateC2S();
    }

    private void updateDispPath(String key, String path) {
        JsonObject ori = Main.JSON_PARSER.parse(getExtraConfig("images", "{}")).getAsJsonObject();
        JsonObject disp = ori.has(key) ? ori.get(key).getAsJsonObject() : new JsonObject();
        disp.addProperty("path", path);
        if (ori.has(key)) ori.remove(key);
        ori.add(key, disp);
        setExtraConfig("images", ori.toString());
        sendUpdateC2S();
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        return List.of(
                createSubModelSelectInfo("adv_board", DEFAULT_SUB_MODEL)
        );
    }

    @Override
    public String getMainModelKey() {
        return MAIN_MODEL_KEY;
    }
}
