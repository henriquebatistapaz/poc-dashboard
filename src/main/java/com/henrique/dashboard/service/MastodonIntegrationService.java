package com.henrique.dashboard.service;

import com.henrique.dashboard.domain.CollectedItem;
import com.henrique.dashboard.domain.MastodonApp;
import com.henrique.dashboard.domain.MastodonConnection;
import com.henrique.dashboard.domain.MastodonOauthState;
import com.henrique.dashboard.repository.CollectedItemRepository;
import com.henrique.dashboard.repository.MastodonAppRepository;
import com.henrique.dashboard.repository.MastodonConnectionRepository;
import com.henrique.dashboard.repository.MastodonOauthStateRepository;
import com.henrique.mastodon.MastodonConfig;
import com.henrique.mastodon.MastodonException;
import com.henrique.mastodon.MastodonOAuthClient;
import com.henrique.mastodon.MastodonPost;
import com.henrique.mastodon.MastodonSearchService;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Conecta a conta Mastodon de cada usuário do dashboard e faz buscas com o token dela. */
@Service
public class MastodonIntegrationService {
    public static final String SOURCE = "mastodon-oauth";
    static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final String APP_NAME = "poc-dashboard";
    private static final int TITLE_MAX = 1000;
    private static final int LINK_MAX = 2000;
    private static final int EXTERNAL_ID_MAX = 2000;

    private final MastodonAppRepository apps;
    private final MastodonOauthStateRepository states;
    private final MastodonConnectionRepository connections;
    private final ScheduleInputCipher cipher;
    private final CollectedItemRepository collectedItems;
    private final Set<String> allowedOrigins;
    private final String redirectUri;
    private final String returnUrl;
    private final Clock clock;

