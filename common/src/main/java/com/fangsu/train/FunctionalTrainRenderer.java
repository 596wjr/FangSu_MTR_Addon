package com.fangsu.train;

import com.fangsu.Main;
import com.fangsu.MainClient;
import com.fangsu.mtr.rail.RailTrainRollHelper;
import com.fangsu.render.scripting.AbstractDrawCalls;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcer.math.PoseStackUtil;
import com.fangsu.render.sowcer.math.Vector3f;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.scripting.DisplayHelper;
import com.fangsu.scripting.GraphicsTexture;
import com.fangsu.train.lcds.MtrLcd;
import com.fangsu.userScripts.ScriptHolderBase;
import com.fangsu.userScripts.ScriptManager;
import org.graalvm.polyglot.proxy.ProxyObject;
import com.fangsu.utils.GraphicsTextureHelper;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mtr.data.TrainClient;
import mtr.render.TrainRendererBase;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FunctionalTrainRenderer extends TrainRendererBase {
    private static final Map<Long, FunctionalTrainRenderer> ACTIVE_LCDS = new ConcurrentHashMap<>();
    private final Object clientWorld = net.minecraft.client.Minecraft.getInstance().level;
    private final Map<ModelCluster, ModelCluster> materialModels = new HashMap<>();
    private ResourceLocation materialTexture;

    /** 配合观测端的客户端车辆快照回收已消失列车的独占纹理。 */
    public static void purgeMissingTrains(Set<Long> alive) {
        ACTIVE_LCDS.entrySet().removeIf(entry -> {
            final long id = entry.getKey();
            if (alive.contains(id) && entry.getValue().clientWorld == net.minecraft.client.Minecraft.getInstance().level) return false;
            GraphicsTextureHelper.getInstance().removeDrawGraphic("train_" + id);
            return true;
        });
    }

    private final TrainClient train;
    private final List<LcdSlot> slots;
    private final Map<List<JsonObject>, DynamicModelHolder> baseModels;
    private final DynamicModelHolder[] carModels;
    private final TrainStatus trainStatus;
    private final LcdInfo lcdInfo;

    private record LcdSlot(JsonObject config, LcdCarFilter filter) {
    }

    public final TrainRendererBase baseRenderer;

    public FunctionalTrainRenderer(LcdInfo lcdInfo, TrainRendererBase base) {
        this.lcdInfo = lcdInfo;
        this.train = null;
        this.baseRenderer = base;

        this.slots = new ArrayList<>();
        if (lcdInfo != null) {
            for (JsonElement element : lcdInfo.slotsInfo().getAsJsonArray("slots")) {
                final JsonObject slot = element.getAsJsonObject();
                slots.add(new LcdSlot(slot, new LcdCarFilter(slot)));
            }
        }
        this.baseModels = new ConcurrentHashMap<>();
        this.carModels = null;
        this.trainStatus = null;
    }

    private FunctionalTrainRenderer(LcdInfo lcdInfo, TrainRendererBase base, TrainClient train,
                                    DynamicModelHolder[] carModels, TrainStatus trainStatus) {
        this.lcdInfo = lcdInfo;
        this.train = train;
        this.baseRenderer = base;
        this.slots = List.of();
        this.baseModels = null;
        this.carModels = carModels;
        this.trainStatus = trainStatus;
    }

    @Override
    public TrainRendererBase createTrainInstance(TrainClient trainClient) {
        var instanceBaseRenderer = baseRenderer == null ? null : baseRenderer.createTrainInstance(trainClient);

        TrainStatus trainStatus = new TrainStatus(trainClient);

        if (lcdInfo == null) {
            return new FunctionalTrainRenderer(null, instanceBaseRenderer, trainClient, null, trainStatus);
        }

        // JS 脚本 LCD：lcd.script 存在时优先走脚本绘制路径（参照 JCM 给列车挂 JS 的方式），
        // 否则走 Java 实现（LcdManager 注册表）
        final boolean scriptLcd = lcdInfo.hasScript();
        final ScriptHolderBase scriptHolder;
        final Map<String, Object> scriptInfo;
        final Map<String, Object> scriptExtra;
        // final：lambda（drawFn）内引用，Java 要求 effectively final；分支内各自赋值
        final LcdBase lcd = LcdManager.getInstance().getLcd(lcdInfo.id());
        if (scriptLcd) {
            scriptHolder = LcdScriptSupport.getHolder(lcdInfo.script());
            scriptInfo = LcdScriptSupport.buildInfo(lcdInfo);
            scriptExtra = LcdScriptSupport.buildExtraConfig(lcdInfo);
        } else {
            scriptHolder = null;
            scriptInfo = null;
            scriptExtra = null;
            if (lcd == null) {
                Main.LOGGER.warn("LCD not found for id: {}", lcdInfo.id());
                return new FunctionalTrainRenderer(lcdInfo, instanceBaseRenderer, trainClient, null, trainStatus);
            }
        }

        // 同一列车共享一张动态纹理，显示相同部件的车厢复用几何。
        final DynamicModelHolder[] carModels = new DynamicModelHolder[trainClient.trainCars];
        for (int car = 0; car < carModels.length; car++) {
            final List<JsonObject> selected = new ArrayList<>();
            for (LcdSlot slot : slots) {
                if (slot.filter().matches(car, carModels.length)) selected.add(slot.config());
            }
            if (!selected.isEmpty()) {
                carModels[car] = baseModels.computeIfAbsent(List.copyOf(selected),
                        key -> new DisplayHelper(lcdInfo.slotsInfo(), key).getBaseModel());
            }
        }

        final Map<String, Object> drawState = new HashMap<>();
        final long[] resourceVersion = {ResourceUtil.getReloadVersion()};
        GraphicsTextureHelper gtHelper = GraphicsTextureHelper.getInstance();
        // 绘制函数：路线数据未就绪时跳过绘制，保留纹理上一帧有效内容
        // 空回调：绘制完成后由 GraphicsTextureHelper.tick() 通过 needsUpload 机制统一上传，
        // 避免绘制线程和 tick 线程同时调用 gt.upload() 导致 NativeImage 数据竞争
        Runnable noopCallback = () -> {};

        GraphicsTextureHelper.DrawFunctionGt drawFn = (gt) -> {
            // 注意：不再清空 drawState——脚本侧用它做跨帧缓存（字体/时间），与 PIDS 脚本同语义；
            // Java 实现（MtrLcd）不使用 state，无副作用
            final long version = ResourceUtil.getReloadVersion();
            if (resourceVersion[0] != version) {
                drawState.clear();
                resourceVersion[0] = version;
            }
            trainStatus.updateRoute();
            if (scriptLcd) {
                // 脚本路径：每帧整张纹理重绘，无线路数据时由脚本自行处理
                if (scriptHolder == null) return; // ScriptManager 未初始化：保持纹理为空
                final Graphics2D scriptGraphics = (Graphics2D) gt.graphics.create();
                try {
                    ScriptManager.getInstance().requestRunFunctionSync(scriptHolder, noopCallback, "draw",
                            scriptGraphics, ProxyObject.fromMap(drawState), trainStatus,
                            LcdScriptSupport.toScriptValue(scriptInfo), LcdScriptSupport.toScriptValue(scriptExtra));
                } finally {
                    scriptGraphics.dispose();
                }
                if (!(lcd instanceof MtrLcd)) return;
            }

            final boolean routeReady = trainStatus.currentRoute != null && trainStatus.drawableRoute != null;
            for (LcdSlot slot : slots) {
                final JsonObject config = slot.config();
                final String name = config.get("name").getAsString();
                final boolean display = lcd instanceof MtrLcd && MtrLcd.isDisplay(name);
                // 脚本车型由脚本绘制门上屏；端屏在等候线路数据时也要绘制底色和标志。
                if (scriptLcd && !display) continue;
                if (!display && !routeReady && !lcd.canDrawWithoutRoute()) continue;
                final JsonArray area = config.getAsJsonArray("texArea");
                final int x = area.get(0).getAsInt(), y = area.get(1).getAsInt();
                final int w = area.get(2).getAsInt(), h = area.get(3).getAsInt();
                final Graphics2D slotGraphics = (Graphics2D) gt.graphics.create();
                try {
                    slotGraphics.clipRect(x, y, w, h);
                    lcd.draw(slotGraphics, trainStatus, lcdInfo, drawState, name, x, y, w, h, noopCallback);
                } finally {
                    slotGraphics.dispose();
                }
            }
        };
        // TrainClient 重建后（trainId 复用）替换绘制函数以捕获新的 trainStatus/drawState，保留旧纹理内容
        if (gtHelper.hasDrawGraphic("train_" + trainClient.id)) {
            gtHelper.replaceDrawFunction("train_" + trainClient.id, drawFn);
        } else {
            gtHelper.addDrawGraphicWithGt("train_" + trainClient.id,
                    new GraphicsTextureHelper.DrawInfo("train_lcd_" + trainClient.id,
                            lcdInfo.slotsInfo().getAsJsonArray("texSize").get(0).getAsInt(),
                            lcdInfo.slotsInfo().getAsJsonArray("texSize").get(1).getAsInt(),
                            false, true),
                    drawFn);
        }
        final FunctionalTrainRenderer instance = new FunctionalTrainRenderer(lcdInfo, instanceBaseRenderer, trainClient, carModels, trainStatus);
        ACTIVE_LCDS.put(trainClient.id, instance);
        return instance;
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

        if (posAverage == null) {
            // 列车在可视范围外，不触发 LCD 绘制。
            // 若在此处调用 getGraphics 触发绘制，MTR 可能未维护完整的路线数据，
            // 导致 updateRoute() 返回空路线，将"无线路信息"写入纹理覆盖掉正确内容。
            return;
        }


        try {
            matrices.translate(x, y, z);
            PoseStackUtil.rotY(matrices, (float) Math.PI + yaw);
            PoseStackUtil.rotX(matrices, hasPitch ? pitch : 0);
        } catch (Exception e) {
            Main.LOGGER.error("Failed to draw train: ", e);
            return;
        }
        try {
            // 车体滚转（P5-6）：车体几何由 baseRenderer 画（本装饰器一个车体顶点都不画），但它画的时候
            // 用的状态键是本装饰器实例（TrainClient.trainRenderer 就是它），所以在那里查得到角度。
            // LCD 覆盖层挂在与车体同一个局部系里，因此这里补一次滚转是物理正确的（屏随车斜）。
            // 内部有「滚转为 0 时一个矩阵运算都不做」的保证，普通轨道上与原生逐位一致。
            RailTrainRollHelper.applyBodyRoll(matrices);
            final int light = LightTexture.pack(world.getBrightness(LightLayer.BLOCK, posAverage), world.getBrightness(LightLayer.SKY, posAverage));
            Matrix4f drawPose = new Matrix4f(matrices.last().pose());
            final DynamicModelHolder holder = carModels != null && carIndex >= 0 && carIndex < carModels.length
                    ? carModels[carIndex] : null;
            final ModelCluster model = holder == null ? null : holder.getUploadedModel();
            if (model != null) {
                GraphicsTexture texture = GraphicsTextureHelper.getInstance().getGraphics("train_" + train.id);
                if (texture != null) {
                    if (!texture.identifier.equals(materialTexture)) {
                        materialModels.clear();
                        materialTexture = texture.identifier;
                    }
                    // 材质按列车独立，避免同型号列车覆盖已经排队的贴图。
                    final ModelCluster materialModel = materialModels.computeIfAbsent(model, source -> {
                        final ModelCluster copy = source.copyForMaterialChanges();
                        copy.replaceAllTexture(texture.identifier);
                        return copy;
                    });
                    new AbstractDrawCalls.ClusterDrawCall(materialModel, Matrix4f.IDENTITY).commit(MainClient.drawScheduler, drawPose, light);
                }
            }
        } catch (Exception e) {
            Main.LOGGER.error("", e);
        } finally {
            // 清理车体滚转的 ThreadLocal（P5-6）：本方法可能在「rotateX 重定向不生效」的
            // 构建上执行（例如本模组的 mixin 被别的模组挤掉），那时 BODY_ROLL 不会被消费，
            // 残值会污染同一线程后续的渲染。清理本身在任何情况下都无害。
            RailTrainRollHelper.clearBodyRoll();
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
