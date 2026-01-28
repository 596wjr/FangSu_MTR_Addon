package com.fangsu.utils;

import com.fangsu.Main;
import com.fangsu.blocks.ModBlocks;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mtr.RegistryClient;
import mtr.mappings.BlockEntityRendererMapper;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Function;
import java.util.function.Supplier;

public class RegisterUtil {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Main.MOD_ID, Registries.BLOCK);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Main.MOD_ID, Registries.ITEM);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Main.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    public static RegistrySupplier<Block> addBlock(String id, Supplier<? extends Block> block) {
        return BLOCKS.register(id, block);
    }

    public static RegistrySupplier<Item> addItem(String id, Supplier<? extends Item> item) {
        return ITEMS.register(id, item);
    }

    public static RegistrySupplier<Item> addBlockItem(String id, RegistrySupplier<Block> block) {
        return ITEMS.register(id, ()->new BlockItem(block.get(),new Item.Properties()));
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
    }


}
