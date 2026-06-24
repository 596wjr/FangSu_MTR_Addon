package com.fangsu.render.scripting;

import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.math.Vector3f;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.reuse.DrawScheduler;
import com.fangsu.MainClient;
import com.fangsu.render.sowcerext.model.integration.BufferSourceProxy;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public abstract class AbstractDrawCalls {
    public static class ClusterDrawCall {
        public ModelCluster model;
        public DynamicModelHolder modelHolder;
        public Matrix4f pose;

        public ClusterDrawCall(ModelCluster model, Matrix4f pose) {
            this.model = model;
            this.pose = pose;
        }

        public ClusterDrawCall(DynamicModelHolder model, Matrix4f pose) {
            this.modelHolder = model;
            this.pose = pose;
        }

        public void commit(DrawScheduler drawScheduler, Matrix4f basePose, int light) {
            // pose 为 IDENTITY 时直接使用 basePose，避免每帧矩阵复制
            final Matrix4f finalPose;
            if (pose == Matrix4f.IDENTITY) {
                finalPose = basePose;
            } else {
                finalPose = basePose.copy();
                finalPose.multiply(pose);
            }
            if (model != null) {
                drawScheduler.enqueue(model, finalPose, light);
            } else {
                if (modelHolder != null) {
                    ModelCluster model = modelHolder.getUploadedModel();
                    if (model != null) {
                        drawScheduler.enqueue(model, finalPose, light);
                    }
                }
            }
        }

        public void commitDirect(BufferSourceProxy proxy, Matrix4f basePose, int light) {
            // pose 为 IDENTITY 时直接使用 basePose，避免每帧矩阵复制
            final Matrix4f finalPose;
            if (pose == Matrix4f.IDENTITY) {
                finalPose = basePose;
            } else {
                finalPose = basePose.copy();
                finalPose.multiply(pose);
            }
            if (model != null) {
                model.enqueueOpaqueBlaze(proxy, finalPose, light, MainClient.drawContext);
                model.enqueueTranslucentBlaze(proxy, finalPose, light, MainClient.drawContext);
            } else if (modelHolder != null) {
                ModelCluster m = modelHolder.getUploadedModel();
                if (m != null) {
                    m.enqueueOpaqueBlaze(proxy, finalPose, light, MainClient.drawContext);
                    m.enqueueTranslucentBlaze(proxy, finalPose, light, MainClient.drawContext);
                }
            }
        }
    }

    public static class PlaySoundCall {
        public SoundEvent sound;
        public Vector3f position;
        public float volume;
        public float pitch;

        public PlaySoundCall(SoundEvent sound, Vector3f position, float volume, float pitch) {
            this.sound = sound;
            this.position = position;
            this.volume = volume;
            this.pitch = pitch;
        }

        public void commit(ClientLevel level, Matrix4f worldPose) {
            Vector3f worldPos = worldPose.transform(position);
            level.playLocalSound(worldPos.x(), worldPos.y(), worldPos.z(),
                    sound, SoundSource.BLOCKS,
                    volume, pitch, false);
        }
    }
}
