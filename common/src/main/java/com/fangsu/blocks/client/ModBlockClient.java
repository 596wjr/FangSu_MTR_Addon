package com.fangsu.blocks.client;

import com.fangsu.blockEntities.client.BaseBlockEntityRender;
import com.fangsu.blocks.ModBlocks;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

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
                ModBlocks.BLOCK_ENTITY_DUANMEN.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_SIGN.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_SIGN_ON_WALL.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_PIDS.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_DIAOBAN.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_RIS.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_ADV_BOARD.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_SIS.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_ROTATING_RAIL.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
    }
}
