package com.fangsu.items;

import com.fangsu.utils.RegisterUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class ItemWrench extends Item {
    public ItemWrench() {
        super(com.fangsu.utils.RegisterUtil.tabProps(new Item.Properties().stacksTo(1)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        RegisterUtil.addDescTooltip(tooltip, "item.fangsu.wrench.desc");
    }
}
