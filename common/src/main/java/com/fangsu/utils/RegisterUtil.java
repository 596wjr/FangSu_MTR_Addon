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
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            FangSuRegistries.createCreativeTabRegister(Main.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            FangSuRegistries.createMenuRegister(Main.MOD_ID);

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

    @SafeVarargs
    public static RegistrySupplier<CreativeModeTab> addCreativeTab(String id, String name, RegistrySupplier<Item> icon, RegistrySupplier<Item>... items) {
        return CREATIVE_TABS.register(
                id,
                () -> {
                    //#if MC_VERSION >= 11900
                    CreativeModeTab.Builder builder = CreativeModeTab.builder();
                    return builder
                            .title(ComponentHelper.translatable(name))
                            .icon(() -> new ItemStack(icon.get()))
                            .displayItems((parameters, output) -> {
                                for (RegistrySupplier<Item> item : items) {
                                    output.accept(item.get());
                                }
                            })
                            .build();
                    //#else
                    //$$ return new CreativeTab(CreativeModeTab.TABS.length, "fangsu." + id, () -> new ItemStack(icon.get()));
                    //#endif
                }
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

    // 1.18.2 辅助类：CreativeModeTab 构造函数为 protected，需子类才能调用
    //#if MC_VERSION < 11900
    //$$private static class CreativeTab extends CreativeModeTab {
    //$$    private final java.util.function.Supplier<ItemStack> icon;
    //$$
    //$$    CreativeTab(int index, String id, java.util.function.Supplier<ItemStack> icon) {
    //$$        super(index, id);
    //$$        this.icon = icon;
    //$$    }
    //$$
    //$$    public ItemStack makeIcon() { return icon.get(); }
    //$$}
    //#endif
}
