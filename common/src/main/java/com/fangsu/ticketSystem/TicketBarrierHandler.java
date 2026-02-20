package com.fangsu.ticketSystem;

import com.fangsu.items.TicketItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Map;

public final class TicketBarrierHandler {

    private TicketBarrierHandler() {
    }

    public static InteractionResult handle(
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit,
            Map<String, String> extraConfigs,
            Runnable sendUpdateC2S
    ) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        boolean isOpen = Boolean.parseBoolean(extraConfigs.getOrDefault("isOpen", "false"));
        if (isOpen) return InteractionResult.PASS;

        int fareType = Integer.parseInt(extraConfigs.getOrDefault("fareType", "0"));
        boolean isExit = Boolean.parseBoolean(extraConfigs.getOrDefault("isExit", "false"));
        boolean useCustomZone = Boolean.parseBoolean(extraConfigs.getOrDefault("useCustomZone", "false"));

        switch (fareType) {
            case 0:
                if (useCustomZone) {
                    int customZone = Integer.parseInt(extraConfigs.getOrDefault("customZone", "0"));
                    String customDisplayName = extraConfigs.getOrDefault("customDisplayName", "");
                    ItemStack stack = player.getItemInHand(hand);
                    if (stack.isEmpty() || !(stack.getItem() instanceof TicketItem ticket)) {
                        player.displayClientMessage(Component.translatable("msg.fangsu.ticketbarrier.requireCard"), true);
                        return InteractionResult.PASS;
                    }
                    boolean success = isExit
                            ? ticket.exit(level, player, stack, new FareInfo(FareType.CUSTOM, customZone, customDisplayName))
                            : ticket.enter(level, player, stack, new FareInfo(FareType.CUSTOM, customZone, customDisplayName));
                    if (!success) return InteractionResult.PASS;
                    extraConfigs.put("isOpen", "true");
                    sendUpdateC2S.run();
                    return InteractionResult.SUCCESS;
                }
                if (!isExit) {
                    if (MtrTicketSystem.enter(level, pos, player)) {
                        extraConfigs.put("isOpen", "true");
                        sendUpdateC2S.run();
                        return InteractionResult.SUCCESS;
                    }
                } else {
                    if (MtrTicketSystem.exit(level, pos, player)) {
                        extraConfigs.put("isOpen", "true");
                        sendUpdateC2S.run();
                        return InteractionResult.SUCCESS;
                    }
                }
                return InteractionResult.PASS;

            case 1:
                MtrTicketSystem.addObjectivesIfMissing(level);
                var balance = MtrTicketSystem.getScore(level, player, MtrTicketSystem.BALANCE_OBJECTIVE);
                int val = Integer.parseInt(extraConfigs.getOrDefault("fareVal", "10"));
                if (balance.getScore() < val) {
                    player.displayClientMessage(Component.translatable("gui.mtr.insufficient_balance", balance.getScore()), true);
                    return InteractionResult.PASS;
                }
                balance.add(-val);
                player.displayClientMessage(Component.translatable("msg.fangsu.ticketbarrier.fareOnce", val, balance.getScore()), true);
                extraConfigs.put("isOpen", "true");
                sendUpdateC2S.run();
                return InteractionResult.SUCCESS;

            default:
                return InteractionResult.PASS;
        }
    }
}
