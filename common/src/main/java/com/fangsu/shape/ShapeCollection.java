package com.fangsu.shape;

import net.minecraft.world.phys.AABB;
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

    /**
     * 从资源包的像素坐标读取单盒或多盒列表；RawShape 内部统一使用方块坐标。
     * 与直接接收方块坐标的构造器区分，避免迁移后形状被放大 16 倍。
     */
    public static ShapeCollection fromPixels(List<?> coordinates) {
        ShapeCollection result = new ShapeCollection();
        if (coordinates == null || coordinates.isEmpty()) return result;
        if (coordinates.get(0) instanceof Number) {
            result.addPixelBox(coordinates);
        } else {
            for (Object row : coordinates) {
                if (row instanceof List<?> box) result.addPixelBox(box);
            }
        }
        return result;
    }

    private void addPixelBox(List<?> coordinates) {
        if (coordinates.size() < 6) return;
        double[] box = new double[6];
        for (int i = 0; i < box.length; i++) {
            if (!(coordinates.get(i) instanceof Number number)) return;
            box[i] = number.doubleValue() / 16d;
            if (!Double.isFinite(box[i])) return;
        }
        shapes.add(new RawShape(box));
    }

    /** VoxelShape 已经使用方块坐标，不再做像素换算。 */
    public static ShapeCollection fromVoxelShape(VoxelShape shape) {
        ShapeCollection result = new ShapeCollection();
        for (AABB box : shape.toAabbs()) {
            result.add(new RawShape(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        }
        return result;
    }

    /** 提交异步计算前生成不可变快照，后台不读取可变的 RawShape 集合。 */
    public List<AABB> toAabbs() {
        return shapes.stream().map(RawShape::asAABB).toList();
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
