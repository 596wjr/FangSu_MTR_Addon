package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.customItem.CustomItemLoader;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.utils.CollisionBoxUtil;
import com.fangsu.utils.CustomItemHelper;
import com.fangsu.utils.FacingBlockUtil;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;

import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;


import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_SCREENDOOR_GLASS;

public class BlockEntityScreendoorGlass extends BaseObjBlockEntity implements Syncable {

    public static final String DEFAULT_MAIN_MODEL = "fangsu:screendoor/kaba.json";
    public static final String DEFAULT_SUB_MODEL_LEFT = "auto";
    public static final String DEFAULT_SUB_MODEL_RIGHT = "auto";
    public static final String MAIN_MODEL_KEY = "screendoor";

    protected String mainModel;
    protected String subModelLeft, subModelRight;

    // auto 解析后的实际结果
    private String actualSubModelLeft, actualSubModelRight;

    private Map<String, Map<String, Object>> loadedLeft, loadedRight;
    protected BlockRelation blockRelation = new BlockRelation();

    DynamicModelHolder dhmLeft, dhmRight;
    CollisionBoxUtil.CollisionBox shapeLeft, shapeRight;

    // auto 延迟 / 重算控制
    private boolean pendingAuto = false;

    public BlockEntityScreendoorGlass(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_SCREENDOOR_GLASS.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        subModelLeft = CustomItemHelper.checkSubModel(this, "subModelLeft", DEFAULT_SUB_MODEL_LEFT);
        subModelRight = CustomItemHelper.checkSubModel(this, "subModelRight", DEFAULT_SUB_MODEL_RIGHT);

        actualSubModelLeft = subModelLeft;
        actualSubModelRight = subModelRight;

        dhmLeft = dhmRight = null;
        shapeLeft = shapeRight = null;

        try {
            loadedLeft = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(mainModel), "glass.left");
            loadedRight = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(mainModel), "glass.right");

            if (!loadedLeft.containsKey(subModelLeft) || !loadedRight.containsKey(subModelRight)) {
                markedError = true;
                return;
            }

