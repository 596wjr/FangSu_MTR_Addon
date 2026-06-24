package com.fangsu.mixin;

import com.fangsu.train.FunctionalCustomTrains;
import com.fangsu.utils.ResourceUtil;
import mtr.client.CustomResources;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomResources.class)
public class CustomResourcesMixin {
    @Inject(method = "reload", at = @At("TAIL"))
    private static void reload(ResourceManager manager, CallbackInfo ci) {
        if (ResourceUtil.isInitialized()) {
            FunctionalCustomTrains.init(manager);
        } else {
            com.fangsu.Main.LOGGER.debug("CustomResourcesMixin: ResourceUtil not initialized yet, skipping FunctionalCustomTrains.init (will be called later by MainClient.initResources)");
        }
    }
}
