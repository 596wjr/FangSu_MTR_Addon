package com.fangsu.render.math;

import java.util.ArrayDeque;
import java.util.Deque;

public class Matrices {
    private final Deque<Matrix4f> stack = new ArrayDeque<>();

    public Matrices() {
        stack.push(new Matrix4f());
    }

    public void pushPose() { stack.push(last().copy()); }
    public void popPose() { if (stack.size() > 1) stack.pop(); }

    public Matrices translate(double x, double y, double z) { last().translate(x, y, z); return this; }
    public Matrices rotateX(float radians) { last().rotateX(radians); return this; }
    public Matrices rotateY(float radians) { last().rotateY(radians); return this; }
    public Matrices rotateZ(float radians) { last().rotateZ(radians); return this; }

    public Matrix4f last() { return stack.peek(); }
}
