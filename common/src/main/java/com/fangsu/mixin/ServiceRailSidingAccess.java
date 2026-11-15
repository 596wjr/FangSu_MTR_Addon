package com.fangsu.mixin;

import mtr.data.Siding;
import mtr.path.PathData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = Siding.class, remap = false)
public interface ServiceRailSidingAccess {
    @Accessor("path")
    List<PathData> fangsu$getPath();
}
