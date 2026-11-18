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
        public String action = "buy", product = "fangsu:single", destination = "", payment = "mtr";
        public int count = 1, recharge = 10;
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
        Catalogue catalogue = TicketWorldData.get(player.getServer()).catalogue;
        TicketMachineConfig config = machine.ticketConfig;
        Network network = catalogue.networks.get(config.network);
        require(network != null && network.enabled, "设备网络已停用");
        Quote quote = new Quote(); quote.request = copy(request, Request.class); quote.payment = request.payment;
        quote.revision = catalogue.revision; quote.config = JSON.toJson(config); quote.expires = System.currentTimeMillis()+30000;
        Station at = TicketStations.at(player.getCommandSenderWorld(), machine.getBlockPos());
        if ("buy".equals(request.action)) {
            require(machine.kind() != 2, "补票机不出售票卡");
            Product product = catalogue.products.get(request.product);
            require(product != null && product.enabled && config.products.contains(product.id), "此机器不出售该票种");
            require(machine.kind() != 1 || product.kind == Kind.IC, "售卡机只出售IC卡");
            require(product.authorizedNetworks(catalogue).stream().allMatch(id -> catalogue.networks.containsKey(id) && catalogue.networks.get(id).enabled)
                    && product.unions.stream().allMatch(id -> catalogue.unions.get(id).enabled), "产品授权网络或联合组已停用");
            require(product.authorizedNetworks(catalogue).contains(config.network), "产品不属于设备网络");
            require(request.count > 0 && request.count <= 6, "一次可购买1至6张");
            int allowance = product.price, unitPrice = product.price;
            if (product.kind == Kind.SINGLE) {
                Station to = catalogue.station(config.network, request.destination);
                require(at != null && catalogue.station(config.network, at.key) != null && to != null, "请选择网络内有效目的站；本站需绑定闸机");
                allowance = network.rule.fare(at, to); unitPrice = allowance;
            } else if (product.kind == Kind.IC) unitPrice = money((long) product.price + product.initialBalance);
            quote.amount = money((long) unitPrice * request.count); quote.count = request.count;
            quote.description = product.name + " × " + request.count;
            for (int i = 0; i < request.count; i++) quote.issued.add(TicketCards.issue(product, allowance, catalogue));
            long free = player.getInventory().items.stream().filter(ItemStack::isEmpty).count();
            require(free >= request.count, "背包空间不足，请先腾出空位");
        } else {
            ItemStack stack = player.getMainHandItem(); Card card = TicketCards.read(stack, at);
            require(card.product.networks.contains(config.network), "此票卡未授权设备网络");
            quote.cardBefore = JSON.toJson(card); quote.cardAfter = copy(card, Card.class);
            if ("recharge".equals(request.action)) {
                require(card.product.kind == Kind.IC && request.recharge > 0, "请选择IC卡和有效充值金额");
                quote.cardAfter.balance = Math.toIntExact((long) card.balance + request.recharge);
                quote.amount = request.recharge; quote.description = "IC卡充值";
            } else {
                require(machine.kind() == 2 && "adjust".equals(request.action), "交易类型无效");
                require(at != null && catalogue.station(config.network, at.key) != null, "本站未绑定此网络闸机");
                TicketEngine.Adjustment adjustment = TicketEngine.adjust(card, network, at, catalogue.stations(config.network));
                quote.cardAfter = adjustment.card(); quote.amount = adjustment.cost(); quote.description = adjustment.description();
            }
        }
        if ("mtr".equals(request.payment)) {
            require(config.mtrPayment, "设备未启用MTR余额支付"); quote.paymentCount = quote.amount;
        } else {
            require("item".equals(request.payment) && config.itemPayment, "支付方式无效或未启用");
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
        require(item != Items.AIR, "物品货币不存在"); return item;
    }
    public static void execute(ServerPlayer player, BlockEntityTicketMachine machine, Quote quote) {
        require(quote.expires >= System.currentTimeMillis(), "报价已过期，请重新确认");
        require(quote.revision == TicketWorldData.get(player.getServer()).catalogue.revision
                && quote.config.equals(JSON.toJson(machine.ticketConfig)), "配置已变更，请重新报价");
        if (quote.cardBefore != null) require(quote.cardBefore.equals(JSON.toJson(TicketCards.read(player.getMainHandItem(), TicketStations.at(player.getCommandSenderWorld(), machine.getBlockPos())))), "手持票卡已变化");
        if (quote.count > 0) require(player.getInventory().items.stream().filter(ItemStack::isEmpty).count() >= quote.count, "背包空间不足");
        // 校验完所有条件后付款；后续只执行已验证的赋值和发放操作。
        TicketMachineConfig config = machine.ticketConfig;
        if ("mtr".equals(quote.payment)) TicketStations.payMtr(player, quote.amount);
        else {
            Item item = currency(config); long available = 0;
            for (ItemStack stack : player.getInventory().items) if (stack.is(item) && (quote.cardBefore == null || stack != player.getMainHandItem())) available += stack.getCount();
            require(available >= quote.paymentCount, "物品货币不足");
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
