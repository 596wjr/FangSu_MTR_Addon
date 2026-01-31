package com.fangsu.utils;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public class CollisionBoxUtil {
    public static class CollisionBox {
        private List<AABB> boxes = new ArrayList<>();

        public CollisionBox(int... pos) {
            if (pos == null || pos.length < 6) return;
            boxes.add(new AABB(pos[0] / 16d, pos[1] / 16d, pos[2] / 16d, pos[3] / 16d, pos[4] / 16d, pos[5] / 16d));
        }

        public CollisionBox(double... pos) {
            if (pos == null || pos.length < 6) return;
            boxes.add(new AABB(pos[0] / 16d, pos[1] / 16d, pos[2] / 16d, pos[3] / 16d, pos[4] / 16d, pos[5] / 16d));
        }

        public CollisionBox(List<?> pos) {
            if (pos == null || pos.isEmpty()) return;
            if (pos.get(0) instanceof Number) {
                if (pos.size() < 6) return;
                boxes.add(new AABB(((Double) pos.get(0)) / 16d, ((Double) pos.get(1)) / 16d, ((Double) pos.get(2)) / 16d, ((Double) pos.get(3)) / 16d, ((Double) pos.get(4)) / 16d, ((Double) pos.get(5)) / 16d));
            } else if (pos.get(0) instanceof List<?>) {
                A:
                for (Object o : pos) {
                    if (o instanceof List<?> oo) {
                        List<Double> thisList = new ArrayList<>();
                        for (Object o2 : oo) {
                            if (o2 instanceof Number) {
                                thisList.add((Double) o2);
                            } else continue A;
                        }
                        if (thisList.size() < 6) continue;
                        boxes.add(new AABB(thisList.get(0) / 16d, thisList.get(1) / 16d, thisList.get(2) / 16d, thisList.get(3) / 16d, thisList.get(4) / 16d, thisList.get(5) / 16d));
                    }
                }
            }
        }

        public VoxelShape asVoxelShape() {
            if (boxes.size() == 0) return null;
            if (boxes.size() == 1) return Shapes.create(boxes.get(0));
            return boxes.stream()
                    .map(Shapes::create)
                    .reduce(Shapes.empty(), Shapes::or);
        }
    }
}
