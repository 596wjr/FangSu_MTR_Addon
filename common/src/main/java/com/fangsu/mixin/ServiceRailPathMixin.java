package com.fangsu.mixin;

import com.fangsu.mtr.rail.ServiceRail;
import mtr.data.Rail;
import mtr.path.PathData;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PathData.class, remap = false)
public abstract class ServiceRailPathMixin {
    @Shadow @Final @Mutable public int dwellTime;

    @Inject(method = "<init>(Lmtr/data/Rail;JILnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;I)V",
            at = @At("RETURN"), remap = true)
    private void fangsu$parkingDwell(Rail rail, long savedRailId, int dwell, BlockPos start, BlockPos end,
                                    int stopIndex, CallbackInfo ci) {
        final var settings = ServiceRail.get(rail);
        if (savedRailId == 0 && settings != null && settings.kind() == ServiceRail.Kind.PARKING) {
            dwellTime = settings.value() * 2;
        }
    }
}
