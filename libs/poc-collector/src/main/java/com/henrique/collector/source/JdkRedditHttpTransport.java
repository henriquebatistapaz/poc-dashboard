package com.henrique.collector.source;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class JdkRedditHttpTransport implements RedditHttpTransport {
    public static final int MAX_RESPONSE_BYTES = 1_000_000;
    private final HttpClient client;

    public JdkRedditHttpTransport() {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public RedditHttpResponse postForm(URI uri, Map<String, String> headers, String formBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(formBody));
        headers.forEach(builder::header);
        return send(builder.build());
    }

    @Override
    public RedditHttpResponse get(URI uri, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET();
        headers.forEach(builder::header);
        return send(builder.build());
    }

    private RedditHttpResponse send(HttpRequest request) {
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new RedditTransportException("Reddit response exceeds the configured size limit.");
                }
                return new RedditHttpResponse(response.statusCode(), new String(bytes, java.nio.charset.StandardCharsets.UTF_8),
                        response.headers().map());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RedditTransportException("Reddit request was interrupted.");
        } catch (IOException e) {
            throw new RedditTransportException("Reddit request failed.");
        }
    }
}
