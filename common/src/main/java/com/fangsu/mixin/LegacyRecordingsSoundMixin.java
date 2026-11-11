package com.fangsu.mixin;

import com.fangsu.announcements.playback.OnboardAnnouncementCoordinator;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 原生车内会话接管后屏蔽旧 recordings 命名空间，避免过渡包仍启用时双播。 */
@Mixin(SoundManager.class)
public class LegacyRecordingsSoundMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void fangsu$nativeOwnsRecordings(SoundInstance sound, CallbackInfo ci) {
        if (sound.getLocation().getNamespace().equals("recordings")) {
            if (com.fangsu.announcements.playback.LegacyRecordingAudio.isNative(sound)) return;
            if (OnboardAnnouncementCoordinator.ownsSession()) ci.cancel();
            else com.fangsu.announcements.playback.LegacyRecordingAudio.track(sound);
        }
    }
}
