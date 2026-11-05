package dev.lilt.player.stream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Minimal blocking HTTP GET client over {@link HttpURLConnection}: timeouts, gzip, a fixed
 * User-Agent and a response-size cap. Stateless and thread-safe. Pure Java (no Android imports)
 * so it is exercised by the offline tests against a local server. Never call on the main thread.
 */
public final class Http {
    /** A completed response (any status). */
    public static final class Response {
        public final int code;
        public final String url;
        public final String contentType;
        public final byte[] body;
        Response(int code, String url, String contentType, byte[] body) {
            this.code = code; this.url = url; this.contentType = contentType == null ? "" : contentType; this.body = body;
        }
        public boolean ok() { return code >= 200 && code < 300; }
        public String text() { return new String(body, StandardCharsets.UTF_8); }
    }

    /** Non-2xx status surfaced as an {@link IOException} so callers can handle one failure type. */
    public static final class StatusException extends IOException {
        private static final long serialVersionUID = 1L;
        public final int code;
        public StatusException(int code, String what) { super("HTTP " + code + " for " + what); this.code = code; }
    }

    private final String userAgent;
    private final int connectTimeoutMs, readTimeoutMs, maxBytes;

    public Http(String userAgent, int connectTimeoutMs, int readTimeoutMs, int maxBytes) {
        this.userAgent = userAgent;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.maxBytes = maxBytes;
    }

    /** Blocking GET. Returns 4xx/5xx responses too; throws only on transport failures or oversize bodies. */
    public Response get(String url, String accept) throws IOException {
        URLConnection opened;
        try { opened = URI.create(url).toURL().openConnection(); }
        catch (IllegalArgumentException malformed) { throw new IOException("Malformed URL", malformed); }
        if (!(opened instanceof HttpURLConnection)) throw new IOException("Not an HTTP URL");
        HttpURLConnection connection = (HttpURLConnection) opened;
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Accept", accept == null ? "*/*" : accept);
        connection.setRequestProperty("Accept-Encoding", "gzip");
        boolean complete = false;
        try {
            int code = connection.getResponseCode();
            InputStream raw = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] body = new byte[0];
            if (raw != null) {
                try (InputStream in = "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? new GZIPInputStream(raw) : raw) {
                    body = readAll(in, maxBytes);
                }
            }
            complete = true;
            return new Response(code, connection.getURL().toString(), connection.getContentType(), body);
        } finally {
            // A fully read and closed body leaves the socket in the keep-alive pool; only tear down on failure.
            if (!complete) connection.disconnect();
        }
    }

    static byte[] readAll(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (out.size() + count > maxBytes) throw new IOException("Response exceeds " + maxBytes + " bytes");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    /** {@code application/x-www-form-urlencoded} UTF-8 encoding for query values. */
    public static String encode(String value) {
        try { return URLEncoder.encode(value == null ? "" : value, "UTF-8"); }
        catch (UnsupportedEncodingException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Percent-encoding for one path segment (spaces as %20, never '+'). */
    public static String encodePath(String segment) {
        return encode(segment).replace("+", "%20");
    }
}
