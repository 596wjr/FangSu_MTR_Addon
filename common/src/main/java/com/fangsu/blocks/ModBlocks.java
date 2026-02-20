package com.fangsu.blocks;

import com.fangsu.blockEntities.*;
import com.fangsu.blocks.client.ModBlockClient;
import com.fangsu.utils.RegisterUtil;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlocks {
    public static final RegistrySupplier<Block> BLOCK_TICKET_BARRIER = RegisterUtil.addBlock("ticket_barrier", BlockTicketBarrier::new);
    public static final RegistrySupplier<Block> BLOCK_SCREENDOOR = RegisterUtil.addBlock("screendoor_door", BlockScreendoor::new);
    public static final RegistrySupplier<Block> BLOCK_SCREENDOOR_GLASS = RegisterUtil.addBlock("screendoor_glass", BlockScreendoorGlass::new);
    public static final RegistrySupplier<Block> BLOCK_SIGN = RegisterUtil.addBlock("sign", BlockSign::new);
    public static final RegistrySupplier<Block> BLOCK_SIGN_ON_WALL = RegisterUtil.addBlock("sign_on_wall", BlockSignOnWall::new);

    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_TICKET_BARRIER =
            RegisterUtil.addBlockEntity("block_entity_ticket_barrier", BLOCK_TICKET_BARRIER, BlockEntityTicketBarrier::new);
    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_SCREENDOOR =
            RegisterUtil.addBlockEntity("block_entity_screendoor_door", BLOCK_SCREENDOOR, BlockEntityScreendoor::new);
    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_SCREENDOOR_GLASS =
            RegisterUtil.addBlockEntity("block_entity_screendoor_glass", BLOCK_SCREENDOOR_GLASS, BlockEntityScreendoorGlass::new);
    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_SIGN =
            RegisterUtil.addBlockEntity("block_entity_sign", BLOCK_SIGN, BlockEntitySign::new);
    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_SIGN_ON_WALL =
            RegisterUtil.addBlockEntity("block_entity_sign_on_wall", BLOCK_SIGN_ON_WALL, BlockEntitySignOnWall::new);

    public static final RegistrySupplier<Item> ITEM_TICKET_BARRIER = RegisterUtil.addBlockItem("ticket_barrier", BLOCK_TICKET_BARRIER);
    public static final RegistrySupplier<Item> ITEM_SCREENDOOR = RegisterUtil.addBlockItem("screendoor_door", BLOCK_SCREENDOOR);
    public static final RegistrySupplier<Item> ITEM_SCREENDOOR_GLASS = RegisterUtil.addBlockItem("screendoor_glass", BLOCK_SCREENDOOR_GLASS);
    public static final RegistrySupplier<Item> ITEM_SIGN = RegisterUtil.addBlockItem("sign", BLOCK_SIGN);
    public static final RegistrySupplier<Item> ITEM_SIGN_ON_WALL = RegisterUtil.addBlockItem("sign_on_wall", BLOCK_SIGN_ON_WALL);

    public static final RegistrySupplier<Block> BLOCK_COLLISION_COMPENSATOR =
            RegisterUtil.addBlock("collision_compensation_block", BlockCollisionCompensator::new);
    public static final RegistrySupplier<Item> ITEM_COLLISION_COMPENSATOR =
            RegisterUtil.addBlockItem("collision_compensation_block", BLOCK_COLLISION_COMPENSATOR);

    public static final RegistrySupplier<CreativeModeTab> FANGSU_MAIN = RegisterUtil.addCreativeTab(
            "main",
            Component.translatable("tab.fangsu.main").getString(),
            ITEM_TICKET_BARRIER,
            ITEM_TICKET_BARRIER,
            ITEM_SCREENDOOR,
            ITEM_SCREENDOOR_GLASS,
            ITEM_SIGN,
            ITEM_SIGN_ON_WALL,
            ITEM_COLLISION_COMPENSATOR
    );

    public static void init() {
    }

    public static void initClient() {
        ModBlockClient.initClient();
    }
}
