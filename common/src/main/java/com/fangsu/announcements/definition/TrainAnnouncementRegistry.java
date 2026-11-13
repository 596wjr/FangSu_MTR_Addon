package com.fangsu.announcements.definition;

import com.fangsu.Main;
import com.fangsu.announcements.recordings.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 与 lcd 并列的车型资源配置，不改列车渲染器，不向模组嵌入录音或复制资源包脚本。 */
public final class TrainAnnouncementRegistry {
    public record Profile(String id, String source, String mode, String variant, List<String> languages,
                          Map<String,String> templates, Map<String,String> languageVoices, RecordingFamily recording,
                          double approachingDistance) { }
    private static volatile Map<String, Profile> vehicles = Map.of();
    private static volatile Map<String, Profile> profiles = Map.of();
    public static Profile match(String vehicleType) { return vehicles.get(vehicleType); }
    public static Map<String, Profile> profiles() { return profiles; }
    public static synchronized boolean reload(ResourceManager manager) {
        try {
            Map<String, Profile> next = new LinkedHashMap<>(), definitions = new LinkedHashMap<>();
            Map<String, RecordingFamily> families = new LinkedHashMap<>(); Map<String, JsonObject> recordingDefinitions = new LinkedHashMap<>();
            for (String path : List.of("mtr:mtr_custom_resources.json", "fangsu:custom_trains.json")) {
                ResourceLocation location = new ResourceLocation(path);
                //#if MC_VERSION >= 11900
                if (manager.getResource(location).isEmpty()) continue;
                List<Resource> stack = manager.getResourceStack(location);
                //#else
                //$$ if (!manager.hasResource(location)) continue;
                //$$ List<Resource> stack = manager.getResources(location);
                //#endif
                for (Resource resource : stack) {
                    JsonObject root = read(resource, 8*1024*1024);
                    if (root.has("custom_trains")) for (var entry : root.getAsJsonObject("custom_trains").entrySet())
                        collect(manager, entry.getKey(), entry.getValue().getAsJsonObject(), true, next, definitions, families, recordingDefinitions);
                    if (root.has("vehicles")) for (var entry : root.getAsJsonArray("vehicles")) {
                        JsonObject vehicle = entry.getAsJsonObject();
                        if (vehicle.has("id")) collect(manager, vehicle.get("id").getAsString(), vehicle, false, next, definitions, families, recordingDefinitions);
                    }
                }
            }
            next.replaceAll((key, value) -> definitions.get(value.id()));
            RecordingRegistry.publish(families, recordingDefinitions);
            profiles = Map.copyOf(definitions); vehicles = Map.copyOf(next); return true;
        } catch (Exception e) { Main.LOGGER.warn("[FangSu] Train announcement configuration rejected; previous bindings retained ({})", e.getClass().getSimpleName()); return false; }
    }
    private static void collect(ResourceManager manager, String key, JsonObject vehicle, boolean legacy, Map<String,Profile> bindings,
            Map<String,Profile> profiles, Map<String,RecordingFamily> families, Map<String,JsonObject> recordingDefinitions) throws Exception {
        if (!vehicle.has("announcements")) return;
        if (bindings.size() > 16384 || profiles.size() > 512) throw new IOException("train_announcement_limit");
        List<String> aliases = new ArrayList<>(); aliases.add(key);
        if (legacy) {
            String canonical = key.startsWith("mtr_custom_train_") ? key : "mtr_custom_train_" + key;
            aliases.add(canonical);
            for (String suffix : List.of("_cab_1", "_cab_2", "_cab_3", "_trailer")) aliases.add(canonical + suffix);
        }
        if (vehicle.get("announcements").isJsonNull()) { aliases.forEach(bindings::remove); return; }
        JsonObject binding = vehicle.getAsJsonObject("announcements");
        String id = AnnouncementRegistry.id(binding.get("id").getAsString());
        String path = AnnouncementRegistry.id(binding.get("config").getAsString());
        if (profiles.containsKey(id) && profiles.get(id).source().equals(path)) { for (String alias : aliases) bindings.put(alias, profiles.get(id)); return; }
        JsonObject config = read(manager, path);
        if (config.get("schemaVersion").getAsInt() != 1) throw new IOException("train_announcement_schema");
        String mode = config.get("mode").getAsString(), variant = config.has("variant") ? config.get("variant").getAsString() : "";
        if (!Set.of("recordings", "cloud").contains(mode) || (!variant.isEmpty() && !variant.matches("[a-z0-9_.-]{1,64}"))) throw new IOException("invalid_train_announcement_mode");
        double approachingDistance = 50;
        if (config.has("approachingDistance")) {
            var distance = config.get("approachingDistance");
            if (!distance.isJsonPrimitive() || !distance.getAsJsonPrimitive().isNumber()) throw new IOException("invalid_approaching_distance");
            approachingDistance = distance.getAsDouble();
            if (!Double.isFinite(approachingDistance) || approachingDistance <= 0) throw new IOException("invalid_approaching_distance");
        }
        List<String> languages = new ArrayList<>();
        for (var language : config.getAsJsonArray("languages")) {
            String locale = language.getAsString(); if (locale.length()>35 || Locale.forLanguageTag(locale).getLanguage().isEmpty()) throw new IOException("invalid_locale");
            if (!languages.contains(locale)) languages.add(locale);
        }
        if (languages.isEmpty() || languages.size()>8) throw new IOException("language_count");
        Map<String,String> templates = new LinkedHashMap<>(), languageVoices = new LinkedHashMap<>();
        String preset=config.has("preset")?config.get("preset").getAsString():"";
        if(!preset.isEmpty()){
            if(!mode.equals("cloud")||!preset.equals("bjmtr"))throw new IOException("unknown_train_announcement_preset");
            templates.put("next","fangsu:train_next");templates.put("arriving","fangsu:train_approaching");templates.put("arrive","fangsu:train_arrived");
        }
        if (config.has("templates")) for (var entry : config.getAsJsonObject("templates").entrySet()) {
            if (!Set.of("next", "arriving", "arrive").contains(entry.getKey())) throw new IOException("unknown_train_event");
            if(entry.getValue().isJsonNull())templates.remove(entry.getKey());else templates.put(entry.getKey(), AnnouncementRegistry.id(entry.getValue().getAsString()));
        }
        if (config.has("languageVoices")) for (var entry : config.getAsJsonObject("languageVoices").entrySet()) {
            if (!languages.contains(entry.getKey())) throw new IOException("voice_language_not_selected");
            languageVoices.put(entry.getKey(), AnnouncementRegistry.id(entry.getValue().getAsString()));
        }
        var snapshot = AnnouncementRegistry.INSTANCE.snapshot();
        for (String voice : languageVoices.values()) if (!snapshot.voices().containsKey(voice)) throw new IOException("unknown_profile_voice");
        for (var entry : templates.entrySet()) {
            var template = snapshot.templates().get(entry.getValue());
            String event = entry.getKey().equals("next") ? "train.next" : entry.getKey().equals("arriving") ? "train.approaching" : "train.arrived";
            if (template == null || !template.event().equals(event)) throw new IOException("unknown_or_incorrect_train_template");
        }
        RecordingFamily family = null;
        if (config.has("recording")) {
            JsonObject main = read(manager, AnnouncementRegistry.id(config.get("recording").getAsString()));
            java.nio.file.Path local = RecordingRegistry.localPath(id);
            if (java.nio.file.Files.exists(local)) {
                try (InputStream stream = java.nio.file.Files.newInputStream(local)) { main = AnnouncementRegistry.read(stream); }
                if (!main.has("schemaVersion") || main.get("schemaVersion").getAsInt()!=1) throw new IOException("recording_override_schema");
            }
            family = family(manager, main); families.put(id, family); recordingDefinitions.put(id, main);
        }
        if (mode.equals("recordings") && family == null) throw new IOException("recording_definition_required");
        if (mode.equals("cloud") && templates.isEmpty()) throw new IOException("cloud_templates_required");
        Profile profile = new Profile(id, path, mode, variant, List.copyOf(languages), Map.copyOf(templates), Map.copyOf(languageVoices), family, approachingDistance);
        profiles.put(id, profile);
        if (family == null) { families.remove(id); recordingDefinitions.remove(id); }
        for (String alias : aliases) bindings.put(alias, profile);
    }
    public static RecordingFamily family(ResourceManager manager, JsonObject main) throws IOException {
        return new RecordingFamily(main,
                main.has("stations_json") ? array(manager, main.get("stations_json").getAsString()) : new JsonArray(),
                main.has("routes_json") ? array(manager, main.get("routes_json").getAsString()) : new JsonArray());
    }
    private static JsonArray array(ResourceManager manager, String path) throws IOException { return element(manager, path).getAsJsonArray(); }
    public static JsonObject read(ResourceManager manager, String path) throws IOException { return element(manager,path).getAsJsonObject(); }
    private static JsonElement element(ResourceManager manager, String path) throws IOException {
        ResourceLocation id = new ResourceLocation(AnnouncementRegistry.id(path)); Resource resource;
        //#if MC_VERSION >= 11900
        resource = manager.getResource(id).orElseThrow(() -> new IOException("missing_announcement_resource"));
        //#else
        //$$ resource = manager.getResource(id);
        //#endif
        return readElement(resource, 1024*1024);
    }
    private static JsonObject read(Resource resource, int limit) throws IOException { return readElement(resource,limit).getAsJsonObject(); }
    private static JsonElement readElement(Resource resource, int limit) throws IOException {
        //#if MC_VERSION >= 11900
        try (InputStream stream = resource.open()) {
        //#else
        //$$ try (InputStream stream = resource.getInputStream()) {
        //#endif
            byte[] bytes = stream.readNBytes(limit+1); if (bytes.length>limit) throw new IOException("announcement_json_limit");
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        }
    }
}
