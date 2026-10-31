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
                ctx -> new TurnoutBlockEntityRenderer(ctx.getBlockEntityRenderDispatcher())
        );
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_LEVEL_CROSSING.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_MULTI_DIRECTION_NODE.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_TRAFFIC_LIGHT.get(),
                ctx -> new BaseBlockEntityRender<>(ctx.getBlockEntityRenderDispatcher())
        );
        // 道岔：几何由 BlockEntityTurnout.whenRendering() 提交（本体不渲染，只有轨道几何）。
        // 必须用专用渲染器：几何可从方块跨出约 32 格，随 section 剔除会让远端整段消失，
        // 故 TurnoutBlockEntityRenderer 只覆写 shouldRenderOffScreen()→true（其余全部继承基类）。
        BlockEntityRendererRegistry.register(
                ModBlocks.BLOCK_ENTITY_TURNOUT.get(),
                ctx -> new TurnoutBlockEntityRenderer(ctx.getBlockEntityRenderDispatcher())
        );
    }
}
