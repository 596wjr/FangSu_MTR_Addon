package com.fangsu.announcements.definition;

import com.fangsu.Main;
import com.fangsu.announcements.definition.AnnouncementDefinitions.*;
import com.fangsu.announcements.resolve.AnnouncementResolver;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 逐 ID 合并低到高优先级资源，校验成功后原子发布；失败保留旧快照。 */
public final class AnnouncementRegistry {
    public static final AnnouncementRegistry INSTANCE = new AnnouncementRegistry();
    private volatile Snapshot snapshot = Snapshot.empty();
    public Snapshot snapshot() { return snapshot; }
    private static final int MAX_JSON = 1024 * 1024;
    private record Loaded(JsonObject json, Source source) { }

    public synchronized boolean reload(ResourceManager manager, Path localOverride) {
        try { snapshot = prepare(manager, localOverride); return true; }
        catch (Exception e) {
            Main.LOGGER.warn("[FangSu] Announcement definitions rejected; retaining previous snapshot ({})", e.getClass().getSimpleName());
            return false;
        }
    }
    public synchronized void publish(Snapshot prepared) { snapshot = prepared; }
    public synchronized Snapshot prepare(ResourceManager manager, Path localOverride) throws Exception {
            Map<String, Template> templates = new LinkedHashMap<>();
            Map<String, Voice> voices = new LinkedHashMap<>();
            Map<String, List<Source>> sources = new LinkedHashMap<>();
            List<String> namespaces = new ArrayList<>(manager.getNamespaces());
            Collections.sort(namespaces);
            int[] count = {0}; List<Loaded> documents = new ArrayList<>();
            Map<String,Integer> priority = new HashMap<>();
            try (var packs = manager.listPacks()) {
                packs.forEach(pack -> {
                    //#if MC_VERSION >= 11903
                    priority.put(pack.packId(), priority.size());
                    //#else
                    //$$ priority.put(pack.getName(), priority.size());
                    //#endif
                });
            }
            for (String namespace : namespaces) {
                ResourceLocation index = new ResourceLocation(namespace, "fangsu/announcements/index.json");
                //#if MC_VERSION >= 11900
                if (manager.getResource(index).isEmpty()) continue;
                //#else
                //$$ if (!manager.hasResource(index)) continue;
                //#endif
                load(manager, index, new HashSet<>(), documents);
            }
            documents.sort(Comparator.comparingInt(doc -> priority.getOrDefault(doc.source().pack(), -1)));
            for (Loaded doc : documents) merge(doc.json(), doc.source(), templates, voices, sources, count);
            if (Files.exists(localOverride)) {
                try (InputStream input = Files.newInputStream(localOverride)) {
                    merge(read(input), new Source("local", "overrides.json"), templates, voices, sources, count);
                }
            }
            for (Template template : templates.values()) {
                if (template.variants().stream().noneMatch(v -> v.id().equals(template.defaultVariant()))) throw new IOException("missing_default_variant");
                for (Variant variant : template.variants()) {
                    if (!variant.voice().isEmpty() && !voices.containsKey(variant.voice())) throw new IOException("missing_voice_reference");
                    for (LanguageVoice language : variant.languageVoices().values()) if (!voices.containsKey(language.voice())) throw new IOException("missing_language_voice_reference");
                }
            }
            Map<String, List<Source>> immutableSources = new LinkedHashMap<>();
            sources.forEach((id, chain) -> immutableSources.put(id, List.copyOf(chain)));
            return new Snapshot(Map.copyOf(templates), Map.copyOf(voices), Map.copyOf(immutableSources), snapshot.revision() + 1);
    }

