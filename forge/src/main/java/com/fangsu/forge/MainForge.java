package com.fangsu.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.fangsu.Main;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.DistExecutor;

@Mod(Main.MOD_ID)
@SuppressWarnings("removal")
public final class MainForge {
    public MainForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(Main.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        Main.init();

        // 外层判断也覆盖开发环境：服务端不会执行 SafeReferent 验证、创建客户端方法引用。
        if (FMLEnvironment.dist == Dist.CLIENT) {
            DistExecutor.safeRunWhenOn(Dist.CLIENT,
                    () -> com.fangsu.forge.client.FangSuConfigRegistration::register);
        }
    }
}
