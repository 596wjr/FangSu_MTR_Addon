package com.fangsu.ticketSystem;

import com.fangsu.items.TicketItem;
import mtr.mappings.Text;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import mtr.data.*;

public class MtrTicketSystem {
    //TODO 交通卡系统

    public static final String BALANCE_OBJECTIVE = "mtr_balance";
    protected static final String ENTRY_ZONE_OBJECTIVE = "mtr_entry_zone";

    private static final int BASE_FARE = 2;
    private static final int ZONE_FARE = 1;
    private static final int EVASION_FINE = 500;

    /* ===================== 公共入口 ===================== */

    public static boolean enter(Level world, String dispName, int zone, Player player) {
        addObjectivesIfMissing(world);

        Score balance = getScore(world, player, BALANCE_OBJECTIVE);
        Score entryZone = getScore(world, player, ENTRY_ZONE_OBJECTIVE);

        // 已入闸
        if (entryZone.getScore() != 0) {
            player.displayClientMessage(Text.translatable("gui.mtr.already_entered"), true);
            return false;
        }

        // 余额不足
        if (balance.getScore() < 0) {
            player.displayClientMessage(Text.translatable("gui.mtr.insufficient_balance", balance.getScore()), true);
            return false;
        }

        entryZone.setScore(encodeZone(zone));
        player.displayClientMessage(
                Text.translatable(
                        "gui.mtr.enter_barrier",
                        dispName.replace('|', ' '),
                        balance.getScore()
                ),
                true
        );
        return true;
    }

    public static boolean exit(Level world, String dispName, int zone, Player player) {

        addObjectivesIfMissing(world);

        Score balance = getScore(world, player, BALANCE_OBJECTIVE);
        Score entryZone = getScore(world, player, ENTRY_ZONE_OBJECTIVE);

        int entry = entryZone.getScore();
        int fare;

        if (entry == 0) {
            // 逃票
            fare = EVASION_FINE;
        } else {
            fare = calcFare(zone, decodeZone(entry));
            if (isConcessionary(player)) {
                fare = (int) Math.ceil(fare / 2F);
            }
        }

        entryZone.setScore(0);
        balance.add(-fare);

        player.displayClientMessage(
                Text.translatable(
                        "gui.mtr.exit_barrier",
                        dispName.replace('|', ' '),
                        fare,
                        balance.getScore()
                ),
                true
        );
        return true;
    }

    /* ===================== 内部工具 ===================== */

    protected static Station getStation(Level world, BlockPos pos) {
        RailwayData data = RailwayData.getInstance(world);
        if (data == null) return null;
        return RailwayData.getStation(data.stations, data.dataCache, pos);
    }

    public static void addObjectivesIfMissing(Level world) {
        try {
            world.getScoreboard().addObjective(
                    BALANCE_OBJECTIVE,
                    ObjectiveCriteria.DUMMY,
                    Text.literal("Balance"),
                    ObjectiveCriteria.RenderType.INTEGER
            );
        } catch (Exception ignored) {
        }

        try {
            world.getScoreboard().addObjective(
                    ENTRY_ZONE_OBJECTIVE,
                    ObjectiveCriteria.DUMMY,
                    Text.literal("Entry Zone"),
                    ObjectiveCriteria.RenderType.INTEGER
            );
        } catch (Exception ignored) {
        }
    }

    public static Score getScore(Level world, Player player, String name) {
        return world.getScoreboard().getOrCreatePlayerScore(
                player.getGameProfile().getName(),
                world.getScoreboard().getObjective(name)
        );
    }

    private static boolean isConcessionary(Player player) {
        return player.isCreative();
    }

    private static int encodeZone(int zone) {
        return zone >= 0 ? zone + 1 : zone;
    }

    private static int decodeZone(int zone) {
        return zone > 0 ? zone - 1 : zone;
    }

    public static int calcFare(int zone1, int zone2) {
        int distance = Math.abs(zone1 - zone2);
        return BASE_FARE + ZONE_FARE * distance;
    }
}
