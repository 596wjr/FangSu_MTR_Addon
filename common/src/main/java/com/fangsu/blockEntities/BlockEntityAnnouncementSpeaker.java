package com.fangsu.blockEntities;

import com.fangsu.blocks.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** 世界只保存语义绑定，绝不保存云端连接、密钥或音频。 */
public final class BlockEntityAnnouncementSpeaker extends BlockEntity {
    public static final int DEFAULT_LEAD_SECONDS=30,MIN_LEAD_SECONDS=1,MAX_LEAD_SECONDS=180;
    public record Binding(long routeId, long platformId) { }
    private boolean allStops;
    private int leadSeconds=DEFAULT_LEAD_SECONDS;
    public int leadSeconds(){return leadSeconds;}
    public boolean allStops() { return allStops; }
    private List<Binding> bindings = List.of();
    private String template = "fangsu:platform_arrival_a", variant = "standard";
    public BlockEntityAnnouncementSpeaker(BlockPos pos, BlockState state) { super(ModBlocks.BLOCK_ENTITY_ANNOUNCEMENT_SPEAKER.get(), pos, state); }
    @Override public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (level.isClientSide) com.fangsu.announcements.playback.StationAnnouncementCoordinator.track(this);
    }
    public List<Binding> bindings() { return bindings; }
    public String template() { return template; }
    public String variant() { return variant; }
    public void configure(List<Binding> bindings, String template, String variant, boolean allStops,int leadSeconds) {
        this.leadSeconds=Math.max(MIN_LEAD_SECONDS,Math.min(MAX_LEAD_SECONDS,leadSeconds));
        this.allStops = allStops; this.bindings = List.copyOf(bindings); this.template = template; this.variant = variant;
        setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.putBoolean("allStops", allStops); tag.putInt("schemaVersion", 1); tag.putString("template", template); tag.putString("variant", variant);
        tag.putInt("leadSeconds",leadSeconds);
        ListTag list = new ListTag(); for (Binding binding : bindings) { CompoundTag b = new CompoundTag(); b.putLong("route", binding.routeId()); b.putLong("platform", binding.platformId()); list.add(b); } tag.put("bindings", list);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.getInt("schemaVersion") != 1) return;
        allStops = tag.getBoolean("allStops");
        leadSeconds=tag.contains("leadSeconds",Tag.TAG_INT)?Math.max(MIN_LEAD_SECONDS,Math.min(MAX_LEAD_SECONDS,tag.getInt("leadSeconds"))):DEFAULT_LEAD_SECONDS;
        List<Binding> loaded = new ArrayList<>(); ListTag list = tag.getList("bindings", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(32, list.size()); i++) { CompoundTag b = list.getCompound(i); loaded.add(new Binding(b.getLong("route"), b.getLong("platform"))); }
        bindings = List.copyOf(loaded);
        String id = tag.getString("template"), voice = tag.getString("variant");
        if (id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") && id.length() <= 256) template = id;
        if (voice.matches("[a-z0-9_.-]{1,64}")) variant = voice;
    }
    @Override public CompoundTag getUpdateTag() { CompoundTag tag = new CompoundTag(); saveAdditional(tag); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
