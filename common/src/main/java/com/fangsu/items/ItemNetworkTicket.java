package com.fangsu.items;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ticketSystem.v2.TicketCards;
import com.fangsu.ticketSystem.v2.TicketDefinitions.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import java.util.List;

/** 日票和联程票共用物品行为，权益由服务端签发快照保存。 */
public class ItemNetworkTicket extends Item {
    public ItemNetworkTicket() { super(com.fangsu.utils.RegisterUtil.tabProps(new Item.Properties().stacksTo(1))); }
    public static void tooltip(ItemStack stack, List<Component> lines) {
        if (stack.getTag() == null || !stack.getTag().contains(TicketCards.KEY)) return;
        try {
            Card card = TicketCards.read(stack, null);
            lines.add(ComponentHelper.literal(card.product.name));
            lines.add(ComponentHelper.literal("可用网络："+String.join("、", card.product.networks)));
            for (Badge badge : card.product.badges) lines.add(ComponentHelper.literal("联合授权："+badge.name));
            if (card.product.kind == Kind.IC) lines.add(ComponentHelper.literal("余额："+card.balance+"元"));
            if (card.product.kind == Kind.SINGLE || card.product.kind == Kind.ITINERARY) lines.add(ComponentHelper.literal("票额："+card.allowance+"元"));
            if (card.product.kind == Kind.ITINERARY) lines.add(ComponentHelper.literal("剩余权益："+(card.product.legs.size()-card.used.size())));
            lines.add(ComponentHelper.literal(card.journey == null ? "未进站" : "已进站："+card.journey.entry.name));
            if (card.activated > 0 && (card.product.kind == Kind.DAY || card.product.validHours > 0))
                lines.add(ComponentHelper.literal("有效至："+new java.util.Date(card.activated+(card.product.kind==Kind.DAY?24:card.product.validHours)*3600000L)));
            lines.add(ComponentHelper.literal("编号："+card.serial));
        } catch (RuntimeException ex) { lines.add(ComponentHelper.literal("票卡数据无效")); }
    }
    @Override public void appendHoverText(ItemStack stack, Level world, List<Component> lines, TooltipFlag flag) { tooltip(stack, lines); }
}
