package com.fangsu.mixin;

import com.fangsu.announcements.playback.CachedAudioPlayback;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

/** 只接管内部生成的缓存声音；.ogg 是游戏资源寻址约定，数据按 PCM WAV 读取。 */
@Mixin(SoundBufferLibrary.class)
public class AnnouncementSoundStreamMixin {
    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void fangsu$stream(ResourceLocation id, boolean loop, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (!id.getNamespace().equals("fangsu") || !id.getPath().startsWith("sounds/announcement_cache/")) return;
        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            try {
                AudioStream stream = CachedAudioPlayback.open(id);
                if (stream == null) throw new java.io.IOException("expired_audio_handle");
                return stream;
            } catch (java.io.IOException e) { throw new java.util.concurrent.CompletionException(e); }
        }));
    }
}
