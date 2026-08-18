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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class EyeCandyDrawCalls extends AbstractDrawCalls {
    private final List<ClusterDrawCall> drawList = new ArrayList<>();
    private final List<PlaySoundCall> soundList = new ArrayList<>();

    // 对象池：复用每帧都会新建的 ClusterDrawCall 与 Matrix4f，减少大量 OBJ 方块时的 GC 压力。
    private final ArrayDeque<ClusterDrawCall> callPool = new ArrayDeque<>();
    private final ArrayDeque<Matrix4f> matrixPool = new ArrayDeque<>();

    public void addModel(ModelCluster model, Matrix4f pose) {
        drawList.add(obtainCall().set(model, null, obtainPose(pose)));
    }

    public void addModel(DynamicModelHolder model, Matrix4f pose) {
        drawList.add(obtainCall().set(null, model, obtainPose(pose)));
    }

    public void addSound(SoundEvent sound, float volume, float pitch) {
        soundList.add(new PlaySoundCall(sound, Vector3f.ZERO, volume, pitch));
    }

    /** 从池中取一个可复用的 ClusterDrawCall，池空时新建。 */
    private ClusterDrawCall obtainCall() {
        ClusterDrawCall c = callPool.poll();
        return c != null ? c : new ClusterDrawCall((ModelCluster) null, Matrix4f.IDENTITY);
    }

    /** 把 pose 值拷贝进池中的 Matrix4f 并返回，池空时新建；IDENTITY 常量直接复用不拷贝。 */
    private Matrix4f obtainPose(Matrix4f pose) {
        if (pose == Matrix4f.IDENTITY) return Matrix4f.IDENTITY;
        Matrix4f m = matrixPool.poll();
        if (m == null) m = new Matrix4f();
        m.set(pose);
        return m;
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
        for (ClusterDrawCall c : drawList) {
            if (c.pose != Matrix4f.IDENTITY) matrixPool.addLast(c.pose);
            callPool.addLast(c);
        }
        drawList.clear();
        soundList.clear();
    }


}
