package com.fangsu.announcements.cache;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** 每键一个原子清单，无全局易损索引；旧版本保留，删除使用持久墓碑。 */
public final class AudioCacheStore {
    public record Artifact(String key, String generation, Path file, WavInfo audio, long createdAt, String contentHash) { }
    private final Path root;
    private final Map<String, Long> epochs = new HashMap<>();
    private record Validated(String hash,BasicFileAttributes attributes,WavInfo audio) { }
    // 仅缓存不可变代际的校验元数据，不保存整段 PCM；多只喇叭仍独立流式读取同一个文件。
    private static final Map<Path,Validated> VALIDATED=new LinkedHashMap<>(128,.75f,true);
    public AudioCacheStore(Path root) throws IOException { this.root = root.toAbsolutePath().normalize(); Files.createDirectories(this.root); }
    private Path directory(String key) {
        if (!key.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid_cache_key");
        return root.resolve(key);
    }
    public synchronized long epoch(String key) { directory(key); return epochs.getOrDefault(key, 0L); }
    public synchronized boolean suppressed(String key) { return Files.exists(directory(key).resolve("deleted")); }
    public synchronized Optional<Artifact> find(String key) throws IOException {
        return readCurrent(directory(key),key);
    }
    /** 播放队列仅保存稳定编号；每段开始时重新解析 current，不能继续使用被本地替换的旧代际。 */
    public static Artifact currentArtifact(Artifact previous) throws IOException {
        String key=previous.key();Path directory=previous.file().toAbsolutePath().normalize().getParent();
        if(key==null||!key.matches("[0-9a-f]{64}")||directory==null||!directory.getFileName().toString().equals(key))throw new IOException("invalid_cache_manifest");
        if(Files.exists(directory.resolve("deleted"),LinkOption.NOFOLLOW_LINKS))throw new IOException("library_audio_deleted");
        return readCurrent(directory,key).orElseThrow(()->new IOException("library_audio_missing"));
    }
    private static Optional<Artifact> readCurrent(Path directory,String key) throws IOException {
        Path manifest=directory.resolve("current.json");
        if(Files.isSymbolicLink(directory)||Files.isSymbolicLink(manifest))throw new IOException("unsafe_cache_file");
        if(Files.exists(directory.resolve("deleted"),LinkOption.NOFOLLOW_LINKS)||!Files.exists(manifest))return Optional.empty();
        try {
            if (Files.size(manifest) > 65536) throw new IOException("invalid_cache_manifest");
            JsonObject json = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
            if (json.get("schemaVersion").getAsInt() != 1) throw new IOException("unsupported_cache_schema");
            String generation = json.get("generation").getAsString();
            if (!generation.matches("[0-9a-f-]{36}")) throw new IOException("invalid_generation");
            Path file = directory.resolve(generation + ".wav");
            String hash=json.get("contentHash").getAsString();
            if(!hash.matches("[0-9a-f]{64}"))throw new IOException("invalid_cache_manifest");
            WavInfo audio=validatedAudio(file,hash);
            if(Files.exists(directory.resolve("deleted"),LinkOption.NOFOLLOW_LINKS))return Optional.empty();
            return Optional.of(new Artifact(key, generation, file, audio, json.get("createdAt").getAsLong(), hash));
        } catch (RuntimeException e) { throw new IOException("invalid_cache_manifest"); }
    }
    private static WavInfo validatedAudio(Path file,String hash) throws IOException {
        synchronized(VALIDATED){
            BasicFileAttributes before=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!before.isRegularFile())throw new IOException("unsafe_cache_file");
            if(before.size()>24*1024*1024)throw new IOException("audio_too_large");
            Validated known=VALIDATED.get(file);
            if(known!=null&&known.hash().equals(hash)&&sameFile(known.attributes(),before))return known.audio();
            byte[] bytes;try(InputStream input=Files.newInputStream(file)){bytes=input.readNBytes(24*1024*1024+1);}
            if(bytes.length>24*1024*1024)throw new IOException("audio_too_large");
            if(!hash.equals(SynthesisKey.digest(bytes)))throw new IOException("cache_checksum_mismatch");
            WavInfo audio=WavInfo.inspect(bytes);
            BasicFileAttributes after=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!after.isRegularFile()||!sameFile(before,after))throw new IOException("cache_checksum_mismatch");
            VALIDATED.put(file,new Validated(hash,after,audio));
            if(VALIDATED.size()>128)VALIDATED.remove(VALIDATED.keySet().iterator().next());
            return audio;
        }
    }
    private static boolean sameFile(BasicFileAttributes a,BasicFileAttributes b){
        return a.size()==b.size()&&a.lastModifiedTime().equals(b.lastModifiedTime())&&a.creationTime().equals(b.creationTime())&&Objects.equals(a.fileKey(),b.fileKey());
    }
    public synchronized Artifact publish(String key, long epoch, byte[] bytes) throws IOException {
        if (epoch(key) != epoch || suppressed(key)) throw new IOException("deleted_or_cancelled");
        WavInfo audio = WavInfo.inspect(bytes);
        Path directory = directory(key); Files.createDirectories(directory);
        String generation = UUID.randomUUID().toString(); long created = System.currentTimeMillis();
        String hash = SynthesisKey.digest(bytes);
        Path file = directory.resolve(generation + ".wav");
        atomic(file, bytes);
        JsonObject manifest = new JsonObject(); manifest.addProperty("schemaVersion", 1);
        manifest.addProperty("generation", generation); manifest.addProperty("createdAt", created);
        manifest.addProperty("contentHash", hash); manifest.addProperty("size", bytes.length);
        manifest.addProperty("durationSeconds",audio.seconds());
        // 单独保留各版本清单，current 切换失败时仍可人工恢复完整版本。
        byte[] metadata = manifest.toString().getBytes(StandardCharsets.UTF_8);
        atomic(directory.resolve(generation + ".json"), metadata);
        atomic(directory.resolve("current.json"), metadata);
        return new Artifact(key, generation, file, audio, created, hash);
    }
    public synchronized void delete(String key) throws IOException {
        Path dir = directory(key); Files.createDirectories(dir);
        atomic(dir.resolve("deleted"), new byte[]{1});
        epochs.put(key, epoch(key) + 1);
        // 延迟物理回收，播放中的文件不会因 Windows 文件锁删除失败。
    }
    public synchronized void restore(String key) throws IOException { Files.deleteIfExists(directory(key).resolve("deleted")); epochs.put(key, epoch(key) + 1); }
    public synchronized void invalidate(String key) { epochs.put(key, epoch(key) + 1); }
    /** 清理旧版派生成品，逐文件删除且不递归；先检查整组路径，再重新核对旧配方归属。 */
    public synchronized void purgeDerived(String key) throws IOException {
        Path dir=directory(key).toAbsolutePath().normalize();
        if(!dir.getParent().equals(root)||Files.isSymbolicLink(root)||Files.isSymbolicLink(dir)
                ||!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))throw new IOException("unsafe_cache_directory");
        Path realRoot=root.toRealPath();
        if(!dir.toRealPath().getParent().equals(realRoot))throw new IOException("unsafe_cache_directory");
        List<Path> generations=new ArrayList<>(),metadata=new ArrayList<>();
        String generation="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
        try(var files=Files.newDirectoryStream(dir)){
            for(Path file:files){
                if(Files.isSymbolicLink(file))throw new IOException("unsafe_cache_file");
                String name=file.getFileName().toString();
                boolean version=name.matches(generation+"\\.(?:wav|json)"),known=Set.of("current.json","library.json","deleted").contains(name);
                if(!version&&!known)continue;
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("unsafe_cache_file");
                (version?generations:metadata).add(file);
            }
        }
        AudioLibrary.Recipe recipe=new AudioLibrary(this).recipe(key);
        if(recipe==null||!recipe.kind().equals("composition"))throw new IOException("not_derived_cache");
        epochs.put(key,epoch(key)+1);
        // 配方最后删除；某个文件被 Windows 锁住时，下次启动仍能识别并继续清理。
        metadata.sort(Comparator.comparingInt(path->path.getFileName().toString().equals("library.json")?1:0));
        generations.addAll(metadata);
        for(Path file:generations){
            if(Files.isSymbolicLink(dir)||!dir.toRealPath().getParent().equals(realRoot)||Files.isSymbolicLink(file)
                    ||!file.toAbsolutePath().normalize().getParent().equals(dir))throw new IOException("unsafe_cache_file");
            Files.deleteIfExists(file);
        }
    }
    public Path root() { return root; }
    public static void atomic(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent()); Path temporary = Files.createTempFile(target.getParent(), ".fangsu-", ".tmp");
        try { Files.write(temporary, bytes); Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temporary); }
    }
}
