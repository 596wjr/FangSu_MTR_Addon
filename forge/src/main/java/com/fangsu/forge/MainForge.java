package com.fangsu.forge;

import com.fangsu.MainClient;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.fangsu.Main;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Main.MOD_ID)
@SuppressWarnings("removal")
public final class MainForge {
    public MainForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(Main.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        Main.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        }
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                // 客户端初始化（只运行一次，注册 BlockEntityRenderers 等）
                MainClient.initClient();
            } catch (Exception e) {
                Main.LOGGER.error("[FangSu] Failed to initialize client", e);
            }
        });
    }
}
