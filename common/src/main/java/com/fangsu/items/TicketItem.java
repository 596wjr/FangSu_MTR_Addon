package com.fangsu.items;

import com.fangsu.ticketSystem.FareInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface TicketItem {
    /**
     * 玩家刷闸机进站
     */
    boolean enter(Level world, Player player, ItemStack stack, FareInfo info);

    /**
     * 玩家刷闸机出站
     */
    boolean exit(Level world, Player player, ItemStack stack, FareInfo info);

    ItemStack createTicket(int price);
}
