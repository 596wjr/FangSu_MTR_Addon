package com.fangsu.forge.client;

import com.fangsu.ui.FangSuConfigScreen;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//#if MC_VERSION >= 11900
import net.minecraftforge.client.ConfigScreenHandler;
//#else
//$$ import net.minecraftforge.client.ConfigGuiHandler;
//#endif

/** 仅从客户端分支调用，注册 Forge 模组列表的配置按钮。 */
@OnlyIn(Dist.CLIENT)
public final class FangSuConfigRegistration {
    private FangSuConfigRegistration() { }

    public static void register() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(FangSuConfigRegistration::onClientSetup);
        //#if MC_VERSION >= 11900
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new FangSuConfigScreen(parent)));
        //#else
        //$$ ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class,
        //$$         () -> new ConfigGuiHandler.ConfigGuiFactory((minecraft, parent) -> new FangSuConfigScreen(parent)));
        //#endif
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                com.fangsu.MainClient.initClient();
            } catch (Exception e) {
                com.fangsu.Main.LOGGER.error("[FangSu] Failed to initialize client", e);
            }
        });
    }
}
