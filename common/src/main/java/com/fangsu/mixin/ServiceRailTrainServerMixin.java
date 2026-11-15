package com.fangsu.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mtr.data.Train;
import mtr.data.TrainServer;
import mtr.path.PathData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = TrainServer.class, remap = false)
public abstract class ServiceRailTrainServerMixin {
    @WrapOperation(method = "getNextStoppingIndex",
            at = @At(value = "FIELD", target = "Lmtr/path/PathData;dwellTime:I"))
    private int fangsu$skipParkingInManual(PathData segment, Operation<Integer> original) {
        final int dwell = original.call(segment);
        return dwell > 1 && segment.savedRailBaseId == 0 && ((Train) (Object) this).isCurrentlyManual() ? 0 : dwell;
    }

    @Inject(method = "openDoors()Z", at = @At("HEAD"), cancellable = true)
    private void fangsu$keepParkingDoorsClosed(CallbackInfoReturnable<Boolean> cir) {
        final TrainAccess train = (TrainAccess) (Object) this;
        final int index = train.fangsu$getNextStoppingIndex();
        final List<PathData> path = ((Train) (Object) this).path;
        if (!((Train) (Object) this).isCurrentlyManual() && index >= 0 && index < path.size()) {
            final PathData segment = path.get(index);
            if (segment.savedRailBaseId == 0 && segment.dwellTime > 1) {
                cir.setReturnValue(false);
            }
        }
    }
}
