package com.fangsu.utils;

import com.fangsu.Main;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class ShapeSerializer {
    private static final Map<String, VoxelShape> shapeMap = new HashMap<>();

    public static boolean isValid(String shape, int yRot) {
        if (shape == null || shape.isEmpty()) return false;
        if (shapeMap.containsKey(shape)) return true;
        try {
            VoxelShape v = parseShape(shape, yRot);
            shapeMap.put(shape + "_" + yRot, v);
            return true;
        } catch (Exception e) {
            Main.LOGGER.error("Error parsing shape: {}", shape, e);
            return false;
        }
    }

    public static VoxelShape getShape(String shape, int yRot) throws Exception {
        if (shape == null || shape.isEmpty()) return Shapes.empty();
        String key = shape + "_" + yRot;
        if (shapeMap.containsKey(key)) {
            return shapeMap.get(key);
        } else {
            VoxelShape v = parseShape(shape, yRot);
            shapeMap.put(key, v);
            return v;
        }
    }

    private static VoxelShape parseShape(String shape, int yRot) throws Exception {
        if (shape == null || shape.isEmpty()) throw new Exception("Invalid shape: " + shape);
        String[] shapeArray = shape.split("/");
        List<VoxelShape> voxelShapes = new ArrayList<>();

        for (int i = 0; i < shapeArray.length; i++) {
            String[] posArray = shapeArray[i].split(",");

            if (posArray.length != 6) {
                throw new Exception("Invalid shape: " + shape);
            }

            Double[] pos = parsePositions(posArray);

            Double[] rotatedPos = applyRotation(pos, yRot);

            VoxelShape voxelShape = Block.box(
                    rotatedPos[0], rotatedPos[1], rotatedPos[2],
                    rotatedPos[3], rotatedPos[4], rotatedPos[5]
            );
            voxelShapes.add(voxelShape);
        }
        return combineShapes(voxelShapes);
    }

    private static Double[] parsePositions(String[] posArray) throws Exception {
        Double[] pos = new Double[6];
        for (int j = 0; j < posArray.length; j++) {
            pos[j] = Double.parseDouble(posArray[j].trim());
        }
        return pos;
    }

    private static Double[] applyRotation(Double[] pos, int yRot) {
        double x1 = pos[0], y1 = pos[1], z1 = pos[2], x2 = pos[3], y2 = pos[4], z2 = pos[5];
        switch (yRot) {
            case 90:
                return new Double[]{16 - z2, y1, x1, 16 - z1, y2, x2};
            case 180:
                return new Double[]{16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1};
            case 270:
                return new Double[]{z1, y1, 16 - x2, z2, y2, 16 - x1};
            default:
                return new Double[]{x1, y1, z1, x2, y2, z2};
        }
    }

    private static VoxelShape combineShapes(List<VoxelShape> voxelShapes) throws Exception {
        if (voxelShapes.isEmpty()) {
            return Shapes.empty();
        }
        VoxelShape finalShape = null;
        for (VoxelShape voxelShape : voxelShapes) {
            if (finalShape == null) {
                finalShape = voxelShape;
            } else {
                finalShape = Shapes.or(finalShape, voxelShape);
            }
        }
        return finalShape;
    }
}