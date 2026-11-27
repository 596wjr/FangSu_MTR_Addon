package com.fangsu.render.sowcerext.model.animation;

import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcerext.model.animation.AnimationClip.Vector;

import java.util.List;
import java.util.Map;

public abstract class AnimatedModel {
    private final List<AnimatedModelNode> nodes;
    private final Map<String, AnimationClip> clips;

    protected AnimatedModel(List<AnimatedModelNode> nodes, Map<String, AnimationClip> clips) {
        this.nodes = List.copyOf(nodes);
        this.clips = Map.copyOf(clips);
    }

    public List<AnimatedModelNode> nodes() { return nodes; }
    public AnimationClip findClip(String name) { return clips.get(name); }

    public Matrix4f[] samplePose(AnimationClip clip, double seconds, AnimationClip.Wrap wrap) {
        double time = clip.time(seconds, wrap);
        Matrix4f[] transforms = new Matrix4f[nodes.size()];
        for (int i = 0; i < transforms.length; i++) {
            AnimatedModelNode node = nodes.get(i);
            AnimationClip.Channels channels = clip.tracks().get(i);
            Vector origin = node.origin();
            Vector rotation = node.rotation();
            Vector scale = Vector.ONE;
            if (channels != null) {
                origin = origin.add(channels.positionAt(time));
                rotation = rotation.add(channels.rotationAt(time));
                scale = channels.scaleAt(time);
            }
            Matrix4f matrix = node.parent() < 0 ? new Matrix4f() : transforms[node.parent()].copy();
            matrix.translate(origin.x(), origin.y(), origin.z());
            rotate(matrix, rotation);
            matrix.scale(scale.x(), scale.y(), scale.z());
            transforms[i] = matrix;
        }
        return transforms;
    }

    public static void rotate(Matrix4f matrix, Vector degrees) {
        matrix.rotateZ((float) Math.toRadians(degrees.z()));
        matrix.rotateY((float) Math.toRadians(degrees.y()));
        matrix.rotateX((float) Math.toRadians(degrees.x()));
    }
}
