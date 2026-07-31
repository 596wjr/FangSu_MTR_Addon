package com.fangsu.forge.client;

import com.fangsu.Main;
import com.fangsu.MainClient;
import com.fangsu.client.ClientHooks;
import com.fangsu.client.ClientHooksImpl;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MainForgeClient {

    private static boolean hooksInitialized = false;

    private static void initializeHooks() {
        Main.LOGGER.info("[FangSu] Initializing hooks");
        if (!hooksInitialized) {
            ClientHooks.OPEN_OBJ_BLOCK_CONFIG_SCREEN = ClientHooksImpl::openObjBlockConfigScreen;
            ClientHooks.OPEN_OBJ_SIGN_SCREEN = ClientHooksImpl::openSignConfigScreen;
            ClientHooks.OPEN_TICKET_MACHINE_SCREEN = ClientHooksImpl::openTicketMachineScreen;
            ClientHooks.OPEN_PLATFORM_SELECT_SCREEN = ClientHooksImpl::openPlatformSelectScreen;
            ClientHooks.OPEN_ROUTE_SELECT_SCREEN = ClientHooksImpl::openRouteSelectScreen;
            ClientHooks.OPEN_STATION_SELECT_SCREEN = ClientHooksImpl::openStationSelectScreen;
            ClientHooks.OPEN_SCREENDOOR_CENTRAL_CONTROL_SCREEN = ClientHooksImpl::openScreendoorCentralControlScreen;
            ClientHooks.OPEN_ROTATING_RAIL_CONFIG_SCREEN = ClientHooksImpl::openRotatingRailConfigScreen;
            hooksInitialized = true;
        }
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        initializeHooks();

        event.registerReloadListener((ResourceManagerReloadListener) rm -> {
            try {
                Main.LOGGER.info("[FangSu] Reloading resources...");

                // RegisterClientReloadListenersEvent 将监听器注册到客户端的
                // ReloadableResourceManager 上，每次客户端资源包重载时都会自动调用。
                // rm 是包含所有已启用资源包（包括模组内置资源）的正确 ResourceManager。
                MainClient.initResources(rm);
            } catch (Exception e) {
                Main.LOGGER.error("[FangSu] Failed to reload resources", e);
            }
        });

        Main.LOGGER.info("[FangSu] Client resource reload listener registered");
    }
}