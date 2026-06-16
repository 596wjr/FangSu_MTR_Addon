package com.fangsu.events;

import com.fangsu.Main;
import com.fangsu.MainClient;
import com.fangsu.mtr.DrawableRoute;
import com.fangsu.utils.GraphicsTextureHelper;
import dev.architectury.event.events.common.PlayerEvent;

public class ModEvents {
    public static void init() {
        PlayerEvent.PLAYER_JOIN.register(JoinInMessage::sendJoinMessage);
        PlayerEvent.PLAYER_JOIN.register(JoinInClearCache::clearCache);
    }
}
