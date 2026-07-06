package com.fangsu.utils;

import com.fangsu.Main;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mappings.FangSuRegistries;
import com.fangsu.mappings.RegistryObject;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

public class RegisterUtil {
    public static final DeferredRegister<Block> BLOCKS =
            FangSuRegistries.createBlockRegister(Main.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            FangSuRegistries.createItemRegister(Main.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            FangSuRegistries.createBlockEntityRegister(Main.MOD_ID);
    //#if MC_VERSION >= 12000
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            FangSuRegistries.createCreativeTabRegister(Main.MOD_ID);
    //#endif
    public static final DeferredRegister<MenuType<?>> MENUS =
            FangSuRegistries.createMenuRegister(Main.MOD_ID);

    public static RegistrySupplier<Block> addBlock(String id, Supplier<? extends Block> block) {
        return BLOCKS.register(id, block);
    }

    public static RegistrySupplier<Item> addItem(String id, Supplier<? extends Item> item) {
        return ITEMS.register(id, item);
    }

    // 1.19.2 及以下需手动添加到创造标签页；1.19.3+ 通过 buildCreativeTab 的 displayItems 自动添加
    //#if MC_VERSION < 11903
    //$$public static Item.Properties tabProps(Item.Properties props) {
    //$$    return props.tab(com.fangsu.creativeTabs.ModCreativeTabs.FANGSU_MAIN);
    //$$}
    //#else
    public static Item.Properties tabProps(Item.Properties props) {
        return props;
    }
    //#endif

    public static RegistrySupplier<Item> addBlockItem(String id, RegistrySupplier<Block> block) {
        return ITEMS.register(id, () -> new BlockItem(block.get(), tabProps(new Item.Properties())));
    }

    //#if MC_VERSION >= 12000
    public static RegistrySupplier<CreativeModeTab> addCreativeTab(String id, String name, RegistrySupplier<Item> icon, RegistrySupplier<Item>... items) {
        return CREATIVE_TABS.register(
                id,
                () -> {
                    CreativeModeTab.Builder builder = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0);
                    return builder
                            .title(ComponentHelper.translatable(name))
                            .icon(() -> new ItemStack(icon.get()))
                            .displayItems((parameters, output) -> {
                                for (RegistrySupplier<Item> item : items) {
                                    output.accept(new ItemStack(item.get()));
                                }
                            })
                            .build();
                }
        );
    }
    //#elseif MC_VERSION >= 11904
    //$$ public static CreativeModeTab addCreativeTab(String id, String name, RegistrySupplier<Item> icon, RegistrySupplier<Item>... items) {
    //$$     CreativeModeTab.Builder builder = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0);
    //$$     return builder
    //$$             .title(ComponentHelper.translatable(name))
    //$$             .icon(() -> new ItemStack(icon.get()))
    //$$             .displayItems((parameters, output) -> {
    //$$                 for (RegistrySupplier<Item> item : items) {
    //$$                     output.accept(new ItemStack(item.get()));
    //$$                 }
    //$$             })
    //$$             .build();
    //$$ }
    //#elseif MC_VERSION >= 11903
    //$$ public static CreativeModeTab addCreativeTab(String id, String name, RegistrySupplier<Item> icon, RegistrySupplier<Item>... items) {
    //$$     CreativeModeTab.Builder builder = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0);
    //$$     return builder
    //$$             .title(ComponentHelper.translatable(name))
    //$$             .icon(() -> new ItemStack(icon.get()))
    //$$             .displayItems((enabledFeatures, output, hasPermissions) -> {
    //$$                 for (RegistrySupplier<Item> item : items) {
    //$$                     output.accept(new ItemStack(item.get()));
    //$$                 }
    //$$             })
    //$$             .build();
    //$$ }
    //#endif

    public static RegistrySupplier<MenuType<?>> addMenu(String id, Supplier<? extends MenuType<?>> menu) {
        return MENUS.register(id, menu);
    }

    public static <T extends BlockEntity> RegistrySupplier<BlockEntityType<T>> addBlockEntity(
            String id,
            Supplier<? extends Block> blockSupplier,
            BlockEntityType.BlockEntitySupplier<T> blockEntitySupplier
    ) {
        return BLOCK_ENTITIES.register(id, () ->
                BlockEntityType.Builder.of(blockEntitySupplier, blockSupplier.get()).build(null)
        );
    }


//    public static <T extends BlockEntity> void addBlockEntityRenderer(
//            RegistrySupplier<BlockEntityType<T>> blockEntityTypeSupplier,
//            BlockEntityRendererProvider<? super T> rendererProvider) {
//
//        BlockEntityRendererRegistry.register(
//                blockEntityTypeSupplier::get,
//                rendererProvider
//        );
//    }

    public static void register() {
        BLOCKS.register();
        ITEMS.register();
        BLOCK_ENTITIES.register();
        //#if MC_VERSION >= 12000
        CREATIVE_TABS.register();
        //#endif
        MENUS.register();
    }

}
