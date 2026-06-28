package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;

import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.contents.TicketBarrierContent;
import com.fangsu.Main;
import com.fangsu.utils.*;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.ticketSystem.*;
import com.fangsu.extraConfig.*;

import com.google.gson.JsonPrimitive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_TICKET_BARRIER;

public class BlockEntityTicketBarrier extends FunctionalObjBlockEntity {
    public static final String DEFAULT_MAIN_MODEL = "fangsu:ticketbarrier/mtr_ticketbarrier.json";
    public static final String DEFAULT_SUB_MODEL = "mtr_ticketbarrier_1";
    public static final String MAIN_MODEL_KEY = "ticketBarrier";

    TicketBarrierContent content;

    private boolean cacheIsOpen = false;
    private long closeTime = 0;
    private long openTime = 0;
    private static final int closeAnimationBeginTime = 500;
    private static final int doorAnimationTime = 200;
    private boolean animationDone = true;
    private float gatePos = 0.5f;

    private DynamicModelHolder mainDmh;
    private TicketBarrierDoorRenderInfo subInfo;
    private CollisionBoxUtil.CollisionBox shape, collisionShape, doorCloseShape, doorCloseCollisionShape;
    private String shapeSerialized = "";
    private String collisionShapeSerialized = "";
    private String doorCloseShapeSerialized = "";
    private String doorCloseCollisionShapeSerialized = "";
//    private AABB ticketBox, cardBox;

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

