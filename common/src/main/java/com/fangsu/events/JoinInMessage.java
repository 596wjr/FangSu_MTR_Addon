package com.fangsu.events;

import com.fangsu.announcements.AnnouncementClient;
import com.fangsu.announcements.provider.ClientTokens;
import com.fangsu.announcements.ui.TtsProviderScreen;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;

import java.io.IOException;

/** 每次进入单人存档或连接服务器时，在本地检查当前报站服务。 */
public final class JoinInMessage {
    private static final ClickEvent OPEN_TTS_SETTINGS = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fangsu:open_tts_settings");
    private static ClientPacketListener lastConnection;

    private JoinInMessage() { }

    public static void tick(Minecraft client) {
        var connection = client.getConnection();
        if (connection == null) { lastConnection = null; return; }
        if (client.player == null || client.level == null || lastConnection == connection) return;
        lastConnection = connection;
        var settings = AnnouncementClient.settings();
        try {
            String key = ClientTokens.resolve(settings.provider, settings.accountNamespace);
            if (key != null && !key.isBlank()) return;
        } catch (IOException ignored) { /* 无法读取本地凭据时也提供设置入口。 */ }
        var link = ComponentHelper.translatable("msg.fangsu.join.tts_configure").withStyle(style ->
                style.withColor(ChatFormatting.GREEN).withUnderlined(true).withClickEvent(OPEN_TTS_SETTINGS));
        client.player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.join.tts_missing", link)
                .withStyle(ChatFormatting.YELLOW), false);
    }

    public static boolean handleClick(Style style) {
        // 只处理由本客户端创建的链接，服务器发来的同名聊天命令保持原有行为。
        if (style == null || style.getClickEvent() != OPEN_TTS_SETTINGS) return false;
        var client = Minecraft.getInstance();
        client.setScreen(new TtsProviderScreen(null));
        return true;
    }
}
