package com.fangsu.render.math;

public class Matrix4f {
    public static final Matrix4f IDENTITY = new Matrix4f();

    private final org.joml.Matrix4f delegate;

    public Matrix4f() {
        this.delegate = new org.joml.Matrix4f();
    }

    public Matrix4f(com.mojang.math.Matrix4f matrix4f) {
        this.delegate = new org.joml.Matrix4f(matrix4f);
    }

    public Matrix4f(org.joml.Matrix4f matrix4f) {
        this.delegate = new org.joml.Matrix4f(matrix4f);
    }

    public Matrix4f copy() {
        return new Matrix4f(delegate);
    }

    public Matrix4f translate(float x, float y, float z) {
        delegate.translate(x, y, z);
        return this;
    }

    public Matrix4f translate(double x, double y, double z) {
        return translate((float) x, (float) y, (float) z);
    }

    public Matrix4f rotateX(float radians) { delegate.rotateX(radians); return this; }
    public Matrix4f rotateY(float radians) { delegate.rotateY(radians); return this; }
    public Matrix4f rotateZ(float radians) { delegate.rotateZ(radians); return this; }

    public org.joml.Matrix4f joml() { return delegate; }
}
