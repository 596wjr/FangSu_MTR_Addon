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
    public enum State { QUEUED, WAITING_FOR_VOICE, REQUESTING, VALIDATING, READY, FAILED, UNCERTAIN, CANCELLED }
    public record Task(String key, State state, String error, CloudFailure cloudFailure) { }
    static final int VOICE_RECHECK_SECONDS = 10;
    private static final long VOICE_REVIEW_NANOS = TimeUnit.MINUTES.toNanos(10);
    private static final int MAX_PENDING = 13;
    private final AudioCacheStore cache;
    private final Path usageFile;
    public static final int ALIYUN_PARALLELISM = 2;
    private final ScheduledThreadPoolExecutor executor = workerPool(1);
    private final ScheduledThreadPoolExecutor aliyunExecutor = workerPool(ALIYUN_PARALLELISM);

    private static ScheduledThreadPoolExecutor workerPool(int workers) {
        var pool = new ScheduledThreadPoolExecutor(workers, work -> {
            Thread thread = new Thread(work, "fangsu-tts");
            thread.setDaemon(true);
            return thread;
        });
        pool.setRemoveOnCancelPolicy(true);
        return pool;
    }
    private final Map<String, Job> pending = new HashMap<>();

    private static final class Job {
        final long epoch;
        final CompletableFuture<AudioCacheStore.Artifact> result = new CompletableFuture<>();
        ScheduledFuture<?> scheduled;
        long reviewDeadline;
        final ScheduledThreadPoolExecutor executor;
        Job(long epoch, ScheduledThreadPoolExecutor executor) {
            this.epoch = epoch;
            this.executor = executor;
        }
    }
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
        Job previous = pending.get(key);
        if (previous != null && previous.epoch == epoch) return previous.result.thenApply(value -> value);
        if (previous == null && pending.size() >= MAX_PENDING) {
            state(key, State.FAILED, "queue_full");
            return CompletableFuture.failedFuture(new IOException("queue_full"));
        }

        Job job = new Job(epoch, provider instanceof AliyunTtsProvider ? aliyunExecutor : executor);
        pending.put(key, job);
        state(key, State.QUEUED, "");
        Runnable work = () -> process(key, job, force, provider, request, keySupplier);
        job.scheduled = job.executor.schedule(work, 0, TimeUnit.SECONDS);
        if (previous != null) {
            previous.scheduled.cancel(false);
            previous.result.completeExceptionally(new IOException("deleted_or_cancelled"));
        }
        return job.result.thenApply(value -> value);
    }

    private void process(String key, Job job, boolean force, TtsProvider provider,
                         TtsProvider.Request request, Supplier<String> keySupplier) {
        try {
            if (!force) {
                Optional<AudioCacheStore.Artifact> cached = cache.find(key);
                if (cached.isPresent()) {
                    finish(key, job, State.READY, "", null, cached.get());
                    return;
                }
            }
            checkActive(key, job);
            String apiKey = keySupplier.get();
            if (apiKey == null || apiKey.isBlank()) throw new IOException("missing_key");
            TtsProvider.Request prepared;
            try {
                prepared = provider.prepare(request, apiKey);
            } catch (TtsProvider.VoicePendingException waiting) {
                deferVoiceReview(key, job, () -> process(key, job, force, provider, request, keySupplier));
                return;
            }
            synchronized (this) {
                checkActive(key, job);
                // 只有实际发起合成才预留额度，远端结果不确定时仍保留扣减。
                reserve(prepared.text().length());
                state(key, State.REQUESTING, "");
            }
            byte[] audio = provider.synthesize(prepared, apiKey);
            synchronized (this) {
                checkActive(key, job);
                state(key, State.VALIDATING, "");
            }
            finish(key, job, State.READY, "", null, cache.publish(key, job.epoch, audio));
        } catch (Exception e) {
            String error = e instanceof IOException ? e.getMessage() : "invalid_request";
            if (error == null || !error.matches("[a-z0-9_]{1,80}")) error = "synthesis_failed";
            State failedState = switch (error) {
                case "remote_result_uncertain" -> State.UNCERTAIN;
                case "deleted_or_cancelled", "synthesis_disabled_or_deleted" -> State.CANCELLED;
                case "aliyun_voice_pending" -> State.WAITING_FOR_VOICE;
                default -> State.FAILED;
            };
            finish(key, job, failedState, error, e instanceof CloudFailure cloud ? cloud : null, null);
        }
    }

    private synchronized void deferVoiceReview(String key, Job job, Runnable work) throws IOException {
        checkActive(key, job);
        long now = System.nanoTime();
        if (job.reviewDeadline == 0) job.reviewDeadline = now + VOICE_REVIEW_NANOS;
        if (now - job.reviewDeadline >= 0) throw new IOException("aliyun_voice_pending");
        state(key, State.WAITING_FOR_VOICE, "");
        job.scheduled = job.executor.schedule(work, VOICE_RECHECK_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void checkActive(String key, Job job) throws IOException {
        if (pending.get(key) != job || cache.epoch(key) != job.epoch) throw new IOException("deleted_or_cancelled");
        if (!enabled || cache.suppressed(key)) throw new IOException("synthesis_disabled_or_deleted");
    }

    private void finish(String key, Job job, State finalState, String error, CloudFailure cloud, AudioCacheStore.Artifact artifact) {
        synchronized (this) {
            if (!pending.remove(key, job)) return;
            state(key, finalState, error, cloud);
        }
        if (artifact != null) job.result.complete(artifact);
        else job.result.completeExceptionally(cloud == null ? new IOException(error) : cloud);
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
        state(key, state, error, null);
    }
    private synchronized void state(String key, State state, String error, CloudFailure cloud) {
        tasks.put(key, new Task(key, state, error, cloud));
        if (tasks.size() > 256) {
            Iterator<String> iterator = tasks.keySet().iterator();
            while (tasks.size() > 256 && iterator.hasNext()) { String candidate = iterator.next(); if (!pending.containsKey(candidate)) iterator.remove(); }
        }
    }
    @Override public synchronized void close() {
        closed = true; enabled = false;
        List<Job> jobs = new ArrayList<>(pending.values());
        pending.keySet().forEach(cache::invalidate);
        pending.clear();
        executor.shutdownNow();
        aliyunExecutor.shutdownNow();
        jobs.forEach(job -> job.result.completeExceptionally(new IOException("service_closed")));
    }
}
