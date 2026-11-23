package dev.henrique.collectoroauth;

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
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluxo OAuth2 authorization_code do Mastodon: registra o app (POST /api/v1/apps),
 * monta a URL de autorização e troca o código por token (POST /oauth/token).
 * Redirects não são seguidos e segredos nunca entram em mensagens de erro.
 */
public final class MastodonOAuthClient {
    static final String SCOPE = "read write follow push";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    private final String origin;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    public MastodonOAuthClient(MastodonConfig instance) {
        this(instance.origin(), defaultClient());
    }

    /** Cliente OAuth para uma instância ainda sem token (fase de login). */
    public MastodonOAuthClient(URI instance) {
        this(MastodonConfig.originOf(instance), defaultClient());
    }

    MastodonOAuthClient(String origin, HttpClient http) {
        this.origin = origin;
        this.http = http;
    }

    private static HttpClient defaultClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Registra o app na instância. Retorna client_id e client_secret. */
    public OAuthApp registerApp(String clientName, String redirectUri) {
        requireText(clientName, "Nome do app");
        requireRedirect(redirectUri);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_name", clientName);
        form.put("redirect_uris", redirectUri);
        form.put("scopes", SCOPE);
        JsonNode json = postForm("/api/v1/apps", form);
        String clientId = json.path("client_id").asText("");
        String clientSecret = json.path("client_secret").asText("");
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            throw new MastodonException("Resposta de registro do app sem client_id ou client_secret.");
        }
        return new OAuthApp(clientId, clientSecret, redirectUri);
    }

    /** URL para o usuário autorizar o app. O state é aleatório e deve ser conferido no retorno. */
    public AuthorizationRequest authorizationRequest(OAuthApp app) {
        String state = randomState();
        String url = origin + "/oauth/authorize"
                + "?client_id=" + enc(app.clientId())
                + "&redirect_uri=" + enc(app.redirectUri())
                + "&response_type=code"
                + "&scope=" + enc(SCOPE)
                + "&state=" + enc(state);
        return new AuthorizationRequest(URI.create(url), state);
    }

    /** Troca o código de autorização pelo access token do usuário. */
    public String exchangeCode(OAuthApp app, String code) {
        requireText(code, "Código de autorização");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("client_id", app.clientId());
        form.put("client_secret", app.clientSecret());
        form.put("redirect_uri", app.redirectUri());
        form.put("code", code);
        form.put("scope", SCOPE);
        JsonNode json = postForm("/oauth/token", form);
        String token = json.path("access_token").asText("");
        if (token.isEmpty()) {
            throw new MastodonException("A instância não retornou access_token.");
        }
        return token;
    }

    private JsonNode postForm(String path, Map<String, String> form) {
        StringBuilder body = new StringBuilder();
        form.forEach((k, v) -> {
            if (body.length() > 0) body.append('&');
            body.append(enc(k)).append('=').append(enc(v));
        });
        HttpRequest request = HttpRequest.newBuilder(URI.create(origin + path))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    throw new MastodonException("Falha OAuth na instância Mastodon (HTTP " + status + ").");
                }
                JsonNode json = mapper.readTree(readBounded(in));
                if (json == null || !json.isObject()) {
                    throw new MastodonException("Resposta OAuth malformada.");
                }
                return json;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MastodonException("Requisição OAuth interrompida.");
        } catch (IOException exception) {
            throw new MastodonException("Falha de rede ou timeout na requisição OAuth.");
        }
    }

    private String randomState() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void requireRedirect(String redirectUri) {
        URI uri = URI.create(redirectUri);
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean loopback = "http".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && (uri.getHost().equals("127.0.0.1") || uri.getHost().equalsIgnoreCase("localhost"));
        if (!https && !loopback) {
            throw new IllegalArgumentException("redirect_uri precisa ser HTTPS ou loopback.");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " ausente.");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        byte[] data = input.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (data.length > MAX_RESPONSE_BYTES) {
            throw new MastodonException("Resposta OAuth maior que o limite permitido.");
        }
        return data;
    }

    /** Credenciais do app registrado. O client_secret não aparece em toString. */
    public record OAuthApp(String clientId, String clientSecret, String redirectUri) {
        @Override public String toString() {
            return "OAuthApp[clientId=" + clientId + ", clientSecret=***, redirectUri=" + redirectUri + "]";
        }
    }

    public record AuthorizationRequest(URI url, String state) {
    }
}
