package com.fangsu.blockEntities;

import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;

import com.fangsu.customItem.ModelSelectInfo;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.CustomItemLoader;
import com.fangsu.customItem.contents.TicketBarrierContent;
import com.fangsu.Main;
import com.fangsu.utils.CustomItemHelper;
import com.fangsu.utils.ResourceUtil;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.utils.CollisionBoxUtil;
import com.fangsu.ticketSystem.*;
import com.fangsu.extraConfig.*;

import com.google.gson.JsonPrimitive;

import mtr.mappings.Text;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.Score;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_TICKET_BARRIER;

public class BlockEntityTicketBarrier extends BaseObjBlockEntity {
    public static final String DEFAULT_MAIN_MODEL = "fangsu:ticketbarrier/mtr_ticketbarrier.json";
    public static final String DEFAULT_SUB_MODEL = "mtr_ticketbarrier_1";
    public static final String MAIN_MODEL_KEY = "ticketBarrier";

    private boolean cacheIsOpen = false;
    private long closeTime = 0;
    private long openTime = 0;
    private static final int closeAnimationBeginTime = 500;
    private static final int doorAnimationTime = 200;
    private boolean animationDone = true;
    private float gatePos = 0.5f;

    private Map<String, Map<String, Object>> loaded;
    private DynamicModelHolder mainDmh;
    private TicketBarrierDoorRenderInfo subInfo;
    private CollisionBoxUtil.CollisionBox shape, collisionShape, doorCloseShape, doorCloseCollisionShape;
    private AABB ticketBox, cardBox;

    public BlockEntityTicketBarrier(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_TICKET_BARRIER.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("isOpen", "false");
        ensureExtraConfig("fareType", "0");
        ensureExtraConfig("isExit", "false");
        ensureExtraConfig("fareVal", "10");
        ensureExtraConfig("useCustomZone", "false");
        ensureExtraConfig("customZone", "0");
        ensureExtraConfig("customDisplayName", "");

        ObjBlockScriptContext ctx = this.scriptContext;
        BaseObjBlockEntity entity = this;
        ObjBlockProperty property = this.getProperty();

        String mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        String subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);

        try {
            loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(entity.mainModel));
            if (!loaded.containsKey(subModel)) {
                markedError = true;
                return;
            }
            Map<String, Object> current = loaded.get(subModel);
            mainDmh = ResourceUtil.loadDmh(new ResourceLocation((String) current.get("model")), (Boolean) current.get("flipV"));
            if (current.get("doors") instanceof ArrayList<?> doors) {
                for (Object door : doors) {
                    if (door instanceof Map<?, ?> d) {
                        TicketBarrierContent.TicketBarrierDoorInfo doorInfo = TicketBarrierContent.TicketBarrierDoorInfo.fromMap(d);
                        if (doorInfo != null) {
                            subInfo = new TicketBarrierDoorRenderInfo(doorInfo);
                        }
                    }
                }
            }

            if (current.containsKey("shape") && current.get("shape") instanceof List<?> s) {
                shape = new CollisionBoxUtil.CollisionBox(s);
            }
            if (current.containsKey("collisionShape") && current.get("collisionShape") instanceof List<?> s) {
                collisionShape = new CollisionBoxUtil.CollisionBox(s);
            } else {
                collisionShape = new CollisionBoxUtil.CollisionBox(List.of(List.of(-1, 0, 0, 1, 24, 16), List.of(15, 0, 0, 17, 24, 16)));
            }
            if (current.containsKey("doorCloseShape") && current.get("doorCloseShape") instanceof List<?> s) {
                doorCloseShape = new CollisionBoxUtil.CollisionBox(s);
            }
            if (current.containsKey("doorCloseCollisionShape") && current.get("doorCloseCollisionShape") instanceof List<?> s) {
                doorCloseCollisionShape = new CollisionBoxUtil.CollisionBox(s);
            } else {
                doorCloseCollisionShape = new CollisionBoxUtil.CollisionBox(List.of(List.of(1, 0, 12, 15, 24, 15)));
            }

