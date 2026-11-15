package com.fangsu.mixin;

import com.fangsu.mtr.rail.ServiceRail;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mtr.data.Rail;
import mtr.data.RailType;
import mtr.render.RenderTrains;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RenderTrains.class, remap = false)
public abstract class ServiceRailOverlayMixin {
    @WrapOperation(method = "lambda$render$7",
            at = @At(value = "FIELD", target = "Lmtr/data/RailType;color:I"))
    private static int fangsu$serviceRailOverlay(RailType type, Operation<Integer> original, @Local(argsOnly = true) Rail rail) {
        final var settings = ServiceRail.get(rail);
        return settings == null ? original.call(type) : settings.kind().overlayColor;
    }
}
