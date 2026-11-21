package com.fangsu.ticketSystem.v2;

import com.fangsu.Main;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

/** 全世界共用目录，设备绑定不会因区块卸载而丢失。 */
public final class TicketWorldData extends SavedData {
    public Catalogue catalogue = new Catalogue();
    public TicketWorldData() { defaults(); }
    private void defaults() {
        if (catalogue.unions == null) catalogue.unions = new java.util.LinkedHashMap<>();
        if (!catalogue.networks.containsKey(DEFAULT_NETWORK)) {
            Network network = new Network(); network.id = DEFAULT_NETWORK; network.name = "默认MTR计费";
            catalogue.networks.put(network.id, network);
        }
        if (!catalogue.products.containsKey("fangsu:single")) {
            add("fangsu:single", "单程票", Kind.SINGLE, 2);
            add("fangsu:ic", "市政交通一卡通（交通联合款）", Kind.IC, 0);
            add("fangsu:day", "市政交通一日通", Kind.DAY, 20);
        }
        // 新产品只进入目录，既有设备的销售白名单不自动增加。
        if (!catalogue.products.containsKey("fangsu:three_day")) addDay("fangsu:three_day", "市政交通三日通", 3, 60, "three_day_pass");
        if (!catalogue.products.containsKey("fangsu:seven_day")) addDay("fangsu:seven_day", "市政交通七日通", 7, 140, "seven_day_pass");
        Product day = catalogue.products.get("fangsu:day");
        if (day != null && day.kind == Kind.DAY && "fangsu:ticketing/single.json".equals(day.style)) {
            day.style = "fangsu:ticketing/day_pass.json";
            if ("一日票".equals(day.name)) day.name = "市政交通一日通";
        }
        if (!catalogue.unions.containsKey(DEFAULT_UNION)) {
            NetworkUnion union = new NetworkUnion(); union.id = DEFAULT_UNION; catalogue.unions.put(union.id, union);
        }
    }
    private void add(String id, String name, Kind kind, int price) {
        Product product = new Product(); product.id = id; product.name = name; product.kind = kind; product.price = price;
        if (kind == Kind.IC) { product.style = "fangsu:ticketing/beijing_ic.json"; product.unions.add(DEFAULT_UNION); }
        if (kind == Kind.DAY) product.style = "fangsu:ticketing/day_pass.json";
        catalogue.products.put(id, product);
    }
    private void addDay(String id, String name, int days, int price, String style) {
        add(id, name, Kind.DAY, price);
        Product product = catalogue.products.get(id); product.validDays = days; product.style = "fangsu:ticketing/" + style + ".json";
    }
    public static TicketWorldData get(MinecraftServer server) {
        //#if MC_VERSION >= 12002
        //$$ return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(TicketWorldData::new, TicketWorldData::load, net.minecraft.util.datafix.DataFixTypes.LEVEL), "fangsu_ticketing");
        //#else
        return server.overworld().getDataStorage().computeIfAbsent(TicketWorldData::load, TicketWorldData::new, "fangsu_ticketing");
        //#endif
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
        data.defaults();
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", 2); tag.putString("Catalogue", JSON.toJson(catalogue)); return tag;
    }
    public void changed() { catalogue.revision++; setDirty(); }
}
