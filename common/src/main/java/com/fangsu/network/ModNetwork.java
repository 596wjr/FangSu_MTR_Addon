package com.fangsu.network;

import com.fangsu.Main;
import net.minecraft.core.Registry;
import com.fangsu.blockEntities.BlockEntityScreendoorCentralControl;
import com.fangsu.blockEntities.Syncable;
import com.fangsu.items.TicketItem;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
//#if MC_VERSION >= 11903
import net.minecraft.core.registries.BuiltInRegistries;
//#endif
//#if MC_VERSION >= 12000
import net.minecraft.core.registries.Registries;
//#endif
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ModNetwork {
    private static final int EMERALD_VALUE = 11;

    public static final ResourceLocation BE_SYNC =
            new ResourceLocation("fangsu", "be_sync");
    public static final ResourceLocation TICKET_MACHINE_SYNC =
            new ResourceLocation("fangsu", "ticket_machine_sync");
    public static final ResourceLocation CENTRAL_CONTROL_SYNC =
            new ResourceLocation("fangsu", "central_control_sync");

    public static void init() {
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                BE_SYNC,
                ModNetwork::handleBeSync
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                TICKET_MACHINE_SYNC,
                ModNetwork::ticketMachineSync
        );
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                CENTRAL_CONTROL_SYNC,
                ModNetwork::handleCentralControlSync
        );
    }

    private static void handleBeSync(
            FriendlyByteBuf buf,
            NetworkManager.PacketContext ctx
    ) {
        BlockPos pos = buf.readBlockPos();
        byte[] payload = new byte[buf.readableBytes()];
        buf.readBytes(payload);


        ctx.queue(() -> {
            ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) return;

            //#if MC_VERSION >= 12000
            Level level = player.level();
            //#else
            //$$ Level level = player.level;
            //#endif
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof Syncable syncable) {
                FriendlyByteBuf safeBuf =
                        new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(payload));

                syncable.readC2S(safeBuf);
//                be.setChanged();
//
//                level.sendBlockUpdated(
//                        pos,
//                        be.getBlockState(),
//                        be.getBlockState(),
//                        3
//                );
            }
        });
    }

    private static void ticketMachineSync(
            FriendlyByteBuf buf,
            NetworkManager.PacketContext context
    ) {
        ResourceLocation itemLocation = buf.readResourceLocation();
        int price = buf.readVarInt();
        int count = buf.readVarInt();

        context.queue(() -> {
            Main.LOGGER.info("1");
            ServerPlayer player = (ServerPlayer) context.getPlayer();
            if (player == null) return;

            // -------- 基础校验 --------
            if (price <= 0 || count <= 0 || count > 64) return;
            Main.LOGGER.info(itemLocation.toString());
            //#if MC_VERSION >= 11903
            Item item = BuiltInRegistries.ITEM.get(itemLocation);
            //#else
            //$$ Item item = net.minecraft.core.Registry.ITEM.get(itemLocation);
            //#endif
            if (!(item instanceof TicketItem ticketItem)) return;
            Main.LOGGER.info("2");

            int totalPrice = price * count;

            // -------- 创造模式：直接给 --------
            if (player.isCreative()) {
                ItemStack stack = ticketItem.createTicket(price);
                Main.LOGGER.info("giving {} stack {} for {}", count, stack, player);
                for (int i = 0; i < count; i++)
                    player.getInventory().add(stack.copy());
                return;
            }

            // -------- 计算绿宝石 --------
            int emeraldCost = totalPrice / EMERALD_VALUE;
            if (emeraldCost * EMERALD_VALUE < totalPrice) {
                emeraldCost++; // 不找零，向上取整
            }

            Inventory inv = player.getInventory();

            if (countItem(inv, Items.EMERALD) < emeraldCost) {
                return; // 钱不够
            }

            // -------- 扣钱 --------
            removeItem(inv, Items.EMERALD, emeraldCost);

            // -------- 给票 --------
            ItemStack stack = ticketItem.createTicket(price);
            Main.LOGGER.info("giving {} stack {} for {}", count, stack, player);
            for (int i = 0; i < count; i++)
                player.getInventory().add(stack.copy());
        });
    }

    private static int countItem(Inventory inv, Item item) {
        int count = 0;
        for (ItemStack stack : inv.items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeItem(Inventory inv, Item item, int amount) {
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (!stack.is(item)) continue;

            int remove = Math.min(stack.getCount(), amount);
            stack.shrink(remove);
            amount -= remove;

            if (stack.isEmpty()) {
                inv.items.set(i, ItemStack.EMPTY);
            }

            if (amount <= 0) {
                inv.setChanged();
                return;
            }
        }
        inv.setChanged();
    }

    private static void handleCentralControlSync(
            FriendlyByteBuf buf,
            NetworkManager.PacketContext ctx
    ) {
        BlockPos pos = buf.readBlockPos();
        byte[] payload = new byte[buf.readableBytes()];
        buf.readBytes(payload);

        ctx.queue(() -> {
            ServerPlayer player = (ServerPlayer) ctx.getPlayer();
            if (player == null) return;

            //#if MC_VERSION >= 12000
            Level level = player.level();
            //#else
            //$$ Level level = player.level;
            //#endif
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof BlockEntityScreendoorCentralControl ctrl) {
                FriendlyByteBuf safeBuf =
                        new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(payload));
                ctrl.readSync(safeBuf);
            }
        });
    }
}
