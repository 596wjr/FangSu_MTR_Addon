package com.fangsu.ticketSystem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class SingleJourneyTicketData {

    private static final String PRICE = "Price";
    private static final String ENTERED = "Entered";
    private static final String ENTRY_ZONE = "EntryZone";

    private SingleJourneyTicketData() {
    }

    /* ========= 初始化（售票机） ========= */

    public static void init(ItemStack stack, int price) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(PRICE, price);
        tag.putBoolean(ENTERED, false);
    }

    /* ========= 状态 ========= */

    public static boolean hasEntered(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(ENTERED);
    }

    public static void enter(ItemStack stack, int entryZone) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(ENTERED, true);
        tag.putInt(ENTRY_ZONE, entryZone);
    }

    /* ========= 数据 ========= */

    public static int getPrice(ItemStack stack) {
        if (stack.getTag() != null) {
            return stack.getTag().getInt(PRICE);
        }
        return 0;
    }

    public static int getEntryZone(ItemStack stack) {
        if (stack.getTag() != null) {
            return stack.getTag().getInt(ENTRY_ZONE);
        }
        return 0;
    }
}

