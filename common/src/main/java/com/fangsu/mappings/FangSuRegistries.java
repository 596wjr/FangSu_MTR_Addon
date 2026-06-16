package com.fangsu.mappings;

import dev.architectury.registry.registries.DeferredRegister;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 版本无关的注册表工具类。
 * 统一 1.19.3+ 的 {@link net.minecraft.core.registries.Registries} 和
 * 1.18.2/1.19.2 的 {@link Registry} 的 API 差异。
 * <p>
 * 除构造和 asMinecraft 外，与 Minecraft 类基本无关。
 */
public class FangSuRegistries {

    // ============ 注册表键（统一为 ResourceLocation） ============

    //#if MC_VERSION >= 11903
    public static final ResourceLocation BLOCK_KEY = net.minecraft.core.registries.Registries.BLOCK.location();
    public static final ResourceLocation ITEM_KEY = net.minecraft.core.registries.Registries.ITEM.location();
    public static final ResourceLocation BLOCK_ENTITY_TYPE_KEY = net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE.location();
    public static final ResourceLocation MENU_KEY = net.minecraft.core.registries.Registries.MENU.location();
    //#if MC_VERSION >= 12000
    public static final ResourceLocation CREATIVE_MODE_TAB_KEY = net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB.location();
    //#elseif MC_VERSION >= 11904
    //$$public static final ResourceLocation CREATIVE_MODE_TAB_KEY = new ResourceLocation("minecraft:creative_mode_tab");
    //#endif
    //#else
    //$$public static final ResourceLocation BLOCK_KEY = new ResourceLocation("minecraft:block");
    //$$public static final ResourceLocation ITEM_KEY = new ResourceLocation("minecraft:item");
    //$$public static final ResourceLocation BLOCK_ENTITY_TYPE_KEY = new ResourceLocation("minecraft:block_entity_type");
    //$$public static final ResourceLocation MENU_KEY = new ResourceLocation("minecraft:menu");
    //#endif

    // ============ DeferredRegister 工厂 ============

    /**
     * 创建 DeferredRegister。
     * 统一使用 ResourceKey，在 1.18.2~1.20.1 均有效。
     */
    public static <T> DeferredRegister<T> createDeferredRegister(String modId, ResourceLocation registryKey) {
        ResourceKey<Registry<T>> resourceKey = ResourceKey.createRegistryKey(registryKey);
        return DeferredRegister.create(modId, resourceKey);
    }

    // ============ 便捷方法：注册表键 + 创建 ============

    public static DeferredRegister<Block> createBlockRegister(String modId) {
        return createDeferredRegister(modId, BLOCK_KEY);
    }

    public static DeferredRegister<Item> createItemRegister(String modId) {
        return createDeferredRegister(modId, ITEM_KEY);
    }

    public static DeferredRegister<BlockEntityType<?>> createBlockEntityRegister(String modId) {
        return createDeferredRegister(modId, BLOCK_ENTITY_TYPE_KEY);
    }

    public static DeferredRegister<MenuType<?>> createMenuRegister(String modId) {
        return createDeferredRegister(modId, MENU_KEY);
    }

    //#if MC_VERSION >= 11904
    public static DeferredRegister<CreativeModeTab> createCreativeTabRegister(String modId) {
        return createDeferredRegister(modId, CREATIVE_MODE_TAB_KEY);
    }
    //#endif
}
