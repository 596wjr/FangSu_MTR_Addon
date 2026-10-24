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
            try {
                uploadedModel = new ModelCluster(finalRawModel, ModelManager.DEFAULT_MAPPING, MainClient.modelManager);
            } catch (Throwable t) {
                // <b>热修（2026-07）</b>：旧实现是「capture → 上传 → restore」的直线写法，没有 try/finally。
                // 只要上传抛一次异常，GlStateTracker.restore() 就被跳过，捕获状态永久残留 →
                // 之后所有走这条渲染栈的方速方块全部不渲染（用户报「其他方速方块也不渲染了」正是这个）。
                // 这里：① restore 必定在 finally 执行；② 失败时把旧模型放回、不清空也不 close；
                // ③ 打全栈日志便于定位（只在失败时打，没有性能问题）。
                Main.LOGGER.warn("[fangsu] model upload failed (meshes={})",
                        finalRawModel == null || finalRawModel.meshList == null ? -1 : finalRawModel.meshList.size(), t);
                uploadedModel = lastUploadedModel;
                return;
            } finally {
                if (needProtection) GlStateTracker.restore();
            }
            if (lastUploadedModel != null) lastUploadedModel.close();
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