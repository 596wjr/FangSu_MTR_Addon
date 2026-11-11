package com.fangsu.announcements.recordings;

import com.google.gson.*;
import java.util.*;

/** recordings 的原生片段解析器；仅解析数据和白名单条件，不运行资源包 JS。 */
public final class RecordingFamily {
    public record Named(long id, String name) { }
    public record Context(Named station, Named destination, Named route, List<Named> transfers,
                          boolean first, boolean terminal, boolean left, boolean right) { }
    public record Clip(String locale, String sound, double seconds, String text) { }
    private final JsonObject definition;
    private final JsonArray stations, routes;
    public RecordingFamily(JsonObject definition, JsonArray stations, JsonArray routes) {
        this.definition = definition.deepCopy(); this.stations = stations.deepCopy(); this.routes = routes.deepCopy();
        for (String mode : List.of("next", "arriving", "arrive")) {
            JsonArray format = definition.has("format_" + mode) ? definition.getAsJsonArray("format_" + mode) : new JsonArray();
            if (format.size() > 128) throw new IllegalArgumentException("recording_format_limit");
            for (JsonElement token : format) if (token.getAsString().length() > 256) throw new IllegalArgumentException("recording_token_limit");
        }
        if (definition.has("sounds")) for (var entry : definition.getAsJsonObject("sounds").entrySet()) validateSounds(entry.getValue().getAsJsonObject());
        for (JsonArray entries : List.of(stations, routes)) for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject(); if (entry.has("sounds")) validateSounds(entry.getAsJsonObject("sounds"));
            if (entry.has("matches") && entry.getAsJsonArray("matches").size() > 128) throw new IllegalArgumentException("alias_limit");
        }
        if (stations.size() > 4096 || routes.size() > 4096) throw new IllegalArgumentException("recording_lookup_limit");
    }
    private static void validateSounds(JsonObject sounds) { for (String language : sounds.keySet()) clip(sounds, language, language); }
    public List<Clip> resolve(String mode, Context context, List<String> locales) {
        List<Clip> result = new ArrayList<>();
        JsonArray format = definition.has("format_" + mode) ? definition.getAsJsonArray("format_" + mode) : new JsonArray();
        for (String locale : locales) {
            String language = locale.equals("zh-CN") ? "zh" : locale.equals("en-US") ? "en" : locale;
            List<Clip> languageClips = new ArrayList<>(); boolean missingGroup = false;
            for (JsonElement element : format) {
                String token = element.getAsString(); int separator = token.indexOf('%');
                String condition = separator < 0 ? "" : token.substring(0, separator), content = separator < 0 ? token : token.substring(separator + 1);
                if (!condition(condition, context, language)) continue;
                if (content.equals("STN") || content.equals("NEXT") || content.equals("DEST")) {
                    Named named = content.equals("DEST") ? context.destination() : context.station();
                    Clip clip = named(stations, named, language, locale);
                    if (clip == null) { missingGroup = true; break; } // 旧格式无句组边界，整语言降级，不能拼残句。
                    languageClips.add(clip);
                } else if (content.equals("ROUTE")) {
                    Clip clip = named(routes, context.route(), language, locale); if (clip != null) languageClips.add(clip);
                } else if (content.equals("TRANS")) {
                    for (Named route : context.transfers()) { Clip clip = named(routes, route, language, locale); if (clip != null) languageClips.add(clip); }
                } else {
                    JsonObject sounds = definition.has("sounds") ? definition.getAsJsonObject("sounds") : new JsonObject();
                    Clip clip = sounds.has(content) ? clip(sounds.getAsJsonObject(content), language, locale) : null;
                    if (clip != null) languageClips.add(clip);
                }
            }
            if (!missingGroup) result.addAll(languageClips);
        }
        return List.copyOf(result);
    }
    private boolean condition(String expression, Context context, String language) {
        if (expression.isEmpty()) return true;
        String[] terms = expression.split("&", -1); if (terms.length > 8) return false;
        for (String term : terms) {
            boolean negated = term.startsWith("!"); String key = negated ? term.substring(1) : term;
            boolean value;
            switch (key) {
                case "BEGIN" -> value = context.first();
                case "DEST" -> value = context.terminal();
                case "LEFT" -> value = context.left();
                case "RIGHT" -> value = context.right();
                case "TRANS" -> value = context.transfers().stream().anyMatch(route -> named(routes, route, language, language) != null);
                case "NOT_ENDS_WITH_STATION" -> value = !name(context.station().name(), language).toLowerCase(Locale.ROOT).matches(".*(站|station|zhan|zhàn)$");
                default -> { return false; } // 未知条件即使被取反也不允许执行。
            }
            if (negated == value) return false;
        }
        return true;
    }
    private static Clip named(JsonArray entries, Named named, String language, String locale) {
        if (named == null) return null;
        // 新格式稳定 ID 优先；旧包继续支持别名。
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.has("id") && entry.get("id").getAsString().equals(Long.toString(named.id())))
                return clip(entry.getAsJsonObject("sounds"), language, locale);
        }
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject(); if (!entry.has("matches")) continue;
            for (JsonElement match : entry.getAsJsonArray("matches")) if (matches(named.name(), match.getAsString()))
                return clip(entry.getAsJsonObject("sounds"), language, locale);
        }
        return null;
    }
    private static Clip clip(JsonObject sounds, String language, String locale) {
        if (sounds == null || !sounds.has(language)) return null;
        JsonObject item = sounds.getAsJsonObject(language);
        String sound = item.get("sound").getAsString(), text = item.has("text") ? item.get("text").getAsString() : "";
        double seconds = item.has("time") ? item.get("time").getAsDouble() : 0;
        if (!sound.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || sound.length() > 256 || sound.contains("..") || !Double.isFinite(seconds) || seconds <= 0 || seconds > 180 || text.length() > 4096) throw new IllegalArgumentException("invalid_recording_clip");
        return new Clip(locale, sound, seconds, text);
    }
    public static String name(String raw, String language) {
        if (raw == null) return "";
        String[] parts = raw.split("\\|\\|", 2)[0].split("\\|", -1);
        int index = language.equals("zh") || language.equals("zh-CN") ? 0 : language.equals("en") || language.equals("en-US") ? 1 : -1;
        if (parts.length == 1) return parts[0];
        return index >= 0 && index < parts.length ? parts[index] : "";
    }
    public static boolean matches(String raw, String candidate) { return Objects.equals(raw, candidate) || name(raw, "zh").equals(candidate) || name(raw, "en").equals(candidate); }
    /** 保留旧包的“上一线路号 JSON”换乘屏蔽约定，限制大小，不执行表达式。 */
    public static List<Named> filterTransfers(List<Named> transfers, String station, String rulesJson) {
        if (rulesJson == null || rulesJson.isBlank() || rulesJson.length() > 16384) return List.copyOf(transfers);
        try {
            JsonArray rules = JsonParser.parseString(rulesJson).getAsJsonArray(); if (rules.size() > 64) return List.copyOf(transfers);
            return transfers.stream().filter(route -> {
                for (JsonElement element : rules) {
                    JsonObject rule = element.getAsJsonObject(); if (!rule.has("at") || !rule.has("ignoreTransfer")) continue;
                    boolean at = false;
                    for (JsonElement match : rule.getAsJsonArray("at")) if (matches(station, match.getAsString())) { at = true; break; }
                    if (at) for (JsonElement match : rule.getAsJsonArray("ignoreTransfer")) if (matches(route.name(), match.getAsString())) return false;
                }
                return true;
            }).toList();
        } catch (RuntimeException ignored) { return List.copyOf(transfers); }
    }
}
