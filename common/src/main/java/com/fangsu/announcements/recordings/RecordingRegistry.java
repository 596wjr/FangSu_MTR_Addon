package com.fangsu.announcements.recordings;

import com.fangsu.Main;
import com.fangsu.utils.ResourceUtil;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.nio.file.*;

/** 资源包录音方案的本地覆盖与快照；模组不携带默认录音。 */
public final class RecordingRegistry {
    private static volatile Map<String, RecordingFamily> families = Map.of();
    private static volatile Map<String, JsonObject> definitions = Map.of();
    public static RecordingFamily family(String id) { return families.get(id); }
    public static JsonObject definition(String id) {
        JsonObject definition = definitions.get(id); if (definition == null) throw new IllegalArgumentException("unknown_family");
        JsonObject copy = definition.deepCopy(); copy.addProperty("schemaVersion", 1); return copy;
    }
    public static Path localPath(String id) {
        com.fangsu.announcements.definition.AnnouncementRegistry.id(id);
        String key = com.fangsu.announcements.cache.SynthesisKey.digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return com.fangsu.announcements.AnnouncementClient.CONFIG.resolve("recordings").resolve(key + ".json");
    }
    public static RecordingFamily validate(JsonObject main) throws Exception {
        return com.fangsu.announcements.definition.TrainAnnouncementRegistry.family(com.fangsu.announcements.AnnouncementClient.resources(), main);
    }
    public static void publish(Map<String, RecordingFamily> loaded, Map<String, JsonObject> loadedDefinitions) {
        families = Map.copyOf(loaded); definitions = Map.copyOf(loadedDefinitions);
    }
    public static boolean reload() { return com.fangsu.announcements.definition.TrainAnnouncementRegistry.reload(com.fangsu.announcements.AnnouncementClient.resources()); }
}
