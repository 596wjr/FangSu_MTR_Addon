package com.fangsu.network;

import com.fangsu.items.ModItems;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 混合构建器网络包。
 * <p>
 * C2S（服务端注册于 {@link com.fangsu.network.ModNetwork#init}）：
 * 客户端编辑器每次改动后整栈同步主手物品 NBT，保证服务端构建时读到最新任务。
 * S2C（客户端注册于 {@link HybridCreatorClient#init}）：
 * 服务端右键物品时打开编辑器屏幕。
 */
public class HybridCreatorPackets {

    /** C2S：客户端编辑器改动后整栈同步主手物品 NBT */
    public static final ResourceLocation UPDATE_HOLDING_ITEM = new ResourceLocation("fangsu", "hybrid_creator_update_holding_item");
    /** S2C：服务端右键开编辑器屏幕 */
    public static final ResourceLocation OPEN_SCREEN = new ResourceLocation("fangsu", "hybrid_creator_open_screen");

    public static void registerServer() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, UPDATE_HOLDING_ITEM, HybridCreatorPackets::handleUpdateHoldingItem);
    }

    public static void registerClient() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, OPEN_SCREEN, HybridCreatorPackets::handleOpenScreen);
    }

    private static void handleUpdateHoldingItem(FriendlyByteBuf buf, NetworkManager.PacketContext ctx) {
        final CompoundTag tag = buf.readNbt();
        ctx.queue(() -> {
            final ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null || tag == null) return;
            // 校验：只接受主手拿着混合构建器时的同步
            final ItemStack stack = player.getMainHandItem();
            if (!stack.is(ModItems.ITEM_HYBRID_CREATOR.get())) return;
            stack.setTag(tag);
        });
    }

    private static void handleOpenScreen(FriendlyByteBuf buf, NetworkManager.PacketContext ctx) {
        ctx.queue(() -> {
            final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            minecraft.setScreen(com.fangsu.ui.HybridCreatorScreen.createScreen(null));
        });
    }

    /** 服务端：通知客户端打开混合构建器编辑器（MTR3 无 mapping holder，直接收原版 ServerPlayer） */
    public static void sendOpenScreenS2C(ServerPlayer player) {
        // Architectury 9.x 的 sendToPlayer 只收预构建的 FriendlyByteBuf（无 Consumer 重载）
        NetworkManager.sendToPlayer(player, OPEN_SCREEN, new FriendlyByteBuf(Unpooled.buffer()));
    }

    /** 客户端：整栈同步主手物品 NBT 到服务端 */
    public static void sendUpdateHoldingItemC2S(CompoundTag tag) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeNbt(tag);
        NetworkManager.sendToServer(UPDATE_HOLDING_ITEM, buf);
    }
}
