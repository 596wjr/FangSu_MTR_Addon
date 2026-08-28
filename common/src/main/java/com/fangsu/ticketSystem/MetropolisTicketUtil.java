package com.fangsu.ticketSystem;

import com.fangsu.mappings.ComponentHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Metropolis（地铁模组，非方速前置）车票 / 交通卡 的反射适配工具。
 * <p>
 * Metropolis 的闸机与计费全部基于物品 NBT 实现，本工具通过 {@code Class.forName} 反射检测
 * 当前环境是否装有 Metropolis（兼容 MTR3 版 {@code team.dovecotmc.metropolis.item.*}
 * 与 MTR4 版 {@code team.dovecotmc.old.metropolis.item.*} 两套包名），并读写其标准 NBT 键
 * （{@code balance} / {@code entered_station} / {@code entered_zone}）。
 * 未安装 Metropolis 时所有方法静默返回 false / 不生效，不构成前置依赖。
 * <p>
 * 计费规则与 Metropolis 原版闸机一致：票价 = |出站区域 - 进站区域| + 1。
 * 交通卡（ItemCard）出站扣余额；单程票（ItemTicket）出站后消耗（shrink）；
 * 出站票（ItemExitTicket，仅出站）出站时消耗；无限交通卡（创造模式）始终放行。
 */
public final class MetropolisTicketUtil {

    private static final String BALANCE = "balance";
    private static final String ENTERED_STATION = "entered_station";
    private static final String ENTERED_ZONE = "entered_zone";

    private static Class<?> cardClass;
    private static Class<?> ticketClass;
    private static Class<?> exitTicketClass;
    private static boolean detected = false;

    private MetropolisTicketUtil() {
    }

    /* ===================== 反射检测 ===================== */

    private static void ensureDetected() {
        if (detected) return;
        detected = true;
        // 依次尝试 MTR3 / MTR4 的 Metropolis 物品类名
        cardClass = loadClass(
                "team.dovecotmc.metropolis.item.ItemCard",
                "team.dovecotmc.old.metropolis.item.ItemCard");
        ticketClass = loadClass(
                "team.dovecotmc.metropolis.item.ItemTicket",
                "team.dovecotmc.old.metropolis.item.ItemTicket");
        exitTicketClass = loadClass(
                "team.dovecotmc.metropolis.item.ItemExitTicket",
                "team.dovecotmc.old.metropolis.item.ItemExitTicket");
    }

    private static Class<?> loadClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name, false, MetropolisTicketUtil.class.getClassLoader());
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    /** 是否为 Metropolis 车票 / 交通卡 / 出站票（任意一种）。 */
    public static boolean isMetropolisItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ensureDetected();
        Class<?> itemClass = stack.getItem().getClass();
        return (cardClass != null && cardClass.isAssignableFrom(itemClass))
                || (ticketClass != null && ticketClass.isAssignableFrom(itemClass))
                || (exitTicketClass != null && exitTicketClass.isAssignableFrom(itemClass));
    }

    /** 是否仅出站票（IItemOpenGate）。 */
    public static boolean isExitTicket(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ensureDetected();
        return exitTicketClass != null && exitTicketClass.isAssignableFrom(stack.getItem().getClass());
    }

    /** 是否交通卡（余额扣费）。 */
    private static boolean isCard(ItemStack stack) {
        ensureDetected();
        return cardClass != null && cardClass.isAssignableFrom(stack.getItem().getClass());
    }

    /**
     * 是否为 Metropolis 无限交通卡（创造模式玩家专用）。
     * 该卡在闸机上始终放行：不记录进出站、不扣费、不消耗。
     */
    public static boolean isCreativeCard(ItemStack stack, Player player) {
        if (stack.isEmpty() || player == null || !player.isCreative()) return false;
        ensureDetected();
        if (cardClass == null || !cardClass.isAssignableFrom(stack.getItem().getClass())) return false;
        try {
            java.lang.reflect.Field infiniteBalance = cardClass.getField("infiniteBalance");
            return Boolean.TRUE.equals(infiniteBalance.get(stack.getItem()));
        } catch (Exception e) {
            return false;
        }
    }

    /* ===================== 计费 ===================== */

    /**
     * 进站。记录进站车站名与区域。
     *
     * @return 是否放行成功（成功后由调用方开闸）
     */
    public static boolean enter(Level level, Player player, ItemStack stack, String stationName, int zone) {
        if (!isMetropolisItem(stack)) return false;

        // 无限交通卡（创造模式）：始终放行
        if (isCreativeCard(stack, player)) return true;

        // 出站票不能进站
        if (isExitTicket(stack)) {
            player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.ticket.error"), true);
            return false;
        }

        CompoundTag tag = stack.getOrCreateTag();
        // 已在站内
        if (tag.contains(ENTERED_ZONE)) {
            player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.ticket.error"), true);
            return false;
        }

        tag.putString(ENTERED_STATION, stationName == null ? "" : stationName);
        tag.putInt(ENTERED_ZONE, zone);
        player.displayClientMessage(ComponentHelper.translatable(
                "msg.fangsu.ticket.enter1",
                (stationName == null ? "" : stationName).replace("|", " ")), true);
        return true;
    }

    /**
     * 出站。按 Metropolis 票价规则扣费（或消耗单程票），并清除进站记录。
     *
     * @return 是否放行成功
     */
    public static boolean exit(Level level, Player player, ItemStack stack, String stationName, int zone) {
        if (!isMetropolisItem(stack)) return false;

        // 无限交通卡（创造模式）：始终放行
        if (isCreativeCard(stack, player)) return true;

        // 出站票：出站后消耗
        if (isExitTicket(stack)) {
            stack.shrink(1);
            player.displayClientMessage(ComponentHelper.translatable(
                    "msg.fangsu.ticket.exit1",
                    (stationName == null ? "" : stationName).replace("|", " ")), true);
            return true;
        }

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ENTERED_ZONE)) {
            player.displayClientMessage(ComponentHelper.translatable("ui.fangsu.ticket.error"), true);
            return false;
        }

        int fare = Math.abs(zone - tag.getInt(ENTERED_ZONE)) + 1;
        int balance = tag.getInt(BALANCE);
        if (balance < fare) {
            player.displayClientMessage(ComponentHelper.translatable("gui.mtr.insufficient_balance", balance), true);
            return false;
        }

        tag.remove(ENTERED_STATION);
        tag.remove(ENTERED_ZONE);
        if (isCard(stack)) {
            // 交通卡：扣余额后保留
            tag.putInt(BALANCE, balance - fare);
        } else {
            // 单程票：出站后消耗
            stack.shrink(1);
        }
        player.displayClientMessage(ComponentHelper.translatable(
                "msg.fangsu.ticket.exit1",
                (stationName == null ? "" : stationName).replace("|", " ")), true);
        return true;
    }
}
