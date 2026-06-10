package com.fangsu.items;

import net.minecraft.world.item.Item;

public class ItemWrench extends Item {
    public ItemWrench() {
        super(com.fangsu.utils.RegisterUtil.tabProps(new Item.Properties().stacksTo(1)));
    }
}
