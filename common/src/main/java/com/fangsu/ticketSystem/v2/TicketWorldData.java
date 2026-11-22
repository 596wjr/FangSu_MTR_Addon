package com.fangsu.ticketSystem.v2;

import com.fangsu.Main;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

/** 全世界共用目录，设备绑定不会因区块卸载而丢失。 */
public final class TicketWorldData extends SavedData {
    public Catalogue catalogue = new Catalogue();
    private final Set<String> removedNetworks = new LinkedHashSet<>();
    private final Set<String> removedProducts = new LinkedHashSet<>();
    private final Map<String, Product> retiredIcProducts = new LinkedHashMap<>();
    private TicketWorldData() { }
    private void defaults() {
        if (catalogue.unions == null) catalogue.unions = new java.util.LinkedHashMap<>();
        if (!catalogue.networks.containsKey(DEFAULT_NETWORK)) {
            Network network = new Network(); network.id = DEFAULT_NETWORK; network.name = "默认MTR计费";
            catalogue.networks.put(network.id, network);
        }
        addDefault("fangsu:single", "单程票", Kind.SINGLE, 2);
        addDefault("fangsu:ic", "市政交通一卡通（交通联合款）", Kind.IC, 0);
        addDefault("fangsu:day", "市政交通一日通", Kind.DAY, 20);
        // 新产品只进入目录，既有设备的销售白名单不自动增加。
        addDay("fangsu:three_day", "市政交通三日通", 3, 60, "three_day_pass");
        addDay("fangsu:seven_day", "市政交通七日通", 7, 140, "seven_day_pass");
        Product day = catalogue.products.get("fangsu:day");
        if (day != null && day.kind == Kind.DAY && "fangsu:ticketing/single.json".equals(day.style)) {
            day.style = "fangsu:ticketing/day_pass.json";
            if ("一日票".equals(day.name)) day.name = "市政交通一日通";
        }
        if (!catalogue.unions.containsKey(DEFAULT_UNION)) {
            NetworkUnion union = new NetworkUnion(); union.id = DEFAULT_UNION; catalogue.unions.put(union.id, union);
        }
    }
    private Product addDefault(String id, String name, Kind kind, int price) {
        if (removedProducts.contains(id) || catalogue.products.containsKey(id)) return null;
        Product product = new Product(); product.id = id; product.name = name; product.kind = kind; product.price = price;
        if (kind == Kind.IC) { product.style = "fangsu:ticketing/beijing_ic.json"; product.unions.add(DEFAULT_UNION); }
        if (kind == Kind.DAY) product.style = "fangsu:ticketing/day_pass.json";
        catalogue.products.put(id, product);
        return product;
    }
    private void addDay(String id, String name, int days, int price, String style) {
        Product product = addDefault(id, name, Kind.DAY, price);
        if (product != null) {
            product.validDays = days;
            product.style = "fangsu:ticketing/" + style + ".json";
        }
    }
    public static TicketWorldData get(MinecraftServer server) {
        //#if MC_VERSION >= 12002
        //$$ return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(TicketWorldData::create, TicketWorldData::load, net.minecraft.util.datafix.DataFixTypes.LEVEL), "fangsu_ticketing");
        //#else
        return server.overworld().getDataStorage().computeIfAbsent(TicketWorldData::load, TicketWorldData::create, "fangsu_ticketing");
        //#endif
    }
    private static TicketWorldData create() {
        return new TicketWorldData().initialize();
    }
    private static TicketWorldData load(CompoundTag tag) {
        TicketWorldData data = new TicketWorldData();
        if (tag.contains("Catalogue")) {
            // 损坏的目录不可静默替换为空目录后覆盖存档。
            try { data.catalogue = JSON.fromJson(tag.getString("Catalogue"), Catalogue.class); }
            catch (RuntimeException ex) { Main.LOGGER.error("msg.fangsu.ticketing.catalogue_corrupt", ex); throw ex; }
            require(data.catalogue != null && data.catalogue.networks != null && data.catalogue.products != null
                    && data.catalogue.bindings != null, "msg.fangsu.ticketing.catalogue_corrupt");
        }
        if (tag.contains("RemovedNetworks")) data.removedNetworks.addAll(Arrays.asList(JSON.fromJson(tag.getString("RemovedNetworks"), String[].class)));
        if (tag.contains("RemovedProducts")) data.removedProducts.addAll(Arrays.asList(JSON.fromJson(tag.getString("RemovedProducts"), String[].class)));
        if (tag.contains("RetiredIcProducts")) {
            for (Product product : JSON.fromJson(tag.getString("RetiredIcProducts"), Product[].class))
                data.retiredIcProducts.put(product.id, product);
        }
        return data.initialize();
    }
    private TicketWorldData initialize() {
        defaults();
        if (TicketCatalogueRegistry.addMissing(catalogue, removedNetworks, removedProducts)) changed();
        return this;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", 2);
        tag.putString("Catalogue", JSON.toJson(catalogue));
        tag.putString("RemovedNetworks", JSON.toJson(removedNetworks));
        tag.putString("RemovedProducts", JSON.toJson(removedProducts));
        tag.putString("RetiredIcProducts", JSON.toJson(retiredIcProducts.values()));
        return tag;
    }
    void applyCatalogue(Catalogue proposed) {
        catalogue.networks.keySet().stream().filter(id -> !proposed.networks.containsKey(id)).forEach(removedNetworks::add);
        catalogue.products.keySet().stream().filter(id -> !proposed.products.containsKey(id)).forEach(removedProducts::add);
        removedNetworks.removeAll(proposed.networks.keySet());
        removedProducts.removeAll(proposed.products.keySet());
        // 已售 IC 卡继续按最后一版产品定义和当前联合组成员计算授权。
        catalogue.products.forEach((id, product) -> {
            Product replacement = proposed.products.get(id);
            if (product.kind == Kind.IC && (replacement == null || replacement.kind != Kind.IC))
                retiredIcProducts.put(id, copy(product, Product.class));
        });
        proposed.products.values().stream().filter(product -> product.kind == Kind.IC)
                .forEach(product -> retiredIcProducts.remove(product.id));
        catalogue.networks = proposed.networks;
        catalogue.products = proposed.products;
        catalogue.unions = proposed.unions;
        changed();
    }
    Product icProduct(String id) {
        Product product = catalogue.products.get(id);
        return product != null && product.kind == Kind.IC ? product : retiredIcProducts.get(id);
    }
    public void changed() { catalogue.revision++; setDirty(); }
}
