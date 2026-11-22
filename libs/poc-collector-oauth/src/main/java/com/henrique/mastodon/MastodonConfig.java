package com.henrique.mastodon;

import java.net.URI;
import java.util.Locale;

/** Instância Mastodon e token de acesso. O token nunca aparece em mensagens nem em toString. */
public record MastodonConfig(URI instance, String accessToken) {

    public MastodonConfig {
        originOf(instance);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Token de acesso Mastodon ausente.");
        }
    }

    /** Origem sem caminho (ex.: https://mastodon.social), após validar que a instância é segura. */
    public static String originOf(URI instance) {
        if (instance == null || instance.getHost() == null || instance.getUserInfo() != null) {
            throw new IllegalArgumentException("Instância Mastodon inválida.");
        }
        String scheme = instance.getScheme() == null ? "" : instance.getScheme().toLowerCase(Locale.ROOT);
        boolean https = "https".equals(scheme);
        boolean loopback = "http".equals(scheme) && isLoopback(instance.getHost());
        if (!https && !loopback) {
            throw new IllegalArgumentException("A instância Mastodon precisa usar HTTPS.");
        }
        int port = instance.getPort();
        return scheme + "://" + instance.getHost() + (port == -1 ? "" : ":" + port);
    }

    public String origin() {
        return originOf(instance);
    }

    @Override public String toString() {
        return "MastodonConfig[instance=" + origin() + ", accessToken=***]";
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host);
    }
}
