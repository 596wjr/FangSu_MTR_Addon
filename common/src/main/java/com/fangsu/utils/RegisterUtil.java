package com.fangsu.utils;

import com.fangsu.Main;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
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
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Main.MOD_ID, Registries.BLOCK);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Main.MOD_ID, Registries.ITEM);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Main.MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Main.MOD_ID, Registries.CREATIVE_MODE_TAB);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Main.MOD_ID, Registries.MENU);

    public static RegistrySupplier<Block> addBlock(String id, Supplier<? extends Block> block) {
        return BLOCKS.register(id, block);
    }

    public static RegistrySupplier<Item> addItem(String id, Supplier<? extends Item> item) {
        return ITEMS.register(id, item);
    }

    public static RegistrySupplier<Item> addBlockItem(String id, RegistrySupplier<Block> block) {
        return ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
    }

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

    public static RegistrySupplier<CreativeModeTab> addCreativeTab(String id, String name, RegistrySupplier<Item> icon, RegistrySupplier<Item>... items) {
        return CREATIVE_TABS.register(
                id,
                () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                        .title(Component.translatable(name))
                        .icon(() -> new ItemStack(icon.get()))
                        .displayItems((parameters, output) -> {
                            // 添加物品（顺序很重要）
                            for (RegistrySupplier<Item> item : items) {
                                output.accept(item.get());
                            }
                        })
                        .build()
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
        CREATIVE_TABS.register();
        MENUS.register();
    }


}
