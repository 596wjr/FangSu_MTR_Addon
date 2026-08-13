package com.fangsu.items;

import com.fangsu.utils.RegisterUtil;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final RegistrySupplier<Item> ITEM_SINGLE_JOURNEY_TICKET = RegisterUtil.addItem("single_journey_ticket", ItemSingleJourneyTicket::new);
    public static final RegistrySupplier<Item> ITEM_IC_CARD = RegisterUtil.addItem("ic_card", ItemIcCard::new);
    public static final RegistrySupplier<Item> ITEM_WRENCH = RegisterUtil.addItem("wrench", ItemWrench::new);
    public static final RegistrySupplier<Item> ITEM_DISPLACEMENT_TOOL = RegisterUtil.addItem("displacement_tool", ItemDisplacementTool::new);
    public static final RegistrySupplier<Item> ITEM_HYBRID_CREATOR = RegisterUtil.addItem("hybrid_creator", ItemHybridCreator::new);

    public static void init() {
    }
}
