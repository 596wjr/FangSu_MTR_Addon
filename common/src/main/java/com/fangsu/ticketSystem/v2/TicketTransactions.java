package com.fangsu.ticketSystem.v2;

import com.fangsu.blockEntities.BlockEntityTicketMachine;
import com.fangsu.ticketSystem.MtrTicketSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.*;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

/** 所有金额均由服务端重新核算，报价绑定设备、目录版本及手持票卡。 */
public final class TicketTransactions {
    public static final class Request {
        public String action = "buy", product = "fangsu:single", origin = "", destination = "", payment = "mtr";
        public int count = 1, recharge = 10, fare = -1;
    }
    public static final class Quote {
        public String id = UUID.randomUUID().toString(), description, cardBefore;
        public int amount, paymentCount, count;
        public String payment;
        public transient Request request;
        public transient Card cardAfter;
        public transient List<ItemStack> issued = new ArrayList<>();
        public transient long revision, expires;
        public transient String config;
    }
    public static Quote quote(ServerPlayer player, BlockEntityTicketMachine machine, Request request) {
        TicketGate.refresh(player.getServer());
        TicketWorldData data = TicketWorldData.get(player.getServer());
        Catalogue catalogue = data.catalogue;
        TicketMachineConfig config = machine.ticketConfig;
        Network network = catalogue.networks.get(config.network);
        require(network != null && network.enabled, "msg.fangsu.ticketing.device_network_disabled");
        Quote quote = new Quote(); quote.request = copy(request, Request.class); quote.payment = request.payment;
        quote.revision = catalogue.revision; quote.config = JSON.toJson(config); quote.expires = System.currentTimeMillis()+30000;
        Station at = TicketStations.at(player.getCommandSenderWorld(), machine.getBlockPos());
        if ("buy".equals(request.action)) {
            require(machine.kind() != 2, "msg.fangsu.ticketing.adjustment_no_sales");
            Product product = catalogue.products.get(request.product);
            require(product != null && product.enabled && config.products.contains(product.id), "msg.fangsu.ticketing.product_not_sold");
            require(TicketMachineConfig.sells(machine.kind(), product.kind), "msg.fangsu.ticketing.unsupported_sale");
            require(product.authorizedNetworks(catalogue).stream().allMatch(id -> catalogue.networks.containsKey(id) && catalogue.networks.get(id).enabled)
                    && product.unions.stream().allMatch(id -> catalogue.unions.get(id).enabled), "msg.fangsu.ticketing.product_authorization_disabled");
            require(product.authorizedNetworks(catalogue).contains(config.network), "msg.fangsu.ticketing.product_network");
            require(request.count > 0 && request.count <= 6, "msg.fangsu.ticketing.purchase_quantity");
            int allowance = product.price, unitPrice = product.price;
            Station origin = null, destination = null;
            if (product.kind == Kind.SINGLE) {
                if (request.destination.isEmpty()) {
                    require(!product.requiresStations(), "msg.fangsu.ticketing.point_to_point_required");
                    require(request.fare >= 0 && request.fare <= 32767, "msg.fangsu.ticketing.select_fare");
                    allowance = request.fare;
                } else {
                    String originKey = request.origin.isEmpty() && at != null ? at.key : request.origin;
                    var stations = TicketStations.forSale(player.getCommandSenderWorld(), catalogue, config.network);
                    origin = TicketStations.find(stations, originKey);
                    require(origin != null, "msg.fangsu.ticketing.select_network_origin");
                    destination = TicketStations.find(stations, request.destination);
                    require(destination != null && !origin.key.equals(destination.key), "msg.fangsu.ticketing.select_network_destination");
                    allowance = network.rule.fare(origin, destination);
                }
                unitPrice = allowance;
            } else if (product.kind == Kind.IC) unitPrice = money((long) product.price + product.initialBalance);
            quote.amount = money((long) unitPrice * request.count); quote.count = request.count;
            quote.description = product.name + " × " + request.count;
            Station salesStation = product.kind == Kind.SINGLE ? at : null;
            for (int i = 0; i < request.count; i++)
                quote.issued.add(TicketCards.issue(product, allowance, catalogue, origin, destination, salesStation));
            long free = player.getInventory().items.stream().filter(ItemStack::isEmpty).count();
            require(free >= request.count, "msg.fangsu.ticketing.inventory_space_required");
        } else {
            ItemStack stack = player.getMainHandItem(); Card card = TicketCards.read(stack, at);
            quote.cardBefore = JSON.toJson(card);
            TicketCards.resolveNetworks(card, data);
            require(card.product.networks.contains(config.network), "msg.fangsu.ticketing.card_machine_network");
            quote.cardAfter = copy(card, Card.class);
            if ("recharge".equals(request.action)) {
                require(machine.kind() == 1 || machine.kind() == 2, "msg.fangsu.ticketing.recharge_machine");
                require(card.product.kind == Kind.IC && request.recharge > 0, "msg.fangsu.ticketing.invalid_recharge");
                quote.cardAfter.balance = Math.toIntExact((long) card.balance + request.recharge);
                quote.amount = request.recharge; quote.description = "ui.fangsu.ticketing.recharge";
            } else {
                require(machine.kind() == 2 && "adjust".equals(request.action), "msg.fangsu.ticketing.invalid_transaction");
                require(at != null && catalogue.station(config.network, at.key) != null, "msg.fangsu.ticketing.station_unbound");
                TicketEngine.Adjustment adjustment = TicketEngine.adjust(card, network, at, catalogue.stations(config.network));
                quote.cardAfter = adjustment.card(); quote.amount = adjustment.cost(); quote.description = adjustment.description();
            }
        }
        if ("mtr".equals(request.payment)) {
            require(config.mtrPayment, "msg.fangsu.ticketing.mtr_payment_disabled"); quote.paymentCount = quote.amount;
        } else {
            require("item".equals(request.payment) && config.itemPayment, "msg.fangsu.ticketing.invalid_payment");
            quote.paymentCount = money(((long) quote.amount + config.currencyValue-1) / config.currencyValue);
            currency(config);
        }
        return quote;
    }
    private static Item currency(TicketMachineConfig config) {
        ResourceLocation location = new ResourceLocation(config.currency);
        //#if MC_VERSION >= 11903
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(location);
        //#else
        //$$ Item item = net.minecraft.core.Registry.ITEM.get(location);
        //#endif
        require(item != Items.AIR, "msg.fangsu.ticketing.currency_missing"); return item;
    }
    public static void execute(ServerPlayer player, BlockEntityTicketMachine machine, Quote quote) {
        require(quote.expires >= System.currentTimeMillis(), "msg.fangsu.ticketing.quote_expired");
        require(quote.revision == TicketWorldData.get(player.getServer()).catalogue.revision
                && quote.config.equals(JSON.toJson(machine.ticketConfig)), "msg.fangsu.ticketing.configuration_changed");
        if (quote.cardBefore != null) require(quote.cardBefore.equals(JSON.toJson(TicketCards.read(player.getMainHandItem(), TicketStations.at(player.getCommandSenderWorld(), machine.getBlockPos())))), "msg.fangsu.ticketing.held_ticket_changed");
        if (quote.count > 0) require(player.getInventory().items.stream().filter(ItemStack::isEmpty).count() >= quote.count, "msg.fangsu.ticketing.inventory_full");
        // 校验完所有条件后付款；后续只执行已验证的赋值和发放操作。
        TicketMachineConfig config = machine.ticketConfig;
        if ("mtr".equals(quote.payment)) TicketStations.payMtr(player, quote.amount);
        else {
            Item item = currency(config); long available = 0;
            for (ItemStack stack : player.getInventory().items) if (stack.is(item) && (quote.cardBefore == null || stack != player.getMainHandItem())) available += stack.getCount();
            require(available >= quote.paymentCount, "msg.fangsu.ticketing.insufficient_items");
            int remaining = quote.paymentCount;
            for (ItemStack stack : player.getInventory().items) if (stack.is(item) && remaining > 0 && (quote.cardBefore == null || stack != player.getMainHandItem())) {
                int take = Math.min(stack.getCount(), remaining); stack.shrink(take); remaining -= take;
            }
        }
        if (quote.cardAfter != null) TicketCards.write(player.getMainHandItem(), quote.cardAfter);
        for (ItemStack stack : quote.issued) player.getInventory().add(stack);
        player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
    }
    private TicketTransactions() { }
}
