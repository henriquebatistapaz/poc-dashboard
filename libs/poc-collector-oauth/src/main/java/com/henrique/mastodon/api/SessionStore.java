package com.henrique.mastodon.api;

import com.henrique.mastodon.MastodonOAuthClient;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Sessões e logins pendentes em memória. Tokens ficam só no servidor; o cliente recebe um id opaco. */
public final class SessionStore {
    private static final Duration PENDING_TTL = Duration.ofMinutes(10);
    private final Duration sessionTtl;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, PendingLogin> pending = new ConcurrentHashMap<>();

    public SessionStore(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public record Session(String origin, String accessToken, Instant expiresAt) {
        @Override public String toString() {
            return "Session[origin=" + origin + ", accessToken=***, expiresAt=" + expiresAt + "]";
        }
    }

    public record PendingLogin(String origin, MastodonOAuthClient.OAuthApp app, Instant expiresAt) {
    }

    public String create(String origin, String accessToken) {
        String id = randomId();
        sessions.put(id, new Session(origin, accessToken, Instant.now().plus(sessionTtl)));
        return id;
    }

    public Optional<Session> find(String id) {
        if (id == null) return Optional.empty();
        Session session = sessions.get(id);
        if (session == null) return Optional.empty();
        if (Instant.now().isAfter(session.expiresAt())) {
            sessions.remove(id);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void remove(String id) {
        if (id != null) sessions.remove(id);
    }

    public void putPending(String state, MastodonOAuthClient.OAuthApp app, String origin) {
        pending.put(state, new PendingLogin(origin, app, Instant.now().plus(PENDING_TTL)));
    }

    /** Consome o login pendente: cada state só pode ser usado uma vez. */
    public Optional<PendingLogin> takePending(String state) {
        if (state == null) return Optional.empty();
        PendingLogin login = pending.remove(state);
        if (login == null || Instant.now().isAfter(login.expiresAt())) return Optional.empty();
        return Optional.of(login);
    }

    private String randomId() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
