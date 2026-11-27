package com.fangsu.mixin;

import com.fangsu.train.data.TrainDataRuntime;
import com.fangsu.train.data.TrainRailTravel;
import mtr.data.RailwayData;
import mtr.data.Train;
import mtr.data.TrainServer;
import mtr.path.PathData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = TrainServer.class, remap = false)
public abstract class TrainDataServerMixin {
    @Shadow private long routeId;
    @Unique private double fangsu$previousHead = Double.NaN;
    @Unique private boolean fangsu$previousReverse;

    @Inject(method = "handlePositions(Lnet/minecraft/world/level/Level;[Lnet/minecraft/world/phys/Vec3;F)Z", at = @At("HEAD"), require = 0, remap = true)
    private void fangsu$collectTrainMovement(Level world, Vec3[] positions, float ticksElapsed,
                                            CallbackInfoReturnable<Boolean> callback) {
        if (!(world instanceof ServerLevel level)) return;
        Train train = (Train) (Object) this;
        if (train.transportMode != mtr.data.TransportMode.TRAIN || train.path.isEmpty()) return;
        TrainTrackAccessor track = (TrainTrackAccessor) train;
        List<Double> ends = track.fangsu$distances();
        if (ends.size() != train.path.size()) return;
        double current = Math.max(0, Math.min(ends.get(ends.size() - 1), train.getRailProgress() - track.fangsu$modelZOffset()));
        double previous = TrainRailTravel.start(fangsu$previousHead, current, fangsu$previousReverse != train.isReversed());
        fangsu$previousHead = current;
        fangsu$previousReverse = train.isReversed();
        RailwayData railway = RailwayData.getInstance(level);
        if (railway == null) return;
        List<TrainDataRuntime.Slice> travel = new ArrayList<>();
        int start = Math.max(0, train.getIndex(previous, false));
        for (int index = start; index < train.path.size(); index++) {
            double begin = index == 0 ? 0 : ends.get(index - 1);
            double end = ends.get(index);
            if (begin > current) break;
            PathData segment = train.path.get(index);
            var station = railway.dataCache.platformIdToStation.get(segment.savedRailBaseId);
            long stationId = station == null ? 0 : station.id;
            boolean opposite = segment.startingPos.asLong() != segment.getRailProduct().getMostSignificantBits();
            TrainRailTravel.append(travel, distance -> segment.rail.getPosition(distance - begin),
                    Math.max(begin, previous), Math.min(current, end), routeId, stationId, opposite);
            if (end > current) break;
        }
        float speed = train.getSpeed();
        // 同 MTR3 广播器，车头当前方块及下方三格，水平半径取速度下整。
        TrainDataRuntime.SensorRange range = new TrainDataRuntime.SensorRange(Math.max(0, (int) Math.floor(speed)), 3, 0);
        TrainDataRuntime.move(level, train.id, train.trainId + "/" + train.trainCars + "/" + train.spacing, travel, speed, range);
    }
}
