package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.customItem.CustomItemLoader;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.extraConfig.*;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.utils.CollisionBoxUtil;
import com.fangsu.utils.CustomItemHelper;
import com.fangsu.utils.FacingBlockUtil;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

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

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_SCREENDOOR;

public class BlockEntityScreendoor extends BaseObjBlockEntity implements Syncable, IPlatformDoor {

    public static final String DEFAULT_MAIN_MODEL = "fangsu:screendoor/kaba.json";
    public static final String DEFAULT_SUB_MODEL_LEFT = "common";
    public static final String DEFAULT_SUB_MODEL_RIGHT = "common";
    public static final String DEFAULT_SUB_MODEL_FLEX = "common";
    public static final String MAIN_MODEL_KEY = "screendoor";

    private boolean doorTarget;
    private float doorValue;

    /**
     * 0 = left
     * 1 = right
     * 2 = flex
     */
    public int doorSide = 0;
    private int dispDoorSide = 0;

    public boolean isAutoDoorSide = true;

    // ★ 延迟自动计算
    private boolean pendingAutoDoorSide = false;

    private Map<String, Map<String, Object>> loaded;
    private List<DoorRenderInfo> infos;

    protected String mainModel;
    protected String subModel;

    private double dispDoorValue;
    private boolean cacheDispIsOpen;
    private long lastRenderTime;

    public BlockEntityScreendoor(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_SCREENDOOR.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("doorSide", "0");
        ensureExtraConfig("isAuto", "true");

        doorSide = Integer.parseInt(extraConfigs.get("doorSide"));
        isAutoDoorSide = extraConfigs.getOrDefault("isAuto", "true").equals("true");

        // 不在 loading 阶段直接算
        pendingAutoDoorSide = true;

        dispDoorValue = (getDoorValue() >= 0.4f && getDoorTarget()) ? 1.0 : 0.0;

        mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
    }

    // ★ 自动门方向重算（可重复调用）
    private void recomputeAutoDoorSide() {
        pendingAutoDoorSide = false;

        if (!isAutoDoorSide) {
            dispDoorSide = doorSide;
            return;
        }

        Level level = getLevel();
        if (level == null) return;

        BlockPos pos = getWorldPos();
        BlockState state = getBlockState();

        BlockEntity left = FacingBlockUtil.getLeftBlockEntity(level, pos, state);
        BlockEntity right = FacingBlockUtil.getRightBlockEntity(level, pos, state);

        boolean leftIsDoor = left instanceof BlockEntityScreendoor;
        boolean rightIsDoor = right instanceof BlockEntityScreendoor;

        if (leftIsDoor && rightIsDoor) dispDoorSide = 2;
        else if (leftIsDoor) dispDoorSide = 1;
        else if (rightIsDoor) dispDoorSide = 0;
        else dispDoorSide = 0;
    }

    @Override
    public void whenRendering() {

        // ★ 自动门方向在 render 阶段算
        if (pendingAutoDoorSide) {
            recomputeAutoDoorSide();
            reloadModels();
        }

        Date date = new Date();
        long delta = date.getTime() - lastRenderTime;
        lastRenderTime = date.getTime();

        if (getDoorTarget() || getDoorValue() >= 0.4f) {
            dispDoorValue = Math.min(1.0, dispDoorValue + delta / 1000.0 * 0.6);
        } else {
            dispDoorValue = Math.max(0.0, dispDoorValue - delta / 1000.0 * 0.6);
        }

        // ★ 修正后的开门方向判定
        int dir =
                dispDoorSide == 0 ? 1 :       // left
                        dispDoorSide == 1 ? -1 :      // right
                                1;                            // flex（由模型控制）

        ObjBlockScriptContext ctx = this.scriptContext;
        if (infos != null) {
            for (DoorRenderInfo info : infos) {
                Matrices mat = new Matrices();
                mat.translate(-dispDoorValue * info.step, 0, 0);
                ctx.drawModel(info.model, mat);
            }
        }

        boolean open = dispDoorValue > 0;
        if (cacheDispIsOpen != open) {
            extraConfigs.put("doorTarget", open ? "true" : "false");
            sendUpdateC2S();
            cacheDispIsOpen = open;
        }
    }

