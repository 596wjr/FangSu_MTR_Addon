package com.fangsu.events;

import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;

public class ModEvents {
    public static void init() {
        PlayerEvent.PLAYER_JOIN.register(JoinInMessage::sendJoinMessage);
    }
}
