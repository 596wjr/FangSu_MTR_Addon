package com.fangsu.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/** 下载 HTTP(S) 图片，供客户端后台解码。 */
public final class NetworkImageLoader {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_REDIRECTS = 5;

    private NetworkImageLoader() {
    }

    public static boolean isHttpUrl(String path) {
        return path.regionMatches(true, 0, "http://", 0, 7)
                || path.regionMatches(true, 0, "https://", 0, 8);
    }

    public static byte[] download(URI uri) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            checkDeadline(deadline);
            if (!isHttpUrl(uri.toString()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IOException("Invalid HTTP(S) image URL");
            }
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("Accept", "image/*");
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String target = connection.getHeaderField("Location");
                    if (target == null) throw new IOException("Image redirect is missing Location");
                    uri = uri.resolve(target);
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) throw new IOException("Image HTTP status " + status);
                if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("Image download is too large");

                try (InputStream input = connection.getInputStream()) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        checkDeadline(deadline);
                        if (output.size() + count > MAX_BYTES) throw new IOException("Image download is too large");
                        output.write(buffer, 0, count);
                    }
                    return output.toByteArray();
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many image redirects");
    }

    private static void checkDeadline(long deadline) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Image download cancelled");
        if (System.nanoTime() - deadline >= 0) throw new IOException("Image download timed out");
    }
}
