package dev.henrique.mastodon.api;

import java.util.concurrent.CountDownLatch;

/**
 * Sobe a API local. Uso: java -cp target/classes dev.henrique.mastodon.api.ApiMain
 * Variáveis: PORT, FRONTEND_URL, OAUTH_REDIRECT_URI, COOKIE_SECURE.
 */
public final class ApiMain {
    private ApiMain() {
    }

    public static void main(String[] args) throws Exception {
        ApiConfig config = ApiConfig.fromEnvironment(System.getenv());
        MastodonBackend backend = new MastodonBackend(new SessionStore(config.sessionTtl()), config.redirectUri());
        ApiServer server = new ApiServer(config, backend);
        server.start();
        System.out.println("poc-collector-oauth API em http://127.0.0.1:" + server.port());
        new CountDownLatch(1).await();
    }
}
