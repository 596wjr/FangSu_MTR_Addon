package com.fangsu.announcements.provider;

import com.google.gson.*;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** 只保留可展示的云端错误字段，不保存整个响应。 */
public final class CloudFailure extends IOException {
    private final String stage;
    private final String details;

    private CloudFailure(String reason, String stage, String code, String message, String requestId, String apiKey) {
        super(reason);
        this.stage = stage;
        var parts = new ArrayList<String>();
        for (String field : new String[]{code, message, requestId.isEmpty() ? "" : "ID " + requestId}) {
            String safe = sanitize(field, apiKey);
            if (!safe.isEmpty() && !parts.contains(safe)) parts.add(safe);
        }
        details = String.join(" · ", parts);
    }

    public String stage() { return stage; }
    public String details() { return details; }

    static CloudFailure transport(String stage, Throwable cause) {
        String type = cause == null ? "" : cause.getClass().getSimpleName();
        return new CloudFailure("remote_result_uncertain", stage, type, "", "", "");
    }

    static CloudFailure http(HttpResponse<?> response, String stage, String apiKey) {
        JsonObject body = new JsonObject();
        try {
            if (response.body() instanceof byte[] bytes)
                body = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException ignored) { }
        JsonObject error = body.has("error") && body.get("error").isJsonObject() ? body.getAsJsonObject("error") : body;
        String id = first(value(body, "request_id"), response.headers().firstValue("x-request-id").orElse(""));
        if (id.isEmpty()) id = response.headers().firstValue("x-dashscope-request-id").orElse("");
        return new CloudFailure("http_" + response.statusCode(), stage, first(value(error, "code"), value(error, "type")),
                value(error, "message"), id, apiKey);
    }

    static CloudFailure remote(String reason, String stage, JsonObject error, String codeField, String messageField,
                               String requestId, String apiKey) {
        return new CloudFailure(reason, stage, value(error, codeField), value(error, messageField), requestId, apiKey);
    }

    static String value(JsonObject object, String key) {
        JsonElement field = object.get(key);
        return field != null && field.isJsonPrimitive() ? field.getAsString() : "";
    }

    private static String first(String value, String fallback) { return value.isEmpty() ? fallback : value; }

    private static String sanitize(String value, String apiKey) {
        if (apiKey != null && !apiKey.isEmpty()) value = value.replace(apiKey, "[redacted]");
        value = value.replaceAll("(?i)data:audio/[^\\s\\\"']+", "[audio]")
                .replaceAll("(?i)\\bBearer\\s+[^\\s\\\"',;]+|\\bsk-[A-Za-z0-9_.-]+", "[redacted]")
                .replaceAll("[A-Za-z0-9+/_=.-]{80,}", "[redacted]")
                .replaceAll("[\\p{Cc}\\p{Cf}]+", " ").strip();
        return value.length() > 300 ? value.substring(0, 300) + "…" : value;
    }
}
