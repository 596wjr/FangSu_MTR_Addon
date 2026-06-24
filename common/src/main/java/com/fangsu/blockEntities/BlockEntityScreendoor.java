package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.contents.ScreendoorDoorContent;
import com.fangsu.extraConfig.*;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.utils.CollisionBoxUtil;
import com.fangsu.utils.ContentInfoUtil;
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

    // ====== 闆嗘帶 / 闅旂鐩稿叧 ======
    /** 鏄惁鍙楅泦鎺ч攣瀹氾紙闆嗘帶鎺у埗涓級 */
    private boolean centralLocked = false;
    /** 闂ㄩ殧绂荤姸鎬侊紙浠呴潪闆嗘帶鏃跺彲鐢級 */
    private boolean isolation = false;
    /** 闂ㄥ紑鍚姸鎬侊紙浠呭湪闅旂鎵撳紑鏃舵湁鏁堬級 */
    private boolean doorOpenOverride = false;

    /**
     * 0 = left
     * 1 = right
     * 2 = flex
     */
    public int doorSide = 0;
    protected int dispDoorSide = 0;

    public boolean isAutoDoorSide = true;

    // 锟?寤惰繜鑷姩璁＄畻
    protected boolean pendingAutoDoorSide = false;

    private List<DoorRenderInfo> infos;

    protected String mainModel;
    protected String subModel;

    protected double dispDoorValue;
    private boolean cacheDispIsOpen;
    private long lastRenderTime;

    public BlockEntityScreendoor(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_SCREENDOOR.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("doorSide", "0");
        ensureExtraConfig("isAuto", "true");
        ensureExtraConfig("isolation", "false");
        ensureExtraConfig("doorOpenOverride", "false");

        doorSide = Integer.parseInt(extraConfigs.get("doorSide"));
        isAutoDoorSide = extraConfigs.getOrDefault("isAuto", "true").equals("true");
        isolation = extraConfigs.getOrDefault("isolation", "false").equals("true");
        doorOpenOverride = extraConfigs.getOrDefault("doorOpenOverride", "false").equals("true");
        doorTarget = extraConfigs.getOrDefault("doorTarget", "false").equals("true");

        // 涓嶅湪 loading 闃舵鐩存帴锟?
        pendingAutoDoorSide = true;

        dispDoorValue = (getDoorValue() >= 0.4f && getDoorTarget()) ? 1.0 : 0.0;

        mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
    }

    // 锟?鑷姩闂ㄦ柟鍚戦噸绠楋紙鍙噸澶嶈皟鐢級
    protected void recomputeAutoDoorSide() {
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

        // 锟?鑷姩闂ㄦ柟鍚戝湪 render 闃舵锟?
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

        // 锟?淇鍚庣殑寮€闂ㄦ柟鍚戝垽锟?
        int dir =
                dispDoorSide == 0 ? 1 :       // left
                        dispDoorSide == 1 ? -1 :      // right
                                1;                            // flex锛堢敱妯″瀷鎺у埗锟?

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
            cacheDispIsOpen = open;
            sendUpdateC2S();
        }
    }

    // 鎶藉嚭鏉ワ紝鏂逛究 auto 閲嶇畻鍚庤皟锟?
    private void reloadModels() {
        try {
            subModel =
                    dispDoorSide == 0 ? CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL_LEFT) :
                            dispDoorSide == 1 ? CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL_RIGHT) :
                                    CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL_FLEX);

            ScreendoorDoorContent doorContent = ContentInfoUtil.getScreendoorDoorContent(mainModel, subModel, dispDoorSide);
            if (doorContent == null) return;

            JsonObject mainJson = ResourceUtil.loadAsJSON(new ResourceLocation(mainModel)).getAsJsonObject();
            String modelKey = mainJson.get("model").getAsString();
            boolean flipV = mainJson.has("flipV") && mainJson.get("flipV").getAsBoolean();
            Map<String, DynamicModelHolder> models = ResourceUtil.loadPartedDmh(new ResourceLocation(modelKey), flipV);

            infos = new ArrayList<>();
            for (ScreendoorDoorContent.DoorInfo door : doorContent.getDoors()) {
                infos.add(new DoorRenderInfo(door, models));
            }
        } catch (Exception e) {
            Main.LOGGER.warn(e.getMessage());
        }
    }

    @Override
    public void whenSaving(Map<String, String> extraConfigs) {
        extraConfigs.put("isOpen", this.cacheDispIsOpen ? "true" : "false");
        extraConfigs.put("doorTarget", this.cacheDispIsOpen ? "true" : "false");
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
        // 闅旂涓斿紑闂ㄦ椂寮哄埗杩斿洖 true
        if (isolation && doorOpenOverride) {
            return true;
        }
        return doorTarget;
    }

    @Override
    public void setDoorTarget(boolean target) {
        // 闅旂鐘舵€佷笅涓嶆帴鍙楀閮ㄨ锟?
        if (!isolation) {
            this.doorTarget = target;
        }
    }

    @Override
    public float getDoorValue() {
        // 闅旂涓斿紑闂ㄦ椂寮哄埗锟?1.0
        if (isolation && doorOpenOverride) {
            return 1.0f;
        }
        return doorValue;
    }

    @Override
    public void setDoorValue(float value) {
        if (!isolation) {
            this.doorValue = value;
        }
    }

    @Override
    public boolean isLocked() {
        return isolation;
    }

    public boolean isCentralLocked() {
        return centralLocked;
    }

    public void setCentralLocked(boolean locked) {
        this.centralLocked = locked;
    }

    public boolean isIsolation() {
        return isolation;
    }

    public void setLocalIsolation(boolean isolation) {
        this.isolation = isolation;
    }

    public boolean isDoorOpenOverride() {
        return doorOpenOverride;
    }

    public void setLocalDoorOpenOverride(boolean doorOpen) {
        this.doorOpenOverride = doorOpen;
    }

    /**
     * 瑙ｉ櫎闅旂鏃堕噸缃棬锟?transient 鐩爣鐘舵€侊拷?
     * 淇濈暀 dispDoorValue 璁╂覆鏌撳姩鐢昏嚜鐒惰繃娓″埌鍏抽棴锟?
     */
    public void resetDoorState() {
        this.doorTarget = false;
        this.doorValue = 0f;
        this.cacheDispIsOpen = false;
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
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
        thisInfo.addAll(getModelSelectOptions(ContentInfoUtil.getScreendoorContentPath(dispDoorSide)));
        infos.add(new SubModelDispInfo(ComponentHelper.translatable("ui.fangsu.block.subModelSelect"), thisInfo,
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
                ComponentHelper.translatable("ui.fangsu.screendoor.isAuto"),
                new ConfigSpec("bool"),
                () -> extra.getOrDefault("isAuto", "true").equals("true"),
                (v) -> extra.put("isAuto", v ? "true" : "false")
        ).setSaveOnChange(true));
        configs.add(new EnumConfig(
                ComponentHelper.translatable("ui.fangsu.screendoor.doorSide"),
                new ConfigSpec("list"),
                List.of(
                        ComponentHelper.translatable("ui.fangsu.screendoor.doorSideLeft"),
                        ComponentHelper.translatable("ui.fangsu.screendoor.doorSideRight"),
                        ComponentHelper.translatable("ui.fangsu.screendoor.doorSideFlex")
                ),
                () -> getExtraConfigInt("doorSide", 0),
                (v) -> extra.put("doorSide", v.toString())
        ).setSaveOnChange(true).setShowCondition(v -> extra.getOrDefault("isAuto", "true").equals("false")));

        // ====== 闅旂鎺у埗锛堥潪闆嗘帶鏃跺彲鐢級 ======
        configs.add(new BoolConfig(
                ComponentHelper.translatable("ui.fangsu.screendoor.isolation"),
                new ConfigSpec("bool"),
                () -> extra.getOrDefault("isolation", "false").equals("true"),
                (v) -> {
                    extra.put("isolation", v ? "true" : "false");
                    isolation = v;
                }
        ).setSaveOnChange(true));
        configs.add(new BoolConfig(
                ComponentHelper.translatable("ui.fangsu.screendoor.doorOpenOverride"),
                new ConfigSpec("bool"),
                () -> extra.getOrDefault("doorOpenOverride", "false").equals("true"),
                (v) -> {
                    extra.put("doorOpenOverride", v ? "true" : "false");
                    doorOpenOverride = v;
                }
        ).setSaveOnChange(true).setShowCondition(v -> extra.getOrDefault("isolation", "false").equals("true")));

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

        private DoorRenderInfo(ScreendoorDoorContent.DoorInfo info, Map<String, DynamicModelHolder> models) {
            step = info.step();
            model = models.get(info.subModel());
            shape = new CollisionBoxUtil.CollisionBox(info.shape());
        }
    }

}
