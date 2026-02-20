package com.fangsu.items;

import com.fangsu.ticketSystem.FareInfo;
import com.fangsu.ticketSystem.SingleJourneyTicketData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ItemSingleJourneyTicket extends Item implements TicketItem {
    public ItemSingleJourneyTicket() {
        super(new Item.Properties()
                .stacksTo(1)
        );
    }

    @Override
    public boolean enter(Level world, Player player, ItemStack stack, FareInfo info) {
        switch (info.type()) {
            case MTR:
                if (SingleJourneyTicketData.hasEntered(stack)) {
                    player.displayClientMessage(Component.translatable("ui.fangsu.ticket.error"), true);
                    return false;
                } else {
                    SingleJourneyTicketData.enter(stack, info.value());
                }
            case FARE_ONCE:
                if (SingleJourneyTicketData.getPrice(stack) >= info.value()) {
                    stack.shrink(1);
                    player.displayClientMessage(Component.translatable("ui.fangsu.ticket.success"), true);
                    return true;
                }
            default:
                return false;
        }
    }

    @Override
    public boolean exit(Level world, Player player, ItemStack stack, FareInfo info) {
        return false;
    }
}
