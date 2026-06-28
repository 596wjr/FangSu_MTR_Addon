package com.fangsu.forge.client;

import com.fangsu.Main;
import com.fangsu.MainClient;
import com.fangsu.client.ClientHooks;
import com.fangsu.client.ClientHooksImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MainForgeClient {

    private static boolean hooksInitialized = false;

    private static void initializeHooks() {
        if (!hooksInitialized) {
            ClientHooks.OPEN_OBJ_BLOCK_CONFIG_SCREEN = ClientHooksImpl::openObjBlockConfigScreen;
            ClientHooks.OPEN_OBJ_SIGN_SCREEN = ClientHooksImpl::openSignConfigScreen;
            ClientHooks.OPEN_TICKET_MACHINE_SCREEN = ClientHooksImpl::openTicketMachineScreen;
            ClientHooks.OPEN_PLATFORM_SELECT_SCREEN = ClientHooksImpl::openPlatformSelectScreen;
            ClientHooks.OPEN_ROUTE_SELECT_SCREEN = ClientHooksImpl::openRouteSelectScreen;
            ClientHooks.OPEN_STATION_SELECT_SCREEN = ClientHooksImpl::openStationSelectScreen;
            ClientHooks.OPEN_SCREENDOOR_CENTRAL_CONTROL_SCREEN = ClientHooksImpl::openScreendoorCentralControlScreen;
            hooksInitialized = true;
        }
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        initializeHooks();

        event.addListener((ResourceManagerReloadListener) eventRm -> {
            try {
                Main.LOGGER.info("[FangSu] Reloading resources...");

                // 使用客户端的 ResourceManager（始终包含模组内置资源），
                // 而非事件提供的 MultiPackResourceManager（可能不包含 assets/fangsu/ 等模组资源）
                var rm = Minecraft.getInstance().getResourceManager();

                MainClient.initResources(rm);
            } catch (Exception e) {
                Main.LOGGER.error("[FangSu] Failed to reload resources", e);
            }
        });

        Main.LOGGER.info("[FangSu] Resource reload listener registered");
    }
}