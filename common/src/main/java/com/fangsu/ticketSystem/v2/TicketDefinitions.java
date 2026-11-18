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
                throw new IllegalArgumentException("找不到同一维度的计费车站");
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
            throw new IllegalArgumentException("缺少无上限的最后一档");
        }
        public void validate() {
            require(mode != null && fixed >= 0, "收费模式或固定金额无效");
            if (mode != Pricing.DISTANCE) return;
            require(!tiers.isEmpty() && tiers.size() <= 64, "需要1至64档阶梯");
            double last = 0;
            for (int i = 0; i < tiers.size(); i++) {
                Tier tier = tiers.get(i);
                require(tier != null && Double.isFinite(tier.end) && tier.unit > 0 && tier.price >= 0,
                        "计费单位须大于0，单价不得为负");
                require(i == tiers.size() - 1 ? tier.end == -1 : tier.end > last, "上限须递增，最后一档上限为-1");
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
    }
    public static final class Leg {
        public String network = DEFAULT_NETWORK, from = "", to = "";
        public int allowance = 20;
    }
    public static final class NetworkUnion {
        public String id = "", name = "交通联合", texture = "fangsu:ticketing/transport_union.png";
        public boolean enabled = true;
        public Set<String> networks = new LinkedHashSet<>(List.of(DEFAULT_NETWORK));
    }
    public static final class Badge {
        public String name, texture;
    }
    public static final class Product {
        public String id = "", name = "新票务产品", style = "fangsu:ticketing/single.json";
        public Kind kind = Kind.SINGLE;
        public boolean enabled = true, ordered = true;
        public int price = 2, initialBalance, validHours;
        public Set<String> networks = new LinkedHashSet<>(List.of(DEFAULT_NETWORK));
        public Set<String> unions = new LinkedHashSet<>();
        public List<Badge> badges = new ArrayList<>();
        public List<Leg> legs = new ArrayList<>();
        public Set<String> authorizedNetworks(Catalogue catalogue) {
            Set<String> result = new LinkedHashSet<>(networks);
            for (String id : unions) {
                NetworkUnion union = catalogue.unions.get(id);
                require(union != null, "联合组不存在");
                result.addAll(union.networks);
            }
            return result;
        }
        public void validate(Catalogue catalogue) {
            Map<String, Network> known = catalogue.networks;
            require(id != null && id.matches("[a-z0-9_.:-]{1,128}") && name != null && !name.isBlank() && name.length() <= 128,
                    "产品ID或名称无效");
            require(kind != null && style != null && style.matches("[a-z0-9_.:/-]{1,256}"), "票种或票面资源无效");
            require(price >= 0 && initialBalance >= 0 && validHours >= 0 && validHours <= 876000, "金额或有效期无效");
            require(networks != null && unions != null && unions.size() <= 16 && networks.size() <= 128 && known.keySet().containsAll(networks), "请选择已有网络");
            Set<String> authorized = authorizedNetworks(catalogue);
            require(!authorized.isEmpty(), "产品至少需要一个网络或联合组授权");
            require(kind != Kind.DAY || authorized.size() == 1, "日票只能授权一个网络");
            require(legs != null && legs.size() <= 128 && (kind != Kind.ITINERARY || !legs.isEmpty()), "联程票需要乘车权益");
            for (Leg leg : legs) require(leg != null && authorized.contains(leg.network) && leg.from != null && leg.to != null
                    && leg.from.length() <= 256 && leg.to.length() <= 256 && leg.allowance >= 0, "联程权益无效或不在授权网络内");
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
        public long activated;
        public Set<Integer> used = new HashSet<>();
        public Journey journey;
        public Permit permit;
        public boolean expired(long now) {
            int hours = product.kind == Kind.DAY ? 24 : product.validHours;
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
    }
    public static <T> T copy(T value, Class<T> type) { return JSON.fromJson(JSON.toJson(value), type); }
    public static int money(long value) {
        require(value >= 0 && value <= Integer.MAX_VALUE, "金额超出范围");
        return (int) value;
    }
    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    private TicketDefinitions() { }
}
