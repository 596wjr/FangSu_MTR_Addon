package com.fangsu.announcements.ui;

import com.fangsu.announcements.provider.CloudFailure;
import com.fangsu.mappings.ComponentHelper;
import net.minecraft.network.chat.MutableComponent;

final class TtsErrorText {
    private TtsErrorText() { }

    static CloudFailure find(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (cause instanceof CloudFailure cloud) return cloud;
        return null;
    }

    static MutableComponent describe(String code, CloudFailure failure) {
        String hint = switch (code) {
            case "http_429" -> "rate_limit";
            case "http_401" -> "invalid_key";
            case "http_403" -> "access_denied";
            case "http_404" -> "not_found";
            case "remote_result_uncertain" -> "uncertain";
            default -> code.matches("http_5[0-9]{2}") ? "server" : "";
        };
        MutableComponent result = ComponentHelper.empty();
        if (failure != null) result.append(ComponentHelper.translatable("ui.fangsu.ann.tts_stage_" + failure.stage())).append(" · ");
        if (!hint.isEmpty()) {
            result.append(ComponentHelper.translatable("ui.fangsu.ann.tts_error_" + hint));
            if (code.startsWith("http_")) result.append(" (HTTP " + code.substring(5) + ")");
        } else if (code.equals("aliyun_voice_pending") || code.equals("aliyun_voice_unavailable")) {
            result.append(ComponentHelper.translatable("ui.fangsu.ann." + code));
        } else if (failure != null) {
            result.append(ComponentHelper.translatable("ui.fangsu.ann.tts_error_remote"));
            if (code.startsWith("http_")) result.append(" (HTTP " + code.substring(5) + ")");
        } else result.append(ComponentHelper.literal(code));
        if (failure != null && !failure.details().isEmpty()) result.append(" · ").append(ComponentHelper.literal(failure.details()));
        return result;
    }
}
