package com.fangsu.shape;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ShapeCollection {
    private final List<RawShape> shapes;

    public ShapeCollection() {
        this.shapes = new ArrayList<>();
    }

    public ShapeCollection(RawShape... shapes) {
        this.shapes = new ArrayList<>(Arrays.asList(shapes));
    }

    public ShapeCollection(double[][] shapes) {
        this.shapes = new ArrayList<>();
        for (double[] shape : shapes) {
            if (shape == null) continue;
            this.shapes.add(new RawShape(shape));
        }
    }

    public ShapeCollection(float[][] shapes) {
        this.shapes = new ArrayList<>();
        for (float[] shape : shapes) {
            if (shape == null) continue;
            this.shapes.add(new RawShape(shape));
        }
    }

    public ShapeCollection(Collection<RawShape> shapes) {
        this.shapes = new ArrayList<>(shapes);
    }

    public ShapeCollection add(RawShape shape) {
        shapes.add(shape);
        return this;
    }

    public ShapeCollection addAll(ShapeCollection shapeCollection) {
        shapes.addAll(shapeCollection.shapes);
        return this;
    }

    public ShapeCollection addAll(Collection<RawShape> shapeCollection) {
        shapes.addAll(shapeCollection);
        return this;
    }

    public ShapeCollection moveAll(double x, double y, double z) {
        shapes.forEach(shape -> shape.move(x, y, z));
        return this;
    }

    public boolean isEmpty() {
        boolean empty = true;
        for (RawShape shape : shapes) empty &= shape.isEmpty();
        return empty;
    }

    public List<RawShape> getShapes() {
        return shapes;
    }

    public ShapeCollection copy() {
        var copyList = new ArrayList<RawShape>();
        for (RawShape shape : shapes) {
            copyList.add(shape.copy());
        }
        return new ShapeCollection(copyList);
    }

    public VoxelShape asVoxelShape() {
        VoxelShape result = Shapes.empty();
        for (RawShape shape : shapes) {
            result = Shapes.or(result, shape.asVoxelShape());
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append('\n');
        for (RawShape shape : shapes) {
            sb.append(shape.toString());
            sb.append('\n');
        }
        sb.append('\n');
        sb.append(']');
        return sb.toString();
    }
}