            if (current.containsKey("gatePos") && current.get("gatePos") instanceof Number pos) {
                gatePos = pos.floatValue();
            } else gatePos = 0.5f;

            cardBox = null;
            ticketBox = null;
            if (current.containsKey("cardBox") && current.get("cardBox") instanceof List<?> rawList) {
                if (rawList.size() == 6) {
                    List<Double> doubleList = new ArrayList<>();
                    for (Object o : rawList) {
                        if (o instanceof Number) doubleList.add(((Number) o).doubleValue());
                    }
                    cardBox = new AABB(doubleList.get(0) / 16d, doubleList.get(1) / 16d, doubleList.get(2) / 16d, doubleList.get(3) / 16d, doubleList.get(4) / 16d, doubleList.get(5) / 16d);
                }
            }
            if (current.containsKey("ticketBox") && current.get("ticketBox") instanceof List<?> rawList) {
                if (rawList.size() == 6) {
                    List<Double> doubleList = new ArrayList<>();
                    for (Object o : rawList) {
                        if (o instanceof Number) doubleList.add(((Number) o).doubleValue());
                    }
                    ticketBox = new AABB(doubleList.get(0) / 16d, doubleList.get(1) / 16d, doubleList.get(2) / 16d, doubleList.get(3) / 16d, doubleList.get(4) / 16d, doubleList.get(5) / 16d);
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
        boolean isOpen = getExtraConfigBool("isOpen", false);
        long currentTime = System.currentTimeMillis();
        if (isOpen != cacheIsOpen) {
            cacheIsOpen = isOpen;
            if (!isOpen) {
                closeTime = currentTime;
            } else {
                openTime = currentTime;
            }
            Main.LOGGER.info("Open time : " + openTime);
            Main.LOGGER.info("Close time : " + closeTime);
        }

        if (mainDmh != null) ctx.drawModel(mainDmh, null);

        double doorAngle = animationDone ? isOpen ? 0d : 1d :
                isOpen ? 1 - (double) (currentTime - openTime) / doorAnimationTime :
                        (double) (currentTime - closeAnimationBeginTime - closeTime) / doorAnimationTime;
        doorAngle = clamp(doorAngle, 0, 1);
        animationDone = isOpen ? currentTime - openTime > doorAnimationTime : currentTime - closeAnimationBeginTime - closeTime > doorAnimationTime;
//        animationDone = false;

        if (subInfo != null) {
            for (TicketBarrierDoorRenderInfo.Door door : subInfo.doors) {
                Matrices mat = new Matrices();
                mat.translate(door.pos[0], door.pos[1], door.pos[2]);
                if (subInfo.doorType == 1) {
                    mat.rotateZ((float) (door.step * doorAngle * Math.PI));
                }
                if (subInfo.doorType == 2) {
                    if (door.side == 0) mat.rotateY((float) (0.5 * doorAngle * Math.PI));
                    else if (door.side == 1) mat.rotateY((float) (0.5 * doorAngle * Math.PI * -1));
                }
                ctx.drawModel(door.dmh, mat);
            }
        }
    }

    @Override
    public void whenSaving(Map<String, String> extraConfigs) {

    }

    @Override
    public InteractionResult whenUseWithOther(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ObjBlockScriptContext ctx = this.scriptContext;
        Map<String, String> extra = this.extraConfigs;

        Vec3 hitPos = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());

        Direction facing = level.getBlockState(pos)
                .getValue(BaseObjBlock.FACING);

        if ((facing == Direction.NORTH && hitPos.z > 0.5) ||
                (facing == Direction.SOUTH && hitPos.z < 0.5) ||
                (facing == Direction.WEST && hitPos.x > 0.5) ||
                (facing == Direction.WEST && hitPos.x < 0.5)
        )
            return TicketBarrierHandler.handle(
                    level,
                    pos,
                    player,
                    hand,
                    hit,
                    extra,
                    this::sendUpdateC2S
            );
        else {
            player.displayClientMessage(Component.translatable("mst.fangsu.ticketbarrier.wrongDirection"), true);
            return InteractionResult.PASS;
        }
    }

