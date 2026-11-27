package com.fangsu.train.data;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.DoubleFunction;

public final class TrainRailTravel {
    private TrainRailTravel() {}

    public static double start(double previous, double current, boolean reset) {
        return reset || !Double.isFinite(previous) || current < previous || current - previous > 96 ? current : previous;
    }

    public static void append(List<TrainDataRuntime.Slice> travel, DoubleFunction<Vec3> sample,
                              double from, double to, long route, long station, boolean opposite) {
        if (!Double.isFinite(from) || !Double.isFinite(to) || to < from) return;
        Vec3 start = sample.apply(from);
        int steps = Math.max(1, (int) Math.ceil((to - from) * 4));
        for (int step = 1; step <= steps; step++) {
            Vec3 end = sample.apply(from + (to - from) * step / steps);
            travel.add(new TrainDataRuntime.Slice(start, end, route, station, opposite));
            start = end;
        }
    }
}
