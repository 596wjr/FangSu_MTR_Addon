package fabric.cn.zbx1425.sowcer.math;

import java.util.ArrayDeque;
import java.util.Deque;

public class Matrices {
    private final Deque<Matrix4f> stack = new ArrayDeque<>();

    public Matrices() {
        stack.push(new Matrix4f());
    }

    public Matrix4f last() {
        return stack.peek();
    }

    public void pushPose() {
        stack.push(last().copy());
    }

    public void popPose() {
        if (stack.size() > 1) stack.pop();
    }

    public void translate(double x, double y, double z) {
        last().translate(x, y, z);
    }

    public void rotateX(float radians) {
        last().rotateX(radians);
    }

    public void rotateY(float radians) {
        last().rotateY(radians);
    }

    public void rotateZ(float radians) {
        last().rotateZ(radians);
    }
}
