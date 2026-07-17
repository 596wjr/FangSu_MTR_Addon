package com.fangsu.fabric.client;

import com.fangsu.MainClient;
import com.fangsu.client.ClientHooks;
import com.fangsu.client.ClientHooksImpl;
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
        MainClient.initClient();
        fabricClientInit();
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

    private void fabricClientInit() {
        ClientHooks.OPEN_OBJ_BLOCK_CONFIG_SCREEN = ClientHooksImpl::openObjBlockConfigScreen;
        ClientHooks.OPEN_OBJ_SIGN_SCREEN = ClientHooksImpl::openSignConfigScreen;
        ClientHooks.OPEN_TICKET_MACHINE_SCREEN = ClientHooksImpl::openTicketMachineScreen;
        ClientHooks.OPEN_PLATFORM_SELECT_SCREEN = ClientHooksImpl::openPlatformSelectScreen;
        ClientHooks.OPEN_ROUTE_SELECT_SCREEN = ClientHooksImpl::openRouteSelectScreen;
        ClientHooks.OPEN_STATION_SELECT_SCREEN = ClientHooksImpl::openStationSelectScreen;
        ClientHooks.OPEN_SCREENDOOR_CENTRAL_CONTROL_SCREEN = ClientHooksImpl::openScreendoorCentralControlScreen;
        ClientHooks.OPEN_ROTATING_RAIL_CONFIG_SCREEN = ClientHooksImpl::openRotatingRailConfigScreen;
    }

    private void initResources(ResourceManager resourceManager) {
        MainClient.initResources(resourceManager);
    }
}
