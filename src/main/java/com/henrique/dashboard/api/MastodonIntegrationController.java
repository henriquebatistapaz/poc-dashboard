package com.henrique.dashboard.api;

import com.henrique.dashboard.domain.MastodonConnection;
import com.henrique.dashboard.service.MastodonIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Integrations", description = "Mastodon account connection (OAuth) and authenticated search")
@RestController
@RequestMapping("/v1/integrations/mastodon")
public class MastodonIntegrationController {
    private final MastodonIntegrationService integration;

    public MastodonIntegrationController(MastodonIntegrationService integration) {
        this.integration = integration;
    }

    @Operation(summary = "Start the OAuth login with a Mastodon instance; returns the URL the user must open")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/connect")
    public ConnectResponse connect(Authentication authentication, @RequestBody ConnectRequest request) {
        URI url = integration.startConnection(authentication.getName(), request == null ? null : request.instance());
        return new ConnectResponse(url.toString());
    }

    @Operation(summary = "OAuth callback opened by the browser after Mastodon; redirects back to the panel")
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(defaultValue = "") String code,
                                         @RequestParam(defaultValue = "") String state) {
        String location = integration.completeConnection(code, state);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
    }

    @Operation(summary = "Connection status of the current user")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/connection")
    public ConnectionResponse connection(Authentication authentication) {
        return integration.connection(authentication.getName())
                .map(connection -> new ConnectionResponse(true, connection.getInstance()))
                .orElseGet(() -> new ConnectionResponse(false, null));
    }

    @Operation(summary = "Remove the current user's Mastodon connection")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/connection")
    public ResponseEntity<Void> disconnect(Authentication authentication) {
        integration.disconnect(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Search public Mastodon posts with the current user's token and store them")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/search")
    public SearchResponse search(Authentication authentication, @RequestBody SearchRequest request) {
        if (request == null || request.query() == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "query is required");
        }
        int limit = request.limit() == null ? 20 : request.limit();
        var outcome = integration.search(authentication.getName(), request.query(), limit);
        return new SearchResponse(outcome.fetched(), outcome.inserted(), outcome.duplicates());
    }

    @Operation(summary = "Publish a post (public or unlisted, default unlisted) on the current user's Mastodon account")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/post")
    public PostResponse post(Authentication authentication, @RequestBody PostRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
        }
        if (request.text().length() > 500) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "text is too long");
        }
        String visibility = request.visibility() == null ? "unlisted" : request.visibility();
        return new PostResponse(integration.post(authentication.getName(), request.text(), visibility));
    }

    public record PostRequest(String text, String visibility) { }

    public record PostResponse(String url) { }

    public record ConnectRequest(String instance) { }

    public record ConnectResponse(String authorizationUrl) { }

    public record ConnectionResponse(boolean connected, String instance) { }

    public record SearchRequest(String query, Integer limit) { }

    public record SearchResponse(int fetched, int inserted, int duplicates) { }
}
