package com.fangsu.render.scripting.eyecandy;

import com.fangsu.render.math.Matrix4f;
import com.fangsu.render.model.ModelCluster;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;

public class EyeCandyDrawCalls {
    private final List<Object> calls = new ArrayList<>();

    public void addModel(ModelCluster model, Matrix4f matrix4f) { calls.add(model); }
    public void addModel(DynamicModelHolder model, Matrix4f matrix4f) { calls.add(model); }
    public void addSound(SoundEvent soundEvent, float volume, float pitch) { calls.add(soundEvent); }
    public void reset() { calls.clear(); }
    public void commit(Object drawScheduler, Matrix4f candyPose, int light) { }
    public void commit(Object drawScheduler, Matrix4f candyPose, Matrix4f worldPose, int light) { }
}
