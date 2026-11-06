package com.henrique.dashboard;

import com.example.collectorrunner.integration.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.henrique.collector.source.SourceAccessException;
import java.sql.SQLException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "dashboard.api-key=reader-key")
@AutoConfigureMockMvc
class CollectionApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;
    @MockBean CollectionRunner runner;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        registry.add("dashboard.admin-email", () -> "admin@example.org");
        registry.add("dashboard.admin-password-hash", () -> new BCryptPasswordEncoder(4).encode("test-password"));
        registry.add("dashboard.jwt-secret", () -> "integration-test-secret-at-least-32-bytes");
    }

    private String login() throws Exception {
        var response = mvc.perform(post("/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.org\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresIn").value(900)).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).path("accessToken").asText();
    }

    @Test
    void loginPermitsManualCollectionAndReadButApiKeyCannotWrite() throws Exception {
        when(runner.run(CollectionSource.GOOGLE_NEWS, "java")).thenReturn(new CollectionRunner.CollectionResult(CollectionSource.GOOGLE_NEWS, 2, 1, 1));
        String body = "{\"source\":\"GOOGLE_NEWS\",\"input\":\"java\"}";
        mvc.perform(post("/v1/collections").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/collections").header("X-API-Key", "reader-key").contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        String token = login();
        mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inserted").value(1)).andExpect(jsonPath("$.duplicates").value(1));
        mvc.perform(get("/v1/items").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void rejectsBadLoginAndInvalidOrExpiredTokens() throws Exception {
        mvc.perform(post("/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.org\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/items").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        var claims = JwtClaimsSet.builder().issuer("dashboard-mini").subject("admin@example.org")
                .issuedAt(Instant.now().minusSeconds(3600)).expiresAt(Instant.now().minusSeconds(120))
                .claim("scope", "items:read collections:write").build();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/v1/items").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongIssuerAndTokensWithoutCollectionScope() throws Exception {
        var now = Instant.now();
        var wrongIssuer = JwtClaimsSet.builder().issuer("other-app").subject("admin@example.org")
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("scope", "items:read collections:write").build();
        String invalid = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), wrongIssuer)).getTokenValue();
        mvc.perform(get("/v1/items").header("Authorization", "Bearer " + invalid)).andExpect(status().isUnauthorized());
        var readOnly = JwtClaimsSet.builder().issuer("dashboard-mini").subject("reader@example.org")
                .issuedAt(now).expiresAt(now.plusSeconds(900)).claim("scope", "items:read").build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), readOnly)).getTokenValue();
        mvc.perform(get("/v1/items").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"source\":\"GOOGLE_NEWS\",\"input\":\"java\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(runner);
    }

    @Test
    void mapsFailuresWithoutExposingSecretFeedUrl() throws Exception {
        String token = login();
        String secret = "https://www.google.com/alerts/feeds/user/secret";
        String body = json.writeValueAsString(new CollectionControllerBody("GOOGLE_ALERTS", secret));
        doThrow(new IllegalStateException(secret)).when(runner).run(CollectionSource.GOOGLE_ALERTS, secret);
        var result = mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body)).andExpect(status().isBadGateway()).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains(secret));
        doThrow(new SQLException(secret)).when(runner).run(CollectionSource.GOOGLE_ALERTS, secret);
        mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token).contentType("application/json").content(body))
                .andExpect(status().isServiceUnavailable());
        doThrow(new SourceAccessException(secret)).when(runner).run(CollectionSource.GOOGLE_ALERTS, secret);
        mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token).contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity());
        doThrow(new IllegalArgumentException(secret)).when(runner).run(CollectionSource.GOOGLE_ALERTS, secret);
        mvc.perform(post("/v1/collections").header("Authorization", "Bearer " + token).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    record CollectionControllerBody(String source, String input) { }
}
