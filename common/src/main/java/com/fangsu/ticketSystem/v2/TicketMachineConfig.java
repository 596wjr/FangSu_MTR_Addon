package com.fangsu.ticketSystem.v2;

import java.util.*;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

/** 每台机器的销售白名单和支付配置。 */
public final class TicketMachineConfig {
    public String network = DEFAULT_NETWORK;
    public Set<String> products = new LinkedHashSet<>(List.of("fangsu:single"));
    public String currency = "minecraft:emerald";
    public int currencyValue = 11;
    public boolean mtrPayment = true, itemPayment = true;
    public String model = "", modelPreset = "", text;
    public boolean flipV = true;
    public double modelOffsetZ = .15625;
    public int screenCycleSeconds = 5;
    public String shape = "[[0,0,0,16,32,8]]";
    public double screenX = 0, screenY = 1.42, screenZ = -.26, screenWidth = .68, screenHeight = .4, screenPitch;
    public int screenPixelsX = 256, screenPixelsY = 128;
    public void validate(Catalogue catalogue) {
        validate(catalogue, -1);
    }
    public void validate(Catalogue catalogue, int machineKind) {
        require(catalogue.networks.containsKey(network), "msg.fangsu.ticketing.device_network_missing");
        require(products != null && products.size() <= 128 && catalogue.products.keySet().containsAll(products), "msg.fangsu.ticketing.invalid_sale_products");
        if (machineKind >= 0 && machineKind != 2)
            for (String id : products)
                require(canSell(catalogue.products.get(id), catalogue, network, machineKind), "msg.fangsu.ticketing.invalid_sale_products");
        require(currency != null && currency.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") && currencyValue > 0, "msg.fangsu.ticketing.invalid_currency");
        require(model != null && model.length() <= 256 && (model.isEmpty() || model.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\.(obj|bbmodel)")), "msg.fangsu.ticketing.invalid_model_format");
        require(modelPreset != null && modelPreset.length() <= 256 && (modelPreset.isEmpty() || modelPreset.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\.json")), "msg.fangsu.ticketing.invalid_model_preset");
        require(text == null || text.length() <= 512, "msg.fangsu.ticketing.screen_text_length");
        require(screenCycleSeconds >= 1 && screenCycleSeconds <= 3600, "msg.fangsu.ticketing.screen_interval");
        require(Double.isFinite(modelOffsetZ) && Math.abs(modelOffsetZ) <= 16, "msg.fangsu.ticketing.invalid_model_offset");
        require(shape != null && shape.length() <= 4096, "msg.fangsu.ticketing.invalid_shape");
        var boxes = JSON.fromJson(shape, double[][].class);
        require(boxes != null && boxes.length <= 64, "msg.fangsu.ticketing.shape_count");
        for (double[] box : boxes) {
            require(box != null && box.length == 6, "msg.fangsu.ticketing.shape_dimensions");
            for (double value : box) require(Double.isFinite(value) && Math.abs(value) <= 256, "msg.fangsu.ticketing.shape_coordinates");
            require(box[0] <= box[3] && box[1] <= box[4] && box[2] <= box[5], "msg.fangsu.ticketing.shape_order");
        }
        require(Double.isFinite(screenX) && Double.isFinite(screenY) && Double.isFinite(screenZ)
                && Math.abs(screenX) <= 16 && Math.abs(screenY) <= 16 && Math.abs(screenZ) <= 16
                && Double.isFinite(screenWidth) && Double.isFinite(screenHeight) && screenWidth > 0 && screenWidth <= 16
                && screenHeight > 0 && screenHeight <= 16 && screenPixelsX >= 16 && screenPixelsX <= 1024
                && screenPixelsY >= 16 && screenPixelsY <= 1024, "msg.fangsu.ticketing.invalid_screen_geometry");
        require(Double.isFinite(screenPitch) && Math.abs(screenPitch)<=90,"msg.fangsu.ticketing.invalid_screen_pitch");
    }
    public static String defaultText(int kind) {
        return switch(kind){case 1 -> "正常服务\nIn Service";case 2 -> "补票\nFare Adjustment";default -> "服务中|In Service";};
    }
    public List<Product> saleProducts(Catalogue catalogue, int machineKind) {
        List<Product> result = new ArrayList<>();
        if (products == null) return result;
        for (String id : products) {
            Product product = catalogue.products.get(id);
            if (canSell(product, catalogue, network, machineKind)) result.add(product);
        }
        return result;
    }
    public List<Product> availableProducts(Catalogue catalogue, int machineKind) {
        return catalogue.products.values().stream()
                .filter(product -> canSell(product, catalogue, network, machineKind))
                .toList();
    }
    public static boolean canSell(Product product, Catalogue catalogue, String network, int machineKind) {
        if (product == null || !product.enabled || !sells(machineKind, product.kind)) return false;
        if (product.networks == null || product.unions == null) return false;
        Set<String> authorized = new LinkedHashSet<>(product.networks);
        for (String id : product.unions) {
            NetworkUnion union = catalogue.unions.get(id);
            if (union == null || !union.enabled || union.networks == null) return false;
            authorized.addAll(union.networks);
        }
        if (!authorized.contains(network)) return false;
        for (String id : authorized) {
            Network candidate = catalogue.networks.get(id);
            if (candidate == null || !candidate.enabled) return false;
        }
        return true;
    }
    public static boolean sells(int machineKind, Kind kind) {
        return machineKind == 0 ? kind == Kind.SINGLE || kind == Kind.ITINERARY
                : machineKind == 1 && (kind == Kind.IC || kind == Kind.DAY);
    }
}
