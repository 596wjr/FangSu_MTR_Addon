package com.fangsu.events;

import com.fangsu.MainClient;
import com.fangsu.mtr.DrawableRoute;
import com.fangsu.utils.GraphicsTextureHelper;
import dev.architectury.event.events.common.PlayerEvent;

public class ModEvents {
    public static void init() {
        PlayerEvent.PLAYER_JOIN.register(JoinInMessage::sendJoinMessage);
        PlayerEvent.PLAYER_JOIN.register(JoinInClearCache::clearCache);

        // 资源重载时清除缓存的绘制数据和路线缓存，防止跨世界数据错乱
        MainClient.addResourceRunnable(JoinInClearCache::clearCache);
    }
}
