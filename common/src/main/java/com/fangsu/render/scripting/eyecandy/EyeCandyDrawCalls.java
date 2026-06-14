package com.fangsu.render.scripting.eyecandy;

import com.fangsu.render.scripting.AbstractDrawCalls;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.math.Vector3f;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.reuse.DrawScheduler;
import com.fangsu.render.sowcerext.model.integration.BufferSourceProxy;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

public class EyeCandyDrawCalls extends AbstractDrawCalls {
    private final List<ClusterDrawCall> drawList = new ArrayList<>();
    private final List<PlaySoundCall> soundList = new ArrayList<>();

    public void addModel(ModelCluster model, Matrix4f pose) {
        drawList.add(new ClusterDrawCall(model, pose));
    }

    public void addModel(DynamicModelHolder model, Matrix4f pose) {
        drawList.add(new ClusterDrawCall(model, pose));
    }

    public void addSound(SoundEvent sound, float volume, float pitch) {
        soundList.add(new PlaySoundCall(sound, Vector3f.ZERO, volume, pitch));
    }

    public void renderDirect(MultiBufferSource multiBufferSource, Matrix4f basePose, int light) {
        BufferSourceProxy proxy = new BufferSourceProxy(multiBufferSource);
        for (ClusterDrawCall clusterDrawCall : drawList) {
            clusterDrawCall.commitDirect(proxy, basePose, light);
        }
        proxy.commit();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (PlaySoundCall playSoundCall : soundList) {
            Vector3f worldPos = basePose.transform(Vector3f.ZERO);
            level.playLocalSound(worldPos.x(), worldPos.y(), worldPos.z(),
                    playSoundCall.sound, SoundSource.BLOCKS,
                    playSoundCall.volume, playSoundCall.pitch, false);
        }
    }

    public void commit(DrawScheduler drawScheduler, Matrix4f basePose, int light) {
        for (ClusterDrawCall clusterDrawCall : drawList) {
//            Matrix4f finalPose = basePose.copy();
//            finalPose.multiply(clusterDrawCall.pose);
//            drawScheduler.enqueue(clusterDrawCall.model, finalPose, light);
            clusterDrawCall.commit(drawScheduler, basePose, light);
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (PlaySoundCall playSoundCall : soundList) {
            Vector3f worldPos = basePose.transform(Vector3f.ZERO);
            level.playLocalSound(worldPos.x(), worldPos.y(), worldPos.z(),
                    playSoundCall.sound, SoundSource.BLOCKS,
                    playSoundCall.volume, playSoundCall.pitch, false);
        }
    }

    public void reset() {
        drawList.clear();
        soundList.clear();
    }


}
