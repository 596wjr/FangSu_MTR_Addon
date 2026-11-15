package com.fangsu.mixin;

import com.fangsu.mtr.rail.ServiceRail;
import mtr.data.Train;
import mtr.path.PathData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = Train.class, remap = false)
public abstract class ServiceRailTrainMixin {
    @Shadow @Final public List<PathData> path;

    @Inject(method = "getRailSpeed", at = @At("HEAD"), cancellable = true)
    private void fangsu$bypassSpeed(int railIndex, CallbackInfoReturnable<Float> cir) {
        final var settings = ServiceRail.get(path.get(railIndex).rail);
        if (settings != null && settings.kind() == ServiceRail.Kind.BYPASS) {
            cir.setReturnValue(settings.value() / 72F);
        }
    }
}
