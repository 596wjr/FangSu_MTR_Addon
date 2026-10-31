package com.fangsu.client;

import java.util.ArrayList;
import java.util.List;

/**
 * 两侧都能安全加载的客户端引导登记表。
 * <p>
 * 扩展模组在自身的 {@code init()}（专用服务端也会执行）里用本类登记客户端资源初始化回调，
 * 客户端资源重载时由 {@link com.fangsu.MainClient#initResources} 统一执行。
 * <p>
 * 不要改成直接调用 {@code MainClient.addResourceRunnable}：{@code MainClient} 带
 * {@code @Environment(EnvType.CLIENT)}，在专用服务端加载它会被 Fabric 直接拒绝并打挂启动。
 */
public final class ClientBootstrap {
    private static final List<Runnable> RESOURCE_INIT_RUNNABLES = new ArrayList<>();

    private ClientBootstrap() {
    }

    public static void addResourceRunnable(Runnable runnable) {
        RESOURCE_INIT_RUNNABLES.add(runnable);
    }

    public static List<Runnable> resourceInitRunnables() {
        return RESOURCE_INIT_RUNNABLES;
    }
}
