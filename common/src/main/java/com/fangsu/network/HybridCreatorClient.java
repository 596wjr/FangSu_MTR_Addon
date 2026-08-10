package com.fangsu.network;

import com.fangsu.items.ModItems;
import dev.architectury.registry.item.ItemPropertiesRegistry;
import net.minecraft.resources.ResourceLocation;
import mtr.item.ItemBlockClickingBase;

/**
 * 混合构建器客户端入口，由 {@link com.fangsu.MainClient#initClient()} 调用。
 */
public class HybridCreatorClient {

    public static void init() {
        HybridCreatorPackets.registerClient();

        // 选中一个节点（NBT 含点击状态机 TAG_POS）时切换为 selected 模型，
        // 与 MTR 选中类物品（rail_connector_selected 等）机制一致：
        // model json 的 overrides 段按 predicate "fangsu:selected" 命中 hybrid_creator_selected。
        // 注意 1.20.1 mojmap 中 vanilla ItemProperties.register/registerGeneric 均为 private，
        // 必须走 Architectury 的 ItemPropertiesRegistry（fabric/forge 各自平台实现）
        ItemPropertiesRegistry.register(ModItems.ITEM_HYBRID_CREATOR.get(),
                new ResourceLocation("fangsu", "selected"),
                (stack, world, entity, seed) -> stack.getOrCreateTag().contains(ItemBlockClickingBase.TAG_POS) ? 1.0F : 0.0F);
    }
}
