package dev.henrique.collector.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Bounded JSON transport for fixed, public REST APIs. Redirects are deliberately not followed. */
final class RestJsonClient {
    static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Set<String> OFFICIAL_HOSTS = Set.of("mastodon.social", "lemmy.world", "dev.to",
            "hn.algolia.com", "api.stackexchange.com", "api.github.com", "www.tabnews.com.br",
            "en.wikipedia.org", "pt.wikipedia.org", "gitlab.com", "lobste.rs");
    private final URI baseUri;
    private final HttpClient client;
    private final ObjectMapper mapper;

    RestJsonClient(URI baseUri) {
        this(baseUri, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(), new ObjectMapper());
    }

    RestJsonClient(URI baseUri, HttpClient client, ObjectMapper mapper) {
        if (baseUri == null || baseUri.getHost() == null || baseUri.getUserInfo() != null
                || !(isOfficialHttps(baseUri) || isLoopbackHttp(baseUri))) {
            throw new IllegalArgumentException("A valid REST endpoint is required.");
        }
        this.baseUri = baseUri;
        this.client = client;
        this.mapper = mapper;
    }

    URI baseUri() {
        return baseUri;
    }

    JsonNode get(URI uri, Map<String, String> headers) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT)
                    .header("Accept", headers.getOrDefault("Accept", "application/json"))
                    .header("Accept-Encoding", "gzip")
                    .header("User-Agent", "poc-dashboard-collector/1.0")
                    .GET();
            headers.forEach((name, value) -> {
                if (!name.equalsIgnoreCase("Accept")) request.header(name, value);
            });
            HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new SourceAccessException("REST source returned an unsuccessful response.");
                }
                byte[] compressedOrPlain = readBounded(body);
                InputStream decoded = response.headers().firstValue("Content-Encoding")
                        .map(value -> value.toLowerCase(Locale.ROOT).contains("gzip"))
                        .orElse(false) ? new GZIPInputStream(new ByteArrayInputStream(compressedOrPlain))
                        : new ByteArrayInputStream(compressedOrPlain);
                byte[] bytes = readBounded(decoded);
                JsonNode root = mapper.readTree(bytes);
                if (root == null || root.isNull() || !(root.isArray() || root.isObject())) {
                    throw new SourceAccessException("REST source returned invalid JSON.");
                }
                return root;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SourceAccessException("REST source request was interrupted.");
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof SourceAccessException sourceAccessException) throw sourceAccessException;
            throw new SourceAccessException("REST source request failed.");
        }
    }

    private static boolean isOfficialHttps(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && OFFICIAL_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private static boolean isLoopbackHttp(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme()) && uri.getPort() != 0
                && (uri.getHost().equalsIgnoreCase("localhost") || uri.getHost().equals("127.0.0.1")
                || uri.getHost().equals("::1"));
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer, 0, Math.min(buffer.length, MAX_RESPONSE_BYTES + 1 - total))) != -1) {
            total += read;
            if (total > MAX_RESPONSE_BYTES) throw new IOException("Response limit exceeded.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
