package com.fangsu.train.bogie;

public final class BogieAnimationState {
    private double previous = Double.NaN;
    private double distance;
    private boolean reversed;

    public double update(double progress, boolean reverse) {
        double delta = progress - previous;
        if (Double.isFinite(delta) && delta >= 0 && delta <= 96 && reversed == reverse) {
            distance += reverse ? -delta : delta;
        }
        previous = progress;
        reversed = reverse;
        return distance;
    }

    public static double time(double distance, double blocksPerCycle, double length) {
        double remainder = distance % blocksPerCycle;
        if (remainder < 0) remainder += blocksPerCycle;
        return remainder / blocksPerCycle * length;
    }
}
