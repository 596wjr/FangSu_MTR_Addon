package com.fangsu.render.sowcer.math;

import java.util.Stack;

public class Matrices {


    private final Stack<Matrix4f> stack = new Stack<>();

    public Matrices() {
        stack.push(new Matrix4f());
    }

    public void translate(double x, double y, double z) {
        this.translate((float) x, (float) y, (float) z);
    }

    public void translate(float x, float y, float z) {
        stack.peek().translate(x, y, z);
    }

    public void rotate(float x, float y, float z, float radian) {
        stack.peek().rotate(new Vector3f(x, y, z), radian);
    }

    public void rotateX(float radian) {
        stack.peek().rotateX(radian);
    }

    public void rotateY(float radian) {
        stack.peek().rotateY(radian);
    }

    public void rotateZ(float radian) {
        stack.peek().rotateZ(radian);
    }

    public void pushPose() {
        stack.push(stack.peek().copy());
    }

    public void popPose() {
        stack.pop();
    }

    public void popPushPose() {
        stack.pop();
        stack.push(stack.peek().copy());
    }

    public Matrix4f last() {
        return this.stack.peek();
    }

    public boolean clear() {
        return this.stack.size() == 1;
    }

    public void setIdentity() {
        stack.pop();
        stack.push(new Matrix4f());
    }

    /**
     * 就地重置栈顶矩阵为单位矩阵（复用现有对象，避免分配）。
     */
    public void identity() {
        //#if MC_VERSION >= "11903"
        stack.peek().asMoj().identity();
        //#else
        //$$ stack.peek().asMoj().setIdentity();
        //#endif
    }
}
