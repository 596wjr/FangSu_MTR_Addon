package com.fangsu.events;

import dev.architectury.event.events.common.PlayerEvent;

public class ModEvents {
    public static void init() {
        PlayerEvent.PLAYER_JOIN.register(JoinInMessage::sendJoinMessage);
        PlayerEvent.PLAYER_JOIN.register(JoinInClearCache::clearCache);
    }
}
