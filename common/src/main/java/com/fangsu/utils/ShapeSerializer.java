package com.fangsu.utils;

import com.fangsu.Main;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ShapeSerializer {
    private static final Map<String, VoxelShape> shapeMap = new HashMap<>();

    public static boolean isValid(String shape, int yRot) {
        if (shape == null || shape.isEmpty()) return false;
        String key = shape + "_" + yRot;
        if (shapeMap.containsKey(key)) return true;
        try {
            VoxelShape v = parseShape(shape, yRot);
            shapeMap.put(key, v);
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

    public static VoxelShape getShape(Object rawShape, int yRot) {
        if (rawShape == null) return Shapes.empty();
        if (rawShape instanceof String s) {
            try {
                return getShape(s, yRot);
            } catch (Exception e) {
                Main.LOGGER.warn("Invalid shape string: {}", s, e);
                return Shapes.empty();
            }
        }
        String serialized = serialize(rawShape);
        if (serialized.isEmpty()) return Shapes.empty();
        try {
            return getShape(serialized, yRot);
        } catch (Exception e) {
            Main.LOGGER.warn("Invalid serialized shape: {}", serialized, e);
            return Shapes.empty();
        }
    }

    public static String serialize(Object rawShape) {
        List<double[]> boxes = new ArrayList<>();
        flattenBoxes(rawShape, boxes);
        if (boxes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < boxes.size(); i++) {
            if (i > 0) sb.append("/");
            double[] b = boxes.get(i);
            for (int j = 0; j < 6; j++) {
                if (j > 0) sb.append(",");
                sb.append(trimTrailingZero(b[j]));
            }
        }
        return sb.toString();
    }

    private static String trimTrailingZero(double value) {
        String text = Double.toString(value);
        if (!text.contains(".")) return text;
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') end--;
        if (end > 0 && text.charAt(end - 1) == '.') end--;
        return text.substring(0, end);
    }

    private static void flattenBoxes(Object raw, List<double[]> out) {
        if (raw == null) return;
        if (raw instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> ordered = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                ordered.put(Objects.toString(e.getKey(), ""), e.getValue());
            }
            ordered.keySet().stream().sorted().forEach(key -> flattenBoxes(ordered.get(key), out));
            return;
        }
        if (raw instanceof List<?> list) {
            if (list.size() == 6 && list.stream().allMatch(v -> v instanceof Number)) {
                double[] box = new double[6];
                for (int i = 0; i < 6; i++) {
                    box[i] = ((Number) list.get(i)).doubleValue();
                }
                out.add(box);
                return;
            }
            for (Object item : list) {
                flattenBoxes(item, out);
            }
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
