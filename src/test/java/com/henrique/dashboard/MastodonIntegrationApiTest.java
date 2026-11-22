package com.henrique.dashboard;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henrique.dashboard.domain.MastodonOauthState;
import com.henrique.dashboard.repository.MastodonConnectionRepository;
import com.henrique.dashboard.repository.MastodonOauthStateRepository;
import com.henrique.dashboard.service.ScheduleInputCipher;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.jdbc.core.JdbcTemplate;
import com.henrique.dashboard.security.JwtConfiguration;

/** Fluxo completo de integração Mastodon com um servidor falso em loopback (sem rede real). */
@SpringBootTest
@AutoConfigureMockMvc
class MastodonIntegrationApiTest {
    private static final String GOOD_CODE = "good-code";
    private static final String USER_TOKEN = "user-token-A";
    private static final HttpServer FAKE_MASTODON = startFakeMastodon();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;
    @Autowired MastodonConnectionRepository connections;
    @Autowired JdbcTemplate jdbc;
    @Autowired MastodonConnectionRepository connectionRows;
    @Autowired MastodonOauthStateRepository stateRows;
    @Autowired ScheduleInputCipher cipher;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String instance = "http://127.0.0.1:" + FAKE_MASTODON.getAddress().getPort();
        registry.add("dashboard.mastodon.allowed-instances", () -> instance);
        registry.add("dashboard.schedule-key", () -> "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");
        registry.add("dashboard.jwt-secret", () -> "integration-test-secret-at-least-32-bytes");
        registry.add("dashboard.admin-email", () -> "admin@example.org");
        registry.add("dashboard.admin-password-hash", () -> new BCryptPasswordEncoder(4).encode("test-password"));
    }

    @AfterAll
    static void stop() { FAKE_MASTODON.stop(0); }

    private String instance() {
        return "http://127.0.0.1:" + FAKE_MASTODON.getAddress().getPort();
    }

    private String token(String subject) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(JwtConfiguration.ISSUER).subject(subject)
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("scope", "items:read collections:write").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String connectAndGetState(String bearer) throws Exception {
        var result = mvc.perform(post("/v1/integrations/mastodon/connect")
                        .header("Authorization", "Bearer " + bearer).contentType("application/json")
                        .content("{\"instance\":\"" + instance() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorizationUrl").isString()).andReturn();
        String url = json.readTree(result.getResponse().getContentAsString()).path("authorizationUrl").asText();
        return query(url).get("state");
    }

    private String callback(String code, String state) throws Exception {
        MvcResult result = mvc.perform(get("/v1/integrations/mastodon/callback").param("code", code).param("state", state))
                .andExpect(status().isFound()).andReturn();
        return result.getResponse().getHeader("Location");
    }

    @Test
    void connectRejectsNonAllowlistedInstanceAndRequiresAuthentication() throws Exception {
        mvc.perform(post("/v1/integrations/mastodon/connect").contentType("application/json")
                .content("{\"instance\":\"" + instance() + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/integrations/mastodon/connect").header("Authorization", "Bearer " + token("owner@example.org"))
                .contentType("application/json").content("{\"instance\":\"https://evil.example\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchBeforeConnectingIsConflict() throws Exception {
        mvc.perform(post("/v1/integrations/mastodon/search").header("Authorization", "Bearer " + token("nobody@example.org"))
                .contentType("application/json").content("{\"query\":\"java\"}")).andExpect(status().isConflict());
    }

    @Test
    void oauthConnectionSearchAndDeduplicationWork() throws Exception {
        String owner = "owner@example.org";
        String bearer = token(owner);
        mvc.perform(get("/v1/integrations/mastodon/connection").header("Authorization", "Bearer " + bearer))
                .andExpect(jsonPath("$.connected").value(false));

        String state = connectAndGetState(bearer);
        String invalid = callback(GOOD_CODE, "forged-state");
        assertTrue(invalid.contains("mastodon=error"), invalid);

        String ok = callback(GOOD_CODE, state);
        assertTrue(ok.contains("mastodon=connected"), ok);
        assertTrue(callback(GOOD_CODE, state).contains("mastodon=error"), "state must be single use");

        mvc.perform(get("/v1/integrations/mastodon/connection").header("Authorization", "Bearer " + bearer))
                .andExpect(jsonPath("$.connected").value(true)).andExpect(jsonPath("$.instance").value(instance()));
        assertFalse(connections.findById(owner).orElseThrow().getEncryptedAccessToken().contains(USER_TOKEN),
                "access token must be stored encrypted");

        var first = mvc.perform(post("/v1/integrations/mastodon/search").header("Authorization", "Bearer " + bearer)
                .contentType("application/json").content("{\"query\":\"java\",\"limit\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fetched").value(2))
                .andExpect(jsonPath("$.inserted").value(2)).andReturn();
        assertEquals(2, json.readTree(first.getResponse().getContentAsString()).path("inserted").asInt());
        mvc.perform(post("/v1/integrations/mastodon/search").header("Authorization", "Bearer " + bearer)
                .contentType("application/json").content("{\"query\":\"java\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inserted").value(0))
                .andExpect(jsonPath("$.duplicates").value(2));
        Integer stored = jdbc.queryForObject("select count(*) from collected_item where source = 'mastodon-oauth'", Integer.class);
        assertEquals(2, stored);
    }

    @Test
    void connectionBelongsOnlyToItsOwnUser() throws Exception {
        mvc.perform(get("/v1/integrations/mastodon/connection").header("Authorization", "Bearer " + token("stranger@example.org")))
                .andExpect(jsonPath("$.connected").value(false));
        mvc.perform(post("/v1/integrations/mastodon/search").header("Authorization", "Bearer " + token("stranger@example.org"))
                .contentType("application/json").content("{\"query\":\"java\"}")).andExpect(status().isConflict());
    }

    @Test
    void disconnectRemovesConnection() throws Exception {
        String owner = "leaver@example.org";
        String bearer = token(owner);
        String state = connectAndGetState(bearer);
        assertTrue(callback(GOOD_CODE, state).contains("mastodon=connected"));
        assertTrue(connections.findById(owner).isPresent());
        mvc.perform(delete("/v1/integrations/mastodon/connection").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isNoContent());
        mvc.perform(get("/v1/integrations/mastodon/connection").header("Authorization", "Bearer " + bearer))
                .andExpect(jsonPath("$.connected").value(false));
        mvc.perform(post("/v1/integrations/mastodon/search").header("Authorization", "Bearer " + bearer)
                .contentType("application/json").content("{\"query\":\"java\"}")).andExpect(status().isConflict());
    }

    @Test
    void authorizationUrlCarriesClientRedirectScopeAndState() throws Exception {
        String url = mvc.perform(post("/v1/integrations/mastodon/connect")
                        .header("Authorization", "Bearer " + token("scope-check@example.org"))
                        .contentType("application/json").content("{\"instance\":\"" + instance() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, String> params = query(json.readTree(url).path("authorizationUrl").asText());
        assertEquals("cid", params.get("client_id"));
        assertTrue(params.get("redirect_uri").endsWith("/v1/integrations/mastodon/callback"));
        assertEquals("read write follow push", params.get("scope"));
        assertFalse(params.get("state").isBlank());
    }

    @Test
    void expiredStateIsRejected() throws Exception {
        stateRows.save(new MastodonOauthState("expired-state-value", "expiry@example.org", instance(),
                Instant.now().minusSeconds(60)));
        String result = callback(GOOD_CODE, "expired-state-value");
        assertTrue(result.contains("mastodon=error"), result);
        assertFalse(connectionRows.findById("expiry@example.org").isPresent());
    }

    @Test
    void revokedTokenAtMastodonReturnsBadGatewayWithoutLeakingToken() throws Exception {
        String owner = "revoked@example.org";
        String bearer = token(owner);
        assertTrue(callback(GOOD_CODE, connectAndGetState(bearer)).contains("mastodon=connected"));
        var row = connectionRows.findById(owner).orElseThrow();
        row.replaceToken(row.getInstance(), cipher.encrypt("revoked-token"), Instant.now());
        connectionRows.save(row);
        var result = mvc.perform(post("/v1/integrations/mastodon/search").header("Authorization", "Bearer " + bearer)
                        .contentType("application/json").content("{\"query\":\"java\"}"))
                .andExpect(status().isBadGateway()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("revoked-token"));
    }

    private static Map<String, String> query(String url) {
        Map<String, String> params = new java.util.HashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return params;
    }

    private static HttpServer startFakeMastodon() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/apps", exchange -> reply(exchange, 200,
                    "{\"client_id\":\"cid\",\"client_secret\":\"csec\"}"));
            server.createContext("/oauth/token", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (body.contains("code=" + GOOD_CODE)) reply(exchange, 200, "{\"access_token\":\"" + USER_TOKEN + "\"}");
                else reply(exchange, 400, "{\"error\":\"invalid_grant\"}");
            });
            server.createContext("/api/v2/search", exchange -> {
                if (("Bearer " + USER_TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    reply(exchange, 200, "{\"statuses\":["
                            + "{\"id\":\"101\",\"visibility\":\"public\",\"url\":\"https://mastodon.example/@a/101\","
                            + "\"account\":{\"acct\":\"a\"},\"content\":\"<p>Java rocks</p>\",\"created_at\":\"2026-11-20T10:00:00Z\"},"
                            + "{\"id\":\"102\",\"visibility\":\"public\",\"url\":\"https://mastodon.example/@b/102\","
                            + "\"account\":{\"acct\":\"b\"},\"content\":\"Spring Boot\",\"created_at\":\"2026-11-21T10:00:00Z\"}"
                            + "]}");
                } else {
                    reply(exchange, 401, "{\"error\":\"unauthorized\"}");
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
    }
}
