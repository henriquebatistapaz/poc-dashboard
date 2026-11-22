package com.henrique.mastodon.api;

import com.henrique.mastodon.MastodonConfig;
import com.henrique.mastodon.MastodonOAuthClient;
import com.henrique.mastodon.MastodonPost;
import com.henrique.mastodon.MastodonSearchService;
import com.henrique.mastodon.MastodonWriteService;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Fachada: login OAuth por sessão e operações Mastodon usando o token guardado na sessão. */
public final class MastodonBackend {
    static final String APP_NAME = "poc-collector-oauth";
    private final SessionStore sessions;
    private final String redirectUri;
    private final Map<String, MastodonOAuthClient.OAuthApp> apps = new ConcurrentHashMap<>();

    public MastodonBackend(SessionStore sessions, String redirectUri) {
        this.sessions = sessions;
        this.redirectUri = redirectUri;
    }

    /** Inicia o login: registra o app na instância (uma vez) e devolve a URL de autorização. */
    public String startLogin(String instanceInput) {
        if (instanceInput == null || instanceInput.isBlank()) {
            throw new IllegalArgumentException("Parâmetro obrigatório: instance");
        }
        String origin = MastodonConfig.originOf(URI.create(instanceInput.trim()));
        MastodonOAuthClient oauth = new MastodonOAuthClient(URI.create(origin));
        MastodonOAuthClient.OAuthApp app = apps.get(origin);
        if (app == null) {
            app = oauth.registerApp(APP_NAME, redirectUri);
            apps.put(origin, app);
        }
        MastodonOAuthClient.AuthorizationRequest request = oauth.authorizationRequest(app);
        sessions.putPending(request.state(), app, origin);
        return request.url().toString();
    }

    /** Conclui o login e devolve o id da nova sessão. */
    public String finishLogin(String state, String code) {
        SessionStore.PendingLogin pending = sessions.takePending(state)
                .orElseThrow(() -> new ApiException(400, "Login expirado ou inválido."));
        String token = new MastodonOAuthClient(URI.create(pending.origin())).exchangeCode(pending.app(), code);
        return sessions.create(pending.origin(), token);
    }

    public Optional<String> instanceOf(String sessionId) {
        return sessions.find(sessionId).map(SessionStore.Session::origin);
    }

    public void logout(String sessionId) {
        sessions.remove(sessionId);
    }

    public List<MastodonPost> search(String sessionId, String query, int limit) {
        return new MastodonSearchService(config(sessionId)).searchPublicStatuses(query, limit);
    }

    public MastodonPost publish(String sessionId, String text, String visibility) {
        return new MastodonWriteService(config(sessionId)).publish(text, visibility);
    }

    public boolean favourite(String sessionId, String statusId) {
        return new MastodonWriteService(config(sessionId)).favourite(statusId);
    }

    public void delete(String sessionId, String statusId) {
        new MastodonWriteService(config(sessionId)).delete(statusId);
    }

    private MastodonConfig config(String sessionId) {
        SessionStore.Session session = sessions.find(sessionId)
                .orElseThrow(() -> new ApiException(401, "Sessão ausente ou expirada. Faça login de novo."));
        return new MastodonConfig(URI.create(session.origin()), session.accessToken());
    }
}
