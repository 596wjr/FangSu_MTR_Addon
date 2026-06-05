package com.fangsu.blocks;

import com.fangsu.blockEntities.*;
import com.fangsu.blocks.client.ModBlockClient;
import com.fangsu.items.ModItems;
import com.fangsu.utils.RegisterUtil;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

public class ModBlocks {
    public static final RegistrySupplier<Block> BLOCK_TICKET_BARRIER = RegisterUtil.addBlock("ticket_barrier", BlockTicketBarrier::new);
    public static final RegistrySupplier<Block> BLOCK_SCREENDOOR = RegisterUtil.addBlock("screendoor_door", BlockScreendoor::new);
    public static final RegistrySupplier<Block> BLOCK_SCREENDOOR_GLASS = RegisterUtil.addBlock("screendoor_glass", BlockScreendoorGlass::new);
    public static final RegistrySupplier<Block> BLOCK_DUANMEN = RegisterUtil.addBlock("duanmen", BlockDuanmen::new);
    public static final RegistrySupplier<Block> BLOCK_SIGN = RegisterUtil.addBlock("sign", BlockSign::new);
    public static final RegistrySupplier<Block> BLOCK_SIGN_ON_WALL = RegisterUtil.addBlock("sign_on_wall", BlockSignOnWall::new);
    public static final RegistrySupplier<Block> BLOCK_PIDS = RegisterUtil.addBlock("pids", BlockPids::new);
    public static final RegistrySupplier<Block> BLOCK_DIAOBAN = RegisterUtil.addBlock("diaoban", BlockDiaoban::new);
    public static final RegistrySupplier<Block> BLOCK_RIS = RegisterUtil.addBlock("route_info_sign", BlockRis::new);
    public static final RegistrySupplier<Block> BLOCK_ADV_BOARD = RegisterUtil.addBlock("adv_board", BlockAdvBoard::new);
    public static final RegistrySupplier<Block> BLOCK_TICKET_MACHINE = RegisterUtil.addBlock("ticket_machine", BlockTicketMachine::new);
    public static final RegistrySupplier<Block> BLOCK_SIS = RegisterUtil.addBlock("station_info_sign", BlockSis::new);
    public static final RegistrySupplier<Block> BLOCK_SCREENDOOR_CENTRAL_CONTROL =
            RegisterUtil.addBlock("screendoor_central_control", BlockScreendoorCentralControl::new);

    public static final RegistrySupplier<BlockEntityType<BlockEntityScreendoorCentralControl>> BLOCK_ENTITY_SCREENDOOR_CENTRAL_CONTROL =
            RegisterUtil.addBlockEntity("block_entity_screendoor_central_control", BLOCK_SCREENDOOR_CENTRAL_CONTROL, BlockEntityScreendoorCentralControl::new);

    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_TICKET_BARRIER =
            RegisterUtil.addBlockEntity("block_entity_ticket_barrier", BLOCK_TICKET_BARRIER, BlockEntityTicketBarrier::new);
    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_SCREENDOOR =
            RegisterUtil.addBlockEntity("block_entity_screendoor_door", BLOCK_SCREENDOOR, BlockEntityScreendoor::new);
    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_SCREENDOOR_GLASS =
            RegisterUtil.addBlockEntity("block_entity_screendoor_glass", BLOCK_SCREENDOOR_GLASS, BlockEntityScreendoorGlass::new);
    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_DUANMEN =
            RegisterUtil.addBlockEntity("block_entity_duanmen", BLOCK_DUANMEN, BlockEntityDuanmen::new);
    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_SIGN =
            RegisterUtil.addBlockEntity("block_entity_sign", BLOCK_SIGN, BlockEntitySign::new);
    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_SIGN_ON_WALL =
            RegisterUtil.addBlockEntity("block_entity_sign_on_wall", BLOCK_SIGN_ON_WALL, BlockEntitySignOnWall::new);
    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_PIDS =
            RegisterUtil.addBlockEntity("block_entity_pids", BLOCK_PIDS, BlockEntityPids::new);
    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_DIAOBAN =
            RegisterUtil.addBlockEntity("block_entity_diaoban", BLOCK_DIAOBAN, BlockEntityDiaoban::new);
    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_ADV_BOARD =
            RegisterUtil.addBlockEntity("block_entity_adv_board", BLOCK_ADV_BOARD, BlockEntityAdvBoard::new);
    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_RIS =
            RegisterUtil.addBlockEntity("block_entity_route_info_sign", BLOCK_RIS, BlockEntityRis::new);
    public static final RegistrySupplier<BlockEntityType<BaseObjBlockEntity>> BLOCK_ENTITY_SIS =
            RegisterUtil.addBlockEntity("block_entity_station_info_sign", BLOCK_SIS, BlockEntitySis::new);

    public static final RegistrySupplier<Item> ITEM_TICKET_BARRIER = RegisterUtil.addBlockItem("ticket_barrier", BLOCK_TICKET_BARRIER);
    public static final RegistrySupplier<Item> ITEM_SCREENDOOR = RegisterUtil.addBlockItem("screendoor_door", BLOCK_SCREENDOOR);
    public static final RegistrySupplier<Item> ITEM_SCREENDOOR_GLASS = RegisterUtil.addBlockItem("screendoor_glass", BLOCK_SCREENDOOR_GLASS);
    public static final RegistrySupplier<Item> ITEM_DUANMEN = RegisterUtil.addBlockItem("duanmen", BLOCK_DUANMEN);
    public static final RegistrySupplier<Item> ITEM_SIGN = RegisterUtil.addBlockItem("sign", BLOCK_SIGN);
    public static final RegistrySupplier<Item> ITEM_SIGN_ON_WALL = RegisterUtil.addBlockItem("sign_on_wall", BLOCK_SIGN_ON_WALL);
    public static final RegistrySupplier<Item> ITEM_TICKET_MACHINE = RegisterUtil.addBlockItem("ticket_machine", BLOCK_TICKET_MACHINE);
    public static final RegistrySupplier<Item> ITEM_PIDS = RegisterUtil.addBlockItem("pids", BLOCK_PIDS);
    public static final RegistrySupplier<Item> ITEM_DIAOBAN = RegisterUtil.addBlockItem("diaoban", BLOCK_DIAOBAN);
    public static final RegistrySupplier<Item> ITEM_RIS = RegisterUtil.addBlockItem("route_info_sign", BLOCK_RIS);
    public static final RegistrySupplier<Item> ITEM_ADV_BOARD = RegisterUtil.addBlockItem("adv_board", BLOCK_ADV_BOARD);
    public static final RegistrySupplier<Item> ITEM_SIS = RegisterUtil.addBlockItem("station_info_sign", BLOCK_SIS);
    public static final RegistrySupplier<Item> ITEM_SCREENDOOR_CENTRAL_CONTROL =
            RegisterUtil.addBlockItem("screendoor_central_control", BLOCK_SCREENDOOR_CENTRAL_CONTROL);

    public static final RegistrySupplier<Block> BLOCK_COLLISION_COMPENSATOR =
            RegisterUtil.addBlock("collision_compensation_block", BlockCollisionCompensator::new);
    public static final RegistrySupplier<Item> ITEM_COLLISION_COMPENSATOR =
            RegisterUtil.addBlockItem("collision_compensation_block", BLOCK_COLLISION_COMPENSATOR);

    public static void init() {
    }

    public static void initClient() {
        ModBlockClient.initClient();
    }
}
