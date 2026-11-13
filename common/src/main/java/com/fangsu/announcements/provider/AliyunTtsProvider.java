package com.fangsu.announcements.provider;

import com.fangsu.announcements.cache.AudioCacheStore;
import com.fangsu.announcements.cache.SynthesisKey;
import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** 百炼北京节点：复刻音色通过 HTTP 创建，逐片段通过 WebSocket 合成。 */
public final class AliyunTtsProvider implements TtsProvider {
    public static final String DEFAULT_MODEL = "cosyvoice-v3.5-flash";
    public static final String ENCODING = "wav/24000/mono/pcm16/v1";
    private static final int RATE = 24000, MAX_PCM = RATE * 2 * 180;
    private static final URI ENROLLMENT = URI.create("https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization");
    private static final Set<String> MODELS = Set.of(DEFAULT_MODEL, "cosyvoice-v3.5-plus", "cosyvoice-v3-flash", "cosyvoice-v3-plus", "cosyvoice-v2");
    private final Path voices;
    private final Deque<Long> recentStarts = new ArrayDeque<>(3);

    private void awaitSynthesisSlot() throws InterruptedException {
        long window = TimeUnit.SECONDS.toNanos(1);
        synchronized (recentStarts) {
            while (recentStarts.size() == 3) {
                long remaining = recentStarts.getFirst() + window - System.nanoTime();
                if (remaining <= 0) recentStarts.removeFirst();
                else TimeUnit.NANOSECONDS.timedWait(recentStarts, remaining);
            }
            recentStarts.addLast(System.nanoTime());
        }
    }

    public AliyunTtsProvider(Path voices) { this.voices = voices; }
    public static boolean supportsModel(String model) { return model != null && MODELS.contains(model); }
    @Override public Capabilities capabilities() { return new Capabilities(true, true, true, true); }

