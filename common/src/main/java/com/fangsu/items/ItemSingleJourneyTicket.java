package com.fangsu.items;

import com.fangsu.ticketSystem.FareInfo;
import com.fangsu.ticketSystem.FareType;
import com.fangsu.ticketSystem.SingleJourneyTicketData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ItemSingleJourneyTicket extends Item implements TicketItem {
    public ItemSingleJourneyTicket() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean enter(Level world, Player player, ItemStack stack, FareInfo info) {
        if (SingleJourneyTicketData.hasEntered(stack)) {
            player.displayClientMessage(Component.translatable("ui.fangsu.ticket.error"), true);
            return false;
        }

        switch (info.type()) {
            case MTR, CUSTOM -> {
                SingleJourneyTicketData.enter(stack, info.value());
                return true;
            }
            case FARE_ONCE -> {
                if (SingleJourneyTicketData.getPrice(stack) >= info.value()) {
                    stack.shrink(1);
                    player.displayClientMessage(Component.translatable("ui.fangsu.ticket.success"), true);
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean exit(Level world, Player player, ItemStack stack, FareInfo info) {
        if (!SingleJourneyTicketData.hasEntered(stack)) {
            player.displayClientMessage(Component.translatable("ui.fangsu.ticket.error"), true);
            return false;
        }

        if (info.type() == FareType.MTR || info.type() == FareType.CUSTOM) {
            int fare = Math.abs(SingleJourneyTicketData.getEntryZone(stack) - info.value());
            if (SingleJourneyTicketData.getPrice(stack) < fare) {
                player.displayClientMessage(Component.translatable("ui.fangsu.ticket.error"), true);
                return false;
            }
            stack.shrink(1);
            player.displayClientMessage(Component.translatable("ui.fangsu.ticket.success"), true);
            return true;
        }

        return false;
    }
}
