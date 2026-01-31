package com.fangsu.blocks;

import com.fangsu.blockEntities.BlockEntityTicketBarrier;
import com.fangsu.blockEntities.client.BaseBlockEntityRender;
import com.fangsu.utils.RegisterUtil;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlocks {
    public static final RegistrySupplier<Block> BLOCK_TICKET_BARRIER = RegisterUtil.addBlock("ticket_barrier", BlockTicketBarrier::new);
    public static final RegistrySupplier<BlockEntityType<BlockEntityTicketBarrier>> BLOCK_ENTITY_TICKET_BARRIER =
            RegisterUtil.addBlockEntity("block_entity_ticket_barrier", BLOCK_TICKET_BARRIER, BlockEntityTicketBarrier::new);

    public static final RegistrySupplier<Item> ITEM_TICKET_BARRIER = RegisterUtil.addBlockItem("ticket_barrier", BLOCK_TICKET_BARRIER);

    public static final RegistrySupplier<CreativeModeTab> FANGSU_MAIN = RegisterUtil.addCreativeTab("main", "tab", ITEM_TICKET_BARRIER, ITEM_TICKET_BARRIER);

    public static void init() {
    }

    public static void initClient() {
        BlockEntityRendererRegistry.register(
                BLOCK_ENTITY_TICKET_BARRIER.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
    }
}
