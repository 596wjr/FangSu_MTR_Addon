package com.fangsu.train.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TrainStationData extends SavedData {
    private static final String NAME = "fangsu_train_station_data";
    private static final int LIMIT = 4096;
    private final Map<Key, TrainDataSnapshot> stations = new LinkedHashMap<>(16, 0.75f, true);

    public record Key(String dimension, long station, long route, boolean reverse, String consist) {}

    public static TrainStationData get(MinecraftServer server) {
        //#if MC_VERSION >= 12002
        //$$ return server.overworld().getDataStorage().computeIfAbsent(
        //$$         new SavedData.Factory<>(TrainStationData::new, TrainStationData::load, net.minecraft.util.datafix.DataFixTypes.LEVEL), NAME);
        //#else
        return server.overworld().getDataStorage().computeIfAbsent(TrainStationData::load, TrainStationData::new, NAME);
        //#endif
    }

    public TrainDataSnapshot visit(Key key, TrainDataSnapshot current) {
        TrainDataSnapshot previous = stations.getOrDefault(key, TrainDataSnapshot.EMPTY);
        TrainDataSnapshot result = previous.overlay(current);
        if (!previous.equals(result)) {
            stations.put(key, result);
            if (stations.size() > LIMIT) stations.remove(stations.keySet().iterator().next());
            setDirty();
        }
        return result;
    }

    private static TrainStationData load(CompoundTag tag) {
        TrainStationData data = new TrainStationData();
        for (Tag item : tag.getList("stations", Tag.TAG_COMPOUND)) {
            if (data.stations.size() >= LIMIT) break;
            CompoundTag row = (CompoundTag) item;
            Key key = new Key(row.getString("dimension"), row.getLong("station"), row.getLong("route"),
                    row.getBoolean("reverse"), row.getString("consist"));
            data.stations.put(key, TrainDataSnapshot.read(row.getCompound("data")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag rows = new ListTag();
        stations.forEach((key, snapshot) -> {
            CompoundTag row = new CompoundTag();
            row.putString("dimension", key.dimension());
            row.putLong("station", key.station());
            row.putLong("route", key.route());
            row.putBoolean("reverse", key.reverse());
            row.putString("consist", key.consist());
            row.put("data", snapshot.write());
            rows.add(row);
        });
        tag.putInt("version", 1);
        tag.put("stations", rows);
        return tag;
    }
}
