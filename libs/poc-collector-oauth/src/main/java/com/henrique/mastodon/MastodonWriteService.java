package com.henrique.mastodon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Operações de escrita da API Mastodon: publicar (POST /api/v1/statuses),
 * curtir (POST /api/v1/statuses/{id}/favourite) e apagar (DELETE /api/v1/statuses/{id}).
 * Exige token com escopos write:statuses e write:favourites. Redirects não são seguidos.
 */
public final class MastodonWriteService {
    static final int MAX_TEXT_LENGTH = 500;
    static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final Set<String> VISIBILITIES = Set.of("public", "unlisted", "private", "direct");
    private static final Pattern STATUS_ID = Pattern.compile("[0-9A-Za-z]{1,64}");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final MastodonConfig config;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public MastodonWriteService(MastodonConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    MastodonWriteService(MastodonConfig config, HttpClient http) {
        if (config == null) throw new IllegalArgumentException("Configuração Mastodon ausente.");
        this.config = config;
        this.http = http;
    }

    /** Publica um status e retorna o post criado. */
    public MastodonPost publish(String text, String visibility) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Texto do post ausente.");
        String trimmed = text.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Post maior que " + MAX_TEXT_LENGTH + " caracteres.");
        }
        String vis = visibility == null ? "public" : visibility;
        if (!VISIBILITIES.contains(vis)) {
            throw new IllegalArgumentException("Visibilidade inválida: use public, unlisted, private ou direct.");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("status", trimmed);
        form.put("visibility", vis);
        JsonNode json = send("POST", "/api/v1/statuses", form);
        String id = json.path("id").asText("");
        if (id.isEmpty()) throw new MastodonException("Resposta de publicação sem id do status.");
        return MastodonSearchService.toPost(json, id);
    }

    /** Curte um status. Retorna true se a instância confirmar o favorito. */
    public boolean favourite(String statusId) {
        String id = validId(statusId);
        JsonNode json = send("POST", "/api/v1/statuses/" + id + "/favourite", null);
        return json.path("favourited").asBoolean(false);
    }

    /** Apaga um status próprio. */
    public void delete(String statusId) {
        String id = validId(statusId);
        send("DELETE", "/api/v1/statuses/" + id, null);
    }

    private static String validId(String statusId) {
        if (statusId == null || !STATUS_ID.matcher(statusId).matches()) {
            throw new IllegalArgumentException("Id de status inválido.");
        }
        return statusId;
    }

    private JsonNode send(String method, String path, Map<String, String> form) {
        HttpRequest.BodyPublisher body = form == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(encode(form), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.origin() + path))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + config.accessToken())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .method(method, body)
                .build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                checkStatus(response.statusCode());
                byte[] bytes = readBounded(in);
                if (bytes.length == 0) return mapper.createObjectNode();
                JsonNode json = mapper.readTree(bytes);
                if (json == null || !json.isObject()) {
                    throw new MastodonException("Resposta Mastodon malformada.");
                }
                return json;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MastodonException("Operação Mastodon interrompida.");
        } catch (IOException exception) {
            throw new MastodonException("Falha de rede ou timeout na operação Mastodon.");
        }
    }

    private static void checkStatus(int status) {
        if (status >= 200 && status < 300) return;
        if (status == 401 || status == 403) {
            throw new MastodonException("Token Mastodon inválido ou sem escopo de escrita (HTTP " + status + ").");
        }
        if (status == 404) throw new MastodonException("Status não encontrado na instância (HTTP 404).");
        if (status == 422) throw new MastodonException("A instância Mastodon recusou a operação (HTTP 422).");
        if (status == 429) throw new MastodonException("A instância Mastodon limitou as requisições (HTTP 429).");
        if (status >= 300 && status < 400) {
            throw new MastodonException("Redirecionamento bloqueado para proteger o token (HTTP " + status + ").");
        }
        throw new MastodonException("Falha na operação Mastodon (HTTP " + status + ").");
    }

    private static String encode(Map<String, String> form) {
        StringBuilder body = new StringBuilder();
        form.forEach((k, v) -> {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(k, StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return body.toString();
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        byte[] data = input.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (data.length > MAX_RESPONSE_BYTES) {
            throw new MastodonException("Resposta Mastodon maior que o limite permitido.");
        }
        return data;
    }
}
