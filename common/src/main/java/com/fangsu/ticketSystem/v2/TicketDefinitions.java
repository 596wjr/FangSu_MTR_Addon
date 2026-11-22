package com.fangsu.ticketSystem.v2;

import com.google.gson.Gson;
import java.util.*;

/** 票务配置和票卡快照。金额均为整数元，ID 不随显示名称变化。 */
public final class TicketDefinitions {
    public static final String DEFAULT_NETWORK = "fangsu:default_mtr";
    public static final String DEFAULT_UNION = "fangsu:transport_union";
    public static final Gson JSON = new Gson();
    public enum Kind { SINGLE, IC, ITINERARY, DAY }
    public enum Pricing { MTR, FIXED, DISTANCE }
    public static final class Tier {
        public double end = -1;
        public int unit = 100, price = 1;
    }
    public static final class Rule {
        public Pricing mode = Pricing.MTR;
        public int fixed = 2, revision = 1;
        public List<Tier> tiers = new ArrayList<>();
        public int fare(Station from, Station to) {
            if (from == null || to == null || !Objects.equals(from.dimension, to.dimension))
                throw new IllegalArgumentException("msg.fangsu.ticketing.station_dimension");
            if (mode == Pricing.FIXED) return fixed;
            if (mode == Pricing.MTR) {
                long zones = 0;
                for (int i = 0; i < 3; i++) zones += Math.abs((long) from.zones[i] - to.zones[i]);
                return money(Math.addExact(2L, zones));
            }
            double distance = Math.hypot(to.x - from.x, to.z - from.z), start = 0;
            long amount = 0;
            for (Tier tier : tiers) {
                double end = tier.end < 0 ? Double.POSITIVE_INFINITY : tier.end;
                double length = Math.max(0, Math.min(distance, end) - start);
                amount = Math.addExact(amount, Math.multiplyExact((long) Math.ceil(length / tier.unit), tier.price));
                if (distance <= end) return money(amount);
                start = end;
            }
            throw new IllegalArgumentException("msg.fangsu.ticketing.missing_final_tier");
        }
        public void validate() {
            require(mode != null && fixed >= 0, "msg.fangsu.ticketing.invalid_pricing");
            if (mode != Pricing.DISTANCE) return;
            require(!tiers.isEmpty() && tiers.size() <= 64, "msg.fangsu.ticketing.tier_count");
            double last = 0;
            for (int i = 0; i < tiers.size(); i++) {
                Tier tier = tiers.get(i);
                require(tier != null && Double.isFinite(tier.end) && tier.unit > 0 && tier.price >= 0,
                        "msg.fangsu.ticketing.invalid_tier_unit");
                require(i == tiers.size() - 1 ? tier.end == -1 : tier.end > last, "msg.fangsu.ticketing.tier_limits");
                last = tier.end;
            }
        }
    }
    public static final class Station {
        public String key = "", dimension = "", name = "";
        public long id;
        public double x, z;
        public int[] zones = new int[3];
    }
    public static final class Network {
        public String id = "", name = "新票务网络";
        public boolean enabled = true;
        public int minimumBalance;
        public Rule rule = new Rule();
        public void validate() {
            require(id != null && id.matches("[a-z0-9_.:-]{1,128}") && name != null
                    && !name.isBlank() && name.length() <= 128 && minimumBalance >= 0 && rule != null,
                    "msg.fangsu.ticketing.invalid_network");
            rule.validate();
        }
    }
    public static final class Leg {
        public String network = DEFAULT_NETWORK, from = "", to = "";
        public int allowance = 20;
    }
    public static final class NetworkUnion {
        public String id = "", name = "交通联合", texture = "fangsu:ticketing/transport_union.png";
        public boolean enabled = true;
        public Set<String> networks = new LinkedHashSet<>(List.of(DEFAULT_NETWORK));
        public void validate(Catalogue catalogue) {
            require(id != null && id.matches("[a-z0-9_.:-]{1,128}") && name != null
                    && !name.isBlank() && name.length() <= 128 && texture != null && texture.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\.png")
                    && networks != null && !networks.isEmpty() && networks.size() <= 128 && catalogue.networks.keySet().containsAll(networks),
                    "msg.fangsu.ticketing.invalid_union");
        }
    }
    public static final class Badge {
        public String name, texture;
    }
    public static final class Product {
        public String id = "", name = "新票务产品", style = "fangsu:ticketing/single.json";
        public Kind kind = Kind.SINGLE;
        public Boolean pointToPointOnly;
        public boolean requiresStations() { return kind == Kind.SINGLE && Boolean.TRUE.equals(pointToPointOnly); }
        public boolean enabled = true, ordered = true;
        public int price = 2, initialBalance, validHours, validDays = 1;
        /** 旧票未保存天数时仍按一日票处理，已售票使用自身快照。 */
        public int durationHours() { return kind == Kind.DAY ? Math.max(1, validDays) * 24 : validHours; }
        public Set<String> networks = new LinkedHashSet<>(List.of(DEFAULT_NETWORK));
        public Set<String> unions = new LinkedHashSet<>();
        public List<Badge> badges = new ArrayList<>();
        public List<Leg> legs = new ArrayList<>();
        public Set<String> authorizedNetworks(Catalogue catalogue) {
            Set<String> result = new LinkedHashSet<>(networks);
            for (String id : unions) {
                NetworkUnion union = catalogue.unions.get(id);
                require(union != null, "msg.fangsu.ticketing.union_missing");
                result.addAll(union.networks);
            }
            return result;
        }
        public void validate(Catalogue catalogue) {
            Map<String, Network> known = catalogue.networks;
            require(id != null && id.matches("[a-z0-9_.:-]{1,128}") && name != null && !name.isBlank() && name.length() <= 128,
                    "msg.fangsu.ticketing.invalid_product_identity");
            require(kind != null && style != null && style.matches("[a-z0-9_.:/-]{1,256}"), "msg.fangsu.ticketing.invalid_ticket_style");
            require(price >= 0 && initialBalance >= 0 && validHours >= 0 && validHours <= 876000, "msg.fangsu.ticketing.invalid_amount_duration");
            require(validDays >= 1 && validDays <= 365, "msg.fangsu.ticketing.day_duration");
            require(networks != null && unions != null && unions.size() <= 16 && networks.size() <= 128 && known.keySet().containsAll(networks), "msg.fangsu.ticketing.select_existing_network");
            Set<String> authorized = authorizedNetworks(catalogue);
            require(!authorized.isEmpty(), "msg.fangsu.ticketing.product_authorization_required");
            require(kind != Kind.DAY || authorized.size() == 1, "msg.fangsu.ticketing.day_network_limit");
            require(legs != null && legs.size() <= 128 && (kind != Kind.ITINERARY || !legs.isEmpty()), "msg.fangsu.ticketing.itinerary_requires_legs");
            for (Leg leg : legs) require(leg != null && authorized.contains(leg.network) && leg.from != null && leg.to != null
                    && leg.from.length() <= 256 && leg.to.length() <= 256 && leg.allowance >= 0, "msg.fangsu.ticketing.invalid_leg");
        }
    }
    public static final class Journey {
        public String network;
        public Station entry;
        public Rule rule;
        public int leg = -1;
    }
    public static final class Permit {
        public String network, station, ticket;
        public int paid;
    }
    public static final class Card {
        public int version = 2;
        public String serial = UUID.randomUUID().toString();
        public Product product;
        public int balance, allowance;
        public String originName, destinationName, salesStationName;
        public long activated;
        public Set<Integer> used = new HashSet<>();
        public Journey journey;
        public Permit permit;
        public boolean expired(long now) {
            int hours = product.durationHours();
            return activated > 0 && hours > 0 && now >= activated + hours * 3600000L;
        }
    }
    public static final class Binding {
        public Station station;
        public Set<String> networks = new LinkedHashSet<>();
    }
    public static final class Catalogue {
        public long revision;
        public Map<String, Network> networks = new LinkedHashMap<>();
        public Map<String, Product> products = new LinkedHashMap<>();
        public Map<String, NetworkUnion> unions = new LinkedHashMap<>();
        public Map<String, Binding> bindings = new LinkedHashMap<>();
        public List<Station> stations(String network) {
            Map<String, Station> stations = new TreeMap<>();
            for (Binding binding : bindings.values()) if (binding.networks.contains(network))
                stations.put(binding.station.key, binding.station);
            return new ArrayList<>(stations.values());
        }
        public Station station(String network, String key) {
            return stations(network).stream().filter(s -> s.key.equals(key)).findFirst().orElse(null);
        }

        public void checkNetworkRemoval(String id) {
            require(!DEFAULT_NETWORK.equals(id), "msg.fangsu.ticketing.remove_default_network");
            require(bindings.values().stream().noneMatch(binding -> binding.networks.contains(id)),
                    "msg.fangsu.ticketing.remove_bound_network");
            require(unions.values().stream().noneMatch(union -> union.networks.contains(id))
                            && products.values().stream().noneMatch(product -> product.networks.contains(id)
                            || product.legs.stream().anyMatch(leg -> id.equals(leg.network))),
                    "msg.fangsu.ticketing.remove_referenced_network");
        }
    }
    public static <T> T copy(T value, Class<T> type) { return JSON.fromJson(JSON.toJson(value), type); }
    public static int money(long value) {
        require(value >= 0 && value <= Integer.MAX_VALUE, "msg.fangsu.ticketing.amount_range");
        return (int) value;
    }
    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    private TicketDefinitions() { }
}
