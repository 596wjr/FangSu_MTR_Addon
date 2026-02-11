package com.fangsu.blockEntities;

import fabric.cn.zbx1425.mtrsteamloco.render.scripting.util.DynamicModelHolder;
import fabric.cn.zbx1425.mtrsteamloco.render.scripting.util.GraphicsTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Map;

import static com.fangsu.blocks.ModBlocks.BLOCK_ENTITY_DIAOBAN;

public class BlockEntitySign extends BaseObjBlockEntity implements Syncable, IPlatformDoor {
    private static final String DEFAULT_MAIN_MODEL = "fangsu:sign/beijing/beijing_sign.json";
    private static final String DEFAULT_SUB_MODEL = "beijing_sign_a";
    private static final String MAIN_MODEL_KEY = "sign";

    private boolean doorTarget = false;
    private float doorValue = 0f;

    private DynamicModelHolder dmhLeft, dmhCenter, dmhRight, dmhDlOn, dmhDlOff, dmhDisp;
    private GraphicsTexture gt;

    public BlockEntitySign(BlockPos pos, BlockState state) {
        super(BLOCK_ENTITY_DIAOBAN.get(), pos, state);
    }

    @Override
    public void whenLoading() {
        ensureExtraConfig("length", "2");
    }

    @Override
    public void whenRendering() {

    }

    @Override
    public void whenSaving(Map<String, String> extraConfigs) {

    }

    @Override
    public InteractionResult whenUseWithinBrush(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return null;
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
        doorTarget = target;
    }

    @Override
    public float getDoorValue() {
        return doorValue;
    }

    @Override
    public void setDoorValue(float value) {
        doorValue = value;
    }
}