    private static void load(ResourceManager manager, ResourceLocation id, Set<ResourceLocation> active,
            List<Loaded> documents) throws IOException {
        if (active.size() >= 16 || !active.add(id)) throw new IOException("cyclic_include");
        List<Resource> stack;
        //#if MC_VERSION >= 11900
        stack = manager.getResourceStack(id);
        //#else
        //$$ stack = manager.getResources(id);
        //#endif
        for (Resource resource : stack) {
            //#if MC_VERSION >= 11900
            try (InputStream stream = resource.open()) {
                Source source = new Source(resource.sourcePackId(), id.toString());
            //#else
            //$$ try (InputStream stream = resource.getInputStream()) {
            //$$     Source source = new Source(resource.getSourceName(), id.toString());
            //#endif
                JsonObject json = read(stream);
                if (json.has("definitions")) for (JsonElement entry : json.getAsJsonArray("definitions")) {
                    String ref = entry.getAsString();
                    id(ref);
                    ResourceLocation location = new ResourceLocation(ref);
                    if (!location.getPath().startsWith("fangsu/announcements/") || !location.getPath().endsWith(".json")) throw new IOException("invalid_include");
                    load(manager, location, active, documents);
                }
                if (documents.size() >= 512) throw new IOException("too_many_definition_files");
                documents.add(new Loaded(json, source));
            }
        }
        active.remove(id);
    }

