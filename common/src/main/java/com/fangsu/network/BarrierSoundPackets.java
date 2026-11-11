package com.fangsu.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.Map;

/** 验票完成后服务端广播一次音效事件；副门翼不再重复验票声。 */
public final class BarrierSoundPackets {
    private static final ResourceLocation CHANNEL = new ResourceLocation("fangsu", "barrier_sound");
    public static void emit(Level level, BlockPos pos, Map<String, String> config, String event) {
        String id = config.getOrDefault(event + "Sound", "");
        if (level.isClientSide || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length() > 256) return;
        float volume = number(config, "soundVolume", 1, 0, 2), pitch = number(config, "soundPitch", 1, .5f, 2);
        for (var player : level.players()) if (player instanceof ServerPlayer serverPlayer && player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5) <= 1024) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer()); buffer.writeBlockPos(pos); buffer.writeUtf(id, 256); buffer.writeFloat(volume); buffer.writeFloat(pitch);
            NetworkManager.sendToPlayer(serverPlayer, CHANNEL, buffer);
        }
    }
    private static float number(Map<String,String> config, String key, float fallback, float min, float max) {
        try { float value = Float.parseFloat(config.getOrDefault(key, String.valueOf(fallback))); return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback; }
        catch (NumberFormatException e) { return fallback; }
    }
    public static void registerClient() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CHANNEL, (buffer, context) -> {
            BlockPos pos = buffer.readBlockPos(); String id = buffer.readUtf(256); float volume = buffer.readFloat(), pitch = buffer.readFloat();
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !Float.isFinite(volume) || volume < 0 || volume > 2 || !Float.isFinite(pitch) || pitch < .5 || pitch > 2) return;
            context.queue(() -> com.fangsu.announcements.playback.ResourceSoundPlayback.play(id, pos, volume, pitch));
        });
    }
}
