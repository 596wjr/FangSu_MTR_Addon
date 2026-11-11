package com.fangsu.announcements.provider;

import com.fangsu.announcements.cache.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** 同键任务合并、持久每日限额和有界队列。返回订阅 Future，取消订阅不取消其他请求者。 */
public final class SynthesisService implements AutoCloseable {
    public enum State { QUEUED, REQUESTING, VALIDATING, READY, FAILED, UNCERTAIN, CANCELLED }
    public record Task(String key, State state, String error) { }
    private final AudioCacheStore cache;
    private final Path usageFile;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(12), r -> { Thread t = new Thread(r, "fangsu-tts"); t.setDaemon(true); return t; }, new ThreadPoolExecutor.AbortPolicy());
    private final Semaphore serialProvider=new Semaphore(1,true);
    private final Map<String, CompletableFuture<AudioCacheStore.Artifact>> pending = new HashMap<>();
    private final Map<String,Long> pendingEpochs=new HashMap<>();
    private final Map<String, Task> tasks = new LinkedHashMap<>();
    private int sessionRequests, dailyRequests, dailyCharacters;
    private String day = LocalDate.now().toString();
    private volatile boolean enabled;
    private boolean closed;
    public SynthesisService(AudioCacheStore cache, Path usageFile) throws IOException {
        this.cache = cache; this.usageFile = usageFile;
        if (Files.exists(usageFile)) {
            if (Files.size(usageFile) > 65536) throw new IOException("invalid_usage_file");
            try {
                JsonObject json = JsonParser.parseString(Files.readString(usageFile)).getAsJsonObject();
                if (json.get("schemaVersion").getAsInt() != 1) throw new IOException("invalid_usage_schema");
                if (day.equals(json.get("day").getAsString())) {
                    dailyRequests = json.get("requests").getAsInt(); dailyCharacters = json.get("characters").getAsInt();
                    if (dailyRequests < 0 || dailyCharacters < 0) throw new IOException("invalid_usage_file");
                }
            } catch (RuntimeException e) { throw new IOException("invalid_usage_file"); }
        }
    }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }
    public synchronized List<Task> tasks() { return List.copyOf(tasks.values()); }
    public synchronized CompletableFuture<AudioCacheStore.Artifact> request(String key, boolean force,
            TtsProvider provider, TtsProvider.Request request, Supplier<String> keySupplier) {
        if (closed) return CompletableFuture.failedFuture(new IOException("service_closed"));
        long epoch = cache.epoch(key);
        CompletableFuture<AudioCacheStore.Artifact> existing = pending.get(key);
        if(existing!=null&&Objects.equals(pendingEpochs.get(key),epoch))return existing.thenApply(value->value);
        if(existing!=null){pending.remove(key);pendingEpochs.remove(key);existing.completeExceptionally(new IOException("deleted_or_cancelled"));}
        CompletableFuture<AudioCacheStore.Artifact> result = new CompletableFuture<>();
        pending.put(key, result);pendingEpochs.put(key,epoch); state(key, State.QUEUED, "");
        try { executor.execute(() -> {
            boolean serial=false;
            try {
                if (!force) {
                    Optional<AudioCacheStore.Artifact> cached = cache.find(key);
                    if (cached.isPresent()) { state(key, State.READY, ""); result.complete(cached.get()); return; }
                }
                if(provider.maxConcurrentRequests()<2){serialProvider.acquire();serial=true;}
                if (!enabled || cache.suppressed(key)) throw new IOException("synthesis_disabled_or_deleted");
                if(cache.epoch(key)!=epoch)throw new IOException("deleted_or_cancelled");
                String apiKey = keySupplier.get();
                if (apiKey == null || apiKey.isBlank()) throw new IOException("missing_key");
                // 在请求前记录额度；超时/失败不自动退回，避免不确定计费导致越限。
                reserve(request.text().length()); state(key, State.REQUESTING, "");
                byte[] audio = provider.synthesize(request, apiKey);
                state(key, State.VALIDATING, "");
                AudioCacheStore.Artifact artifact = cache.publish(key, epoch, audio);
                state(key, State.READY, ""); result.complete(artifact);
            } catch (Exception e) {
                String error = e instanceof IOException ? e.getMessage() : "invalid_request";
                if (error == null || !error.matches("[a-z0-9_]{1,80}")) error = "synthesis_failed";
                state(key, error.equals("remote_result_uncertain") ? State.UNCERTAIN : State.FAILED, error);
                result.completeExceptionally(new IOException(error));
            } finally { if(serial)serialProvider.release();synchronized (this) { if(pending.remove(key,result))pendingEpochs.remove(key); } }
        }); } catch (RejectedExecutionException e) {
            if(pending.remove(key,result))pendingEpochs.remove(key); state(key, State.FAILED, "queue_full"); result.completeExceptionally(new IOException("queue_full"));
        }
        return result.thenApply(value -> value);
    }
    private synchronized void reserve(int characters) throws IOException {
        if (characters < 1 || characters > 4096) throw new IOException("sentence_limit");
        String today = LocalDate.now().toString();
        if (!today.equals(day)) { day = today; dailyRequests = 0; dailyCharacters = 0; }
        if (sessionRequests >= 100 || dailyRequests >= 200 || characters > 50000 - dailyCharacters) throw new IOException("usage_limit");
        JsonObject json = new JsonObject(); json.addProperty("schemaVersion", 1); json.addProperty("day", day);
        json.addProperty("requests", dailyRequests + 1); json.addProperty("characters", dailyCharacters + characters);
        AudioCacheStore.atomic(usageFile, json.toString().getBytes(StandardCharsets.UTF_8));
        sessionRequests++; dailyRequests++; dailyCharacters += characters;
    }
    private synchronized void state(String key, State state, String error) {
        tasks.put(key, new Task(key, state, error));
        if (tasks.size() > 256) {
            Iterator<String> iterator = tasks.keySet().iterator();
            while (tasks.size() > 256 && iterator.hasNext()) { String candidate = iterator.next(); if (!pending.containsKey(candidate)) iterator.remove(); }
        }
    }
    @Override public synchronized void close() {
        closed = true; enabled = false;
        pending.forEach((key, future) -> { cache.invalidate(key); future.completeExceptionally(new IOException("service_closed")); });
        executor.shutdownNow(); pending.clear();pendingEpochs.clear();
    }
}
