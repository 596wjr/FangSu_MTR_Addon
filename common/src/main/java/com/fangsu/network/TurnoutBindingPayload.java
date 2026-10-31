package com.fangsu.network;

import com.fangsu.Main;
import com.fangsu.mappings.ComponentHelper;
import com.fangsu.turnout.TurnoutBinding;
import com.fangsu.turnout.TurnoutValidation;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 道岔绑定结果（S2C）。
 * <p>
 * 成功：带完整 {@link TurnoutBinding}（客户端就地应用，并在动作栏提示 {@code fangsu.turnout.built}）；
 * 失败：只带提示键 + 一个格式化参数（"暂未支持 %s 道岔"里的 %s，见 {@code TurnoutKind#langKey()}）。
 * <p>
 * 服务端权威性由 {@link TurnoutBuildRequestPayload} 保证：客户端<b>不能</b>发本包，
 * 只有服务端在拓扑校验之后才会写出绑定。
 */
public final class TurnoutBindingPayload {

    public static final ResourceLocation CHANNEL = new ResourceLocation("fangsu", "turnout_binding");

    /**
     * @param reasonKey 提示键（成功时是 {@code fangsu.turnout.built}）
     * @param reasonArg 提示键的 %s 参数（类型语言键；仅用于 unsupported）
     */
    public record Payload(BlockPos pos, boolean success, String reasonKey, String reasonArg,
                          @Nullable TurnoutBinding binding) {
    }

    private TurnoutBindingPayload() {
    }

    public static void registerClient() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CHANNEL, TurnoutBindingPayload::handleClient);
    }

    // ==================== 服务端发包 ====================

    public static void sendSuccess(ServerPlayer player, BlockPos pos, @Nullable TurnoutBinding binding) {
        send(player, new Payload(pos, true, TurnoutValidation.BUILT, "", binding));
    }

    public static void sendFailure(ServerPlayer player, BlockPos pos, String reasonKey, String reasonArg) {
        send(player, new Payload(pos, false, reasonKey, reasonArg, null));
    }

    private static void send(ServerPlayer player, Payload payload) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(payload.pos());
        buf.writeBoolean(payload.success());
        buf.writeUtf(payload.reasonKey() == null ? "" : payload.reasonKey());
        buf.writeUtf(payload.reasonArg() == null ? "" : payload.reasonArg());
        TurnoutBinding.writeNullable(buf, payload.binding());
        NetworkManager.sendToPlayer(player, CHANNEL, buf);
    }

    static Payload read(FriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        final boolean success = buf.readBoolean();
        final String reasonKey = buf.readUtf();
        final String reasonArg = buf.readUtf();
        final TurnoutBinding binding = TurnoutBinding.readNullable(buf);
        return new Payload(pos, success, reasonKey, reasonArg, binding);
    }

    // ==================== 客户端处理（只在客户端加载） ====================

    private static void handleClient(FriendlyByteBuf buf, NetworkManager.PacketContext ctx) {
        // 落地逻辑整段放在 TurnoutBindingClient 里：本类在服务端启动期就会被加载，
        // 只要字节码里出现 Minecraft/ClientLevel/LocalPlayer 这些客户端类型，专用服务端就会拒绝加载本类。
        TurnoutBindingClient.handle(read(buf), ctx);
    }
}
