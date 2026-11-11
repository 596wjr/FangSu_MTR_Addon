package com.fangsu.announcements.playback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import java.util.*;

/** 接管时停止已开始的旧包声音，避免仅拦截新声音仍有数秒重叠。 */
public final class LegacyRecordingAudio {
    private static final Set<SoundInstance> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<SoundInstance> NATIVE = Collections.newSetFromMap(new WeakHashMap<>());
    public static synchronized void markNative(SoundInstance sound) { NATIVE.add(sound); }
    public static synchronized boolean isNative(SoundInstance sound) { return NATIVE.contains(sound); }
    public static synchronized void track(SoundInstance sound) { ACTIVE.removeIf(value -> !Minecraft.getInstance().getSoundManager().isActive(value)); if (ACTIVE.size() < 128) ACTIVE.add(sound); }
    public static synchronized void stop() { for (var sound : List.copyOf(ACTIVE)) Minecraft.getInstance().getSoundManager().stop(sound); ACTIVE.clear(); }
}
