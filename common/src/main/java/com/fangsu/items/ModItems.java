package com.fangsu.items;

import com.fangsu.turnout.marker.MarkerColor;
import com.fangsu.utils.RegisterUtil;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final RegistrySupplier<Item> ITEM_CURVED_PLATFORM = RegisterUtil.addItem("curved_platform_tool", ItemCurvedPlatform::new);
    public static final RegistrySupplier<Item> ITEM_PLATFORM_RAIL_SELECTOR = RegisterUtil.addItem("platform_rail_selector", ItemPlatformRailSelector::new);
    public static final RegistrySupplier<Item> ITEM_PLATFORM_CONFIGURATOR = RegisterUtil.addItem("platform_configurator", () -> new ItemPlatformTool(ItemPlatformTool.Role.CONFIGURE));
    public static final RegistrySupplier<Item> ITEM_SCREENDOOR_CONFIGURATOR = RegisterUtil.addItem("screendoor_configurator", () -> new ItemPlatformTool(ItemPlatformTool.Role.SCREEN_DOOR));
    public static final RegistrySupplier<Item> ITEM_SINGLE_JOURNEY_TICKET = RegisterUtil.addItem("single_journey_ticket", ItemSingleJourneyTicket::new);
    public static final RegistrySupplier<Item> ITEM_IC_CARD = RegisterUtil.addItem("ic_card", ItemIcCard::new);
    public static final RegistrySupplier<Item> ITEM_WRENCH = RegisterUtil.addItem("wrench", ItemWrench::new);
    public static final RegistrySupplier<Item> ITEM_DISPLACEMENT_TOOL = RegisterUtil.addItem("displacement_tool", ItemDisplacementTool::new);
    public static final RegistrySupplier<Item> ITEM_HYBRID_CREATOR = RegisterUtil.addItem("hybrid_creator", ItemHybridCreator::new);
    public static final RegistrySupplier<Item> ITEM_NODE_MARKER_RED = RegisterUtil.addItem("node_marker_red",
            () -> new ItemNodeMarker(MarkerColor.RED, "item.fangsu.node_marker_red.desc"));
    public static final RegistrySupplier<Item> ITEM_NODE_MARKER_BLUE = RegisterUtil.addItem("node_marker_blue",
            () -> new ItemNodeMarker(MarkerColor.BLUE, "item.fangsu.node_marker_blue.desc"));
    public static final RegistrySupplier<Item> ITEM_MARKER_CLEARER = RegisterUtil.addItem("node_marker_clearer", ItemMarkerClearer::new);

    public static void init() {
    }
}
