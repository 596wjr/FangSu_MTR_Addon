package com.fangsu.mtr.rail;

import net.minecraft.core.BlockPos;

public record BypassMapMarker(BlockPos start, BlockPos end, ServiceRail settings,
                              int left, int top, int right, int bottom) {
    public boolean contains(double x, double y) {
        return x >= left && x < right && y >= top && y < bottom;
    }
}
