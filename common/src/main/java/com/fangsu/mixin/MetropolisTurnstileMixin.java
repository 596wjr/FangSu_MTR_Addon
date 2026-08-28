package com.fangsu.mixin;

import com.fangsu.items.TicketItem;
import com.fangsu.ticketSystem.FareInfo;
import com.fangsu.ticketSystem.FareType;
import com.fangsu.ticketSystem.MetropolisTicketUtil;
import com.fangsu.ticketSystem.MtrTicketSystem;
import mtr.data.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 Metropolis 闸机支持：空手使用 MTR 计费开门；手持方速单程票开门。
 * <p>
 * Metropolis 非方速前置，因此使用字符串 {@code targets} + {@code @Pseudo}：
 * 未安装 Metropolis 时该 mixin 静默跳过，不会导致方速启动失败。
 * 目标方法为 Metropolis 闸机方块的 {@code use}（intermediary 名 method_9534，MTR3 版包名
 * {@code team.dovecotmc.metropolis.block.BlockTurnstile}）。
 * <p>
 * 仅注入服务端：空手或方速票时按其系统进出站，成功后通过反射把闸机 OPEN 置为 true 并
 * 定时关闭（沿用 Metropolis 的 CLOSE_DELAY=80tick）；其余物品（Metropolis 自身车票/交通卡等）
 * 交给原闸机逻辑处理。
 */
@Pseudo
@Mixin(targets = "team.dovecotmc.metropolis.block.BlockTurnstile", remap = false)
public abstract class MetropolisTurnstileMixin {

    @Inject(
            method = "method_9534",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void fangsu$onUseMetropolisGate(
            BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (level.isClientSide) return;
        if (hand != InteractionHand.MAIN_HAND) return;

        boolean isEnter = fangsu$isEnterGate(state);

        // 获取闸机所在车站（无区域时交给原闸机逻辑处理）
        Station station = MtrTicketSystem.getStation(level, pos);
        if (station == null) return;
        String name = station.name;
        int zone = station.zone;

        ItemStack stack = player.getItemInHand(hand);

        // Metropolis 无限交通卡（创造模式）：始终放行
        if (MetropolisTicketUtil.isCreativeCard(stack, player)) {
            fangsu$openGate(level, pos, state);
            cir.setReturnValue(InteractionResult.SUCCESS);
            cir.cancel();
            return;
        }

        // Metropolis 出站票：出站时开门并消耗；进站时交给原闸机处理
        if (MetropolisTicketUtil.isExitTicket(stack)) {
            if (!isEnter) {
                stack.shrink(1);
                fangsu$openGate(level, pos, state);
                cir.setReturnValue(InteractionResult.SUCCESS);
                cir.cancel();
            }
            return;
        }

        // 方速单程票：进/出站并开闸
        if (stack.getItem() instanceof TicketItem ticket) {
            boolean ok = isEnter
                    ? ticket.enter(level, player, stack, new FareInfo(FareType.MTR, zone, name))
                    : ticket.exit(level, player, stack, new FareInfo(FareType.MTR, zone, name));
            if (ok) {
                fangsu$openGate(level, pos, state);
                cir.setReturnValue(InteractionResult.SUCCESS);
                cir.cancel();
            }
            return;
        }

        // 空手 → MTR 计费（与方速闸机效果一致）；若闸机槽0已插入卡片，则交给原闸机取回卡片
        if (stack.isEmpty()) {
            if (fangsu$slotHasItem(level, pos)) return;
            boolean ok = isEnter
                    ? MtrTicketSystem.enter(level, name, zone, player)
                    : MtrTicketSystem.exit(level, name, zone, player);
            if (ok) {
                fangsu$openGate(level, pos, state);
                cir.setReturnValue(InteractionResult.SUCCESS);
                cir.cancel();
            }
        }
        // 其余物品（Metropolis 车票/交通卡等）交给原闸机处理
    }

    /** 读取闸机 type 属性：0 = ENTER（入站），其余为出站/直接扣款。 */
    @Unique
    private static boolean fangsu$isEnterGate(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if ("type".equals(property.getName()) && property instanceof IntegerProperty integerProperty) {
                return state.getValue(integerProperty) == 0;
            }
        }
        return true;
    }

    /** 反射置 OPEN=true 并定时关闭，使 Metropolis 客户端渲染开闸动画。 */
    @Unique
    private static void fangsu$openGate(Level level, BlockPos pos, BlockState state) {
        try {
            BooleanProperty open = null;
            for (Property<?> property : state.getProperties()) {
                if ("open".equals(property.getName()) && property instanceof BooleanProperty booleanProperty) {
                    open = booleanProperty;
                    break;
                }
            }
            if (open == null) return;
            if (state.getValue(open)) return; // 已开
            level.setBlock(pos, state.setValue(open, true), 3);
            level.scheduleTick(pos, state.getBlock(), 80);
        } catch (Exception e) {
            com.fangsu.Main.LOGGER.warn("Failed to open metropolis gate: {}", e.toString());
        }
    }

    @Unique
    private static final String TURNSTILE_BE_CLASS = "team.dovecotmc.metropolis.block.entity.BlockEntityTurnstile";

    @Unique
    private static Class<?> fangsu$turnstileBeClass;
    @Unique
    private static boolean fangsu$turnstileBeLoaded = false;

    /** 反射读取 Metropolis 闸机方块实体槽0，判断是否已插入卡片（避免与取回卡片流程冲突）。 */
    @Unique
    private static boolean fangsu$slotHasItem(Level level, BlockPos pos) {
        try {
            if (!fangsu$turnstileBeLoaded) {
                fangsu$turnstileBeLoaded = true;
                fangsu$turnstileBeClass = Class.forName(TURNSTILE_BE_CLASS, false, MetropolisTurnstileMixin.class.getClassLoader());
            }
            if (fangsu$turnstileBeClass == null) return false;
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            if (be == null || !fangsu$turnstileBeClass.isInstance(be)) return false;
            java.util.List<?> items = (java.util.List<?>) fangsu$turnstileBeClass.getMethod("getItems").invoke(be);
            return items != null && !items.isEmpty() && !((net.minecraft.world.item.ItemStack) items.get(0)).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
