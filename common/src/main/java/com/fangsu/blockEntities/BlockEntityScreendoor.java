package com.fangsu.blockEntities;

import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.customItem.CustomItemLoader;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.extraConfig.*;
import com.fangsu.utils.CollisionBoxUtil;
import com.fangsu.utils.CustomItemHelper;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
//#if FABRIC
import fabric.cn.zbx1425.sowcer.math.Matrices;
import fabric.cn.zbx1425.mtrsteamloco.render.scripting.util.DynamicModelHolder;
//#elseif FORGE
//$$ import forge.cn.zbx1425.sowcer.math.Matrices;
//$$ import forge.cn.zbx1425.mtrsteamloco.render.scripting.util.DynamicModelHolder;
//#endif

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
    private boolean actualDoorSide = false;
    private Map<String, Map<String, Object>> loaded;
    private List<DoorRenderInfo> infos;


    private double dispDoorValue;
    private boolean cacheDispIsOpen;

    private long lastRenderTime;

    public BlockEntityScreendoor(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_SCREENDOOR.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("doorSide", "0");
        ensureExtraConfig("autoDoorSide", "true");

        doorSide = Integer.parseInt(extraConfigs.get("doorSide"));
        isAutoDoorSide = "true".equals(extraConfigs.get("autoDoorSide"));

        ObjBlockScriptContext ctx = this.scriptContext;
        BaseObjBlockEntity entity = this;

        dispDoorValue = (getDoorValue() >= 0.4f && getDoorTarget()) ? 1.0 : 0.0;

        ObjBlockProperty property = this.getProperty();

        String mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        String subModel = doorSide == 0 ?
                CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL_LEFT)
                : doorSide == 1 ?
                CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL_RIGHT) :
                CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL_FLEX);
        try {
            switch (doorSide) {
                case 0:
                    loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(entity.mainModel), "door.left");
                    break;
                case 1:
                    loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(entity.mainModel), "door.right");
                    break;
                case 2:
                    loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(entity.mainModel), "door.flex");
                    break;
                default:
                    this.markedError = true;
                    return;
            }

            if (!loaded.containsKey(subModel)) {
                markedError = true;
                return;
            }

            Map<String, Object> current = loaded.get(subModel);

            JsonObject mainJson = ResourceUtil.loadAsJSON(new ResourceLocation(entity.mainModel)).getAsJsonObject();
            String modelKey = mainJson.get("model").getAsString();
            boolean flipV = mainJson.has("flipV") ? mainJson.get("flipV").getAsBoolean() : false;
            Map<String, DynamicModelHolder> models = ResourceUtil.loadPartedDmh(new ResourceLocation(modelKey), flipV);

            if (current.get("doors") instanceof List<?> list) {
                infos = new ArrayList<DoorRenderInfo>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Map<String, Object> m2 = new HashMap<>();
                        for (Object o2 : m.keySet()) {
                            if (o2 instanceof String s) {
                                m2.put(s, m.get(o2));
                            }
                        }
                        infos.add(new DoorRenderInfo(m2, models));
                    }
                }
            }
        } catch (Exception e) {
            Main.LOGGER.warn(e.getMessage());
        }
    }

    @Override
    public void whenRendering() {
        ObjBlockScriptContext ctx = this.scriptContext;
        Map<String, String> extra = this.extraConfigs;

        Date date = new Date();
        long renderDelta = date.getTime() - lastRenderTime;
        lastRenderTime = date.getTime();

        actualDoorSide = dispDoorSide == 0;

        if (getDoorTarget()) {
            dispDoorValue += renderDelta / 1000.0 * 0.6;
            if (dispDoorValue > 1.0) dispDoorValue = 1.0;
        } else {
            dispDoorValue -= renderDelta / 1000.0 * 0.6;
            if (dispDoorValue < 0.0) dispDoorValue = 0.0;
        }

        if (infos != null)
            for (DoorRenderInfo info : infos) {
                Matrices mat = new Matrices();
                mat.translate(-1 * dispDoorValue * (info.step) * (actualDoorSide ? 1 : -1), 0, 0);
                ctx.drawModel(info.model, mat);
            }

        boolean dispIsOpen = dispDoorValue > 0.0;
        if (cacheDispIsOpen != dispIsOpen) {
            this.extraConfigs.put("doorTarget", dispIsOpen ? "true" : "false");
            sendUpdateC2S();
            cacheDispIsOpen = dispIsOpen;
        }
    }

    @Override
    public void whenSaving(Map<String, String> extraConfigs) {
        extraConfigs.put("isOpen", this.cacheDispIsOpen ? "true" : "false");
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
            switch (doorSide) {
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
                (be) -> this.subModels.getOrDefault("subModel", doorSide == 0 ? DEFAULT_SUB_MODEL_LEFT : doorSide == 1 ? DEFAULT_SUB_MODEL_RIGHT : doorSide == 2 ? DEFAULT_SUB_MODEL_FLEX : DEFAULT_SUB_MODEL_LEFT),
                (be, v) -> this.subModels.put("subModel", v)));
        return infos;
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        Map<String, String> extra = this.extraConfigs;
        configs.add(new EnumConfig(
                Component.translatable("ui.fangsu.screendoor.doorSide"),
                new ConfigSpec("list"),
                List.of(
                        Component.translatable("ui.fangsu.screendoor.doorSideLeft"),
                        Component.translatable("ui.fangsu.screendoor.doorSideRight"),
                        Component.translatable("ui.fangsu.screendoor.doorSideFlex")
                ),
                be -> getExtraConfigInt("doorSide", 0),
                (be, v) -> extra.put("doorSide", v.toString())
        ).setSaveOnChange(true));

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
