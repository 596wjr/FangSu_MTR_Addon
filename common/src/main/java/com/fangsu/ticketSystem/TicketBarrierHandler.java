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
        return handleResult(level, pos, player, hand, hit, extraConfigs, sendUpdateC2S).handled();
    }

    public enum Outcome {
        ACCEPTED(true), REJECTED(false), REJECTED_HANDLED(true), ALREADY_OPEN(false), NOT_HANDLED(false);
        private final boolean handled;
        Outcome(boolean handled) { this.handled = handled; }
        public boolean handled() { return handled; }
    }

    public static Outcome handleResult(
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit,
            Map<String, String> extraConfigs,
            Runnable sendUpdateC2S
    ) {
        if (level.isClientSide) return Outcome.NOT_HANDLED;

        boolean isOpen = Boolean.parseBoolean(extraConfigs.getOrDefault("isOpen", "false"));
        if (isOpen) return Outcome.ALREADY_OPEN;

        int fareType = 0; // 旧设备统一迁入默认MTR计费网络
        boolean isExit = Boolean.parseBoolean(extraConfigs.getOrDefault("isExit", "false"));
        boolean useCustomZone = Boolean.parseBoolean(extraConfigs.getOrDefault("useCustomZone", "false"));

        ItemStack stack = player.getItemInHand(hand);
        com.fangsu.ticketSystem.v2.TicketGate.bind(level,pos,extraConfigs);
        extraConfigs.putIfAbsent("ticketNetwork",com.fangsu.ticketSystem.v2.TicketDefinitions.DEFAULT_NETWORK);
        if(com.fangsu.ticketSystem.v2.TicketCards.isNative(stack))
            return com.fangsu.ticketSystem.v2.TicketGate.use(level,pos,player,extraConfigs,sendUpdateC2S,-1) ? Outcome.ACCEPTED : Outcome.REJECTED;
        if(!com.fangsu.ticketSystem.v2.TicketDefinitions.DEFAULT_NETWORK.equals(extraConfigs.get("ticketNetwork"))
                || Boolean.parseBoolean(extraConfigs.getOrDefault("ticketTransfer","false"))) {
            player.displayClientMessage(ComponentHelper.literal("此网络仅接受已授权的方速票卡"),true); return Outcome.REJECTED;
        }


        // 手持 Metropolis 车票/交通卡（反射检测，Metropolis 非方速前置）：按其 NBT 计费规则进出站
        if (MetropolisTicketUtil.isMetropolisItem(stack)) {
            String metroName;
            int metroZone;
            if (useCustomZone) {
                metroZone = Integer.parseInt(extraConfigs.getOrDefault("customZone", "0"));
                metroName = extraConfigs.getOrDefault("customDisplayName", "");
            } else {
                Station station = MtrTicketSystem.getStation(level, pos);
                if (station == null) {
                    player.displayClientMessage(
                            ComponentHelper.translatable("msg.fangsu.ticketbarrier.noStationArea"),
                            true
                    );
                    return Outcome.REJECTED;
                }
                metroName = station.name;
                metroZone = station.zone;
            }
            boolean metroSuccess = isExit
                    ? MetropolisTicketUtil.exit(level, player, stack, metroName, metroZone)
                    : MetropolisTicketUtil.enter(level, player, stack, metroName, metroZone);
            if (!metroSuccess) return Outcome.REJECTED;
            extraConfigs.put("isOpen", "true");
            sendUpdateC2S.run();
            return Outcome.ACCEPTED;
        }

        switch (fareType) {
            case 0:
                String dispName = "";
                int zone = 0;
                if (useCustomZone) {
                    zone = Integer.parseInt(extraConfigs.getOrDefault("customZone", "0"));
                    dispName = extraConfigs.getOrDefault("customDisplayName", "");
                } else {
                    Station station = MtrTicketSystem.getStation(level, pos);
                    if (station == null) {
                        // 车站区域未划定或闸机不在区域内时，静默返回会让玩家以为闸机坏了，给出明确提示
                        player.displayClientMessage(
                                ComponentHelper.translatable("msg.fangsu.ticketbarrier.noStationArea"),
                                true
                        );
                        return Outcome.REJECTED;
                    }
                    dispName = station.name;
                    zone = station.zone;
                }

                if (stack.isEmpty() || !(stack.getItem() instanceof TicketItem ticket)) {
                    if (!isExit) {
                        if (MtrTicketSystem.enter(level, dispName, zone, player)) {
                            extraConfigs.put("isOpen", "true");
                            sendUpdateC2S.run();
                            return Outcome.ACCEPTED;
                        }
                    } else {
                        if (MtrTicketSystem.exit(level, dispName, zone, player)) {
                            extraConfigs.put("isOpen", "true");
                            sendUpdateC2S.run();
                            return Outcome.ACCEPTED;
                        }
                    }
                    return Outcome.REJECTED_HANDLED;
                } else {
                    boolean success = isExit
                            ? ticket.exit(level, player, stack, new FareInfo(FareType.CUSTOM, zone, dispName))
                            : ticket.enter(level, player, stack, new FareInfo(FareType.CUSTOM, zone, dispName));
                    if (!success) return Outcome.REJECTED;
                    extraConfigs.put("isOpen", "true");
                    sendUpdateC2S.run();
                    return Outcome.ACCEPTED;
                }

            case 1:
                if (stack.isEmpty() || !(stack.getItem() instanceof TicketItem ticket)) {
                    MtrTicketSystem.addObjectivesIfMissing(level);
                    Score balance = MtrTicketSystem.getScore(level, player, MtrTicketSystem.BALANCE_OBJECTIVE);
                    int val = Integer.parseInt(extraConfigs.getOrDefault("fareVal", "10"));
                    if (balance.getScore() < val) {
                        player.displayClientMessage(ComponentHelper.translatable("gui.mtr.insufficient_balance", balance.getScore()), true);
                        return Outcome.REJECTED;
                    }
                    balance.add(-val);
                    player.displayClientMessage(ComponentHelper.translatable("msg.fangsu.ticketbarrier.fareOnce", val, balance.getScore()), true);
                    extraConfigs.put("isOpen", "true");
                    sendUpdateC2S.run();
                    return Outcome.ACCEPTED;
                } else {
                    int val = Integer.parseInt(extraConfigs.getOrDefault("fareVal", "10"));
                    boolean success = ticket.enter(level, player, stack, new FareInfo(FareType.FARE_ONCE, val, ""));
                    if (!success) return Outcome.REJECTED;
                    extraConfigs.put("isOpen", "true");
                    sendUpdateC2S.run();
                    return Outcome.ACCEPTED;
                }

            default:
                return Outcome.REJECTED;
        }
    }
}
