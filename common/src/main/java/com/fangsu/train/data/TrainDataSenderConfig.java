package com.fangsu.train.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record TrainDataSenderConfig(Set<Long> routes, Mode mode, String texture, List<Entry> entries) {
    public static final int LIMIT = 64;
    public static final TrainDataSenderConfig EMPTY = new TrainDataSenderConfig(Set.of(), Mode.ANY, "", List.of());
    private static final String PANTOGRAPH = "pantograph:";
    private static final String GROUP = PANTOGRAPH + "group:";

    public enum Mode { ANY, STOPPED, MOVING }
    public enum BowTarget { ALL, BOW, GROUP }

    /** 车厢 0 代表通配符，具体车厢以 1 为起点。 */
    public record Entry(int car, String key, String value) {
        public Entry {
            if (car < 0 || car > 999 || value.length() > 512) throw new IllegalArgumentException("Invalid car or value");
            if (key.startsWith(PANTOGRAPH)) {
                String target = key.substring(key.startsWith(GROUP) ? GROUP.length() : PANTOGRAPH.length());
                if (!(target.equals("*") && !key.startsWith(GROUP)) && !TrainDataSnapshot.validName(target)) {
                    throw new IllegalArgumentException("Invalid pantograph key");
                }
                value = value.trim().toUpperCase(Locale.ROOT);
                if (!value.equals("UP") && !value.equals("DOWN")) throw new IllegalArgumentException("Invalid pantograph state");
            } else if (!TrainDataSnapshot.validName(key)) {
                throw new IllegalArgumentException("Invalid train data key");
            }
        }

        public String carLabel() { return car == 0 ? "*" : Integer.toString(car); }

        public boolean isPantograph() { return key.startsWith(PANTOGRAPH); }

        public BowTarget bowTarget() {
            if (key.equals(PANTOGRAPH + "*")) return BowTarget.ALL;
            return key.startsWith(GROUP) ? BowTarget.GROUP : BowTarget.BOW;
        }

        public String bowName() {
            return key.substring(key.startsWith(GROUP) ? GROUP.length() : PANTOGRAPH.length());
        }

        public static Entry pantograph(int car, BowTarget target, String name, TrainDataSnapshot.PantographState state) {
            if (target != BowTarget.ALL && !TrainDataSnapshot.validName(name)) {
                throw new IllegalArgumentException("Invalid pantograph name");
            }
            String key = switch (target) {
                case ALL -> PANTOGRAPH + "*";
                case BOW -> PANTOGRAPH + name;
                case GROUP -> GROUP + name;
            };
            return new Entry(car, key, state.name());
        }

        private String identity() { return carLabel() + "/" + key; }
    }

    public TrainDataSenderConfig {
        routes = Set.copyOf(routes);
        entries = List.copyOf(entries);
        texture = texture.trim();
        if (entries.size() > LIMIT || entries.stream().map(Entry::identity).distinct().count() != entries.size()
                || texture.length() > 256 || !texture.isEmpty() && !validTexture(texture)) {
            throw new IllegalArgumentException("Invalid sender configuration");
        }
    }

    private static boolean validTexture(String texture) {
        ResourceLocation id = ResourceLocation.tryParse(texture);
        return id != null && id.getPath().startsWith("textures/") && id.getPath().endsWith(".png");
    }

    public boolean matches(long route, double speed) {
        return (routes.isEmpty() || routes.contains(route))
                && (speed < 0 || mode == Mode.ANY || mode == Mode.STOPPED && speed == 0 || mode == Mode.MOVING && speed > 0);
    }

    public TrainDataSnapshot data() {
        List<TrainDataSnapshot.Command> commands = new ArrayList<>();
        Map<String, String> fields = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (!entry.isPantograph()) {
                fields.put(TrainDataSnapshot.textKey(entry.car(), entry.key()), entry.value());
                continue;
            }
            String selector;
            if (entry.bowTarget() == BowTarget.GROUP) {
                String name = entry.bowName();
                selector = entry.car() == 0 ? "g:" + name : "g:" + entry.car() + "/" + name;
            } else {
                String name = entry.bowName();
                selector = entry.car() == 0 && name.equals("*") ? "*" : "p:" + entry.carLabel() + "/" + name;
            }
            commands.add(new TrainDataSnapshot.Command(selector, TrainDataSnapshot.PantographState.valueOf(entry.value())));
        }
        return new TrainDataSnapshot(commands.isEmpty() ? List.of() : List.of(commands), fields);
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putLongArray("routes", routes.stream().sorted().toList());
        tag.putString("mode", mode.name());
        tag.putString("texture", texture);
        ListTag rows = new ListTag();
        for (Entry entry : entries) {
            CompoundTag row = new CompoundTag();
            row.putInt("car", entry.car());
            row.putString("key", entry.key());
            row.putString("value", entry.value());
            rows.add(row);
        }
        tag.put("entries", rows);
        return tag;
    }

    public static TrainDataSenderConfig read(CompoundTag tag) {
        if (tag == null) return EMPTY;
        Set<Long> routes = new LinkedHashSet<>();
        for (long route : tag.getLongArray("routes")) {
            if (route != 0) routes.add(route);
        }
        Mode mode;
        try {
            mode = Mode.valueOf(tag.getString("mode"));
        } catch (IllegalArgumentException ignored) {
            mode = Mode.ANY;
        }
        String texture = tag.getString("texture").trim();
        if (texture.length() > 256 || !validTexture(texture)) texture = "";
        Map<String, Entry> rows = new LinkedHashMap<>();
        if (tag.contains("entries", Tag.TAG_LIST)) {
            for (Tag item : tag.getList("entries", Tag.TAG_COMPOUND)) {
                if (rows.size() >= LIMIT) break;
                CompoundTag row = (CompoundTag) item;
                try {
                    Entry entry = new Entry(row.getInt("car"), row.getString("key"), row.getString("value"));
                    rows.put(entry.identity(), entry);
                } catch (IllegalArgumentException ignored) {
                    // 跳过损坏的行，其余配置仍可使用。
                }
            }
        } else {
            // 迁移旧版的受电弓选择器和全列文本。
            TrainDataSnapshot previous = TrainDataSnapshot.read(tag.getCompound("data"));
            for (List<TrainDataSnapshot.Command> batch : previous.batches()) {
                for (TrainDataSnapshot.Command command : batch) {
                    String target = command.target();
                    int slash = target.indexOf('/');
                    int car = slash < 0 || target.charAt(2) == '*' ? 0 : Integer.parseInt(target.substring(2, slash));
                    String name = slash >= 0 ? target.substring(slash + 1) : target.equals("*") ? "*" : target.substring(2);
                    Entry entry = new Entry(car, (target.startsWith("g:") ? GROUP : PANTOGRAPH) + name, command.state().name());
                    rows.put(entry.identity(), entry);
                }
            }
            previous.texts().forEach((key, value) -> {
                if (TrainDataSnapshot.validName(key)) {
                    Entry entry = new Entry(0, key, value);
                    rows.put(entry.identity(), entry);
                }
            });
        }
        return new TrainDataSenderConfig(routes, mode, texture, rows.values().stream().limit(LIMIT).toList());
    }
}
