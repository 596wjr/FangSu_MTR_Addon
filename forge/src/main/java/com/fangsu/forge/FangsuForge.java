package com.fangsu.forge;

import com.fangsu.Fangsu;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Fangsu.MOD_ID)
public final class FangsuForge {
    public FangsuForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(Fangsu.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        Fangsu.init();
    }
}
