package com.fangsu.network;

import com.fangsu.items.ModItems;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 位移工具网络包。
 * <p>
 * C2S（服务端注册于 {@link com.fangsu.network.ModNetwork#init}）：
 * 客户端在右键轨道节点完成位移计算后，将目标坐标与朝向发给服务端执行传送。
 * 服务端只做纯 vanilla 校验与传送，不触碰任何 MTR 数据——消除原版 NTE 位移工具
 * 服务端直读 {@code RailwayData} 私有 rails map（mixin 绕过锁）的崩服风险。
 */
public class DisplacementToolPackets {

    /** C2S：客户端请求服务端将玩家传送到目标坐标 */
    public static final ResourceLocation TELEPORT = new ResourceLocation("fangsu", "displacement_tool_teleport");

    public static void registerServer() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, TELEPORT, DisplacementToolPackets::handleTeleport);
    }

    private static void handleTeleport(FriendlyByteBuf buf, NetworkManager.PacketContext ctx) {
        // 全部字段在网络线程读完，主线程只做校验与传送
        final BlockPos clickedPos = buf.readBlockPos();
        final double x = buf.readDouble();
        final double y = buf.readDouble();
        final double z = buf.readDouble();
        final float yaw = buf.readFloat();
        final float pitch = buf.readFloat();

        ctx.queue(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) return;
            // 校验 1：坐标与角度必须有限（拦截 NaN/Infinity，防止实体坐标损坏崩服）
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
            // 校验 2：世界边界
            if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) return;
            if (Math.abs(y) > 4096) return;
            // 校验 3：角度合理性（正常 yaw 范围很小，留 10 圈余量）
            if (Math.abs(yaw) > 3600f || Math.abs(pitch) > 3600f) return;
            // 校验 4：必须手持位移工具（主手或副手；用 getItem()== 保持 1.18.2 兼容）
            final ItemStack mainHand = player.getMainHandItem();
            final ItemStack offHand = player.getOffhandItem();
            if (!(mainHand.getItem() == ModItems.ITEM_DISPLACEMENT_TOOL.get()
                    || offHand.getItem() == ModItems.ITEM_DISPLACEMENT_TOOL.get())) return;
            // 校验 5：点击节点必须位于玩家附近（右键交互距离 5 格，16 格留足余量）
            final double dx = clickedPos.getX() + 0.5 - player.getX();
            final double dy = clickedPos.getY() + 0.5 - player.getY();
            final double dz = clickedPos.getZ() + 0.5 - player.getZ();
            if (dx * dx + dy * dy + dz * dz > 256) return;
            // 校验 6：连接必须有效（断线/死亡间隙）
            if (player.connection == null) return;
            //#if MC_VERSION >= 12000
            Level level = player.level();
            //#else
            //$$ Level level = player.level;
            //#endif
            if (!(level instanceof ServerLevel serverLevel)) return;
            player.teleportTo(serverLevel, x, y, z, yaw, pitch);
        });
    }

    /** 客户端：请求传送到目标坐标与朝向 */
    public static void sendTeleportC2S(BlockPos clickedPos, double x, double y, double z, float yaw, float pitch) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(clickedPos);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        NetworkManager.sendToServer(TELEPORT, buf);
    }
}