        String mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        String subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);

        try {
            content = ContentInfoUtil.getTicketBarrierContent(mainModel, subModel);
            if (content == null) {
                markedError = true;
                return;
            }
            mainDmh = ResourceUtil.loadDmh(new ResourceLocation(content.getModel()), content.getFilpV());
            for (TicketBarrierContent.TicketBarrierDoorInfo doorInfo : content.getDoors()) {
                subInfo = new TicketBarrierDoorRenderInfo(doorInfo);
            }

            if (!content.getShape().isEmpty()) {
                shape = new CollisionBoxUtil.CollisionBox(content.getShape());
            }
            if (!content.getCollisionShape().isEmpty()) {
                collisionShape = new CollisionBoxUtil.CollisionBox(content.getCollisionShape());
            } else {
                collisionShape = new CollisionBoxUtil.CollisionBox(List.of(List.of(-1, 0, 0, 1, 24, 16), List.of(15, 0, 0, 17, 24, 16)));
            }
            if (!content.getDoorCloseShape().isEmpty()) {
                doorCloseShape = new CollisionBoxUtil.CollisionBox(content.getDoorCloseShape());
            }
            if (!content.getDoorCloseCollisionShape().isEmpty()) {
                doorCloseCollisionShape = new CollisionBoxUtil.CollisionBox(content.getDoorCloseCollisionShape());
            } else {
                doorCloseCollisionShape = new CollisionBoxUtil.CollisionBox(List.of(List.of(1, 0, 12, 15, 24, 15)));
            }
            shapeSerialized = ShapeSerializer.serialize(content.getShape());
            collisionShapeSerialized = ShapeSerializer.serialize(content.getCollisionShape());
            doorCloseShapeSerialized = ShapeSerializer.serialize(content.getDoorCloseShape());
            doorCloseCollisionShapeSerialized = ShapeSerializer.serialize(content.getDoorCloseCollisionShape());

            gatePos = content.getGatePos() != null ? content.getGatePos().floatValue() : 0.5f;

//            cardBox = parseBox(content.getCardBox());
//            ticketBox = parseBox(content.getTicketBox());
        } catch (Exception e) {
            Main.LOGGER.warn(e.getMessage());
            markedError = true;
        }

    }

    @Override
    public void whenRendering() {
        ObjBlockScriptContext ctx = this.scriptContext;
        boolean isOpen = getExtraConfigBool("isOpen", false);
        long currentTime = System.currentTimeMillis();
        if (isOpen != cacheIsOpen) {
            cacheIsOpen = isOpen;
            if (!isOpen) {
                closeTime = currentTime;
            } else {
                openTime = currentTime;
            }
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
    public InteractionResult whenUseWithOther(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        Map<String, String> extra = this.extraConfigs;

        Vec3 hitPos = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());

        Direction facing = level.getBlockState(pos)
                .getValue(BaseObjBlock.FACING);

        if ((facing == Direction.NORTH && hitPos.z > 0.5) ||
                (facing == Direction.SOUTH && hitPos.z < 0.5) ||
                (facing == Direction.WEST && hitPos.x > 0.5) ||
                (facing == Direction.EAST && hitPos.x < 0.5)
        ) {
            boolean success = TicketBarrierHandler.handle(
                    level,
                    pos,
                    player,
                    hand,
                    hit,
                    extra,
                    this::sendUpdateC2S
            );
            if (success) {
                checkSideOpen(level, pos);
                return InteractionResult.SUCCESS;
            } else return InteractionResult.PASS;
        } else {
            player.displayClientMessage(ComponentHelper.translatable("mst.fangsu.ticketbarrier.wrongDirection"), true);
            return InteractionResult.PASS;
        }
    }

    private void checkSideOpen(Level level, BlockPos pos) {
        if (content == null) return;
        TicketBarrierContent.TicketBarrierConnectType connectType = content.getConnectType();
        BlockEntity sideBlockEntity;
        switch (connectType) {
            case LEFT -> sideBlockEntity = FacingBlockUtil.getRightBlockEntity(level, pos, getBlockState());
            case RIGHT -> sideBlockEntity = FacingBlockUtil.getLeftBlockEntity(level, pos, getBlockState());
            default -> sideBlockEntity = null;
        }
        if (sideBlockEntity instanceof BlockEntityTicketBarrier barrier) {
            barrier.requestOpen(connectType);
        }
    }

    private void checkSideClose(Level level, BlockPos pos) {
        if (content == null) return;
        TicketBarrierContent.TicketBarrierConnectType connectType = content.getConnectType();
        BlockEntity sideBlockEntity;
        switch (connectType) {
            case LEFT -> sideBlockEntity = FacingBlockUtil.getRightBlockEntity(level, pos, getBlockState());
            case RIGHT -> sideBlockEntity = FacingBlockUtil.getLeftBlockEntity(level, pos, getBlockState());
            default -> sideBlockEntity = null;
        }
        if (sideBlockEntity instanceof BlockEntityTicketBarrier barrier) {
            barrier.requestClose(connectType);
        }
    }

    private void requestOpen(TicketBarrierContent.TicketBarrierConnectType source) {
        if (content == null) return;
        TicketBarrierContent.TicketBarrierConnectType connectType = content.getConnectType();
        if ((connectType == TicketBarrierContent.TicketBarrierConnectType.LEFT && source == TicketBarrierContent.TicketBarrierConnectType.RIGHT) ||
                (connectType == TicketBarrierContent.TicketBarrierConnectType.RIGHT && source == TicketBarrierContent.TicketBarrierConnectType.LEFT)) {
            extraConfigs.put("isOpen", "true");
            sendUpdateC2S();
        }
    }

    private void requestClose(TicketBarrierContent.TicketBarrierConnectType source) {
        if (content == null) return;
        TicketBarrierContent.TicketBarrierConnectType connectType = content.getConnectType();
        if ((connectType == TicketBarrierContent.TicketBarrierConnectType.LEFT && source == TicketBarrierContent.TicketBarrierConnectType.RIGHT) ||
                (connectType == TicketBarrierContent.TicketBarrierConnectType.RIGHT && source == TicketBarrierContent.TicketBarrierConnectType.LEFT)) {
            extraConfigs.put("isOpen", "false");
            sendUpdateC2S();
        }
    }

    @Override
    public void whenEntityInside(Player player) {

        boolean isOpen = getExtraConfigBool("isOpen", false);
        if (isOpen) {
            if (worldToLocal(player.position()).z > gatePos) {
                extraConfigs.put("isOpen", "false");
                Level level = getLevel();
                BlockPos pos = getBlockPos();
                checkSideClose(level, pos);
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
                ComponentHelper.translatable("ui.fangsu.ticketbarrier.mode"),
                new ConfigSpec("list"),
                List.of(
                        ComponentHelper.translatable("ui.fangsu.ticketbarrier.modeMtr"),
                        ComponentHelper.translatable("ui.fangsu.ticketbarrier.modeFareOnce")
                ),
                () -> getExtraConfigInt("fareType", 0),
                (v) -> extra.put("fareType", v.toString())
        ).setSaveOnChange(true));
        configs.add(new BoolConfig(
                ComponentHelper.translatable("ui.fangsu.ticketbarrier.isExit"),
                new ConfigSpec("bool"),
                () -> getExtraConfigBool("isExit", false),
                (v) -> extra.put("isExit", v.toString())
        ).setShowCondition(v -> {
            int fareType = getExtraConfigInt("fareType", 0);
            return fareType == 0;
        }));
        configs.add(new BoolConfig(
                ComponentHelper.translatable("ui.fangsu.ticketbarrier.useCustomZone"),
                new ConfigSpec("bool"),
                () -> getExtraConfigBool("useCustomZone", false),
                (v) -> extra.put("useCustomZone", v.toString())
        ).setSaveOnChange(true).setShowCondition(v -> 0 == getExtraConfigInt("fareType", 0)));
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.ticketbarrier.fareVal"),
                new ConfigSpec("number_input")
                        .setParam("max", new JsonPrimitive(32767))
                        .setParam("min", new JsonPrimitive(0))
                        .setParam("isInt", new JsonPrimitive(true)),
                () -> (float) getExtraConfigInt("fareVal", 10),
                (v) -> extra.put("fareVal", String.valueOf(v.intValue()))
        ).setShowCondition(v -> 1 == getExtraConfigInt("fareType", 0)));
        configs.add(new NumberInputConfig(
                ComponentHelper.translatable("ui.fangsu.ticketbarrier.customZone"),
                new ConfigSpec("number_input")
                        .setParam("max", new JsonPrimitive(32767))
                        .setParam("min", new JsonPrimitive(-32768))
                        .setParam("isInt", new JsonPrimitive(true)),
                () -> (float) getExtraConfigInt("customZone", 0),
                (v) -> extra.put("customZone", String.valueOf(v.intValue()))
        ).setShowCondition(v -> 0 == getExtraConfigInt("fareType", 0) && getExtraConfigBool("useCustomZone", false)));
        configs.add(new StringConfig(
                ComponentHelper.translatable("ui.fangsu.ticketbarrier.customDisplayName"),
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
        infos.add(createSubModelSelectInfo("content", DEFAULT_SUB_MODEL));
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
                if (dmh == null && dmhs.size() == 1) {
                    dmh = dmhs.values().iterator().next();
                }
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
        if (!shapeSerialized.isEmpty()) {
            VoxelShape openShape = resolveSerializedShape(shapeSerialized, state);
            if (isOpen || doorCloseShapeSerialized.isEmpty()) {
                return openShape;
            }
            VoxelShape closeShape = resolveSerializedShape(doorCloseShapeSerialized, state);
            return Shapes.or(openShape, closeShape);
        }
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
        if (!shapeSerialized.isEmpty() || !collisionShapeSerialized.isEmpty()) {
            String baseShape = !collisionShapeSerialized.isEmpty() ? collisionShapeSerialized : shapeSerialized;
            if (baseShape.isEmpty()) return null;
            VoxelShape openShape = resolveSerializedShape(baseShape, state);
            String closeShapeRaw = !doorCloseCollisionShapeSerialized.isEmpty() ? doorCloseCollisionShapeSerialized : doorCloseShapeSerialized;
            if (isOpen || closeShapeRaw.isEmpty()) {
                return openShape;
            }
            VoxelShape closeShape = resolveSerializedShape(closeShapeRaw, state);
            return Shapes.or(openShape, closeShape);
        }
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

    private VoxelShape resolveSerializedShape(String serialized, BlockState state) {
        try {
            Direction facing = state.getValue(BaseObjBlock.FACING);
            int yRot = Math.floorMod((int) facing.toYRot(), 360);
            Vec3 trans = transformOffset(facing, new Vec3(translateX, translateY, translateZ));
            VoxelShape shape = ShapeSerializer.getShape(serialized, yRot);

            if (rotateX != 0 || rotateY != 0 || rotateZ != 0) {
                VoxelShape rotated = Shapes.empty();
                long posLong = worldPosition.asLong();
                for (AABB box : shape.toAabbs()) {
                    CollisionBoxUtil.CollisionBox collisionBox = new CollisionBoxUtil.CollisionBox(box.minX * 16, box.minY * 16, box.minZ * 16, box.maxX * 16, box.maxY * 16, box.maxZ * 16);
                    VoxelShape part = CollisionBoxUtil.cachedRotatedShape(posLong, collisionBox, Vec3.ZERO, rotateX, rotateY, rotateZ, 0.1f);
                    rotated = Shapes.or(rotated, part);
                }
                shape = rotated.optimize();
            }

            return shape.move(trans.x, trans.y, trans.z);
        } catch (Exception e) {
            return Shapes.empty();
        }
    }
}
