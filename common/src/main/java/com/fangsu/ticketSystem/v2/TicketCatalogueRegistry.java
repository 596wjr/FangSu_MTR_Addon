package com.fangsu.ticketSystem.v2;

import java.util.Map;
import java.util.Set;

import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

/** 扩展模组在通用入口登记票务目录，服务端加载世界时补齐缺失条目。 */
public final class TicketCatalogueRegistry {
    private static final Catalogue REGISTERED = new Catalogue();

    /**
     * 登记包含 networks、products 和可选 unions 的 JSON。键是稳定的带命名空间 ID，
     * 产品和联合组可引用同一批注册中的条目，也可引用已注册的条目及方速默认网络、联合组。
     * 请在模组初始化阶段调用。存档中已存在的同 ID 条目优先。
     */
    public static synchronized void register(String json) {
        Catalogue entries = JSON.fromJson(json, Catalogue.class);
        require(entries != null && entries.networks != null && entries.products != null && entries.unions != null,
                "票务扩展目录无效");

        Catalogue combined = new Catalogue();
        combined.networks.put(DEFAULT_NETWORK, new Network());
        combined.unions.put(DEFAULT_UNION, new NetworkUnion());
        combined.networks.putAll(REGISTERED.networks);
        combined.unions.putAll(REGISTERED.unions);
        combined.products.putAll(REGISTERED.products);
        entries.networks.forEach((id, network) -> {
            checkEntry(combined.networks, id, network);
            network.id = id;
            network.validate();
            combined.networks.put(id, network);
        });
        entries.unions.forEach((id, union) -> {
            checkEntry(combined.unions, id, union);
            union.id = id;
            union.validate(combined);
            combined.unions.put(id, union);
        });
        entries.products.forEach((id, product) -> {
            checkEntry(combined.products, id, product);
            product.id = id;
            product.validate(combined);
        });

        REGISTERED.networks.putAll(entries.networks);
        REGISTERED.unions.putAll(entries.unions);
        REGISTERED.products.putAll(entries.products);
    }

    private static void checkEntry(Map<String, ?> known, String id, Object entry) {
        require(entry != null && id.matches("[a-z0-9_.-]+:[a-z0-9_.-]+"), "票务扩展条目无效: " + id);
        require(!known.containsKey(id), "票务扩展 ID 重复: " + id);
    }

    static synchronized boolean addMissing(Catalogue catalogue, Set<String> removedNetworks, Set<String> removedProducts) {
        int size = catalogue.networks.size() + catalogue.unions.size() + catalogue.products.size();
        REGISTERED.networks.forEach((id, entry) -> {
            if (!removedNetworks.contains(id)) catalogue.networks.computeIfAbsent(id, key -> copy(entry, Network.class));
        });
        REGISTERED.unions.forEach((id, entry) -> {
            if (catalogue.networks.keySet().containsAll(entry.networks))
                catalogue.unions.computeIfAbsent(id, key -> copy(entry, NetworkUnion.class));
        });
        REGISTERED.products.forEach((id, entry) -> {
            if (!removedProducts.contains(id) && catalogue.unions.keySet().containsAll(entry.unions)
                    && catalogue.networks.keySet().containsAll(entry.networks)
                    && entry.legs.stream().allMatch(leg -> catalogue.networks.containsKey(leg.network)))
                catalogue.products.computeIfAbsent(id, key -> copy(entry, Product.class));
        });
        boolean updated = size != catalogue.networks.size() + catalogue.unions.size() + catalogue.products.size();
        for (var entry : REGISTERED.products.entrySet()) {
            Product current = catalogue.products.get(entry.getKey());
            if (current != null && current.pointToPointOnly == null && entry.getValue().pointToPointOnly != null) {
                current.pointToPointOnly = entry.getValue().pointToPointOnly;
                updated = true;
            }
        }
        return updated;
    }

    private TicketCatalogueRegistry() { }
}
