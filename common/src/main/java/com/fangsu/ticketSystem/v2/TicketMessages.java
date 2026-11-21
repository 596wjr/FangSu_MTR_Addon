package com.fangsu.ticketSystem.v2;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.network.chat.Component;

/** 服务端保留翻译键，交给各玩家客户端按自己的语言显示。 */
public final class TicketMessages {
    public static Component text(String value) {
        if(value==null)return ComponentHelper.translatable("msg.fangsu.ticketing.invalid_request");
        return value.startsWith("msg.fangsu.ticketing.")||value.startsWith("ui.fangsu.ticketing.")
                ?ComponentHelper.translatable(value):ComponentHelper.literal(value);
    }
    public static Component error(Throwable exception){return text(exception.getMessage());}
    private TicketMessages(){ }
}