            // ★ 不在 loading 阶段直接算 auto
            pendingAuto = true;

        } catch (Exception e) {
            markedError = true;
            Main.LOGGER.warn(e.getMessage());
            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                Main.LOGGER.error(stackTraceElement.toString());
            }
        }
    }

    // ★ 抽出的 auto 重算逻辑
    private void recomputeAuto() {
        Map<String, Object> a = loadedLeft.get(subModelLeft);
        Map<String, Object> b = loadedRight.get(subModelRight);
        boolean leftIsAuto = a != null && a.containsKey("auto");
        boolean rightIsAuto = b != null && b.containsKey("auto");

        if (!leftIsAuto && !rightIsAuto) return;

        String prevLeft = actualSubModelLeft;
        String prevRight = actualSubModelRight;

        try {
            JsonObject mainJson = ResourceUtil
                    .loadAsJSON(new ResourceLocation(mainModel))
                    .getAsJsonObject();

            String autoKey = (String) loadedLeft.get(subModelLeft).get("auto");
            JsonArray commands = mainJson
                    .getAsJsonObject("glass")
                    .getAsJsonArray(autoKey);

            blockRelation.refresh();

            for (int i = commands.size() - 1; i >= 0; i--) {
                JsonObject commandJson = commands.get(i).getAsJsonObject();
                if (!commandJson.has("if")) continue;

                boolean isAvailable = false;
                JsonElement ifElement = commandJson.get("if");

                // if: "LEFT_BLOCK"
                if (ifElement.isJsonPrimitive()) {
                    String key = ifElement.getAsString();
                    if ("TRUE".equals(key)) {
                        isAvailable = true;
                    } else {
                        String target = blockRelation.get(key);
                        if (commandJson.has("is")) {
                            isAvailable = target.equals(commandJson.get("is").getAsString());
                        } else if (commandJson.has("not")) {
                            isAvailable = !target.equals(commandJson.get("not").getAsString());
                        }
                    }
                }
                // if: { ... }
                else if (ifElement.isJsonObject()) {
                    boolean failed = false;
                    for (Map.Entry<String, JsonElement> e : ifElement.getAsJsonObject().entrySet()) {
                        String target = blockRelation.get(e.getKey());
                        JsonObject cond = e.getValue().getAsJsonObject();
                        if (cond.has("is") && !target.equals(cond.get("is").getAsString())) {
                            failed = true;
                            break;
                        }
                        if (cond.has("not") && target.equals(cond.get("not").getAsString())) {
                            failed = true;
                            break;
                        }
                    }
                    isAvailable = !failed;
                }

                if (isAvailable) {
                    if (leftIsAuto && commandJson.has("left"))
                        actualSubModelLeft = commandJson.get("left").getAsString();
                    if (rightIsAuto && commandJson.has("right"))
                        actualSubModelRight = commandJson.get("right").getAsString();
                }
            }

            // ★ 结果变了才通知邻居
            boolean changed =
                    !Objects.equals(prevLeft, actualSubModelLeft) ||
                            !Objects.equals(prevRight, actualSubModelRight);

            if (changed) {
                notifyNeighborsForAuto();
                reloadModelAndShape();
            }

        } catch (Exception e) {
            Main.LOGGER.warn("Auto recompute failed: {}", e.getMessage());
        }
    }

    // 模型 / 碰撞箱刷新
    private void reloadModelAndShape() throws Exception {
        JsonObject mainJson = ResourceUtil
                .loadAsJSON(new ResourceLocation(mainModel))
                .getAsJsonObject();

        String modelKey = mainJson.get("model").getAsString();
        boolean flipV = mainJson.has("flipV") && mainJson.get("flipV").getAsBoolean();
        Map<String, DynamicModelHolder> models =
                ResourceUtil.loadPartedDmh(new ResourceLocation(modelKey), flipV);

        Map<String, Object> left = loadedLeft.get(actualSubModelLeft);
        Map<String, Object> right = loadedRight.get(actualSubModelRight);

        dhmLeft = dhmRight = null;
        shapeLeft = shapeRight = null;

        if (left.containsKey("subModel") && models.containsKey(left.get("subModel"))) {
            dhmLeft = models.get(left.get("subModel"));
        }
        if (left.get("shape") instanceof List<?> l) {
            shapeLeft = new CollisionBoxUtil.CollisionBox(l);
        }

        if (right.containsKey("subModel") && models.containsKey(right.get("subModel"))) {
            dhmRight = models.get(right.get("subModel"));
        }
        if (right.get("shape") instanceof List<?> l) {
            shapeRight = new CollisionBoxUtil.CollisionBox(l);
        }
    }

    // ★ 邻居通知（只左右）
    private void notifyNeighborsForAuto() {
        Level level = getLevel();
        if (level == null) return;

        BlockPos pos = getWorldPos();
        for (BlockPos p : List.of(
                FacingBlockUtil.getLeftPos(pos, getBlockState()),
                FacingBlockUtil.getRightPos(pos, getBlockState())
        )) {
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof BlockEntityScreendoorGlass g) {
                g.pendingAuto = true;
            }
        }
    }

    @Override
    public void whenRendering() {
        if (pendingAuto) {
            pendingAuto = false;
            recomputeAuto();
        }

        ObjBlockScriptContext ctx = this.scriptContext;
        if (dhmLeft != null) ctx.drawModel(dhmLeft, null);
        if (dhmRight != null) ctx.drawModel(dhmRight, null);
    }

    @Override
    public void whenSaving(Map<String, String> extraConfigs) {
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
    public VoxelShape setCollisionShape(BlockState state) {
        if (markedError) return Shapes.empty();
        return getFinalShape(state);
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        if (markedError) return Shapes.block();
        return getFinalShape(state);
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        List<ModelSelectInfo> infoLeft = new ArrayList<>();
        List<ModelSelectInfo> infoRight = new ArrayList<>();
        try {
            for (String key : loadedLeft.keySet()) {
                Map<String, Object> item = loadedLeft.get(key);
                String text = "";
                String content = "";
                String contentText = null;
                if (item.containsKey("text") && item.get("text") instanceof String s) text = s;
                if (item.containsKey("id") && item.get("id") instanceof String s) content = s;
                if (item.containsKey("contentText") && item.get("contentText") instanceof String s) contentText = s;
                if (contentText != null) infoLeft.add(new ModelSelectInfo(text, content, contentText));
                else infoLeft.add(new ModelSelectInfo(text, content));
            }
            for (String key : loadedRight.keySet()) {
                Map<String, Object> item = loadedRight.get(key);
                String text = "";
                String content = "";
                String contentText = null;
                if (item.containsKey("text") && item.get("text") instanceof String s) text = s;
                if (item.containsKey("id") && item.get("id") instanceof String s) content = s;
                if (item.containsKey("contentText") && item.get("contentText") instanceof String s) contentText = s;
                if (contentText != null) infoRight.add(new ModelSelectInfo(text, content, contentText));
                else infoRight.add(new ModelSelectInfo(text, content));
            }
        } catch (Exception e) {
            return null;
        }
        infos.add(new SubModelDispInfo(Component.translatable("ui.fangsu.block.subModelLeftSelect"), infoLeft,
                (be) -> this.subModels.getOrDefault("subModelLeft", DEFAULT_SUB_MODEL_LEFT),
                (be, v) -> this.subModels.put("subModelLeft", v)));
        infos.add(new SubModelDispInfo(Component.translatable("ui.fangsu.block.subModelRightSelect"), infoRight,
                (be) -> this.subModels.getOrDefault("subModelRight", DEFAULT_SUB_MODEL_RIGHT),
                (be, v) -> this.subModels.put("subModelRight", v)));
        return infos;
    }

    private VoxelShape getFinalShape(BlockState state) {
        Direction facing = state.getValue(BaseObjBlock.FACING);
        Vec3 trans = transformOffset(facing, new Vec3(translateX, translateY, translateZ));
        float rotX = this.rotateX;
        float rotY = this.rotateY + (float) Math.toRadians(-facing.toYRot());
        float rotZ = this.rotateZ;
        long posLong = worldPosition.asLong();
        VoxelShape shape = Shapes.empty();
        if (shapeLeft != null)
            shape = Shapes.or(shape, CollisionBoxUtil.cachedRotatedShape(posLong, shapeLeft, Vec3.ZERO, rotX, rotY, rotZ, 0.1f).move(trans.x, trans.y, trans.z));
        if (shapeRight != null)
            shape = Shapes.or(shape, CollisionBoxUtil.cachedRotatedShape(posLong, shapeRight, Vec3.ZERO, rotX, rotY, rotZ, 0.1f).move(trans.x, trans.y, trans.z));
        return shape;
    }

    @Override
    public void afterChangeModel() {
        recomputeAuto();
    }

    // =========================================================
    // BlockRelation（不使用 actual，auto 仍然是合法状态）
    // =========================================================

    private class BlockRelation {
        String LEFT_BLOCK, RIGHT_BLOCK;
        String LEFT_GLASS_LEFT, LEFT_GLASS_RIGHT;
        String RIGHT_GLASS_LEFT, RIGHT_GLASS_RIGHT;
        String LEFT_DOOR, RIGHT_DOOR;

        private void refresh() {
            LEFT_BLOCK = RIGHT_BLOCK = "NULL";
            LEFT_GLASS_LEFT = LEFT_GLASS_RIGHT = "NULL";
            RIGHT_GLASS_LEFT = RIGHT_GLASS_RIGHT = "NULL";
            LEFT_DOOR = RIGHT_DOOR = "NULL";

            Level level = getLevel();
            if (level == null) return;

            BlockPos pos = getWorldPos();
            BlockState state = getBlockState();

            BlockEntity left = FacingBlockUtil.getLeftBlockEntity(level, pos, state);
            BlockEntity right = FacingBlockUtil.getRightBlockEntity(level, pos, state);

            if (left instanceof BlockEntityScreendoor d) {
                LEFT_BLOCK = "SCREENDOOR_DOOR";
                LEFT_DOOR = d.subModel;
            }
            if (right instanceof BlockEntityScreendoor d) {
                RIGHT_BLOCK = "SCREENDOOR_DOOR";
                RIGHT_DOOR = d.subModel;
            }
            if (left instanceof BlockEntityScreendoorGlass g) {
                LEFT_BLOCK = "SCREENDOOR_GLASS";
                LEFT_GLASS_LEFT = g.subModelLeft;
                LEFT_GLASS_RIGHT = g.subModelRight;
            }
            if (right instanceof BlockEntityScreendoorGlass g) {
                RIGHT_BLOCK = "SCREENDOOR_GLASS";
                RIGHT_GLASS_LEFT = g.subModelLeft;
                RIGHT_GLASS_RIGHT = g.subModelRight;
            }
        }

        private String get(String key) {
            return switch (key) {
                case "LEFT_BLOCK" -> LEFT_BLOCK;
                case "RIGHT_BLOCK" -> RIGHT_BLOCK;
                case "LEFT_GLASS_LEFT" -> LEFT_GLASS_LEFT;
                case "LEFT_GLASS_RIGHT" -> LEFT_GLASS_RIGHT;
                case "RIGHT_GLASS_LEFT" -> RIGHT_GLASS_LEFT;
                case "RIGHT_GLASS_RIGHT" -> RIGHT_GLASS_RIGHT;
                case "LEFT_DOOR" -> LEFT_DOOR;
                case "RIGHT_DOOR" -> RIGHT_DOOR;
                default -> "NULL";
            };
        }
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
}
