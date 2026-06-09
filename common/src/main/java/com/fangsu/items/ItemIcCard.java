package com.fangsu.items;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ticketSystem.FareInfo;
import com.fangsu.ticketSystem.FareType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ItemIcCard extends Item implements TicketItem {

    private static final String BALANCE = "Balance";
    private static final String ENTERED = "Entered";
    private static final String ENTRY_ZONE = "EntryZone";

    private static final int BASE_FARE = 2;
    private static final int ZONE_FARE = 1;

    public ItemIcCard() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public boolean enter(Level world, Player player, ItemStack stack, FareInfo info) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(BALANCE)) tag.putInt(BALANCE, 100);
        if (tag.getBoolean(ENTERED)) {
            player.displayClientMessage(ComponentHelper.translatable("gui.mtr.already_entered"), true);
            return false;
        }

        tag.putBoolean(ENTERED, true);
        tag.putInt(ENTRY_ZONE, info.value());
        String name = info.displayName() == null || info.displayName().isEmpty() ? ComponentHelper.translatable("block.fangsu.ticket_barrier").getString() : info.displayName();
        int balance = tag.getInt(BALANCE);
        player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.ticket.enter", name, balance), true);
        return true;
    }

    @Override
    public boolean exit(Level world, Player player, ItemStack stack, FareInfo info) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.getBoolean(ENTERED)) {
            player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.ticket.notEntered"), true);
            return false;
        }

        if (!tag.contains(BALANCE)) tag.putInt(BALANCE, 100);
        int balance = tag.getInt(BALANCE);
        int fare = computeFare(tag.getInt(ENTRY_ZONE), info);
        if (balance < fare) {
            player.displayClientMessage(ComponentHelper.translatable("gui.mtr.insufficient_balance", balance), true);
            return false;
        }

        tag.putInt(BALANCE, balance - fare);
        tag.putBoolean(ENTERED, false);
        tag.putInt(ENTRY_ZONE, 0);
        String name = info.displayName() == null || info.displayName().isEmpty() ? ComponentHelper.translatable("block.fangsu.ticket_barrier").getString() : info.displayName();
        player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.ticket.exit", name, fare, balance - fare), true);
        return true;
    }

    @Override
    public ItemStack createTicket(int price) {
        return null;
    }

    private int computeFare(int entryZone, FareInfo info) {
        if (info.type() == FareType.FARE_ONCE) {
            return Math.max(0, info.value());
        }
        int distance = Math.abs(entryZone - info.value());
        return BASE_FARE + ZONE_FARE * distance;
    }
}
