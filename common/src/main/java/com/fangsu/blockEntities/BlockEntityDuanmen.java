package com.fangsu.blockEntities;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.Main;
import com.fangsu.blocks.BaseObjBlock;
import com.fangsu.customItem.SubModelDispInfo;
import com.fangsu.customItem.contents.DuanmenContent;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrices;
import com.fangsu.utils.CollisionBoxUtil;
import com.fangsu.utils.ContentInfoUtil;
import com.fangsu.utils.CustomItemHelper;
import com.fangsu.utils.ResourceUtil;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_DUANMEN;

public class BlockEntityDuanmen extends FunctionalObjBlockEntity {
    public static final String DEFAULT_MAIN_MODEL = "fangsu:duanmen/kaba_duanmen.json";
    public static final String DEFAULT_SUB_MODEL = "kaba_a_right";
    public static final String MAIN_MODEL_KEY = "duanmen";

    private static final long ANIMATION_DURATION = 500;

    private long closeTime = 0;
    private long openTime = 0;

    private DynamicModelHolder dmhMain;
    private DynamicModelHolder dmhDoor;
    private DynamicModelHolder dmhOpen;
    private DynamicModelHolder dmhDlOn;
    private DynamicModelHolder dmhDlOff;

    private CollisionBoxUtil.CollisionBox shapeClose, shapeOpen;

    private DuanmenContent content;
    private double[] doorPos;
    private double hitPoint;

    public BlockEntityDuanmen(BlockPos blockPos, BlockState blockState) {
        super(BLOCK_ENTITY_DUANMEN.get(), blockPos, blockState);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("isOpen", "false");

        String mainModel = CustomItemHelper.checkMainModel(this, DEFAULT_MAIN_MODEL);
        String subModel = CustomItemHelper.checkSubModel(this, "subModel", DEFAULT_SUB_MODEL);

        try {
            content = ContentInfoUtil.getDuanmenContent(mainModel, subModel);
            if (content == null) {
                Main.LOGGER.error("Duanmen content is null");
                markedError = true;
                return;
            }
            Map<String, DynamicModelHolder> dmhMap = ResourceUtil.loadPartedDmh(new ResourceLocation(content.getModel()), content.isFilpV());
            Map<String, String> models = content.getSubModels();
            if (models != null) {
                if (models.containsKey("main")) {
                    dmhMain = dmhMap.get(models.get("main"));
                } else {
                    dmhMain = null;
                }
                if (models.containsKey("door")) {
                    dmhDoor = dmhMap.get(models.get("door"));
                } else dmhDoor = null;
                if (models.containsKey("open")) {
                    dmhOpen = dmhMap.get(models.get("open"));
                } else dmhOpen = null;
            }
            if (content.getDoorPos() == null || content.getDoorPos().length < 2) doorPos = new double[]{0, 0, 0};
            else doorPos = new double[]{content.getDoorPos()[0], content.getDoorPos()[1], content.getDoorPos()[2]};
            if (content.getShape() != null) {
                if (content.getShape().containsKey("open")) {
                    List<List<Integer>> shape = content.getShape().get("open");
                    shapeOpen = new CollisionBoxUtil.CollisionBox(shape);
                }
                if (content.getShape().containsKey("close")) {
                    List<List<Integer>> shape = content.getShape().get("close");
                    shapeClose = new CollisionBoxUtil.CollisionBox(shape);
                }
            }
            hitPoint = content.getHitPos();

        } catch (Exception e) {
            Main.LOGGER.error("Duanmen content load error", e);
            markedError = true;
        }
    }

