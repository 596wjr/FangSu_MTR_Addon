package com.fangsu.train;

import com.fangsu.MainClient;
import com.fangsu.mtr.LocalRoute;
import com.fangsu.render.RenderUtil;
import com.fangsu.render.scripting.AbstractDrawCalls;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.math.PoseStackUtil;
import com.fangsu.render.sowcer.math.Vector3f;
import com.fangsu.scripting.DisplayHelper;
import com.fangsu.train.lcds.MtrLcd;
import com.fangsu.utils.GraphicsTextureHelper;
import mtr.data.TrainClient;
import mtr.render.RenderTrains;
import mtr.render.TrainRendererBase;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

public class FunctionalTrainRenderer extends TrainRendererBase {
    public final TrainRendererBase baseRenderer;
    private final TrainClient train;
    private final TrainStatus trainStatus;

    public LcdInfo lcdInfo;
    private DisplayHelper dhBase;
    private DisplayHelper dh;
    private LcdBase lcd;

    private long cacheRoute = 0;

    public FunctionalTrainRenderer(TrainRendererBase baseRenderer) {
        this.baseRenderer = baseRenderer;
        this.train = null;
        this.trainStatus = null;
    }

    public FunctionalTrainRenderer(TrainRendererBase baseRenderer, LcdInfo lcdInfo) {
        this.baseRenderer = baseRenderer;
        this.train = null;
        this.trainStatus = null;
        this.lcdInfo = lcdInfo;

        dhBase = new DisplayHelper(lcdInfo.slotsInfo());
    }

    private FunctionalTrainRenderer(FunctionalTrainRenderer base, TrainClient trainClient) {
        this.baseRenderer = base;
        this.train = trainClient;
        this.trainStatus = new TrainStatus(trainClient);
        this.lcdInfo = base.lcdInfo;

        //TODO 测试代码!!!
        lcd = new MtrLcd();

        GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
        gtHelper.addDrawGraphic("train_" + (trainClient.trainId),
                new GraphicsTextureHelper.DrawInfo("train_lcd_" + trainClient.trainId, dh.texture.width,
                        dh.texture.height, false, true),
                (g) -> {
                    lcd.draw(g, trainStatus, 0, 0, 0, 200, 200, () -> dh.upload());
                });
        dh = base.dhBase.create(gtHelper.getGraphics("train_" + (trainClient.trainId)));
    }

    @Override
    public TrainRendererBase createTrainInstance(TrainClient trainClient) {
        return new FunctionalTrainRenderer(this, trainClient);
    }

