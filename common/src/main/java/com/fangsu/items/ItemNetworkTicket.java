package com.fangsu.items;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ticketSystem.v2.TicketCards;
import com.fangsu.ticketSystem.v2.TicketDefinitions.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import java.util.List;

/** 名称与权益来自已售票快照；提示文字交给客户端按游戏语言翻译。 */
public class ItemNetworkTicket extends Item {
    public ItemNetworkTicket() { super(new Item.Properties().stacksTo(1)); }
    public static Component productName(ItemStack stack,Component fallback) {
        if(stack.getTag()==null||!stack.getTag().contains(TicketCards.KEY))return fallback;
        try{
            String name=TicketCards.read(stack,null).product.name;
            return name==null||name.isBlank()?fallback:ComponentHelper.literal(name);
        }catch(RuntimeException ex){return fallback;}
    }
    @Override public Component getName(ItemStack stack){return productName(stack,super.getName(stack));}
    public static void tooltip(ItemStack stack,List<Component> lines) {
        if(stack.getTag()==null||!stack.getTag().contains(TicketCards.KEY))return;
        try{
            Card card=TicketCards.read(stack,null);
            lines.add(ComponentHelper.translatable("tooltip.fangsu.ticket.networks",String.join(", ",card.product.networks)));
            for(Badge badge:card.product.badges)lines.add(ComponentHelper.translatable("tooltip.fangsu.ticket.union",badge.name));
            if(card.product.kind==Kind.IC)lines.add(ComponentHelper.translatable("tooltip.fangsu.ticket.balance",card.balance));
            if(card.product.kind==Kind.SINGLE||card.product.kind==Kind.ITINERARY)lines.add(ComponentHelper.translatable("tooltip.fangsu.ticket.fare",card.allowance));
            if(card.product.kind==Kind.ITINERARY)lines.add(ComponentHelper.translatable("tooltip.fangsu.ticket.rights",card.product.legs.size()-card.used.size()));
            if(card.product.kind==Kind.DAY)lines.add(ComponentHelper.translatable("tooltip.fangsu.ticket.duration",card.product.durationHours()));
            lines.add(card.journey==null?ComponentHelper.translatable("tooltip.fangsu.ticket.outside"):ComponentHelper.translatable("tooltip.fangsu.ticket.inside",com.fangsu.ticketSystem.v2.TicketStations.formatName(card.journey.entry)));
            if(card.activated>0&&(card.product.kind==Kind.DAY||card.product.validHours>0)){
                String expiry=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.ROOT).format(new java.util.Date(card.activated+card.product.durationHours()*3600000L));
                lines.add(ComponentHelper.translatable("tooltip.fangsu.ticket.expiry",expiry));
            }
            lines.add(ComponentHelper.translatable("tooltip.fangsu.ticket.serial",card.serial));
        }catch(RuntimeException ex){lines.add(ComponentHelper.translatable("tooltip.fangsu.ticket.invalid"));}
    }
    @Override public void appendHoverText(ItemStack stack,Level world,List<Component> lines,TooltipFlag flag){tooltip(stack,lines);}
}
