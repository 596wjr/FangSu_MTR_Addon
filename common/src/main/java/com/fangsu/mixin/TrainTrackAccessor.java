package com.fangsu.mixin;

import mtr.data.Train;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(value = Train.class, remap = false)
public interface TrainTrackAccessor {
    @Accessor("distances")
    List<Double> fangsu$distances();

    @Invoker("getModelZOffset")
    float fangsu$modelZOffset();
}
