package com.fangsu.announcements.playback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/** 普通资源音效试听与服务端事件共用，不经过验票和计费代码。 */
public final class ResourceSoundPlayback extends AbstractSoundInstance {
    private ResourceSoundPlayback(String id, BlockPos pos, float volume, float pitch) { this(id,pos,volume,pitch,false); }
    private ResourceSoundPlayback(String id, BlockPos pos, float volume, float pitch, boolean onboard) {
        //#if MC_VERSION >= 11900
        super(new ResourceLocation(id), onboard ? SoundSource.VOICE : SoundSource.BLOCKS, net.minecraft.util.RandomSource.create());
        //#else
        //$$ super(new ResourceLocation(id), onboard ? SoundSource.VOICE : SoundSource.BLOCKS);
        //#endif
        if (onboard) { relative = true; attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE; }
        this.x = pos.getX()+.5; this.y = pos.getY()+.5; this.z = pos.getZ()+.5; this.volume = volume; this.pitch = pitch;
    }
    public static net.minecraft.client.resources.sounds.SoundInstance playOnboard(String id) {
        ResourceSoundPlayback sound = new ResourceSoundPlayback(id, BlockPos.ZERO, 1, 1, true);
        LegacyRecordingAudio.markNative(sound);
        Minecraft.getInstance().getSoundManager().play(sound); return sound;
    }
    public static void play(String id, BlockPos pos, float volume, float pitch) {
        if (id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) Minecraft.getInstance().getSoundManager().play(new ResourceSoundPlayback(id, pos, volume, pitch));
    }
}
