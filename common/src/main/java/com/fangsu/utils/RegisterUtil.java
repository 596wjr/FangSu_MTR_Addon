package com.fangsu.utils;

import com.fangsu.Main;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.mappings.FangSuRegistries;
import com.fangsu.mappings.RegistryObject;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;
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
        return ITEMS.register(id, () -> new FangSuBlockItem(block.get(), tabProps(new Item.Properties()), "block.fangsu." + id + ".desc"));
    }

    /**
     * 在物品栏 tooltip 中追加一条灰色描述（前置空行），供各物品/方块物品统一调用
     */
    public static void addDescTooltip(List<Component> tooltip, String descKey) {
        tooltip.add(ComponentHelper.empty());
        tooltip.add(ComponentHelper.translatable(descKey).withStyle(ChatFormatting.GRAY));
    }

    /**
     * 方速方块物品：自动在物品栏 tooltip 中追加该方块的描述（lang 键 block.fangsu.<id>.desc）
     */
    public static class FangSuBlockItem extends BlockItem {
        private final String descKey;

        public FangSuBlockItem(Block block, Item.Properties properties, String descKey) {
            super(block, properties);
            this.descKey = descKey;
        }

        @Override
        public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, level, tooltip, flag);
            addDescTooltip(tooltip, descKey);
        }
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
    //$$     // 1.19.3+ 自定义标签页必须经平台注册机制才能出现在创造物品栏：
    //$$     // Architectury 内部（Fabric 用 FabricItemGroup.builder + ItemGroupEvents，Forge 用 CreativeModeTabEvent），
    //$$     // 与 mtr4 Minecraft-Mappings-rewrite 1.19.4 的处理一致；直接 new CreativeModeTab.builder(...).build() 不会注册
    //$$     return dev.architectury.registry.CreativeTabRegistry
    //$$             .create(new net.minecraft.resources.ResourceLocation(Main.MOD_ID, id), builder -> builder
    //$$                     .title(ComponentHelper.translatable(name))
    //$$                     .icon(() -> new ItemStack(icon.get()))
    //$$                     .displayItems((parameters, output) -> {
    //$$                         for (RegistrySupplier<Item> item : items) {
    //$$                             output.accept(new ItemStack(item.get()));
    //$$                         }
    //$$                     }))
    //$$             .get();
    //$$ }
    //#elseif MC_VERSION >= 11903
    //$$ public static CreativeModeTab addCreativeTab(String id, String name, RegistrySupplier<Item> icon, RegistrySupplier<Item>... items) {
    //$$     // 1.19.3 的 DisplayItemsGenerator 为 3 参数签名（FeatureFlagSet, Output, boolean）
    //$$     return dev.architectury.registry.CreativeTabRegistry
    //$$             .create(new net.minecraft.resources.ResourceLocation(Main.MOD_ID, id), builder -> builder
    //$$                     .title(ComponentHelper.translatable(name))
    //$$                     .icon(() -> new ItemStack(icon.get()))
    //$$                     .displayItems((enabledFeatures, output, hasPermissions) -> {
    //$$                         for (RegistrySupplier<Item> item : items) {
    //$$                             output.accept(new ItemStack(item.get()));
    //$$                         }
    //$$                     }))
    //$$             .get();
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
