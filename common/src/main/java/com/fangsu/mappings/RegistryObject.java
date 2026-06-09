package com.fangsu.mappings;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

public class RegistryObject<T> {

    private final RegistrySupplier<T> supplier;

    public RegistryObject(RegistrySupplier<T> supplier) {
        this.supplier = supplier;
    }

    /**
     * 获取注册的对象。
     */
    public T get() {
        return supplier.get();
    }

    /**
     * 获取原始 {@link RegistrySupplier}。
     */
    public RegistrySupplier<T> asMinecraft() {
        return supplier;
    }

    // ============ 便捷创建工厂 ============

    public static <T extends Block> RegistryObject<T> block(DeferredRegister<Block> register, String id, Supplier<? extends T> block) {
        return new RegistryObject<>(register.register(id, block));
    }

    public static <T extends Item> RegistryObject<T> item(DeferredRegister<Item> register, String id, Supplier<? extends T> item) {
        return new RegistryObject<>(register.register(id, item));
    }

    public static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> blockEntity(
            DeferredRegister<BlockEntityType<?>> register, String id,
            Supplier<? extends Block> blockSupplier,
            BlockEntityType.BlockEntitySupplier<T> beSupplier
    ) {
        return new RegistryObject<>(register.register(id, () ->
                BlockEntityType.Builder.of(beSupplier, blockSupplier.get()).build(null)
        ));
    }

    public static RegistryObject<MenuType<?>> menu(DeferredRegister<MenuType<?>> register, String id, Supplier<? extends MenuType<?>> menu) {
        return new RegistryObject<>(register.register(id, menu));
    }
}
