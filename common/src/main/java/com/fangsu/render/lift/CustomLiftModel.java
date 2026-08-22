package com.fangsu.render.lift;

import com.fangsu.Main;
import com.fangsu.MainClient;
import com.fangsu.render.scripting.util.DynamicModelHolder;
import com.fangsu.render.sowcer.math.Matrix4f;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.model.integration.BufferSourceProxy;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时自定义电梯模型：加载 properties.json 与模型源，拼装成轿厢并上传 GPU，
 * 在电梯渲染回调里把静态部位 + 滑动门扇写进 {@link MultiBufferSource}（Blaze 路径）。
 */
public class CustomLiftModel {

    private static final Map<String, CustomLiftModel> CACHE = new HashMap<>();

    private final ResourceLocation propertiesLocation;
    private final ResourceLocation modelLocation;
    private final ResourceLocation textureLocation;
    private LiftAssemblyProperties properties;
    private final DynamicModelHolder bodyHolder = new DynamicModelHolder();
    private final DynamicModelHolder doorLeftHolder = new DynamicModelHolder();
    private final DynamicModelHolder doorRightHolder = new DynamicModelHolder();
    private boolean loaded = false;
    private int cachedWidth = -1, cachedDepth = -1, cachedHeight = -1;
    private boolean lastGoingUp, lastGoingDown, lastStopped;
    private List<LiftModelAssembler.DisplayInfo> displays = java.util.Collections.emptyList();

    /** model/texture 可来自电梯条目（mtr_custom_resources.json），也可为 null（回退用 properties.json 里的）。 */
    public static CustomLiftModel get(ResourceLocation propertiesLocation, ResourceLocation model, ResourceLocation texture) {
        final String key = propertiesLocation + "|" + model + "|" + texture;
        return CACHE.computeIfAbsent(key, k -> new CustomLiftModel(propertiesLocation, model, texture));
    }

    private CustomLiftModel(ResourceLocation propertiesLocation, ResourceLocation model, ResourceLocation texture) {
        this.propertiesLocation = propertiesLocation;
        this.modelLocation = model;
        this.textureLocation = texture;
    }

    public LiftAssemblyProperties getProperties() {
        ensureLoaded(1, 1, 1, LiftModelAssembler.LiftConditionContext.NORMAL);
        return properties;
    }

    private void ensureLoaded(int width, int depth, int height, LiftModelAssembler.LiftConditionContext condition) {
        boolean condSame = condition != null
                && condition.goingUp == lastGoingUp
                && condition.goingDown == lastGoingDown
                && condition.stopped == lastStopped;
        if (loaded && width == cachedWidth && depth == cachedDepth && height == cachedHeight && condSame) {
            return;
        }
        try {
            JsonElement raw = ResourceUtil.loadAsJSON(propertiesLocation);
            if (raw == null || !raw.isJsonObject() || raw.getAsJsonObject().size() == 0) {
                Main.LOGGER.error("[FangSu] properties.json empty/not found: {}", propertiesLocation);
                loaded = true;
                return;
            }
            LiftAssemblyProperties newProps = new LiftAssemblyProperties(raw.getAsJsonObject());
            ResourceLocation model = modelLocation;
            if (model == null) {
                Main.LOGGER.error("[FangSu] custom lift has no model: {}", propertiesLocation);
                loaded = true;
                return;
            }
            Map<String, RawModel> groups;
            if (isBbmodel(model)) {
                groups = BbModelLoader.loadModels(model);
                if (textureLocation != null) {
                    for (RawModel g : groups.values()) {
                        g.replaceAllTexture(textureLocation);
                    }
                }
            } else {
                // obj：贴图/材质由 obj 的 mtllib -> mtl 自动关联，texture 字段（.mtl）仅为标识。
                groups = ResourceUtil.loadPartedModel(model, false);
            }

            this.properties = newProps;
            LiftModelAssembler.AssembledLift assembled = LiftModelAssembler.assemble(groups, newProps, width, depth, height, condition);
            bodyHolder.uploadLater(assembled.body);
            if (assembled.doorLeft != null) doorLeftHolder.uploadLater(assembled.doorLeft);
            if (assembled.doorRight != null) doorRightHolder.uploadLater(assembled.doorRight);
            this.displays = assembled.displays;
            cachedWidth = width;
            cachedDepth = depth;
            cachedHeight = height;
            if (condition != null) {
                lastGoingUp = condition.goingUp;
                lastGoingDown = condition.goingDown;
                lastStopped = condition.stopped;
            }
            loaded = true;
        } catch (Exception e) {
            Main.LOGGER.error("[FangSu] Failed to load custom lift {}: {}", propertiesLocation, e.getMessage());
            loaded = true;
        }
    }

    private static boolean isBbmodel(ResourceLocation model) {
        return model.getPath().endsWith(".bbmodel");
    }

    /**
     * 渲染轿厢。
     *
     * @param proxy      写面的 BufferSourceProxy（包住电梯渲染的 vertexConsumers）
     * @param basePose   电梯局部系基矩阵（RenderTrainsMixin 已变换到电梯局部系）
     * @param light      光照
     * @param doorValue  门开度 [0,1]，0=关门
     */
    public void render(BufferSourceProxy proxy, Matrix4f basePose, int light, float doorValue) {
        // 真实尺寸从渲染回调处暂用占位；实际由 renderWithSize 传入
        renderWithSize(proxy, basePose, light, doorValue, cachedWidth, cachedDepth, cachedHeight, LiftModelAssembler.LiftConditionContext.NORMAL);
    }

    /**
     * 渲染轿厢（带尺寸与电梯状态条件）。
     */
    public void renderWithSize(BufferSourceProxy proxy, Matrix4f basePose, int light, float doorValue,
                               int width, int depth, int height, LiftModelAssembler.LiftConditionContext condition) {
        ensureLoaded(width, depth, height, condition);

        ModelCluster body = bodyHolder.getUploadedModel();
        if (body != null) {
            body.enqueueOpaqueBlaze(proxy, basePose, light, MainClient.drawContext);
            body.enqueueTranslucentBlaze(proxy, basePose, light, MainClient.drawContext);
        }

        ModelCluster doorLeft = doorLeftHolder.getUploadedModel();
        ModelCluster doorRight = doorRightHolder.getUploadedModel();
        if (doorLeft != null) {
            Matrix4f pose = basePose.copy();
            pose.translate(-doorValue * getDoorSlide(), 0, 0);
            doorLeft.enqueueOpaqueBlaze(proxy, pose, light, MainClient.drawContext);
            doorLeft.enqueueTranslucentBlaze(proxy, pose, light, MainClient.drawContext);
        }
        if (doorRight != null) {
            Matrix4f pose = basePose.copy();
            pose.translate(doorValue * getDoorSlide(), 0, 0);
            doorRight.enqueueOpaqueBlaze(proxy, pose, light, MainClient.drawContext);
            doorRight.enqueueTranslucentBlaze(proxy, pose, light, MainClient.drawContext);
        }
    }

    /** 门扇最大滑动距离（模型单位）。 */
    private float getDoorSlide() {
        return properties == null ? 8F : properties.getCellSize();
    }

    /** 楼层屏显示部位信息（供渲染文字）。 */
    public List<LiftModelAssembler.DisplayInfo> getDisplays() {
        return displays;
    }

    public static void clearCache() {
        CACHE.values().forEach(CustomLiftModel::close);
        CACHE.clear();
    }

    private void close() {
        bodyHolder.close();
        doorLeftHolder.close();
        doorRightHolder.close();
    }
}