    // 抽出来，方便 auto 重算后调用
    private void reloadModels() {
        try {
            subModel =
                    dispDoorSide == 0 ? CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL_LEFT) :
                            dispDoorSide == 1 ? CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL_RIGHT) :
                                    CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL_FLEX);

            String key =
                    dispDoorSide == 0 ? "door.left" :
                            dispDoorSide == 1 ? "door.right" :
                                    "door.flex";

            loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(mainModel), key);

            if (!loaded.containsKey(subModel)) return;

            JsonObject mainJson = ResourceUtil.loadAsJSON(new ResourceLocation(mainModel)).getAsJsonObject();
            String modelKey = mainJson.get("model").getAsString();
            boolean flipV = mainJson.has("flipV") && mainJson.get("flipV").getAsBoolean();
            Map<String, DynamicModelHolder> models = ResourceUtil.loadPartedDmh(new ResourceLocation(modelKey), flipV);

            infos = new ArrayList<>();
            Object doors = loaded.get(subModel).get("doors");
            if (doors instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Map<String, Object> map = new HashMap<>();
                        for (Object k : m.keySet()) {
                            if (k instanceof String s) map.put(s, m.get(k));
                        }
                        infos.add(new DoorRenderInfo(map, models));
                    }
                }
            }
        } catch (Exception e) {
            Main.LOGGER.warn(e.getMessage());
        }
    }

    @Override
    public void whenSaving(Map<String, String> extraConfigs) {
        extraConfigs.put("isOpen", this.cacheDispIsOpen ? "true" : "false");
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
    public boolean getDoorTarget() {
        return doorTarget;
    }

    @Override
    public void setDoorTarget(boolean target) {
        this.doorTarget = target;

    }

    @Override
    public float getDoorValue() {
        return doorValue;
    }

    @Override
    public void setDoorValue(float value) {
        this.doorValue = value;
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
        doorTarget = this.extraConfigs.getOrDefault("doorTarget", "false").equals("true");
        if (getDoorTarget()) return Shapes.empty();
        return getFinalShape(state);
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        return getFinalShape(state);
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        List<ModelSelectInfo> thisInfo = new ArrayList<>();
        try {
            switch (dispDoorSide) {
                case 0:
                    loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(this.mainModel), "door.left");
                    break;
                case 1:
                    loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(this.mainModel), "door.right");
                    break;
                case 2:
                    loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(this.mainModel), "door.flex");
                    break;
                default:
                    return null;
            }
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
        } catch (Exception e) {
            return null;
        }
        infos.add(new SubModelDispInfo(Component.translatable("ui.fangsu.block.subModelSelect"), thisInfo,
                (be) -> this.subModels.getOrDefault("subModel",
                        dispDoorValue == 0 ? DEFAULT_SUB_MODEL_LEFT :
                                dispDoorValue == 1 ? DEFAULT_SUB_MODEL_RIGHT :
                                        dispDoorValue == 2 ? DEFAULT_SUB_MODEL_FLEX : DEFAULT_SUB_MODEL_LEFT),
                (be, v) -> this.subModels.put("subModel", v)));
        return infos;
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        Map<String, String> extra = this.extraConfigs;
        configs.add(new BoolConfig(
                Component.translatable("ui.fangsu.screendoor.isAuto"),
                new ConfigSpec("bool"),
                () -> extra.getOrDefault("isAuto", "true").equals("true"),
                (v) -> extra.put("isAuto", v ? "true" : "false")
        ).setSaveOnChange(true));
        configs.add(new EnumConfig(
                Component.translatable("ui.fangsu.screendoor.doorSide"),
                new ConfigSpec("list"),
                List.of(
                        Component.translatable("ui.fangsu.screendoor.doorSideLeft"),
                        Component.translatable("ui.fangsu.screendoor.doorSideRight"),
                        Component.translatable("ui.fangsu.screendoor.doorSideFlex")
                ),
                () -> getExtraConfigInt("doorSide", 0),
                (v) -> extra.put("doorSide", v.toString())
        ).setSaveOnChange(true).setShowCondition(v -> extra.getOrDefault("isAuto", "true").equals("false")));

        return configs;
    }

    private VoxelShape getFinalShape(BlockState state) {
        if (infos == null) return Shapes.empty();
        Direction facing = state.getValue(BaseObjBlock.FACING);
        Vec3 trans = transformOffset(facing, new Vec3(translateX, translateY, translateZ));
        float rotX = this.rotateX;
        float rotY = this.rotateY + (float) Math.toRadians(-facing.toYRot());
        float rotZ = this.rotateZ;
        long posLong = worldPosition.asLong();

        VoxelShape shape = Shapes.empty();
        for (DoorRenderInfo info : infos) {
            if (info.shape != null) {
                VoxelShape thisShape = CollisionBoxUtil.cachedRotatedShape(posLong, info.shape, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
                shape = Shapes.or(shape, thisShape.move(trans.x, trans.y, trans.z));
            }
        }
        return shape;
    }

    private static class DoorRenderInfo {
        DynamicModelHolder model;
        CollisionBoxUtil.CollisionBox shape;
        float step;

        private DoorRenderInfo(Map<String, Object> map, Map<String, DynamicModelHolder> models) {
            if (map.containsKey("step") && map.get("step") instanceof Number v) step = v.floatValue();
            if (map.containsKey("subModel") && map.get("subModel") instanceof String v) model = models.get(v);
            if (map.containsKey("shape") && map.get("shape") instanceof List<?> v)
                shape = new CollisionBoxUtil.CollisionBox(v);
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