    public MastodonIntegrationService(MastodonAppRepository apps,
                                      MastodonOauthStateRepository states,
                                      MastodonConnectionRepository connections,
                                      ScheduleInputCipher cipher,
                                      CollectedItemRepository collectedItems,
                                      @Value("${dashboard.mastodon.allowed-instances:https://mastodon.social}") String allowedInstances,
                                      @Value("${dashboard.public-url:http://localhost:8084}") String publicUrl,
                                      @Value("${dashboard.mastodon.return-url:http://localhost:8085/}") String returnUrl) {
        this.apps = apps;
        this.states = states;
        this.connections = connections;
        this.cipher = cipher;
        this.collectedItems = collectedItems;
        this.allowedOrigins = Set.copyOf(java.util.Arrays.stream(allowedInstances.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> MastodonConfig.originOf(URI.create(s)).toLowerCase(Locale.ROOT)).toList());
        this.redirectUri = stripTrailingSlash(publicUrl) + "/v1/integrations/mastodon/callback";
        this.returnUrl = returnUrl;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public URI startConnection(String userId, String instanceInput) {
        requireCipher();
        String origin = allowedOrigin(instanceInput);
        MastodonOAuthClient client = new MastodonOAuthClient(URI.create(origin));
        MastodonApp app = apps.findById(origin).orElseGet(() -> registerApp(origin, client));
        String secret = cipher.decrypt(app.getEncryptedClientSecret());
        var oauthApp = new MastodonOAuthClient.OAuthApp(app.getClientId(), secret, redirectUri);
        var request = client.authorizationRequest(oauthApp);
        states.save(new MastodonOauthState(request.state(), userId, origin, Instant.now(clock).plus(STATE_TTL)));
        return request.url();
    }

    /** Chamado pelo navegador após o Mastodon. Sempre devolve a URL de retorno ao painel. */
    @Transactional
    public String completeConnection(String code, String state) {
        if (!cipher.isConfigured() || state == null || code == null || code.isBlank()) return failure();
        MastodonOauthState pending = states.findById(state).orElse(null);
        if (pending == null) return failure();
        states.delete(pending);
        if (pending.getExpiresAt().isBefore(Instant.now(clock))) return failure();
        try {
            MastodonApp app = apps.findById(pending.getInstance()).orElseThrow();
            var oauthApp = new MastodonOAuthClient.OAuthApp(app.getClientId(),
                    cipher.decrypt(app.getEncryptedClientSecret()), redirectUri);
            String token = new MastodonOAuthClient(URI.create(pending.getInstance())).exchangeCode(oauthApp, code);
            Instant now = Instant.now(clock);
            String encrypted = cipher.encrypt(token);
            connections.findById(pending.getUserId())
                    .ifPresentOrElse(existing -> existing.replaceToken(pending.getInstance(), encrypted, now),
                            () -> connections.save(new MastodonConnection(pending.getUserId(), pending.getInstance(), encrypted, now)));
            return returnUrl + (returnUrl.contains("?") ? "&" : "?") + "mastodon=connected";
        } catch (RuntimeException exception) {
            return failure();
        }
    }

    @Transactional(readOnly = true)
    public Optional<MastodonConnection> connection(String userId) {
        return connections.findById(userId);
    }

    @Transactional
    public void disconnect(String userId) {
        connections.findById(userId).ifPresent(connections::delete);
    }

    public SearchOutcome search(String userId, String query, int limit) {
        requireCipher();
        MastodonConnection connection = connections.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Mastodon account is not connected"));
        String token = cipher.decrypt(connection.getEncryptedAccessToken());
        List<MastodonPost> posts;
        try {
            posts = new MastodonSearchService(new MastodonConfig(URI.create(connection.getInstance()), token))
                    .searchPublicStatuses(query, limit);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (MastodonException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mastodon search failed");
        }
        Map<String, CollectedItem> fresh = new LinkedHashMap<>();
        Instant now = Instant.now(clock);
        for (MastodonPost post : posts) {
            if (post.url() == null || post.url().isBlank()) continue;
            fresh.putIfAbsent(truncate(post.id(), EXTERNAL_ID_MAX), toEntity(post, now));
        }
        Set<String> existing = fresh.isEmpty() ? Set.of()
                : collectedItems.findExistingExternalIds(SOURCE, fresh.keySet());
        List<CollectedItem> toStore = fresh.entrySet().stream()
                .filter(entry -> !existing.contains(entry.getKey()))
                .map(Map.Entry::getValue).toList();
        collectedItems.saveAll(toStore);
        return new SearchOutcome(posts.size(), toStore.size(), fresh.size() - toStore.size());
    }

    public record SearchOutcome(int fetched, int inserted, int duplicates) { }

    private static CollectedItem toEntity(MastodonPost post, Instant collectedAt) {
        String text = post.text() == null ? "" : post.text().replaceAll("\\s+", " ").trim();
        String title = text.isEmpty() ? "@" + post.accountAcct() : truncate(text, TITLE_MAX);
        return new CollectedItem(SOURCE, truncate(post.id(), EXTERNAL_ID_MAX), title,
                truncate(post.url(), LINK_MAX), post.createdAt(), collectedAt);
    }

    private MastodonApp registerApp(String origin, MastodonOAuthClient client) {
        var registered = client.registerApp(APP_NAME, redirectUri);
        var app = new MastodonApp(origin, registered.clientId(), cipher.encrypt(registered.clientSecret()), Instant.now(clock));
        return apps.save(app);
    }

    private String allowedOrigin(String input) {
        if (input == null || input.isBlank()) throw badRequest("instance is required");
        String origin;
        try {
            origin = MastodonConfig.originOf(URI.create(input.trim()));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw badRequest("Invalid Mastodon instance");
        }
        if (!allowedOrigins.contains(origin.toLowerCase(Locale.ROOT))) throw badRequest("Mastodon instance is not allowed");
        return origin;
    }

    private void requireCipher() {
        if (!cipher.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Integration key is not configured");
        }
    }

    private String failure() {
        return returnUrl + (returnUrl.contains("?") ? "&" : "?") + "mastodon=error";
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
