package com.fangsu.announcements.cache;

import java.io.IOException;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** 严格接收 PCM16 单/双声道 WAV，拒绝扩展名伪装、截断和超预算音频。 */
public record WavInfo(int sampleRate, int channels, int dataOffset, int dataLength, double seconds) {
    public static WavInfo inspect(byte[] bytes) throws IOException {
        if (bytes.length < 44 || bytes.length > 24 * 1024 * 1024 || !tag(bytes, 0).equals("RIFF") || !tag(bytes, 8).equals("WAVE")) throw new IOException("invalid_wav");
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (Integer.toUnsignedLong(b.getInt(4)) + 8 != bytes.length) throw new IOException("truncated_wav");
        int rate = 0, channels = 0, offset = -1, length = 0;
        for (long pos = 12; pos + 8 <= bytes.length;) {
            int p = (int) pos; long size = Integer.toUnsignedLong(b.getInt(p + 4));
            if (pos + 8 + size > bytes.length) throw new IOException("truncated_chunk");
            String tag = tag(bytes, p);
            if (tag.equals("fmt ")) {
                if (size < 16 || b.getShort(p + 8) != 1 || b.getShort(p + 22) != 16) throw new IOException("unsupported_wav_encoding");
                channels = b.getShort(p + 10); rate = b.getInt(p + 12);
                if (channels < 1 || channels > 2 || rate < 8000 || rate > 96000 || b.getShort(p + 20) != channels * 2 || b.getInt(p + 16) != rate * channels * 2) throw new IOException("invalid_wav_format");
            } else if (tag.equals("data")) { offset = p + 8; length = (int) size; }
            pos += 8 + size + (size & 1);
        }
        if (rate == 0 || offset < 0 || length == 0 || length % (channels * 2) != 0) throw new IOException("missing_wav_data");
        double seconds = length / (double) (rate * channels * 2);
        if (seconds > 180) throw new IOException("audio_too_long");
        return new WavInfo(rate, channels, offset, length, seconds);
    }
    private static String tag(byte[] bytes, int offset) { return new String(bytes, offset, 4, StandardCharsets.US_ASCII); }
}