    @Override
    public void whenRendering() {
        ObjBlockScriptContext ctx = this.scriptContext;


        if (dmhMain != null)
            ctx.drawModel(dmhMain, null);

        boolean isOpen = getExtraConfigBool("isOpen", false);
        long currentTime = System.currentTimeMillis();
        long animationBeginTime;
        if (isOpen) {
            openTime = currentTime;
            animationBeginTime = closeTime;
        } else {
            closeTime = currentTime;
            animationBeginTime = openTime;
        }
        float doorVal = isOpen ?
                (currentTime - animationBeginTime) / (ANIMATION_DURATION * 1f) :
                1f - (currentTime - animationBeginTime) / (ANIMATION_DURATION * 1f);
        if (doorVal > 1f) doorVal = 1f;
        if (doorVal < 0f) doorVal = 0f;
        Matrices mat = new Matrices();
        if (doorPos != null) {
            mat.translate(doorPos[0], doorPos[1], doorPos[2]);
            mat.rotateY((float) (0.5 * content.getDoorAngle() * doorVal * Math.PI));
            mat.translate(-1f * doorPos[0], -1f * doorPos[1], -1f * doorPos[2]);
        }
        if (dmhDoor != null) ctx.drawModel(dmhDoor, mat);
        if (dmhOpen != null && isOpen) ctx.drawModel(dmhOpen, null);

    }

    @Override
    public List<SubModelDispInfo> getSubModelInfos() {
        List<SubModelDispInfo> infos = new ArrayList<>();
        infos.add(createSubModelSelectInfo("content", DEFAULT_SUB_MODEL));
        return infos;
    }

    @Override
    public InteractionResult whenUseWithBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (getExtraConfigBool("isOpen", false))
            setExtraConfig("isOpen", "false");
        else setExtraConfig("isOpen", "true");
        sendUpdateC2S();
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult whenUseWithOther(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) return InteractionResult.SUCCESS;

        Vec3 hitPos = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());

        Direction facing = level.getBlockState(pos)
                .getValue(BaseObjBlock.FACING);

        if (getExtraConfigBool("isOpen", false)) {
            setExtraConfig("isOpen", "false");
            sendUpdateC2S();
            return InteractionResult.SUCCESS;
        } else {
            if ((facing == Direction.NORTH && hitPos.z < hitPoint) ||     //z-
                    (facing == Direction.SOUTH && hitPos.z > (1 - hitPoint)) || //z+
                    (facing == Direction.WEST && hitPos.x < hitPoint) ||  //x-
                    (facing == Direction.EAST && hitPos.x > (1 - hitPoint))     //x+
            ) {
                setExtraConfig("isOpen", "true");
//                player.displayClientMessage(Component.literal("direction = " + facing + " x = " + hitPos.x + " z = " + hitPos.z + " hitPoint = " + hitPoint), true);
                sendUpdateC2S();
                return InteractionResult.SUCCESS;
            } else {
                player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.duanmen.requiresKey"), true);
//                player.displayClientMessage(Component.literal("direction = " + facing + " x = " + hitPos.x + " z = " + hitPos.z + " hitPoint = " + hitPoint), true);
                return InteractionResult.PASS;
            }
        }
    }

    @Override
    public VoxelShape setShape(BlockState state) {
        if (markedError) return Shapes.block();
        boolean isOpen = getExtraConfigBool("isOpen", false);

        Direction facing = state.getValue(BaseObjBlock.FACING);
        Vec3 trans = transformOffset(facing, new Vec3(translateX, translateY, translateZ));
        float rotX = this.rotateX;
        float rotY = this.rotateY + (float) Math.toRadians(-facing.toYRot());
        float rotZ = this.rotateZ;
        long posLong = worldPosition.asLong();
        if (isOpen) {
            if (shapeOpen != null) {
                return CollisionBoxUtil.cachedRotatedShape(posLong, shapeOpen, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
            }
        } else {
            if (shapeClose != null) {
                return CollisionBoxUtil.cachedRotatedShape(posLong, shapeClose, Vec3.ZERO, rotX, rotY, rotZ, 0.1f);
            }
        }
        return Shapes.block();
    }

    @Override
    public VoxelShape setCollisionShape(BlockState state) {
        if (markedError) return Shapes.empty();
        if (shapeOpen == null || shapeClose == null) return Shapes.empty();
        return setShape(state);
    }

    @Override
    public String getMainModelKey() {
        return MAIN_MODEL_KEY;
    }
}
