package com.fangsu.train;

import com.fangsu.blockEntities.IPlatformDoor;
import com.fangsu.blocks.IBlockPlatform;
import com.fangsu.mixin.TrainDoorPredictionAccessor;
import mtr.block.BlockPlatform;
import mtr.block.BlockPSDAPGBase;
import mtr.block.BlockPSDAPGDoorBase;
import mtr.data.RailwayData;
import mtr.data.TrainClient;
import mtr.path.PathData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.List;

/** 仅解算下一站的停车姿态并读取站台方块，绝不调用 simulateTrain / scanDoors 驱动屏蔽门。 */
final class DoorSidePredictorMtr3 {
    private static final Class<?> METROPOLIS_PLATFORM = optionalClass("team.dovecotmc.metropolis.block.interfaces.IBlockPlatform");
    private static final Method EYE_CANDY_IS_PLATFORM = eyeCandyMethod();

    private DoorSidePredictorMtr3() {
    }

    static int nextStopIndex(TrainClient train) {
        final List<Double> distances = ((TrainDoorPredictionAccessor) train).fangsu$getDistances();
        final int start = Math.max(0, train.getIndex(0, train.spacing, true));
        for (int i = start; i < train.path.size() && i < distances.size(); i++) {
            final PathData path = train.path.get(i);
            if (distances.get(i) + 0.001 < train.getRailProgress() || path.dwellTime <= 0) continue;
            if (path.savedRailBaseId != 0 && path.savedRailBaseId != train.sidingId) return i;
        }
        return -1;
    }

    static long nextPlatformId(TrainClient train) {
        final int index = nextStopIndex(train);
        return index < 0 ? 0 : train.path.get(index).savedRailBaseId;
    }

    static void predict(TrainClient train) {
        if (!train.isOnRoute() || train.getDoorValue() > 0.01 || train.trainCars <= 0) return;
        final int stopIndex = nextStopIndex(train);
        if (stopIndex < 0) return;
        final long platform = train.path.get(stopIndex).savedRailBaseId;
        final String type = train.trainId == null || train.trainId.isEmpty() ? train.baseTrainType : train.trainId;
        final int length = (int) Math.round(train.trainCars * (double) train.spacing);
        if (!DoorOpenCacheClient.hasUnknown(DoorOpenCacheClient.query(platform, type, train.trainCars, length))) return;
        final Level world = Minecraft.getInstance().level;
        if (world == null) return;
        final TrainDoorPredictionAccessor accessor = (TrainDoorPredictionAccessor) train;
        final List<Double> distances = accessor.fangsu$getDistances();
        final double stoppingDistance = distances.get(stopIndex);
        final double modelOffset = accessor.fangsu$getModelZOffset();
        final Vec3[] positions = new Vec3[train.trainCars + 1];
        for (int i = 0; i <= train.trainCars; i++) {
            // 与 MTR getRoutePosition 一致：反向时交换车体两端，并保留车型的半米纵向偏移。
            final int car = train.isReversed() ? train.trainCars - i : i;
            final double progress = Math.max(stoppingDistance - car * train.spacing - modelOffset, 0);
            final int pathIndex = train.getIndex(progress, false);
            if (pathIndex < 0 || pathIndex >= train.path.size()) return;
            positions[i] = train.path.get(pathIndex).rail.getPosition(progress - (pathIndex == 0 ? 0 : distances.get(pathIndex - 1)))
                    .add(0, train.transportMode.railOffset, 0);
        }

        final DoorOpenRecord.DoorOpenSide[] sides = new DoorOpenRecord.DoorOpenSide[train.trainCars];
        for (int car = 0; car < train.trainCars; car++) {
            final Vec3 first = positions[car], second = positions[car + 1];
            final double x = (first.x + second.x) / 2;
            final double y = (first.y + second.y) / 2 + 1;
            final double z = (first.z + second.z) / 2;
            final double spacing = first.distanceTo(second);
            final float yaw = (float) Mth.atan2(second.x - first.x, second.z - first.z);
            final float pitch = spacing == 0 ? 0 : (float) Math.asin((second.y - first.y) / spacing);
            final Boolean left = scan(world, x, y, z, (float) Math.PI + yaw, pitch, spacing / 2);
            final Boolean right = scan(world, x, y, z, yaw, pitch, spacing / 2);
            if (left == null || right == null) return;
            sides[car] = DoorOpenRecord.DoorOpenSide.of(left, right);
        }
        DoorOpenCacheClient.applyPrediction(platform,
                new DoorOpenRecord(sides, type, train.trainCars, length, train.isReversed(), 0).normalized());
    }

    /** 保留 MTR 的逐车厢扫描范围；区块未加载不把空气误记为 NONE。 */
    private static Boolean scan(Level world, double x, double y, double z, float yaw, float pitch, double halfSpacing) {
        final Vec3 offset = new Vec3(1, 0, 0).yRot(yaw).xRot(pitch);
        final Vec3 traverse = new Vec3(0, 0, 1).yRot(yaw).xRot(pitch);
        boolean found = false;
        for (int dx = 1; dx <= 3; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (double dz = -halfSpacing; dz <= halfSpacing; dz++) {
                    final BlockPos pos = RailwayData.newBlockPos(x + offset.x * dx + traverse.x * dz,
                            y + dy, z + offset.z * dx + traverse.z * dz);
                    if (!world.hasChunkAt(pos)) return null;
                    final var state = world.getBlockState(pos);
                    final var block = state.getBlock();
                    final var entity = world.getBlockEntity(pos);
                    if (entity instanceof IPlatformDoor door && door.isLocked()) continue;
                    if (block instanceof BlockPSDAPGDoorBase) {
                        if (state.getValue(BlockPSDAPGDoorBase.UNLOCKED)) found = true;
                    } else if (block instanceof BlockPlatform || block instanceof BlockPSDAPGBase
                            || block instanceof IBlockPlatform || METROPOLIS_PLATFORM.isInstance(block)) {
                        found = true;
                    } else if (entity != null && EYE_CANDY_IS_PLATFORM != null
                            && EYE_CANDY_IS_PLATFORM.getDeclaringClass().isInstance(entity)) {
                        try {
                            if (Boolean.TRUE.equals(EYE_CANDY_IS_PLATFORM.invoke(entity))) found = true;
                        } catch (ReflectiveOperationException ignored) {
                        }
                    }
                }
            }
        }
        return found;
    }

    private static Class<?> optionalClass(String name) {
        try {
            return Class.forName(name, false, DoorSidePredictorMtr3.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            return Void.class;
        }
    }

    private static Method eyeCandyMethod() {
        for (final String prefix : new String[]{"", "fabric.", "forge."}) {
            try {
                return optionalClass(prefix + "cn.zbx1425.mtrsteamloco.block.BlockEyeCandy$BlockEntityEyeCandy").getMethod("isPlatform");
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
