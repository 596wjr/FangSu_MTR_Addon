package com.fangsu.turnout.marker;

import net.minecraft.core.BlockPos;

/** 单个轨道节点标记：节点坐标 + 颜色 */
public record NodeMarker(BlockPos pos, MarkerColor color) {
}
