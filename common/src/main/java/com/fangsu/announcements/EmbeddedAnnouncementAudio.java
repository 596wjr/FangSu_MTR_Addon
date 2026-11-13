package com.fangsu.announcements;

import com.fangsu.Main;
import com.fangsu.announcements.cache.AudioCacheStore;
import com.fangsu.announcements.cache.AudioLibrary;
import com.fangsu.announcements.cache.AudioPack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** 读取资源包内嵌的方速音频 ZIP。缓存文件和手动导入共用，资源包绑定只保留在内存。 */
final class EmbeddedAnnouncementAudio {
    private static final String DIRECTORY = "fangsu/announcements/audio_packs";
    private static final long MAX_ARCHIVE_BYTES = AudioPack.MAX_TOTAL_BYTES + 16L * 1024 * 1024;
    private record Embedded(ResourceLocation id, Resource resource, int priority) { }

    static void reload(ResourceManager manager, AudioLibrary library, AudioCacheStore cache) {
        List<Path> temporary = new ArrayList<>();
        Map<String, Path> archives = new LinkedHashMap<>();
        try {
            long remaining = MAX_ARCHIVE_BYTES;
            for (var file : list(manager)) {
                Path zip = null;
                //#if MC_VERSION >= 11900
                try (InputStream stream = file.resource().open()) {
                //#else
                //$$ try (InputStream stream = file.resource().getInputStream()) {
                //#endif
                    zip = Files.createTempFile("fangsu-resource-audio-", ".zip");
                    remaining -= copy(stream, zip, remaining);
                    temporary.add(zip);
                    archives.put(file.id().toString(), zip);
                } catch (IOException e) {
                    if (zip != null && !temporary.contains(zip)) removeTemporary(zip);
                    Main.LOGGER.warn("[FangSu] Could not read embedded audio pack {} ({})", file.id(), e.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException e) {
            Main.LOGGER.warn("[FangSu] Could not list embedded audio packs ({})", e.getClass().getSimpleName());
        }
        try {
            var result = AudioPack.mountEmbedded(library, cache, archives);
            if (!result.failures().isEmpty()) Main.LOGGER.warn("[FangSu] Embedded audio packs rejected {} entries", result.failures().size());
        } finally {
            for (Path zip : temporary) removeTemporary(zip);
        }
    }
    private static void removeTemporary(Path zip) {
        try { Files.deleteIfExists(zip); }
        catch (IOException e) { Main.LOGGER.warn("[FangSu] Could not remove temporary embedded audio ZIP ({})", e.getClass().getSimpleName()); }
    }

    private static List<Embedded> list(ResourceManager manager) {
        Map<String, Integer> priorities = new HashMap<>();
        try (var packs = manager.listPacks()) {
            //#if MC_VERSION >= 11900
            packs.forEach(pack -> priorities.put(pack.packId(), priorities.size()));
            //#else
            //$$ packs.forEach(pack -> priorities.put(pack.getName(), priorities.size()));
            //#endif
        }
        List<Embedded> result = new ArrayList<>();
        //#if MC_VERSION >= 11900
        manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".zip")).forEach((id, resource) ->
                result.add(new Embedded(id, resource, priorities.getOrDefault(resource.sourcePackId(), -1))));
        //#else
        //$$ for (ResourceLocation id : manager.listResources(DIRECTORY, name -> name.endsWith(".zip"))) {
        //$$     try {
        //$$         Resource resource = manager.getResource(id);
        //$$         result.add(new Embedded(id, resource, priorities.getOrDefault(resource.getSourceName(), -1)));
        //$$     } catch (IOException e) { Main.LOGGER.warn("[FangSu] Could not open embedded audio pack {}", id); }
        //$$ }
        //#endif
        result.sort(Comparator.comparingInt(Embedded::priority).thenComparing(entry -> entry.id().toString()));
        return result;
    }

    private static long copy(InputStream stream, Path target, long maximum) throws IOException {
        byte[] buffer = new byte[65536];
        long copied = 0;
        try (var output = Files.newOutputStream(target)) {
            int read;
            while ((read = stream.read(buffer)) != -1) {
                copied += read;
                if (copied > maximum) throw new IOException("embedded_audio_pack_limit");
                output.write(buffer, 0, read);
            }
        }
        return copied;
    }
}
