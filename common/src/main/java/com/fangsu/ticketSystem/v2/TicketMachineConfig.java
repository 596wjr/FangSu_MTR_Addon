package com.fangsu.ticketSystem.v2;

import java.util.*;
import static com.fangsu.ticketSystem.v2.TicketDefinitions.*;

/** 每台机器的销售白名单和支付配置。 */
public final class TicketMachineConfig {
    public String network = DEFAULT_NETWORK;
    public Set<String> products = new LinkedHashSet<>(List.of("fangsu:single", "fangsu:day", "fangsu:ic"));
    public String currency = "minecraft:emerald";
    public int currencyValue = 11;
    public boolean mtrPayment = true, itemPayment = true;
    public String model = "", text;
    public String shape = "[[0,0,0,16,32,8]]";
    public double screenX = 0, screenY = 1.42, screenZ = -.26, screenWidth = .68, screenHeight = .4, screenPitch;
    public int screenPixelsX = 256, screenPixelsY = 128;
    public void validate(Catalogue catalogue) {
        require(catalogue.networks.containsKey(network), "设备网络不存在");
        require(products != null && products.size() <= 128 && catalogue.products.keySet().containsAll(products), "销售产品无效");
        require(currency != null && currency.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") && currencyValue > 0, "物品货币或兑换比例无效");
        require(model != null && model.length() <= 256 && (model.isEmpty() || model.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\.(obj|bbmodel)")), "模型须为obj或bbmodel");
        require(text == null || text.length() <= 512, "屏幕文字过长");
        require(shape != null && shape.length() <= 4096, "碰撞形状无效");
        var boxes = JSON.fromJson(shape, double[][].class);
        require(boxes != null && boxes.length <= 64, "碰撞盒数量无效");
        for (double[] box : boxes) {
            require(box != null && box.length == 6, "碰撞盒需包含6个坐标");
            for (double value : box) require(Double.isFinite(value) && Math.abs(value) <= 256, "碰撞坐标越界");
            require(box[0] <= box[3] && box[1] <= box[4] && box[2] <= box[5], "碰撞盒起点须小于终点");
        }
        require(Double.isFinite(screenX) && Double.isFinite(screenY) && Double.isFinite(screenZ)
                && Math.abs(screenX) <= 16 && Math.abs(screenY) <= 16 && Math.abs(screenZ) <= 16
                && Double.isFinite(screenWidth) && Double.isFinite(screenHeight) && screenWidth > 0 && screenWidth <= 16
                && screenHeight > 0 && screenHeight <= 16 && screenPixelsX >= 16 && screenPixelsX <= 1024
                && screenPixelsY >= 16 && screenPixelsY <= 1024, "屏幕尺寸或位置无效");
        require(Double.isFinite(screenPitch) && Math.abs(screenPitch)<=90,"屏幕倾斜角无效");
    }
}
