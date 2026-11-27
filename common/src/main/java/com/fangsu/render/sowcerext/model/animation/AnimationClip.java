package com.fangsu.render.sowcerext.model.animation;

import java.util.List;
import java.util.Map;

public record AnimationClip(String name, double length, boolean loop, Map<Integer, Channels> tracks) {
    public AnimationClip {
        tracks = Map.copyOf(tracks);
        if (!Double.isFinite(length) || length < 0) throw new IllegalArgumentException("Invalid clip length");
    }

    public enum Wrap { LOOP, CLAMP }

    public double time(double seconds, Wrap wrap) {
        if (!Double.isFinite(seconds) || length == 0) return 0;
        if (wrap == Wrap.CLAMP) return Math.max(0, Math.min(length, seconds));
        double remainder = seconds % length;
        return remainder < 0 ? remainder + length : remainder;
    }

    public record Vector(float x, float y, float z) {
        public static final Vector ZERO = new Vector(0, 0, 0);
        public static final Vector ONE = new Vector(1, 1, 1);

        public Vector add(Vector other) {
            return new Vector(x + other.x, y + other.y, z + other.z);
        }

        public Vector subtract(Vector other) {
            return new Vector(x - other.x, y - other.y, z - other.z);
        }

        public Vector multiply(float factor) {
            return new Vector(x * factor, y * factor, z * factor);
        }
    }

    public record Keyframe(double time, Vector value) {}

    public record Channels(List<Keyframe> position, List<Keyframe> rotation, List<Keyframe> scale) {
        public Channels {
            position = List.copyOf(position);
            rotation = List.copyOf(rotation);
            scale = List.copyOf(scale);
        }

        public Vector positionAt(double time) { return sample(position, time, Vector.ZERO); }
        public Vector rotationAt(double time) { return sample(rotation, time, Vector.ZERO); }
        public Vector scaleAt(double time) { return sample(scale, time, Vector.ONE); }
    }

    private static Vector sample(List<Keyframe> frames, double time, Vector fallback) {
        if (frames.isEmpty()) return fallback;
        if (time <= frames.get(0).time) return frames.get(0).value;
        int last = frames.size() - 1;
        if (time >= frames.get(last).time) return frames.get(last).value;
        int first = 0;
        while (last - first > 1) {
            int middle = (last + first) >>> 1;
            if (frames.get(middle).time <= time) first = middle;
            else last = middle;
        }
        Keyframe start = frames.get(first);
        Keyframe end = frames.get(last);
        float ratio = (float) ((time - start.time) / (end.time - start.time));
        return start.value.add(end.value.subtract(start.value).multiply(ratio));
    }
}
