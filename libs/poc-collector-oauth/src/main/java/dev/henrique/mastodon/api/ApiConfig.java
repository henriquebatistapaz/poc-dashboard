package dev.henrique.mastodon.api;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Configuração da API. Lida de variáveis de ambiente:
 * PORT (8080), FRONTEND_URL (http://localhost:5173/), OAUTH_REDIRECT_URI, COOKIE_SECURE (false).
 */
public record ApiConfig(int port, String frontendUrl, String redirectUri, boolean secureCookies, Duration sessionTtl) {

    public ApiConfig {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Porta inválida.");
        URI frontend = URI.create(frontendUrl);
        if (frontend.getHost() == null) throw new IllegalArgumentException("FRONTEND_URL inválida.");
        if (sessionTtl == null || sessionTtl.isNegative() || sessionTtl.isZero()) {
            throw new IllegalArgumentException("Duração da sessão inválida.");
        }
    }

    /** Origem do frontend (esquema, host e porta), usada no CORS. */
    public String frontendOrigin() {
        URI u = URI.create(frontendUrl);
        return u.getScheme() + "://" + u.getHost() + (u.getPort() == -1 ? "" : ":" + u.getPort());
    }

    public static ApiConfig fromEnvironment(Map<String, String> env) {
        int port = Integer.parseInt(env.getOrDefault("PORT", "8080"));
        String frontend = env.getOrDefault("FRONTEND_URL", "http://localhost:5173/");
        String redirect = env.getOrDefault("OAUTH_REDIRECT_URI", "http://127.0.0.1:" + port + "/api/oauth/callback");
        boolean secure = Boolean.parseBoolean(env.getOrDefault("COOKIE_SECURE", "false"));
        return new ApiConfig(port, frontend, redirect, secure, Duration.ofHours(8));
    }
}
