package com.fangsu.train.pantograph;

import com.fangsu.train.data.TrainDataSnapshot.PantographState;

public final class PantographAnimationState {
    private double seconds;
    private long previous;
    private PantographState target = PantographState.UNKNOWN;

    public double sample(PantographState next, double length, long now, boolean initial) {
        if (previous != 0) {
            double elapsed = Math.max(0, now - previous) / 1_000_000_000.0;
            seconds = Math.max(0, Math.min(length, seconds + (target == PantographState.UP ? elapsed : -elapsed)));
        }
        if (initial && target == PantographState.UNKNOWN) seconds = next == PantographState.UP ? length : 0;
        previous = now;
        target = next;
        return seconds;
    }
}