    @Override
    public void whenEntityInside(Player player) {

        ObjBlockScriptContext ctx = this.scriptContext;
        Map<String, String> extra = this.extraConfigs;
        boolean isOpen = getExtraConfigBool("isOpen", false);
        if (isOpen) {
            if (worldToLocal(player.position()).z > gatePos) {
                extraConfigs.put("isOpen", "false");
                sendUpdateC2S();
            }
        }
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {

        boolean isOpen = getExtraConfigBool("isOpen", false);
        VoxelShape resolved = buildCollisionShape(state, isOpen);
        if (resolved != null) return resolved;
//        Main.LOGGER.warn("using default cbox");
        return Block.box(0, 0, 0, 0, 0, 0);
    }

    @Override
    public VoxelShape setShape(BlockState state) {

        boolean isOpen = getExtraConfigBool("isOpen", false);
        VoxelShape resolved = buildOutlineShape(state, isOpen);
        if (resolved != null) return resolved;
        return Block.box(0, 0, 0, 16, 16, 16);
    }

    @Override
    public List<ConfigEntry<?>> getConfigs() {
        List<ConfigEntry<?>> configs = new ArrayList<>();
        Map<String, String> extra = this.extraConfigs;
        configs.add(new EnumConfig(
                Component.translatable("ui.fangsu.ticketbarrier.mode"),
                new ConfigSpec("list"),
                List.of(
                        Component.translatable("ui.fangsu.ticketbarrier.modeMtr"),
                        Component.translatable("ui.fangsu.ticketbarrier.modeFareOnce")
                ),
                () -> getExtraConfigInt("fareType", 0),
                (v) -> extra.put("fareType", v.toString())
        ).setSaveOnChange(true));
        configs.add(new BoolConfig(
                Component.translatable("ui.fangsu.ticketbarrier.isExit"),
                new ConfigSpec("bool"),
                () -> getExtraConfigBool("isExit", false),
                (v) -> extra.put("isExit", v.toString())
        ).setShowCondition(v -> {
            int fareType = getExtraConfigInt("fareType", 0);
            return fareType == 0;
        }));
        configs.add(new BoolConfig(
                Component.translatable("ui.fangsu.ticketbarrier.useCustomZone"),
                new ConfigSpec("bool"),
                () -> getExtraConfigBool("useCustomZone", false),
                (v) -> extra.put("useCustomZone", v.toString())
        ).setSaveOnChange(true).setShowCondition(v -> 0 == getExtraConfigInt("fareType", 0)));
        configs.add(new NumberInputConfig(
                Component.translatable("ui.fangsu.ticketbarrier.fareVal"),
                new ConfigSpec("number_input")
                        .setParam("max", new JsonPrimitive(32767))
                        .setParam("min", new JsonPrimitive(0))
                        .setParam("isInt", new JsonPrimitive(true)),
                () -> (float) getExtraConfigInt("fareVal", 10),
                (v) -> extra.put("fareVal", String.valueOf(v.intValue()))
        ).setShowCondition(v -> 1 == getExtraConfigInt("fareType", 0)));
        configs.add(new NumberInputConfig(
                Component.translatable("ui.fangsu.ticketbarrier.customZone"),
                new ConfigSpec("number_input")
                        .setParam("max", new JsonPrimitive(32767))
                        .setParam("min", new JsonPrimitive(-32768))
                        .setParam("isInt", new JsonPrimitive(true)),
                () -> (float) getExtraConfigInt("customZone", 0),
                (v) -> extra.put("customZone", String.valueOf(v.intValue()))
        ).setShowCondition(v -> 0 == getExtraConfigInt("fareType", 0) && getExtraConfigBool("useCustomZone", false)));
        configs.add(new StringConfig(
                Component.translatable("ui.fangsu.ticketbarrier.customDisplayName"),
                new ConfigSpec("string"),
                () -> extra.getOrDefault("customDisplayName", ""),
                (v) -> extra.put("customDisplayName", v)
        ).setShowCondition(v -> 0 == getExtraConfigInt("fareType", 0) && getExtraConfigBool("useCustomZone", false)));
        return configs;
    }

    @Override
    public java.lang.String getMainModelKey() {
        return MAIN_MODEL_KEY;
    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        List<ModelSelectInfo> thisInfo = new ArrayList<>();
        try {
            loaded = CustomItemLoader.optimizeCustomItemJSON(new ResourceLocation(this.mainModel));
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
                (be) -> this.subModels.getOrDefault("subModel", DEFAULT_SUB_MODEL),
                (be, v) -> this.subModels.put("subModel", v)));
        return infos;
    }

