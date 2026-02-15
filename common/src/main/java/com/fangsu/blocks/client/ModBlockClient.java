package com.fangsu.blocks.client;

import com.fangsu.blockEntities.client.BaseBlockEntityRender;
import com.fangsu.blocks.ModBlocks;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;

public class ModBlockClient {
    public static void initClient() {
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_TICKET_BARRIER.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_SCREENDOOR.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_SCREENDOOR_GLASS.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_SIGN.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
    }
}
