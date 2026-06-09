package com.fangsu.events;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;

public class JoinInMessage {

    private static final String FEEDBACK_URL = "https://github.com/596wjr/FangSu_MTR_Addon/issues";

    protected static void sendJoinMessage(ServerPlayer player) {
        // 鍒涘缓鍙偣鍑荤殑閾炬帴缁勪欢 - 浣跨敤 translatable
        Component link = ComponentHelper.translatable("msg.fangsu.join.link")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.OPEN_URL,
                                FEEDBACK_URL
                        ))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                ComponentHelper.translatable("msg.fangsu.join.link.hover")
                                        .withStyle(ChatFormatting.YELLOW)
                        ))
                );

        // Build complete message
        Component message = ComponentHelper.empty()
                .append(ComponentHelper.translatable("msg.fangsu.join.prefix")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(ComponentHelper.translatable("msg.fangsu.join.warning")
                        .withStyle(ChatFormatting.YELLOW))
                .append(ComponentHelper.literal("\n"))
                .append(ComponentHelper.translatable("msg.fangsu.join.feedback.prefix")
                        .withStyle(ChatFormatting.GRAY))
                .append(link)
                .append(ComponentHelper.translatable("msg.fangsu.join.feedback.suffix")
                        .withStyle(ChatFormatting.GRAY));

        // Send message to player
        //#if MC_VERSION >= 11900
        player.sendSystemMessage(message);
        //#else
        //$$ player.displayClientMessage(message, false);
        //#endif
    }
}