package com.fangsu.render.scripting.util;

import com.fangsu.Main;
import com.fangsu.MainClient;
import com.fangsu.render.sowcer.util.GlStateTracker;
import com.fangsu.render.sowcerext.model.ModelCluster;
import com.fangsu.render.sowcerext.model.RawModel;
import com.fangsu.render.sowcerext.reuse.ModelManager;
import com.mojang.blaze3d.systems.RenderSystem;

public class DynamicModelHolder {

    private ModelCluster uploadedModel;

    /**
     * 是否由 {@code ResourceUtil} 的共享缓存持有。
     * <p>
     * 共享 holder 可能被多个方块实例同时引用，方块销毁时**不能**关闭它，
     * 必须由缓存统一回收（见 {@code ResourceUtil.init}）。否则一个方块被拆掉会连带
     * 让其他使用同一模型的方块丢失几何。
     */
    private volatile boolean shared = false;

    /**
     * 标记为共享缓存持有。仅供 {@code ResourceUtil} 内部调用。
     */
    public DynamicModelHolder markShared() {
        this.shared = true;
        return this;
    }

    public boolean isShared() {
        return shared;
    }

    public void uploadLater(RawModel rawModel) {
        // 专用服务端不存在客户端渲染类，且模型只可能在客户端上传；
        // 直接返回，避免触碰 RenderSystem（见 close() 的说明）。
        if (!Main.isPhysicalClient()) return;
        RawModel finalRawModel = rawModel.copyForMaterialChanges();
        finalRawModel.sourceLocation = null;
        RenderSystem.recordRenderCall(() -> {
            boolean needProtection = !GlStateTracker.isStateProtected;
            if (needProtection) GlStateTracker.capture();
            ModelCluster lastUploadedModel = uploadedModel;
            uploadedModel = new ModelCluster(finalRawModel, ModelManager.DEFAULT_MAPPING, MainClient.modelManager);
            if (lastUploadedModel != null) lastUploadedModel.close();
            if (needProtection) GlStateTracker.restore();
        });
    }

    public ModelCluster getUploadedModel() {
        return uploadedModel;
    }

    public void close() {
        // 服务端崩溃修复：方块实体的 whenDisposing/setRemoved 在服务端同样会被调用，
        // 而专用服务端没有 com.mojang.blaze3d.systems.RenderCall 这个客户端专有类
        // （Fabric 运行时为 net.minecraft.class_4573）。只要执行到下一行的
        // recordRenderCall，JVM 在链接它接收的 lambda（invokedynamic）时就必须加载该类，
        // 于是抛 NoClassDefFoundError 打挂服务端 tick 循环（拆掉一个方块必现）。
        // 专用服务端既没有 GL 上下文也不可能上传过模型，直接返回即可。
        if (!Main.isPhysicalClient()) return;
        RenderSystem.recordRenderCall(() -> {
            if (uploadedModel != null) {
                uploadedModel.close();
                uploadedModel = null;
            }
        });
    }

    /**
     * 仅当本 holder 由调用方独占持有时才关闭。
     * 共享缓存的 holder 由 {@code ResourceUtil} 统一回收，方块销毁时调用本方法即可安全跳过。
     */
    public void closeIfOwned() {
        if (!shared) close();
    }

    /**
     * 是否仍然持有已上传的模型。用于调试与生命周期判断。
     */
    public boolean hasUploadedModel() {
        return uploadedModel != null;
    }
}