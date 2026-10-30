package com.fangsu.mixin;

import mtr.data.Train;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** 只读停车姿态计算需要的里程表与车型纵向偏移，不修改正在运行的列车。 */
@Mixin(value = Train.class, remap = false)
public interface TrainDoorPredictionAccessor {
    @Accessor("distances")
    List<Double> fangsu$getDistances();

    @Invoker("getModelZOffset")
    float fangsu$getModelZOffset();
}
