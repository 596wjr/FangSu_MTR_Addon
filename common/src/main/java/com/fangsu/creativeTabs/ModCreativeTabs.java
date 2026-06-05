package com.fangsu.creativeTabs;

import com.fangsu.blocks.ModBlocks;
import com.fangsu.items.ModItems;
import com.fangsu.utils.RegisterUtil;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;

public class ModCreativeTabs {
    public static final RegistrySupplier<CreativeModeTab> FANGSU_MAIN = RegisterUtil.addCreativeTab(
            "main",
            Component.translatable("tab.fangsu.main").getString(),
            ModBlocks.ITEM_TICKET_BARRIER,
            ModItems.ITEM_WRENCH,
            ModBlocks.ITEM_TICKET_BARRIER,
            ModBlocks.ITEM_SCREENDOOR,
            ModBlocks.ITEM_SCREENDOOR_GLASS,
            ModBlocks.ITEM_DUANMEN,
            ModBlocks.ITEM_SIGN,
            ModBlocks.ITEM_SIGN_ON_WALL,
            ModBlocks.ITEM_PIDS,
            ModBlocks.ITEM_DIAOBAN,
            ModBlocks.ITEM_TICKET_MACHINE,
            ModBlocks.ITEM_RIS,
            ModBlocks.ITEM_ADV_BOARD,
            ModBlocks.ITEM_SCREENDOOR_CENTRAL_CONTROL,
            ModBlocks.ITEM_COLLISION_COMPENSATOR
    );

    public static void init() {
    }
}
