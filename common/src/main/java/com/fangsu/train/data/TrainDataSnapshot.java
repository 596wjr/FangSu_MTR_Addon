package com.fangsu.train.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TrainDataSnapshot(List<List<Command>> batches, Map<String, String> texts) {
    public static final int MAX_FIELDS = 128;
    public static final TrainDataSnapshot EMPTY = new TrainDataSnapshot(List.of(), Map.of());

    public TrainDataSnapshot {
        batches = batches.stream().map(List::copyOf).toList();
        texts = Collections.unmodifiableMap(new LinkedHashMap<>(texts));
    }

    public enum PantographState { UNKNOWN, UP, DOWN }

    public record Command(String target, PantographState state) {
        public Command {
            if (!validTarget(target) || state == PantographState.UNKNOWN) throw new IllegalArgumentException("Invalid pantograph command");
        }

        private boolean matches(int car, String id, String group) {
            return target.equals("*") || target.equals("p:*/*") || target.equals("g:" + group) || target.equals("g:" + car + "/" + group)
                    || target.equals("p:" + car + "/" + id) || target.equals("p:" + car + "/*")
                    || target.equals("p:*/" + id);
        }

        private int specificity() {
            if (target.equals("*")) return 0;
            int carPriority = target.indexOf('/') >= 0 && target.charAt(2) != '*' ? 4 : 0;
            return carPriority + (target.startsWith("g:") ? 1 : target.endsWith("/*") ? 0 : 2);
        }
    }

    public static boolean validName(String name) { return name.matches("[\\p{L}\\p{N}_.-]{1,64}"); }

    private static boolean validTarget(String target) {
        return target.equals("*") || target.matches("g:([1-9][0-9]{0,2}/)?[\\p{L}\\p{N}_.-]{1,64}")
                || target.matches("p:([1-9][0-9]{0,2}|\\*)/([\\p{L}\\p{N}_.-]{1,64}|\\*)");
    }

    static String textKey(int car, String key) { return car > 0 ? car + "/" + key : key; }

    public String text(int car, String key) {
        return texts.getOrDefault(textKey(car, key), texts.get(key));
    }

    public PantographState pantograph(int car, String id, String group) {
        Command match = null;
        for (int i = batches.size() - 1; i >= 0; i--) {
            for (Command command : batches.get(i)) {
                if (command.matches(car, id, group) && (match == null || command.specificity() > match.specificity())) {
                    match = command;
                }
            }
        }
        return match == null ? PantographState.UNKNOWN : match.state();
    }

    public TrainDataSnapshot overlay(TrainDataSnapshot newer) {
        if (equals(newer) || newer.equals(EMPTY)) return this;
        List<List<Command>> source = new ArrayList<>(batches);
        source.addAll(newer.batches);
        Map<String, Command> remaining = new LinkedHashMap<>();
        List<List<Command>> merged = new ArrayList<>();
        for (int i = source.size() - 1; i >= 0 && remaining.size() < MAX_FIELDS; i--) {
            List<Command> row = new ArrayList<>();
            for (Command command : source.get(i)) {
                if (remaining.size() >= MAX_FIELDS) break;
                if (remaining.putIfAbsent(command.target(), command) == null) row.add(command);
            }
            if (!row.isEmpty()) merged.add(row);
        }
        Collections.reverse(merged);
        Map<String, String> mergedTexts = new LinkedHashMap<>(texts);
        newer.texts.forEach((key, value) -> {
            mergedTexts.remove(key);
            mergedTexts.put(key, value);
        });
        while (mergedTexts.size() > MAX_FIELDS) mergedTexts.remove(mergedTexts.keySet().iterator().next());
        return new TrainDataSnapshot(merged, mergedTexts);
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        ListTag rows = new ListTag();
        for (List<Command> batch : batches) {
            ListTag row = new ListTag();
            for (Command command : batch) {
                CompoundTag entry = new CompoundTag();
                entry.putString("target", command.target());
                entry.putString("state", command.state().name());
                row.add(entry);
            }
            if (!row.isEmpty()) rows.add(row);
        }
        tag.put("commands", rows);
        CompoundTag fields = new CompoundTag();
        texts.forEach(fields::putString);
        tag.put("texts", fields);
        return tag;
    }

    public static TrainDataSnapshot read(CompoundTag tag) {
        if (tag == null) return EMPTY;
        ListTag rows = tag.getList("commands", Tag.TAG_LIST);
        List<List<Command>> batches = new ArrayList<>();
        int count = 0;
        for (Tag row : rows) {
            List<Command> batch = new ArrayList<>();
            for (Tag item : (ListTag) row) {
                if (count >= MAX_FIELDS) break;
                CompoundTag command = (CompoundTag) item;
                try {
                    batch.add(new Command(command.getString("target"), PantographState.valueOf(command.getString("state"))));
                    count++;
                } catch (IllegalArgumentException ignored) {
                    // 旧存档中无效的条目不参与解析。
                }
            }
            if (!batch.isEmpty()) batches.add(batch);
            if (count >= MAX_FIELDS) break;
        }
        Map<String, String> texts = new LinkedHashMap<>();
        CompoundTag fields = tag.getCompound("texts");
        for (String key : fields.getAllKeys()) {
            if (texts.size() >= MAX_FIELDS) break;
            String value = fields.getString(key);
            int slash = key.indexOf('/');
            boolean validKey = slash < 0 ? validName(key)
                    : key.substring(0, slash).matches("[1-9][0-9]{0,2}") && validName(key.substring(slash + 1));
            if (validKey && fields.contains(key, Tag.TAG_STRING) && value.length() <= 512) texts.put(key, value);
        }
        return new TrainDataSnapshot(batches, texts);
    }
}
