package com.fangsu.network;

import com.fangsu.Main;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.turnout.TurnoutValidation;
import dev.architectury.networking.NetworkManager;

/**
 * 道岔绑定结果（S2C）的客户端落地逻辑。
 * <p>
 * 单独成类的原因：{@link TurnoutBindingPayload} 会在专用服务端启动期被加载，
 * 而这里的代码使用 {@code Minecraft}/{@code ClientLevel}/{@code LocalPlayer} 等客户端类型；
 * 只要这些类型出现在被加载类的字节码里，Fabric 就会拒绝加载并打挂服务端启动。
 * 本类只在收到 S2C 包（只存在于客户端）时才被首次加载。
 */
final class TurnoutBindingClient {

    private TurnoutBindingClient() {
    }

    static void handle(TurnoutBindingPayload.Payload payload, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> {
            final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            final net.minecraft.client.player.LocalPlayer player = minecraft.player;
            if (player == null || minecraft.level == null) return;

            // 诊断（每次回包一行）：把"服务端判定 → 客户端落地"这条链在日志里对齐
            Main.LOGGER.info("[turnout] binding applied pos={} success={} kind={} reason={}",
                    payload.pos(), payload.success(),
                    payload.success() ? "-" : payload.reasonArg(), payload.reasonKey());

            if (minecraft.level.getBlockEntity(payload.pos()) instanceof com.fangsu.blockEntities.BlockEntityTurnout turnout) {
                if (payload.success()) {
                    turnout.setBinding(payload.binding());
                } else {
                    turnout.markBuildFinished();
                }
            }

            // 绑定成功 → 只消耗本次完整绑定使用的节点标记
            if (payload.success() && payload.binding() != null) {
                final com.fangsu.turnout.TurnoutBinding used = payload.binding();
                final java.util.List<net.minecraft.core.BlockPos> consumed = used.markers().stream()
                        .map(com.fangsu.turnout.marker.NodeMarker::pos).distinct().toList();
                final int removed = com.fangsu.turnout.marker.NodeMarkerStore.getInstance().removeAll(consumed);
                // 重建时标记早就被消耗光了，removed == 0 属于正常情况，不刷日志
                if (removed > 0) {
                    Main.LOGGER.info("[fangsu][turnout] markers consumed={} toe={} straight={} branch={}",
                            removed, used.toe(), used.straightEnd(), used.branchEnd());
                }
            }

            if (payload.success()) {
                player.displayClientMessage(ComponentHelper.translatable(TurnoutValidation.BUILT), true);
            } else {
                // 动作栏提示（true = 不进聊天栏）：unsupported 的 %s 用类型名填
                final net.minecraft.network.chat.Component message =
                        payload.reasonArg() == null || payload.reasonArg().isEmpty()
                                ? ComponentHelper.translatable(payload.reasonKey())
                                : ComponentHelper.translatable(payload.reasonKey(),
                                ComponentHelper.translatable(payload.reasonArg()));
                player.displayClientMessage(message, true);
            }
        });
    }
}
