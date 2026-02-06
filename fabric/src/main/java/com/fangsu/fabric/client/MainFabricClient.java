package com.fangsu.fabric.client;

import com.fangsu.Main;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public final class MainFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
        Main.initClient();
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        initResources(resourceManager);
                    }

                    @Override
                    public ResourceLocation getFabricId() {
                        return new ResourceLocation("fangsu", "custom_blocks_loader");
                    }
                }
        );
    }

    protected void fabricClientInit() {
    }

    private void initResources(ResourceManager resourceManager) {
        Main.initResources(resourceManager);
    }
}
