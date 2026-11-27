package com.fangsu.blockEntities;

import com.fangsu.blocks.ModBlocks;
import com.fangsu.train.data.TrainDataRuntime;
import com.fangsu.train.data.TrainDataSenderConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockEntityTrainDataSender extends BlockEntity {
    private TrainDataSenderConfig config = TrainDataSenderConfig.EMPTY;
    private ResourceLocation faceTexture;
    private boolean registered;

    public BlockEntityTrainDataSender(BlockPos pos, BlockState state) {
        super(ModBlocks.BLOCK_ENTITY_TRAIN_DATA_SENDER.get(), pos, state);
    }

    public TrainDataSenderConfig config() { return config; }

    public ResourceLocation faceTexture() { return faceTexture; }

    public void setConfig(TrainDataSenderConfig config) {
        this.config = config;
        faceTexture = config.texture().isEmpty() ? null : ResourceLocation.tryParse(config.texture());
        registered = false;
        registerSender();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    public void registerSender() {
        if (!registered && level instanceof ServerLevel serverLevel) {
            TrainDataRuntime.observe(serverLevel, worldPosition, config);
            registered = true;
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) TrainDataRuntime.forget(serverLevel, worldPosition);
        registered = false;
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("config", config.write());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("config")) config = TrainDataSenderConfig.read(tag.getCompound("config"));
        String texture = tag.contains("face_texture") ? tag.getString("face_texture") : config.texture();
        faceTexture = texture.isEmpty() ? null : ResourceLocation.tryParse(texture);
        registered = false;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("face_texture", config.texture());
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
