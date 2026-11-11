package com.fangsu.announcements.provider;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** MiMo 和 MiniMax 独立协议适配。固定 TLS 端点，不重定向，不自动重试付费请求。 */
public final class CloudTtsProvider implements TtsProvider {
    public enum Region {
        MIMO("https://api.xiaomimimo.com/v1/chat/completions"),
        MINIMAX_CN("https://api.minimax.cn/v1/t2a_v2"),
        MINIMAX_INTL("https://api.minimax.io/v1/t2a_v2");
        public final URI endpoint;
        Region(String endpoint) { this.endpoint = URI.create(endpoint); }
    }
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private static final Gson GSON = new Gson();
    private final Region region;
    public CloudTtsProvider(Region region) { this.region = region; }
    @Override public int maxConcurrentRequests(){return region==Region.MIMO?2:1;}
    @Override public Capabilities capabilities() { return new Capabilities(true, region == Region.MIMO, region != Region.MIMO, region == Region.MIMO); }

    public JsonObject payload(Request request) {
        if (request.text() == null || request.text().isBlank() || request.text().length() > 4096
                || request.voice() == null || request.voice().isBlank()) throw new IllegalArgumentException("invalid_request");
        JsonObject root = new JsonObject(); root.addProperty("model", request.model()); root.addProperty("stream", false);
        JsonObject audio = new JsonObject(); audio.addProperty("format", "wav");
        if (region == Region.MIMO) {
            boolean clone = request.voice().startsWith("data:audio/");
            if (!(clone ? "mimo-v2.5-tts-voiceclone" : "mimo-v2.5-tts").equals(request.model())) throw new IllegalArgumentException("mimo_model_voice_mismatch");
            if (clone && !(request.voice().startsWith("data:audio/wav;base64,") || request.voice().startsWith("data:audio/mp3;base64,"))) throw new IllegalArgumentException("invalid_sample_format");
            JsonArray messages = new JsonArray();
            if (!request.style().isEmpty()) messages.add(message("user", request.style()));
            messages.add(message("assistant", request.text()));
            root.add("messages", messages); audio.addProperty("voice", request.voice()); root.add("audio", audio);
        } else {
            if (request.voice().startsWith("data:") || !request.style().isEmpty()) throw new IllegalArgumentException("minimax_requires_mapped_voice_and_no_style");
            root.addProperty("text", request.text()); root.addProperty("output_format", "hex");
            JsonObject voice = new JsonObject(); voice.addProperty("voice_id", request.voice());
            voice.addProperty("speed", 1); voice.addProperty("pitch", 0); voice.addProperty("vol", 1);
            root.add("voice_setting", voice); audio.addProperty("sample_rate", 32000); audio.addProperty("channel", 1);
            root.add("audio_setting", audio);
        }
        return root;
    }
    private static JsonObject message(String role, String content) { JsonObject m = new JsonObject(); m.addProperty("role", role); m.addProperty("content", content); return m; }

    @Override public byte[] synthesize(Request request, String apiKey) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) throw new IOException("missing_key");
        HttpRequest.Builder builder = HttpRequest.newBuilder(region.endpoint).timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json");
        if (region == Region.MIMO) builder.header("api-key", apiKey); else builder.header("Authorization", "Bearer " + apiKey);
        HttpResponse<byte[]> response;
        CompletableFuture<HttpResponse<byte[]>> pending = HTTP.sendAsync(builder.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload(request)))).build(), info -> new LimitedBody());
        try {
            response = pending.get(90, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) { pending.cancel(true); throw new IOException("remote_result_uncertain"); }
        catch (InterruptedException e) { pending.cancel(true); Thread.currentThread().interrupt(); throw new IOException("remote_result_uncertain"); }
        // 不将响应正文或异常中的请求数据放入日志/界面。
        if (response.statusCode() != 200) throw new IOException("http_" + response.statusCode());
        return decode(new String(response.body(), StandardCharsets.UTF_8));
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long size;
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; delegate.onSubscribe(subscription); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) size += buffer.remaining();
            if (size > 32 * 1024 * 1024) { subscription.cancel(); delegate.onError(new IOException("response_too_large")); }
            else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { delegate.onError(error); }
        @Override public void onComplete() { delegate.onComplete(); }
    }
    public byte[] decode(String body) throws IOException {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (region == Region.MIMO) return Base64.getDecoder().decode(json.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").getAsJsonObject("audio").get("data").getAsString());
            int status = json.getAsJsonObject("base_resp").get("status_code").getAsInt();
            if (status != 0) throw new IOException("provider_" + status);
            return HexFormat.of().parseHex(json.getAsJsonObject("data").get("audio").getAsString());
        } catch (RuntimeException e) { throw new IOException("invalid_provider_response"); }
    }
}
