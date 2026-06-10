package com.fangsu.items;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.ticketSystem.FareInfo;
import com.fangsu.ticketSystem.FareType;
import com.fangsu.ticketSystem.SingleJourneyTicketData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class ItemSingleJourneyTicket extends Item implements TicketItem {
    public ItemSingleJourneyTicket() {
        super(com.fangsu.utils.RegisterUtil.tabProps(new Item.Properties().stacksTo(1)));
    }

    @Override
    public boolean enter(Level world, Player player, ItemStack stack, FareInfo info) {
        if (SingleJourneyTicketData.hasEntered(stack)) {
            player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.ticket.error"), true);
            return false;
        }

        switch (info.type()) {
            case MTR, CUSTOM -> {
                SingleJourneyTicketData.enter(stack, info.value());
                String name = info.displayName() == null || info.displayName().isEmpty() ? ComponentHelper.translatable("block.fangsu.ticket_barrier").getString() : info.displayName();
                player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.ticket.enter1", name.replace("|", " ")), true);
                return true;
            }
            case FARE_ONCE -> {
                if (SingleJourneyTicketData.getPrice(stack) >= info.value()) {
                    stack.shrink(1);
                    player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.ticket.success"), true);
                    return true;
                }
                player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.ticket.error"), true);
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
            player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.ticket.error"), true);
            return false;
        }

        if (info.type() == FareType.MTR || info.type() == FareType.CUSTOM) {
            int fare = Math.abs(SingleJourneyTicketData.getEntryZone(stack) - info.value());
            if (SingleJourneyTicketData.getPrice(stack) < fare) {
                player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.ticket.error"), true);
                return false;
            }
            stack.shrink(1);
            if (info.type() == FareType.MTR) {
                String name = info.displayName() == null || info.displayName().isEmpty() ? ComponentHelper.translatable("block.fangsu.ticket_barrier").getString() : info.displayName();
                player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.ticket.exit1", name.replace("|", " ")), true);
            } else player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.ticket.success"), true);
            return true;
        }

        return false;
    }

    @Override
    public ItemStack createTicket(int price) {
        ItemStack stack = new ItemStack(this);
        SingleJourneyTicketData.init(stack, price);
        return stack;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Level level,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;

        tooltip.add(
                ComponentHelper.translatable(
                        "ui.fangsu.ticket.value",
                        SingleJourneyTicketData.getPrice(stack)
                )
        );

        if (SingleJourneyTicketData.hasEntered(stack) && level != null) {
            tooltip.add(
                    ComponentHelper.translatable(
                            "ui.fangsu.ticket.entered"

                    )
            );
        } else {
            tooltip.add(
                    ComponentHelper.translatable(
                            "ui.fangsu.ticket.not_entered"
                    )
            );
        }
    }
}
