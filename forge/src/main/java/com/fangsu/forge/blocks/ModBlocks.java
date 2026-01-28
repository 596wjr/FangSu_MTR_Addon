package com.fangsu.forge.blocks;

import com.fangsu.utils.RegisterUtil;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import forge.cn.zbx1425.mtrsteamloco.block.BlockEyeCandy;
import forge.cn.zbx1425.mtrsteamloco.render.block.BlockEntityEyeCandyRenderer;
import mtr.mappings.BlockEntityRendererMapper;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlocks {
    static RegistrySupplier<Block> BLOCK_TICKET_BARRIER;
    static RegistrySupplier<BlockEntityType<BaseEyeCandyFunctionalBlock.FunctionaBlockEntityEyeCandy>> BLOCK_ENTITY_TICKET_BARRIER;
    public static void init() {
        BLOCK_TICKET_BARRIER= RegisterUtil.addBlock("ticket_barrier", ForgeTicketBarrier::new);
        BLOCK_ENTITY_TICKET_BARRIER=RegisterUtil.addBlockEntity(
                "block_entity_ticket_barrier",
                BLOCK_TICKET_BARRIER,
                ForgeTicketBarrier.FunctionaBlockEntityEyeCandy::new
        );

        RegisterUtil.addBlockItem("ticket_barrier",BLOCK_TICKET_BARRIER);
    }
    public static void initClient() {
        BlockEntityRendererRegistry.register(
                BLOCK_ENTITY_TICKET_BARRIER.get(),
                context -> {
                    BlockEntityRendererMapper<BlockEyeCandy.BlockEntityEyeCandy> renderer =
                            new BlockEntityEyeCandyRenderer(context.getBlockEntityRenderDispatcher());

                    @SuppressWarnings("unchecked")
                    BlockEntityRenderer<BaseEyeCandyFunctionalBlock.FunctionaBlockEntityEyeCandy> castRenderer =
                            (BlockEntityRenderer<BaseEyeCandyFunctionalBlock.FunctionaBlockEntityEyeCandy>) (BlockEntityRenderer<?>) renderer;

                    return castRenderer;
                }
        );
    }
}
