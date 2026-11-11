package com.fangsu.network;

import com.fangsu.blockEntities.BlockEntityAnnouncementSpeaker;
import com.fangsu.blockEntities.BlockEntityAnnouncementSpeaker.Binding;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.*;

/** 小型固定结构 C2S，校验写权限、距离、区块、条目数和 ID，不接受服务商参数。 */
public final class AnnouncementSpeakerPackets {
    private static final ResourceLocation CHANNEL = new ResourceLocation("fangsu", "announcement_speaker_config");
    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, CHANNEL, (buffer, context) -> {
            final BlockPos pos; final List<Binding> bindings = new ArrayList<>(); final String template, variant; final boolean allStops;final int leadSeconds;
            try {
                pos = buffer.readBlockPos(); int count = buffer.readVarInt(); if (count < 0 || count > 32) return;
                for (int i = 0; i < count; i++) bindings.add(new Binding(buffer.readLong(), buffer.readLong()));
                template = buffer.readUtf(256); variant = buffer.readUtf(64); allStops = buffer.readBoolean();
                leadSeconds=buffer.readableBytes()==0?BlockEntityAnnouncementSpeaker.DEFAULT_LEAD_SECONDS:buffer.readVarInt();
                if(leadSeconds<BlockEntityAnnouncementSpeaker.MIN_LEAD_SECONDS||leadSeconds>BlockEntityAnnouncementSpeaker.MAX_LEAD_SECONDS)return;
                if (buffer.readableBytes() != 0 || !template.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || template.contains("..") || !variant.matches("[a-z0-9_.-]{1,64}")) return;
            } catch (RuntimeException e) { return; }
            context.queue(() -> {
                if (!(context.getPlayer() instanceof ServerPlayer player) || !player.hasPermissions(2)
                        || player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64) return;
                //#if MC_VERSION >= 12000
                Level level = player.level();
                //#else
                //$$ Level level = player.level;
                //#endif
                if (!level.hasChunkAt(pos) || !level.mayInteract(player, pos)) return;
                if (level.getBlockEntity(pos) instanceof BlockEntityAnnouncementSpeaker speaker)
                    speaker.configure(bindings.stream().distinct().toList(), template, variant, allStops,leadSeconds);
            });
        });
    }
    public static void send(BlockPos pos, List<Binding> bindings, String template, String variant, boolean allStops,int leadSeconds) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer()); buffer.writeBlockPos(pos); buffer.writeVarInt(bindings.size());
        for (Binding binding : bindings) { buffer.writeLong(binding.routeId()); buffer.writeLong(binding.platformId()); }
        buffer.writeUtf(template, 256); buffer.writeUtf(variant, 64); buffer.writeBoolean(allStops);buffer.writeVarInt(leadSeconds);NetworkManager.sendToServer(CHANNEL, buffer);
    }
}