    public static JsonObject read(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_JSON + 1);
        if (bytes.length > MAX_JSON) throw new IOException("definition_too_large");
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static void fields(JsonObject object, String... allowed) {
        Set<String> keys = Set.of(allowed);
        for (String key : object.keySet()) if (!keys.contains(key)) throw new IllegalArgumentException("unknown_field");
    }
    public static String id(String id) {
        if (id.length() > 256 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.contains("..")) throw new IllegalArgumentException("invalid_id");
        return id;
    }
    private static String str(JsonObject o, String key, String fallback) { return o.has(key) ? o.get(key).getAsString() : fallback; }
    private static int revision(JsonObject o) { int value = o.has("revision") ? o.get("revision").getAsInt() : 1; if (value < 1) throw new IllegalArgumentException("invalid_revision"); return value; }
    private static void merge(JsonObject json, Source source, Map<String, Template> templates,
            Map<String, Voice> voices, Map<String, List<Source>> sources, int[] count) {
        fields(json, "schemaVersion", "definitions", "templates", "voices");
        if (!json.has("schemaVersion") || json.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("unsupported_schema");
        if (json.has("voices")) for (JsonElement element : json.getAsJsonArray("voices")) {
            if (++count[0] > 4096) throw new IllegalArgumentException("too_many_definitions");
            JsonObject v = element.getAsJsonObject();
            fields(v, "id", "revision", "displayName", "mode", "samples", "providerHints", "presetVoices");
            String key = id(v.get("id").getAsString()), mode = str(v, "mode", "preset");
            if (!Set.of("preset", "reference_audio", "recording").contains(mode)) throw new IllegalArgumentException("invalid_voice_mode");
            Map<String, ReferenceSample> samples = new LinkedHashMap<>();Map<String,String> presets = new LinkedHashMap<>();
            if (v.has("samples")) v.getAsJsonObject("samples").entrySet().forEach(e -> {
                locale(e.getKey());
                JsonObject sample = e.getValue().getAsJsonObject(); fields(sample, "resource", "text", "chineseNamesResource", "chineseNamesText");
                String text=str(sample,"text","");
                String chineseNames=str(sample,"chineseNamesResource",""),chineseNamesText=str(sample,"chineseNamesText","");
                if(text.length()>4096||chineseNamesText.length()>4096)throw new IllegalArgumentException("sample_text_limit");
                if(!chineseNames.isEmpty())id(chineseNames);
                samples.put(e.getKey(),new ReferenceSample(id(sample.get("resource").getAsString()),text,chineseNames,chineseNamesText));
            });
            if (mode.equals("reference_audio") && samples.isEmpty()) throw new IllegalArgumentException("missing_sample");
            if (v.has("presetVoices")) v.getAsJsonObject("presetVoices").entrySet().forEach(e -> {
                if (!Set.of("mimo", "minimax-cn", "minimax-intl").contains(e.getKey()) || e.getValue().getAsString().length() > 256) throw new IllegalArgumentException("invalid_provider_voice");
                presets.put(e.getKey(), e.getValue().getAsString());
            });
            voices.put(key, new Voice(key, revision(v), mode, Map.copyOf(samples), Map.copyOf(presets)));
            sources.computeIfAbsent("voice/" + key, k -> new ArrayList<>()).add(source);
        }
        if (json.has("templates")) for (JsonElement element : json.getAsJsonArray("templates")) {
            if (++count[0] > 4096) throw new IllegalArgumentException("too_many_definitions");
            JsonObject t = element.getAsJsonObject();
            fields(t, "id", "revision", "event", "variants", "defaultVariant", "locales", "displayName", "requiredVariables");
            String key = id(t.get("id").getAsString());
            String event = t.get("event").getAsString();
            if (!Set.of("platform.approaching", "train.next", "train.approaching", "train.arrived", "train.terminal").contains(event)) throw new IllegalArgumentException("invalid_event");
            List<Variant> variants = new ArrayList<>(); Set<String> variantIds = new HashSet<>();
            for (JsonElement ve : t.getAsJsonArray("variants")) {
                JsonObject v = ve.getAsJsonObject(); fields(v, "id", "voice", "style", "languageVoices");
                String variant = v.get("id").getAsString(), style = str(v, "style", "");
                if (!variant.matches("[a-z0-9_.-]{1,64}") || !variantIds.add(variant) || style.length() > 1024) throw new IllegalArgumentException("invalid_variant");
                Map<String, LanguageVoice> languages = new LinkedHashMap<>();
                if (v.has("languageVoices")) for (var entry : v.getAsJsonObject("languageVoices").entrySet()) {
                    locale(entry.getKey()); JsonObject binding = entry.getValue().getAsJsonObject(); fields(binding, "voice", "style");
                    String languageStyle = str(binding, "style", style);
                    if (languageStyle.length() > 1024) throw new IllegalArgumentException("invalid_style");
                    languages.put(entry.getKey(), new LanguageVoice(id(binding.get("voice").getAsString()), languageStyle));
                }
                String fallbackVoice = str(v, "voice", "");
                if (!fallbackVoice.isEmpty()) id(fallbackVoice);
                if (fallbackVoice.isEmpty() && languages.isEmpty()) throw new IllegalArgumentException("missing_voice");
                if (languages.size() > 32) throw new IllegalArgumentException("too_many_language_voices");
                variants.add(new Variant(variant, fallbackVoice, style, Map.copyOf(languages)));
            }
            if (variants.isEmpty() || variants.size() > 32) throw new IllegalArgumentException("variant_count");
            Map<String, List<Segment>> locales = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : t.getAsJsonObject("locales").entrySet()) {
                locale(entry.getKey()); List<Segment> segments = new ArrayList<>();
                JsonObject language = entry.getValue().getAsJsonObject(); fields(language, "segments");
                for (JsonElement se : language.getAsJsonArray("segments")) {
                    JsonObject segment = se.getAsJsonObject(); fields(segment, "text", "when");
                    String when = str(segment, "when", "");
                    if (!when.isEmpty() && !AnnouncementResolver.CONDITIONS.contains(when)) throw new IllegalArgumentException("unknown_condition");
                    segments.add(new Segment(AnnouncementResolver.validateText(segment.get("text").getAsString()), when));
                }
                if (segments.size() > 32) throw new IllegalArgumentException("segment_count");
                locales.put(entry.getKey(), List.copyOf(segments));
            }
            Set<String> required=new HashSet<>();
            if(t.has("requiredVariables"))for(var variable:t.getAsJsonArray("requiredVariables")){
                String name=variable.getAsString();if(!AnnouncementResolver.VARIABLES.contains(name))throw new IllegalArgumentException("unknown_variable");required.add(name);
            }
            templates.put(key, new Template(key, revision(t), event, t.get("defaultVariant").getAsString(), List.copyOf(variants), Map.copyOf(locales),Set.copyOf(required)));
            sources.computeIfAbsent("template/" + key, k -> new ArrayList<>()).add(source);
        }
    }
    private static void locale(String locale) {
        if (locale.length() > 35 || Locale.forLanguageTag(locale).getLanguage().isEmpty() || !Locale.forLanguageTag(locale).toLanguageTag().equalsIgnoreCase(locale)) throw new IllegalArgumentException("invalid_locale");
    }
}
