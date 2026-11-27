package com.fangsu.train.parts;

import com.fangsu.mixin.TrainTrackAccessor;
import com.fangsu.mtr.rail.RailTiltRenderHelper;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import mtr.data.Rail;
import mtr.data.TrainClient;
import mtr.path.PathData;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class TrainPartRenderAdapterMtr3 {
    private static final class CarFrame {
        final long train;
        final int index;
        final Vec3 center;
        Matrix4f unrolledBody;

        CarFrame(long train, int index, Vec3 center) {
            this.train = train;
            this.index = index;
            this.center = center;
        }
    }

    record TrackPose(Vec3 anchor, float yaw, float pitch, float roll) {}

    private static final ThreadLocal<CarFrame> CAR = new ThreadLocal<>();

    private TrainPartRenderAdapterMtr3() {}

    public static void begin(TrainClient train, int car, double x, double y, double z) {
        TrainPartsRegistry.Parts parts = TrainPartsRegistry.get(train.trainId);
        if (parts != null && !parts.bogies().isEmpty()) {
            CAR.set(new CarFrame(train.id, car, new Vec3(x, y, z)));
        }
    }

    public static void end() { CAR.remove(); }

    /** 保留相机及乘车变换，后续用轨道姿态取代车体姿态。 */
    public static void captureUnrolledBody(PoseStack matrices) {
        CarFrame car = CAR.get();
        if (car != null) car.unrolledBody = new Matrix4f(matrices.last().pose()).copy();
    }

    public static void pantographs(TrainClient train, int index, PoseStack matrices, int light) {
        TrainPartsRegistry.Parts parts = TrainPartsRegistry.get(train.trainId);
        if (parts == null) return;
        Matrix4f base = new Matrix4f(matrices.last().pose()).copy();
        base.rotateX((float) Math.PI);
        TrainPartRenderer.pantographs(train.id, index + 1, train.trainCars, parts, base, light);
    }

    public static boolean carBogie(TrainClient train, int slot, double z, PoseStack matrices,
                                   float yaw, float pitch, int light) {
        CarFrame car = CAR.get();
        if (car == null || car.train != train.id) return false;
        Matrix4f unrolled = car.unrolledBody == null ? new Matrix4f(matrices.last().pose()) : car.unrolledBody;
        return drawBogie(train, car, slot, z, false, unrolled, car.center, yaw, pitch, light);
    }

    public static boolean sharedBogie(TrainClient train, PoseStack matrices, double x, double y, double z,
                                      float yaw, float pitch, int light) {
        CarFrame car = CAR.get();
        if (car == null || car.train != train.id) return false;
        // 连接处收到的是减去车辆偏移的坐标，未乘车时没有该偏移。
        Vec3 offset = train.vehicleRidingClient.getVehicleOffset();
        Vec3 center = offset == null ? new Vec3(x, y, z) : new Vec3(x + offset.x, y + offset.y, z + offset.z);
        return drawBogie(train, car, 2, 0, true, new Matrix4f(matrices.last().pose()), center, yaw, pitch, light);
    }

    private static boolean drawBogie(TrainClient train, CarFrame car, int slot, double z, boolean shared,
                                     Matrix4f unrolled, Vec3 center, float yaw, float pitch, int light) {
        TrainPartsRegistry.Parts parts = TrainPartsRegistry.get(train.trainId);
        if (parts == null) return false;
        TrainPartsRegistry.Bogie definition = parts.bogie(car.index + 1, train.trainCars, slot);
        if (definition == null) return false;

        TrainTrackAccessor track = (TrainTrackAccessor) (Object) train;
        boolean reversed = train.isReversed();
        int facing = reversed ? 1 : -1;
        double carNumber = car.index + (shared ? 0 : 0.5);
        double railDistance = train.getRailProgress()
                - train.spacing * (reversed ? train.trainCars - carNumber : carNumber) - track.fangsu$modelZOffset()
                + z * facing;
        TrackPose target = sampleTrackPose(train.path, track.fangsu$distances(), railDistance, facing, train.transportMode.railOffset);
        if (target == null) return false;

        boolean canPitch = pitch < 0 ? train.transportMode.hasPitchAscending : train.transportMode.hasPitchDescending;
        Matrix4f pose = trackTransform(unrolled, center, yaw, canPitch ? pitch : 0, target);
        return TrainPartRenderer.bogie(train.id, definition, pose, train.getRailProgress(), reversed, light);
    }

    static Matrix4f trackTransform(Matrix4f unrolled, Vec3 origin, float yaw, float pitch, TrackPose target) {
        Matrix4f base = unrolled.copy();
        base.rotateX(-((float) Math.PI + pitch));
        base.rotateY(-((float) Math.PI + yaw));
        Vec3 delta = target.anchor().subtract(origin);
        base.translate((float) delta.x, (float) delta.y, (float) delta.z);
        base.rotateY(target.yaw());
        base.rotateX(target.pitch());
        base.rotateZ(target.roll());
        return base;
    }

    static TrackPose sampleTrackPose(List<PathData> path, List<Double> ends, double distance, int facing, double railOffset) {
        if (path.isEmpty() || ends.size() != path.size() || !Double.isFinite(distance)) return null;
        double position = Math.max(0, Math.min(ends.get(ends.size() - 1), distance));
        int low = 0;
        int high = ends.size() - 1;
        // 与 MTR 的 getIndex(progress, false) 一致，里程在接头处选择后一段。
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (position < ends.get(middle)) high = middle;
            else low = middle + 1;
        }

        Rail rail = path.get(low).rail;
        double begin = low == 0 ? 0 : ends.get(low - 1);
        double local = Math.max(0, Math.min(rail.getLength(), position - begin));
        Vec3 anchor = rail.getPosition(local).add(0, railOffset, 0);
        // 同一条轨道内求切向，折返接头不会把正、反方向的向量混在一起。
        Vec3 forward = rail.getPosition(local + 0.35);
        Vec3 backward = rail.getPosition(local - 0.35);
        Vec3 tangent = forward.subtract(backward).scale(facing);
        if (tangent.lengthSqr() < 1e-8) return null;

        RailTiltRenderHelper.RollFrame frame = RailTiltRenderHelper.frameFor(rail);
        double roll = frame == null ? 0 : RailTiltRenderHelper.railFrameAngle(
                frame.core.getRollRadians(local), frame.parameterIsPosition1ToPosition2);
        // 模型的 +Z 轴朝向与 native +Z 相反，换算成模型局部滚转。
        return new TrackPose(anchor, (float) (Math.PI + Math.atan2(tangent.x, tangent.z)),
                (float) Math.atan2(tangent.y, Math.hypot(tangent.x, tangent.z)), (float) (-facing * roll));
    }
}
