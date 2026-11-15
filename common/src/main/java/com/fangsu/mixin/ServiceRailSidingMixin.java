package com.fangsu.mixin;

import com.fangsu.mtr.rail.ServiceRail;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mtr.data.RailType;
import mtr.data.Siding;
import mtr.path.PathData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Siding.class, remap = false)
public abstract class ServiceRailSidingMixin {
    @WrapOperation(method = "generateTimeSegments",
            at = @At(value = "FIELD", target = "Lmtr/data/RailType;maxBlocksPerTick:F"))
    private float fangsu$bypassSpeed(RailType type, Operation<Float> original, @Local PathData segment) {
        final var settings = ServiceRail.get(segment.rail);
        return settings != null && settings.kind() == ServiceRail.Kind.BYPASS ? settings.value() / 72F : original.call(type);
    }
}
