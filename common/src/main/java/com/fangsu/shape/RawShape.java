package com.fangsu.shape;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.stream.IntStream;

public class RawShape {
    private double x1, x2, y1, y2, z1, z2;

    public RawShape() {
        x1 = 0;
        x2 = 0;
        y2 = 0;
        y1 = 0;
        z1 = 0;
        z2 = 0;
    }

    public RawShape(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.x1 = x1;
        this.x2 = x2;
        this.y2 = y2;
        this.y1 = y1;
        this.z1 = z1;
        this.z2 = z2;
        normalize();
    }

    public RawShape(double[] points) {
        if (points.length != 6) throw new IllegalStateException("RawShape must have 6 points");
        x1 = points[0];
        y1 = points[1];
        z1 = points[2];
        x2 = points[3];
        y2 = points[4];
        z2 = points[5];
        normalize();
    }

    public RawShape(float[] points) {
        this(IntStream.range(0, points.length)
                .mapToDouble(i -> points[i])
                .toArray());
    }

    private RawShape(RawShape other) {
        x1 = other.x1;
        x2 = other.x2;
        y2 = other.y2;
        y1 = other.y1;
        z1 = other.z1;
        z2 = other.z2;
        normalize();
    }

    public RawShape(List<? extends Number> points) {
        if (points.size() != 6)
            throw new IllegalStateException("RawShape must have 6 points but input " + (points.toString()));
        x1 = points.get(0).doubleValue();
        y1 = points.get(1).doubleValue();
        z1 = points.get(2).doubleValue();
        x2 = points.get(3).doubleValue();
        y2 = points.get(4).doubleValue();
        z2 = points.get(5).doubleValue();
        normalize();
    }

    private void normalize() {
        double temp;
        if (x1 > x2) {
            temp = x2;
            x2 = x1;
            x1 = temp;
        }
        if (y1 > y2) {
            temp = y2;
            y2 = y1;
            y1 = temp;
        }
        if (z1 > z2) {
            temp = z2;
            z2 = z1;
            z1 = temp;
        }
    }

    public boolean isEmpty() {
        return x1 == x2 && y1 == y2 && z1 == z2;
    }

    public RawShape move(double x, double y, double z) {
        this.x1 += x;
        this.x2 += x;
        this.y1 += y;
        this.y2 += y;
        this.z1 += z;
        this.z2 += z;
        return this;
    }


    public AABB asAABB() {
        return new AABB(x1, y1, z1, x2, y2, z2);
    }

    public VoxelShape asVoxelShape() {
        return Shapes.create(asAABB());
    }

    public RawShape copy() {
        return new RawShape(this);
    }

    @Override
    public String toString() {
        return "[" + x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2 + "]";
    }
}
