package com.fangsu.render.sowcer.math;

import org.apache.commons.lang3.builder.EqualsBuilder;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class Matrix4f {


    protected final org.joml.Matrix4f impl;

    public Matrix4f() {
        this.impl = new org.joml.Matrix4f();
        this.impl.identity();
    }

    public Matrix4f(org.joml.Matrix4f moj) {
        this.impl = moj;
    }

    private Matrix4f(Matrix4f other) {
        this.impl = new org.joml.Matrix4f(other.impl);
    }

    public Matrix4f copy() {
        return new Matrix4f(this);
    }

    public org.joml.Matrix4f asMoj() {
        return impl;
    }

    public static Matrix4f translation(float x, float y, float z) {
        Matrix4f result = new Matrix4f();
        result.impl.translation(x, y, z);
        return result;
    }

    public void multiply(Matrix4f other) {
        impl.mul(other.impl);
    }

    public void store(FloatBuffer buffer) {
        buffer
                .put(0, impl.m00())
                .put(1, impl.m01())
                .put(2, impl.m02())
                .put(3, impl.m03())
                .put(4, impl.m10())
                .put(5, impl.m11())
                .put(6, impl.m12())
                .put(7, impl.m13())
                .put(8, impl.m20())
                .put(9, impl.m21())
                .put(10, impl.m22())
                .put(11, impl.m23())
                .put(12, impl.m30())
                .put(13, impl.m31())
                .put(14, impl.m32())
                .put(15, impl.m33());
    }

    public void load(FloatBuffer buffer) {
        float[] bufferValues = new float[16];
        buffer.get(bufferValues);
        impl.set(bufferValues);
    }

    public void rotateX(float rad) {
        impl.rotateX(rad);
    }

    public void rotateY(float rad) {
        impl.rotateY(rad);
    }

    public void rotateZ(float rad) {
        impl.rotateZ(rad);
    }

    public void rotate(Vector3f axis, float rad) {
        impl.rotate(rad, axis.impl);
    }

    public void translate(float x, float y, float z) {
        impl.translate(x, y, z);
    }

    public Vector3f transform(Vector3f src) {
        org.joml.Vector3f srcCpy = new org.joml.Vector3f(src.impl);
        return new Vector3f(impl.transformPosition(srcCpy));
    }

    public Vector3f transform3(Vector3f src) {
        org.joml.Vector3f srcCpy = new org.joml.Vector3f(src.impl);
        return new Vector3f(impl.transformDirection(srcCpy));
    }

    public org.joml.Matrix3f getRotationPart() {
        org.joml.Matrix3f result = new org.joml.Matrix3f();
        return impl.get3x3(result);
    }

    public Vector3f getTranslationPart() {
        org.joml.Vector3f result = new org.joml.Vector3f();
        return new Vector3f(impl.getTranslation(result));
    }


    @Override
    public int hashCode() {
        return impl.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Matrix4f matrix4f = (Matrix4f) o;

        return impl.equals(matrix4f.impl);
    }

    public static final Matrix4f IDENTITY = new Matrix4f();
}
