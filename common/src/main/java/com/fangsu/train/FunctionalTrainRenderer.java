package com.fangsu.train;

import com.fangsu.Main;
import com.fangsu.MainClient;
import com.fangsu.render.RenderUtil;
import com.fangsu.render.scripting.AbstractDrawCalls;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.math.PoseStackUtil;
import com.fangsu.render.sowcer.math.Vector3f;
import com.fangsu.scripting.DisplayHelper;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.train.lcds.MtrLcd;
import com.fangsu.utils.GraphicsTextureHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mtr.data.TrainClient;
import mtr.render.TrainRendererBase;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class FunctionalTrainRenderer extends TrainRendererBase {
    private final TrainClient train;
    private final DisplayHelper dhBase;
    private final DisplayHelper dh;
    private final TrainStatus trainStatus;
    private LcdBase lcd;
    private final LcdInfo lcdInfo;

    private final Map<String, Object> drawState = new HashMap<>();

    public final TrainRendererBase baseRenderer;

    public FunctionalTrainRenderer(LcdInfo lcdInfo, TrainRendererBase base) {
        this.lcdInfo = lcdInfo;
        this.train = null;
        this.baseRenderer = base;

        this.lcd = null;

        this.dhBase = lcdInfo != null ? new DisplayHelper(lcdInfo.slotsInfo()) : null;
        this.dh = null;
        this.trainStatus = null;
    }

    private FunctionalTrainRenderer(LcdInfo lcdInfo, TrainRendererBase base, TrainClient train, LcdBase lcd, DisplayHelper dh, TrainStatus trainStatus) {
        this.lcdInfo = lcdInfo;
        this.train = train;
        this.baseRenderer = base;
        this.lcd = lcd;
        this.dhBase = null;
        this.dh = dh;
        this.trainStatus = trainStatus;
    }

    @Override
    public TrainRendererBase createTrainInstance(TrainClient trainClient) {
        var instanceBaseRenderer = baseRenderer == null ? null : baseRenderer.createTrainInstance(trainClient);

        TrainStatus trainStatus = new TrainStatus(trainClient);

        if (lcdInfo == null) {
            return new FunctionalTrainRenderer(null, instanceBaseRenderer, trainClient, null, null, trainStatus);
        }

        LcdBase lcd = LcdManager.getInstance().getLcd(lcdInfo.id());
        if (lcd == null) {
            Main.LOGGER.warn("LCD not found for id: {}", lcdInfo.id());
            return new FunctionalTrainRenderer(lcdInfo, instanceBaseRenderer, trainClient, null, null, trainStatus);
        }

        GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
        gtHelper.addDrawGraphicWithGt("train_" + (trainClient.trainId),
                new GraphicsTextureHelper.DrawInfo("train_lcd_" + trainClient.trainId,
                        lcdInfo.slotsInfo().getAsJsonArray("texSize").get(0).getAsInt(),
                        lcdInfo.slotsInfo().getAsJsonArray("texSize").get(1).getAsInt(),
                        false, true),
                (gt) -> {
                    drawState.clear();
                    var slots = lcdInfo.slotsInfo().getAsJsonArray("slots");
                    trainStatus.updateRoute();
                    for (JsonElement slot : slots) {
                        JsonObject obj = slot.getAsJsonObject();
                        String name = obj.get("name").getAsString();
                        JsonArray texAreaJson = obj.get("texArea").getAsJsonArray();
                        int[] texArea = new int[]{texAreaJson.get(0).getAsInt(), texAreaJson.get(1).getAsInt(), texAreaJson.get(2).getAsInt(), texAreaJson.get(3).getAsInt()};
                        lcd.draw(gt.graphics, trainStatus, lcdInfo, drawState,
                                name, texArea[0], texArea[1], texArea[2], texArea[3], gt::upload);
                    }
                });
        var dh = dhBase.create(gtHelper.getGraphics("train_" + (trainClient.trainId)));

        return new FunctionalTrainRenderer(lcdInfo, instanceBaseRenderer, trainClient, lcd, dh, trainStatus);
    }

    @Override
    public void renderCar(int carIndex, double x, double y, double z, float yaw, float pitch, boolean doorLeftOpen, boolean doorRightOpen) {
        if (baseRenderer != null) {
            baseRenderer.renderCar(carIndex, x, y, z, yaw, pitch, doorLeftOpen, doorRightOpen);
        }

//        if (RenderUtil.shouldSkipRenderTrain(train)) {
//            return;
//        }

        final BlockPos posAverage = applyAverageTransform(train.getViewOffset(), x, y, z);

        final boolean hasPitch = pitch < 0 ? train.transportMode.hasPitchAscending : train.transportMode.hasPitchDescending;

        Vector3f carPos = new Vector3f((float) x, (float) y, (float) z);
        Vec3 offset = train.vehicleRidingClient.getVehicleOffset();
        Matrix4f carPose = new Matrix4f();

        carPose.translate((float) x, (float) y, (float) z);
        if (offset != null) {
            carPos.add((float) offset.x, (float) offset.y, (float) offset.z);
        }
        carPose.rotateY((float) Math.PI + yaw);
        carPose.rotateX(hasPitch ? pitch : 0);

        trainStatus.update(carIndex, doorLeftOpen, doorRightOpen, carPose.copy());

//        if (posAverage == null) {
//            if (carIndex == train.trainCars - 1) {
//                // So it's outside visible range, but still need to call render function
//                GraphicsTextureHelper.getInstance().getGraphics("train_" + (train.trainId));
//            }
//            return;
//        }


        matrices.translate(x, y, z);
        PoseStackUtil.rotY(matrices, (float) Math.PI + yaw);
        PoseStackUtil.rotX(matrices, hasPitch ? pitch : 0);
        try {
            final int light = LightTexture.pack(world.getBrightness(LightLayer.BLOCK, posAverage), world.getBrightness(LightLayer.SKY, posAverage));
            Matrix4f drawPose = new Matrix4f(matrices.last().pose());
            if (dh != null && dh.model != null) {
                GraphicsTexture texture = GraphicsTextureHelper.getInstance().getGraphics("train_" + (train.trainId));
                if (texture != null) {
                    dh.changeSharedGt(texture);
                    var model = dh.model.getUploadedModel();
                    if (model != null) {
                        model.replaceAllTexture(texture.identifier);
                        new AbstractDrawCalls.ClusterDrawCall(model, Matrix4f.IDENTITY).commit(MainClient.drawScheduler, drawPose, light);
                    }
                }
            }
        } catch (Exception e) {
            Main.LOGGER.error("", e);
        } finally {
            matrices.popPose();
        }

    }

    @Override
    public void renderConnection(Vec3 prevPos1, Vec3 prevPos2, Vec3 prevPos3, Vec3 prevPos4, Vec3 thisPos1, Vec3 thisPos2, Vec3 thisPos3, Vec3 thisPos4, double x, double y, double z, float yaw, float pitch) {
        if (baseRenderer != null)
            baseRenderer.renderConnection(prevPos1, prevPos2, prevPos3, prevPos4, thisPos1, thisPos2, thisPos3, thisPos4, x, y, z, yaw, pitch);
    }

    @Override
    public void renderBarrier(Vec3 prevPos1, Vec3 prevPos2, Vec3 prevPos3, Vec3 prevPos4, Vec3 thisPos1, Vec3 thisPos2, Vec3 thisPos3, Vec3 thisPos4, double x, double y, double z, float yaw, float pitch) {
        if (baseRenderer != null)
            baseRenderer.renderBarrier(prevPos1, prevPos2, prevPos3, prevPos4, thisPos1, thisPos2, thisPos3, thisPos4, x, y, z, yaw, pitch);

    }
}
