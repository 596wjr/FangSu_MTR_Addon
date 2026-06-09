package com.fangsu.ticketSystem;

import com.fangsu.mappings.ComponentHelper;
import com.fangsu.items.TicketItem;
import mtr.data.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.scores.Score;

import java.util.Map;

public final class TicketBarrierHandler {

    private TicketBarrierHandler() {
    }

    public static boolean handle(
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit,
            Map<String, String> extraConfigs,
            Runnable sendUpdateC2S
    ) {
        if (level.isClientSide) return false;

        boolean isOpen = Boolean.parseBoolean(extraConfigs.getOrDefault("isOpen", "false"));
        if (isOpen) return false;

        int fareType = Integer.parseInt(extraConfigs.getOrDefault("fareType", "0"));
        boolean isExit = Boolean.parseBoolean(extraConfigs.getOrDefault("isExit", "false"));
        boolean useCustomZone = Boolean.parseBoolean(extraConfigs.getOrDefault("useCustomZone", "false"));

        ItemStack stack = player.getItemInHand(hand);

        switch (fareType) {
            case 0:
                String dispName = "";
                int zone = 0;
                if (useCustomZone) {
                    zone = Integer.parseInt(extraConfigs.getOrDefault("customZone", "0"));
                    dispName = extraConfigs.getOrDefault("customDisplayName", "");
                } else {
                    Station station = MtrTicketSystem.getStation(level, pos);
                    if (station == null) return false;
                    dispName = station.name;
                    zone = station.zone;
                }

                if (stack.isEmpty() || !(stack.getItem() instanceof TicketItem ticket)) {
                    if (!isExit) {
                        if (MtrTicketSystem.enter(level, dispName, zone, player)) {
                            extraConfigs.put("isOpen", "true");
                            sendUpdateC2S.run();
                            return true;
                        }
                    } else {
                        if (MtrTicketSystem.exit(level, dispName, zone, player)) {
                            extraConfigs.put("isOpen", "true");
                            sendUpdateC2S.run();
                            return true;
                        }
                    }
                    return true;
                } else {
                    boolean success = isExit
                            ? ticket.exit(level, player, stack, new FareInfo(FareType.CUSTOM, zone, dispName))
                            : ticket.enter(level, player, stack, new FareInfo(FareType.CUSTOM, zone, dispName));
                    if (!success) return false;
                    extraConfigs.put("isOpen", "true");
                    sendUpdateC2S.run();
                    return true;
                }

            case 1:
                if (stack.isEmpty() || !(stack.getItem() instanceof TicketItem ticket)) {
                    MtrTicketSystem.addObjectivesIfMissing(level);
                    Score balance = MtrTicketSystem.getScore(level, player, MtrTicketSystem.BALANCE_OBJECTIVE);
                    int val = Integer.parseInt(extraConfigs.getOrDefault("fareVal", "10"));
                    if (balance.getScore() < val) {
                        player.displayClientMessage(ComponentHelper.translatable("gui.mtr.insufficient_balance", balance.getScore()), true);
                        return false;
                    }
                    balance.add(-val);
                    player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.ticketbarrier.fareOnce", val, balance.getScore()), true);
                    extraConfigs.put("isOpen", "true");
                    sendUpdateC2S.run();
                    return true;
                } else {
                    int val = Integer.parseInt(extraConfigs.getOrDefault("fareVal", "10"));
                    boolean success = ticket.enter(level, player, stack, new FareInfo(FareType.FARE_ONCE, val, ""));
                    if (!success) return false;
                    extraConfigs.put("isOpen", "true");
                    sendUpdateC2S.run();
                    return true;
                }

            default:
                return false;
        }
    }
}
