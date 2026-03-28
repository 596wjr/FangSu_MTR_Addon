package com.fangsu.events;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;

public class JoinInMessage {

    private static final String FEEDBACK_URL = "https://github.com/596wjr/FangSu_MTR_Addon/issues";

    protected static void sendJoinMessage(ServerPlayer player) {
        // 创建可点击的链接组件 - 使用 translatable
        Component link = Component.translatable("msg.fangsu.join.link")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.OPEN_URL,
                                FEEDBACK_URL
                        ))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("msg.fangsu.join.link.hover")
                                        .withStyle(ChatFormatting.YELLOW)
                        ))
                );

        // 构建完整消息 - 使用 translatable
        Component message = Component.empty()
                .append(Component.translatable("msg.fangsu.join.prefix")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.translatable("msg.fangsu.join.warning")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"))
                .append(Component.translatable("msg.fangsu.join.feedback.prefix")
                        .withStyle(ChatFormatting.GRAY))
                .append(link)
                .append(Component.translatable("msg.fangsu.join.feedback.suffix")
                        .withStyle(ChatFormatting.GRAY));

        // 发送消息给玩家
        player.sendSystemMessage(message);
    }
}