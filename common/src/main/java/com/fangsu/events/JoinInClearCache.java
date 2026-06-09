package com.fangsu.events;

import com.fangsu.mtr.DrawableRoute;
import com.fangsu.utils.GraphicsTextureHelper;
import net.minecraft.server.level.ServerPlayer;

public class JoinInClearCache {
    public static void clearCache() {
        GraphicsTextureHelper.getInstance().removeDrawGraphicsByPrefix("train_");
        DrawableRoute.clearCache();
    }

    public static void clearCache(ServerPlayer serverPlayer) {
        clearCache();
    }
}
