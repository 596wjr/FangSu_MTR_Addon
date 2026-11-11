package com.fangsu.announcements.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** 长度前缀编码避免分隔符歧义；调用者传实际合成参数，严禁加入密钥或播放身份。 */
public final class SynthesisKey {
    private SynthesisKey() { }
    public static String of(Map<String, String> parameters) {
        StringBuilder canonical = new StringBuilder("fangsu-tts-v1;");
        new TreeMap<>(parameters).forEach((key, value) -> {
            String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
            canonical.append(key.length()).append(':').append(key).append(normalized.length()).append(':').append(normalized);
        });
        return digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }
    public static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
