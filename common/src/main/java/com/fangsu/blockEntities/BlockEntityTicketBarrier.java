package com.fangsu.blockEntities;

//#if FABRIC

import com.fangsu.extraConfig.*;
import com.google.gson.JsonPrimitive;
import fabric.cn.zbx1425.mtrsteamloco.render.scripting.util.DynamicModelHolder;
import fabric.cn.zbx1425.sowcer.math.Matrices;
//#elseif FORGE
//$$ import forge.cn.zbx1425.mtrsteamloco.render.scripting.util.DynamicModelHolder;
//$$ import forge.cn.zbx1425.sowcer.math.Matrices;
//#endif

import com.fangsu.Main;
import com.fangsu.utils.CustomItemHelper;
import com.fangsu.utils.ResourceUtil;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.utils.CollisionBoxUtil;
import com.fangsu.ticketSystem.*;

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

    private boolean cacheIsOpen = false;
    private long closeTime = 0;
    private long openTime = 0;
    private static final int closeAnimationBeginTime = 500;
    private static final int doorAnimationTime = 200;
    private boolean animationDone = true;
    private float gatePos = 0.5f;

    private Map<String, Map<String, Object>> loaded;
    private DynamicModelHolder mainDmh;
    private DoorsInfo subInfo;
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

        ObjBlockScriptContext ctx = this.scriptContext;
        BaseObjBlockEntity entity = this;
        ObjBlockProperty property = this.getProperty();

        String mainModel = CustomItemHelper.checkMainModel(entity, DEFAULT_MAIN_MODEL);
        String subModel = CustomItemHelper.checkSubModel(entity, "subModel", DEFAULT_SUB_MODEL);

        try {
            loaded = CustomItemHelper.optimizeCustomItemJSON(new ResourceLocation(entity.mainModel));
            if (!loaded.containsKey(subModel)) {
                entity.mainModel = DEFAULT_MAIN_MODEL;
                entity.subModels.put("subModel", DEFAULT_SUB_MODEL);
            }
            Map<String, Object> current = loaded.get(subModel);
            mainDmh = ResourceUtil.loadDmh(new ResourceLocation((String) current.get("model")), (Boolean) current.get("flipV"));
            if (current.get("doors") instanceof ArrayList<?> doors) {
                for (Object door : doors) {
                    if (door instanceof Map<?, ?> d) {
                        subInfo = new DoorsInfo(d);
                    }
                }
            }

            if (current.containsKey("shape") && current.get("shape") instanceof List<?> s) {
                shape = new CollisionBoxUtil.CollisionBox(s);
            }
            if (current.containsKey("collisionShape") && current.get("collisionShape") instanceof List<?> s) {
                collisionShape = new CollisionBoxUtil.CollisionBox(s);
            }
            if (current.containsKey("doorCloseShape") && current.get("doorCloseShape") instanceof List<?> s) {
                doorCloseShape = new CollisionBoxUtil.CollisionBox(s);
            }
            if (current.containsKey("doorCloseCollisionShape") && current.get("doorCloseCollisionShape") instanceof List<?> s) {
                doorCloseCollisionShape = new CollisionBoxUtil.CollisionBox(s);
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
            for (DoorsInfo.Door door : subInfo.doors) {
                Matrices mat = new Matrices();
                mat.translate(door.pos[0], door.pos[1], door.pos[2]);
                if (subInfo.doorType == 1) {
                    if (door.side == 0) mat.rotateZ((float) (door.step * doorAngle * Math.PI));
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
    public InteractionResult whenUseWithinBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ObjBlockScriptContext ctx = this.scriptContext;
        Map<String, String> extra = this.extraConfigs;

        Vec3 hitPos = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());

        boolean isOpen = getExtraConfigBool("isOpen", false);
        if (!isOpen) {
            //TODO 纸质客票系统
            if (getExtraConfigInt("fareType", 0) == 0) {
                if (!getExtraConfigBool("isExit", false)) {
                    //entrance
                    if (MtrTicketSystem.enter(level, pos, player)) {
                        extra.put("isOpen", "true");
                        sendUpdateC2S();
                        return InteractionResult.SUCCESS;
                    } else return InteractionResult.PASS;
                } else {
                    //exit
                    if (MtrTicketSystem.exit(level, pos, player)) {
                        extra.put("isOpen", "true");
                        sendUpdateC2S();
                        return InteractionResult.SUCCESS;
                    } else return InteractionResult.PASS;
                }
            } else if (getExtraConfigInt("fareType", 0) == 1) {
                //单次扣费
                MtrTicketSystem.addObjectivesIfMissing(level);
                Score balance = MtrTicketSystem.getScore(level, player, MtrTicketSystem.BALANCE_OBJECTIVE);
                int val = getExtraConfigInt("fareVal", 10);
                if (balance.getScore() < val) {
                    player.displayClientMessage(Text.translatable("gui.mtr.insufficient_balance", balance.getScore()), true);
                    return InteractionResult.PASS;
                } else {
                    balance.add(-val);
                    player.displayClientMessage(Text.translatable("msg.fangsu.ticketbarrier.fareOnce", val, balance.getScore()), true);
                    extra.put("isOpen", "true");
                    sendUpdateC2S();
                    return InteractionResult.SUCCESS;
                }
            } else if (getExtraConfigInt("fareType", 0) == 3) {
                //TODO 自定义计费模型
                player.displayClientMessage(Component.translatable("刷卡入闸"), true);
                extra.put("isOpen", "true");
                sendUpdateC2S();
                return InteractionResult.SUCCESS;
            }

        }

//        Main.LOGGER.info("isOpen : " + isOpen);
//        Main.LOGGER.info("cacheIsOpen : " + cacheIsOpen);

        return InteractionResult.PASS;
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
                Component.literal("模式"),
                new ConfigSpec("list"),
                List.of(
                        Component.literal("ui.fangsu.ticketbarrier.modeMtr"),
                        Component.literal("ui.fangsu.ticketbarrier.modeFareOnce"),
                        Component.literal("ui.fangsu.ticketbarrier.modeCustom")
                ),
                be -> getExtraConfigInt("fareType", 0),
                (be, v) -> extra.put("fareType", v.toString())
        ).setSaveOnChange(true));
        configs.add(new BoolConfig(
                Component.literal("ui.fangsu.ticketbarrier.isExit"),
                new ConfigSpec("bool"),
                be -> getExtraConfigBool("isExit", false),
                (be, v) -> extra.put("isExit", v.toString())
        ).setShowCondition(v -> 0 == getExtraConfigInt("fareType", 0)));
        configs.add(new NumberInputConfig(
                Component.literal("ui.fangsu.ticketbarrier.fareVal"),
                new ConfigSpec("number_input")
                        .setParam("max", new JsonPrimitive(32767))
                        .setParam("min", new JsonPrimitive(0))
                        .setParam("isInt", new JsonPrimitive(true)),
                be -> (float) getExtraConfigInt("fareVal", 10),
                (be, v) -> extra.put("fareVal", String.valueOf(v.intValue()))
        ).setShowCondition(v -> 1 == getExtraConfigInt("fareType", 0)));
        return configs;
    }

    private double clamp(double num, double min, double max) {
        return Math.min(Math.max(num, min), max);
    }

    private static class DoorsInfo {
        int doorType = 1;
        List<Door> doors = new ArrayList<>();

        private DoorsInfo(Map<?, ?> baseMap) throws Exception {
            // doorType
            Object dtObj = baseMap.get("doorType");
            if (dtObj instanceof Number num) {
                this.doorType = num.intValue();
            }

            boolean usePartedModel = Boolean.TRUE.equals(baseMap.get("use_parted_model"));

            if (usePartedModel) {
                Object modelObj = baseMap.get("model");
                if (!(modelObj instanceof String modelPath)) {
                    throw new IllegalArgumentException("model must be a String");
                }

                boolean flipV = Boolean.TRUE.equals(baseMap.get("flipV"));
                Map<String, DynamicModelHolder> dmhs = ResourceUtil.loadPartedDmh(new ResourceLocation(modelPath), flipV);

                Object posObj = baseMap.get("pos");
                if (posObj instanceof List<?> mapPos) {
                    for (Object posEntry : mapPos) {
                        if (!(posEntry instanceof Map<?, ?> thisMap)) continue;

                        // subModel
                        Object subModelObj = thisMap.get("subModel");
                        if (!(subModelObj instanceof String subModel)) continue;

                        // pos 数组
                        Object posListObj = thisMap.get("pos");
                        Double[] posArray = null;
                        if (posListObj instanceof List<?> posList) {
                            posArray = posList.stream()
                                    .map(o -> {
                                        if (o instanceof Number n) return n.doubleValue();
                                        else return 0.0; // 默认值，避免空指针
                                    })
                                    .toArray(Double[]::new);
                        }

                        // side
                        Object sideObj = thisMap.get("side");
                        Integer side = null;
                        if (sideObj instanceof Number n) {
                            side = n.intValue();
                        }

                        // step
                        Object stepObj = thisMap.get("step");
                        Integer step = null;
                        if (stepObj instanceof Number n) {
                            step = n.intValue();
                        }

                        // 只有 subModel 和 posArray 非空才添加
                        if (subModel != null && posArray != null && side != null) {
                            doors.add(new Door(dmhs.get(subModel), posArray, side, step));
                        }
                    }
                }
            }
        }

        private record Door(DynamicModelHolder dmh, Double[] pos, Integer side, Integer step) {
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
        VoxelShape openShape = CollisionBoxUtil.cachedRotatedShape(posLong, baseCollision, Vec3.ZERO, rotX, rotY, rotZ, 1);
        openShape = openShape.move(trans.x, trans.y, trans.z);
        if (isOpen || closeCollision == null) {
            return openShape;
        }
        VoxelShape closeShape = CollisionBoxUtil.cachedRotatedShape(posLong, closeCollision, Vec3.ZERO, rotX, rotY, rotZ, 1);
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
