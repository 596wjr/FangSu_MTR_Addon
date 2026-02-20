package com.fangsu.ticketSystem;

import com.fangsu.items.TicketItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Map;

public final class TicketBarrierHandler {

    private TicketBarrierHandler() {
    }

    /**
     * 玩家在闸机使用票据
     * 支持：
     * - 纸质客票（fareType 0）
     * - 单次扣费（fareType 1）
     * - 单程票 / IC 卡通过 IJourneyTicket 接口扩展
     */
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

        switch (fareType) {

            case 0: // 纸质客票
                if (!isExit) { // entrance
                    if (MtrTicketSystem.enter(level, pos, player)) {
                        extraConfigs.put("isOpen", "true");
                        sendUpdateC2S.run();
                        return InteractionResult.SUCCESS;
                    } else return InteractionResult.PASS;
                } else { // exit
                    if (MtrTicketSystem.exit(level, pos, player)) {
                        extraConfigs.put("isOpen", "true");
                        sendUpdateC2S.run();
                        return InteractionResult.SUCCESS;
                    } else return InteractionResult.PASS;
                }

            case 1: // 单次扣费
                MtrTicketSystem.addObjectivesIfMissing(level);
                var balance = MtrTicketSystem.getScore(level, player, MtrTicketSystem.BALANCE_OBJECTIVE);
                int val = Integer.parseInt(extraConfigs.getOrDefault("fareVal", "10"));
                if (balance.getScore() < val) {
                    player.displayClientMessage(Component.translatable("gui.mtr.insufficient_balance", balance.getScore()), true);
                    return InteractionResult.PASS;
                } else {
                    balance.add(-val);
                    player.displayClientMessage(
                            Component.translatable("msg.fangsu.ticketbarrier.fareOnce", val, balance.getScore()),
                            true
                    );
                    extraConfigs.put("isOpen", "true");
                    sendUpdateC2S.run();
                    return InteractionResult.SUCCESS;
                }

            case 3: // 自定义计费模型（扩展 IJourneyTicket 逻辑可接入这里）
                // 如果玩家手持票据，实现 IJourneyTicket 接口就可以直接处理
                var stack = player.getItemInHand(hand);
                if (!stack.isEmpty() && stack.getItem() instanceof TicketItem ticket) {
                    boolean success = isExit
                            ? ticket.exit(level, player, stack, new FareInfo(FareType.CUSTOM, 0))
                            : ticket.enter(level, player, stack, new FareInfo(FareType.CUSTOM, 0));

                    if (success) {
                        extraConfigs.put("isOpen", "true");
                        sendUpdateC2S.run();
                        return InteractionResult.SUCCESS;
                    } else {
                        return InteractionResult.PASS;
                    }
                } else {
                    // fallback 提示
                    player.displayClientMessage(Component.translatable("刷卡入闸"), true);
                    extraConfigs.put("isOpen", "true");
                    sendUpdateC2S.run();
                    return InteractionResult.SUCCESS;
                }

            default:
                return InteractionResult.PASS;
        }
    }
}
