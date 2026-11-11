package com.fangsu.blocks;

import com.fangsu.blockEntities.BlockEntityAnnouncementSpeaker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 站内广播专用喇叭；普通方块模型，无逐帧模型脚本。 */
public final class BlockAnnouncementSpeaker extends Block implements EntityBlock {
    private static final VoxelShape SPEAKER_SHAPE = Shapes.or(
            Block.box(4, 15.5, 4, 12, 16, 12),
            Block.box(4.5, 15.25, 4.5, 11.5, 15.5, 11.5)
    );

    public BlockAnnouncementSpeaker() {
        super(BlockBehaviour.Properties.of(
                //#if MC_VERSION >= 12000
                )
                //#else
                //$$ net.minecraft.world.level.material.Material.METAL)
                //#endif
                .strength(2).noOcclusion());
    }
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SPEAKER_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SPEAKER_SHAPE;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BlockEntityAnnouncementSpeaker(pos, state); }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        var held = player.getItemInHand(hand);
        if (!held.is(com.fangsu.items.ModItems.ITEM_WRENCH.get()) && !held.is(mtr.Items.BRUSH.get())) return InteractionResult.PASS;
        if (level.isClientSide && level.getBlockEntity(pos) instanceof BlockEntityAnnouncementSpeaker speaker)
            com.fangsu.announcements.ui.SpeakerConfigScreen.open(speaker);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
