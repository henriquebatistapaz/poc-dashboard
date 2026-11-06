package com.henrique.collector.feed;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class JdkHttpFeedFetcher implements FeedFetcher {
    public static final int MAX_RESPONSE_BYTES = 1_000_000;
    private final HttpClient client;

    public JdkHttpFeedFetcher() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public byte[] fetch(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                .header("User-Agent", "MultiSourceCollectorMini/0.1 (+private project feed reader)")
                .GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new FeedFetchException("Feed endpoint returned HTTP " + response.statusCode() + ".");
                }
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) throw new FeedFetchException("Feed response exceeds the size limit.");
                return bytes;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FeedFetchException("Feed request was interrupted.", e);
        } catch (IOException e) {
            throw new FeedFetchException("Feed request failed.", e);
        }
    }
}
