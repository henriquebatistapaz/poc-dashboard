package com.henrique.mastodon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Busca de posts públicos via API oficial do Mastodon (GET /api/v2/search, type=statuses).
 * Redirects não são seguidos, para o token nunca sair para outro host.
 */
public final class MastodonSearchService {
    static final int MAX_QUERY_LENGTH = 500;
    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 40;
    static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final MastodonConfig config;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public MastodonSearchService(MastodonConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    MastodonSearchService(MastodonConfig config, HttpClient http) {
        if (config == null) throw new IllegalArgumentException("Configuração Mastodon ausente.");
        this.config = config;
        this.http = http;
    }

    /** Retorna posts públicos únicos (por id), na ordem devolvida pela instância. */
    public List<MastodonPost> searchPublicStatuses(String query, int limit) {
        String q = validateQuery(query);
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limite de busca fora do intervalo 1 a " + MAX_LIMIT + ".");
        }
        URI uri = URI.create(config.origin() + "/api/v2/search?q=" + encode(q)
                + "&type=statuses&limit=" + limit);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + config.accessToken())
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                checkStatus(response.statusCode());
                return parse(readBounded(body));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MastodonException("Busca Mastodon interrompida.");
        } catch (IOException exception) {
            throw new MastodonException("Falha de rede ou timeout ao consultar a instância Mastodon.");
        }
    }

    private static String validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Informe um termo de busca.");
        }
        String trimmed = query.trim();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("Termo de busca maior que " + MAX_QUERY_LENGTH + " caracteres.");
        }
        return trimmed;
    }

    private static void checkStatus(int status) {
        if (status == 401 || status == 403) {
            throw new MastodonException("Token Mastodon inválido ou sem escopo read:search (HTTP " + status + ").");
        }
        if (status == 429) {
            throw new MastodonException("A instância Mastodon limitou as requisições (HTTP 429).");
        }
        if (status == 422) {
            throw new MastodonException("A instância Mastodon recusou a busca (HTTP 422).");
        }
        if (status >= 300 && status < 400) {
            throw new MastodonException("Redirecionamento bloqueado para proteger o token (HTTP " + status + ").");
        }
        if (status < 200 || status >= 300) {
            throw new MastodonException("Falha na busca Mastodon (HTTP " + status + ").");
        }
    }

    List<MastodonPost> parse(byte[] bytes) throws IOException {
        JsonNode root = mapper.readTree(bytes);
        if (root == null || !root.isObject() || !root.path("statuses").isArray()) {
            throw new MastodonException("Resposta Mastodon sem a coleção statuses esperada.");
        }
        Map<String, MastodonPost> unique = new LinkedHashMap<>();
        for (JsonNode node : root.path("statuses")) {
            if (!"public".equals(node.path("visibility").asText())) continue;
            String id = node.path("id").asText("");
            if (id.isEmpty()) continue;
            unique.putIfAbsent(id, toPost(node, id));
        }
        return new ArrayList<>(unique.values());
    }

    static MastodonPost toPost(JsonNode node, String id) {
        String url = node.path("url").asText(null);
        if (url == null || url.isBlank()) url = node.path("uri").asText(null);
        String acct = node.path("account").path("acct").asText(null);
        Instant created = parseInstant(node.path("created_at").asText(null));
        return new MastodonPost(id, url, acct, htmlToText(node.path("content").asText("")), created);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    static String htmlToText(String html) {
        String text = html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", "");
        return text.replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer, 0, Math.min(buffer.length, MAX_RESPONSE_BYTES + 1 - total))) != -1) {
            total += read;
            if (total > MAX_RESPONSE_BYTES) {
                throw new MastodonException("Resposta Mastodon maior que o limite permitido.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
