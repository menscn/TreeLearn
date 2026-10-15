package com.treelearn.ai.gateway;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

/**
 * The real socket layer: {@code HttpsURLConnection}, streaming, no third-party HTTP client
 * (technical design 9.1).
 *
 * <p>Three rules are enforced here rather than trusted to callers. The URL goes through
 * {@link GatewayEndpoints#requireHttps} before a connection exists, so cleartext cannot be reached.
 * The request body is written with a fixed length, so nothing is buffered twice. And for a non-2xx
 * status the error body is <em>never</em> fetched or read — only the status is classified — because
 * a provider error body can echo the request.
 *
 * <p>Timeouts are explicit: the read timeout has to be generous, since a slow reasoning phase can
 * legitimately send nothing for a while.
 */
public final class HttpsTransport implements GatewayPorts.Transport {

    /** Handed out for a non-2xx status, where the body must not be read. */
    private static final InputStream NO_BODY = new ByteArrayInputStream(new byte[0]);

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public HttpsTransport(int connectTimeoutMillis, int readTimeoutMillis) {
        this.connectTimeoutMillis = requirePositive(connectTimeoutMillis, "connectTimeoutMillis");
        this.readTimeoutMillis = requirePositive(readTimeoutMillis, "readTimeoutMillis");
    }

    @Override
    public GatewayPorts.Response post(String url, String apiKey, byte[] body) throws IOException {
        return open("POST", url, apiKey, body);
    }

    @Override
    public GatewayPorts.Response get(String url, String apiKey) throws IOException {
        return open("GET", url, apiKey, null);
    }

    private GatewayPorts.Response open(String method, String url, String apiKey, byte[] body)
            throws IOException {
        HttpsURLConnection connection =
                (HttpsURLConnection) new URL(GatewayEndpoints.requireHttps(url)).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setUseCaches(false);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Accept", "text/event-stream");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(body.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream request = connection.getOutputStream()) {
                request.write(body);
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status / 100 == 2 ? connection.getInputStream() : NO_BODY;
        return new HttpStream(connection, status, stream);
    }

    private static int requirePositive(int value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be >= 1 but was " + value);
        }
        return value;
    }

    private static final class HttpStream implements GatewayPorts.Response {

        private final HttpURLConnection connection;
        private final int status;
        private final InputStream stream;

        HttpStream(HttpURLConnection connection, int status, InputStream stream) {
            this.connection = connection;
            this.status = status;
            this.stream = stream;
        }

        @Override
        public int status() {
            return status;
        }

        @Override
        public InputStream body() {
            return stream;
        }

        /** Also the cancellation path: disconnecting unblocks a pending read. */
        @Override
        public void close() {
            connection.disconnect();
        }
    }
}