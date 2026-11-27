package com.fangsu.train.data;

import com.fangsu.Main;
import com.fangsu.blockEntities.BlockEntityTrainDataSender;
import com.fangsu.blocks.BlockTrainDataSender;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class TrainDataNetwork {
    static final ResourceLocation UPDATE = new ResourceLocation(Main.MOD_ID, "train_data_update");
    static final ResourceLocation WATCH = new ResourceLocation(Main.MOD_ID, "train_data_watch");
    static final ResourceLocation EDIT = new ResourceLocation(Main.MOD_ID, "train_data_edit");
    static final ResourceLocation OPEN = new ResourceLocation(Main.MOD_ID, "train_data_open");

    private TrainDataNetwork() {}

    public static void register() {
        TrainDataRuntime.register();
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, WATCH, (buf, context) -> {
            long id = buf.readLong();
            context.queue(() -> {
                if (context.getPlayer() instanceof ServerPlayer player && level(player) instanceof ServerLevel level) {
                    TrainDataRuntime.subscribe(level, id, player);
                }
            });
        });
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, EDIT, (buf, context) -> {
            BlockPos pos = buf.readBlockPos();
            if (buf.readableBytes() > 262144) return;
            CompoundTag tag = buf.readNbt();
            context.queue(() -> {
                if (context.getPlayer() instanceof ServerPlayer player && canEdit(player, pos)
                        && (BlockTrainDataSender.isConfigTool(player.getMainHandItem()) || BlockTrainDataSender.isConfigTool(player.getOffhandItem()))
                        && level(player).getBlockEntity(pos) instanceof BlockEntityTrainDataSender sender) {
                    sender.setConfig(TrainDataSenderConfig.read(tag));
                }
            });
        });
    }

    public static net.minecraft.world.level.Level level(ServerPlayer player) {
        //#if MC_VERSION >= 12000
        return player.level();
        //#else
        //$$ return player.level;
        //#endif
    }

    public static boolean canEdit(ServerPlayer player, BlockPos pos) {
        return player.mayBuild() && level(player).mayInteract(player, pos) && level(player).hasChunkAt(pos)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    public static void open(ServerPlayer player, BlockPos pos, TrainDataSenderConfig config) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeNbt(config.write());
        NetworkManager.sendToPlayer(player, OPEN, buf);
    }

    public static void send(ServerPlayer player, long id, TrainDataSnapshot snapshot, boolean initial) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeResourceLocation(level(player).dimension().location());
        buf.writeLong(id);
        buf.writeBoolean(initial);
        buf.writeNbt(snapshot.write());
        NetworkManager.sendToPlayer(player, UPDATE, buf);
    }
}
