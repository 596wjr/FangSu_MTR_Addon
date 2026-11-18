package com.fangsu.ticketSystem.v2;

import com.fangsu.items.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

public final class TicketCards {
    public static final String KEY = "FangsuTicket";
    public static boolean isNative(ItemStack stack) {
        return stack.getItem() instanceof ItemSingleJourneyTicket || stack.getItem() instanceof ItemIcCard
                || stack.getItem() instanceof ItemNetworkTicket;
    }
    public static Card read(ItemStack stack, Station current) {
        require(isNative(stack) && stack.getCount() == 1, "请手持一张方速票卡");
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(KEY)) {
            Card card = JSON.fromJson(tag.getString(KEY), Card.class);
            require(card != null && card.version == 2 && card.serial != null && card.serial.length() <= 128
                    && card.product != null && card.product.kind != null && card.product.networks != null && card.product.badges != null
                    && card.product.legs != null && card.used != null, "票卡数据损坏");
            return card;
        }
        // 旧版票卡统一迁入默认MTR网络，保留余额、票额和分区入站记录。
        Card card = new Card(); Product product = new Product();
        product.kind = stack.getItem() instanceof ItemIcCard ? Kind.IC : Kind.SINGLE;
        product.id = product.kind == Kind.IC ? "fangsu:ic" : "fangsu:single";
        product.name = product.kind == Kind.IC ? "交通一卡通" : "单程票";
        if (product.kind == Kind.IC) product.style = "fangsu:ticketing/beijing_ic.json";
        if (product.kind == Kind.IC) {
            Badge badge = new Badge(); badge.name = "交通联合"; badge.texture = "fangsu:ticketing/transport_union.png"; product.badges.add(badge);
        }
        card.product = product;
        if (tag != null) {
            card.balance = tag.getInt("Balance"); card.allowance = Math.max(0, tag.getInt("Price"));
            if (tag.getBoolean("Entered")) {
                require(current != null, "迁移旧票入站记录需要车站信息");
                Journey journey = new Journey(); journey.network = DEFAULT_NETWORK; journey.rule = new Rule();
                journey.entry = copy(current, Station.class); journey.entry.key = "legacy";
                journey.entry.zones = new int[]{tag.contains("EntryZone1") ? tag.getInt("EntryZone1") : tag.getInt("EntryZone"), tag.getInt("EntryZone2"), tag.getInt("EntryZone3")};
                card.journey = journey;
            }
        }
        return card;
    }
    public static void write(ItemStack stack, Card card) {
        stack.getOrCreateTag().putString(KEY, JSON.toJson(card));
        // 保留可读的旧字段，旧提示及第三方只读集成仍可显示余额。
        stack.getOrCreateTag().putInt("Balance", card.balance);
        stack.getOrCreateTag().putInt("Price", card.allowance);
        stack.getOrCreateTag().putBoolean("Entered", card.journey != null);
    }
    public static ItemStack issue(Product product, int allowance, Catalogue catalogue) {
        var item = switch (product.kind) {
            case SINGLE -> ModItems.ITEM_SINGLE_JOURNEY_TICKET.get();
            case IC -> ModItems.ITEM_IC_CARD.get();
            case DAY -> ModItems.ITEM_DAY_TICKET.get();
            case ITINERARY -> ModItems.ITEM_ITINERARY_TICKET.get();
        };
        ItemStack stack = new ItemStack(item); Card card = new Card();
        card.product = copy(product, Product.class); card.product.badges.clear();
        card.product.networks = product.authorizedNetworks(catalogue);
        for (String id : product.unions) {
            NetworkUnion union = catalogue.unions.get(id);
            Badge badge = new Badge(); badge.name = union.name; badge.texture = union.texture; card.product.badges.add(badge);
        }
        card.allowance = allowance; card.balance = product.initialBalance;
        write(stack, card); return stack;
    }
    private TicketCards() { }
}
