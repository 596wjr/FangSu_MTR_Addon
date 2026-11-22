package com.fangsu.items;

import com.fangsu.client.ClientHooks;
import com.fangsu.ticketSystem.v2.TicketCards;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

abstract class ItemPreviewableTicket extends Item {
    protected ItemPreviewableTicket(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getTag() == null || !stack.getTag().contains(TicketCards.KEY)) {
            return super.use(level, player, hand);
        }
        // 方块交互先于物品的 use；方块放行后才打开票面。
        if (level.isClientSide) ClientHooks.OPEN_TICKET_PREVIEW.accept(stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