    @Override public Request prepare(Request request, String apiKey) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) throw new IOException("missing_key");
        if (!supportsModel(request.model()) || request.text().isBlank() || request.text().length() > 4096)
            throw new IOException("invalid_request");
        if (!request.voice().startsWith("data:audio/")) return request;
        String voice = enrolledVoice(request, apiKey);
        return new Request(request.model(), request.text(), request.locale(), voice, request.style());
    }

    @Override public byte[] synthesize(Request request, String apiKey) throws IOException, InterruptedException {
        String voice = request.voice();
        if (!validVoice(voice)) throw new IOException("voice_mapping_required");

        awaitSynthesisSlot();
        Task task = new Task(request, voice, apiKey);
        var opening = CloudTtsProvider.HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiKey)
                .buildAsync(CloudTtsProvider.Region.ALIYUN.endpoint, task);
        WebSocket socket = null;
        try {
            socket = opening.get(15, TimeUnit.SECONDS);
            return task.result.get(75, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof WebSocketHandshakeException handshake)
                throw CloudFailure.http(handshake.getResponse(), "connection", apiKey);
            if (e.getCause() instanceof IOException io) throw io;
            throw CloudFailure.transport(socket == null ? "connection" : "synthesis", e.getCause());
        } catch (TimeoutException e) {
            throw CloudFailure.transport(socket == null ? "connection" : "synthesis", e);
        } finally {
            if (socket != null) socket.abort();
            else opening.thenAccept(WebSocket::abort);
        }
    }

    private static boolean validVoice(String voice) { return voice.matches("[A-Za-z0-9_.-]{1,256}"); }

    private synchronized String enrolledVoice(Request request, String apiKey) throws IOException, InterruptedException {
        String sample = request.voice();
        if (!(sample.startsWith("data:audio/wav;base64,") || sample.startsWith("data:audio/mp3;base64,"))
                || sample.length() > 12 * 1024 * 1024) throw new IOException("invalid_sample_format");
        String language = Locale.forLanguageTag(request.locale()).getLanguage();
        // 仅供本地复刻缓存定位，凭据及其指纹均不进入可导出的音频配方。
        String fingerprint = SynthesisKey.digest((apiKey + "\n" + request.model() + "\n" + language + "\n" + sample).getBytes(StandardCharsets.UTF_8));
        Path file = voices.resolve(fingerprint + ".txt");
        if (Files.exists(file)) {
            if (Files.size(file) > 256) throw new IOException("invalid_aliyun_voice_cache");
            String saved = Files.readString(file);
            if (!validVoice(saved)) throw new IOException("invalid_aliyun_voice_cache");
            if (readyVoices.contains(saved)) return saved;
            return checkVoice(saved, apiKey);
        }

        JsonObject input = object("action", "create_voice", "target_model", request.model(), "prefix", "fangsu", "audio", sample);
        if (!language.isEmpty()) {
            JsonArray hints = new JsonArray();
            hints.add(language);
            input.add("language_hints", hints);
        }
        String created = field(enrollment(input, apiKey), "voice_id");
        if (!validVoice(created)) throw new IOException("invalid_provider_response");
        // 先记下 ID，后续审核未完成时继续查同一个音色。
        AudioCacheStore.atomic(file, created.getBytes(StandardCharsets.UTF_8));
        return checkVoice(created, apiKey);
    }

    private final Set<String> readyVoices = new HashSet<>();
    private final Map<String, Long> nextVoiceCheck = new HashMap<>();

    private String checkVoice(String id, String apiKey) throws IOException, InterruptedException {
        Long next = nextVoiceCheck.get(id);
        if (next != null && next - System.nanoTime() > 0) throw new VoicePendingException();
        nextVoiceCheck.remove(id);
        String status = field(enrollment(object("action", "query_voice", "voice_id", id), apiKey), "status");
        if (status.equals("OK")) {
            readyVoices.add(id);
            return id;
        }
        if (status.equals("DEPLOYING")) {
            nextVoiceCheck.put(id, System.nanoTime() + TimeUnit.SECONDS.toNanos(SynthesisService.VOICE_RECHECK_SECONDS));
            throw new VoicePendingException();
        }
        throw new IOException("aliyun_voice_unavailable");
    }

    private static JsonObject enrollment(JsonObject input, String apiKey) throws IOException, InterruptedException {
        JsonObject json = object("model", "voice-enrollment");
        json.add("input", input);
        HttpRequest request = HttpRequest.newBuilder(ENROLLMENT).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json.toString())).build();
        HttpResponse<byte[]> response = CloudTtsProvider.send(request, 15, "voice_" + input.get("action").getAsString(), apiKey);
        try {
            JsonObject result = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject output = result.getAsJsonObject("output");
            if (output == null) throw CloudFailure.remote("aliyun_voice_enrollment_failed",
                    "voice_" + input.get("action").getAsString(), result, "code", "message", CloudFailure.value(result, "request_id"), apiKey);
            return output;
        } catch (RuntimeException e) { throw new IOException("invalid_provider_response"); }
    }

    private static String field(JsonObject object, String name) throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IOException("invalid_provider_response");
        return value.getAsString();
    }

    private static JsonObject object(String... pairs) {
        JsonObject json = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) json.addProperty(pairs[i], pairs[i + 1]);
        return json;
    }

    private static final class Task implements WebSocket.Listener {
        private final Request request;
        private final String voice, apiKey, id = UUID.randomUUID().toString();
        private final StringBuilder message = new StringBuilder();
        private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private boolean started;

        Task(Request request, String voice, String apiKey) {
            this.request = request;
            this.voice = voice;
            this.apiKey = apiKey;
        }

        private String event(String action, JsonObject payload) {
            JsonObject json = new JsonObject();
            json.add("header", object("action", action, "task_id", id, "streaming", "duplex"));
            json.add("payload", payload);
            return json.toString();
        }

        private void watch(CompletionStage<WebSocket> send) {
            send.exceptionally(error -> {
                result.completeExceptionally(CloudFailure.transport("synthesis", error));
                return null;
            });
        }

        @Override public void onOpen(WebSocket socket) {
            JsonObject parameters = object("text_type", "PlainText", "voice", voice, "format", "pcm");
            parameters.addProperty("sample_rate", RATE);
            String instruction = request.style();
            if (!instruction.isBlank() && request.model().startsWith("cosyvoice-v3.5-")
                    && instruction.codePoints().map(c -> c < 128 ? 1 : 2).sum() <= 100)
                parameters.addProperty("instruction", instruction);
            JsonObject payload = object("task_group", "audio", "task", "tts", "function", "SpeechSynthesizer", "model", request.model());
            payload.add("parameters", parameters);
            payload.add("input", new JsonObject());
            watch(socket.sendText(event("run-task", payload), true));
            socket.request(1);
        }

        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            try {
                if (message.length() + data.length() > 65536) throw new IOException("response_too_large");
                message.append(data);
                if (last) {
                    JsonObject header = JsonParser.parseString(message.toString()).getAsJsonObject().getAsJsonObject("header");
                    message.setLength(0);
                    if (!id.equals(field(header, "task_id"))) throw new IOException("invalid_provider_response");
                    switch (field(header, "event")) {
                        case "task-started" -> {
                            if (started) throw new IOException("invalid_provider_response");
                            started = true;
                            JsonObject text = new JsonObject(), finish = new JsonObject();
                            text.add("input", object("text", request.text()));
                            finish.add("input", new JsonObject());
                            watch(socket.sendText(event("continue-task", text), true)
                                    .thenCompose(ws -> ws.sendText(event("finish-task", finish), true)));
                        }
                        case "task-finished" -> result.complete(wave(pcm.toByteArray()));
                        case "task-failed" -> { throw CloudFailure.remote("aliyun_synthesis_failed", "synthesis", header, "error_code", "error_message", id, apiKey); }
                        default -> { }
                    }
                }
            } catch (IOException e) { result.completeExceptionally(e); }
            catch (RuntimeException e) { result.completeExceptionally(new IOException("invalid_provider_response")); }
            if (!result.isDone()) socket.request(1);
            return null;
        }

        @Override public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
            if (!started || data.remaining() > MAX_PCM - pcm.size())
                result.completeExceptionally(new IOException("invalid_provider_audio"));
            else {
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                pcm.writeBytes(bytes);
                socket.request(1);
            }
            return null;
        }

        @Override public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
            result.completeExceptionally(new IOException("remote_result_uncertain"));
            return null;
        }

        @Override public void onError(WebSocket socket, Throwable error) {
            result.completeExceptionally(CloudFailure.transport("synthesis", error));
        }
    }

    static byte[] wave(byte[] pcm) throws IOException {
        if (pcm.length == 0 || (pcm.length & 1) != 0 || pcm.length > MAX_PCM) throw new IOException("invalid_provider_audio");
        ByteBuffer wav = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        wav.putInt(0x46464952).putInt(36 + pcm.length).putInt(0x45564157)
                .putInt(0x20746d66).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16)
                .putInt(0x61746164).putInt(pcm.length).put(pcm);
        return wav.array();
    }
}
