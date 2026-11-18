package com.fangsu.ticketSystem.v2;

import java.util.*;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

/** 纯业务试算器：只修改副本；调用方在全部验证成功后一次替换原票卡。 */
public final class TicketEngine {
    public record Result(Card card, boolean consumed, int fare) { }
    public record Adjustment(Card card, int cost, String description) { }
    private static void authorized(Card card, String network) {
        require(card.product.networks.contains(network), "这张票卡不能用于此票务网络");
    }
    public static List<Integer> eligible(Card card, String network, Station at) {
        List<Integer> result = new ArrayList<>();
        if (card.product.kind != Kind.ITINERARY) return result;
        for (int i = 0; i < card.product.legs.size(); i++) {
            if (card.used.contains(i)) continue;
            Leg leg = card.product.legs.get(i);
            if (leg.network.equals(network) && (leg.from.isEmpty() || leg.from.equals(at.key))) result.add(i);
            if (card.product.ordered) break;
        }
        return result;
    }
    public static Result enter(Card original, Network network, Station at, int selectedLeg, long now) {
        Card card = copy(original, Card.class);
        authorized(card, network.id);
        require(card.journey == null && card.permit == null, "已在站内，请先出站");
        require(!card.expired(now), "票卡已过期，请办理补票");
        require(card.product.kind != Kind.IC || card.balance >= network.minimumBalance, "卡内余额低于最低进站金额");
        Journey journey = new Journey(); journey.network = network.id; journey.entry = copy(at, Station.class);
        journey.rule = copy(network.rule, Rule.class);
        if (card.product.kind == Kind.ITINERARY) {
            List<Integer> legs = eligible(card, network.id, at);
            require(!legs.isEmpty(), "没有可用于本站的联程权益");
            if (selectedLeg < 0 && legs.size() == 1) selectedLeg = legs.get(0);
            require(legs.contains(selectedLeg), "请选择本次使用的联程权益");
            journey.leg = selectedLeg;
            card.allowance = card.product.legs.get(selectedLeg).allowance;
        }
        if (card.activated == 0) card.activated = now;
        card.journey = journey;
        return new Result(card, false, 0);
    }
    public static Result exit(Card original, String network, Station at) {
        Card card = copy(original, Card.class); authorized(card, network);
        boolean permit = card.permit != null && Objects.equals(card.permit.ticket, card.serial)
                && Objects.equals(card.permit.network, network) && Objects.equals(card.permit.station, at.key);
        require(permit || card.journey != null, "缺失入站记录，请到补票机办理");
        if (card.journey != null) require(card.journey.network.equals(network), "请在入站网络出站");
        int fare = permit ? 0 : card.journey.rule.fare(card.journey.entry, at);
        Kind kind = card.product.kind;
        if (!permit) {
            if (kind == Kind.SINGLE) require(card.allowance >= fare, "票额不足，请补票");
            if (kind == Kind.ITINERARY) {
                Leg leg = card.product.legs.get(card.journey.leg);
                require(leg.to.isEmpty() || leg.to.equals(at.key), "目的站不符，请办理补票");
                require(card.allowance >= fare, "本段票额不足，请补票");
            }
            if (kind == Kind.IC) card.balance = Math.toIntExact((long) card.balance - fare);
        }
        if (kind == Kind.ITINERARY && card.journey != null) card.used.add(card.journey.leg);
        card.journey = null; card.permit = null;
        return new Result(card, kind == Kind.SINGLE || kind == Kind.ITINERARY && card.used.size() == card.product.legs.size(), fare);
    }
    public static Result transfer(Card original, Network from, Network to, Station at, int selectedLeg, long now) {
        require(!from.id.equals(to.id), "换乘的两个网络必须不同");
        require(original.product.kind == Kind.IC || original.product.kind == Kind.ITINERARY, "此票种不能刷换乘闸机");
        Result exited = exit(original, from.id, at);
        require(!exited.consumed(), "联程票没有剩余权益");
        Result entered = prepareEnter(exited.card(), to, at, selectedLeg, now);
        return new Result(entered.card(), false, exited.fare());
    }
    /** 保留统一进站入口，票额仅在开始新的一段时装入。 */
    public static Result prepareEnter(Card original, Network network, Station at, int selectedLeg, long now) {
        return enter(original, network, at, selectedLeg, now);
    }
    public static Adjustment adjust(Card original, Network network, Station at, List<Station> members) {
        Card card = copy(original, Card.class); authorized(card, network.id);
        require(card.permit == null, "已完成补票，请到指定车站出站");
        require(!members.isEmpty(), "网络内没有可计费车站");
        if (card.journey != null) require(card.journey.network.equals(network.id), "请在原入站网络补票");
        int fare;
        if (card.journey == null) {
            fare = members.stream().filter(s -> s.dimension.equals(at.dimension))
                    .mapToInt(s -> network.rule.fare(s, at)).max().orElseThrow(() -> new IllegalArgumentException("网络内没有同维度计费车站"));
        } else fare = card.journey.rule.fare(card.journey.entry, at);
        boolean wrongStation = card.product.kind == Kind.ITINERARY && card.journey != null
                && !card.product.legs.get(card.journey.leg).to.isEmpty()
                && !card.product.legs.get(card.journey.leg).to.equals(at.key);
        int credit = card.product.kind == Kind.SINGLE || card.product.kind == Kind.ITINERARY ? card.allowance : 0;
        int cost = card.journey == null ? fare : Math.max(0, fare - credit);
        if (card.journey != null && !wrongStation && (card.product.kind == Kind.SINGLE || card.product.kind == Kind.ITINERARY)) {
            card.allowance = Math.max(card.allowance, fare);
            return new Adjustment(card, cost, "补足本站出站票额");
        }
        if (card.journey != null && card.product.kind == Kind.DAY) cost = 0;
        if (card.product.kind == Kind.ITINERARY && card.journey == null) {
            int selected = -1;
            for (int i = 0; i < card.product.legs.size(); i++) {
                if (card.used.contains(i)) continue;
                if (card.product.legs.get(i).network.equals(network.id)) { selected = i; break; }
                if (card.product.ordered) break;
            }
            require(selected >= 0, "此网络没有可补票的剩余权益");
            Journey repaired = new Journey(); repaired.network = network.id; repaired.entry = copy(at, Station.class);
            repaired.rule = copy(network.rule, Rule.class); repaired.leg = selected; card.journey = repaired;
        }
        Permit permit = new Permit(); permit.network = network.id; permit.station = at.key; permit.ticket = card.serial; permit.paid = cost;
        card.permit = permit;
        return new Adjustment(card, cost, "本站一次性出站许可");
    }
    private TicketEngine() { }
}
