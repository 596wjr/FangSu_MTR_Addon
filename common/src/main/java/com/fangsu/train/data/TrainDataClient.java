package com.fangsu.train.data;

import com.fangsu.ui.TrainDataSenderScreen;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class TrainDataClient {
    private static final Map<Long, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Set<Long> PENDING = ConcurrentHashMap.newKeySet();
    private static volatile ClientLevel level;
    private static int tick;

    private static class Entry {
        volatile TrainDataSnapshot snapshot = TrainDataSnapshot.EMPTY;
        int lastSeen;
        int lastWatch = -100;
        boolean initial;
    }

    private TrainDataClient() {}

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, TrainDataNetwork.UPDATE, (buf, context) -> {
            ResourceLocation dimension = buf.readResourceLocation();
            long id = buf.readLong();
            boolean initial = buf.readBoolean();
            TrainDataSnapshot snapshot = TrainDataSnapshot.read(buf.readNbt());
            context.queue(() -> {
                ensureLevel(Minecraft.getInstance().level);
                Entry entry = ENTRIES.get(id);
                if (entry != null && level != null && dimension.equals(level.dimension().location())) {
                    entry.snapshot = snapshot;
                    entry.initial = initial;
                }
            });
        });
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, TrainDataNetwork.OPEN, (buf, context) -> {
            BlockPos pos = buf.readBlockPos();
            TrainDataSenderConfig config = TrainDataSenderConfig.read(buf.readNbt());
            context.queue(() -> Minecraft.getInstance().setScreen(new TrainDataSenderScreen(pos, config)));
        });
        ClientTickEvent.CLIENT_POST.register(client -> {
            ensureLevel(client.level);
            tick++;
            for (long id : PENDING) {
                if (PENDING.remove(id)) get(id);
            }
            if (tick % 40 == 0) ENTRIES.values().removeIf(entry -> tick - entry.lastSeen > 180);
        });
    }

    private static void ensureLevel(ClientLevel next) {
        if (next != level) {
            level = next;
            ENTRIES.clear();
            PENDING.clear();
        }
    }

    public static TrainDataSnapshot get(long id) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            PENDING.add(id);
            Entry existing = ENTRIES.get(id);
            return existing == null || client.level != level ? TrainDataSnapshot.EMPTY : existing.snapshot;
        }
        ensureLevel(client.level);
        if (level == null || client.getConnection() == null) return TrainDataSnapshot.EMPTY;
        Entry entry = ENTRIES.computeIfAbsent(id, ignored -> new Entry());
        entry.lastSeen = tick;
        if (tick - entry.lastWatch >= 100) {
            entry.lastWatch = tick;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeLong(id);
            NetworkManager.sendToServer(TrainDataNetwork.WATCH, buf);
        }
        return entry.snapshot;
    }

    public static boolean initial(long id) {
        Entry entry = ENTRIES.get(id);
        return entry != null && entry.initial;
    }

    public static void save(BlockPos pos, TrainDataSenderConfig config) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeNbt(config.write());
        NetworkManager.sendToServer(TrainDataNetwork.EDIT, buf);
    }
}
