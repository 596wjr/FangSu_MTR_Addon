package com.fangsu.creativeTabs;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.blocks.ModBlocks;
import com.fangsu.items.ModItems;
import com.fangsu.utils.RegisterUtil;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.item.CreativeModeTab;

public class ModCreativeTabs {
    //#if MC_VERSION >= 11904
    public static final RegistrySupplier<CreativeModeTab> FANGSU_MAIN = RegisterUtil.addCreativeTab(
            "main",
            ComponentHelper.translatable("tab.fangsu.main").getString(),
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
            ModBlocks.ITEM_SIS,
            ModBlocks.ITEM_ADV_BOARD,
            ModBlocks.ITEM_SCREENDOOR_CENTRAL_CONTROL,
            ModBlocks.ITEM_COLLISION_COMPENSATOR
    );
    //#elseif MC_VERSION >= 11903
    //$$ public static final CreativeModeTab FANGSU_MAIN = RegisterUtil.addCreativeTab(
    //$$         "main",
    //$$         ComponentHelper.translatable("tab.fangsu.main").getString(),
    //$$         ModBlocks.ITEM_TICKET_BARRIER,
    //$$         ModItems.ITEM_WRENCH,
    //$$         ModBlocks.ITEM_TICKET_BARRIER,
    //$$         ModBlocks.ITEM_SCREENDOOR,
    //$$         ModBlocks.ITEM_SCREENDOOR_GLASS,
    //$$         ModBlocks.ITEM_DUANMEN,
    //$$         ModBlocks.ITEM_SIGN,
    //$$         ModBlocks.ITEM_SIGN_ON_WALL,
    //$$         ModBlocks.ITEM_PIDS,
    //$$         ModBlocks.ITEM_DIAOBAN,
    //$$         ModBlocks.ITEM_TICKET_MACHINE,
    //$$         ModBlocks.ITEM_RIS,
    //$$         ModBlocks.ITEM_SIS,
    //$$         ModBlocks.ITEM_ADV_BOARD,
    //$$         ModBlocks.ITEM_SCREENDOOR_CENTRAL_CONTROL,
    //$$         ModBlocks.ITEM_COLLISION_COMPENSATOR
    //$$ );
    //#else
    //$$public static final CreativeModeTab FANGSU_MAIN = dev.architectury.registry.CreativeTabRegistry.create(
    //$$        new net.minecraft.resources.ResourceLocation("fangsu", "main"),
    //$$        () -> new net.minecraft.world.item.ItemStack(ModBlocks.ITEM_TICKET_BARRIER.get())
    //$$);
    //#endif

    public static void init() {
    }
}
