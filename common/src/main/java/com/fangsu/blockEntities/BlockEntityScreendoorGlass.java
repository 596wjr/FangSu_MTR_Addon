package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.contents.ScreendoorGlassContent;
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

    // auto 瑙ｆ瀽鍚庣殑瀹為檯缁撴灉
    private String actualSubModelLeft, actualSubModelRight;

    private Map<String, Map<String, Object>> loadedLeft, loadedRight;
    protected BlockRelation blockRelation = new BlockRelation();

    DynamicModelHolder dhmLeft, dhmRight;
    CollisionBoxUtil.CollisionBox shapeLeft, shapeRight;

    // auto 寤惰繜 / 閲嶇畻鎺у埗
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
            loadedLeft = ScreendoorGlassContent.loadLeftEntries(mainModel);
            loadedRight = ScreendoorGlassContent.loadRightEntries(mainModel);

            if (!loadedLeft.containsKey(subModelLeft) || !loadedRight.containsKey(subModelRight)) {
                markedError = true;
                return;
            }

            // 锟?涓嶅湪 loading 闃舵鐩存帴锟?auto
            pendingAuto = true;

        } catch (Exception e) {
            markedError = true;
            Main.LOGGER.warn(e.getMessage());
            for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                Main.LOGGER.error(stackTraceElement.toString());
            }
        }
    }

    // 锟?鎶藉嚭锟?auto 閲嶇畻閫昏緫
    private void recomputeAuto() {
        subModelLeft = subModels.getOrDefault("subModelLeft", DEFAULT_SUB_MODEL_LEFT);
        subModelRight = subModels.getOrDefault("subModelRight", DEFAULT_SUB_MODEL_RIGHT);
        actualSubModelLeft = subModelLeft;
        actualSubModelRight = subModelRight;

        Map<String, Object> a = loadedLeft.get(subModelLeft);
        Map<String, Object> b = loadedRight.get(subModelRight);
        boolean leftIsAuto = a != null && a.containsKey("auto");
        boolean rightIsAuto = b != null && b.containsKey("auto");

        String prevLeft = actualSubModelLeft;
        String prevRight = actualSubModelRight;

        try {
            if (leftIsAuto || rightIsAuto) {
                // 从实际 auto 的那一侧读取 autoKey，避免当只有一侧为 auto 时读取错误侧导致 NPE
                String autoKey;
                if (leftIsAuto) {
                    autoKey = (String) loadedLeft.get(subModelLeft).get("auto");
                } else {
                    autoKey = (String) loadedRight.get(subModelRight).get("auto");
                }
                List<JsonObject> commands = ScreendoorGlassContent.loadAutoCommands(mainModel, autoKey);

                blockRelation.refresh();

                for (int i = commands.size() - 1; i >= 0; i--) {
                    JsonObject commandJson = commands.get(i);
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
            }

            reloadModelAndShape();

            // 结果变了才通知邻居
            boolean changed =
                    !Objects.equals(prevLeft, actualSubModelLeft) ||
                            !Objects.equals(prevRight, actualSubModelRight);

            if (changed) {
                notifyNeighborsForAuto();
            }

        } catch (Exception e) {
            Main.LOGGER.warn("Auto recompute failed: {}", e.getMessage());
        }
    }

    // 妯″瀷 / 纰版挒绠卞埛锟?
    private void reloadModelAndShape() throws Exception {
        ScreendoorGlassContent.MainModelInfo modelInfo = ScreendoorGlassContent.loadMainModelInfo(mainModel);
        if (modelInfo == null) return;
        Map<String, DynamicModelHolder> models =
                ResourceUtil.loadPartedDmh(new ResourceLocation(modelInfo.model()), modelInfo.flipV());

        Map<String, Object> left = loadedLeft.get(actualSubModelLeft);
        Map<String, Object> right = loadedRight.get(actualSubModelRight);
        if (left == null || right == null) return;

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

    // 锟?閭诲眳閫氱煡锛堝彧宸﹀彸锟?
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
        if (DEFAULT_SUB_MODEL_LEFT.equals(subModels.getOrDefault("subModelLeft", DEFAULT_SUB_MODEL_LEFT))
                && actualSubModelLeft != null && !actualSubModelLeft.isEmpty()) {
            subModels.put("subModelLeft", actualSubModelLeft);
        }
        if (DEFAULT_SUB_MODEL_RIGHT.equals(subModels.getOrDefault("subModelRight", DEFAULT_SUB_MODEL_RIGHT))
                && actualSubModelRight != null && !actualSubModelRight.isEmpty()) {
            subModels.put("subModelRight", actualSubModelRight);
        }
    }

    @Override
    public InteractionResult whenUseWithOther(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult whenUseWithBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        triggerAutoSync(true, true);
        BlockEntity left = FacingBlockUtil.getLeftBlockEntity(level, pos, getBlockState());
        if (left instanceof BlockEntityScreendoorGlass g) {
            g.triggerAutoSync(false, true);
        }
        BlockEntity right = FacingBlockUtil.getRightBlockEntity(level, pos, getBlockState());
        if (right instanceof BlockEntityScreendoorGlass g) {
            g.triggerAutoSync(true, false);
        }
        return InteractionResult.SUCCESS;
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
        infoLeft.addAll(ScreendoorGlassContent.loadLeftModelSelectInfos(mainModel));
        infoRight.addAll(ScreendoorGlassContent.loadRightModelSelectInfos(mainModel));
        infos.add(new SubModelDispInfo(ComponentHelper.translatable("ui.fangsu.block.subModelLeftSelect"), infoLeft,
                (be) -> this.subModels.getOrDefault("subModelLeft", DEFAULT_SUB_MODEL_LEFT),
                (be, v) -> this.subModels.put("subModelLeft", v)));
        infos.add(new SubModelDispInfo(ComponentHelper.translatable("ui.fangsu.block.subModelRightSelect"), infoRight,
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

    private void triggerAutoSync(boolean triggerLeft, boolean triggerRight) {
        if (triggerLeft) subModels.put("subModelLeft", DEFAULT_SUB_MODEL_LEFT);
        if (triggerRight) subModels.put("subModelRight", DEFAULT_SUB_MODEL_RIGHT);
        pendingAuto = true;
        recomputeAuto();
        sendUpdateC2S();
        setChanged();
    }

    // =========================================================
    // BlockRelation锛堜笉浣跨敤 actual锛宎uto 浠嶇劧鏄悎娉曠姸鎬侊級
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

}
