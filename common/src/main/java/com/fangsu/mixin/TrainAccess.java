package com.fangsu.mixin;

import mtr.data.Train;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Train.class, remap = false)
public interface TrainAccess {
    @Accessor("nextStoppingIndex")
    int fangsu$getNextStoppingIndex();
}
