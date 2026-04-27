package com.fangsu.render;

import mtr.data.TrainClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class RenderUtil {
    public static boolean shouldSkipRenderTrain(TrainClient train) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            return train.isPlayerRiding(player);
        }
        return false;
    }
}