    private double clamp(double num, double min, double max) {
        return Math.min(Math.max(num, min), max);
    }

    private static class TicketBarrierDoorRenderInfo {
        int doorType = 1;
        List<Door> doors = new ArrayList<>();

        private TicketBarrierDoorRenderInfo(TicketBarrierContent.TicketBarrierDoorInfo doorInfo) throws Exception {
            this.doorType = doorInfo.getDoorType();
            if (!doorInfo.isUsePartedModel()) {
                return;
            }
            Map<String, DynamicModelHolder> dmhs = ResourceUtil.loadPartedDmh(new ResourceLocation(doorInfo.getModel()), doorInfo.isFlipV());
            for (TicketBarrierContent.TicketBarrierDoorInfo.DoorInfo door : doorInfo.getDoors()) {
                List<Double> posList = door.getPos();
                if (posList.size() < 3) {
                    continue;
                }
                DynamicModelHolder dmh = dmhs.get(door.getSubModel());
                if (dmh == null) {
                    continue;
                }
                Double[] posArray = posList.toArray(Double[]::new);
                doors.add(new Door(dmh, posArray, door.getSide(), door.getStep()));
            }
        }

        private record Door(DynamicModelHolder dmh, Double[] pos, int side, double step) {
        }
    }

    private VoxelShape buildOutlineShape(BlockState state, boolean isOpen) {
        if (shape == null) return null;
        Direction facing = state.getValue(BaseObjBlock.FACING);
        Vec3 trans = transformOffset(facing, new Vec3(translateX, translateY, translateZ));
        float rotX = this.rotateX;
        float rotY = this.rotateY + (float) Math.toRadians(-facing.toYRot());
        float rotZ = this.rotateZ;
        long posLong = worldPosition.asLong();
        VoxelShape openShape = CollisionBoxUtil.cachedRotatedShape(posLong, shape, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
        openShape = openShape.move(trans.x, trans.y, trans.z);
        if (isOpen || doorCloseShape == null) {
            return openShape;
        }
        VoxelShape closeShape = CollisionBoxUtil.cachedRotatedShape(posLong, doorCloseShape, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
        closeShape = closeShape.move(trans.x, trans.y, trans.z);
        return Shapes.or(openShape, closeShape);
    }

    private VoxelShape buildCollisionShape(BlockState state, boolean isOpen) {
        CollisionBoxUtil.CollisionBox baseCollision = collisionShape != null ? collisionShape : shape;
        if (baseCollision == null) return null;
        CollisionBoxUtil.CollisionBox closeCollision = doorCloseCollisionShape != null ? doorCloseCollisionShape : doorCloseShape;
        Direction facing = state.getValue(BaseObjBlock.FACING);
        Vec3 trans = transformOffset(facing, new Vec3(translateX, translateY, translateZ));
        float rotX = this.rotateX;
        float rotY = this.rotateY + (float) Math.toRadians(-facing.toYRot());
        float rotZ = this.rotateZ;
        long posLong = worldPosition.asLong();
        VoxelShape openShape = CollisionBoxUtil.cachedRotatedShape(posLong, baseCollision, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
        openShape = openShape.move(trans.x, trans.y, trans.z);
        if (isOpen || closeCollision == null) {
            return openShape;
        }
        VoxelShape closeShape = CollisionBoxUtil.cachedRotatedShape(posLong, closeCollision, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
        closeShape = closeShape.move(trans.x, trans.y, trans.z);
        return Shapes.or(openShape, closeShape);
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
