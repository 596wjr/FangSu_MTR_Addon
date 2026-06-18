package com.fangsu.forge.client;

import com.fangsu.Main;
import com.fangsu.MainClient;
import com.fangsu.client.ClientHooks;
import com.fangsu.client.ClientHooksImpl;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MainForgeClient {
    static {
        ClientHooks.OPEN_OBJ_BLOCK_CONFIG_SCREEN = ClientHooksImpl::openObjBlockConfigScreen;
        ClientHooks.OPEN_OBJ_SIGN_SCREEN = ClientHooksImpl::openSignConfigScreen;
        ClientHooks.OPEN_TICKET_MACHINE_SCREEN = ClientHooksImpl::openTicketMachineScreen;
        ClientHooks.OPEN_PLATFORM_SELECT_SCREEN = ClientHooksImpl::openPlatformSelectScreen;
        ClientHooks.OPEN_ROUTE_SELECT_SCREEN = ClientHooksImpl::openRouteSelectScreen;
        ClientHooks.OPEN_STATION_SELECT_SCREEN = ClientHooksImpl::openStationSelectScreen;
        ClientHooks.OPEN_SCREENDOOR_CENTRAL_CONTROL_SCREEN = ClientHooksImpl::openScreendoorCentralControlScreen;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier stage, ResourceManager resourceManager,
                                                   Executor backgroundExecutor, Executor gameExecutor) {
                return stage.wait(null).thenRunAsync(() -> {
                    try {
                        MainClient.initResources(resourceManager);
                    } catch (Exception e) {
                        Main.LOGGER.error("Failed to reload FangSu resources", e);
                    }
                }, gameExecutor);
            }
        });
    }
}