    @Override
    public void renderCar(int carIndex, double x, double y, double z, float yaw, float pitch, boolean doorLeftOpen, boolean doorRightOpen) {
        assert train != null && trainStatus != null;
        if (train.getThisRoute() != null && cacheRoute != train.getThisRoute().id) {
            trainStatus.currentRoute = new LocalRoute(train.getThisRoute());
            cacheRoute = train.getThisRoute().id;
        }
        boolean shouldRender = !RenderUtil.shouldSkipRenderTrain(train);

        if (shouldRender && baseRenderer != null) {
            baseRenderer.renderCar(carIndex, x, y, z, yaw, pitch, doorLeftOpen, doorRightOpen);
        }

        if (isTranslucentBatch) return;

        final BlockPos posAverage = applyAverageTransform(train.getViewOffset(), x, y, z);
        Vector3f carPos = new Vector3f((float) x, (float) y, (float) z);
        Vec3 offset = train.vehicleRidingClient.getVehicleOffset();
        if (offset != null) {
            carPos.add((float) offset.x, (float) offset.y, (float) offset.z);
        }
        final boolean hasPitch = pitch < 0 ? train.transportMode.hasPitchAscending : train.transportMode.hasPitchDescending;
        Matrix4f worldPose = new Matrix4f();
        worldPose.translate(carPos.x(), carPos.y(), carPos.z());
        worldPose.rotateY((float) Math.PI + yaw);
        worldPose.rotateX(hasPitch ? pitch : 0);
        trainStatus.doorLeftOpen[carIndex] = doorLeftOpen;
        trainStatus.doorRightOpen[carIndex] = doorRightOpen;
        trainStatus.lastWorldPose[carIndex] = worldPose;
        trainStatus.lastCarPosition[carIndex] = carPos.copy();
        trainStatus.lastCarRotation[carIndex] = new Vector3f(hasPitch ? pitch : 0, (float) Math.PI + yaw, 0);
        trainStatus.isInDetailDistance |= posAverage != null
                && posAverage.distSqr(camera.getBlockPosition()) <= RenderTrains.DETAIL_RADIUS_SQUARED;
        trainStatus.shouldRender = shouldRender;

        if (posAverage == null) {
            if (carIndex == train.trainCars - 1) {
                // So it's outside visible range, but still need to call render function
            }
            return;
        }


        final int light = LightTexture.pack(world.getBrightness(LightLayer.BLOCK, posAverage), world.getBrightness(LightLayer.SKY, posAverage));

        if (shouldRender) {
            Matrix4f basePose = worldPose.copy();
            basePose.translate((float) x, (float) y, (float) z);
            basePose.rotateY((float) Math.PI + yaw);
            basePose.rotateX(hasPitch ? pitch : 0);
            basePose.translate(0, -1, 0);
//            basePose.rotateZ(isReversed ? -roll : roll);
            basePose.translate(0, 1, 0);
//            synchronized (trainScripting) {
//                trainScripting.commitCar(carIndex, MainClient.drawScheduler, basePose, worldPose, light);
            new AbstractDrawCalls.ClusterDrawCall(dh.model, basePose).commit(MainClient.drawScheduler, basePose, light);
//            }
        }

        matrices.popPose();

        if (carIndex == train.trainCars - 1) {
//            trainScripting.extraFinished();
//            typeScripting.tryCallRenderFunctionAsync(trainScripting);
        }
    }

    @Override
    public void renderConnection(Vec3 prevPos1, Vec3 prevPos2, Vec3 prevPos3, Vec3 prevPos4, Vec3 thisPos1, Vec3 thisPos2, Vec3 thisPos3, Vec3 thisPos4, double x, double y, double z, float yaw, float pitch) {
        assert train != null;
        if (RenderUtil.shouldSkipRenderTrain(train)) return;

        if (baseRenderer != null) {
            baseRenderer.renderConnection(prevPos1, prevPos2, prevPos3, prevPos4, thisPos1, thisPos2, thisPos3, thisPos4, x, y, z, yaw, pitch);
        }

        if (isTranslucentBatch) return;

        final BlockPos posAverage = applyAverageTransform(train.getViewOffset(), x, y, z);
        if (posAverage == null) return;
        matrices.pushPose();
        matrices.translate(x, y, z);
        PoseStackUtil.rotY(matrices, (float) Math.PI + yaw);
        final boolean hasPitch = pitch < 0 ? train.transportMode.hasPitchAscending : train.transportMode.hasPitchDescending;
        PoseStackUtil.rotX(matrices, hasPitch ? pitch : 0);
        final int light = LightTexture.pack(world.getBrightness(LightLayer.BLOCK, posAverage), world.getBrightness(LightLayer.SKY, posAverage));
        Matrix4f pose = new Matrix4f(matrices.last().pose());
        //预留:风挡
//        matrices.popPose();

        matrices.popPose();
    }

    @Override
    public void renderBarrier(Vec3 prevPos1, Vec3 prevPos2, Vec3 prevPos3, Vec3 prevPos4, Vec3 thisPos1, Vec3 thisPos2, Vec3 thisPos3, Vec3 thisPos4, double x, double y, double z, float yaw, float pitch) {
        assert train != null;
        if (RenderUtil.shouldSkipRenderTrain(train)) return;

        if (baseRenderer != null) {
            baseRenderer.renderBarrier(prevPos1, prevPos2, prevPos3, prevPos4, thisPos1, thisPos2, thisPos3, thisPos4, x, y, z, yaw, pitch);
        }
    }
}
